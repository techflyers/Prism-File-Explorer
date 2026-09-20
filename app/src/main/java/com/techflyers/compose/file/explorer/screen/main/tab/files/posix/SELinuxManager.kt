package com.techflyers.compose.file.explorer.screen.main.tab.files.posix

import android.os.Build
import android.system.Os
import androidx.annotation.RequiresApi
import com.techflyers.compose.file.explorer.screen.main.tab.files.shizuku.ShizukuManager
import java.io.File
import java.lang.reflect.Method
import java.nio.charset.StandardCharsets

object SELinuxManager {
    private val seLinuxClass: Class<*>? by lazy {
        try {
            Class.forName("android.os.SELinux")
        } catch (_: Exception) {
            null
        }
    }

    private val isSELinuxEnabledMethod: Method? by lazy {
        try {
            seLinuxClass?.getMethod("isSELinuxEnabled")
        } catch (_: Exception) {
            null
        }
    }

    private val isSELinuxEnforcedMethod: Method? by lazy {
        try {
            seLinuxClass?.getMethod("isSELinuxEnforced")
        } catch (_: Exception) {
            null
        }
    }

    private val getFileContextMethod: Method? by lazy {
        try {
            seLinuxClass?.getMethod("getFileContext", String::class.java)
        } catch (_: Exception) {
            null
        }
    }

    private val setFileContextMethod: Method? by lazy {
        try {
            seLinuxClass?.getMethod("setFileContext", String::class.java, String::class.java)
        } catch (_: Exception) {
            null
        }
    }

    private val restoreconStringMethod: Method? by lazy {
        try {
            seLinuxClass?.getMethod("restorecon", String::class.java)
        } catch (_: Exception) {
            null
        }
    }

    private val restoreconRecursiveMethod: Method? by lazy {
        try {
            seLinuxClass?.getMethod("restoreconRecursive", File::class.java)
        } catch (_: Exception) {
            null
        }
    }

    fun isSELinuxEnabled(): Boolean =
        try {
            (isSELinuxEnabledMethod?.invoke(null) as? Boolean) ?: true
        } catch (_: Exception) {
            true
        }

    fun isSELinuxEnforced(): Boolean =
        try {
            (isSELinuxEnforcedMethod?.invoke(null) as? Boolean) ?: true
        } catch (_: Exception) {
            true
        }

    fun getFileContext(path: String): String? {
        // 1. Try android.os.SELinux reflection
        try {
            val ctx = getFileContextMethod?.invoke(null, path) as? String
            if (!ctx.isNullOrBlank()) return ctx
        } catch (_: Exception) {}

        // 2. Try android.system.Os.getxattr (API 26+)
        try {
            val bytes = Os.getxattr(path, "security.selinux")
            if (bytes != null && bytes.isNotEmpty()) {
                val validLen = if (bytes.last() == 0.toByte()) bytes.size - 1 else bytes.size
                val ctx = String(bytes, 0, validLen, StandardCharsets.UTF_8).trim()
                if (ctx.isNotEmpty()) return ctx
            }
        } catch (_: Exception) {}

        // 3. Try privileged shell if available
        if (ShizukuManager.isPrivileged) {
            try {
                val safePath = ShizukuManager.escapeShellArg(path)
                val out = ShizukuManager.runCommand("ls -Zd $safePath 2>/dev/null || stat -c '%C' $safePath 2>/dev/null")
                if (!out.isNullOrBlank()) {
                    val trimmed = out.trim()
                    // ls -Zd output is: "context path"
                    val parts = trimmed.split("\\s+".toRegex())
                    val candidate = parts.firstOrNull { it.contains(":") }
                    if (!candidate.isNullOrBlank() && candidate != "?") {
                        return candidate
                    }
                }
            } catch (_: Exception) {}
        }

        return null
    }

    fun setFileContext(path: String, context: String, recursive: Boolean = false): Boolean {
        var success = false

        // 1. Try android.os.SELinux reflection
        try {
            success = setFileContextMethod?.invoke(null, path, context) as? Boolean ?: false
        } catch (_: Exception) {}

        // 2. Try Os.setxattr
        if (!success) {
            try {
                val bytes = (context + "\u0000").toByteArray(StandardCharsets.UTF_8)
                Os.setxattr(path, "security.selinux", bytes, 0)
                success = true
            } catch (_: Exception) {}
        }

        // 3. Fallback to privileged shell
        if (!success && ShizukuManager.isPrivileged) {
            val safePath = ShizukuManager.escapeShellArg(path)
            val safeCtx = ShizukuManager.escapeShellArg(context)
            val flag = if (recursive) "-R " else ""
            val cmd = "chcon $flag$safeCtx $safePath"
            success = ShizukuManager.executeCommand(cmd)?.isSuccess == true
        }

        if (success && recursive && !ShizukuManager.isPrivileged) {
            val dir = File(path)
            if (dir.isDirectory) {
                dir.walkTopDown().drop(1).forEach { child ->
                    setFileContext(child.absolutePath, context, false)
                }
            }
        }

        return success
    }

    fun restorecon(path: String, recursive: Boolean = false): Boolean {
        var success = false

        // 1. Try reflection
        try {
            if (recursive) {
                success = restoreconRecursiveMethod?.invoke(null, File(path)) as? Boolean ?: false
            } else {
                success = restoreconStringMethod?.invoke(null, path) as? Boolean ?: false
            }
        } catch (_: Exception) {}

        // 2. Fallback to privileged shell
        if (!success && ShizukuManager.isPrivileged) {
            val safePath = ShizukuManager.escapeShellArg(path)
            val flag = if (recursive) "-R " else ""
            val cmd = "restorecon $flag$safePath"
            success = ShizukuManager.executeCommand(cmd)?.isSuccess == true
        }

        return success
    }
}
