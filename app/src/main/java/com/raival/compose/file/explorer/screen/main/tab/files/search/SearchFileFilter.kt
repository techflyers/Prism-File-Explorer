package com.raival.compose.file.explorer.screen.main.tab.files.search

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.VideoFile
import androidx.compose.ui.graphics.vector.ImageVector
import com.raival.compose.file.explorer.R
import com.raival.compose.file.explorer.screen.main.tab.files.holder.ContentHolder
import com.raival.compose.file.explorer.screen.main.tab.files.misc.FileMimeType

/**
 * Tag filters for narrowing file searches by file format.
 */
enum class SearchFileFilter(
    val labelRes: Int,
    val icon: ImageVector
) {
    FOLDERS(R.string.folders, Icons.Rounded.Folder),
    ARCHIVE(R.string.archives, Icons.Rounded.Archive),
    APK(R.string.apk, Icons.Rounded.Android),
    AUDIO(R.string.audios, Icons.Rounded.AudioFile),
    DOCUMENT(R.string.documents, Icons.Rounded.Description),
    IMAGE(R.string.images, Icons.Rounded.Image),
    VIDEO(R.string.videos, Icons.Rounded.VideoFile);

    fun matches(item: ContentHolder): Boolean {
        val ext = item.extension.lowercase()
        return when (this) {
            FOLDERS -> item.isFolder
            ARCHIVE -> !item.isFolder && (
                FileMimeType.archiveFileType.contains(ext) ||
                FileMimeType.supportedArchiveFileType.contains(ext)
            )
            APK -> !item.isFolder && (
                ext == FileMimeType.apkFileType ||
                FileMimeType.apkBundleFileType.contains(ext) ||
                item.isApk() ||
                item.isApkBundle()
            )
            AUDIO -> !item.isFolder && FileMimeType.audioFileType.contains(ext)
            DOCUMENT -> !item.isFolder && FileMimeType.documentFileType.contains(ext)
            IMAGE -> !item.isFolder && FileMimeType.imageFileType.contains(ext)
            VIDEO -> !item.isFolder && FileMimeType.videoFileType.contains(ext)
        }
    }
}
