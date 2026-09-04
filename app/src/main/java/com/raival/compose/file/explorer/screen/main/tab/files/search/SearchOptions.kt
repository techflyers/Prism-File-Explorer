package com.raival.compose.file.explorer.screen.main.tab.files.search

import com.raival.compose.file.explorer.screen.main.tab.files.holder.ContentHolder

data class SearchOptions(
    val ignoreCase: Boolean = true,
    val useRegex: Boolean = false,
    val searchByExtension: Boolean = false,
    val searchInFileContent: Boolean = false,
    val maxFileSize: Long = 50 * 1024 * 1024,
    val maxResults: Int = 1000,
    val selectedFormats: Set<SearchFileFilter> = emptySet()
) {
    fun matchesFilter(item: ContentHolder): Boolean {
        if (selectedFormats.isEmpty()) return true
        return selectedFormats.any { it.matches(item) }
    }
}