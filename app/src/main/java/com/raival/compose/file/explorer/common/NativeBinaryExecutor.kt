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
            val buffer = ByteArray(2048)
            val lineBuffer = StringBuilder()
            val percentPattern = java.util.regex.Pattern.compile("([0-9]{1,3})\\s*%")
            val stream = process.inputStream

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

                val read = stream.read(buffer)
                if (read == -1) break

                val textChunk = String(buffer, 0, read, Charsets.UTF_8)
                outputBuilder.append(textChunk)

                if (onProgressUpdate != null) {
                    for (i in 0 until read) {
                        val c = buffer[i].toInt().toChar()
                        if (c == '\r' || c == '\n') {
                            val line = lineBuffer.toString().trim()
                            lineBuffer.clear()
                            if (line.isNotEmpty()) {
                                parseAndReportProgress(line, percentPattern, onProgressUpdate)
                            }
                        } else {
                            lineBuffer.append(c)
                            if (c == '%' && lineBuffer.length in 2..15) {
                                parseAndReportProgress(lineBuffer.toString(), percentPattern, onProgressUpdate)
                            }
                            if (lineBuffer.length > 500) {
                                lineBuffer.clear()
                            }
                        }
                    }
                }
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

    private fun parseAndReportProgress(
        line: String,
        pattern: java.util.regex.Pattern,
        onProgressUpdate: (progressPercent: Float, statusText: String) -> Unit
    ) {
        val matcher = pattern.matcher(line)
        val percent = if (matcher.find()) {
            matcher.group(1)?.toIntOrNull()
        } else null

        val fileName = when {
            line.contains(" - ") -> line.substringAfter(" - ").trim().substringAfterLast('/')
            line.contains(" + ") -> line.substringAfter(" + ").trim().substringAfterLast('/')
            line.contains("Extracting ") -> line.substringAfter("Extracting").trim().substringAfterLast('/')
            line.contains("Compressing ") -> line.substringAfter("Compressing").trim().substringAfterLast('/')
            else -> ""
        }

        if (percent != null) {
            val progressFloat = (percent / 100f).coerceIn(0f, 1f)
            onProgressUpdate(progressFloat, fileName)
        } else if (fileName.isNotEmpty()) {
            onProgressUpdate(-1f, fileName)
        }
    }
}
