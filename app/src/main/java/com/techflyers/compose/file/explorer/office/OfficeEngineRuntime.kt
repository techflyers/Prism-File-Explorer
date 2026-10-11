package com.techflyers.compose.file.explorer.office

import android.content.Context
import android.net.Uri
import org.libreoffice.kit.LibreOfficeKit
import org.libreoffice.kit.Office
import java.io.File

/**
 * LibreOfficeKit inside the `:office_service` process.
 * Calls to native LibreOfficeKit are isolated here to protect the main app.
 */
internal object OfficeEngineRuntime {

    private var office: Office? = null

    class ConversionException(message: String, val needsPassword: Boolean = false) : Exception(message)

    private fun ensureStarted(context: Context): Office {
        office?.let { return it }
        val app = context.applicationContext
        val engineDir = OfficeEngineStore.installedDir(app)
            ?: throw ConversionException("Office engine is not installed")
        val lib = File(engineDir, "lib")

        OfficeEngineRelocation.pinAlias(engineDir)
        System.load(File(lib, "libc++_shared.so").absolutePath)
        System.load(File(lib, "liblo-native-code.so").absolutePath)

        val tmp = File(app.cacheDir, "office-tmp").apply { mkdirs() }
        configureFonts(app, engineDir)
        LibreOfficeKit.putenv("SAL_LOG=-WARN-INFO")
        LibreOfficeKit.putenv("SAL_LOK_OPTIONS=compact_fonts")
        LibreOfficeKit.putenv("TMPDIR=${tmp.absolutePath}")
        LibreOfficeKit.redirectStdio(true)
        val ok = LibreOfficeKit.initializeNative(
            engineDir.absolutePath,
            tmp.absolutePath,
            app.packageResourcePath,
            app.assets
        )
        if (!ok) throw ConversionException("Office engine failed to initialise")
        val handle = LibreOfficeKit.getLibreOfficeKitHandle()
            ?: throw ConversionException("Office engine failed to initialise")
        return Office(handle).also { office = it }
    }

    private fun configureFonts(app: Context, engineDir: File) {
        val fontsDir = File(engineDir, "etc/fonts").apply { mkdirs() }
        val cache = File(app.cacheDir, "fontconfig").apply { mkdirs() }
        val home = File(app.filesDir, "office-home").apply { mkdirs() }
        val dirs = listOf("/system/fonts", "/product/fonts", "/system/product/fonts") +
            listOf("user/fonts", "share/fonts", "share/fonts/truetype").map { File(engineDir, it) }
                .filter { it.isDirectory }.map { it.absolutePath }
        fun alias(from: String, to: String) =
            "  <alias binding=\"same\"><family>$from</family><prefer><family>$to</family></prefer></alias>\n"
        val conf = buildString {
            append("<?xml version=\"1.0\"?>\n<!DOCTYPE fontconfig SYSTEM \"fonts.dtd\">\n<fontconfig>\n")
            dirs.forEach { append("  <dir>").append(it).append("</dir>\n") }
            append("  <cachedir>").append(cache.absolutePath).append("</cachedir>\n")
            append(alias("Calibri", "Carlito"))
            append(alias("Cambria", "Caladea"))
            append(alias("Arial", "Liberation Sans"))
            append(alias("Helvetica", "Liberation Sans"))
            append(alias("Arial Narrow", "Liberation Sans Narrow"))
            append(alias("Times New Roman", "Liberation Serif"))
            append(alias("Times", "Liberation Serif"))
            append(alias("Courier New", "Liberation Mono"))
            append(alias("Courier", "Liberation Mono"))
            append(alias("sans-serif", "Roboto"))
            append(alias("serif", "Noto Serif"))
            append(alias("monospace", "Droid Sans Mono"))
            append("</fontconfig>\n")
        }
        val confFile = File(fontsDir, "fonts.conf")
        if (!confFile.isFile || confFile.readText() != conf) confFile.writeText(conf)
        val env = listOf(
            "FONTCONFIG_FILE" to confFile.absolutePath,
            "FONTCONFIG_PATH" to fontsDir.absolutePath,
            "XDG_CACHE_HOME" to app.cacheDir.absolutePath,
            "HOME" to home.absolutePath
        )
        for ((k, v) in env) {
            runCatching { android.system.Os.setenv(k, v, true) }
            LibreOfficeKit.putenv("$k=$v")
        }
    }

    fun convertToPdf(context: Context, input: File, output: File, password: String? = null) {
        val app = context.applicationContext
        val office = ensureStarted(app)
        val inUrl = Uri.fromFile(input).toString()
        val outUrl = Uri.fromFile(output).toString()

        var requestedPassword = false
        office.setOptionalFeatures(Office.FEATURE_DOCUMENT_PASSWORD)
        office.callback = { type, _ ->
            if (type == Office.LOK_CALLBACK_DOCUMENT_PASSWORD) {
                requestedPassword = true
                office.setDocumentPassword(inUrl, password)
            }
        }
        val doc = try {
            office.documentLoad(inUrl)
        } finally {
            office.callback = null
        }
        if (doc == null) {
            if (requestedPassword && password == null) throw ConversionException("Password required", needsPassword = true)
            if (requestedPassword) throw ConversionException("Incorrect password", needsPassword = true)
            throw ConversionException(office.getError() ?: "Failed to open document")
        }
        try {
            output.parentFile?.mkdirs()
            output.delete()
            doc.saveAs(outUrl, "pdf", "")
            if (!output.isFile || output.length() == 0L) {
                throw ConversionException(office.getError() ?: "Failed to export PDF")
            }
        } finally {
            doc.destroy()
        }
    }
}
