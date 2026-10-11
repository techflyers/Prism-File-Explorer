package com.techflyers.compose.file.explorer.screen.viewer.document.core

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.net.Uri
import android.util.Size
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

interface PdfViewer {
    fun open(context: Context, uri: Uri): PdfDocument
    fun renderPage(document: PdfDocument, pageIndex: Int, width: Int, rotationDegrees: Int = 0): Bitmap?
    fun pageSize(document: PdfDocument, pageIndex: Int): Size?
    fun close(document: PdfDocument)
}

class PdfViewerImpl : PdfViewer {

    private val lock = ReentrantLock()
    private val renderers = HashMap<Uri, android.graphics.pdf.PdfRenderer>()
    private val fds = HashMap<Uri, android.os.ParcelFileDescriptor>()

    override fun open(context: Context, uri: Uri): PdfDocument = lock.withLock {
        renderers.remove(uri)?.runCatching { close() }
        fds.remove(uri)?.runCatching { close() }

        val fd = context.contentResolver.openFileDescriptor(uri, "r")
            ?: throw IllegalArgumentException("Cannot open file: $uri")
        val renderer = android.graphics.pdf.PdfRenderer(fd)
        fds[uri] = fd
        renderers[uri] = renderer

        PdfDocument(
            uri = uri,
            name = queryFileName(context, uri) ?: "Unknown.pdf",
            pageCount = renderer.pageCount,
            sizeBytes = fd.statSize
        )
    }

    override fun renderPage(document: PdfDocument, pageIndex: Int, width: Int, rotationDegrees: Int): Bitmap? =
        lock.withLock {
            val renderer = renderers[document.uri] ?: return null
            if (pageIndex < 0 || pageIndex >= renderer.pageCount) return null

            val page = try {
                renderer.openPage(pageIndex)
            } catch (t: Throwable) {
                return@withLock null
            }
            var bitmap: Bitmap? = null
            try {
                val rotation = ((rotationDegrees % 360) + 360) % 360
                val swap = rotation == 90 || rotation == 270
                val srcW = page.width.toFloat().coerceAtLeast(1f)
                val srcH = page.height.toFloat().coerceAtLeast(1f)

                val dispW = if (swap) srcH else srcW
                val dispH = if (swap) srcW else srcH

                val targetW = width.coerceAtLeast(1)
                val targetH = (targetW * (dispH / dispW)).toInt().coerceAtLeast(1)

                bitmap = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(android.graphics.Color.WHITE)

                val matrix = Matrix().apply {
                    if (rotation != 0) {
                        postRotate(rotation.toFloat())
                        when (rotation) {
                            90 -> postTranslate(srcH, 0f)
                            180 -> postTranslate(srcW, srcH)
                            270 -> postTranslate(0f, srcW)
                        }
                    }
                    postScale(targetW / dispW, targetH / dispH)
                }

                page.render(bitmap, null, matrix, android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                bitmap
            } catch (t: Throwable) {
                bitmap?.recycle()
                null
            } finally {
                runCatching { page.close() }
            }
        }

    override fun pageSize(document: PdfDocument, pageIndex: Int): Size? = lock.withLock {
        val renderer = renderers[document.uri] ?: return null
        if (pageIndex < 0 || pageIndex >= renderer.pageCount) return null
        val page = try {
            renderer.openPage(pageIndex)
        } catch (_: Throwable) {
            return null
        }
        try {
            Size(page.width, page.height)
        } finally {
            runCatching { page.close() }
        }
    }

    override fun close(document: PdfDocument) = lock.withLock {
        renderers.remove(document.uri)?.runCatching { close() }
        fds.remove(document.uri)?.runCatching { close() }
        Unit
    }

    private fun queryFileName(context: Context, uri: Uri): String? {
        return try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) cursor.getString(idx) else null
                } else null
            }
        } catch (_: Exception) {
            null
        }
    }
}
