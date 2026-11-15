package com.techflyers.compose.file.explorer.coil.apk

import coil3.ImageLoader
import coil3.asImage
import coil3.decode.DecodeResult
import coil3.decode.Decoder
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import com.techflyers.compose.file.explorer.App.Companion.globalClass
import com.techflyers.compose.file.explorer.common.drawableToBitmap
import com.techflyers.compose.file.explorer.screen.main.tab.files.misc.FileMimeType.apkBundleFileType
import com.techflyers.compose.file.explorer.screen.main.tab.files.misc.FileMimeType.apkFileType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class ApkFileDecoder(
    private val source: File,
) : Decoder {
    override suspend fun decode(): DecodeResult? = withContext(Dispatchers.IO) {
        val ext = source.extension.lowercase()
        if (apkBundleFileType.contains(ext)) {
            ApkBundleHelper.loadBundleIconBitmap(source)?.let { bitmap ->
                return@withContext DecodeResult(bitmap.asImage(), false)
            }
            return@withContext null
        }

        val packageManager = globalClass.packageManager
        packageManager.getPackageArchiveInfo(source.absolutePath, 0)?.let { apkInfo ->
            apkInfo.applicationInfo?.apply {
                sourceDir = source.absolutePath
                publicSourceDir = source.absolutePath
                loadIcon(packageManager).drawableToBitmap()?.let { bitmap ->
                    return@withContext DecodeResult(bitmap.asImage(), false)
                }
            }
        }
        null
    }

    class Factory : Decoder.Factory {
        override fun create(
            result: SourceFetchResult,
            options: Options,
            imageLoader: ImageLoader
        ): Decoder? {
            val file = result.source.file().toFile()
            val ext = file.extension.lowercase()
            if (file.exists() && (ext == apkFileType || apkBundleFileType.contains(ext))) {
                return ApkFileDecoder(file)
            }
            return null
        }
    }
}
