package com.raival.compose.file.explorer.screen.main.tab.files.search.ai

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Abstract interface for Optical Character Recognition (OCR).
 *
 * Ported from fileai/lib/src/file_discovery.dart → OcrEngine.
 * Implement this interface or use [MlKitOcrEngine] to extract text
 * from image files and scanned PDFs.
 */
interface OcrEngine {
    /**
     * Extract text from an image file (e.g. PNG, JPG, WEBP).
     */
    suspend fun extractTextFromImage(imageFile: File): String

    /**
     * Extract text from a scanned (image-only) PDF file.
     * Returns empty string if the PDF cannot be OCR'd.
     */
    suspend fun extractTextFromPdf(pdfFile: File): String
}

/**
 * Implementation of [OcrEngine] powered by Google ML Kit Text Recognition.
 *
 * Runs fully on-device — no network permission required.
 * The Latin script model is bundled automatically with the ML Kit dependency.
 *
 * For PDFs, each page is rasterized via Android's [PdfRenderer] (requires API 21+,
 * within Prism's minSdk = 26), then OCR'd per page and the results are joined.
 *
 * Ported from fileai/lib/src/file_discovery.dart → MlKitOcrEngine.
 */
class MlKitOcrEngine : OcrEngine {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    /**
     * OCR a single image file using ML Kit.
     */
    override suspend fun extractTextFromImage(imageFile: File): String =
        withContext(Dispatchers.IO) {
            try {
                val inputImage = InputImage.fromFilePath(
                    com.raival.compose.file.explorer.App.globalClass,
                    android.net.Uri.fromFile(imageFile)
                )
                recognizeText(inputImage)
            } catch (_: Exception) {
                ""
            }
        }

    /**
     * OCR a scanned PDF by rasterizing each page with [PdfRenderer] and
     * running ML Kit on each page bitmap. Pages are concatenated with newlines.
     *
     * Skips pages that fail to render; returns concatenated text.
     */
    override suspend fun extractTextFromPdf(pdfFile: File): String =
        withContext(Dispatchers.IO) {
            val sb = StringBuilder()
            try {
                val fd = ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY)
                fd.use {
                    val renderer = PdfRenderer(fd)
                    renderer.use {
                        val pageCount = renderer.pageCount
                        for (i in 0 until pageCount) {
                            try {
                                val page = renderer.openPage(i)
                                page.use {
                                    // Render at 2× scale (≈150 DPI) for good OCR quality
                                    val scale = 2
                                    val width = page.width * scale
                                    val height = page.height * scale
                                    val bitmap = Bitmap.createBitmap(
                                        width, height, Bitmap.Config.ARGB_8888
                                    )
                                    // Fill white background so ML Kit sees text correctly
                                    bitmap.eraseColor(Color.WHITE)
                                    page.render(
                                        bitmap,
                                        null,
                                        null,
                                        PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY
                                    )
                                    val inputImage = InputImage.fromBitmap(bitmap, 0)
                                    val pageText = recognizeText(inputImage)
                                    if (pageText.isNotBlank()) {
                                        sb.append(pageText).append('\n')
                                    }
                                    bitmap.recycle()
                                }
                            } catch (_: Exception) {
                                // Skip pages that fail to render
                            }
                        }
                    }
                }
            } catch (_: Exception) {
                // If PDF can't be opened for rendering, return what was collected
            }
            sb.toString()
        }

    /**
     * Suspend wrapper around ML Kit's callback-based [TextRecognizer.process].
     */
    private suspend fun recognizeText(inputImage: InputImage): String =
        suspendCancellableCoroutine { cont ->
            recognizer.process(inputImage)
                .addOnSuccessListener { result -> cont.resume(result.text) }
                .addOnFailureListener { e -> cont.resumeWithException(e) }
        }

    /**
     * Release ML Kit resources. Call when the engine is no longer needed.
     */
    fun close() {
        recognizer.close()
    }
}
