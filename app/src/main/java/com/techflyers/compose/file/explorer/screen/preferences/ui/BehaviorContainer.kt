package com.techflyers.compose.file.explorer.screen.preferences.ui

import android.text.format.Formatter
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.CompareArrows
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Animation
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.FindReplace
import androidx.compose.material.icons.rounded.FlipToBack
import androidx.compose.material.icons.rounded.Gesture
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LowPriority
import androidx.compose.material.icons.rounded.OpenInBrowser
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material.icons.rounded.SmartDisplay
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.SwipeVertical
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.VideoFile
import androidx.compose.material.icons.rounded.VpnKey
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.techflyers.compose.file.explorer.App.Companion.globalClass
import com.techflyers.compose.file.explorer.R
import com.techflyers.compose.file.explorer.office.OfficeEngine
import com.techflyers.compose.file.explorer.office.OfficeEngineMode
import com.techflyers.compose.file.explorer.office.OfficeEngineState
import com.techflyers.compose.file.explorer.screen.preferences.constant.DragDropAction
import com.techflyers.compose.file.explorer.screen.preferences.constant.VideoDecoderMode

@Composable
fun BehaviorContainer() {
    val prefs = globalClass.preferencesManager
    val context = LocalContext.current

    val defaultOpeningMethodsList = remember(prefs.defaultOpeningMethods) {
        prefs.getDefaultOpeningMethodsList()
    }
    var showDefaultAppsDialog by remember { mutableStateOf(false) }

    if (showDefaultAppsDialog) {
        DefaultAppsDialog(
            show = true,
            onDismissRequest = { showDefaultAppsDialog = false }
        )
    }

    Container(
        title = stringResource(R.string.behavior),
        icon = Icons.Rounded.Tune
    ) {
        PreferenceItem(
            label = stringResource(R.string.default_opening_methods),
            supportingText = if (defaultOpeningMethodsList.isNotEmpty()) {
                stringResource(R.string.default_opening_methods_count, defaultOpeningMethodsList.size)
            } else {
                stringResource(R.string.no_default_opening_methods)
            },
            icon = Icons.AutoMirrored.Rounded.OpenInNew,
            onClick = { showDefaultAppsDialog = true }
        )

        PreferenceDivider()

        PreferenceItem(
            label = stringResource(R.string.show_files_options_menu_on_long_click),
            supportingText = "Open the file actions menu when long-pressing an already-selected item",
            icon = Icons.Rounded.TouchApp,
            switchState = prefs.showFileOptionMenuOnLongClick,
            onSwitchChange = { prefs.showFileOptionMenuOnLongClick = it }
        )

        PreferenceDivider()

        PreferenceItem(
            label = "Do not focus extension field on Enter",
            supportingText = "Pressing Enter in the filename field will not move focus to the extension text field",
            icon = Icons.Rounded.Gesture,
            switchState = prefs.disableEnterFocusExtension,
            onSwitchChange = { prefs.disableEnterFocusExtension = it }
        )

        PreferenceDivider()

        PreferenceItem(
            label = "Range selection using long presses",
            supportingText = "Long-press one item and then long-press another item to select all items between them",
            icon = Icons.Rounded.SelectAll,
            switchState = prefs.twoTapLongPressRangeSelection,
            onSwitchChange = { prefs.twoTapLongPressRangeSelection = it }
        )

        PreferenceDivider()

        PreferenceItem(
            label = stringResource(R.string.drag_and_drop_action),
            supportingText = when (prefs.dragDropAction) {
                DragDropAction.ALWAYS_MOVE.ordinal -> stringResource(R.string.drag_drop_move)
                DragDropAction.ALWAYS_COPY.ordinal -> stringResource(R.string.drag_drop_copy)
                else -> stringResource(R.string.drag_drop_ask)
            },
            icon = Icons.Rounded.LowPriority,
            onClick = {
                prefs.singleChoiceDialog.show(
                    title = globalClass.getString(R.string.drag_and_drop_action),
                    description = globalClass.getString(R.string.drag_and_drop_action_desc),
                    choices = listOf(
                        globalClass.getString(R.string.drag_drop_ask),
                        globalClass.getString(R.string.drag_drop_copy),
                        globalClass.getString(R.string.drag_drop_move)
                    ),
                    selectedChoice = prefs.dragDropAction,
                    onSelect = { prefs.dragDropAction = it }
                )
            }
        )

        PreferenceDivider()

        val hashChoices = listOf(
            "xxHash3 (Fast non-cryptographic comparison)",
            "SHA-256 (Cryptographic integrity verification)",
            "MD5 (Legacy compatibility checksum)"
        )
        val hashKeys = listOf("xxHash3", "SHA-256", "MD5")
        val currentHashIdx = hashKeys.indexOf(prefs.comparisonHashAlgorithm).takeIf { it >= 0 } ?: 0

        PreferenceItem(
            label = "Directory comparison hash algorithm",
            supportingText = hashChoices[currentHashIdx],
            icon = Icons.AutoMirrored.Rounded.CompareArrows,
            onClick = {
                prefs.singleChoiceDialog.show(
                    title = "Directory Comparison Hash Algorithm",
                    description = "xxHash3 is recommended for fast routine comparisons. SHA-256 is always available on demand for cryptographic integrity checks.",
                    choices = hashChoices,
                    selectedChoice = currentHashIdx,
                    onSelect = { idx ->
                        prefs.comparisonHashAlgorithm = hashKeys.getOrElse(idx) { "xxHash3" }
                    }
                )
            }
        )

        PreferenceDivider()

        PreferenceItem(
            label = stringResource(R.string.show_video_duration),
            supportingText = stringResource(R.string.show_video_duration_desc),
            icon = Icons.Rounded.VideoFile,
            switchState = prefs.showVideoDuration,
            onSwitchChange = { prefs.showVideoDuration = it }
        )

        PreferenceDivider()

        PreferenceItem(
            label = stringResource(R.string.disable_pull_down_to_refresh),
            supportingText = "Disable the pull-down gesture that reloads the current directory",
            icon = Icons.Rounded.Refresh,
            switchState = prefs.disablePullDownToRefresh,
            onSwitchChange = { prefs.disablePullDownToRefresh = it }
        )

        PreferenceDivider()

        PreferenceItem(
            label = "Disable navigation gestures",
            supportingText = "Turn off swipe shortcuts in the file list and tab bar",
            icon = Icons.Rounded.Gesture,
            switchState = prefs.disableNavigationGestures,
            onSwitchChange = { prefs.disableNavigationGestures = it }
        )

        val searchQuery = LocalPreferenceSearchQuery.current.trim()
        if (!prefs.disableNavigationGestures &&
            (searchQuery.isEmpty() || matchesPreferenceQuery(searchQuery, "Middle Swipe Area Width", "swipe", "sub-categories"))
        ) {
            PreferenceDivider()

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Middle Swipe Area Width (${prefs.middleSwipeZoneWidthPercent}%)",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f)
                    )
                }
                Text(
                    text = "Center zone swipes between sub-categories; left and right zones swipe between main tabs",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp
                )
                Slider(
                    value = prefs.middleSwipeZoneWidthPercent.toFloat(),
                    onValueChange = { prefs.middleSwipeZoneWidthPercent = it.toInt().coerceIn(20, 70) },
                    valueRange = 20f..70f
                )
            }
        }

        PreferenceDivider()

        PreferenceItem(
            label = "Disable spring / bounce effect",
            supportingText = "Remove elastic overscroll bounce from file lists and pull-to-refresh",
            icon = Icons.Rounded.SwipeVertical,
            switchState = prefs.disableSpringEffect,
            onSwitchChange = { prefs.disableSpringEffect = it }
        )

        PreferenceDivider()

        PreferenceItem(
            label = stringResource(R.string.skip_home_when_tab_closed),
            supportingText = "Switch directly to the adjacent file tab instead of returning to the Home tab when closing a tab",
            icon = Icons.Rounded.Home,
            switchState = prefs.skipHomeWhenTabClosed,
            onSwitchChange = { prefs.skipHomeWhenTabClosed = it }
        )

        PreferenceDivider()

        PreferenceItem(
            label = stringResource(R.string.close_tab_on_back_nav),
            supportingText = "Close the current tab when pressing Back at the root of its navigation history",
            icon = Icons.Rounded.FlipToBack,
            switchState = prefs.closeTabOnBackPress,
            onSwitchChange = { prefs.closeTabOnBackPress = it }
        )

        PreferenceDivider()

        PreferenceItem(
            label = stringResource(R.string.remember_last_session),
            supportingText = stringResource(R.string.remember_last_session_desc),
            icon = Icons.Rounded.Restore,
            switchState = prefs.rememberLastSession,
            onSwitchChange = { prefs.rememberLastSession = it }
        )

        PreferenceDivider()

        PreferenceItem(
            label = stringResource(R.string.confirm_before_exit),
            supportingText = "Prompt for confirmation before closing Prism when pressing Back on the final tab",
            icon = Icons.Rounded.Warning,
            switchState = prefs.confirmBeforeAppClose,
            onSwitchChange = { prefs.confirmBeforeAppClose = it }
        )

        PreferenceDivider()

        PreferenceItem(
            label = stringResource(R.string.use_builtin_viewers),
            supportingText = stringResource(R.string.use_builtin_viewers_desc),
            icon = Icons.Rounded.OpenInBrowser,
            switchState = prefs.useBuiltInViewer,
            onSwitchChange = { prefs.useBuiltInViewer = it }
        )

        PreferenceDivider()

        // Document Viewer / LibreOffice Engine Preferences
        val installer = remember(context) { OfficeEngine.installer(context) }
        val engineState by installer.state.collectAsState()
        var currentEngineModeOrd by remember {
            mutableIntStateOf(OfficeEngine.getEngineMode(context).ordinal)
        }
        var showResetEngineConfirm by remember { mutableStateOf(false) }

        val engineModeChoices = listOf(
            "Ask on demand (use LibreOffice when installed)",
            "Prefer LibreOffice Engine (high fidelity)",
            "Always use Built-in Viewer (fast fallback)"
        )

        PreferenceItem(
            label = "Default Office Document Engine",
            supportingText = engineModeChoices.getOrElse(currentEngineModeOrd) { engineModeChoices[0] },
            icon = Icons.Rounded.Description,
            onClick = {
                prefs.singleChoiceDialog.show(
                    title = "Default Office Document Engine",
                    description = "Choose which rendering engine to use when opening Office documents (.docx, .pptx, .xlsx, .doc, .ppt, .xls)",
                    choices = engineModeChoices,
                    selectedChoice = currentEngineModeOrd,
                    onSelect = { selected ->
                        currentEngineModeOrd = selected
                        val mode = OfficeEngineMode.entries.getOrElse(selected) { OfficeEngineMode.ASK }
                        OfficeEngine.setEngineMode(context, mode)
                    }
                )
            }
        )

        PreferenceDivider()

        val engineStatusText = when (val st = engineState) {
            is OfficeEngineState.Installed ->
                "Installed (v${st.version} • ${Formatter.formatFileSize(context, st.sizeBytes)}) — Tap to reset or remove"
            is OfficeEngineState.Downloading -> {
                val pct = if (st.total > 0) ((st.downloaded * 100) / st.total).toInt() else 0
                if (st.waitingForNetwork) "Waiting for network… (Tap to cancel/reset)"
                else "Downloading ($pct%) — Tap to cancel/reset"
            }
            is OfficeEngineState.Installing ->
                "Installing engine… (Tap to cancel/reset)"
            is OfficeEngineState.Failed ->
                "Failed: ${st.message} — Tap to reset or retry"
            is OfficeEngineState.Unsupported ->
                "Not supported on this device architecture"
            else ->
                "Not installed — Tap to download (~45 MB) or reset state"
        }

        PreferenceItem(
            label = "LibreOffice Document Engine",
            supportingText = engineStatusText,
            icon = if (engineState is OfficeEngineState.NotInstalled) Icons.Rounded.CloudDownload else Icons.Rounded.Build,
            onClick = {
                when (engineState) {
                    is OfficeEngineState.NotInstalled, is OfficeEngineState.Failed -> {
                        installer.install()
                        globalClass.showMsg("Started LibreOffice engine download")
                    }
                    else -> {
                        showResetEngineConfirm = true
                    }
                }
            }
        )

        PreferenceDivider()

        PreferenceItem(
            label = "Reset LibreOffice Engine",
            supportingText = "Cancel stuck downloads, clear cached engine files, and restore the viewer prompt",
            icon = Icons.Rounded.RestartAlt,
            onClick = { showResetEngineConfirm = true }
        )

        if (showResetEngineConfirm) {
            AlertDialog(
                onDismissRequest = { showResetEngineConfirm = false },
                title = { Text("Reset LibreOffice Engine?") },
                text = {
                    Text(
                        "This will cancel any active or stuck LibreOffice download, remove partial or installed engine files and conversion cache, and restore the prompt to choose between LibreOffice and the built-in viewer."
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showResetEngineConfirm = false
                            OfficeEngine.resetEngine(context)
                            globalClass.showMsg("LibreOffice engine reset successfully")
                        }
                    ) {
                        Text("Reset", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showResetEngineConfirm = false }) {
                        Text(stringResource(android.R.string.cancel))
                    }
                }
            )
        }

        PreferenceDivider()

        PreferenceItem(
            label = stringResource(R.string.auto_hide_video_controls),
            supportingText = stringResource(R.string.auto_hide_video_controls_summary),
            icon = Icons.Rounded.SmartDisplay,
            switchState = prefs.autoHideVideoControls,
            onSwitchChange = { prefs.autoHideVideoControls = it }
        )

        PreferenceDivider()

        val decoderChoices = listOf(
            "Auto (Hardware first, fallback to Software on error)",
            "Software (FFmpeg / CPU priority)",
            "Hardware (MediaCodec / GPU priority)"
        )
        val currentDecoderIdx = prefs.videoDecoderMode.coerceIn(0, decoderChoices.lastIndex)

        PreferenceItem(
            label = "Media Decoder Mode",
            supportingText = decoderChoices[currentDecoderIdx],
            icon = Icons.Rounded.VideoFile,
            onClick = {
                prefs.singleChoiceDialog.show(
                    title = "Media Decoder Mode",
                    description = "Auto starts with Hardware (MediaCodec) for high performance and power efficiency, and automatically falls back to the bundled FFmpeg software decoder when a codec or profile is unsupported.",
                    choices = decoderChoices,
                    selectedChoice = currentDecoderIdx,
                    onSelect = { idx ->
                        prefs.videoDecoderMode = VideoDecoderMode.fromOrdinal(idx).ordinal
                    }
                )
            }
        )

        PreferenceDivider()

        PreferenceItem(
            label = stringResource(R.string.disable_magika_ext_override),
            supportingText = stringResource(R.string.disable_magika_ext_override_desc),
            icon = Icons.Rounded.FindReplace,
            switchState = prefs.disableMagikaExtOverride,
            onSwitchChange = { prefs.disableMagikaExtOverride = it }
        )

        PreferenceDivider()

        PreferenceItem(
            label = stringResource(R.string.hide_root_storage),
            supportingText = stringResource(R.string.hide_root_storage_desc),
            icon = Icons.Rounded.Storage,
            switchState = prefs.hideRootStorage,
            onSwitchChange = { prefs.hideRootStorage = it }
        )

        PreferenceDivider()

        val showApiKeyDialogState = remember { mutableStateOf(false) }

        PreferenceItem(
            label = "Convertio API Key",
            supportingText = if (prefs.convertioApiKey.isBlank()) "Configure API key for online document and PDF conversion" else "Key configured: " + "•".repeat(prefs.convertioApiKey.length.coerceAtMost(8)),
            icon = Icons.Rounded.VpnKey,
            onClick = { showApiKeyDialogState.value = true }
        )

        if (showApiKeyDialogState.value) {
            com.techflyers.compose.file.explorer.common.ConvertioApiKeyDialog(
                onDismiss = { showApiKeyDialogState.value = false },
                onConfirm = { key ->
                    prefs.convertioApiKey = key
                    showApiKeyDialogState.value = false
                }
            )
        }
    }
}
