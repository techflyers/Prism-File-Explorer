package com.raival.compose.file.explorer.screen.main.tab.files.shizuku

import android.content.pm.PackageManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.raival.compose.file.explorer.App.Companion.globalClass
import com.raival.compose.file.explorer.App.Companion.logger
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Manages Shizuku and root (su) access for privileged file operations.
 *
 * Architecture follows nfile's root_shizuku_service.dart pattern:
 * - Shizuku path: uses Shizuku.newProcess() via reflection to spawn a privileged shell
 * - Root path: uses Runtime.getRuntime().exec(["su", "-c", command])
 *
 * Usage:
 *   ShizukuManager.checkStatus()
 *   if (ShizukuManager.isShizukuReady) { ... }
 *   ShizukuManager.runCommand("ls /data") { output -> ... }
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

        // Check root availability
        isRootAvailable = checkRoot()

        // Auto-select best available mode
        if (accessMode == AccessMode.NONE) {
            if (isShizukuGranted) accessMode = AccessMode.SHIZUKU
            else if (isRootAvailable) accessMode = AccessMode.ROOT
        }
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

    // ─── Command execution ────────────────────────────────────────────────────

    /**
     * Runs a shell command with the current privileged access mode.
     * Shizuku path uses Shizuku.newProcess() via reflection (mirrors nfile).
     * Root path uses `su -c`.
     *
     * @return stdout output, or null on failure
     */
    fun runCommand(command: String): String? {
        return when (accessMode) {
            AccessMode.SHIZUKU -> runViaShizuku(command)
            AccessMode.ROOT -> runViaRoot(command)
            AccessMode.NONE -> null
        }
    }

    /**
     * Runs command via Shizuku using reflection to call Shizuku.newProcess().
     * Mirrors nfile's root_shizuku_service.dart approach.
     */
    private fun runViaShizuku(command: String): String? {
        return try {
            // Reflect Shizuku.newProcess(String[] cmd, String[] env, String dir)
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

            val output = BufferedReader(InputStreamReader(process.inputStream))
                .readLines()
                .joinToString("\n")
            process.waitFor()
            output
        } catch (e: Exception) {
            logger.logError(e)
            null
        }
    }

    /**
     * Runs command via root su shell.
     */
    private fun runViaRoot(command: String): String? {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", command))
            val output = BufferedReader(InputStreamReader(process.inputStream))
                .readLines()
                .joinToString("\n")
            process.waitFor()
            output
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

    // ─── File listing ─────────────────────────────────────────────────────────

    /**
     * Lists files in the given directory path using privileged shell.
     * Uses `stat -L -c "%F|%s|%Y|%n"` — same format as nfile's listFiles().
     * Returns list of (name, isDir, size, modTime) tuples.
     */
    fun listFiles(dirPath: String): List<ShizukuFileEntry> {
        val command = "stat -L -c \"%F|%s|%Y|%n\" \"$dirPath\"/*  2>/dev/null || ls -la \"$dirPath\""
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
