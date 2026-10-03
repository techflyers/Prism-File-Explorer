package com.techflyers.compose.file.explorer.screen.terminal

import android.app.Activity
import android.content.ComponentName
import android.content.Context
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

    fun openTermux(context: Context, path: String) {
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
        val cdCmd = "cd '$escapedDir'"

        // Check if RUN_COMMAND permission is granted; try granting via Shizuku if available
        var hasPerm = hasTermuxPermission(context)
        if (!hasPerm) {
            hasPerm = tryGrantPermissionViaShizuku(context)
        }

        if (!hasPerm) {
            // Request permission via system prompt if context is an Activity
            if (context is Activity) {
                ActivityCompat.requestPermissions(context, arrayOf(TERMUX_PERMISSION), 1001)
            }

            // Fallback: copy command to clipboard and launch Termux with instructions
            cdCmd.copyToClipboard()
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

        // Termux RUN_COMMAND intent:
        // Execute bash to change directory and then seamlessly launch Termux's login shell.
        // NOTE: We deliberately do NOT put com.termux.RUN_COMMAND_WORKDIR!
        // Termux enforces a strict directory whitelist for EXTRA_WORKDIR and fails on external
        // storage or SD cards without a trailing slash. By doing `cd` inside the command itself,
        // it reliably works for any directory path.
        val runCommandIntent = Intent().apply {
            component = ComponentName(TERMUX_PACKAGE, TERMUX_RUN_COMMAND_SERVICE)
            action = TERMUX_RUN_COMMAND_ACTION
            putExtra("com.termux.RUN_COMMAND_PATH", "/data/data/com.termux/files/usr/bin/bash")
            putExtra(
                "com.termux.RUN_COMMAND_ARGUMENTS",
                arrayOf(
                    "-c",
                    "cd '$escapedDir' 2>/dev/null; [ -x /data/data/com.termux/files/usr/bin/login ] && exec /data/data/com.termux/files/usr/bin/login || exec bash -l"
                )
            )
            putExtra("com.termux.RUN_COMMAND_BACKGROUND", false)
            putExtra("com.termux.RUN_COMMAND_SESSION_ACTION", "0")
            putExtra("com.termux.execute.session_action", "0")
        }

        var serviceStarted = false
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(runCommandIntent)
            } else {
                context.startService(runCommandIntent)
            }
            serviceStarted = true
        } catch (e: Exception) {
            Log.w(TAG, "startForegroundService failed, attempting startService: ${e.message}")
            try {
                context.startService(runCommandIntent)
                serviceStarted = true
            } catch (e2: Exception) {
                Log.e(TAG, "Failed to send RUN_COMMAND to Termux: ${e2.message}")
            }
        }

        if (serviceStarted) {
            // Give RunCommandService ~200ms to register the new terminal session before bringing
            // TermuxActivity to the foreground, preventing race conditions on Android 10+.
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
            // If service start failed, fall back to copying cd command to clipboard
            cdCmd.copyToClipboard()
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

    fun openCustomTerminal(
        context: Context,
        path: String,
        packageName: String,
        commandTemplate: String
    ) {
        val targetFile = File(path)
        val targetDir = if (targetFile.isFile) targetFile.parent ?: path else path
        val escapedDir = targetDir.replace("'", "'\\''")
        val command = commandTemplate
            .replace("{dir}", escapedDir)
            .replace("{path}", escapedDir)
            .replace("{file}", targetFile.name)

        if (!isPackageInstalled(context, packageName)) {
            Toast.makeText(
                context,
                context.getString(R.string.terminal_package_not_installed, packageName),
                Toast.LENGTH_LONG
            ).show()
            return
        }

        // Handle Jackpal Android Terminal Emulator: directly execute script
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

        // Handle Termux or forks (e.g., com.termux, com.termux.nix)
        if (packageName.contains("termux", ignoreCase = true)) {
            val runIntent = Intent().apply {
                component = ComponentName(packageName, "$packageName.app.RunCommandService")
                action = "$packageName.RUN_COMMAND"
                putExtra("$packageName.RUN_COMMAND_PATH", "/data/data/$packageName/files/usr/bin/bash")
                putExtra(
                    "$packageName.RUN_COMMAND_ARGUMENTS",
                    arrayOf(
                        "-c",
                        "cd '$escapedDir' 2>/dev/null; [ -x /data/data/$packageName/files/usr/bin/login ] && exec /data/data/$packageName/files/usr/bin/login || exec bash -l"
                    )
                )
                putExtra("$packageName.RUN_COMMAND_BACKGROUND", false)
                putExtra("$packageName.RUN_COMMAND_SESSION_ACTION", "0")
                putExtra("$packageName.execute.session_action", "0")
            }
            var started = false
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(runIntent)
                } else {
                    context.startService(runIntent)
                }
                started = true
            } catch (e: Exception) {
                try {
                    context.startService(runIntent)
                    started = true
                } catch (e2: Exception) {
                    Log.e(TAG, "Failed to send RUN_COMMAND to $packageName: ${e2.message}")
                }
            }
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
            }
        }

        // Fallback for other custom terminal apps: copy command to clipboard and launch
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
