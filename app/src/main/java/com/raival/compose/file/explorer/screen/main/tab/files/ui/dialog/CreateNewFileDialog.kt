package com.raival.compose.file.explorer.screen.main.tab.files.ui.dialog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.raival.compose.file.explorer.App.Companion.globalClass
import com.raival.compose.file.explorer.R
import com.raival.compose.file.explorer.common.emptyString
import com.raival.compose.file.explorer.common.isValidAsFileName
import com.raival.compose.file.explorer.common.ui.CheckableText
import com.raival.compose.file.explorer.common.ui.Space
import com.raival.compose.file.explorer.common.ui.autoShowKeyboard
import com.raival.compose.file.explorer.screen.main.tab.files.FilesTab
import kotlinx.coroutines.launch

@Composable
fun CreateNewFileDialog(
    show: Boolean,
    tab: FilesTab,
    onDismissRequest: () -> Unit
) {
    if (show) {
        val context = LocalContext.current
        var isOpenFileDirectly by remember { mutableStateOf(false) }
        val listContent by remember(tab.activeFolderContent) {
            mutableStateOf(tab.activeFolderContent.map { it.displayName }.toTypedArray())
        }
        var newNameInput by remember { mutableStateOf("") }
        var error by remember { mutableStateOf("") }

        LaunchedEffect(newNameInput) {
            error = if (newNameInput.isBlank()) {
                emptyString
            } else if (!newNameInput.isValidAsFileName()) {
                globalClass.getString(R.string.invalid_file_name)
            } else {
                emptyString
            }
        }

        suspend fun resolveUniqueName(baseName: String, isFile: Boolean): String {
            if (tab.activeFolder.findFile(baseName) == null) return baseName
            val dot = if (isFile) baseName.lastIndexOf('.') else -1
            val name = if (dot > 0) baseName.substring(0, dot) else baseName
            val ext = if (dot > 0) baseName.substring(dot) else ""
            var counter = 1
            var candidate = "$name ($counter)$ext"
            while (tab.activeFolder.findFile(candidate) != null) {
                counter++
                candidate = "$name ($counter)$ext"
            }
            return candidate
        }

        Dialog(
            onDismissRequest = onDismissRequest,
        ) {
            Card(
                shape = RoundedCornerShape(6.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(
                    modifier = Modifier
                        .padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Column {
                        Text(
                            modifier = Modifier.fillMaxWidth(),
                            text = stringResource(R.string.create_new),
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

                    TextField(
                        modifier = Modifier
                            .fillMaxWidth()
                            .autoShowKeyboard(),
                        value = newNameInput,
                        onValueChange = {
                            newNameInput = it
                        },
                        label = { Text(text = stringResource(R.string.name)) },
                        placeholder = { Text("New Folder / New File") },
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
                        } else if (newNameInput.isBlank()) {
                            { Text("Leave empty for auto-generated name", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        } else null
                    )

                    CheckableText(
                        checked = isOpenFileDirectly,
                        onCheckedChange = { isOpenFileDirectly = it },
                    ) {
                        Text(stringResource(R.string.open_created_folder))
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            modifier = Modifier.weight(1f),
                            onClick = {
                                tab.scope.launch {
                                    val rawName = newNameInput.trim().ifEmpty { "New File.txt" }
                                    if (rawName.isValidAsFileName()) {
                                        val finalName = resolveUniqueName(rawName, isFile = true)
                                        onDismissRequest()
                                        tab.isLoading = true
                                        tab.activeFolder.createSubFile(finalName) { newFile ->
                                            tab.isLoading = false
                                            if (newFile == null) {
                                                globalClass.showMsg(R.string.failed_to_create_file)
                                            } else {
                                                tab.onNewFileCreated(
                                                    newFile,
                                                    isOpenFileDirectly,
                                                    context
                                                )
                                            }
                                        }
                                    } else {
                                        globalClass.showMsg(R.string.invalid_file_name)
                                    }
                                }
                            },
                            enabled = error.isEmpty(),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.file),
                                style = MaterialTheme.typography.labelLarge
                            )
                        }

                        Button(
                            modifier = Modifier.weight(1f),
                            onClick = {
                                tab.scope.launch {
                                    val rawName = newNameInput.trim().ifEmpty { "New Folder" }
                                    if (rawName.isValidAsFileName()) {
                                        val finalName = resolveUniqueName(rawName, isFile = false)
                                        onDismissRequest()
                                        tab.isLoading = true

                                        tab.activeFolder.createSubFolder(finalName) { newFile ->
                                            tab.isLoading = false
                                            if (newFile == null) {
                                                globalClass.showMsg(R.string.failed_to_create_folder)
                                            } else {
                                                tab.onNewFileCreated(
                                                    newFile,
                                                    isOpenFileDirectly,
                                                    context
                                                )
                                            }
                                        }
                                    } else {
                                        globalClass.showMsg(R.string.invalid_folder_name)
                                    }
                                }
                            },
                            enabled = error.isEmpty(),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.folder),
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                }
            }
        }
    }
}