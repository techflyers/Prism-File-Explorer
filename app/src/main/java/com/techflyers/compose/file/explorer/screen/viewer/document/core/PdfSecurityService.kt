package com.techflyers.compose.file.explorer.screen.viewer.document.core

import android.content.Context
import android.net.Uri
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import java.io.File

object PdfSecurityService {

    class PasswordRequiredException : IllegalStateException("PDF password required")

    private fun ensurePdfBox(context: Context) {
        try {
            PDFBoxResourceLoader.init(context.applicationContext)
        } catch (_: Exception) {}
    }

    fun isPasswordProtected(context: Context, uri: Uri): Boolean {
        ensurePdfBox(context)
        return try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                PDDocument.load(stream).use { document -> document.isEncrypted }
            } ?: false
        } catch (error: Exception) {
            if (isPasswordError(error)) true else throw error
        }
    }

    fun decryptToCache(context: Context, uri: Uri, password: String): File {
        ensurePdfBox(context)
        val outputDirectory = File(context.cacheDir, "decrypted_pdfs").apply { mkdirs() }
        val output = File(outputDirectory, "unlocked_${System.currentTimeMillis()}.pdf")
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                PDDocument.load(input, password).use { document ->
                    document.setAllSecurityToBeRemoved(true)
                    output.outputStream().use { document.save(it) }
                }
            } ?: throw IllegalStateException("Unable to read PDF")
        } catch (error: Exception) {
            output.delete()
            if (isPasswordError(error)) throw PasswordRequiredException()
            throw error
        }
        return output
    }

    private fun isPasswordError(error: Throwable): Boolean {
        var current: Throwable? = error
        while (current != null) {
            val message = current.message.orEmpty()
            if (current is com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException ||
                message.contains("password", ignoreCase = true) ||
                message.contains("encrypted", ignoreCase = true)
            ) {
                return true
            }
            current = current.cause
        }
        return false
    }
}
