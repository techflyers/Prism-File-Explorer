package com.raival.compose.file.explorer.screen.terminal

import android.content.Context
import com.raival.compose.file.explorer.App
import java.io.File

fun getPrivateDir(context: Context = App.appContext): File =
    context.filesDir.parentFile!!.also { it.mkdirs() }

fun getCacheDir(context: Context = App.appContext): File =
    context.cacheDir.also { it.mkdirs() }

fun localDir(context: Context = App.appContext): File =
    File(getPrivateDir(context), "local").also { it.mkdirs() }

fun localBinDir(context: Context = App.appContext): File =
    File(localDir(context), "bin").also { it.mkdirs() }

fun localLibDir(context: Context = App.appContext): File =
    File(localDir(context), "lib").also { it.mkdirs() }

fun sandboxDir(context: Context = App.appContext): File =
    File(localDir(context), "sandbox").also { it.mkdirs() }

fun sandboxHomeDir(context: Context = App.appContext): File =
    File(localDir(context), "home").also { it.mkdirs() }

fun getTempDir(context: Context = App.appContext): File {
    val tmp = File(context.filesDir.parentFile, "tmp")
    if (!tmp.exists()) tmp.mkdir()
    return tmp
}

/** Returns true if the Ubuntu rootfs is set up and extraction marker exists. */
fun isTerminalInstalled(context: Context = App.appContext): Boolean {
    val rootfsFiles = sandboxDir(context).listFiles()?.filter {
        it.absolutePath != sandboxHomeDir(context).absolutePath &&
            it.absolutePath != File(sandboxDir(context), "tmp").absolutePath
    } ?: emptyList()
    return File(localDir(context), ".terminal_setup_ok_DO_NOT_REMOVE").exists() &&
        rootfsFiles.isNotEmpty()
}

fun File.child(name: String): File = File(this, name)

fun File.createFileIfNot(): File {
    if (!exists()) {
        parentFile?.mkdirs()
        createNewFile()
    }
    return this
}

fun File.createDirIfNot(): File {
    if (!exists()) mkdirs()
    return this
}
