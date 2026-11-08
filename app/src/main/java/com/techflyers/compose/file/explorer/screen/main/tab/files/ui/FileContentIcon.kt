package com.techflyers.compose.file.explorer.screen.main.tab.files.ui

import android.content.Context
import android.content.pm.PackageManager
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.BuildCircle
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.QuestionMark
import androidx.compose.material.icons.filled.Slideshow
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.Bookmarks
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.techflyers.compose.file.explorer.common.icons.Code
import com.techflyers.compose.file.explorer.common.icons.Iso
import com.techflyers.compose.file.explorer.common.icons.Java
import com.techflyers.compose.file.explorer.common.icons.Kotlin
import com.techflyers.compose.file.explorer.common.icons.Markdown
import com.techflyers.compose.file.explorer.common.icons.Pdf
import com.techflyers.compose.file.explorer.common.icons.PrismIcons
import com.techflyers.compose.file.explorer.common.icons.Sql
import com.techflyers.compose.file.explorer.common.icons.Vector
import com.techflyers.compose.file.explorer.common.magika.MagikaFileTypeDetector
import com.techflyers.compose.file.explorer.screen.main.tab.files.holder.ContentHolder
import com.techflyers.compose.file.explorer.screen.main.tab.files.holder.LocalFileHolder
import com.techflyers.compose.file.explorer.screen.main.tab.files.misc.FileMimeType.apkFileType
import com.techflyers.compose.file.explorer.screen.main.tab.files.misc.FileMimeType.archiveFileType
import com.techflyers.compose.file.explorer.screen.main.tab.files.misc.FileMimeType.audioFileType
import com.techflyers.compose.file.explorer.screen.main.tab.files.misc.FileMimeType.codeFileType
import com.techflyers.compose.file.explorer.screen.main.tab.files.misc.FileMimeType.comicFileType
import com.techflyers.compose.file.explorer.screen.main.tab.files.misc.FileMimeType.djvuFileTypes
import com.techflyers.compose.file.explorer.screen.main.tab.files.misc.FileMimeType.docFileType
import com.techflyers.compose.file.explorer.screen.main.tab.files.misc.FileMimeType.ebookFileType
import com.techflyers.compose.file.explorer.screen.main.tab.files.misc.FileMimeType.editableFileType
import com.techflyers.compose.file.explorer.screen.main.tab.files.misc.FileMimeType.excelFileType
import com.techflyers.compose.file.explorer.screen.main.tab.files.misc.FileMimeType.fontFileType
import com.techflyers.compose.file.explorer.screen.main.tab.files.misc.FileMimeType.imageFileType
import com.techflyers.compose.file.explorer.screen.main.tab.files.misc.FileMimeType.isoFileType
import com.techflyers.compose.file.explorer.screen.main.tab.files.misc.FileMimeType.javaFileType
import com.techflyers.compose.file.explorer.screen.main.tab.files.misc.FileMimeType.kotlinFileType
import com.techflyers.compose.file.explorer.screen.main.tab.files.misc.FileMimeType.markdownFileType
import com.techflyers.compose.file.explorer.screen.main.tab.files.misc.FileMimeType.pdfFileType
import com.techflyers.compose.file.explorer.screen.main.tab.files.misc.FileMimeType.pptFileType
import com.techflyers.compose.file.explorer.screen.main.tab.files.misc.FileMimeType.prismPrefsFileType
import com.techflyers.compose.file.explorer.screen.main.tab.files.misc.FileMimeType.sqlFileType
import com.techflyers.compose.file.explorer.screen.main.tab.files.misc.FileMimeType.vectorFileType
import com.techflyers.compose.file.explorer.screen.main.tab.files.misc.FileMimeType.videoFileType
import com.techflyers.compose.file.explorer.screen.main.tab.files.shizuku.ShizukuManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

private val iconCache = LruCache<String, FileContentIcon>(1000)
private val appFolderIconCache = LruCache<String, ImageBitmap>(300)
private val missingAppPackages = ConcurrentHashMap.newKeySet<String>()

/**
 * Detects if [holder] is an app-specific directory inside `Android/data/<pkg>`,
 * `Android/obb/<pkg>`, `Android/media/<pkg>`, `/data/data/<pkg>`, or `/data/user/<id>/<pkg>`
 * (whether accessed via standard storage or Shizuku/Root) and returns `<pkg>`.
 */
fun extractAndroidAppPackageName(holder: ContentHolder): String? {
    if (!holder.isFolder) return null
    val normalized = holder.uniquePath.replace('\\', '/').trimEnd('/')
    val parentPath = normalized.substringBeforeLast('/', "")
    val folderName = normalized.substringAfterLast('/')
    if (!folderName.contains('.') || folderName.startsWith('.')) return null

    val lowerParent = parentPath.lowercase()
    val isAppPackageParent = lowerParent.endsWith("/android/data") ||
        lowerParent.endsWith("/android/obb") ||
        lowerParent.endsWith("/android/media") ||
        lowerParent == "/data/data" ||
        lowerParent.matches(Regex("^/data/user/\\d+$")) ||
        lowerParent.matches(Regex("^/data/user_de/\\d+$"))
    return if (isAppPackageParent) folderName else null
}

private fun resolveAppPackageIcon(context: Context, packageName: String): ImageBitmap? {
    appFolderIconCache.get(packageName)?.let { return it }
    if (missingAppPackages.contains(packageName)) return null

    val pm = context.packageManager

    // 1. Standard PackageManager lookup (supported via QUERY_ALL_PACKAGES)
    val drawable = runCatching {
        pm.getApplicationIcon(packageName)
    }.getOrElse {
        runCatching {
            @Suppress("DEPRECATION")
            val appInfo = pm.getApplicationInfo(
                packageName,
                PackageManager.MATCH_UNINSTALLED_PACKAGES or PackageManager.MATCH_DISABLED_COMPONENTS
            )
            pm.getApplicationIcon(appInfo)
        }.getOrNull()
    } ?: run {
        // 2. Fallback via Shizuku `pm path <package>` for multi-user / restricted packages
        if (ShizukuManager.isPrivileged) {
            runCatching {
                val output = ShizukuManager.runCommand("pm path ${ShizukuManager.escapeShellArg(packageName)}")
                val apkPath = output
                    ?.lineSequence()
                    ?.map { it.trim() }
                    ?.firstOrNull { it.startsWith("package:") }
                    ?.removePrefix("package:")
                    ?.trim()
                if (!apkPath.isNullOrEmpty()) {
                    @Suppress("DEPRECATION")
                    val pkgInfo = pm.getPackageArchiveInfo(apkPath, 0)
                    val appInfo = pkgInfo?.applicationInfo
                    if (appInfo != null) {
                        appInfo.sourceDir = apkPath
                        appInfo.publicSourceDir = apkPath
                        appInfo.loadIcon(pm)
                    } else null
                } else null
            }.getOrNull()
        } else null
    }

    if (drawable != null) {
        val bitmap = runCatching {
            drawable.toBitmap(width = 128, height = 128).asImageBitmap()
        }.getOrNull()
        if (bitmap != null) {
            appFolderIconCache.put(packageName, bitmap)
            return bitmap
        }
    }

    missingAppPackages.add(packageName)
    return null
}

data class FileContentIcon(
    val icon: Any,
    val backgroundColor: Color? = Color(0xFF1C439B),
    val iconColor: Color? = Color.White,
)

/**
 * Maps a Magika label to the corresponding [FileContentIcon].
 * Returns null if the label doesn't map to a known icon.
 */
private fun magikaIconForLabel(label: String): FileContentIcon? = when (label) {
    "java" -> FileContentIcon(PrismIcons.Java)
    "kotlin" -> FileContentIcon(PrismIcons.Kotlin)
    "markdown" -> FileContentIcon(PrismIcons.Markdown)
    "iso" -> FileContentIcon(PrismIcons.Iso)
    "sql" -> FileContentIcon(PrismIcons.Sql)
    "pdf" -> FileContentIcon(PrismIcons.Pdf)
    "apk" -> FileContentIcon(Icons.Default.Android)
    "mp4", "qt", "mov", "mkv", "webm", "flv", "3gp", "avi", "wmv", "asf", "mpeg", "mpg", "vob", "ts", "m2ts", "ogv" -> FileContentIcon(Icons.Default.Videocam)
    "png", "jpeg", "gif", "webp", "bmp", "tiff", "ico", "psd" -> FileContentIcon(Icons.Default.Image)
    "svg" -> FileContentIcon(PrismIcons.Vector)
    "doc", "docx" -> FileContentIcon(Icons.Default.Description)
    "xls", "xlsx" -> FileContentIcon(Icons.Default.TableChart)
    "ppt", "pptx" -> FileContentIcon(Icons.Default.Slideshow)
    "ttf", "otf" -> FileContentIcon(Icons.Default.TextFields)
    "mp3", "m4a", "m4b", "aac", "wav", "ogg", "oga", "opus", "flac", "wma", "aiff", "amr", "ac3", "midi" -> FileContentIcon(Icons.Default.Audiotrack)
    "epub" -> FileContentIcon(Icons.Rounded.Bookmark)
    "cbz", "cbr", "cb7", "cbt" -> FileContentIcon(Icons.Rounded.Bookmarks)
    "zip", "jar", "apk", "gzip", "bzip", "sevenzip", "rar", "tar", "xz", "iso", "dmg", "cab" ->
        FileContentIcon(Icons.Default.Archive)
    "python", "javascript", "typescript", "c", "cpp", "go", "rust", "ruby", "php",
    "perl", "shell", "css", "scss", "html", "xml", "json", "yaml", "latex",
    "swift", "scala", "lua", "r", "dart" ->
        FileContentIcon(PrismIcons.Code)
    "txt", "csv", "tsv", "ini", "toml", "rst", "diff", "rtf" ->
        FileContentIcon(Icons.Default.Description)
    else -> null
}

@Composable
private fun getContentIcon(content: ContentHolder): FileContentIcon {
    if (content.isFolder) return FileContentIcon(
        icon = Icons.Rounded.Folder,
        backgroundColor = colorScheme.surfaceVariant,
        iconColor = colorScheme.onSurfaceVariant
    )

    val extension = content.extension

    if (extension == prismPrefsFileType) return FileContentIcon(Icons.Default.BuildCircle)
    if (extension == javaFileType) return FileContentIcon(PrismIcons.Java)
    if (extension == kotlinFileType) return FileContentIcon(PrismIcons.Kotlin)
    if (extension == markdownFileType) return FileContentIcon(PrismIcons.Markdown)
    if (extension == isoFileType) return FileContentIcon(PrismIcons.Iso)
    if (extension == sqlFileType) return FileContentIcon(PrismIcons.Sql)
    if (extension == pdfFileType) return FileContentIcon(PrismIcons.Pdf)
    if (extension == apkFileType || content.isApkBundle()) return FileContentIcon(Icons.Default.Android)

    if (videoFileType.contains(extension)) return FileContentIcon(Icons.Default.Videocam)
    if (imageFileType.contains(extension)) return FileContentIcon(Icons.Default.Image)
    if (docFileType.contains(extension)) return FileContentIcon(Icons.Default.Description)
    if (excelFileType.contains(extension)) return FileContentIcon(Icons.Default.TableChart)
    if (pptFileType.contains(extension)) return FileContentIcon(Icons.Default.Slideshow)
    if (fontFileType.contains(extension)) return FileContentIcon(Icons.Default.TextFields)
    if (vectorFileType.contains(extension)) return FileContentIcon(PrismIcons.Vector)
    if (audioFileType.contains(extension)) return FileContentIcon(Icons.Default.Audiotrack)
    if (codeFileType.contains(extension)) return FileContentIcon(PrismIcons.Code)
    if (comicFileType.contains(extension)) return FileContentIcon(Icons.Rounded.Bookmarks)
    if (ebookFileType.contains(extension) || djvuFileTypes.contains(extension) || content.displayName.lowercase().endsWith(".fb2.zip")) return FileContentIcon(Icons.Rounded.Bookmark)
    if (editableFileType.contains(extension)) return FileContentIcon(Icons.Default.Description)
    if (archiveFileType.contains(extension) ||
        com.techflyers.compose.file.explorer.screen.main.tab.files.zip.ArchiveManager.isSplitArchivePart(content.displayName)
    ) return FileContentIcon(Icons.Default.Archive)

    return FileContentIcon(Icons.Default.QuestionMark)
}

@Composable
fun FileContentIcon(item: ContentHolder) {
    val context = LocalContext.current

    // Check if this folder is an Android app data/obb/media package directory
    val appPackageName = remember(item.uniquePath, item.isFolder) {
        extractAndroidAppPackageName(item)
    }

    if (appPackageName != null) {
        val cachedBitmap = appFolderIconCache.get(appPackageName)
        val appBitmap by produceState<ImageBitmap?>(initialValue = cachedBitmap, appPackageName) {
            if (value == null && !missingAppPackages.contains(appPackageName)) {
                value = withContext(Dispatchers.IO) {
                    resolveAppPackageIcon(context, appPackageName)
                }
            }
        }

        if (appBitmap != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(color = colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    bitmap = appBitmap!!,
                    contentDescription = item.displayName,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize(0.78f)
                        .clip(RoundedCornerShape(8.dp))
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(2.dp)
                        .size(14.dp)
                        .background(colorScheme.surfaceContainerHighest.copy(alpha = 0.92f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Folder,
                        contentDescription = null,
                        tint = colorScheme.primary,
                        modifier = Modifier.size(10.dp)
                    )
                }
            }
            return
        }
    }

    // Start with extension-based icon
    val extensionIcon = getContentIcon(item)

    // If extension-based icon is unknown and item is a local file, try Magika detection with caching
    val fileContentIcon = if (
        extensionIcon.icon == Icons.Default.QuestionMark &&
        item is LocalFileHolder &&
        !item.isFolder
    ) {
        val cacheKey = item.uniquePath
        val cachedIcon = iconCache.get(cacheKey)

        if (cachedIcon != null) {
            cachedIcon
        } else {
            val fromDetector = MagikaFileTypeDetector.getCachedResult(item.file)?.let { magikaIconForLabel(it.label) }
            if (fromDetector != null) {
                iconCache.put(cacheKey, fromDetector)
                fromDetector
            } else {
                val magikaIcon by produceState(initialValue = extensionIcon, cacheKey) {
                    value = withContext(Dispatchers.IO) {
                        try {
                            val result = MagikaFileTypeDetector.detect(item.file)
                            val resolved = result?.let { magikaIconForLabel(it.label) } ?: extensionIcon
                            iconCache.put(cacheKey, resolved)
                            resolved
                        } catch (_: Exception) {
                            iconCache.put(cacheKey, extensionIcon)
                            extensionIcon
                        }
                    }
                }
                magikaIcon
            }
        }
    } else {
        extensionIcon
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(color = fileContentIcon.backgroundColor ?: colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        val fileIcon = fileContentIcon.icon
        if (fileIcon is ImageVector) {
            Icon(
                modifier = Modifier.fillMaxSize(0.7f),
                imageVector = fileIcon,
                contentDescription = null,
                tint = fileContentIcon.iconColor ?: Color.Unspecified
            )
        }
    }
}
