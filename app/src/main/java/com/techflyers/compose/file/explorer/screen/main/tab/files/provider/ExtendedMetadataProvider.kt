package com.techflyers.compose.file.explorer.screen.main.tab.files.provider

import android.content.pm.PackageManager
import android.media.MediaMetadataRetriever
import androidx.exifinterface.media.ExifInterface
import com.techflyers.compose.file.explorer.App.Companion.globalClass
import com.techflyers.compose.file.explorer.screen.main.tab.files.holder.ContentHolder
import com.techflyers.compose.file.explorer.screen.main.tab.files.holder.LocalFileHolder
import com.techflyers.compose.file.explorer.screen.main.tab.files.misc.DocumentPageCount
import com.techflyers.compose.file.explorer.screen.main.tab.files.misc.FileMimeType
import java.io.File
import java.util.zip.ZipFile

data class MetadataField(val label: String, val value: String)
data class MetadataSection(val title: String, val fields: List<MetadataField>)

object ExtendedMetadataProvider {
    fun collect(holder: ContentHolder): List<MetadataSection> {
        val file = (holder as? LocalFileHolder)?.file ?: return emptyList()
        if (!file.isFile) return emptyList()
        val ext = file.extension.lowercase()
        val sections = mutableListOf<MetadataSection>()
        when {
            ext in FileMimeType.imageFileType -> sections += imageSection(file)
            ext in FileMimeType.audioFileType || ext in FileMimeType.videoFileType ->
                sections += mediaSection(file, ext in FileMimeType.videoFileType)
            ext == "pdf" || ext in FileMimeType.officeFileType -> sections += documentSection(file)
            holder.isApk() || holder.isApkBundle() -> sections += apkSection(file)
            ext in FileMimeType.archiveFileType -> sections += archiveSection(file)
        }
        return sections.filter { it.fields.isNotEmpty() }
    }

    private fun imageSection(file: File): MetadataSection {
        val fields = mutableListOf<MetadataField>()
        try {
            val exif = ExifInterface(file.absolutePath)
            fun add(tag: String, label: String) {
                exif.getAttribute(tag)?.takeIf { it.isNotBlank() }?.let {
                    fields += MetadataField(label, it)
                }
            }
            add(ExifInterface.TAG_MAKE, "Camera make")
            add(ExifInterface.TAG_MODEL, "Camera model")
            add(ExifInterface.TAG_DATETIME, "Captured")
            add(ExifInterface.TAG_FOCAL_LENGTH, "Focal length")
            add(ExifInterface.TAG_F_NUMBER, "Aperture")
            add(ExifInterface.TAG_ISO_SPEED_RATINGS, "ISO")
            add(ExifInterface.TAG_COLOR_SPACE, "Color space")
            add(ExifInterface.TAG_IMAGE_WIDTH, "Width")
            add(ExifInterface.TAG_IMAGE_LENGTH, "Height")
            val latLong = FloatArray(2)
            if (exif.getLatLong(latLong)) {
                fields += MetadataField("GPS", "${latLong[0]}, ${latLong[1]}")
            }
        } catch (_: Exception) {
        }
        return MetadataSection("Image", fields)
    }

    private fun mediaSection(file: File, video: Boolean): MetadataSection {
        val fields = mutableListOf<MetadataField>()
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(file.absolutePath)
            fun add(key: Int, label: String) {
                retriever.extractMetadata(key)?.takeIf { it.isNotBlank() }?.let {
                    fields += MetadataField(label, it)
                }
            }
            add(MediaMetadataRetriever.METADATA_KEY_TITLE, "Title")
            add(MediaMetadataRetriever.METADATA_KEY_ARTIST, "Artist")
            add(MediaMetadataRetriever.METADATA_KEY_ALBUM, "Album")
            add(MediaMetadataRetriever.METADATA_KEY_DURATION, "Duration (ms)")
            add(MediaMetadataRetriever.METADATA_KEY_BITRATE, "Bitrate")
            add(MediaMetadataRetriever.METADATA_KEY_MIMETYPE, "MIME")
            if (video) {
                add(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH, "Width")
                add(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT, "Height")
                add(MediaMetadataRetriever.METADATA_KEY_VIDEO_FRAME_COUNT, "Frames")
            }
        } catch (_: Exception) {
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {
            }
        }
        return MetadataSection(if (video) "Video" else "Audio", fields)
    }

    private fun documentSection(file: File): MetadataSection {
        val fields = mutableListOf<MetadataField>()
        DocumentPageCount.get(file)?.let {
            fields += MetadataField(it.unit.replaceFirstChar { c -> c.uppercase() }, it.value.toString())
        }
        officeCore(file).forEach { (k, v) -> fields += MetadataField(k, v) }
        return MetadataSection("Document", fields)
    }

    private fun officeCore(file: File): Map<String, String> {
        return try {
            ZipFile(file).use { zip ->
                val entry = zip.getEntry("docProps/core.xml") ?: return emptyMap()
                val xml = zip.getInputStream(entry).bufferedReader().use { it.readText() }
                buildMap {
                    Regex("<dc:creator>(.*?)</dc:creator>").find(xml)?.groupValues?.get(1)
                        ?.let { put("Creator", it) }
                    Regex("<cp:lastModifiedBy>(.*?)</cp:lastModifiedBy>").find(xml)?.groupValues?.get(1)
                        ?.let { put("Last modified by", it) }
                    Regex("<dcterms:modified[^>]*>(.*?)</dcterms:modified>").find(xml)?.groupValues?.get(1)
                        ?.let { put("Content modified", it) }
                    Regex("<dc:title>(.*?)</dc:title>").find(xml)?.groupValues?.get(1)
                        ?.let { put("Title", it) }
                }
            }
        } catch (_: Exception) {
            emptyMap()
        }
    }

    private fun apkSection(file: File): MetadataSection {
        val fields = mutableListOf<MetadataField>()
        try {
            val pm = globalClass.packageManager
            val info = pm.getPackageArchiveInfo(file.absolutePath, PackageManager.GET_PERMISSIONS)
            if (info != null) {
                info.applicationInfo?.sourceDir = file.absolutePath
                info.applicationInfo?.publicSourceDir = file.absolutePath
                val label = info.applicationInfo?.loadLabel(pm)?.toString()
                if (!label.isNullOrBlank()) fields += MetadataField("App name", label)
                fields += MetadataField("Package", info.packageName.orEmpty())
                val verCode = androidx.core.content.pm.PackageInfoCompat.getLongVersionCode(info)
                fields += MetadataField("Version", info.versionName ?: verCode.toString())
                info.applicationInfo?.minSdkVersion?.let {
                    fields += MetadataField("Min SDK", it.toString())
                }
                fields += MetadataField("Permissions", (info.requestedPermissions?.size ?: 0).toString())
            }
        } catch (_: Exception) {
        }
        return MetadataSection("APK", fields)
    }

    private fun archiveSection(file: File): MetadataSection {
        val fields = mutableListOf<MetadataField>()
        try {
            ZipFile(file).use { zip ->
                var entries = 0
                var uncompressed = 0L
                var compressed = 0L
                zip.entries().asSequence().forEach { entry ->
                    if (!entry.isDirectory) {
                        entries++
                        uncompressed += entry.size.coerceAtLeast(0)
                        compressed += entry.compressedSize.coerceAtLeast(0)
                    }
                }
                fields += MetadataField("Entries", entries.toString())
                if (uncompressed > 0) {
                    fields += MetadataField(
                        "Compression",
                        "${((1 - compressed.toDouble() / uncompressed) * 100).toInt()}%"
                    )
                }
            }
        } catch (_: Exception) {
        }
        return MetadataSection("Archive", fields)
    }
}
