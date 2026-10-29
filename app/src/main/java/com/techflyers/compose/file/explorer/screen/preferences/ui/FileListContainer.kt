package com.techflyers.compose.file.explorer.screen.preferences.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.FormatListBulleted
import androidx.compose.material.icons.automirrored.rounded.ShortText
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.Badge
import androidx.compose.material.icons.rounded.FilterAlt
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.FormatSize
import androidx.compose.material.icons.rounded.HorizontalRule
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Numbers
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SubdirectoryArrowLeft
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.VerticalAlignBottom
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.techflyers.compose.file.explorer.App.Companion.globalClass
import com.techflyers.compose.file.explorer.R
import com.techflyers.compose.file.explorer.screen.main.tab.files.misc.FilterScope
import com.techflyers.compose.file.explorer.screen.main.tab.files.ui.dialog.FilterCustomizationDialog
import com.techflyers.compose.file.explorer.screen.preferences.constant.FileItemSize

@Composable
fun FileListContainer() {
    val prefs = globalClass.preferencesManager

    var showEndCharsDialog by remember { mutableStateOf(false) }
    var showFilterCustomizeDialog by remember { mutableStateOf(false) }

    if (showFilterCustomizeDialog) {
        FilterCustomizationDialog(
            initialScope = FilterScope.GENERAL,
            onDismiss = { showFilterCustomizeDialog = false }
        )
    }

    if (showEndCharsDialog) {
        var portraitInput by remember { mutableStateOf(prefs.filenameEndCharsCount.toString()) }
        val landscapeRaw = prefs.filenameEndCharsCountLandscape
        var landscapeInput by remember {
            mutableStateOf(if (landscapeRaw < 0) "" else landscapeRaw.toString())
        }
        var useSameForBoth by remember { mutableStateOf(landscapeRaw < 0) }

        AlertDialog(
            onDismissRequest = { showEndCharsDialog = false },
            title = { Text(stringResource(R.string.filename_end_characters)) },
            text = {
                Column {
                    Text(
                        text = stringResource(R.string.filename_end_characters_desc),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    OutlinedTextField(
                        value = portraitInput,
                        onValueChange = { portraitInput = it.filter { c -> c.isDigit() }.take(3) },
                        label = { Text(stringResource(R.string.portrait)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = useSameForBoth,
                            onCheckedChange = { useSameForBoth = it }
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.use_same_for_both_orientations))
                    }
                    if (!useSameForBoth) {
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = landscapeInput,
                            onValueChange = { landscapeInput = it.filter { c -> c.isDigit() }.take(3) },
                            label = { Text(stringResource(R.string.landscape)) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    prefs.filenameEndCharsCount = portraitInput.toIntOrNull()?.coerceIn(0, 99) ?: 0
                    prefs.filenameEndCharsCountLandscape = if (useSameForBoth) -1
                    else landscapeInput.toIntOrNull()?.coerceIn(0, 99) ?: -1
                    showEndCharsDialog = false
                }) { Text(stringResource(R.string.save)) }
            },
            dismissButton = {
                TextButton(onClick = { showEndCharsDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    Container(
        title = stringResource(R.string.file_list),
        icon = Icons.AutoMirrored.Rounded.FormatListBulleted
    ) {
        PreferenceItem(
            label = stringResource(R.string.file_list_size),
            supportingText = "Row density and icon scale: " + when (prefs.itemSize) {
                FileItemSize.EXTRA_SMALL.ordinal -> stringResource(R.string.extra_small)
                FileItemSize.SMALL.ordinal -> stringResource(R.string.small)
                FileItemSize.MEDIUM.ordinal -> stringResource(R.string.medium)
                FileItemSize.LARGE.ordinal -> stringResource(R.string.large)
                else -> stringResource(R.string.extra_large)
            },
            icon = Icons.Rounded.FormatSize,
            onClick = {
                prefs.singleChoiceDialog.show(
                    title = globalClass.getString(R.string.file_list_size),
                    description = globalClass.getString(R.string.file_list_size_desc),
                    choices = listOf(
                        globalClass.getString(R.string.extra_small),
                        globalClass.getString(R.string.small),
                        globalClass.getString(R.string.medium),
                        globalClass.getString(R.string.large),
                        globalClass.getString(R.string.extra_large)
                    ),
                    selectedChoice = prefs.itemSize,
                    onSelect = { prefs.setGlobalItemSize(it) }
                )
            }
        )

        PreferenceDivider()

        PreferenceItem(
            label = stringResource(R.string.show_hidden_files),
            supportingText = "Display files and folders whose names start with a dot (.)",
            icon = Icons.Rounded.VisibilityOff,
            switchState = prefs.showHiddenFiles,
            onSwitchChange = { prefs.showHiddenFiles = it }
        )

        PreferenceDivider()

        PreferenceItem(
            label = stringResource(R.string.show_folder_s_content_count),
            supportingText = "Display the number of subfolders and files inside each folder row",
            icon = Icons.Rounded.Numbers,
            switchState = prefs.showFolderContentCount,
            onSwitchChange = { prefs.showFolderContentCount = it }
        )

        PreferenceDivider()

        PreferenceItem(
            label = "Show \"..\" parent folder entry",
            supportingText = "Display a '..' row at the top of directory listings to quickly navigate up one level",
            icon = Icons.Rounded.SubdirectoryArrowLeft,
            switchState = prefs.showParentDirectoryEntry,
            onSwitchChange = { prefs.showParentDirectoryEntry = it }
        )

        PreferenceDivider()

        PreferenceItem(
            label = stringResource(R.string.show_directory_type_filter_ribbon),
            supportingText = stringResource(R.string.show_directory_type_filter_ribbon_desc),
            icon = Icons.Rounded.FilterList,
            switchState = prefs.showDirectoryTypeFilterRibbon,
            onSwitchChange = { prefs.showDirectoryTypeFilterRibbon = it }
        )

        PreferenceDivider()

        PreferenceItem(
            label = "Customize filters & extensions",
            supportingText = "Add, remove, rearrange, rename, and edit file extensions for general, search, and category filters",
            icon = Icons.Rounded.FilterAlt,
            onClick = { showFilterCustomizeDialog = true }
        )

        PreferenceDivider()

        PreferenceItem(
            label = "Position filter buttons at bottom",
            supportingText = if (prefs.filterBarAtBottom) {
                "Filter chips appear at the bottom of directory, category, and search views"
            } else {
                "Filter chips appear at the top of directory, category, and search views"
            },
            icon = Icons.Rounded.VerticalAlignBottom,
            switchState = prefs.filterBarAtBottom,
            onSwitchChange = { prefs.filterBarAtBottom = it }
        )

        PreferenceDivider()

        PreferenceItem(
            label = "Deep empty folder check",
            supportingText = "Show an unslashed circle icon (○) when a folder has subfolders but no files inside",
            icon = Icons.Rounded.RadioButtonUnchecked,
            switchState = prefs.deepEmptyFolderCheck,
            onSwitchChange = { prefs.deepEmptyFolderCheck = it }
        )

        PreferenceDivider()

        PreferenceItem(
            label = "Hide file extensions",
            supportingText = "Display filenames without their trailing file extension suffix",
            icon = Icons.Rounded.TextFields,
            switchState = prefs.hideFileExtensions,
            onSwitchChange = { prefs.hideFileExtensions = it }
        )

        PreferenceDivider()

        val endCharsText = if (prefs.filenameEndCharsCount <= 0) {
            stringResource(R.string.filename_end_characters_none) + " — preserve file extension only"
        } else {
            val lv = prefs.filenameEndCharsCountLandscape
            if (lv >= 0 && lv != prefs.filenameEndCharsCount) {
                stringResource(R.string.filename_end_characters_n, prefs.filenameEndCharsCount) +
                        " / " + stringResource(R.string.filename_end_characters_n, lv) +
                        " " + stringResource(R.string.landscape)
            } else {
                stringResource(R.string.filename_end_characters_n, prefs.filenameEndCharsCount)
            }
        }
        PreferenceItem(
            label = stringResource(R.string.filename_end_characters),
            supportingText = endCharsText,
            icon = Icons.AutoMirrored.Rounded.ShortText,
            onClick = { showEndCharsDialog = true }
        )

        PreferenceDivider()

        PreferenceItem(
            label = stringResource(R.string.show_source_folder_badges),
            supportingText = stringResource(R.string.show_source_folder_badges_desc),
            icon = Icons.Rounded.Badge,
            switchState = prefs.showSourceBadges,
            onSwitchChange = { prefs.showSourceBadges = it }
        )

        PreferenceDivider()

        PreferenceItem(
            label = stringResource(R.string.show_file_list_divider),
            supportingText = stringResource(R.string.show_file_list_divider_desc),
            icon = Icons.Rounded.HorizontalRule,
            switchState = prefs.showFileListDivider,
            onSwitchChange = { prefs.showFileListDivider = it }
        )

        PreferenceDivider()

        PreferenceItem(
            label = stringResource(R.string.file_size_tint),
            supportingText = stringResource(R.string.file_size_tint_desc),
            icon = Icons.Rounded.Palette,
            switchState = prefs.sizeTintEnabled,
            onSwitchChange = { prefs.sizeTintEnabled = it }
        )

        PreferenceDivider()

        PreferenceItem(
            label = stringResource(R.string.folder_scan_for_tint),
            supportingText = stringResource(R.string.folder_scan_for_tint_desc),
            icon = Icons.Rounded.FolderOpen,
            switchState = prefs.folderScanForTint,
            onSwitchChange = { prefs.folderScanForTint = it }
        )

        PreferenceDivider()

        PreferenceItem(
            label = stringResource(R.string.deep_scan_categories),
            supportingText = stringResource(R.string.deep_scan_categories_desc),
            icon = Icons.Rounded.Search,
            switchState = prefs.deepScanCategories,
            onSwitchChange = { prefs.deepScanCategories = it }
        )

        PreferenceDivider()

        PreferenceItem(
            label = "Stabilize directory loading",
            supportingText = "Pre-resolve item details and load directory batches smoothly to prevent layout shifts",
            icon = Icons.Rounded.Layers,
            switchState = prefs.stabilizeDirectoryLoading,
            onSwitchChange = { prefs.stabilizeDirectoryLoading = it }
        )

        PreferenceDivider()

        PreferenceItem(
            label = "Only refresh sort icon on navigation",
            supportingText = "Reuse cached folder listings when navigating back and forth instead of forcing a full reload",
            icon = Icons.AutoMirrored.Rounded.Sort,
            switchState = prefs.onlyRefreshSortIconOnNavigation,
            onSwitchChange = { prefs.onlyRefreshSortIconOnNavigation = it }
        )

        PreferenceDivider()

        PreferenceItem(
            label = "Disable auto-scroll on bottom selection",
            supportingText = "Prevent the file list from nudging upward when selecting items near the bottom edge",
            icon = Icons.Rounded.SwapVert,
            switchState = prefs.disableAutoScrollOnBottomSelection,
            onSwitchChange = { prefs.disableAutoScrollOnBottomSelection = it }
        )
    }
}
