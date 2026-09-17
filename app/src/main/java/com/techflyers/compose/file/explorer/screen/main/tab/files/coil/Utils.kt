package com.techflyers.compose.file.explorer.screen.main.tab.files.coil

import com.techflyers.compose.file.explorer.screen.main.tab.files.holder.ContentHolder
import com.techflyers.compose.file.explorer.screen.main.tab.files.misc.FileMimeType.apkFileType
import com.techflyers.compose.file.explorer.screen.main.tab.files.misc.FileMimeType.audioFileType
import com.techflyers.compose.file.explorer.screen.main.tab.files.misc.FileMimeType.comicFileType
import com.techflyers.compose.file.explorer.screen.main.tab.files.misc.FileMimeType.ebookFileType
import com.techflyers.compose.file.explorer.screen.main.tab.files.misc.FileMimeType.imageFileType
import com.techflyers.compose.file.explorer.screen.main.tab.files.misc.FileMimeType.pdfFileType
import com.techflyers.compose.file.explorer.screen.main.tab.files.misc.FileMimeType.videoFileType

fun canUseCoil(contentHolder: ContentHolder): Boolean {
    val ext = contentHolder.extension.lowercase()
    return (contentHolder.isFile()
            && (imageFileType.contains(ext)
            || videoFileType.contains(ext)
            || audioFileType.contains(ext)
            || ext == apkFileType
            || ext == pdfFileType
            || comicFileType.contains(ext)
            || ebookFileType.contains(ext)
            || contentHolder.displayName.lowercase().endsWith(".fb2.zip")))
}