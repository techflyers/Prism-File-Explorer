package com.techflyers.compose.file.explorer.office

import android.content.Context
import java.io.File
import java.io.IOException

object OfficeEngineStore {

    private const val READY = ".ready"
    private const val CURRENT = "current"

    fun root(context: Context): File = File(context.applicationContext.noBackupFilesDir, "office-engine")

    fun stagingDir(context: Context): File = File(root(context), "staging")

    fun downloadDir(context: Context): File = File(root(context), "download")

    fun installedDir(context: Context): File? {
        val root = root(context)
        val name = runCatching { File(root, CURRENT).readText().trim() }.getOrNull()
            ?.takeIf { it.isNotEmpty() && !it.contains('/') } ?: return null
        val dir = File(root, name)
        val ok = File(dir, READY).isFile &&
            File(dir, "lib/liblo-native-code.so").isFile &&
            File(dir, "lib/libc++_shared.so").isFile &&
            File(dir, "program/fundamentalrc").isFile
        return if (ok) dir else null
    }

    fun isInstalled(context: Context): Boolean = installedDir(context) != null

    fun installedVersion(context: Context): String? =
        installedDir(context)?.let { runCatching { File(it, READY).readText().trim() }.getOrNull() }

    fun sizeOnDisk(context: Context): Long = root(context).walkBottomUp().filter { it.isFile }.sumOf { it.length() }

    fun resetStaging(context: Context): File {
        val staging = stagingDir(context)
        staging.deleteRecursively()
        if (!staging.mkdirs()) throw IOException("Cannot create $staging")
        return staging
    }

    @Synchronized
    fun commit(context: Context, staging: File, abi: String) {
        val lib = File(staging, "lib")
        for (required in listOf("liblo-native-code.so", "libc++_shared.so")) {
            if (!File(lib, required).isFile) throw IOException("Engine bundle is missing $required")
        }
        if (!File(staging, "program/fundamentalrc").isFile) throw IOException("Engine bundle is missing its runtime")
        OfficeEngineRelocation.patchLibraries(lib, OfficeEngineManifest.RELOCATE_FROM)
        File(staging, READY).writeText(OfficeEngineManifest.VERSION)

        val root = root(context)
        val name = "${OfficeEngineManifest.VERSION}-$abi"
        val target = File(root, name)
        if (target.exists()) {
            val trash = File(root, "trash-${System.nanoTime()}")
            if (!target.renameTo(trash)) throw IOException("Cannot replace $target")
        }
        if (!staging.renameTo(target)) throw IOException("Cannot activate $target")
        val tmp = File(root, "$CURRENT.tmp")
        tmp.writeText(name)
        if (!tmp.renameTo(File(root, CURRENT))) throw IOException("Cannot switch engine version")

        root.listFiles()?.forEach { f ->
            if (f.isDirectory && f.name != name && f.name != "download") f.deleteRecursively()
        }
    }

    @Synchronized
    fun delete(context: Context) {
        root(context).deleteRecursively()
    }
}
