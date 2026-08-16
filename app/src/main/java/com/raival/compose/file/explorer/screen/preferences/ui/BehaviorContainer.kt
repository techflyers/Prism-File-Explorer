package com.raival.compose.file.explorer.screen.preferences.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Animation
import androidx.compose.material.icons.rounded.FindReplace
import androidx.compose.material.icons.rounded.FlipToBack
import androidx.compose.material.icons.rounded.Gesture
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.OpenInBrowser
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.SubdirectoryArrowLeft
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material.icons.rounded.VpnKey
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import com.raival.compose.file.explorer.App.Companion.globalClass
import com.raival.compose.file.explorer.R
import com.raival.compose.file.explorer.common.emptyString

@Composable
fun BehaviorContainer() {
    val prefs = globalClass.preferencesManager

    Container(title = stringResource(R.string.behavior)) {
        PreferenceItem(
            label = stringResource(R.string.show_files_options_menu_on_long_click),
            supportingText = emptyString,
            icon = Icons.Rounded.TouchApp,
            switchState = prefs.showFileOptionMenuOnLongClick,
            onSwitchChange = { prefs.showFileOptionMenuOnLongClick = it }
        )

        HorizontalDivider(
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            thickness = 3.dp
        )

        PreferenceItem(
            label = stringResource(R.string.disable_pull_down_to_refresh),
            supportingText = emptyString,
            icon = Icons.Rounded.Refresh,
            switchState = prefs.disablePullDownToRefresh,
            onSwitchChange = { prefs.disablePullDownToRefresh = it }
        )

        HorizontalDivider(
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            thickness = 3.dp
        )

        PreferenceItem(
            label = "Disable Navigation Gestures",
            supportingText = "Turn off swipe-up shortcuts in the file list",
            icon = Icons.Rounded.Gesture,
            switchState = prefs.disableNavigationGestures,
            onSwitchChange = { prefs.disableNavigationGestures = it }
        )

        HorizontalDivider(
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            thickness = 3.dp
        )

        PreferenceItem(
            label = "Disable Spring / Bounce Effect",
            supportingText = "Remove elastic bounce from pull-to-refresh",
            icon = Icons.Rounded.Refresh,
            switchState = prefs.disableSpringEffect,
            onSwitchChange = { prefs.disableSpringEffect = it }
        )

        HorizontalDivider(
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            thickness = 3.dp
        )

        PreferenceItem(
            label = "Show \"..\" Parent Folder Entry",
            supportingText = "Show a \'..\' row at the top of file lists to navigate up",
            icon = Icons.Rounded.SubdirectoryArrowLeft,
            switchState = prefs.showParentDirectoryEntry,
            onSwitchChange = { prefs.showParentDirectoryEntry = it }
        )

        HorizontalDivider(
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            thickness = 3.dp
        )

        PreferenceItem(
            label = stringResource(R.string.skip_home_when_tab_closed),
            supportingText = emptyString,
            icon = Icons.Rounded.Home,
            switchState = prefs.skipHomeWhenTabClosed,
            onSwitchChange = { prefs.skipHomeWhenTabClosed = it }
        )

        HorizontalDivider(
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            thickness = 3.dp
        )

        PreferenceItem(
            label = stringResource(R.string.close_tab_on_back_nav),
            supportingText = emptyString,
            icon = Icons.Rounded.FlipToBack,
            switchState = prefs.closeTabOnBackPress,
            onSwitchChange = { prefs.closeTabOnBackPress = it }
        )

        HorizontalDivider(
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            thickness = 3.dp
        )

        PreferenceItem(
            label = stringResource(R.string.remember_last_session),
            supportingText = stringResource(R.string.remember_last_session_desc),
            icon = Icons.Rounded.Restore,
            switchState = prefs.rememberLastSession,
            onSwitchChange = { prefs.rememberLastSession = it }
        )

        HorizontalDivider(
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            thickness = 3.dp
        )

        PreferenceItem(
            label = stringResource(R.string.confirm_before_exit),
            supportingText = emptyString,
            icon = Icons.Rounded.Warning,
            switchState = prefs.confirmBeforeAppClose,
            onSwitchChange = { prefs.confirmBeforeAppClose = it }
        )

        PreferenceItem(
            label = stringResource(R.string.use_builtin_viewers),
            supportingText = stringResource(R.string.use_builtin_viewers_desc),
            icon = Icons.Rounded.OpenInBrowser,
            switchState = prefs.useBuiltInViewer,
            onSwitchChange = { prefs.useBuiltInViewer = it }
        )

        HorizontalDivider(
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            thickness = 3.dp
        )

        PreferenceItem(
            label = stringResource(R.string.disable_magika_ext_override),
            supportingText = stringResource(R.string.disable_magika_ext_override_desc),
            icon = Icons.Rounded.FindReplace,
            switchState = prefs.disableMagikaExtOverride,
            onSwitchChange = { prefs.disableMagikaExtOverride = it }
        )

        HorizontalDivider(
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            thickness = 3.dp
        )

        PreferenceItem(
            label = stringResource(R.string.hide_root_storage),
            supportingText = stringResource(R.string.hide_root_storage_desc),
            icon = Icons.Rounded.Storage,
            switchState = prefs.hideRootStorage,
            onSwitchChange = { prefs.hideRootStorage = it }
        )

        HorizontalDivider(
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            thickness = 3.dp
        )

        val showApiKeyDialogState = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }

        PreferenceItem(
            label = "Convertio API Key",
            supportingText = if (prefs.convertioApiKey.isBlank()) "No key set (used for PDF conversion)" else "Key: " + "•".repeat(prefs.convertioApiKey.length.coerceAtMost(8)),
            icon = Icons.Rounded.VpnKey,
            onClick = { showApiKeyDialogState.value = true }
        )

        if (showApiKeyDialogState.value) {
            com.raival.compose.file.explorer.common.ConvertioApiKeyDialog(
                onDismiss = { showApiKeyDialogState.value = false },
                onConfirm = { key ->
                    prefs.convertioApiKey = key
                    showApiKeyDialogState.value = false
                }
            )
        }
    }
}