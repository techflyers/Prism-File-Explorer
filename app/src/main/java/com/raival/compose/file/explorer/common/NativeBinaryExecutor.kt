package com.raival.compose.file.explorer.common

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Result of executing a native binary.
 */
data class NativeBinaryResult(
    val exitCode: Int,
    val output: String
) {
    val success: Boolean get() = exitCode == 0
}

/**
 * Executes bundled native binaries (lib7za.so, libtectonic.so) from
 * the app's nativeLibraryDir. Ported from NFile's MainActivity.kt runNativeBinary.
 */
object NativeBinaryExecutor {

    /**
     * Run a native binary with the given arguments.
     *
     * @param context Application context
     * @param binaryName The binary filename (e.g., "lib7za.so", "libtectonic.so")
     * @param arguments Command-line arguments to pass
     * @param workingDir Optional working directory for the process
     * @return [NativeBinaryResult] with exit code and combined stdout+stderr output
     */
    suspend fun run(
        context: Context,
        binaryName: String,
        arguments: List<String>,
        workingDir: String? = null,
        isAborted: (() -> Boolean)? = null,
        onProgressUpdate: ((progressPercent: Float, statusText: String) -> Unit)? = null
    ): NativeBinaryResult = withContext(Dispatchers.IO) {
        val nativeLibDir = context.applicationInfo.nativeLibraryDir
        val binaryFile = File(nativeLibDir, binaryName)

        if (!binaryFile.exists()) {
            return@withContext NativeBinaryResult(
                exitCode = -1,
                output = "Binary not found: ${binaryFile.absolutePath}"
            )
        }

        // Ensure binary is executable
        if (!binaryFile.canExecute()) {
            binaryFile.setExecutable(true, false)
        }

        val cmd = mutableListOf(binaryFile.absolutePath)
        cmd.addAll(arguments)

        val pb = ProcessBuilder(cmd)
        pb.redirectErrorStream(true)

        if (workingDir != null) {
            val wd = File(workingDir)
            wd.mkdirs()
            pb.directory(wd)
        }

        // Pass through essential environment variables
        val env = pb.environment()
        env["HOME"] = context.filesDir.absolutePath
        env["TMPDIR"] = context.cacheDir.absolutePath
        env["PATH"] = "/system/bin:/system/xbin"
        // Tectonic uses XDG_CACHE_HOME to locate its cached bundles
        env["XDG_CACHE_HOME"] = context.filesDir.absolutePath

        val tag = "NativeBinaryExecutor"
        android.util.Log.d(tag, "Executing: ${cmd.joinToString(" ")}")
        if (workingDir != null) {
            android.util.Log.d(tag, "Working directory: $workingDir")
        }

        try {
            val process = pb.start()
            val outputBuilder = StringBuilder()
            val charBuffer = CharArray(1024)
            val lineBuffer = StringBuilder()
            val streamReader = java.io.InputStreamReader(process.inputStream, Charsets.UTF_8)

            var lastReportedPercent = -1f
            var lastReportedFile = ""

            fun evaluateProgress(line: String) {
                val (percent, fileName) = parseProgress(line)
                if (percent != null && percent >= 0f) {
                    val effectiveFile = if (fileName.isNotEmpty()) fileName else lastReportedFile
                    if (percent != lastReportedPercent || effectiveFile != lastReportedFile) {
                        lastReportedPercent = percent
                        lastReportedFile = effectiveFile
                        onProgressUpdate?.invoke(percent, effectiveFile)
                    }
                } else if (fileName.isNotEmpty() && fileName != lastReportedFile) {
                    lastReportedFile = fileName
                    onProgressUpdate?.invoke(-1f, fileName)
                }
            }

            while (true) {
                if (isAborted?.invoke() == true) {
                    try {
                        process.destroy()
                        process.destroyForcibly()
                    } catch (_: Exception) {}
                    return@withContext NativeBinaryResult(
                        exitCode = -1,
                        output = "Aborted by user"
                    )
                }

                val read = streamReader.read(charBuffer)
                if (read == -1) break

                outputBuilder.append(charBuffer, 0, read)

                if (onProgressUpdate != null) {
                    for (i in 0 until read) {
                        val c = charBuffer[i]
                        when (c) {
                            '\b' -> {
                                // 7-Zip on Linux/Android uses \b to clear the previous progress line
                                if (lineBuffer.isNotEmpty()) {
                                    lineBuffer.deleteCharAt(lineBuffer.length - 1)
                                }
                            }
                            '\r', '\n' -> {
                                val line = lineBuffer.toString().trim()
                                lineBuffer.clear()
                                if (line.isNotEmpty()) {
                                    evaluateProgress(line)
                                }
                            }
                            else -> {
                                lineBuffer.append(c)
                            }
                        }
                    }

                    // 7-Zip flushes after printing progress without emitting \r or \n.
                    // If lineBuffer contains a complete progress string, evaluate it immediately.
                    if (lineBuffer.isNotEmpty()) {
                        evaluateProgress(lineBuffer.toString().trim())
                    }
                }
            }

            if (onProgressUpdate != null && lineBuffer.isNotEmpty()) {
                evaluateProgress(lineBuffer.toString().trim())
            }

            val exitCode = process.waitFor()
            val output = outputBuilder.toString()

            android.util.Log.d(tag, "Process exited with code: $exitCode")
            NativeBinaryResult(exitCode = exitCode, output = output)
        } catch (e: Exception) {
            android.util.Log.e(tag, "Process start failed", e)
            NativeBinaryResult(
                exitCode = -1,
                output = "Execution error: ${e.message}"
            )
        }
    }

    private val PERCENT_PATTERN = java.util.regex.Pattern.compile("(\\d{1,3})\\s*%")
    private val ACTION_REGEX = Regex("""%\s*\d*\.?\s*([+\-U])\s+""")

    internal fun parseProgress(line: String): Pair<Float?, String> {
        val cleanLine = line.trim()
        val matcher = PERCENT_PATTERN.matcher(cleanLine)
        val percent = if (matcher.find()) {
            val p = matcher.group(1)?.toIntOrNull()
            p?.let { (it / 100f).coerceIn(0f, 1f) }
        } else null

        val fileName = extractFileName(cleanLine)
        return Pair(percent, fileName)
    }

    internal fun extractFileName(line: String): String {
        val cleanLine = line.trim()
        val actionIndex = when {
            cleanLine.contains(" + ") -> cleanLine.indexOf(" + ") + 3
            cleanLine.contains(" - ") -> cleanLine.indexOf(" - ") + 3
            cleanLine.contains(" U ") -> cleanLine.indexOf(" U ") + 3
            cleanLine.contains("Extracting ") -> cleanLine.indexOf("Extracting ") + 11
            cleanLine.contains("Compressing ") -> cleanLine.indexOf("Compressing ") + 12
            cleanLine.startsWith("+ ") -> 2
            cleanLine.startsWith("- ") -> 2
            cleanLine.startsWith("U ") -> 2
            else -> {
                val match = ACTION_REGEX.find(cleanLine)
                if (match != null) {
                    match.range.last + 1
                } else {
                    -1
                }
            }
        }

        if (actionIndex != -1 && actionIndex < cleanLine.length) {
            val rawPath = cleanLine.substring(actionIndex).trim()
            val fileName = rawPath.substringAfterLast('/').substringAfterLast('\\').trim()
            if (fileName.isNotEmpty() && !fileName.startsWith("%")) {
                return fileName
            }
        }
        return ""
    }
}
