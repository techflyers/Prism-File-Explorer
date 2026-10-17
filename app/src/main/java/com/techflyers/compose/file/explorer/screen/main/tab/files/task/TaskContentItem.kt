package com.techflyers.compose.file.explorer.screen.main.tab.files.task

import com.techflyers.compose.file.explorer.screen.main.tab.files.holder.ContentHolder

data class TaskContentItem(
    val content: ContentHolder,
    var relativePath: String,
    var status: TaskContentStatus,
    val rootFolderName: String? = null
)
