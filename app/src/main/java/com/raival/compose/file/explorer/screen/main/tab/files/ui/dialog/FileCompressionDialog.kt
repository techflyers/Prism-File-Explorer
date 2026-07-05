package com.raival.compose.file.explorer.screen.main.tab.files.ui.dialog

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import com.raival.compose.file.explorer.App.Companion.globalClass
import com.raival.compose.file.explorer.R
import com.raival.compose.file.explorer.common.emptyString
import com.raival.compose.file.explorer.common.isValidAsFileName
import com.raival.compose.file.explorer.common.ui.Space
import com.raival.compose.file.explorer.screen.main.tab.files.FilesTab
import com.raival.compose.file.explorer.screen.main.tab.files.holder.LocalFileHolder
import com.raival.compose.file.explorer.screen.main.tab.files.task.CompressTaskParameters
import java.io.File

/**
 * Describes an output compression format shown in the format picker.
 *
 * @param label         Human-readable chip label shown in the UI.
 * @param extension     The file extension appended to the archive name (e.g. "7z", "tar.gz").
 * @param supportsPassword  Whether the format supports password protection.
 * @param supportsLevel     Whether a compression-level slider is meaningful.
 */
private data class CompressFormat(
    val label: String,
    val extension: String,
    val supportsPassword: Boolean = false,
    val supportsLevel: Boolean = true
)

private val COMPRESS_FORMATS = listOf(
    CompressFormat("ZIP",    "zip",    supportsPassword = true),
    CompressFormat("7Z",     "7z",     supportsPassword = true),
    CompressFormat("TAR",    "tar",    supportsLevel = false),
    CompressFormat("TAR.GZ", "tar.gz", supportsLevel = true),
    CompressFormat("TAR.BZ2","tar.bz2",supportsLevel = true),
    CompressFormat("TAR.XZ", "tar.xz", supportsLevel = true),
    CompressFormat("WIM",    "wim",    supportsLevel = false)
)

/** Strip any known compression extension suffix from [baseName] and append [newExtension]. */
private fun rebuildFileName(baseName: String, newExtension: String): String {
    // Remove known multi-part extensions first (tar.gz etc.)
    val multiPart = listOf("tar.gz", "tar.bz2", "tar.xz", "tar.zst")
    var stripped = baseName
    for (mp in multiPart) {
        if (baseName.endsWith(".$mp", ignoreCase = true)) {
            stripped = baseName.dropLast(mp.length + 1)
            break
        }
    }
    // If no multi-part match, strip the last single extension
    if (stripped == baseName && baseName.contains('.')) {
        stripped = baseName.substringBeforeLast('.')
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

        var selectedFormatIndex by remember { mutableStateOf(0) }
        val selectedFormat = COMPRESS_FORMATS[selectedFormatIndex]

        // Derive the initial base name from the active folder
        val baseFolderName = tab.activeFolder.displayName

        // File name — automatically updated when the format changes
        var newNameInput by remember {
            mutableStateOf("$baseFolderName.${COMPRESS_FORMATS[0].extension}")
        }

        var passwordInput by remember { mutableStateOf("") }
        var passwordVisible by remember { mutableStateOf(false) }
        var compressionLevel by remember { mutableFloatStateOf(5f) }
        var error by remember { mutableStateOf("") }

        val levelSteps = listOf(0f, 1f, 3f, 5f, 7f, 9f)
        val levelNames = listOf("Store", "Fastest", "Fast", "Normal", "Maximum", "Ultra")

        // When format changes → rebuild the file name and reset password if not supported
        LaunchedEffect(selectedFormatIndex) {
            newNameInput = rebuildFileName(newNameInput, selectedFormat.extension)
            if (!selectedFormat.supportsPassword) passwordInput = ""
        }

        // Validate file name on each keystroke
        LaunchedEffect(newNameInput) {
            error = if (newNameInput.isBlank()) {
                emptyString
            } else if (!newNameInput.isValidAsFileName()) {
                globalClass.getString(R.string.invalid_file_name)
            } else if (listContent.contains(newNameInput)) {
                globalClass.getString(R.string.similar_file_exists)
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
                    modifier = Modifier.padding(24.dp),
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
                    TextField(
                        modifier = Modifier.fillMaxWidth(),
                        value = newNameInput,
                        onValueChange = { newNameInput = it },
                        label = { Text(text = stringResource(R.string.name)) },
                        singleLine = true,
                        shape = RoundedCornerShape(6.dp),
                        colors = TextFieldDefaults.colors(
                            errorIndicatorColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            disabledIndicatorColor = Color.Transparent
                        ),
                        isError = error.isNotEmpty(),
                        supportingText = if (error.isNotEmpty()) {
                            { Text(error) }
                        } else null
                    )

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
                                if (newNameInput.isValidAsFileName() && !listContent.contains(newNameInput)) {
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
                                                compressionLevel.toInt() else 0
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