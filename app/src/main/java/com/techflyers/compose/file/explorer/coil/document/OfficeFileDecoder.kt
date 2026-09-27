package com.techflyers.compose.file.explorer.coil.document

import android.graphics.BitmapFactory
import coil3.ImageLoader
import coil3.asImage
import coil3.decode.DecodeResult
import coil3.decode.Decoder
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import com.techflyers.compose.file.explorer.screen.main.tab.files.misc.FileMimeType
import java.io.File
import java.util.zip.ZipFile

class OfficeFileDecoder(private val source: File) : Decoder {
    override suspend fun decode(): DecodeResult? {
        val bitmap = extractFirstEmbeddedImage(source) ?: return null
        return DecodeResult(bitmap.asImage(), false)
    }

    class Factory : Decoder.Factory {
        override fun create(
            result: SourceFetchResult,
            options: Options,
            imageLoader: ImageLoader
        ): Decoder? {
            val file = runCatching { result.source.file().toFile() }.getOrNull() ?: return null
            val ext = file.extension.lowercase()
            if (file.exists() && ext in FileMimeType.officeFileType) {
                return OfficeFileDecoder(file)
            }
            return null
        }
    }

    companion object {
        private val imageEntryRegex = Regex(
            """(word|ppt|xl)/media/.+\.(png|jpe?g|webp|gif)$""",
            RegexOption.IGNORE_CASE
        )

        fun extractFirstEmbeddedImage(file: File): android.graphics.Bitmap? {
            return try {
                ZipFile(file).use { zip ->
                    val entry = zip.entries().asSequence()
                        .firstOrNull { !it.isDirectory && imageEntryRegex.matches(it.name) }
                        ?: return null
                    zip.getInputStream(entry).use { stream ->
                        BitmapFactory.decodeStream(stream)
                    }
                }
            } catch (_: Exception) {
                null
            }
        }
    }
}
