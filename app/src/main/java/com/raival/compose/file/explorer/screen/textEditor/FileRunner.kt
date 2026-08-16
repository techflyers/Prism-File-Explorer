package com.raival.compose.file.explorer.screen.textEditor

import android.content.Context
import android.content.Intent
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.raival.compose.file.explorer.App.Companion.globalClass
import com.raival.compose.file.explorer.R
import com.raival.compose.file.explorer.screen.main.tab.files.holder.LocalFileHolder
import com.raival.compose.file.explorer.screen.terminal.TerminalActivity
import com.raival.compose.file.explorer.screen.terminal.TerminalCommand
import com.raival.compose.file.explorer.screen.terminal.pendingTerminalCommand
import com.raival.compose.file.explorer.screen.viewer.html.HtmlLivePreviewActivity
import com.raival.compose.file.explorer.screen.viewer.html.LocalHtmlPreviewServer
import java.io.File

/**
 * Xed-style "run current file" helpers: HTML via a local server, source files
 * via the Ubuntu terminal runtime.
 */
object FileRunner {
    fun isRunnable(file: File): Boolean = commandFor(file) != null || isHtml(file)

    fun isHtml(file: File): Boolean {
        val ext = file.extension.lowercase()
        return ext == "html" || ext == "htm"
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

    fun run(context: Context, file: File) {
        if (!file.exists()) {
            globalClass.showMsg(R.string.file_not_found)
            return
        }
        if (isHtml(file)) {
            runHtml(context, file)
            return
        }
        val command = commandFor(file)
        if (command == null) {
            globalClass.showMsg(R.string.cannot_run_file)
            return
        }
        // Use normalized path for terminal working directory
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

    private fun commandFor(file: File): String? {
        // Normalize the path for terminal use
        val normalizedPath = normalizePathForTerminal(file.absolutePath)
        val path = shellQuote(normalizedPath)
        val normalizedParent = File(normalizedPath).parent ?: "."

        // Check custom runners first (user-defined overrides)
        val customRunners = getCustomRunners()
        val ext = file.extension.lowercase()
        customRunners[ext]?.let { template ->
            return template
                .replace("{file}", path)
                .replace("{dir}", shellQuote(normalizedParent))
                .replace("{name}", file.nameWithoutExtension)
        }

        // Built-in runners
        return when (ext) {
            "py" -> "python3 $path"
            "sh", "bash" -> "bash $path"
            "js" -> "node $path"
            "ts" -> "npx --yes ts-node $path"
            "rb" -> "ruby $path"
            "php" -> "php $path"
            "pl" -> "perl $path"
            "lua" -> "lua $path"
            "r" -> "Rscript $path"
            "go" -> "go run $path"
            "rs" -> "rustc $path -o /tmp/${file.nameWithoutExtension} && /tmp/${file.nameWithoutExtension}"
            "c" -> "cc $path -o /tmp/${file.nameWithoutExtension} && /tmp/${file.nameWithoutExtension}"
            "cpp", "cc", "cxx" -> "c++ $path -o /tmp/${file.nameWithoutExtension} && /tmp/${file.nameWithoutExtension}"
            "java" -> "javac $path && java -cp ${shellQuote(normalizedParent)} ${file.nameWithoutExtension}"
            else -> null
        }
    }

    private fun shellQuote(value: String): String =
        "'" + value.replace("'", "'\"'\"'") + "'"
}

