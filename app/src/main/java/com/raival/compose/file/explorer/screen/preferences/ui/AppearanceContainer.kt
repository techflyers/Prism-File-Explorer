package com.raival.compose.file.explorer.screen.preferences.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.AltRoute
import androidx.compose.material.icons.automirrored.rounded.Label
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Nightlight
import androidx.compose.material.icons.rounded.VerticalAlignBottom
import androidx.compose.material.icons.rounded.VerticalAlignTop
import androidx.compose.material.icons.rounded.UnfoldLess
import androidx.compose.material.icons.rounded.ViewHeadline
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
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
import com.raival.compose.file.explorer.App.Companion.globalClass
import com.raival.compose.file.explorer.R
import com.raival.compose.file.explorer.common.emptyString
import com.raival.compose.file.explorer.common.showMsg
import com.raival.compose.file.explorer.common.ui.Space
import com.raival.compose.file.explorer.screen.preferences.constant.ThemePreference
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun AppearanceContainer() {
    val prefs = globalClass.preferencesManager

    Container(title = stringResource(R.string.appearance)) {
        PreferenceItem(
            label = stringResource(R.string.theme),
            supportingText = when (prefs.theme) {
                ThemePreference.LIGHT.ordinal -> stringResource(R.string.light)
                ThemePreference.DARK.ordinal -> stringResource(R.string.dark)
                else -> stringResource(R.string.follow_system)
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

        HorizontalDivider(
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            thickness = 3.dp
        )

        PreferenceItem(
            label = "Show directory path bar",
            supportingText = "Display breadcrumb path bar above the file list",
            icon = Icons.Rounded.FolderOpen,
            switchState = prefs.showPathBar,
            onSwitchChange = { prefs.showPathBar = it }
        )

        HorizontalDivider(
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            thickness = 3.dp
        )

        PreferenceItem(
            label = "Disable Tabs",
            supportingText = "Disable tab bar in favor of a compact single-row top bar",
            icon = Icons.Rounded.ViewHeadline,
            switchState = prefs.disableTabBar,
            onSwitchChange = { prefs.disableTabBar = it }
        )

        HorizontalDivider(
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            thickness = 3.dp
        )

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
            supportingText = prefs.dateTimeFormat,
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

        HorizontalDivider(
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            thickness = 3.dp
        )

        PreferenceItem(
            label = "12-hour time format",
            supportingText = if (prefs.use12HourFormat) "Using 12-hour format" else "Using 24-hour format",
            icon = Icons.Rounded.AccessTime,
            switchState = prefs.use12HourFormat,
            onSwitchChange = { prefs.use12HourFormat = it }
        )

        HorizontalDivider(
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            thickness = 3.dp
        )

        PreferenceItem(
            label = "Hide main toolbar",
            supportingText = emptyString,
            icon = Icons.Rounded.VerticalAlignTop,
            switchState = prefs.hideToolbar,
            onSwitchChange = { prefs.hideToolbar = it }
        )

        HorizontalDivider(
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            thickness = 3.dp
        )

        PreferenceItem(
            label = "Auto-hide toolbars on scroll",
            supportingText = "Toolbar and tab bar hide when scrolling down, reappear on scroll up",
            icon = Icons.Rounded.UnfoldLess,
            switchState = prefs.autoHideToolbars,
            onSwitchChange = { prefs.autoHideToolbars = it }
        )

        HorizontalDivider(
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            thickness = 3.dp
        )

        PreferenceItem(
            label = stringResource(R.string.move_toolbar_to_bottom),
            supportingText = emptyString,
            icon = Icons.Rounded.VerticalAlignBottom,
            switchState = prefs.moveToolbarToBottom,
            onSwitchChange = { prefs.moveToolbarToBottom = it }
        )

        HorizontalDivider(
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            thickness = 3.dp
        )

        PreferenceItem(
            label = stringResource(R.string.move_tabs_to_bottom),
            supportingText = emptyString,
            icon = Icons.Rounded.VerticalAlignBottom,
            switchState = prefs.moveTabsToBottom,
            onSwitchChange = { prefs.moveTabsToBottom = it }
        )

        HorizontalDivider(
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            thickness = 3.dp
        )

        PreferenceItem(
            label = stringResource(R.string.enable_bottom_toolbar),
            supportingText = stringResource(R.string.enable_bottom_toolbar_desc),
            icon = Icons.Rounded.VerticalAlignBottom,
            switchState = prefs.enableBottomToolbar,
            onSwitchChange = { prefs.enableBottomToolbar = it }
        )

        if (prefs.enableBottomToolbar) {
            HorizontalDivider(
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                thickness = 3.dp
            )

            PreferenceItem(
                label = stringResource(R.string.show_bottom_bar_labels),
                supportingText = emptyString,
                icon = Icons.AutoMirrored.Rounded.Label,
                switchState = prefs.showBottomBarLabels,
                onSwitchChange = { prefs.showBottomBarLabels = it }
            )
        }
    }
}