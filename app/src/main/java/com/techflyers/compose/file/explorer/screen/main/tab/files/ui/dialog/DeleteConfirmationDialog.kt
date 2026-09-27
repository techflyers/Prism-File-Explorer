package com.techflyers.compose.file.explorer.screen.main.tab.files.ui.dialog

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.techflyers.compose.file.explorer.App.Companion.globalClass
import com.techflyers.compose.file.explorer.R
import com.techflyers.compose.file.explorer.common.ui.CheckableText
import com.techflyers.compose.file.explorer.common.ui.Space
import com.techflyers.compose.file.explorer.screen.main.tab.files.FilesTab
import com.techflyers.compose.file.explorer.screen.main.tab.files.holder.ContentHolder
import com.techflyers.compose.file.explorer.screen.main.tab.files.holder.LocalFileHolder
import com.techflyers.compose.file.explorer.screen.main.tab.files.task.CopyTask
import com.techflyers.compose.file.explorer.screen.main.tab.files.task.CopyTaskParameters
import com.techflyers.compose.file.explorer.screen.main.tab.files.task.DeleteTask
import com.techflyers.compose.file.explorer.screen.main.tab.files.task.DeleteTaskParameters
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

@Composable
fun DeleteConfirmationDialog(
    show: Boolean,
    tab: FilesTab,
    onDismissRequest: () -> Unit
) {
    if (show) {
        val preferencesManager = globalClass.preferencesManager

        var moveToRecycleBin by remember {
            mutableStateOf(preferencesManager.moveToRecycleBin)
        }
        var showRememberChoice by remember {
            mutableStateOf(false)
        }
        var rememberChoice by remember {
            mutableStateOf(false)
        }

        val targetFiles by remember(tab.id, tab.activeFolder.uniquePath) {
            mutableStateOf(tab.selectedFiles.map { it.value }.toList())
        }

        val bottomOptionsBarState = tab.bottomOptionsBarState.collectAsState()

        AlertDialog(
            onDismissRequest = { onDismissRequest() },
            confirmButton = {
                TextButton(
                    onClick = {
                        // Snapshot target files before any state changes
                        val filesToProcess = targetFiles.toList()
                        onDismissRequest()

                        if (filesToProcess.isEmpty()) return@TextButton

                        if (showRememberChoice && rememberChoice) {
                            preferencesManager.moveToRecycleBin =
                                moveToRecycleBin
                        }
                        tab.scope.launch {
                            if (!bottomOptionsBarState.value.showEmptyRecycleBinButton && moveToRecycleBin) {
                                val timestamp = System.currentTimeMillis().toString()
                                globalClass.recycleBinDir.createSubFolder(
                                    timestamp
                                ) { newDir ->
                                    tab.scope.launch {
                                        if (newDir != null) {
                                            // Save metadata.json with original paths for restore (streamed on IO)
                                            saveRecycleBinMetadata(newDir, filesToProcess)
                                            // Unselect after snapshotting
                                            tab.unselectAllFiles()
                                            globalClass.taskManager.addTaskAndRun(
                                                CopyTask(filesToProcess, true),
                                                CopyTaskParameters(
                                                    newDir
                                                )
                                            )
                                        } else {
                                            tab.unselectAllFiles()
                                            globalClass.showMsg(globalClass.getString(R.string.unable_to_move_to_recycle_bin))
                                        }
                                    }
                                }
                            } else {
                                tab.unselectAllFiles()
                                globalClass.taskManager.addTaskAndRun(
                                    DeleteTask(filesToProcess),
                                    DeleteTaskParameters()
                                )
                            }
                        }
                    }
                ) { Text(stringResource(R.string.confirm)) }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        onDismissRequest()
                    }
                ) { Text(stringResource(R.string.dismiss)) }
            },
            title = { Text(text = stringResource(R.string.delete_confirmation)) },
            text = {
                Column(Modifier.fillMaxWidth()) {
                    Text(
                        text = stringResource(id = R.string.delete_confirmation_message)
                    )

                    if (!bottomOptionsBarState.value.showEmptyRecycleBinButton) {
                        Space(size = 8.dp)

                        CheckableText(
                            modifier = Modifier.fillMaxWidth(),
                            checked = moveToRecycleBin,
                            onCheckedChange = {
                                moveToRecycleBin = it
                                showRememberChoice = true
                            },
                            text = {
                                Text(
                                    modifier = Modifier.alpha(0.7f),
                                    text = stringResource(R.string.move_to_recycle_bin)
                                )
                            }
                        )

                        if (showRememberChoice) {
                            Space(size = 4.dp)

                            CheckableText(
                                modifier = Modifier.fillMaxWidth(),
                                checked = rememberChoice,
                                onCheckedChange = { rememberChoice = it },
                                text = {
                                    Text(
                                        modifier = Modifier.alpha(0.6f),
                                        text = stringResource(R.string.remember_this_choice)
                                    )
                                }
                            )
                        }
                    }
                }
            }
        )
    }
}

/**
 * Saves a metadata.json file in the recycle bin folder that records the original
 * paths of deleted files, enabling restore to their original locations.
 * Uses buffered streaming on Dispatchers.IO to prevent OutOfMemoryError and ANRs on large batches.
 */
private suspend fun saveRecycleBinMetadata(recycleBinFolder: ContentHolder, files: List<ContentHolder>) {
    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        try {
            if (recycleBinFolder is LocalFileHolder) {
                val metadataFile = File(recycleBinFolder.file, "metadata.json")
                metadataFile.bufferedWriter().use { writer ->
                    writer.write("{\"items\":[\n")
                    files.forEachIndexed { index, file ->
                        val isDir = runCatching { file.isFolder }.getOrDefault(false)
                        val nameEscaped = JSONObject.quote(file.displayName)
                        val pathEscaped = JSONObject.quote(file.uniquePath)
                        writer.write("  {\"name\":$nameEscaped,\"originalPath\":$pathEscaped,\"isDirectory\":$isDir,\"deletedAt\":${System.currentTimeMillis()}}")
                        if (index < files.size - 1) {
                            writer.write(",\n")
                        } else {
                            writer.write("\n")
                        }
                    }
                    writer.write("]}\n")
                }
            }
        } catch (_: Exception) {
            // Non-critical: metadata save failure should not block deletion
        }
    }
}