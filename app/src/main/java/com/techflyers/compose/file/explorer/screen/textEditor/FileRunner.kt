package com.techflyers.compose.file.explorer.screen.textEditor

import android.content.Context
import android.content.Intent
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.techflyers.compose.file.explorer.App.Companion.globalClass
import com.techflyers.compose.file.explorer.R
import com.techflyers.compose.file.explorer.screen.main.tab.files.holder.LocalFileHolder
import com.techflyers.compose.file.explorer.screen.preferences.constant.TerminalAppPreference
import com.techflyers.compose.file.explorer.screen.terminal.TerminalActivity
import com.techflyers.compose.file.explorer.screen.terminal.TerminalCommand
import com.techflyers.compose.file.explorer.screen.terminal.TerminalLauncher
import com.techflyers.compose.file.explorer.screen.terminal.pendingTerminalCommand
import com.techflyers.compose.file.explorer.screen.viewer.html.HtmlLivePreviewActivity
import com.techflyers.compose.file.explorer.screen.viewer.html.LocalHtmlPreviewServer
import java.io.File

/**
 * Xed-style "run current file" helpers: HTML via a local server, source files
 * via the configured terminal app (Built-in Ubuntu proot terminal, Termux via RUN_COMMAND,
 * or Custom terminal).
 */
object FileRunner {
    fun isRunnable(file: File): Boolean = commandFor(file, forTermux = false) != null || isHtml(file)

    fun isHtml(file: File): Boolean {
        val ext = file.extension.lowercase()
        return ext == "html" || ext == "htm"
    }

    fun getRunnerLabel(file: File): String {
        if (isHtml(file)) return "HTML Live Preview"
        val prefs = globalClass.preferencesManager
        return when (prefs.terminalApp) {
            TerminalAppPreference.TERMUX.ordinal -> "Termux"
            TerminalAppPreference.CUSTOM.ordinal ->
                prefs.customTerminalPackage.ifBlank { "Custom Terminal" }
            else -> "Built-in Terminal"
        }
    }

    /**
     * Normalizes a native Android file path for use inside the proot sandbox.
     * /storage/emulated/0/... → /sdcard/... since proot binds /sdcard directly.
     */
    private fun normalizePathForTerminal(path: String): String {
        return path
            .replace("/storage/emulated/0/", "/sdcard/")
            .replace("/storage/emulated/0", "/sdcard")
    }

    fun runFile(context: Context, file: File) = run(context, file)

    fun run(context: Context, file: File) {
        if (!file.exists()) {
            globalClass.showMsg(R.string.file_not_found)
            return
        }
        if (isHtml(file)) {
            runHtml(context, file)
            return
        }

        val prefs = globalClass.preferencesManager
        val useExternalTermux = prefs.terminalApp == TerminalAppPreference.TERMUX.ordinal ||
            prefs.terminalApp == TerminalAppPreference.CUSTOM.ordinal

        val command = commandFor(file, forTermux = useExternalTermux)
        if (command == null) {
            globalClass.showMsg(R.string.cannot_run_file)
            return
        }

        when (prefs.terminalApp) {
            TerminalAppPreference.TERMUX.ordinal -> {
                val workingDir = file.parentFile?.absolutePath ?: file.absolutePath
                TerminalLauncher.runCommandInTermux(context, workingDir, command)
            }
            TerminalAppPreference.CUSTOM.ordinal -> {
                val workingDir = file.parentFile?.absolutePath ?: file.absolutePath
                TerminalLauncher.openCustomTerminal(
                    context = context,
                    path = workingDir,
                    packageName = prefs.customTerminalPackage.ifBlank { TerminalLauncher.TERMUX_PACKAGE },
                    commandTemplate = prefs.customTerminalCommand.ifBlank { "cd {dir}" },
                    customRunnerCommand = command
                )
            }
            else -> {
                // Built-in Ubuntu proot terminal
                val nativePath = normalizePathForTerminal(file.absolutePath)
                val workingDir = File(nativePath).parent ?: nativePath
                val quotedDir = "'" + workingDir.replace("'", "'\"'\"'") + "'"
                pendingTerminalCommand = TerminalCommand(
                    sandbox = true,
                    exe = "/bin/bash",
                    args = arrayOf("-lc", "cd $quotedDir && $command; exec bash"),
                    id = "run-${file.nameWithoutExtension}",
                    workingDir = workingDir
                )
                context.startActivity(
                    Intent(context, TerminalActivity::class.java).apply {
                        putExtra("cwd", workingDir)
                        if (context !is android.app.Activity) {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                    }
                )
            }
        }
    }

    fun run(context: Context, holder: LocalFileHolder) = run(context, holder.file)

    private fun runHtml(context: Context, file: File) {
        val root = file.parentFile ?: file
        val base = LocalHtmlPreviewServer.start(root)
        val url = base + file.name
        context.startActivity(
            Intent(context, HtmlLivePreviewActivity::class.java).apply {
                putExtra(HtmlLivePreviewActivity.EXTRA_URL, url)
                putExtra(HtmlLivePreviewActivity.EXTRA_TITLE, file.name)
                if (context !is android.app.Activity) {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
        )
    }

    /**
     * Returns the custom code runners map from preferences (extension → command template).
     */
    private fun getCustomRunners(): Map<String, String> {
        return try {
            val json = globalClass.preferencesManager.customCodeRunners
            if (json.isBlank() || json == "{}") emptyMap()
            else Gson().fromJson(json, object : TypeToken<Map<String, String>>() {}.type)
        } catch (_: Exception) { emptyMap() }
    }

    private fun commandFor(file: File, forTermux: Boolean = false): String? {
        val targetPath = if (forTermux) file.absolutePath else normalizePathForTerminal(file.absolutePath)
        val path = shellQuote(targetPath)
        val parentDir = File(targetPath).parent ?: "."
        val quotedParent = shellQuote(parentDir)
        val safeBinName = file.nameWithoutExtension.replace(Regex("[^A-Za-z0-9_.-]"), "_").ifBlank { "a.out" }
        val tmpOut = if (forTermux) "\${TMPDIR:-/data/data/com.termux/files/usr/tmp}/$safeBinName" else "/tmp/$safeBinName"

        // Check custom runners first (user-defined overrides)
        val customRunners = getCustomRunners()
        val ext = file.extension.lowercase()
        customRunners[ext]?.takeIf { it.isNotBlank() }?.let { template ->
            return template
                .replace("{file}", path)
                .replace("{path}", path)
                .replace("{dir}", quotedParent)
                .replace("{name}", file.nameWithoutExtension)
        }

        // Built-in runners
        return when (ext) {
            "py" -> "python3 $path"
            "sh", "bash" -> "bash $path"
            "js", "mjs", "cjs" -> "node $path"
            "ts" -> "npx --yes ts-node $path"
            "rb" -> "ruby $path"
            "php" -> "php $path"
            "pl" -> "perl $path"
            "lua" -> "lua $path"
            "r" -> "Rscript $path"
            "go" -> "go run $path"
            "kt", "kts" -> "kotlinc -script $path"
            "rs" -> "rustc $path -o \"$tmpOut\" && chmod +x \"$tmpOut\" && \"$tmpOut\""
            "c" -> "cc $path -o \"$tmpOut\" && chmod +x \"$tmpOut\" && \"$tmpOut\""
            "cpp", "cc", "cxx" -> "c++ $path -o \"$tmpOut\" && chmod +x \"$tmpOut\" && \"$tmpOut\""
            "java" -> "javac $path && java -cp $quotedParent ${file.nameWithoutExtension}"
            else -> null
        }
    }

    private fun shellQuote(value: String): String =
        "'" + value.replace("'", "'\"'\"'") + "'"
}
