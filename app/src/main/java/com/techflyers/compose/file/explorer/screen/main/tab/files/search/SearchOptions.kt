package com.techflyers.compose.file.explorer.screen.main.tab.files.search

import com.techflyers.compose.file.explorer.screen.main.tab.files.holder.ContentHolder
import com.techflyers.compose.file.explorer.screen.main.tab.files.misc.FilterConfigManager

data class SearchOptions(
    val ignoreCase: Boolean = true,
    val useRegex: Boolean = false,
    val searchByExtension: Boolean = false,
    val searchInFileContent: Boolean = false,
    val maxFileSize: Long = 50 * 1024 * 1024,
    val maxResults: Int = 1000,
    val selectedFormats: Set<SearchFileFilter> = emptySet(),
    val selectedFilterIds: Set<String> = emptySet()
) {
    fun hasActiveFilters(): Boolean = selectedFormats.isNotEmpty() || selectedFilterIds.isNotEmpty()

    fun matchesFilter(item: ContentHolder): Boolean {
        if (selectedFormats.isEmpty() && selectedFilterIds.isEmpty()) return true
        val configuredFilters = if (selectedFilterIds.isNotEmpty()) {
            FilterConfigManager.getSearchFilters().filter { it.id in selectedFilterIds }
        } else {
            emptyList()
        }
        val matchesCustom = configuredFilters.any { it.matches(item) }
        val matchesEnum = selectedFormats.any { it.matches(item) }
        return matchesCustom || matchesEnum
    }
}
