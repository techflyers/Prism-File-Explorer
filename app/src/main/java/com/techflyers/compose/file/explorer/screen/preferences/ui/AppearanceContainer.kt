package com.techflyers.compose.file.explorer.screen.preferences.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Label
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BorderBottom
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.CallToAction
import androidx.compose.material.icons.rounded.Dock
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Nightlight
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.ScreenRotation
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.rounded.Tab
import androidx.compose.material.icons.rounded.TabUnselected
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.UnfoldLess
import androidx.compose.material.icons.rounded.VerticalAlignTop
import androidx.compose.material.icons.rounded.ViewStream
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.techflyers.compose.file.explorer.App.Companion.globalClass
import com.techflyers.compose.file.explorer.R
import com.techflyers.compose.file.explorer.common.showMsg
import com.techflyers.compose.file.explorer.common.ui.Space
import com.techflyers.compose.file.explorer.screen.main.tab.files.ui.dialog.SelectionRibbonSettingsDialog
import com.techflyers.compose.file.explorer.screen.preferences.constant.ThemePreference
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun AppearanceContainer() {
    val prefs = globalClass.preferencesManager
    var showSelectionRibbonSettingsDialog by remember { mutableStateOf(false) }

    Container(
        title = stringResource(R.string.appearance),
        icon = Icons.Rounded.Palette
    ) {
        PreferenceItem(
            label = stringResource(R.string.theme),
            supportingText = when (prefs.theme) {
                ThemePreference.LIGHT.ordinal -> "Light theme (${stringResource(R.string.light)})"
                ThemePreference.DARK.ordinal -> "Dark theme (${stringResource(R.string.dark)})"
                else -> "Match system theme (${stringResource(R.string.follow_system)})"
            },
            icon = Icons.Rounded.Nightlight,
            onClick = {
                prefs.singleChoiceDialog.show(
                    title = globalClass.getString(R.string.theme),
                    description = globalClass.getString(R.string.select_theme_preference),
                    choices = listOf(
                        globalClass.getString(R.string.light),
                        globalClass.getString(R.string.dark),
                        globalClass.getString(R.string.follow_system)
                    ),
                    selectedChoice = prefs.theme,
                    onSelect = { prefs.theme = it }
                )
            }
        )

        PreferenceDivider()

        PreferenceItem(
            label = "Show directory path bar",
            supportingText = "Display the interactive breadcrumb path bar above the file list",
            icon = Icons.Rounded.FolderOpen,
            switchState = prefs.showPathBar,
            onSwitchChange = { prefs.showPathBar = it }
        )

        PreferenceDivider()

        PreferenceItem(
            label = "Hide Home tab search bar",
            supportingText = "Hide the search box on the Home tab while keeping the toolbar search icon visible",
            icon = Icons.Rounded.SearchOff,
            switchState = prefs.hideHomeSearchBar,
            onSwitchChange = { prefs.hideHomeSearchBar = it }
        )

        PreferenceDivider()

        PreferenceItem(
            label = "Search external storage on Home screen",
            supportingText = "Include mounted external storage devices (SD cards, USB drives) when searching from the Home screen",
            icon = Icons.Rounded.FolderOpen,
            switchState = prefs.searchExternalStorageFromHome,
            onSwitchChange = { prefs.searchExternalStorageFromHome = it }
        )

        PreferenceDivider()

        PreferenceItem(
            label = "Disable tab bar (Single-row top bar)",
            supportingText = "Replace the separate tab bar with a compact dropdown tab switcher in the main toolbar",
            icon = Icons.Rounded.TabUnselected,
            switchState = prefs.disableTabBar,
            onSwitchChange = { prefs.disableTabBar = it }
        )

        PreferenceDivider()

        val plusPositionChoices = listOf("Right (Default)", "Left")
        PreferenceItem(
            label = "Tab bar '+' button position",
            supportingText = if (prefs.tabBarPlusButtonOnLeft) "Left side of the tab bar" else "Right side of the tab bar",
            icon = Icons.Rounded.Tab,
            onClick = {
                prefs.singleChoiceDialog.show(
                    title = "Tab bar '+' button position",
                    description = "Choose whether the '+' (New Tab) button appears on the left or right side of the tab bar",
                    choices = plusPositionChoices,
                    selectedChoice = if (prefs.tabBarPlusButtonOnLeft) 1 else 0,
                    onSelect = { idx ->
                        prefs.tabBarPlusButtonOnLeft = (idx == 1)
                    }
                )
            }
        )

        PreferenceDivider()

        PreferenceItem(
            label = stringResource(R.string.show_tabs_in_drawer),
            supportingText = stringResource(R.string.show_tabs_in_drawer_desc),
            icon = Icons.Rounded.Tab,
            switchState = prefs.showTabsInDrawer,
            onSwitchChange = { prefs.showTabsInDrawer = it }
        )

        PreferenceDivider()

        PreferenceItem(
            label = stringResource(R.string.show_bookmarks_in_drawer),
            supportingText = stringResource(R.string.show_bookmarks_in_drawer_desc),
            icon = Icons.Rounded.Bookmark,
            switchState = prefs.showBookmarksInDrawer,
            onSwitchChange = { prefs.showBookmarksInDrawer = it }
        )

        PreferenceDivider()

        val customFormatLabel = stringResource(R.string.custom_format)
        val commonDateFormat = arrayListOf(
            "dd/MM/yy • HH:mm",
            "dd/MM/yy • HH:mm:ss",
            "dd/MM/yyyy • HH:mm",
            "dd/MM/yyyy • HH:mm:ss",
            "dd-MM-yyyy HH:mm",
            "dd-MM-yyyy HH:mm:ss",
            "dd MMM yyyy • HH:mm",
            "MMM dd, yyyy HH:mm:ss",
            "MMM dd, yyyy",
            "MMMM dd, yyyy",
            "yyyy-MM-dd HH:mm:ss",
            "yyyy-MM-dd",
            customFormatLabel
        )

        var showCustomDateDialog by remember { mutableStateOf(false) }
        var customDateInput by remember { mutableStateOf(prefs.dateTimeFormat) }

        if (showCustomDateDialog) {
            AlertDialog(
                onDismissRequest = { showCustomDateDialog = false },
                title = { Text(text = stringResource(R.string.custom_date_format)) },
                text = {
                    Column {
                        Text(
                            text = stringResource(R.string.enter_date_format_pattern),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Space(8.dp)
                        OutlinedTextField(
                            value = customDateInput,
                            onValueChange = { customDateInput = it },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            if (customDateInput.isNotBlank()) {
                                try {
                                    SimpleDateFormat(customDateInput.trim(), Locale.getDefault())
                                    prefs.dateTimeFormat = customDateInput.trim()
                                    showCustomDateDialog = false
                                } catch (_: Exception) {
                                    showMsg(globalClass.getString(R.string.invalid_file_name))
                                }
                            }
                        }
                    ) {
                        Text(stringResource(R.string.confirm))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showCustomDateDialog = false }) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            )
        }

        PreferenceItem(
            label = stringResource(R.string.date_time_format),
            supportingText = "Format for file modification timestamps (${prefs.dateTimeFormat})",
            icon = Icons.Rounded.CalendarToday,
            onClick = {
                val selectedIndex = commonDateFormat.indexOf(prefs.dateTimeFormat).let {
                    if (it >= 0) it else commonDateFormat.lastIndex
                }
                prefs.singleChoiceDialog.show(
                    title = globalClass.getString(R.string.date_time_format),
                    description = globalClass.getString(R.string.select_date_format),
                    choices = commonDateFormat,
                    selectedChoice = selectedIndex,
                    onSelect = {
                        if (it == commonDateFormat.lastIndex) {
                            customDateInput = prefs.dateTimeFormat
                            showCustomDateDialog = true
                        } else {
                            prefs.dateTimeFormat = commonDateFormat[it]
                        }
                    }
                )
            }
        )

        PreferenceDivider()

        PreferenceItem(
            label = "12-hour time format",
            supportingText = if (prefs.use12HourFormat) "Display timestamps in 12-hour AM/PM format" else "Display timestamps in 24-hour format",
            icon = Icons.Rounded.AccessTime,
            switchState = prefs.use12HourFormat,
            onSwitchChange = { prefs.use12HourFormat = it }
        )

        PreferenceDivider()

        PreferenceItem(
            label = "Hide main toolbar",
            supportingText = "Hide the top application bar to maximize screen space for files",
            icon = Icons.Rounded.VerticalAlignTop,
            switchState = prefs.hideToolbar,
            onSwitchChange = { prefs.hideToolbar = it }
        )

        PreferenceDivider()

        PreferenceItem(
            label = "Auto-hide toolbars on scroll (Portrait)",
            supportingText = "Collapse the toolbar and tab bar when scrolling down in portrait mode",
            icon = Icons.Rounded.UnfoldLess,
            switchState = prefs.autoHideToolbars,
            onSwitchChange = { prefs.autoHideToolbars = it }
        )

        PreferenceDivider()

        PreferenceItem(
            label = "Auto-hide toolbars on scroll (Landscape)",
            supportingText = "Collapse the toolbar and tab bar when scrolling down in landscape mode",
            icon = Icons.Rounded.ScreenRotation,
            switchState = prefs.autoHideToolbarsLandscape,
            onSwitchChange = { prefs.autoHideToolbarsLandscape = it }
        )

        PreferenceDivider()

        PreferenceItem(
            label = stringResource(R.string.move_toolbar_to_bottom),
            supportingText = "Position the main navigation toolbar at the bottom of the screen for easier one-handed reach",
            icon = Icons.Rounded.CallToAction,
            switchState = prefs.moveToolbarToBottom,
            onSwitchChange = { prefs.moveToolbarToBottom = it }
        )

        PreferenceDivider()

        PreferenceItem(
            label = stringResource(R.string.move_tabs_to_bottom),
            supportingText = "Position the tab bar at the bottom of the screen above the navigation bar",
            icon = Icons.Rounded.BorderBottom,
            switchState = prefs.moveTabsToBottom,
            onSwitchChange = { prefs.moveTabsToBottom = it }
        )

        PreferenceDivider()

        PreferenceItem(
            label = stringResource(R.string.enable_bottom_toolbar),
            supportingText = stringResource(R.string.enable_bottom_toolbar_desc),
            icon = Icons.Rounded.Dock,
            switchState = prefs.enableBottomToolbar,
            onSwitchChange = { prefs.enableBottomToolbar = it }
        )

        if (prefs.enableBottomToolbar) {
            PreferenceDivider()

            PreferenceItem(
                label = stringResource(R.string.show_bottom_bar_labels),
                supportingText = "Display text captions underneath icons in the persistent bottom toolbar",
                icon = Icons.AutoMirrored.Rounded.Label,
                switchState = prefs.showBottomBarLabels,
                onSwitchChange = { prefs.showBottomBarLabels = it }
            )
        }

        PreferenceDivider()

        PreferenceItem(
            label = stringResource(R.string.selection_ribbon_two_rows),
            supportingText = stringResource(R.string.selection_ribbon_two_rows_desc),
            icon = Icons.Rounded.ViewStream,
            switchState = prefs.selectionRibbonTwoRows,
            onSwitchChange = { prefs.selectionRibbonTwoRows = it }
        )

        PreferenceDivider()

        PreferenceItem(
            label = stringResource(R.string.customize_selection_ribbon),
            supportingText = stringResource(R.string.customize_selection_ribbon_desc),
            icon = Icons.Rounded.Tune,
            onClick = { showSelectionRibbonSettingsDialog = true }
        )
        if (showSelectionRibbonSettingsDialog) {
            SelectionRibbonSettingsDialog(onDismiss = { showSelectionRibbonSettingsDialog = false })
        }
    }
}
