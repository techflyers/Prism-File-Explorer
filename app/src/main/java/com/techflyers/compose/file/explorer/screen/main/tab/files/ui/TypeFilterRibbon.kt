package com.techflyers.compose.file.explorer.screen.main.tab.files.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import com.techflyers.compose.file.explorer.screen.main.tab.files.TimeFilterOption
import com.techflyers.compose.file.explorer.screen.main.tab.files.holder.VirtualFileHolder
import com.techflyers.compose.file.explorer.screen.main.tab.files.misc.CompactFilterChip
import com.techflyers.compose.file.explorer.screen.main.tab.files.misc.CompactFilterCustomizeButton
import com.techflyers.compose.file.explorer.screen.main.tab.files.misc.FilterConfigManager
import com.techflyers.compose.file.explorer.screen.main.tab.files.misc.FilterScope
import com.techflyers.compose.file.explorer.screen.main.tab.files.ui.dialog.FilterCustomizationDialog

@Composable
fun TypeFilterRibbon(tab: FilesTab) {
    if (tab.activeFolder is VirtualFileHolder) return
    val prefs = globalClass.preferencesManager
    if (!prefs.showDirectoryTypeFilterRibbon) return
    if (tab.isLoading && tab.activeFolderContent.isEmpty()) return

    var showCustomizeDialog by remember { mutableStateOf(false) }
    var showTimeMenu by remember { mutableStateOf(false) }
    val activeTimeFilter = tab.selectedTimeFilter
    val configuredFilters = remember(prefs.generalFiltersConfigJson, prefs.filterConfigVersion) {
        FilterConfigManager.getGeneralFilters().filter { it.isEnabled }
    }

    if (showCustomizeDialog) {
        FilterCustomizationDialog(
            initialScope = FilterScope.GENERAL,
            onDismiss = { showCustomizeDialog = false },
            onUpdated = {
                tab.updateContentSnapshot()
            }
        )
    }

    val selectedFilters = tab.selectedTypeFilters

    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        contentPadding = PaddingValues(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        item(key = "all") {
            val isAll = selectedFilters.isEmpty() && activeTimeFilter == TimeFilterOption.ALL
            CompactFilterChip(
                label = stringResource(R.string.all),
                selected = isAll,
                onClick = {
                    tab.selectedTypeFilters = emptySet()
                    tab.selectedTimeFilter = TimeFilterOption.ALL
                    tab.updateContentSnapshot()
                },
                onLongClick = { showCustomizeDialog = true }
            )
        }

        item(key = "time_filter_dropdown") {
            Box {
                CompactFilterChip(
                    label = if (activeTimeFilter == TimeFilterOption.ALL) "Time" else "Time: ${activeTimeFilter.label}",
                    selected = activeTimeFilter != TimeFilterOption.ALL,
                    onClick = { showTimeMenu = true },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.AccessTime,
                            contentDescription = "Time filter",
                            modifier = Modifier.size(14.dp),
                            tint = if (activeTimeFilter != TimeFilterOption.ALL) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    trailingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.ArrowDropDown,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = if (activeTimeFilter != TimeFilterOption.ALL) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                )
                DropdownMenu(
                    expanded = showTimeMenu,
                    onDismissRequest = { showTimeMenu = false }
                ) {
                    TimeFilterOption.entries.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option.label) },
                            leadingIcon = {
                                if (option == activeTimeFilter) {
                                    Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                }
                            },
                            onClick = {
                                tab.selectedTimeFilter = option
                                tab.updateContentSnapshot()
                                showTimeMenu = false
                            }
                        )
                    }
                }
            }
        }

        items(configuredFilters, key = { it.id }) { filterItem ->
            val selected = filterItem.id in selectedFilters
            CompactFilterChip(
                label = filterItem.name,
                selected = selected,
                onClick = {
                    tab.selectedTypeFilters = if (selected) {
                        selectedFilters - filterItem.id
                    } else {
                        selectedFilters + filterItem.id
                    }
                    tab.updateContentSnapshot()
                },
                onLongClick = { showCustomizeDialog = true }
            )
        }
        item(key = "customize_general_filters") {
            CompactFilterCustomizeButton(
                onClick = { showCustomizeDialog = true }
            )
        }
    }
}
