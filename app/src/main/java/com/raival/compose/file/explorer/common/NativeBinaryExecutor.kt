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
        workingDir: String? = null
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

        // Recursively log Tectonic cache contents for diagnostics
        val tectonicDir = File(context.filesDir, "Tectonic")
        android.util.Log.d(tag, "Checking Tectonic cache at: ${tectonicDir.absolutePath} (exists: ${tectonicDir.exists()})")
        if (tectonicDir.exists()) {
            var count = 0
            tectonicDir.walkTopDown().forEach { file ->
                if (file.isFile) {
                    count++
                    if (count <= 50) {
                        android.util.Log.d(tag, "  File: ${file.relativeTo(tectonicDir).path} (size: ${file.length()})")
                    }
                }
            }
            android.util.Log.d(tag, "  Total cached files: $count")
        }

        try {
            val process = pb.start()
            val outputBytes = process.inputStream.readBytes()
            val exitCode = process.waitFor()
            val output = outputBytes.toString(Charsets.UTF_8)

            android.util.Log.d(tag, "Process exited with code: $exitCode")
            android.util.Log.d(tag, "Output:\n$output")

            NativeBinaryResult(exitCode = exitCode, output = output)
        } catch (e: Exception) {
            android.util.Log.e(tag, "Process start failed", e)
            NativeBinaryResult(
                exitCode = -1,
                output = "Execution error: ${e.message}"
            )
        }
    }
}
