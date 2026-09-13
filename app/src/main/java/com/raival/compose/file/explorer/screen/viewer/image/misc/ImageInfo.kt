package com.raival.compose.file.explorer.screen.viewer.image.misc

import android.graphics.BitmapFactory
import android.net.Uri
import com.anggrayudi.storage.extension.toDocumentFile
import com.raival.compose.file.explorer.App.Companion.globalClass
import com.raival.compose.file.explorer.R
import com.raival.compose.file.explorer.common.emptyString
import com.raival.compose.file.explorer.common.toFormattedDate
import com.raival.compose.file.explorer.common.toFormattedSize
import com.raival.compose.file.explorer.screen.viewer.image.ImageViewerActivity
import java.io.File

// Data class for image information
data class ImageInfo(
    val name: String,
    val size: String,
    val dimensions: String,
    val format: String,
    val lastModified: String,
    val path: String
) {
    companion object {
        // Helper function to extract image information
        fun extractImageInfo(
            uri: Uri,
            width: String = "",
            height: String = "",
            explicitPath: String? = null
        ): ImageInfo {
            val resolvedPath = explicitPath?.takeIf { File(it).isFile }
                ?: ImageViewerActivity.resolveFilePath(globalClass, uri)

            val file = resolvedPath?.let { File(it) }
            val docFile = if (file == null || !file.exists()) uri.toDocumentFile(globalClass) else null

            val name = file?.name ?: docFile?.name ?: uri.lastPathSegment ?: emptyString
            val size = (file?.length() ?: docFile?.length() ?: 0L).toFormattedSize()
            val lastModified = (file?.lastModified() ?: docFile?.lastModified() ?: 0L).toFormattedDate()
            val path = file?.absolutePath ?: uri.path.orEmpty()

            val format = file?.extension?.uppercase()?.takeIf { it.isNotEmpty() }
                ?: globalClass.contentResolver.getType(uri)
                    ?.substringAfter("image/", globalClass.getString(R.string.not_available))
                    ?.uppercase()
                ?: globalClass.getString(R.string.not_available)

            val (w, h) = if (width.isNotEmpty() && height.isNotEmpty()) {
                width to height
            } else {
                decodeDimensions(uri, file)
            }

            val dimensions = if (w.isNotEmpty() && h.isNotEmpty()) {
                "$w × $h"
            } else {
                globalClass.getString(R.string.unknown)
            }

            return ImageInfo(
                name = name,
                size = size,
                dimensions = dimensions,
                format = format,
                lastModified = lastModified,
                path = path
            )
        }

        private fun decodeDimensions(uri: Uri, file: File?): Pair<String, String> {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            try {
                if (file != null && file.exists()) {
                    BitmapFactory.decodeFile(file.absolutePath, options)
                } else {
                    globalClass.contentResolver.openInputStream(uri)?.use { stream ->
                        BitmapFactory.decodeStream(stream, null, options)
                    }
                }
                if (options.outWidth > 0 && options.outHeight > 0) {
                    return options.outWidth.toString() to options.outHeight.toString()
                }
            } catch (_: Exception) {
            }
            return "" to ""
        }
    }
}