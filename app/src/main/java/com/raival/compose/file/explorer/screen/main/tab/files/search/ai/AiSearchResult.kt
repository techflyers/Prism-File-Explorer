package com.raival.compose.file.explorer.screen.main.tab.files.search.ai

/**
 * A single semantic search result.
 */
data class AiSearchResult(
    val filePath: String,
    val displayName: String,
    val score: Double
)
