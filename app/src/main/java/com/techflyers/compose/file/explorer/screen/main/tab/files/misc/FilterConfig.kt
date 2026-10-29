package com.techflyers.compose.file.explorer.screen.main.tab.files.misc

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.techflyers.compose.file.explorer.App.Companion.globalClass
import com.techflyers.compose.file.explorer.screen.main.tab.files.holder.ContentHolder
import com.techflyers.compose.file.explorer.screen.main.tab.files.holder.VirtualFileHolder

enum class FilterScope {
    GENERAL,
    SEARCH,
    CATEGORY
}

data class CustomFilterItem(
    val id: String,
    val name: String,
    val extensions: List<String>,
    val isEnabled: Boolean = true
) {
    fun matches(item: ContentHolder): Boolean {
        val normalizedExts = extensions.map { it.lowercase().trim().removePrefix(".") }.filter { it.isNotEmpty() }
        if ("__folder__" in normalizedExts || "folder" in normalizedExts || "folders" in normalizedExts) {
            if (item.isFolder) return true
            if (normalizedExts.size == 1) return false
        }
        if (item.isFolder) return false
        val itemExt = item.extension.lowercase().trim().removePrefix(".")
        if (itemExt.isNotEmpty() && itemExt in normalizedExts) return true
        if ("apk" in normalizedExts && (item.isApk() || item.isApkBundle())) return true
        return false
    }
}

object FilterConfigManager {
    private val gson = Gson()
    private val listType = object : TypeToken<List<CustomFilterItem>>() {}.type
    private val categoryMapType = object : TypeToken<Map<String, List<CustomFilterItem>>>() {}.type

    fun defaultGeneralFilters(): List<CustomFilterItem> = listOf(
        CustomFilterItem("image", "Images", FileMimeType.imageFileType.toList(), true),
        CustomFilterItem("video", "Videos", FileMimeType.videoFileType.toList(), true),
        CustomFilterItem("audio", "Audio", FileMimeType.audioFileType.toList(), true),
        CustomFilterItem("document", "Docs", FileMimeType.documentFileType.toList(), true),
        CustomFilterItem("archive", "Archives", (FileMimeType.archiveFileType + FileMimeType.supportedArchiveFileType).distinct(), true),
        CustomFilterItem("apk", "APKs", listOf("apk") + FileMimeType.apkBundleFileType, true)
    )

    fun defaultSearchFilters(): List<CustomFilterItem> = listOf(
        CustomFilterItem("folders", "Folders", listOf("__folder__"), true),
        CustomFilterItem("image", "Images", FileMimeType.imageFileType.toList(), true),
        CustomFilterItem("video", "Videos", FileMimeType.videoFileType.toList(), true),
        CustomFilterItem("audio", "Audio", FileMimeType.audioFileType.toList(), true),
        CustomFilterItem("document", "Documents", FileMimeType.documentFileType.toList(), true),
        CustomFilterItem("archive", "Archives", (FileMimeType.archiveFileType + FileMimeType.supportedArchiveFileType).distinct(), true),
        CustomFilterItem("apk", "APK", listOf("apk") + FileMimeType.apkBundleFileType, true)
    )

    fun defaultCategoryFilters(categoryType: Int): List<CustomFilterItem> = when (categoryType) {
        VirtualFileHolder.IMAGE -> listOf(
            CustomFilterItem("img_jpg", "JPG", listOf("jpg", "jpeg"), true),
            CustomFilterItem("img_png", "PNG", listOf("png"), true),
            CustomFilterItem("img_webp", "WEBP", listOf("webp"), true),
            CustomFilterItem("img_gif", "GIF", listOf("gif"), true),
            CustomFilterItem("img_svg", "SVG", listOf("svg"), true),
            CustomFilterItem("img_heic", "HEIC", listOf("heic", "heif"), true),
            CustomFilterItem("img_bmp", "BMP", listOf("bmp"), true)
        )
        VirtualFileHolder.VIDEO -> listOf(
            CustomFilterItem("vid_mp4", "MP4", listOf("mp4", "m4v"), true),
            CustomFilterItem("vid_mkv", "MKV", listOf("mkv"), true),
            CustomFilterItem("vid_webm", "WEBM", listOf("webm"), true),
            CustomFilterItem("vid_mov", "MOV", listOf("mov"), true),
            CustomFilterItem("vid_avi", "AVI", listOf("avi"), true),
            CustomFilterItem("vid_3gp", "3GP", listOf("3gp"), true)
        )
        VirtualFileHolder.AUDIO -> listOf(
            CustomFilterItem("aud_mp3", "MP3", listOf("mp3"), true),
            CustomFilterItem("aud_flac", "FLAC", listOf("flac"), true),
            CustomFilterItem("aud_wav", "WAV", listOf("wav"), true),
            CustomFilterItem("aud_m4a", "M4A/AAC", listOf("m4a", "aac"), true),
            CustomFilterItem("aud_ogg", "OGG/OPUS", listOf("ogg", "opus"), true)
        )
        VirtualFileHolder.DOCUMENT -> listOf(
            CustomFilterItem("doc_pdf", "PDF", listOf("pdf"), true),
            CustomFilterItem("doc_word", "DOCX/DOC", listOf("docx", "doc", "odt", "rtf"), true),
            CustomFilterItem("doc_sheet", "XLSX/CSV", listOf("xlsx", "xls", "csv", "ods"), true),
            CustomFilterItem("doc_slide", "PPTX/PPT", listOf("pptx", "ppt", "odp"), true),
            CustomFilterItem("doc_txt", "TXT/MD", listOf("txt", "md", "log", "json", "xml"), true),
            CustomFilterItem("doc_ebook", "EPUB/MOBI", listOf("epub", "mobi", "fb2"), true)
        )
        VirtualFileHolder.ARCHIVE -> listOf(
            CustomFilterItem("arc_zip", "ZIP", listOf("zip"), true),
            CustomFilterItem("arc_rar", "RAR", listOf("rar"), true),
            CustomFilterItem("arc_7z", "7Z", listOf("7z"), true),
            CustomFilterItem("arc_tar", "TAR/GZ/XZ", listOf("tar", "gz", "bz2", "xz", "zst", "tgz"), true),
            CustomFilterItem("arc_iso", "ISO", listOf("iso"), true)
        )
        VirtualFileHolder.APK -> listOf(
            CustomFilterItem("apk_std", "APK", listOf("apk"), true),
            CustomFilterItem("apk_apks", "APKS", listOf("apks"), true),
            CustomFilterItem("apk_xapk", "XAPK", listOf("xapk"), true),
            CustomFilterItem("apk_apkm", "APKM", listOf("apkm"), true)
        )
        else -> emptyList()
    }

    fun getGeneralFilters(): List<CustomFilterItem> {
        val raw = globalClass.preferencesManager.generalFiltersConfigJson
        if (raw.isBlank()) return defaultGeneralFilters()
        return runCatching {
            gson.fromJson<List<CustomFilterItem>>(raw, listType)?.takeIf { it.isNotEmpty() }
                ?: defaultGeneralFilters()
        }.getOrElse { defaultGeneralFilters() }
    }

    fun saveGeneralFilters(items: List<CustomFilterItem>) {
        globalClass.preferencesManager.generalFiltersConfigJson = gson.toJson(items)
    }

    fun getSearchFilters(): List<CustomFilterItem> {
        val raw = globalClass.preferencesManager.searchFiltersConfigJson
        if (raw.isBlank()) return defaultSearchFilters()
        return runCatching {
            gson.fromJson<List<CustomFilterItem>>(raw, listType)?.takeIf { it.isNotEmpty() }
                ?: defaultSearchFilters()
        }.getOrElse { defaultSearchFilters() }
    }

    fun saveSearchFilters(items: List<CustomFilterItem>) {
        globalClass.preferencesManager.searchFiltersConfigJson = gson.toJson(items)
    }

    fun getCategoryFilters(categoryType: Int): List<CustomFilterItem> {
        val raw = globalClass.preferencesManager.categoryFiltersConfigJson
        if (raw.isNotBlank()) {
            runCatching {
                val map: Map<String, List<CustomFilterItem>>? = gson.fromJson(raw, categoryMapType)
                val list = map?.get(categoryType.toString())
                if (!list.isNullOrEmpty()) return list
            }
        }
        return defaultCategoryFilters(categoryType)
    }

    fun saveCategoryFilters(categoryType: Int, items: List<CustomFilterItem>) {
        val raw = globalClass.preferencesManager.categoryFiltersConfigJson
        val map: MutableMap<String, List<CustomFilterItem>> = if (raw.isNotBlank()) {
            runCatching {
                gson.fromJson<Map<String, List<CustomFilterItem>>>(raw, categoryMapType)?.toMutableMap()
            }.getOrNull() ?: mutableMapOf()
        } else {
            mutableMapOf()
        }
        map[categoryType.toString()] = items
        globalClass.preferencesManager.categoryFiltersConfigJson = gson.toJson(map)
    }
}

/**
 * Compact filter button without checkmarks and with reduced padding.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CompactFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null
) {
    val containerColor = if (selected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerLow
    }
    val contentColor = if (selected) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val borderColor = if (selected) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
    } else {
        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    }

    Box(
        modifier = modifier
            .heightIn(min = 26.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(containerColor, RoundedCornerShape(6.dp))
            .border(0.8.dp, borderColor, RoundedCornerShape(6.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(horizontal = 8.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            lineHeight = 14.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = contentColor,
            maxLines = 1
        )
    }
}

/**
 * Compact Customize Filter button placed at the end of filter ribbons.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CompactFilterCustomizeButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .heightIn(min = 26.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow, RoundedCornerShape(6.dp))
            .border(
                0.8.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                RoundedCornerShape(6.dp)
            )
            .combinedClickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.Tune,
                contentDescription = "Customize filters",
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}
