package com.raival.compose.file.explorer.common

/**
 * Splits a file display name into base name + extension.
 *
 * Folders and names without a usable extension (including leading-dot names
 * such as `.gitignore`) return an empty extension. The extension never
 * includes the dot.
 */
fun splitFileName(displayName: String, isFolder: Boolean): Pair<String, String> {
    if (isFolder) return displayName to emptyString
    val lastDot = displayName.lastIndexOf('.')
    if (lastDot <= 0 || lastDot == displayName.lastIndex) {
        return displayName to emptyString
    }
    return displayName.substring(0, lastDot) to displayName.substring(lastDot + 1)
}

fun joinFileName(name: String, extension: String): String {
    val trimmedName = name.trim()
    val trimmedExt = extension.trim().trimStart('.')
    return if (trimmedExt.isEmpty()) trimmedName else "$trimmedName.$trimmedExt"
}
