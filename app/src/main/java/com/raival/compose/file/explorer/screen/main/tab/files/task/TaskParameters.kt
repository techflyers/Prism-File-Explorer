package com.raival.compose.file.explorer.screen.main.tab.files.task

import com.raival.compose.file.explorer.screen.main.tab.files.holder.ContentHolder

interface TaskParameters

data class CopyTaskParameters(
    val destHolder: ContentHolder
) : TaskParameters

class DeleteTaskParameters : TaskParameters

data class ExtractTaskParameters(
    val password: String? = null
) : TaskParameters

data class CompressTaskParameters(
    val destPath: String,
    val password: String? = null,
    val compressionLevel: Int = 5
) : TaskParameters

data class RenameTaskParameters(
    val newName: String,
    val toFind: String,
    val toReplace: String,
    val useRegex: Boolean
) : TaskParameters

data class ApksMergeTaskParameters(
    val autoSign: Boolean
) : TaskParameters
