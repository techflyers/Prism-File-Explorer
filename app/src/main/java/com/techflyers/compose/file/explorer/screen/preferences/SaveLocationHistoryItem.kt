package com.techflyers.compose.file.explorer.screen.preferences

data class SaveLocationHistoryItem(
    val path: String,
    val title: String,
    val isRemote: Boolean = false
)
