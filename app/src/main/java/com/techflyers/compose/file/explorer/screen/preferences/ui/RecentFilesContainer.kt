package com.techflyers.compose.file.explorer.screen.preferences.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ManageSearch
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.FilterNone
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderOff
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.techflyers.compose.file.explorer.App.Companion.globalClass
import com.techflyers.compose.file.explorer.R
import com.techflyers.compose.file.explorer.common.ui.BottomSheetDialog
import com.techflyers.compose.file.explorer.common.ui.Space
import com.techflyers.compose.file.explorer.screen.main.tab.files.provider.StorageProvider
import com.techflyers.compose.file.explorer.screen.main.tab.home.HomeTab

@Composable
fun RecentFilesContainer() {
    val prefs = globalClass.preferencesManager
    var showExcludedPathsDialog by remember { mutableStateOf(false) }
    var showCustomLimitDialog by remember { mutableStateOf(false) }

    val recentFilesLimits = remember {
        arrayListOf("0", "5", "10", "15", "25", "50", "100", "250", "500", "Unlimited", "Custom…")
    }

    val refreshHomeRecentFiles = {
        StorageProvider.invalidateRecentFilesCache()
        globalClass.mainActivityManager.state.value.tabs
            .filterIsInstance<HomeTab>()
            .forEach { it.refreshRecentFiles() }
    }

    if (showCustomLimitDialog) {
        var customLimitInput by remember {
            mutableStateOf(if (prefs.recentFilesLimit >= 0) prefs.recentFilesLimit.toString() else "25")
        }
        AlertDialog(
            onDismissRequest = { showCustomLimitDialog = false },
            title = { Text(stringResource(R.string.recent_files_limit)) },
            text = {
                Column {
                    Text(
                        text = "Enter a custom maximum number of recent files (0 disables recent files loading):",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    OutlinedTextField(
                        value = customLimitInput,
                        onValueChange = { input ->
                            customLimitInput = input.filter { it.isDigit() }
                        },
                        label = { Text("Limit (0 or higher)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val parsed = customLimitInput.toIntOrNull()
                        if (parsed != null && parsed >= 0) {
                            prefs.recentFilesLimit = parsed
                            refreshHomeRecentFiles()
                        }
                        showCustomLimitDialog = false
                    }
                ) {
                    Text(stringResource(R.string.apply))
                }
            },
            dismissButton = {
                TextButton(onClick = { showCustomLimitDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (showExcludedPathsDialog) {
        BottomSheetDialog(
            onDismissRequest = { showExcludedPathsDialog = false }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.FolderOff,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Space(8.dp)
                    Text(
                        text = stringResource(R.string.excluded_paths),
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Medium
                    )
                }

                var newPathText by remember { mutableStateOf("") }
                var isAddButtonEnabled by remember { mutableStateOf(false) }

                TextField(
                    value = newPathText,
                    onValueChange = {
                        newPathText = it
                        isAddButtonEnabled = it.trim().isNotEmpty()
                    },
                    label = { Text(stringResource(R.string.add_new_path)) },
                    placeholder = {
                        Text(
                            text = "/storage/emulated/0/example",
                            modifier = Modifier.alpha(0.4f)
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.CreateNewFolder,
                            contentDescription = null
                        )
                    },
                    trailingIcon = {
                        IconButton(
                            onClick = {
                                if (newPathText.trim().isNotEmpty()) {
                                    val currentPaths =
                                        prefs.excludedPathsFromRecentFiles.toMutableSet()
                                    currentPaths.add(newPathText.trim())
                                    prefs.excludedPathsFromRecentFiles = currentPaths
                                    StorageProvider.invalidateRecentFilesCache()
                                    newPathText = ""
                                    isAddButtonEnabled = false
                                }
                            },
                            enabled = isAddButtonEnabled
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Add,
                                contentDescription = null,
                                tint = if (isAddButtonEnabled) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                }
                            )
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    singleLine = true
                )

                val excludedPaths = prefs.excludedPathsFromRecentFiles

                if (excludedPaths.isNotEmpty()) {
                    Text(
                        text = stringResource(R.string.excluded_paths),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    LazyColumn(
                        modifier = Modifier.heightIn(max = 300.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(excludedPaths.toList()) { path ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Folder,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(end = 12.dp)
                                    )

                                    Text(
                                        text = path,
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.weight(1f),
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )

                                    IconButton(
                                        onClick = {
                                            val currentPaths =
                                                prefs.excludedPathsFromRecentFiles.toMutableSet()
                                            currentPaths.remove(path)
                                            prefs.excludedPathsFromRecentFiles = currentPaths
                                            StorageProvider.invalidateRecentFilesCache()
                                        }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Delete,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.FolderOff,
                            contentDescription = null,
                            modifier = Modifier
                                .size(48.dp)
                                .padding(bottom = 16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )

                        Text(
                            text = stringResource(R.string.no_excluded_paths),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )

                        Text(
                            text = stringResource(R.string.add_paths_to_exclude_desc),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    val canDisableOnHome = prefs.showRecentInSidebar || prefs.showRecentInCategories

    Container(
        title = stringResource(R.string.recent_files),
        icon = Icons.Rounded.History
    ) {
        PreferenceItem(
            label = stringResource(R.string.recent_files_limit),
            supportingText = when (prefs.recentFilesLimit) {
                -1 -> "Unlimited (lists all recent files ordered from newest to oldest)"
                0 -> "0 (Recent files disabled)"
                else -> "Show up to ${prefs.recentFilesLimit} most recently modified files"
            },
            icon = Icons.Rounded.FilterNone,
            onClick = {
                val unlimitedIndex = recentFilesLimits.indexOf("Unlimited")
                val customIndex = recentFilesLimits.lastIndex
                val selectedIdx = if (prefs.recentFilesLimit == -1) {
                    unlimitedIndex
                } else {
                    recentFilesLimits.indexOf(prefs.recentFilesLimit.toString())
                        .takeIf { it >= 0 } ?: customIndex
                }
                prefs.singleChoiceDialog.show(
                    title = globalClass.getString(R.string.recent_files_limit),
                    description = "Maximum number of recent files to list (0 disables loading, Unlimited lists all files from newer to older)",
                    choices = recentFilesLimits,
                    selectedChoice = selectedIdx,
                    onSelect = { idx ->
                        val chosen = recentFilesLimits[idx]
                        when (chosen) {
                            "Unlimited" -> {
                                prefs.recentFilesLimit = -1
                                refreshHomeRecentFiles()
                            }
                            "Custom…" -> {
                                showCustomLimitDialog = true
                            }
                            else -> {
                                prefs.recentFilesLimit = chosen.toIntOrNull() ?: -1
                                refreshHomeRecentFiles()
                            }
                        }
                    }
                )
            }
        )

        PreferenceDivider()

        PreferenceItem(
            label = "Show Recent files in sidebar",
            supportingText = "Add a quick Recent files shortcut inside the navigation drawer",
            icon = Icons.Rounded.Menu,
            switchState = prefs.showRecentInSidebar,
            onSwitchChange = { enabled ->
                prefs.showRecentInSidebar = enabled
                if (!enabled && !prefs.showRecentInCategories) {
                    prefs.disableRecentOnHomeTab = false
                    refreshHomeRecentFiles()
                }
            }
        )

        PreferenceDivider()

        PreferenceItem(
            label = "Show Recent files under Categories",
            supportingText = "Display a Recent files tile inside the Home Categories grid",
            icon = Icons.Rounded.Category,
            switchState = prefs.showRecentInCategories,
            onSwitchChange = { enabled ->
                prefs.showRecentInCategories = enabled
                if (!enabled && !prefs.showRecentInSidebar) {
                    prefs.disableRecentOnHomeTab = false
                    refreshHomeRecentFiles()
                }
            }
        )

        PreferenceDivider()

        PreferenceItem(
            label = "Disable Recent files on Home tab",
            supportingText = if (canDisableOnHome) {
                "Hide the Recent files carousel from the Home tab"
            } else {
                "Enable Recent files in Sidebar or Categories first to disable it on the Home tab"
            },
            icon = Icons.Rounded.Home,
            enabled = canDisableOnHome,
            switchState = prefs.disableRecentOnHomeTab && canDisableOnHome,
            onSwitchChange = { enabled ->
                if (canDisableOnHome) {
                    prefs.disableRecentOnHomeTab = enabled
                    if (!enabled) {
                        refreshHomeRecentFiles()
                    }
                }
            }
        )

        PreferenceDivider()

        PreferenceItem(
            label = stringResource(R.string.extensive_recent_files_scan),
            supportingText = stringResource(R.string.scan_hidden_files_and_folders),
            icon = Icons.AutoMirrored.Rounded.ManageSearch,
            switchState = !prefs.removeHiddenPathsFromRecentFiles,
            onSwitchChange = { enabled ->
                prefs.removeHiddenPathsFromRecentFiles = !enabled
                if (enabled) {
                    StorageProvider.triggerRecentFilesIndexingIfNeeded(forceRebuild = false) {
                        refreshHomeRecentFiles()
                    }
                } else {
                    refreshHomeRecentFiles()
                }
            }
        )

        if (!prefs.removeHiddenPathsFromRecentFiles) {
            PreferenceDivider()

            val isIndexing = StorageProvider.isIndexingRecentFiles
            val indexedCount = StorageProvider.indexedRecentFilesCount
            PreferenceItem(
                label = "Recent files index",
                supportingText = if (isIndexing) {
                    "Indexing device files… ($indexedCount files indexed)"
                } else if (indexedCount > 0) {
                    "$indexedCount files indexed • Tap to rebuild index"
                } else {
                    "Tap to build recent files index now"
                },
                icon = Icons.Rounded.History,
                enabled = !isIndexing,
                onClick = {
                    StorageProvider.triggerRecentFilesIndexingIfNeeded(forceRebuild = true) {
                        refreshHomeRecentFiles()
                    }
                }
            )
        }

        PreferenceDivider()

        PreferenceItem(
            label = stringResource(R.string.hide_temp_and_db_files),
            supportingText = stringResource(R.string.hide_temp_and_db_files_desc),
            icon = Icons.Rounded.DeleteSweep,
            switchState = prefs.hideTempAndDbFilesFromRecentFiles,
            onSwitchChange = {
                prefs.hideTempAndDbFilesFromRecentFiles = it
                refreshHomeRecentFiles()
            }
        )

        PreferenceDivider()

        PreferenceItem(
            label = stringResource(R.string.excluded_paths),
            supportingText = stringResource(R.string.manage_excluded_paths),
            icon = Icons.Rounded.Block,
            onClick = { showExcludedPathsDialog = true }
        )
    }
}
