package com.techflyers.compose.file.explorer.screen.main.model

import com.techflyers.compose.file.explorer.screen.main.tab.Tab
import java.util.UUID

data class ClosedTabEntry(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val subtitle: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val createTab: () -> Tab
)
