package com.raival.compose.file.explorer.screen.main.tab.files.misc

/**
 * Encapsulates originating folder or application identity for a file.
 *
 * @param folderName Display name of the source folder or originating application (e.g. "WhatsApp Images", "Camera", "Download").
 * @param icon The visual icon for the source (can be Bitmap, Drawable, ImageVector, or @DrawableRes Int).
 * @param appPackage Android package name if this source originates from an installed app, or null.
 * @param isApp True if this represents an application directory, false for standard/generic folders.
 */
data class SourceFolderInfo(
    val folderName: String,
    val icon: Any,
    val appPackage: String? = null,
    val isApp: Boolean = false
)
