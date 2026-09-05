package com.raival.compose.file.explorer.screen.textEditor.language

import android.content.Context
import androidx.core.content.pm.PackageInfoCompat
import com.raival.compose.file.explorer.App.Companion.logger
import io.github.rosemoe.sora.langs.textmate.registry.provider.FileResolver
import java.io.File
import java.io.InputStream
import java.util.zip.ZipFile

/**
 * High-performance, lazy-loading [FileResolver] that resolves TextMate grammars,
 * language configurations, and themes from a single compressed zip bundle.
 */
class ZipFileResolver(
    private val zipFile: ZipFile
) : FileResolver {

    override fun resolveStreamByPath(path: String): InputStream? {
        val cleanPath = path.removePrefix("/")
        val entry = zipFile.getEntry(cleanPath) ?: return null
        return try {
            zipFile.getInputStream(entry)
        } catch (e: Exception) {
            try {
                logger.logError(e)
            } catch (_: Exception) {}
            null
        }
    }

    override fun dispose() {
        try {
            zipFile.close()
        } catch (_: Exception) {}
    }

    companion object {
        const val DEFAULT_BUNDLE_ASSET = "textmate.bundle"
        private const val BUNDLE_CACHE_NAME = "textmate.bundle"
        private const val VERSION_FILE_NAME = "textmate.version"

        @Volatile
        private var instance: ZipFileResolver? = null

        fun getInstance(context: Context): ZipFileResolver? {
            instance?.let { return it }
            synchronized(this) {
                instance?.let { return it }
                val created = fromAssets(context)
                instance = created
                return created
            }
        }

        fun fromAssets(
            context: Context,
            assetName: String = DEFAULT_BUNDLE_ASSET
        ): ZipFileResolver? {
            return try {
                val cacheDir = context.filesDir
                val targetFile = File(cacheDir, BUNDLE_CACHE_NAME)
                val versionFile = File(cacheDir, VERSION_FILE_NAME)

                val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
                val versionCode = PackageInfoCompat.getLongVersionCode(pInfo)

                val needsExtract = !targetFile.exists() ||
                        !versionFile.exists() ||
                        versionFile.readText().trim() != versionCode.toString()

                if (needsExtract) {
                    val tempFile = File(cacheDir, "$BUNDLE_CACHE_NAME.tmp")
                    context.assets.open(assetName).use { input ->
                        tempFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    if (targetFile.exists()) targetFile.delete()
                    if (!tempFile.renameTo(targetFile)) {
                        tempFile.copyTo(targetFile, overwrite = true)
                        tempFile.delete()
                    }
                    versionFile.writeText(versionCode.toString())
                }

                ZipFileResolver(ZipFile(targetFile))
            } catch (e: Exception) {
                try {
                    logger.logError(e)
                } catch (_: Exception) {}
                null
            }
        }

        fun fromFile(file: File): ZipFileResolver? {
            return try {
                if (file.exists() && file.isFile) {
                    ZipFileResolver(ZipFile(file))
                } else {
                    null
                }
            } catch (e: Exception) {
                try {
                    logger.logError(e)
                } catch (_: Exception) {}
                null
            }
        }
    }
}
