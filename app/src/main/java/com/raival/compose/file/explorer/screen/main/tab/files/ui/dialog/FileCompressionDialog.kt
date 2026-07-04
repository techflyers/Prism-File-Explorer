package com.raival.compose.file.explorer.screen.main.tab.files.ui.dialog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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

        var newNameInput by remember { mutableStateOf("${tab.activeFolder.displayName}.zip") }
        var passwordInput by remember { mutableStateOf("") }
        var passwordVisible by remember { mutableStateOf(false) }
        var compressionLevel by remember { mutableFloatStateOf(5f) } // 0, 1, 3, 5, 7, 9 - map to nearest slider index
        var error by remember { mutableStateOf("") }

        val levelSteps = listOf(0f, 1f, 3f, 5f, 7f, 9f)
        val levelNames = listOf("Store", "Fastest", "Fast", "Normal", "Maximum", "Ultra")

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

                    TextField(
                        modifier = Modifier.fillMaxWidth(),
                        value = newNameInput,
                        onValueChange = {
                            newNameInput = it
                        },
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

                    // Password Input field (Optional)
                    TextField(
                        modifier = Modifier.fillMaxWidth(),
                        value = passwordInput,
                        onValueChange = { passwordInput = it },
                        label = { Text("Password (Optional)") },
                        singleLine = true,
                        shape = RoundedCornerShape(6.dp),
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        colors = TextFieldDefaults.colors(
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            disabledIndicatorColor = Color.Transparent
                        ),
                        trailingIcon = {
                            val image = if (passwordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(imageVector = image, contentDescription = "Toggle password visibility")
                            }
                        }
                    )

                    // Compression Level Slider
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

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                if (newNameInput.isValidAsFileName() && !listContent.contains(
                                        newNameInput
                                    )
                                ) {
                                    onDismissRequest()
                                    globalClass.taskManager.runTask(
                                        tab.compressTaskHolder!!.id,
                                        CompressTaskParameters(
                                            destPath = File(
                                                (tab.activeFolder as LocalFileHolder).file,
                                                newNameInput
                                            ).absolutePath,
                                            password = passwordInput.ifEmpty { null },
                                            compressionLevel = compressionLevel.toInt()
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
                            onClick = {
                                onDismissRequest()
                            },
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