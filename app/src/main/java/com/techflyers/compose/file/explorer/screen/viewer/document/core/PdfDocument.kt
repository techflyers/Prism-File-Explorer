package com.techflyers.compose.file.explorer.screen.viewer.document.core

import android.net.Uri

data class PdfDocument(
    val uri: Uri,
    val name: String,
    val pageCount: Int,
    val sizeBytes: Long
)
