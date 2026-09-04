package com.raival.compose.file.explorer.coil.comic

import android.graphics.BitmapFactory
import coil3.ImageLoader
import coil3.asImage
import coil3.decode.DecodeResult
import coil3.decode.Decoder
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import com.raival.compose.file.explorer.comic.ComicArchiveFactory
import com.raival.compose.file.explorer.screen.main.tab.files.misc.FileMimeType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class ComicCoverDecoder(val source: File) : Decoder {

    override suspend fun decode(): DecodeResult? = withContext(Dispatchers.IO) {
        try {
            ComicArchiveFactory.open(source).use { archive ->
                if (archive.pageCount > 0) {
                    val bytes = archive.imageBytes(0)
                    // Downsample if needed to conserve memory for thumbnails
                    val options = BitmapFactory.Options().apply {
                        inJustDecodeBounds = true
                    }
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)

                    val maxDim = 512
                    var sampleSize = 1
                    while (options.outWidth / (sampleSize * 2) >= maxDim && options.outHeight / (sampleSize * 2) >= maxDim) {
                        sampleSize *= 2
                    }

                    val decodeOptions = BitmapFactory.Options().apply {
                        inSampleSize = sampleSize
                    }
                    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOptions)
                    if (bitmap != null) {
                        return@withContext DecodeResult(bitmap.asImage(), false)
                    }
                }
            }
        } catch (_: Exception) {}
        return@withContext null
    }

    class Factory : Decoder.Factory {
        override fun create(
            result: SourceFetchResult,
            options: Options,
            imageLoader: ImageLoader
        ): Decoder? {
            val file = result.source.file().toFile()
            if (file.exists() && FileMimeType.comicFileType.contains(file.extension.lowercase())) {
                return ComicCoverDecoder(file)
            }
            return null
        }
    }
}
