package com.techflyers.compose.file.explorer.screen.main.tab.files.ui.dialog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountTree
import androidx.compose.material.icons.rounded.CheckCircleOutline
import androidx.compose.material.icons.rounded.DataUsage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.techflyers.compose.file.explorer.App.Companion.globalClass
import com.techflyers.compose.file.explorer.R
import com.techflyers.compose.file.explorer.common.toFormattedSize
import com.techflyers.compose.file.explorer.common.ui.CheckableText
import com.techflyers.compose.file.explorer.common.ui.Space
import com.techflyers.compose.file.explorer.screen.main.tab.files.FilesTab
import com.techflyers.compose.file.explorer.screen.main.tab.files.holder.ContentHolder
import com.techflyers.compose.file.explorer.screen.main.tab.files.holder.LocalFileHolder
import com.techflyers.compose.file.explorer.screen.main.tab.files.task.CopyTask
import com.techflyers.compose.file.explorer.screen.main.tab.files.task.CopyTaskParameters
import com.techflyers.compose.file.explorer.screen.main.tab.files.task.DeleteTask
import com.techflyers.compose.file.explorer.screen.main.tab.files.task.DeleteTaskParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import org.json.JSONObject
import java.io.File

@Composable
fun DeleteConfirmationDialog(
    show: Boolean,
    tab: FilesTab,
    customFiles: List<ContentHolder>? = null,
    onDeleted: ((List<ContentHolder>) -> Unit)? = null,
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

        val targetFiles by remember(tab.id, tab.activeFolder.uniquePath, customFiles) {
            mutableStateOf(customFiles ?: tab.selectedFiles.map { it.value }.toList())
        }

        val directFoldersCount = remember(targetFiles) { targetFiles.count { it.isFolder } }
        val directFilesCount = remember(targetFiles) { targetFiles.count { !it.isFolder } }
        val directFilesSize = remember(targetFiles) {
            targetFiles.filter { !it.isFolder }.sumOf { it.size }
        }

        var totalFoldersCount by remember(targetFiles) { mutableLongStateOf(directFoldersCount.toLong()) }
        var totalFilesCount by remember(targetFiles) { mutableLongStateOf(directFilesCount.toLong()) }
        var totalSizeBytes by remember(targetFiles) { mutableLongStateOf(directFilesSize) }
        var isCalculatingStats by remember(targetFiles) { mutableStateOf(directFoldersCount > 0) }

        LaunchedEffect(targetFiles) {
            if (directFoldersCount == 0) {
                totalFoldersCount = 0L
                totalFilesCount = directFilesCount.toLong()
                totalSizeBytes = directFilesSize
                isCalculatingStats = false
                return@LaunchedEffect
            }
            isCalculatingStats = true
            withContext(Dispatchers.IO) {
                var runningFolders = directFoldersCount.toLong()
                var runningFiles = directFilesCount.toLong()
                var runningSize = directFilesSize
                var processed = 0L

                suspend fun scanDirectory(dir: ContentHolder) {
                    if (!currentCoroutineContext().isActive) return
                    try {
                        for (child in dir.listContent()) {
                            if (!currentCoroutineContext().isActive) return
                            processed++
                            if (child.isFolder) {
                                runningFolders++
                                scanDirectory(child)
                            } else {
                                runningFiles++
                                runningSize += child.size
                            }
                            if (processed % 40 == 0L) {
                                totalFoldersCount = runningFolders
                                totalFilesCount = runningFiles
                                totalSizeBytes = runningSize
                                yield()
                            }
                        }
                    } catch (_: Exception) {}
                }

                for (folder in targetFiles.filter { it.isFolder }) {
                    if (!isActive) break
                    scanDirectory(folder)
                }
                totalFoldersCount = runningFolders
                totalFilesCount = runningFiles
                totalSizeBytes = runningSize
                isCalculatingStats = false
            }
        }

        val bottomOptionsBarState = tab.bottomOptionsBarState.collectAsState()

        AlertDialog(
            onDismissRequest = { onDismissRequest() },
            confirmButton = {
                TextButton(
                    onClick = {
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
                                            saveRecycleBinMetadata(newDir, filesToProcess)
                                            if (customFiles == null) {
                                                tab.unselectAllFiles()
                                            }
                                            onDeleted?.invoke(filesToProcess)
                                            globalClass.taskManager.addTaskAndRun(
                                                CopyTask(filesToProcess, true),
                                                CopyTaskParameters(
                                                    newDir
                                                )
                                            )
                                        } else {
                                            if (customFiles == null) {
                                                tab.unselectAllFiles()
                                            }
                                            globalClass.showMsg(globalClass.getString(R.string.unable_to_move_to_recycle_bin))
                                        }
                                    }
                                }
                            } else {
                                if (customFiles == null) {
                                    tab.unselectAllFiles()
                                }
                                onDeleted?.invoke(filesToProcess)
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

                    Space(size = 12.dp)

                    // Clear summary distinguishing Selected vs Recursive (total) items
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.65f),
                                shape = RoundedCornerShape(8.dp)
                            )
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (targetFiles.size == 1) {
                            Text(
                                text = targetFiles.first().displayName,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            )
                        }

                        val selectedFoldersWord = if (directFoldersCount == 1) "folder" else "folders"
                        val selectedFilesWord = if (directFilesCount == 1) "file" else "files"
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.CheckCircleOutline,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Selected: $directFoldersCount $selectedFoldersWord, $directFilesCount $selectedFilesWord",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        val totalFoldersWord = if (totalFoldersCount == 1L) "folder" else "folders"
                        val totalFilesWord = if (totalFilesCount == 1L) "file" else "files"
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.AccountTree,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Recursive: $totalFoldersCount $totalFoldersWord, $totalFilesCount $totalFilesWord (total)" +
                                        if (isCalculatingStats) " …" else "",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.DataUsage,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Total size: ${totalSizeBytes.toFormattedSize()}" +
                                        if (isCalculatingStats) " (calculating…)" else "",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

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
