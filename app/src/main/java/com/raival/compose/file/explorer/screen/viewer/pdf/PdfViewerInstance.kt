package com.raival.compose.file.explorer.screen.viewer.pdf

import android.net.Uri
import android.text.format.Formatter
import java.io.File
import android.os.ParcelFileDescriptor
import com.anggrayudi.storage.extension.toDocumentFile
import com.raival.compose.file.explorer.App.Companion.globalClass
import com.raival.compose.file.explorer.App.Companion.logger
import com.raival.compose.file.explorer.R
import com.raival.compose.file.explorer.common.name
import com.raival.compose.file.explorer.common.toFormattedDate
import com.raival.compose.file.explorer.screen.viewer.ViewerInstance
import com.raival.compose.file.explorer.screen.viewer.pdf.misc.PdfMetadata
import kotlinx.coroutines.runBlocking
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper

class PdfViewerInstance(
    override val uri: Uri,
    override val id: String
) : ViewerInstance {

    var isPasswordProtected: Boolean = false
        private set

    private var decryptedTempFile: File? = null

    /**
     * The URI to pass to [androidx.pdf.viewer.fragment.PdfViewerFragment].
     * If the document was password-protected and decrypted, this points to the
     * decrypted temp file URI; otherwise it is the original URI.
     */
    var activeUri: Uri = uri
        private set

    var isPasswordChecked: Boolean = false
        private set

    /**
     * Checks password protection in a non-blocking way.
     * Must be called before loading the document.
     */
    suspend fun checkPasswordProtection(): Boolean {
        if (isPasswordChecked) return isPasswordProtected
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                PDFBoxResourceLoader.init(globalClass)
                globalClass.contentResolver.openInputStream(uri)?.use { inputStream ->
                    PDDocument.load(inputStream).use { document ->
                        isPasswordProtected = document.isEncrypted
                    }
                }
            } catch (e: com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException) {
                isPasswordProtected = true
            } catch (e: Exception) {
                if (e.message?.contains("password", ignoreCase = true) == true ||
                    e.cause?.message?.contains("password", ignoreCase = true) == true
                ) {
                    isPasswordProtected = true
                }
            }
            isPasswordChecked = true
            isPasswordProtected
        }
    }

    /**
     * Decrypts the password-protected document into a temp file.
     * On success, [activeUri] is updated to point at the decrypted file.
     */
    fun tryDecrypt(password: String): Boolean {
        try {
            PDFBoxResourceLoader.init(globalClass)
            globalClass.contentResolver.openInputStream(uri)?.use { inputStream ->
                PDDocument.load(inputStream, password).use { document ->
                    if (document.isEncrypted) {
                        document.setAllSecurityToBeRemoved(true)
                    }
                    val tempFile = File(
                        globalClass.cacheDir,
                        "decrypted_${System.currentTimeMillis()}.pdf"
                    )
                    tempFile.outputStream().use { out -> document.save(out) }

                    // Clean up any previous temp file
                    decryptedTempFile?.let { if (it.exists()) it.delete() }
                    decryptedTempFile = tempFile

                    activeUri = Uri.fromFile(tempFile)
                    isPasswordProtected = false
                    return true
                }
            }
        } catch (e: Exception) {
            logger.logError(e)
        }
        return false
    }

    // ── Metadata ──────────────────────────────────────────────────────────────

    val metadata: PdfMetadata by lazy {
        val fileSize = try {
            globalClass.contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize } ?: 0L
        } catch (e: Exception) {
            0L
        }
        PdfMetadata(
            name = uri.name ?: globalClass.getString(R.string.unknown),
            path = uri.toString(),
            size = fileSize,
            lastModified = uri.toDocumentFile(globalClass)?.lastModified() ?: 0L,
            pages = 0  // PdfViewerFragment handles page count display natively
        )
    }

    fun getInfo(): List<Pair<String, String>> = listOf(
        globalClass.getString(R.string.name) to metadata.name,
        globalClass.getString(R.string.size) to Formatter.formatFileSize(globalClass, metadata.size),
        globalClass.getString(R.string.path) to metadata.path,
        globalClass.getString(R.string.last_modified) to metadata.lastModified.toFormattedDate()
    )

    // ── Reflow (text extraction via PDFBox) ───────────────────────────────────

    /**
     * Extracts all text from the PDF for reflow mode display.
     * Uses PDFBox which can read both the original and decrypted temp file.
     */
    fun extractAllText(): String {
        try {
            PDFBoxResourceLoader.init(globalClass)
            // If decrypted, read from the temp file directly (no password needed)
            val inputStream = if (decryptedTempFile != null && decryptedTempFile!!.exists()) {
                decryptedTempFile!!.inputStream()
            } else {
                globalClass.contentResolver.openInputStream(uri)
            }
            inputStream?.use { stream ->
                PDDocument.load(stream).use { document ->
                    val stripper = PDFTextStripper()
                    stripper.sortByPosition = true
                    return stripper.getText(document)
                }
            }
        } catch (e: Exception) {
            logger.logError(e)
            return "Error extracting text: ${e.message}"
        }
        return ""
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onClose() {
        runBlocking {
            try {
                decryptedTempFile?.let { if (it.exists()) it.delete() }
            } catch (e: Exception) {
                logger.logError(e)
            }
        }
    }
}