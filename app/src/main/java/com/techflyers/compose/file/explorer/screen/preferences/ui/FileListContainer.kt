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
import androidx.compose.material.icons.automirrored.rounded.ShortText
import androidx.compose.material.icons.automirrored.rounded.WrapText
import androidx.compose.material.icons.rounded.Badge
import androidx.compose.material.icons.rounded.Height
import androidx.compose.material.icons.rounded.HideSource
import androidx.compose.material.icons.rounded.Numbers
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.techflyers.compose.file.explorer.App.Companion.globalClass
import com.techflyers.compose.file.explorer.R
import com.techflyers.compose.file.explorer.common.emptyString
import com.techflyers.compose.file.explorer.screen.preferences.constant.FileItemSize

@Composable
fun FileListContainer() {
    val prefs = globalClass.preferencesManager

    var showEndCharsDialog by remember { mutableStateOf(false) }
    var showMaxLinesDialog by remember { mutableStateOf(false) }

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

    if (showMaxLinesDialog) {
        var linesInput by remember { mutableStateOf(prefs.filenameMaxLines.toString()) }
        AlertDialog(
            onDismissRequest = { showMaxLinesDialog = false },
            title = { Text(stringResource(R.string.filename_wrap_lines)) },
            text = {
                Column {
                    Text(
                        text = stringResource(R.string.filename_wrap_lines_desc),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    OutlinedTextField(
                        value = linesInput,
                        onValueChange = { linesInput = it.filter { c -> c.isDigit() }.take(1) },
                        label = { Text(stringResource(R.string.lines)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = stringResource(R.string.filename_wrap_lines_range),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    prefs.filenameMaxLines = linesInput.toIntOrNull()?.coerceIn(1, 5) ?: 1
                    showMaxLinesDialog = false
                }) { Text(stringResource(R.string.save)) }
            },
            dismissButton = {
                TextButton(onClick = { showMaxLinesDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    Container(title = stringResource(R.string.file_list)) {
        PreferenceItem(
            label = stringResource(R.string.file_list_size),
            supportingText = when (prefs.itemSize) {
                FileItemSize.EXTRA_SMALL.ordinal -> stringResource(R.string.extra_small)
                FileItemSize.SMALL.ordinal -> stringResource(R.string.small)
                FileItemSize.MEDIUM.ordinal -> stringResource(R.string.medium)
                FileItemSize.LARGE.ordinal -> stringResource(R.string.large)
                else -> stringResource(R.string.extra_large)
            },
            icon = Icons.Rounded.Height,
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

        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainerLow, thickness = 3.dp)

        PreferenceItem(
            label = stringResource(R.string.show_hidden_files),
            supportingText = emptyString,
            icon = Icons.Rounded.HideSource,
            switchState = prefs.showHiddenFiles,
            onSwitchChange = { prefs.showHiddenFiles = it }
        )

        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainerLow, thickness = 3.dp)

        PreferenceItem(
            label = stringResource(R.string.show_folder_s_content_count),
            supportingText = emptyString,
            icon = Icons.Rounded.Numbers,
            switchState = prefs.showFolderContentCount,
            onSwitchChange = { prefs.showFolderContentCount = it }
        )

        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainerLow, thickness = 3.dp)

        PreferenceItem(
            label = "Deep empty folder check",
            supportingText = "Show an unslashed null icon (○) for folders containing subfolders but no files within",
            icon = Icons.Rounded.RadioButtonUnchecked,
            switchState = prefs.deepEmptyFolderCheck,
            onSwitchChange = { prefs.deepEmptyFolderCheck = it }
        )

        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainerLow, thickness = 3.dp)

        PreferenceItem(
            label = "Hide File Extensions",
            supportingText = "Show file names without their extension suffix",
            icon = Icons.Rounded.HideSource,
            switchState = prefs.hideFileExtensions,
            onSwitchChange = { prefs.hideFileExtensions = it }
        )

        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainerLow, thickness = 3.dp)

        val endCharsText = if (prefs.filenameEndCharsCount <= 0) {
            stringResource(R.string.filename_end_characters_none)
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

        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainerLow, thickness = 3.dp)

        PreferenceItem(
            label = stringResource(R.string.filename_wrap_lines),
            supportingText = if (prefs.filenameMaxLines <= 1) {
                stringResource(R.string.filename_wrap_lines_off)
            } else {
                stringResource(R.string.filename_wrap_lines_n, prefs.filenameMaxLines)
            },
            icon = Icons.AutoMirrored.Rounded.WrapText,
            onClick = { showMaxLinesDialog = true }
        )

        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainerLow, thickness = 3.dp)

        PreferenceItem(
            label = stringResource(R.string.show_source_folder_badges),
            supportingText = stringResource(R.string.show_source_folder_badges_desc),
            icon = Icons.Rounded.Badge,
            switchState = prefs.showSourceBadges,
            onSwitchChange = { prefs.showSourceBadges = it }
        )
    }
}
