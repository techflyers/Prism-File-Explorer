package com.techflyers.compose.file.explorer.screen.main.tab.files.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.techflyers.compose.file.explorer.App.Companion.globalClass
import com.techflyers.compose.file.explorer.R
import com.techflyers.compose.file.explorer.screen.main.tab.files.FilesTab
import com.techflyers.compose.file.explorer.screen.main.tab.files.holder.VirtualFileHolder
import com.techflyers.compose.file.explorer.screen.main.tab.files.misc.CompactFilterChip
import com.techflyers.compose.file.explorer.screen.main.tab.files.misc.CompactFilterCustomizeButton
import com.techflyers.compose.file.explorer.screen.main.tab.files.misc.CustomFilterItem
import com.techflyers.compose.file.explorer.screen.main.tab.files.misc.FilterConfigManager
import com.techflyers.compose.file.explorer.screen.main.tab.files.misc.FilterScope
import com.techflyers.compose.file.explorer.screen.main.tab.files.ui.dialog.FilterCustomizationDialog

@Composable
fun FormatRibbon(tab: FilesTab, compact: Boolean = false) {
    val folder = tab.activeFolder as? VirtualFileHolder ?: return
    if (folder.type !in setOf(
            VirtualFileHolder.IMAGE,
            VirtualFileHolder.VIDEO,
            VirtualFileHolder.AUDIO,
            VirtualFileHolder.DOCUMENT,
            VirtualFileHolder.ARCHIVE,
            VirtualFileHolder.APK
        )
    ) return

    val prefs = globalClass.preferencesManager
    var showCustomizeDialog by remember { mutableStateOf(false) }

    val configuredFilters = remember(folder.type, prefs.categoryFiltersConfigJson, prefs.filterConfigVersion) {
        FilterConfigManager.getCategoryFilters(folder.type).filter { it.isEnabled }
    }

    val discoveredExts = folder.availableFormats()
    val coveredExts = remember(configuredFilters) {
        configuredFilters.flatMap { it.extensions }.map { it.lowercase() }.toSet()
    }
    val extraDiscoveredFilters = remember(discoveredExts, coveredExts) {
        discoveredExts.filter { it !in coveredExts }.map { ext ->
            CustomFilterItem(
                id = "auto_$ext",
                name = ext.uppercase(),
                extensions = listOf(ext),
                isEnabled = true
            )
        }
    }
    val allFilters = remember(configuredFilters, extraDiscoveredFilters) {
        configuredFilters + extraDiscoveredFilters
    }

    if (showCustomizeDialog) {
        FilterCustomizationDialog(
            initialScope = FilterScope.CATEGORY,
            initialCategoryType = folder.type,
            onDismiss = { showCustomizeDialog = false },
            onUpdated = {
                tab.reloadFiles()
            }
        )
    }

    if (allFilters.isEmpty()) return

    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = if (compact) 1.dp else 3.dp),
        contentPadding = PaddingValues(horizontal = if (compact) 4.dp else 8.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        item("all") {
            val allSelected = tab.selectedFormats.isEmpty()
            CompactFilterChip(
                label = stringResource(R.string.all),
                selected = allSelected,
                onClick = {
                    tab.selectedFormats = emptySet()
                    (tab.activeFolder as? VirtualFileHolder)?.selectedFormats = emptySet()
                    tab.reloadFiles()
                },
                onLongClick = { showCustomizeDialog = true }
            )
        }
        items(allFilters, key = { it.id }) { filterItem ->
            val filterExts = filterItem.extensions.map { it.lowercase() }.toSet()
            val selected = filterExts.isNotEmpty() && filterExts.all { it in tab.selectedFormats }
            CompactFilterChip(
                label = filterItem.name,
                selected = selected,
                onClick = {
                    val next = tab.selectedFormats.toMutableSet()
                    if (selected) {
                        next.removeAll(filterExts)
                    } else {
                        next.addAll(filterExts)
                    }
                    tab.selectedFormats = next
                    (tab.activeFolder as? VirtualFileHolder)?.selectedFormats = next
                    tab.reloadFiles()
                },
                onLongClick = { showCustomizeDialog = true }
            )
        }
        item("customize_category_filters") {
            CompactFilterCustomizeButton(
                onClick = { showCustomizeDialog = true }
            )
        }
    }
}
