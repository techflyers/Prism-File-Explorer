package com.raival.compose.file.explorer.screen.main.tab.files.misc

import com.raival.compose.file.explorer.screen.main.tab.files.holder.ContentHolder
import java.util.Locale

val sortFoldersFirst = Comparator { file1: ContentHolder, file2: ContentHolder ->
    if (file1.isFolder && !file2.isFolder) {
        return@Comparator -1
    } else if (!file1.isFolder && file2.isFolder) {
        return@Comparator 1
    } else {
        return@Comparator 0
    }
}

val sortFilesFirst = Comparator { file2: ContentHolder, file1: ContentHolder ->
    if (file1.isFolder && !file2.isFolder) {
        return@Comparator -1
    } else if (!file1.isFolder && file2.isFolder) {
        return@Comparator 1
    } else {
        return@Comparator 0
    }
}

val sortOlderFirst = Comparator.comparingLong { obj: ContentHolder -> obj.lastModified }

val sortNewerFirst = Comparator { file1: ContentHolder, file2: ContentHolder ->
    file2.lastModified.compareTo(file1.lastModified)
}

val sortName = Comparator { file1: ContentHolder, file2: ContentHolder ->
    naturalCompare(file1.displayName, file2.displayName)
}

val sortNameRev = Comparator { file1: ContentHolder, file2: ContentHolder ->
    naturalCompare(file2.displayName, file1.displayName)
}

private fun naturalCompare(s1: String, s2: String): Int {
    var i1 = 0
    var i2 = 0
    val len1 = s1.length
    val len2 = s2.length

    while (i1 < len1 && i2 < len2) {
        val raw1 = s1[i1]
        val raw2 = s2[i2]

        if (raw1.isDigit() && raw2.isDigit()) {
            // Skip leading zeros so "01" and "1" compare as equal numerically
            while (i1 < len1 - 1 && s1[i1] == '0' && s1[i1 + 1].isDigit()) i1++
            while (i2 < len2 - 1 && s2[i2] == '0' && s2[i2 + 1].isDigit()) i2++

            var num1 = 0L
            var num2 = 0L

            while (i1 < len1 && s1[i1].isDigit()) {
                num1 = num1 * 10 + (s1[i1] - '0')
                i1++
            }

            while (i2 < len2 && s2[i2].isDigit()) {
                num2 = num2 * 10 + (s2[i2] - '0')
                i2++
            }

            if (num1 != num2) {
                return num1.compareTo(num2)
            }
        } else {
            val c1 = raw1.lowercaseChar()
            val c2 = raw2.lowercaseChar()

            if (c1 != c2) {
                return when {
                    raw1.isDigit() && !raw2.isDigit() -> -1
                    !raw1.isDigit() && raw2.isDigit() -> 1
                    else -> c1.compareTo(c2)
                }
            }
            i1++
            i2++
        }
    }

    return len1.compareTo(len2)
}

val sortSmallerFirst = Comparator.comparingLong { obj: ContentHolder -> obj.size }

val sortLargerFirst = Comparator { file1: ContentHolder, file2: ContentHolder ->
    file2.size.compareTo(file1.size)
}

val sortType = Comparator.comparing { file: ContentHolder ->
    file.extension
}

val sortTypeRev = Comparator { file1: ContentHolder, file2: ContentHolder ->
    file2.extension.compareTo(file1.extension)
}
