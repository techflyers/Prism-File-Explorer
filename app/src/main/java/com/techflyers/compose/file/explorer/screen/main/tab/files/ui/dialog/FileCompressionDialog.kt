package com.techflyers.compose.file.explorer.screen.main.tab.files.ui.dialog

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.techflyers.compose.file.explorer.App.Companion.globalClass
import com.techflyers.compose.file.explorer.R
import com.techflyers.compose.file.explorer.common.emptyString
import com.techflyers.compose.file.explorer.common.isValidAsFileName
import com.techflyers.compose.file.explorer.common.ui.FilenameExtensionInput
import com.techflyers.compose.file.explorer.common.ui.Space
import com.techflyers.compose.file.explorer.common.ui.autoShowKeyboard
import com.techflyers.compose.file.explorer.screen.main.tab.files.FilesTab
import com.techflyers.compose.file.explorer.screen.main.tab.files.holder.LocalFileHolder
import com.techflyers.compose.file.explorer.screen.main.tab.files.task.CompressTaskParameters
import java.io.File

/**
 * Describes an output compression format shown in the format picker.
 *
 * @param label             Human-readable chip label shown in the UI.
 * @param extension         The file extension appended to the archive name (e.g. "7z", "tar.gz").
 * @param supportsPassword  Whether the format supports password protection.
 * @param supportsLevel     Whether a compression-level slider is meaningful.
 * @param supportsSplit     Whether splitting into multi-part volumes (-v) is supported.
 */
private data class CompressFormat(
    val label: String,
    val extension: String,
    val supportsPassword: Boolean = false,
    val supportsLevel: Boolean = true,
    val supportsSplit: Boolean = true
)

private val COMPRESS_FORMATS = listOf(
    CompressFormat("ZIP",     "zip",     supportsPassword = true),
    CompressFormat("7Z",      "7z",      supportsPassword = true),
    CompressFormat("TAR",     "tar",     supportsLevel = false),
    CompressFormat("TAR.GZ",  "tar.gz",  supportsLevel = true),
    CompressFormat("TAR.BZ2", "tar.bz2", supportsLevel = true),
    CompressFormat("TAR.XZ",  "tar.xz",  supportsLevel = true),
    CompressFormat("WIM",     "wim",     supportsLevel = false)
)

private data class SplitVolumePreset(
    val label: String,
    val spec: String? // null = Single file, "custom" = Custom input, otherwise 7za -v spec like "10m"
)

private val SPLIT_PRESETS = listOf(
    SplitVolumePreset("Single File", null),
    SplitVolumePreset("10 MB", "10m"),
    SplitVolumePreset("25 MB", "25m"),
    SplitVolumePreset("100 MB", "100m"),
    SplitVolumePreset("500 MB", "500m"),
    SplitVolumePreset("1 GB", "1g"),
    SplitVolumePreset("2 GB", "2g"),
    SplitVolumePreset("4 GB (FAT32)", "4095m"),
    SplitVolumePreset("Custom", "custom")
)

private val SPLIT_UNITS = listOf("MB" to "m", "GB" to "g", "KB" to "k")

/** Strip any known compression extension suffix from [baseName] and append [newExtension]. */
private fun rebuildFileName(baseName: String, newExtension: String): String {
    val multiPart = listOf("tar.gz", "tar.bz2", "tar.xz", "tar.zst")
    var stripped = baseName.removeSuffix(".001")
    for (mp in multiPart) {
        if (stripped.endsWith(".$mp", ignoreCase = true)) {
            stripped = stripped.dropLast(mp.length + 1)
            break
        }
    }
    if (stripped == baseName.removeSuffix(".001") && stripped.contains('.')) {
        stripped = stripped.substringBeforeLast('.')
    }
    return "$stripped.$newExtension"
}

@Composable
fun FileCompressionDialog(
    show: Boolean,
    tab: FilesTab,
    onDismissRequest: () -> Unit
) {
    if (show) {
        val listContent by remember(tab.activeFolderContent) {
            mutableStateOf(tab.activeFolderContent.map { it.displayName }.toTypedArray())
        }

        var selectedFormatIndex by remember { mutableIntStateOf(0) }
        val selectedFormat = COMPRESS_FORMATS[selectedFormatIndex]

        val baseFolderName = tab.activeFolder.displayName

        var newNameInput by remember {
            mutableStateOf("$baseFolderName.${COMPRESS_FORMATS[0].extension}")
        }

        var passwordInput by remember { mutableStateOf("") }
        var passwordVisible by remember { mutableStateOf(false) }
        var compressionLevel by remember { mutableFloatStateOf(5f) }

        // Split into parts state
        var selectedSplitPresetIndex by remember { mutableIntStateOf(0) }
        var customSplitSizeInput by remember { mutableStateOf("50") }
        var selectedSplitUnitIndex by remember { mutableIntStateOf(0) } // 0 = MB, 1 = GB, 2 = KB

        var error by remember { mutableStateOf("") }

        val levelSteps = listOf(0f, 1f, 3f, 5f, 7f, 9f)
        val levelNames = listOf("Store", "Fastest", "Fast", "Normal", "Maximum", "Ultra")

        val resolvedSplitSpec: String? = remember(
            selectedSplitPresetIndex,
            customSplitSizeInput,
            selectedSplitUnitIndex,
            selectedFormat.supportsSplit
        ) {
            if (!selectedFormat.supportsSplit) return@remember null
            val preset = SPLIT_PRESETS.getOrNull(selectedSplitPresetIndex) ?: return@remember null
            when (preset.spec) {
                null -> null
                "custom" -> {
                    val amount = customSplitSizeInput.trim().toLongOrNull()
                    if (amount != null && amount > 0L) {
                        val unitSuffix = SPLIT_UNITS[selectedSplitUnitIndex].second
                        "$amount$unitSuffix"
                    } else null
                }
                else -> preset.spec
            }
        }

        // When format changes → rebuild the file name and reset password if not supported
        LaunchedEffect(selectedFormatIndex) {
            newNameInput = rebuildFileName(newNameInput, selectedFormat.extension)
            if (!selectedFormat.supportsPassword) passwordInput = ""
        }

        // Validate file name on each keystroke
        LaunchedEffect(newNameInput, resolvedSplitSpec, selectedSplitPresetIndex, customSplitSizeInput) {
            val isCustomInvalid = SPLIT_PRESETS.getOrNull(selectedSplitPresetIndex)?.spec == "custom" &&
                    (customSplitSizeInput.trim().toLongOrNull() ?: 0L) <= 0L
            val effectiveCheckName = if (resolvedSplitSpec != null) "$newNameInput.001" else newNameInput

            error = if (newNameInput.isBlank()) {
                emptyString
            } else if (!newNameInput.isValidAsFileName()) {
                globalClass.getString(R.string.invalid_file_name)
            } else if (listContent.contains(effectiveCheckName) || listContent.contains(newNameInput)) {
                globalClass.getString(R.string.similar_file_exists)
            } else if (isCustomInvalid) {
                "Enter a valid part size greater than 0"
            } else {
                emptyString
            }
        }

        Dialog(onDismissRequest = onDismissRequest) {
            Card(
                shape = RoundedCornerShape(6.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(
                    modifier = Modifier
                        .padding(24.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // ── Title ──────────────────────────────────────────────────
                    Column {
                        Text(
                            modifier = Modifier.fillMaxWidth(),
                            text = stringResource(R.string.create_archive),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Space(8.dp)
                        HorizontalDivider(
                            thickness = 1.dp,
                            color = MaterialTheme.colorScheme.outlineVariant
                        )
                    }

                    // ── Format picker ─────────────────────────────────────────
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "Format",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Space(6.dp)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            COMPRESS_FORMATS.forEachIndexed { index, fmt ->
                                FilterChip(
                                    selected = selectedFormatIndex == index,
                                    onClick = { selectedFormatIndex = index },
                                    label = {
                                        Text(
                                            text = fmt.label,
                                            style = MaterialTheme.typography.labelMedium
                                        )
                                    }
                                )
                            }
                        }
                    }

                    // ── Archive name ──────────────────────────────────────────
                    FilenameExtensionInput(
                        initialFullName = newNameInput,
                        onFullNameChanged = { newNameInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        isFolder = false,
                        autoFocusName = true,
                        label = stringResource(R.string.name),
                        isError = error.isNotEmpty(),
                        supportingText = if (error.isNotEmpty()) {
                            { Text(error) }
                        } else if (resolvedSplitSpec != null) {
                            {
                                Text(
                                    text = "Parts: $newNameInput.001, $newNameInput.002, …",
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        } else null
                    )

                    // ── Split into parts (Multi-volume) ───────────────────────
                    if (selectedFormat.supportsSplit) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = "Split into Parts (Volumes)",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Space(6.dp)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                SPLIT_PRESETS.forEachIndexed { index, preset ->
                                    FilterChip(
                                        selected = selectedSplitPresetIndex == index,
                                        onClick = { selectedSplitPresetIndex = index },
                                        label = {
                                            Text(
                                                text = preset.label,
                                                style = MaterialTheme.typography.labelMedium
                                            )
                                        }
                                    )
                                }
                            }

                            if (SPLIT_PRESETS.getOrNull(selectedSplitPresetIndex)?.spec == "custom") {
                                Space(8.dp)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    TextField(
                                        modifier = Modifier.weight(1f),
                                        value = customSplitSizeInput,
                                        onValueChange = { input ->
                                            customSplitSizeInput = input.filter { it.isDigit() }
                                        },
                                        label = { Text("Part Size") },
                                        singleLine = true,
                                        shape = RoundedCornerShape(6.dp),
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        colors = TextFieldDefaults.colors(
                                            focusedIndicatorColor = Color.Transparent,
                                            unfocusedIndicatorColor = Color.Transparent,
                                            disabledIndicatorColor = Color.Transparent
                                        )
                                    )
                                    SPLIT_UNITS.forEachIndexed { unitIdx, (unitLabel, _) ->
                                        FilterChip(
                                            selected = selectedSplitUnitIndex == unitIdx,
                                            onClick = { selectedSplitUnitIndex = unitIdx },
                                            label = { Text(unitLabel) }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // ── Password (only for ZIP and 7Z) ────────────────────────
                    if (selectedFormat.supportsPassword) {
                        TextField(
                            modifier = Modifier.fillMaxWidth(),
                            value = passwordInput,
                            onValueChange = { passwordInput = it },
                            label = { Text("Password (Optional)") },
                            singleLine = true,
                            shape = RoundedCornerShape(6.dp),
                            visualTransformation = if (passwordVisible) VisualTransformation.None
                                                   else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            colors = TextFieldDefaults.colors(
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                disabledIndicatorColor = Color.Transparent
                            ),
                            trailingIcon = {
                                val image = if (passwordVisible) Icons.Filled.Visibility
                                            else Icons.Filled.VisibilityOff
                                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                    Icon(imageVector = image, contentDescription = "Toggle password visibility")
                                }
                            }
                        )
                    }

                    // ── Compression level (hidden for formats that don't support it) ──
                    if (selectedFormat.supportsLevel) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            val selectedIndex = remember(compressionLevel) {
                                levelSteps.indexOf(compressionLevel).coerceAtLeast(0)
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Compression Level",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = levelNames[selectedIndex],
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Slider(
                                value = selectedIndex.toFloat(),
                                onValueChange = {
                                    val idx = it.toInt().coerceIn(0, levelSteps.lastIndex)
                                    compressionLevel = levelSteps[idx]
                                },
                                valueRange = 0f..(levelSteps.size - 1).toFloat(),
                                steps = levelSteps.size - 2
                            )
                        }
                    }

                    // ── Action buttons ────────────────────────────────────────
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                if (newNameInput.isValidAsFileName() && error.isEmpty()) {
                                    onDismissRequest()
                                    globalClass.taskManager.runTask(
                                        tab.compressTaskHolder!!.id,
                                        CompressTaskParameters(
                                            destPath = File(
                                                (tab.activeFolder as LocalFileHolder).file,
                                                newNameInput
                                            ).absolutePath,
                                            password = passwordInput.ifEmpty { null },
                                            compressionLevel = if (selectedFormat.supportsLevel)
                                                compressionLevel.toInt() else 0,
                                            splitVolumeSize = resolvedSplitSpec
                                        )
                                    )
                                } else {
                                    globalClass.showMsg(R.string.invalid_file_name)
                                }
                            },
                            enabled = error.isEmpty() && newNameInput.isNotBlank(),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.create),
                                style = MaterialTheme.typography.labelLarge
                            )
                        }

                        OutlinedButton(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { onDismissRequest() },
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.minimize),
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                }
            }
        }
    }
}
