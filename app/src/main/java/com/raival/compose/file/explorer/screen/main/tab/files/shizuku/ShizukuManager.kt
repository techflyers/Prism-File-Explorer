package com.raival.compose.file.explorer.screen.main.tab.files.shizuku

import android.content.pm.PackageManager
import android.util.Base64
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.raival.compose.file.explorer.App.Companion.globalClass
import com.raival.compose.file.explorer.App.Companion.logger
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

/**
 * Manages Shizuku and root (su) access for privileged file operations.
 */
object ShizukuManager {

    enum class AccessMode { NONE, SHIZUKU, ROOT }

    var accessMode by mutableStateOf(AccessMode.NONE)
        private set

    var isShizukuInstalled by mutableStateOf(false)
        private set

    var isShizukuGranted by mutableStateOf(false)
        private set

    var isRootAvailable by mutableStateOf(false)
        private set

    val isShizukuReady get() = accessMode == AccessMode.SHIZUKU && isShizukuGranted
    val isPrivileged get() = accessMode != AccessMode.NONE

    // ─── Lifecycle ────────────────────────────────────────────────────────────

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        onShizukuBinderReceived()
    }

    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        if (accessMode == AccessMode.SHIZUKU) {
            accessMode = AccessMode.NONE
            isShizukuGranted = false
        }
    }

    private val requestResultListener = Shizuku.OnRequestPermissionResultListener { _, result ->
        isShizukuGranted = result == PackageManager.PERMISSION_GRANTED
        if (isShizukuGranted) accessMode = AccessMode.SHIZUKU
    }

    fun initialize() {
        Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
        Shizuku.addBinderDeadListener(binderDeadListener)
        Shizuku.addRequestPermissionResultListener(requestResultListener)
        checkStatus()
    }

    fun cleanup() {
        Shizuku.removeBinderReceivedListener(binderReceivedListener)
        Shizuku.removeBinderDeadListener(binderDeadListener)
        Shizuku.removeRequestPermissionResultListener(requestResultListener)
    }

    // ─── Status ───────────────────────────────────────────────────────────────

    fun checkStatus() {
        // Check if Shizuku package is installed
        isShizukuInstalled = try {
            globalClass.packageManager.getPackageInfo("moe.shizuku.privileged.api", 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }

        // Check if Shizuku binder is alive
        if (isShizukuInstalled) {
            try {
                if (Shizuku.pingBinder()) {
                    onShizukuBinderReceived()
                }
            } catch (_: Exception) {}
        }

        // Auto-select Shizuku mode if granted
        if (accessMode == AccessMode.NONE && isShizukuGranted) {
            accessMode = AccessMode.SHIZUKU
        }
    }

    /**
     * Probes root availability on-demand (does NOT run on app startup).
     * Call this when opening the Privileged Access settings screen or when user opts into Root.
     */
    fun probeRoot(): Boolean {
        isRootAvailable = checkRoot()
        if (accessMode == AccessMode.NONE && isRootAvailable && !isShizukuGranted) {
            accessMode = AccessMode.ROOT
        }
        return isRootAvailable
    }

    private fun onShizukuBinderReceived() {
        try {
            isShizukuGranted = Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
            if (isShizukuGranted && accessMode == AccessMode.NONE) {
                accessMode = AccessMode.SHIZUKU
            }
        } catch (_: Exception) {}
    }

    fun requestShizukuPermission() {
        try {
            if (Shizuku.shouldShowRequestPermissionRationale()) return
            Shizuku.requestPermission(1001)
        } catch (e: Exception) {
            logger.logError(e)
        }
    }

    fun updateAccessMode(mode: AccessMode) {
        accessMode = mode
    }

    // ─── Shell Utility ────────────────────────────────────────────────────────

    fun escapeShellArg(arg: String): String {
        return "'" + arg.replace("'", "'\\''") + "'"
    }

    data class CommandResult(
        val exitCode: Int,
        val stdout: String,
        val stderr: String
    ) {
        val isSuccess: Boolean get() = exitCode == 0
    }

    // ─── Command execution ────────────────────────────────────────────────────

    /**
     * Executes a command in privileged shell and returns exitCode, stdout, and stderr.
     */
    fun executeCommand(command: String): CommandResult? {
        return when (accessMode) {
            AccessMode.SHIZUKU -> executeViaShizuku(command)
            AccessMode.ROOT -> executeViaRoot(command)
            AccessMode.NONE -> null
        }
    }

    /**
     * Runs a shell command with the current privileged access mode.
     * @return stdout output, or null on failure
     */
    fun runCommand(command: String): String? {
        val result = executeCommand(command) ?: return null
        return result.stdout
    }

    private fun executeViaShizuku(command: String): CommandResult? {
        return try {
            val newProcessMethod = Shizuku::class.java.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java
            )
            newProcessMethod.isAccessible = true
            val process = newProcessMethod.invoke(
                null,
                arrayOf("sh", "-c", command),
                null,
                null
            ) as Process

            val stdout = BufferedReader(InputStreamReader(process.inputStream))
                .readLines()
                .joinToString("\n")
            val stderr = BufferedReader(InputStreamReader(process.errorStream))
                .readLines()
                .joinToString("\n")
            val exitCode = process.waitFor()

            if (exitCode != 0 && stderr.isNotBlank()) {
                logger.logWarning("Shizuku command exited with $exitCode: $command\nStderr: $stderr")
            }

            CommandResult(exitCode, stdout, stderr)
        } catch (e: Exception) {
            logger.logError(e)
            null
        }
    }

    private fun executeViaRoot(command: String): CommandResult? {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", command))
            val stdout = BufferedReader(InputStreamReader(process.inputStream))
                .readLines()
                .joinToString("\n")
            val stderr = BufferedReader(InputStreamReader(process.errorStream))
                .readLines()
                .joinToString("\n")
            val exitCode = process.waitFor()

            if (exitCode != 0 && stderr.isNotBlank()) {
                logger.logWarning("Root command exited with $exitCode: $command\nStderr: $stderr")
            }

            CommandResult(exitCode, stdout, stderr)
        } catch (e: Exception) {
            logger.logError(e)
            null
        }
    }

    // ─── Root detection ───────────────────────────────────────────────────────

    private fun checkRoot(): Boolean {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "id"))
            val output = BufferedReader(InputStreamReader(process.inputStream)).readLine() ?: ""
            process.waitFor()
            output.contains("uid=0")
        } catch (_: Exception) {
            false
        }
    }

    // ─── Privileged File Operations ───────────────────────────────────────────

    fun createFile(path: String): Boolean {
        val cmd = "touch " + escapeShellArg(path)
        return executeCommand(cmd)?.isSuccess == true
    }

    fun createDirectory(path: String): Boolean {
        val cmd = "mkdir -p " + escapeShellArg(path)
        return executeCommand(cmd)?.isSuccess == true
    }

    fun delete(path: String): Boolean {
        val cmd = "rm -rf " + escapeShellArg(path)
        return executeCommand(cmd)?.isSuccess == true
    }

    fun rename(src: String, dst: String): Boolean {
        val cmd = "mv " + escapeShellArg(src) + " " + escapeShellArg(dst)
        return executeCommand(cmd)?.isSuccess == true
    }

    fun copy(src: String, dst: String): Boolean {
        val cmd = "cp -a " + escapeShellArg(src) + " " + escapeShellArg(dst) +
                " || cp -r " + escapeShellArg(src) + " " + escapeShellArg(dst)
        return executeCommand(cmd)?.isSuccess == true
    }

    fun exists(path: String): Boolean {
        val cmd = "[ -e " + escapeShellArg(path) + " ]"
        return executeCommand(cmd)?.isSuccess == true
    }

    fun isDirectory(path: String): Boolean {
        val cmd = "[ -d " + escapeShellArg(path) + " ]"
        return executeCommand(cmd)?.isSuccess == true
    }

    fun readText(path: String): String? {
        val cmd = "cat " + escapeShellArg(path)
        val res = executeCommand(cmd)
        return if (res?.isSuccess == true) res.stdout else null
    }

    fun writeText(path: String, content: String): Boolean {
        val encoded = Base64.encodeToString(content.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        val cmd = "echo " + escapeShellArg(encoded) + " | base64 -d > " + escapeShellArg(path)
        return executeCommand(cmd)?.isSuccess == true
    }

    fun readBytes(path: String): ByteArray? {
        val cmd = "base64 " + escapeShellArg(path)
        val res = executeCommand(cmd)
        if (res?.isSuccess == true && res.stdout.isNotEmpty()) {
            return try {
                Base64.decode(res.stdout, Base64.DEFAULT)
            } catch (_: Exception) {
                null
            }
        }
        return null
    }

    fun writeBytes(path: String, bytes: ByteArray): Boolean {
        val encoded = Base64.encodeToString(bytes, Base64.NO_WRAP)
        val cmd = "echo " + escapeShellArg(encoded) + " | base64 -d > " + escapeShellArg(path)
        return executeCommand(cmd)?.isSuccess == true
    }

    fun copyToLocal(privilegedSrc: String, localDst: File): Boolean {
        localDst.parentFile?.mkdirs()
        // Try direct cp first (if target directory is accessible to shell user)
        val cpCmd = "cp -a " + escapeShellArg(privilegedSrc) + " " + escapeShellArg(localDst.absolutePath)
        val cpRes = executeCommand(cpCmd)
        if (cpRes?.isSuccess == true && localDst.exists() && localDst.canRead()) {
            return true
        }
        // Fallback: pipe bytes
        val bytes = readBytes(privilegedSrc)
        if (bytes != null) {
            return try {
                localDst.writeBytes(bytes)
                true
            } catch (_: Exception) {
                false
            }
        }
        return false
    }

    fun copyFromLocal(localSrc: File, privilegedDst: String): Boolean {
        val cpCmd = "cp -a " + escapeShellArg(localSrc.absolutePath) + " " + escapeShellArg(privilegedDst)
        val cpRes = executeCommand(cpCmd)
        if (cpRes?.isSuccess == true && exists(privilegedDst)) {
            return true
        }
        return try {
            val bytes = localSrc.readBytes()
            writeBytes(privilegedDst, bytes)
        } catch (_: Exception) {
            false
        }
    }

    // ─── File listing ─────────────────────────────────────────────────────────

    /**
     * Lists files in the given directory path using privileged shell.
     * Uses `find <dir> -mindepth 1 -maxdepth 1 -exec stat -L -c "%F|%s|%Y|%n" {} +`
     * to safely include hidden files and avoid wildcard expansion issues.
     */
    fun listFiles(dirPath: String): List<ShizukuFileEntry> {
        val cleanPath = if (dirPath.length > 1 && dirPath.endsWith("/")) {
            dirPath.removeSuffix("/")
        } else {
            dirPath
        }
        val safePath = escapeShellArg(cleanPath)

        val command = "find $safePath -mindepth 1 -maxdepth 1 -exec stat -L -c \"%F|%s|%Y|%n\" {} + 2>/dev/null || " +
                "find $safePath -mindepth 1 -maxdepth 1 -exec stat -c \"%F|%s|%Y|%n\" {} + 2>/dev/null || " +
                "stat -L -c \"%F|%s|%Y|%n\" $safePath/* 2>/dev/null"

        val output = runCommand(command) ?: return emptyList()

        return output.lines()
            .filter { it.isNotBlank() }
            .mapNotNull { line ->
                try {
                    val parts = line.split("|")
                    if (parts.size >= 4) {
                        val type = parts[0].trim()
                        val size = parts[1].trim().toLongOrNull() ?: 0L
                        val modTime = parts[2].trim().toLongOrNull()?.times(1000L) ?: 0L
                        val fullPath = parts[3].trim()
                        val name = fullPath.substringAfterLast("/")
                        if (name.isNotEmpty() && name != "." && name != "..") {
                            ShizukuFileEntry(
                                name = name,
                                path = fullPath,
                                isDirectory = type.contains("directory"),
                                size = size,
                                lastModified = modTime
                            )
                        } else null
                    } else null
                } catch (_: Exception) {
                    null
                }
            }
    }
}

data class ShizukuFileEntry(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val size: Long,
    val lastModified: Long
)
