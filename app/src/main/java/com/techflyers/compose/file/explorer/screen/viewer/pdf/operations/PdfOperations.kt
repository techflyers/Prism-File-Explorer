package com.techflyers.compose.file.explorer.screen.viewer.pdf.operations

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color as AndroidColor
import android.graphics.Matrix as AndroidMatrix
import android.graphics.pdf.PdfDocument as AndroidPdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.pdf.PdfRect
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.color.PDColor
import com.tom_roush.pdfbox.pdmodel.graphics.color.PDDeviceRGB
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import com.tom_roush.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationTextMarkup
import com.tom_roush.pdfbox.util.Matrix
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import kotlin.math.min

data class PdfStrokePoint(val x: Float, val y: Float)

data class PdfStrokeItem(
    val points: List<PdfStrokePoint>,
    val colorArgb: Int,
    val strokeWidth: Float
)

enum class AnnotationType {
    HIGHLIGHT,
    UNDERLINE,
    STRIKETHROUGH
}

data class PdfAnnotationItem(
    val type: AnnotationType,
    val bounds: List<PdfRect>,
    val colorArgb: Int
)

enum class CompressionLevel(val title: String, val scaleFactor: Float, val jpegQuality: Int) {
    HIGH("High Compression (Smallest size)", 0.5f, 30),
    RECOMMENDED("Recommended (Balanced)", 0.7f, 55),
    LOW("Low Compression (High quality)", 0.85f, 80)
}

enum class TargetImageFormat(val extension: String, val mimeType: String, val compressFormat: Bitmap.CompressFormat) {
    JPEG("jpg", "image/jpeg", Bitmap.CompressFormat.JPEG),
    PNG("png", "image/png", Bitmap.CompressFormat.PNG),
    WEBP("webp", "image/webp", if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Bitmap.CompressFormat.WEBP_LOSSY else Bitmap.CompressFormat.WEBP)
}

object PdfOperations {

    private const val TAG = "PdfOperations"
    private var isPdfBoxInitialized = false

    fun ensurePdfBox(context: Context) {
        if (!isPdfBoxInitialized) {
            try {
                PDFBoxResourceLoader.init(context.applicationContext)
                isPdfBoxInitialized = true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize PDFBox", e)
            }
        }
    }

    /**
     * Apply highlights, underlines, and strikethroughs to [targetPdfFile].
     * Both standard PDF annotation markup and visual content stream rendering are applied
     * so that the annotations are permanently and immediately visible across all renderers.
     */
    suspend fun applyAnnotations(
        context: Context,
        targetPdfFile: File,
        annotations: List<PdfAnnotationItem>
    ): Boolean = withContext(Dispatchers.IO) {
        if (annotations.isEmpty()) return@withContext true
        ensurePdfBox(context)
        val tempOut = File(targetPdfFile.parentFile, "${targetPdfFile.name}.tmp_${System.currentTimeMillis()}")
        try {
            FileInputStream(targetPdfFile).use { input ->
                PDDocument.load(input).use { doc ->
                    val pageCount = doc.numberOfPages
                    val annotationsByPage = annotations.flatMap { item ->
                        item.bounds.map { rect -> rect to item }
                    }.groupBy { it.first.pageNum }

                    for ((pageNum, items) in annotationsByPage) {
                        if (pageNum !in 0 until pageCount) continue
                        val page = doc.getPage(pageNum)
                        val boxRect = page.cropBox ?: page.mediaBox ?: continue
                        val pageH = boxRect.height

                        PDPageContentStream(
                            doc,
                            page,
                            PDPageContentStream.AppendMode.APPEND,
                            true,
                            true
                        ).use { cs ->
                            for ((rect, annotation) in items) {
                                val pdfLeft = rect.left
                                val pdfBottom = (pageH - rect.bottom).coerceAtLeast(0f)
                                val pdfWidth = (rect.right - rect.left).coerceAtLeast(1f)
                                val pdfHeight = (rect.bottom - rect.top).coerceAtLeast(1f)

                                val c = AndroidColor.valueOf(annotation.colorArgb)
                                val pdColor = PDColor(floatArrayOf(c.red(), c.green(), c.blue()), PDDeviceRGB.INSTANCE)

                                cs.saveGraphicsState()
                                when (annotation.type) {
                                    AnnotationType.HIGHLIGHT -> {
                                        val gs = PDExtendedGraphicsState().apply {
                                            nonStrokingAlphaConstant = 0.38f
                                        }
                                        cs.setGraphicsStateParameters(gs)
                                        cs.setNonStrokingColor(c.red(), c.green(), c.blue())
                                        cs.addRect(pdfLeft, pdfBottom, pdfWidth, pdfHeight)
                                        cs.fill()

                                        // Standard PDF highlight annotation
                                        val mark = PDAnnotationTextMarkup(PDAnnotationTextMarkup.SUB_TYPE_HIGHLIGHT)
                                        mark.setRectangle(PDRectangle(pdfLeft, pdfBottom, pdfWidth, pdfHeight))
                                        mark.setQuadPoints(floatArrayOf(
                                            pdfLeft, pdfBottom + pdfHeight,
                                            pdfLeft + pdfWidth, pdfBottom + pdfHeight,
                                            pdfLeft, pdfBottom,
                                            pdfLeft + pdfWidth, pdfBottom
                                        ))
                                        mark.setColor(pdColor)
                                        page.annotations.add(mark)
                                    }

                                    AnnotationType.UNDERLINE -> {
                                        val gs = PDExtendedGraphicsState().apply {
                                            strokingAlphaConstant = 0.95f
                                        }
                                        cs.setGraphicsStateParameters(gs)
                                        cs.setStrokingColor(c.red(), c.green(), c.blue())
                                        cs.setLineWidth(1.8f)
                                        cs.moveTo(pdfLeft, pdfBottom + 2f)
                                        cs.lineTo(pdfLeft + pdfWidth, pdfBottom + 2f)
                                        cs.stroke()

                                        // Standard PDF underline annotation
                                        val mark = PDAnnotationTextMarkup(PDAnnotationTextMarkup.SUB_TYPE_UNDERLINE)
                                        mark.setRectangle(PDRectangle(pdfLeft, pdfBottom, pdfWidth, pdfHeight))
                                        mark.setQuadPoints(floatArrayOf(
                                            pdfLeft, pdfBottom + pdfHeight,
                                            pdfLeft + pdfWidth, pdfBottom + pdfHeight,
                                            pdfLeft, pdfBottom,
                                            pdfLeft + pdfWidth, pdfBottom
                                        ))
                                        mark.setColor(pdColor)
                                        page.annotations.add(mark)
                                    }

                                    AnnotationType.STRIKETHROUGH -> {
                                        val gs = PDExtendedGraphicsState().apply {
                                            strokingAlphaConstant = 0.95f
                                        }
                                        cs.setGraphicsStateParameters(gs)
                                        cs.setStrokingColor(c.red(), c.green(), c.blue())
                                        cs.setLineWidth(1.8f)
                                        cs.moveTo(pdfLeft, pdfBottom + pdfHeight * 0.42f)
                                        cs.lineTo(pdfLeft + pdfWidth, pdfBottom + pdfHeight * 0.42f)
                                        cs.stroke()

                                        // Standard PDF strikeout annotation
                                        val mark = PDAnnotationTextMarkup(PDAnnotationTextMarkup.SUB_TYPE_STRIKEOUT)
                                        mark.setRectangle(PDRectangle(pdfLeft, pdfBottom, pdfWidth, pdfHeight))
                                        mark.setQuadPoints(floatArrayOf(
                                            pdfLeft, pdfBottom + pdfHeight,
                                            pdfLeft + pdfWidth, pdfBottom + pdfHeight,
                                            pdfLeft, pdfBottom,
                                            pdfLeft + pdfWidth, pdfBottom
                                        ))
                                        mark.setColor(pdColor)
                                        page.annotations.add(mark)
                                    }
                                }
                                cs.restoreGraphicsState()
                            }
                        }
                    }
                    FileOutputStream(tempOut).use { doc.save(it) }
                }
            }
            if (tempOut.exists() && tempOut.length() > 0) {
                targetPdfFile.delete()
                tempOut.renameTo(targetPdfFile)
                true
            } else {
                tempOut.delete()
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "applyAnnotations error", e)
            tempOut.delete()
            false
        }
    }

    /**
     * Compress PDF by re-sampling raster content to optimized resolution & JPEG quality.
     */
    suspend fun compressPdf(
        context: Context,
        sourceUri: Uri,
        destinationFile: File,
        level: CompressionLevel,
        onProgress: ((done: Int, total: Int) -> Unit)? = null
    ): Boolean = withContext(Dispatchers.IO) {
        val pfd = context.contentResolver.openFileDescriptor(sourceUri, "r") ?: return@withContext false
        pfd.use { descriptor ->
            PdfRenderer(descriptor).use { renderer ->
                val total = renderer.pageCount
                val outDoc = AndroidPdfDocument()
                val matrix = AndroidMatrix()
                val stream = ByteArrayOutputStream(256 * 1024)
                var reusableBitmap: Bitmap? = null

                try {
                    for (i in 0 until total) {
                        renderer.openPage(i).use { page ->
                            val origW = page.width
                            val origH = page.height
                            val w = (origW * level.scaleFactor).toInt().coerceAtLeast(1)
                            val h = (origH * level.scaleFactor).toInt().coerceAtLeast(1)

                            if (reusableBitmap == null || reusableBitmap!!.isRecycled || reusableBitmap!!.width != w || reusableBitmap!!.height != h) {
                                reusableBitmap?.recycle()
                                reusableBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                            }
                            val workingBitmap = reusableBitmap!!
                            workingBitmap.eraseColor(AndroidColor.WHITE)

                            matrix.reset()
                            matrix.setScale(level.scaleFactor, level.scaleFactor)
                            page.render(workingBitmap, null, matrix, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)

                            stream.reset()
                            workingBitmap.compress(Bitmap.CompressFormat.JPEG, level.jpegQuality, stream)
                            val compressedBytes = stream.toByteArray()
                            val compressedBitmap = BitmapFactory.decodeByteArray(compressedBytes, 0, compressedBytes.size)
                                ?: throw IllegalStateException("Failed to compress page ${i + 1}")

                            val pageInfo = AndroidPdfDocument.PageInfo.Builder(origW, origH, i).create()
                            val outPage = outDoc.startPage(pageInfo)
                            val destRect = android.graphics.RectF(0f, 0f, origW.toFloat(), origH.toFloat())
                            outPage.canvas.drawBitmap(compressedBitmap, null, destRect, null)
                            outDoc.finishPage(outPage)
                            compressedBitmap.recycle()
                        }
                        onProgress?.invoke(i + 1, total)
                    }

                    FileOutputStream(destinationFile).use { out ->
                        outDoc.writeTo(out)
                        out.flush()
                    }
                    true
                } catch (e: Exception) {
                    Log.e(TAG, "compressPdf error", e)
                    destinationFile.delete()
                    false
                } finally {
                    reusableBitmap?.recycle()
                    outDoc.close()
                }
            }
        }
    }

    /**
     * Render each PDF page to an image file (JPEG / PNG / WEBP) at the target DPI.
     */
    suspend fun rasterizePdf(
        context: Context,
        sourceUri: Uri,
        format: TargetImageFormat,
        dpi: Int = 150,
        quality: Int = 90,
        onProgress: ((done: Int, total: Int) -> Unit)? = null
    ): List<File> = withContext(Dispatchers.IO) {
        val outDir = File(context.cacheDir, "pdf_raster_${System.currentTimeMillis()}").apply { mkdirs() }
        val outputFiles = mutableListOf<File>()

        val pfd = context.contentResolver.openFileDescriptor(sourceUri, "r") ?: return@withContext emptyList()
        pfd.use { descriptor ->
            PdfRenderer(descriptor).use { renderer ->
                val total = renderer.pageCount
                val scale = dpi / 72f
                for (i in 0 until total) {
                    renderer.openPage(i).use { page ->
                        val w = (page.width * scale).toInt().coerceAtLeast(1)
                        val h = (page.height * scale).toInt().coerceAtLeast(1)
                        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                        bitmap.eraseColor(AndroidColor.WHITE)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                        val file = File(outDir, "page_${i + 1}.${format.extension}")
                        FileOutputStream(file).use { out ->
                            bitmap.compress(format.compressFormat, quality.coerceIn(10, 100), out)
                        }
                        bitmap.recycle()
                        outputFiles.add(file)
                    }
                    onProgress?.invoke(i + 1, total)
                }
            }
        }
        outputFiles
    }

    /**
     * Reorder, rotate, or delete pages while preserving the vector & text fidelity of the document.
     */
    suspend fun organizePages(
        context: Context,
        sourceUri: Uri,
        order: List<Int>,
        rotationByOriginalIndex: Map<Int, Int>,
        destinationFile: File
    ): Boolean = withContext(Dispatchers.IO) {
        if (order.isEmpty()) return@withContext false
        ensurePdfBox(context)
        val outputDoc = PDDocument()
        try {
            val input = context.contentResolver.openInputStream(sourceUri) ?: return@withContext false
            input.use { stream ->
                PDDocument.load(stream).use { doc ->
                    for (orig in order) {
                        if (orig !in 0 until doc.numberOfPages) continue
                        val srcPage = doc.getPage(orig)
                        val imported = outputDoc.importPage(srcPage)
                        val extraRotation = rotationByOriginalIndex[orig] ?: 0
                        if (extraRotation != 0) {
                            imported.rotation = (((srcPage.rotation + extraRotation) % 360) + 360) % 360
                        }
                    }
                    if (outputDoc.numberOfPages == 0) return@withContext false
                    FileOutputStream(destinationFile).use { outputDoc.save(it) }
                }
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "organizePages error", e)
            destinationFile.delete()
            false
        } finally {
            outputDoc.close()
        }
    }

    /**
     * Apply a repeated text watermark on all pages of the PDF.
     */
    suspend fun applyWatermark(
        context: Context,
        sourceUri: Uri,
        destinationFile: File,
        text: String,
        opacity: Float = 0.25f,
        diagonal: Boolean = true,
        colorArgb: Int = 0xFF8A8A8A.toInt()
    ): Boolean = withContext(Dispatchers.IO) {
        val sanitized = text.trim()
        if (sanitized.isEmpty()) return@withContext false
        ensurePdfBox(context)
        val c = AndroidColor.valueOf(colorArgb)
        val alpha = opacity.coerceIn(0.05f, 1f)

        try {
            val input = context.contentResolver.openInputStream(sourceUri) ?: return@withContext false
            input.use { stream ->
                PDDocument.load(stream).use { doc ->
                    val font = PDType1Font.HELVETICA_BOLD
                    for (i in 0 until doc.numberOfPages) {
                        val page = doc.getPage(i)
                        val boxRect = page.cropBox ?: page.mediaBox ?: continue
                        val w = boxRect.width
                        val h = boxRect.height
                        val originX = boxRect.lowerLeftX
                        val originY = boxRect.lowerLeftY

                        val fontSize = (min(w, h) * 0.11f).coerceIn(18f, 120f)
                        val textWidth = font.getStringWidth(sanitized) / 1000f * fontSize
                        val cx = originX + w / 2f
                        val cy = originY + h / 2f

                        PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true).use { cs ->
                            cs.saveGraphicsState()
                            val gs = PDExtendedGraphicsState().apply { nonStrokingAlphaConstant = alpha }
                            cs.setGraphicsStateParameters(gs)
                            cs.setNonStrokingColor(c.red(), c.green(), c.blue())
                            cs.beginText()
                            cs.setFont(font, fontSize)
                            if (diagonal) {
                                cs.setTextMatrix(Matrix.getRotateInstance(Math.toRadians(45.0), cx, cy))
                                cs.newLineAtOffset(-textWidth / 2f, -fontSize / 2.6f)
                            } else {
                                cs.newLineAtOffset(cx - textWidth / 2f, cy - fontSize / 2.6f)
                            }
                            runCatching { cs.showText(sanitized) }
                            cs.endText()
                            cs.restoreGraphicsState()
                        }
                    }
                    FileOutputStream(destinationFile).use { doc.save(it) }
                }
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "applyWatermark error", e)
            destinationFile.delete()
            false
        }
    }

    /**
     * Convert multiple image files into a single PDF document.
     */
    suspend fun imagesToPdf(
        context: Context,
        imageUris: List<Uri>,
        destinationFile: File,
        fitToA4: Boolean = true,
        jpegQuality: Int = 85,
        onProgress: ((done: Int, total: Int) -> Unit)? = null
    ): Boolean = withContext(Dispatchers.IO) {
        if (imageUris.isEmpty()) return@withContext false
        ensurePdfBox(context)
        val doc = PDDocument()
        var pagesAdded = 0
        try {
            imageUris.forEachIndexed { index, uri ->
                val bitmap = decodeBitmap(context, uri) ?: return@forEachIndexed
                try {
                    val pdImage = JPEGFactory.createFromImage(doc, bitmap, jpegQuality / 100f)
                    val page = if (fitToA4) {
                        PDPage(PDRectangle.A4)
                    } else {
                        PDPage(PDRectangle(bitmap.width.toFloat(), bitmap.height.toFloat()))
                    }
                    doc.addPage(page)

                    PDPageContentStream(doc, page).use { content ->
                        val box = page.mediaBox
                        if (fitToA4) {
                            val scale = minOf(box.width / bitmap.width, box.height / bitmap.height)
                            val w = bitmap.width * scale
                            val h = bitmap.height * scale
                            val x = (box.width - w) / 2f
                            val y = (box.height - h) / 2f
                            content.drawImage(pdImage, x, y, w, h)
                        } else {
                            content.drawImage(pdImage, 0f, 0f, box.width, box.height)
                        }
                    }
                    pagesAdded++
                    onProgress?.invoke(index + 1, imageUris.size)
                } finally {
                    bitmap.recycle()
                }
            }

            if (pagesAdded == 0) return@withContext false
            FileOutputStream(destinationFile).use { doc.save(it) }
            true
        } catch (e: Exception) {
            Log.e(TAG, "imagesToPdf error", e)
            destinationFile.delete()
            false
        } finally {
            doc.close()
        }
    }

    /**
     * Convert an image from one format to another (JPEG, PNG, WEBP).
     */
    suspend fun convertImageFormat(
        context: Context,
        sourceUri: Uri,
        destinationFile: File,
        targetFormat: TargetImageFormat,
        quality: Int = 90
    ): Boolean = withContext(Dispatchers.IO) {
        val bitmap = decodeBitmap(context, sourceUri) ?: return@withContext false
        try {
            FileOutputStream(destinationFile).use { out ->
                bitmap.compress(targetFormat.compressFormat, quality.coerceIn(10, 100), out)
                out.flush()
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "convertImageFormat error", e)
            destinationFile.delete()
            false
        } finally {
            bitmap.recycle()
        }
    }

    private fun decodeBitmap(context: Context, uri: Uri): Bitmap? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
        } catch (e: Exception) {
            Log.e(TAG, "decodeBitmap error for uri: $uri", e)
            null
        }
    }

    /**
     * Stamp an image or signature bitmap onto a specific page of [targetPdfFile].
     * Coordinates (pdfX, pdfY) are in standard PDF points from lower-left of the page.
     */
    suspend fun stampBitmap(
        context: Context,
        targetPdfFile: File,
        pageIndex: Int,
        bitmap: Bitmap,
        pdfX: Float,
        pdfY: Float,
        widthPdf: Float,
        heightPdf: Float
    ): Boolean = withContext(Dispatchers.IO) {
        ensurePdfBox(context)
        val tempOut = File(targetPdfFile.parentFile, "${targetPdfFile.name}.tmp_stamp_${System.currentTimeMillis()}")
        try {
            FileInputStream(targetPdfFile).use { input ->
                PDDocument.load(input).use { doc ->
                    if (pageIndex !in 0 until doc.numberOfPages) return@withContext false
                    val page = doc.getPage(pageIndex)
                    val boxRect = page.cropBox ?: page.mediaBox ?: return@withContext false
                    val pageH = boxRect.height
                    val bottomY = (pageH - pdfY - heightPdf).coerceAtLeast(0f)
                    val pdImage = LosslessFactory.createFromImage(doc, bitmap)

                    PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true).use { cs ->
                        cs.drawImage(pdImage, pdfX, bottomY, widthPdf, heightPdf)
                    }
                    FileOutputStream(tempOut).use { doc.save(it) }
                }
            }
            if (tempOut.exists() && tempOut.length() > 0) {
                targetPdfFile.delete()
                tempOut.renameTo(targetPdfFile)
                true
            } else {
                tempOut.delete()
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "stampBitmap error", e)
            tempOut.delete()
            false
        }
    }

    /**
     * Add drawn pen strokes onto a specific page of [targetPdfFile].
     * Coordinates are in standard PDF points from lower-left of the page.
     */
    suspend fun addStrokes(
        context: Context,
        targetPdfFile: File,
        pageIndex: Int,
        strokes: List<PdfStrokeItem>
    ): Boolean = withContext(Dispatchers.IO) {
        if (strokes.isEmpty()) return@withContext true
        ensurePdfBox(context)
        val tempOut = File(targetPdfFile.parentFile, "${targetPdfFile.name}.tmp_strokes_${System.currentTimeMillis()}")
        try {
            FileInputStream(targetPdfFile).use { input ->
                PDDocument.load(input).use { doc ->
                    if (pageIndex !in 0 until doc.numberOfPages) return@withContext false
                    val page = doc.getPage(pageIndex)
                    val boxRect = page.cropBox ?: page.mediaBox ?: return@withContext false
                    val pageH = boxRect.height

                    PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true).use { cs ->
                        for (stroke in strokes) {
                            if (stroke.points.size < 2) continue
                            val c = AndroidColor.valueOf(stroke.colorArgb)
                            cs.saveGraphicsState()
                            val gs = PDExtendedGraphicsState().apply {
                                strokingAlphaConstant = c.alpha()
                            }
                            cs.setGraphicsStateParameters(gs)
                            cs.setStrokingColor(c.red(), c.green(), c.blue())
                            cs.setLineWidth(stroke.strokeWidth)
                            cs.setLineCapStyle(1)  // round cap
                            cs.setLineJoinStyle(1) // round join

                            val p0 = stroke.points.first()
                            val pdfY0 = (pageH - p0.y).coerceAtLeast(0f)
                            cs.moveTo(p0.x, pdfY0)

                            for (i in 1 until stroke.points.size) {
                                val pt = stroke.points[i]
                                val pdfY = (pageH - pt.y).coerceAtLeast(0f)
                                cs.lineTo(pt.x, pdfY)
                            }
                            cs.stroke()
                            cs.restoreGraphicsState()
                        }
                    }
                    FileOutputStream(tempOut).use { doc.save(it) }
                }
            }
            if (tempOut.exists() && tempOut.length() > 0) {
                targetPdfFile.delete()
                tempOut.renameTo(targetPdfFile)
                true
            } else {
                tempOut.delete()
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "addStrokes error", e)
            tempOut.delete()
            false
        }
    }
}
