package com.techflyers.compose.file.explorer.screen.terminal

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.techflyers.compose.file.explorer.App.Companion.globalClass
import com.techflyers.compose.file.explorer.R
import com.techflyers.compose.file.explorer.common.copyToClipboard
import com.techflyers.compose.file.explorer.screen.main.tab.files.shizuku.ShizukuManager
import com.techflyers.compose.file.explorer.screen.preferences.constant.TerminalAppPreference
import java.io.File

object TerminalLauncher {
    private const val TAG = "TerminalLauncher"
    const val TERMUX_PACKAGE = "com.termux"
    const val TERMUX_RUN_COMMAND_SERVICE = "com.termux.app.RunCommandService"
    const val TERMUX_RUN_COMMAND_ACTION = "com.termux.RUN_COMMAND"
    const val TERMUX_PERMISSION = "com.termux.permission.RUN_COMMAND"
    const val TERMUX_PERMISSION_REQUEST_CODE = 1001

    @Volatile
    private var pendingTermuxPath: String? = null

    @Volatile
    private var pendingTermuxCommand: String? = null

    private tailrec fun Context.findActivity(): Activity? = when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }

    fun isPackageInstalled(context: Context, packageName: String): Boolean {
        return try {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }

    fun hasTermuxPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            TERMUX_PERMISSION
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Attempts to automatically grant com.termux.permission.RUN_COMMAND via Shizuku or root
     * if the user has already configured privileged access in Prism.
     */
    fun tryGrantPermissionViaShizuku(context: Context): Boolean {
        return try {
            if (ShizukuManager.isPrivileged) {
                ShizukuManager.runCommand("pm grant ${context.packageName} $TERMUX_PERMISSION")
                hasTermuxPermission(context)
            } else {
                false
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to grant permission via Shizuku: ${e.message}")
            false
        }
    }

    fun openAppSettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", context.packageName, null)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open app settings: ${e.message}")
        }
    }

    fun onTermuxPermissionResult(context: Context, granted: Boolean) {
        val path = pendingTermuxPath
        val customCmd = pendingTermuxCommand
        pendingTermuxPath = null
        pendingTermuxCommand = null
        if (granted) {
            Toast.makeText(
                context,
                context.getString(R.string.termux_permission_granted_toast),
                Toast.LENGTH_SHORT
            ).show()
            if (!path.isNullOrEmpty()) {
                if (!customCmd.isNullOrBlank()) {
                    runCommandInTermux(context, path, customCmd)
                } else {
                    openTermux(context, path)
                }
            }
        } else if (!path.isNullOrEmpty()) {
            val targetFile = File(path)
            val targetDir = if (targetFile.isFile) targetFile.parent ?: path else path
            val escapedDir = targetDir.replace("'", "'\\''")
            val fallbackCmd = if (!customCmd.isNullOrBlank()) {
                "cd '$escapedDir' && $customCmd"
            } else {
                "cd '$escapedDir'"
            }
            fallbackCmd.copyToClipboard()
            Toast.makeText(
                context,
                context.getString(R.string.termux_permission_copy_notice),
                Toast.LENGTH_LONG
            ).show()
            val launchIntent = context.packageManager.getLaunchIntentForPackage(TERMUX_PACKAGE)?.apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (launchIntent != null) {
                context.startActivity(launchIntent)
            }
        }
    }

    fun openTerminal(context: Context, path: String) {
        val prefs = globalClass.preferencesManager
        when (prefs.terminalApp) {
            TerminalAppPreference.TERMUX.ordinal -> {
                openTermux(context, path)
            }
            TerminalAppPreference.CUSTOM.ordinal -> {
                openCustomTerminal(
                    context = context,
                    path = path,
                    packageName = prefs.customTerminalPackage.ifBlank { TERMUX_PACKAGE },
                    commandTemplate = prefs.customTerminalCommand.ifBlank { "cd {dir}" }
                )
            }
            else -> {
                openBuiltInTerminal(context, path)
            }
        }
    }

    fun openBuiltInTerminal(context: Context, path: String) {
        openFolderInTerminal(context, path)
        val intent = Intent(context, TerminalActivity::class.java).apply {
            putExtra("cwd", path)
            if (context !is Activity) {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
        context.startActivity(intent)
    }

    private fun buildTermuxRunCommandIntent(
        packageName: String,
        targetDir: String,
        escapedDir: String,
        customCommand: String? = null
    ): Intent {
        val shellCmd = buildString {
            append("export PREFIX='/data/data/$packageName/files/usr'; ")
            append("export HOME='/data/data/$packageName/files/home'; ")
            append("export TMPDIR='/data/data/$packageName/files/usr/tmp'; ")
            append("export PATH=\"/data/data/$packageName/files/usr/bin:\$PATH\"; ")
            append("export LD_LIBRARY_PATH=\"/data/data/$packageName/files/usr/lib:\$LD_LIBRARY_PATH\"; ")
            append("export PWD='$escapedDir'; cd '$escapedDir' 2>/dev/null; ")
            if (!customCommand.isNullOrBlank()) {
                append("$customCommand; ")
                append("echo ''; ")
            }
            append("if [ -x /data/data/$packageName/files/home/.termux/shell ]; then ")
            append("exec /data/data/$packageName/files/home/.termux/shell -l; ")
            append("elif [ -n \"\$SHELL\" ] && [ -x \"\$SHELL\" ]; then ")
            append("exec \"\$SHELL\" -l; ")
            append("elif [ -x /data/data/$packageName/files/usr/bin/bash ]; then ")
            append("exec /data/data/$packageName/files/usr/bin/bash -l; ")
            append("elif [ -x /data/data/$packageName/files/usr/bin/login ]; then ")
            append("exec /data/data/$packageName/files/usr/bin/login; ")
            append("else exec sh; fi")
        }
        return Intent().apply {
            component = ComponentName(packageName, "$packageName.app.RunCommandService")
            action = "$packageName.RUN_COMMAND"
            putExtra("$packageName.RUN_COMMAND_PATH", "/data/data/$packageName/files/usr/bin/bash")
            putExtra("$packageName.RUN_COMMAND_ARGUMENTS", arrayOf("-c", shellCmd))
            putExtra("$packageName.RUN_COMMAND_WORKDIR", targetDir)
            putExtra("$packageName.RUN_COMMAND_BACKGROUND", false)
            putExtra("$packageName.RUN_COMMAND_SESSION_ACTION", "0")
            putExtra("$packageName.execute.session_action", "0")
        }
    }

    private fun tryStartRunCommandService(context: Context, intent: Intent): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
            true
        } catch (e: Exception) {
            Log.w(TAG, "startForegroundService failed, attempting startService: ${e.message}")
            try {
                context.startService(intent)
                true
            } catch (e2: Exception) {
                Log.w(TAG, "startService failed: ${e2.message}")
                false
            }
        }
    }

    fun openTermux(context: Context, path: String) {
        dispatchToTermux(context, path, customCommand = null)
    }

    /**
     * Executes a custom runner or shell command inside Termux in [workingDir], keeping the
     * interactive Termux session open afterwards (using the same RUN_COMMAND pipeline as `cd`).
     */
    fun runCommandInTermux(context: Context, workingDir: String, command: String) {
        dispatchToTermux(context, workingDir, customCommand = command)
    }

    private fun dispatchToTermux(context: Context, path: String, customCommand: String?) {
        val targetFile = File(path)
        val targetDir = if (targetFile.isFile) targetFile.parent ?: path else path

        if (!isPackageInstalled(context, TERMUX_PACKAGE)) {
            Toast.makeText(
                context,
                context.getString(R.string.termux_not_installed_error),
                Toast.LENGTH_LONG
            ).show()
            try {
                val marketIntent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$TERMUX_PACKAGE")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(marketIntent)
            } catch (_: Exception) {
                val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/termux/termux-app/releases")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(browserIntent)
            }
            return
        }

        val escapedDir = targetDir.replace("'", "'\\''")
        val fallbackClipboardCmd = if (!customCommand.isNullOrBlank()) {
            "cd '$escapedDir' && $customCommand"
        } else {
            "cd '$escapedDir'"
        }

        // Check if RUN_COMMAND permission is granted; try granting via Shizuku if available
        var hasPerm = hasTermuxPermission(context)
        if (!hasPerm) {
            hasPerm = tryGrantPermissionViaShizuku(context)
        }

        if (!hasPerm) {
            val activity = context.findActivity()
            if (activity != null) {
                // Request runtime permission first and wait for user response in BaseActivity.onRequestPermissionsResult
                pendingTermuxPath = targetDir
                pendingTermuxCommand = customCommand
                ActivityCompat.requestPermissions(
                    activity,
                    arrayOf(TERMUX_PERMISSION),
                    TERMUX_PERMISSION_REQUEST_CODE
                )
                return
            }

            // Fallback only when no Activity context is available
            fallbackClipboardCmd.copyToClipboard()
            Toast.makeText(
                context,
                context.getString(R.string.termux_permission_copy_notice),
                Toast.LENGTH_LONG
            ).show()

            val launchIntent = context.packageManager.getLaunchIntentForPackage(TERMUX_PACKAGE)?.apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (launchIntent != null) {
                context.startActivity(launchIntent)
            }
            return
        }

        val runCommandIntent = buildTermuxRunCommandIntent(
            packageName = TERMUX_PACKAGE,
            targetDir = targetDir,
            escapedDir = escapedDir,
            customCommand = customCommand
        )
        val serviceStarted = tryStartRunCommandService(context, runCommandIntent)

        if (serviceStarted) {
            Handler(Looper.getMainLooper()).postDelayed({
                try {
                    val launchIntent = context.packageManager.getLaunchIntentForPackage(TERMUX_PACKAGE)?.apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    }
                    if (launchIntent != null) {
                        context.startActivity(launchIntent)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to bring Termux activity to foreground: ${e.message}")
                }
            }, 200)
        } else {
            // On Android 12+, starting RunCommandService while Termux is in the background can throw
            // BackgroundServiceStartNotAllowedException. Bring TermuxActivity to the foreground FIRST,
            // then dispatch RUN_COMMAND once Termux's process is in the foreground state.
            val launchIntent = context.packageManager.getLaunchIntentForPackage(TERMUX_PACKAGE)?.apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
            if (launchIntent != null) {
                try {
                    context.startActivity(launchIntent)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to launch Termux activity: ${e.message}")
                }
            }

            val mainHandler = Handler(Looper.getMainLooper())
            mainHandler.postDelayed({
                val retryOk = tryStartRunCommandService(context.applicationContext, runCommandIntent)
                if (!retryOk) {
                    mainHandler.postDelayed({
                        val secondRetryOk = tryStartRunCommandService(context.applicationContext, runCommandIntent)
                        if (!secondRetryOk) {
                            fallbackClipboardCmd.copyToClipboard()
                            Toast.makeText(
                                context.applicationContext,
                                context.getString(R.string.termux_permission_copy_notice),
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }, 450)
                }
            }, 350)
        }
    }

    fun openCustomTerminal(
        context: Context,
        path: String,
        packageName: String,
        commandTemplate: String,
        customRunnerCommand: String? = null
    ) {
        val targetFile = File(path)
        val targetDir = if (targetFile.isFile) targetFile.parent ?: path else path
        val escapedDir = targetDir.replace("'", "'\\''")
        val baseCommand = commandTemplate
            .replace("{dir}", escapedDir)
            .replace("{path}", escapedDir)
            .replace("{file}", targetFile.name)
        val command = if (!customRunnerCommand.isNullOrBlank()) {
            "cd '$escapedDir' && $customRunnerCommand"
        } else {
            baseCommand
        }

        if (!isPackageInstalled(context, packageName)) {
            Toast.makeText(
                context,
                context.getString(R.string.terminal_package_not_installed, packageName),
                Toast.LENGTH_LONG
            ).show()
            return
        }

        if (packageName == "jackpal.androidterm") {
            try {
                val termIntent = Intent("jackpal.androidterm.RUN_SCRIPT").apply {
                    setPackage(packageName)
                    putExtra("jackpal.androidterm.iInitialCommand", "$command\n")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(termIntent)
                return
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start jackpal terminal script: ${e.message}")
            }
        }

        if (packageName.contains("termux", ignoreCase = true)) {
            val runIntent = buildTermuxRunCommandIntent(
                packageName = packageName,
                targetDir = targetDir,
                escapedDir = escapedDir,
                customCommand = customRunnerCommand
            )
            val started = tryStartRunCommandService(context, runIntent)
            if (started) {
                Handler(Looper.getMainLooper()).postDelayed({
                    val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)?.apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    }
                    if (launchIntent != null) {
                        context.startActivity(launchIntent)
                    }
                }, 200)
                return
            } else {
                val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)?.apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                }
                if (launchIntent != null) {
                    runCatching { context.startActivity(launchIntent) }
                }
                Handler(Looper.getMainLooper()).postDelayed({
                    if (!tryStartRunCommandService(context.applicationContext, runIntent)) {
                        command.copyToClipboard()
                        Toast.makeText(
                            context.applicationContext,
                            context.getString(R.string.termux_permission_copy_notice),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }, 400)
                return
            }
        }

        command.copyToClipboard()
        Toast.makeText(
            context,
            context.getString(R.string.termux_permission_copy_notice),
            Toast.LENGTH_SHORT
        ).show()
        val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)?.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (launchIntent != null) {
            context.startActivity(launchIntent)
        }
    }
}
