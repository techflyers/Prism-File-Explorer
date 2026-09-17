package com.techflyers.compose.file.explorer.screen.main.tab.files.ui.dialog

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.app.ShareCompat
import androidx.core.content.FileProvider
import com.techflyers.compose.file.explorer.App.Companion.globalClass
import com.techflyers.compose.file.explorer.R
import com.techflyers.compose.file.explorer.common.ui.Space
import com.techflyers.compose.file.explorer.screen.main.tab.files.FilesTab
import com.techflyers.compose.file.explorer.screen.main.tab.files.holder.LocalFileHolder
import com.techflyers.compose.file.explorer.screen.main.tab.files.zip.ArchiveManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private data class ShareFormatOption(
    val label: String,
    val extension: String,
    val mimeType: String
)

private data class CompressionLevelOption(
    val name: String,
    val level: Int
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareFolderCompressDialog(
    show: Boolean,
    tab: FilesTab,
    onDismissRequest: () -> Unit
) {
    if (!show) return

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val targetFiles = remember(tab.selectedFiles) {
        tab.selectedFiles.values.filterIsInstance<LocalFileHolder>()
    }

    val formats = remember {
        listOf(
            ShareFormatOption("ZIP (.zip)", "zip", "application/zip"),
            ShareFormatOption("7Z (.7z)", "7z", "application/x-7z-compressed"),
            ShareFormatOption("TAR (.tar)", "tar", "application/x-tar")
        )
    }

    val levels = remember {
        listOf(
            CompressionLevelOption("Store (No compression)", 0),
            CompressionLevelOption("Fastest (1)", 1),
            CompressionLevelOption("Fast (3)", 3),
            CompressionLevelOption("Normal (5)", 5),
            CompressionLevelOption("Maximum (7)", 7),
            CompressionLevelOption("Ultra (9)", 9)
        )
    }

    var selectedFormatIndex by remember { mutableIntStateOf(0) }
    var selectedLevelIndex by remember { mutableIntStateOf(3) } // Normal (5)

    var formatExpanded by remember { mutableStateOf(false) }
    var levelExpanded by remember { mutableStateOf(false) }

    var isCompressing by remember { mutableStateOf(false) }
    var progressPercent by remember { mutableFloatStateOf(0f) }
    var currentFile by remember { mutableStateOf("") }
    var compressJob by remember { mutableStateOf<Job?>(null) }
    var isAborted by remember { mutableStateOf(false) }
    var partialDestFile by remember { mutableStateOf<File?>(null) }

    AlertDialog(
        onDismissRequest = {
            if (!isCompressing) {
                onDismissRequest()
            }
        },
        icon = {
            Icon(
                imageVector = Icons.Rounded.Share,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text(
                text = if (isCompressing) "Compressing Archive..." else "Compress & Share",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (!isCompressing) {
                    val promptText = if (targetFiles.size == 1 && targetFiles.first().isFolder) {
                        "\"${targetFiles.first().displayName}\" is a folder and must be compressed before sharing."
                    } else {
                        "Selected items contain folder(s) and must be compressed into an archive before sharing."
                    }
                    Text(
                        text = promptText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // Type / Format Dropdown
                    ExposedDropdownMenuBox(
                        expanded = formatExpanded,
                        onExpandedChange = { formatExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = formats[selectedFormatIndex].label,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Archive Type") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = formatExpanded) },
                            modifier = Modifier
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                .fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = formatExpanded,
                            onDismissRequest = { formatExpanded = false }
                        ) {
                            formats.forEachIndexed { index, fmt ->
                                DropdownMenuItem(
                                    text = { Text(fmt.label) },
                                    onClick = {
                                        selectedFormatIndex = index
                                        formatExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    // Compression Level Dropdown
                    ExposedDropdownMenuBox(
                        expanded = levelExpanded,
                        onExpandedChange = { levelExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = levels[selectedLevelIndex].name,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Compression Level") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = levelExpanded) },
                            modifier = Modifier
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                .fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = levelExpanded,
                            onDismissRequest = { levelExpanded = false }
                        ) {
                            levels.forEachIndexed { index, lvl ->
                                DropdownMenuItem(
                                    text = { Text(lvl.name) },
                                    onClick = {
                                        selectedLevelIndex = index
                                        levelExpanded = false
                                    }
                                )
                            }
                        }
                    }
                } else {
                    // Compressing progress UI
                    LinearProgressIndicator(
                        progress = { if (progressPercent > 0f) progressPercent else 0f },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (currentFile.isNotEmpty()) currentFile else "Compressing...",
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Space(8.dp)
                        Text(
                            text = "${(progressPercent.coerceAtLeast(0f) * 100).toInt()}%",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (!isCompressing) {
                Button(
                    onClick = {
                        if (targetFiles.isEmpty()) {
                            globalClass.showMsg("No valid local files to compress")
                            onDismissRequest()
                            return@Button
                        }

                        val chosenFormat = formats[selectedFormatIndex]
                        val chosenLevel = levels[selectedLevelIndex].level

                        isCompressing = true
                        progressPercent = 0f
                        currentFile = ""
                        isAborted = false

                        val cacheDir = File(context.cacheDir, "shared_archives")
                        if (!cacheDir.exists()) cacheDir.mkdirs()

                        // Clean up old archives (> 1 day old)
                        cacheDir.listFiles()?.forEach { oldFile ->
                            if (System.currentTimeMillis() - oldFile.lastModified() > 24 * 60 * 60 * 1000) {
                                oldFile.delete()
                            }
                        }

                        val baseName = if (targetFiles.size == 1) {
                            targetFiles.first().displayName
                        } else {
                            tab.activeFolder.displayName.ifEmpty { "archive" }
                        }
                        val destFile = File(cacheDir, "$baseName.${chosenFormat.extension}")
                        if (destFile.exists()) destFile.delete()
                        partialDestFile = destFile

                        compressJob = scope.launch(Dispatchers.IO) {
                            try {
                                val parentPath = targetFiles.firstOrNull()?.getParent()?.uniquePath?.takeIf {
                                    it.isNotEmpty() && File(it).isDirectory
                                }
                                val sourcePaths = if (parentPath != null && targetFiles.all { it.getParent()?.uniquePath == parentPath }) {
                                    targetFiles.map { it.displayName }
                                } else {
                                    targetFiles.map { it.file.absolutePath }
                                }

                                ArchiveManager.compress(
                                    sourcePaths = sourcePaths,
                                    archivePath = destFile.absolutePath,
                                    password = null,
                                    compressionLevel = chosenLevel,
                                    workingDir = parentPath,
                                    isAborted = { isAborted },
                                    onProgress = { pct, curFile ->
                                        progressPercent = pct
                                        currentFile = curFile
                                    }
                                )

                                if (!isAborted && destFile.exists() && destFile.length() > 0) {
                                    withContext(Dispatchers.Main) {
                                        isCompressing = false
                                        onDismissRequest()

                                        val uri = FileProvider.getUriForFile(
                                            context,
                                            context.packageName + ".provider",
                                            destFile
                                        )

                                        val shareIntent = ShareCompat.IntentBuilder(context)
                                            .setType(chosenFormat.mimeType)
                                            .addStream(uri)
                                            .intent
                                            .apply {
                                                action = Intent.ACTION_SEND
                                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                            }

                                        val chooser = Intent.createChooser(shareIntent, "Share archive")
                                        context.startActivity(chooser)
                                    }
                                } else if (isAborted) {
                                    if (destFile.exists()) destFile.delete()
                                    withContext(Dispatchers.Main) {
                                        isCompressing = false
                                        onDismissRequest()
                                    }
                                }
                            } catch (e: Exception) {
                                if (destFile.exists()) destFile.delete()
                                withContext(Dispatchers.Main) {
                                    isCompressing = false
                                    globalClass.showMsg("Compression failed: ${e.message ?: "Unknown error"}")
                                    onDismissRequest()
                                }
                            }
                        }
                    }
                ) {
                    Text("Compress & Share")
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    if (isCompressing) {
                        isAborted = true
                        compressJob?.cancel()
                        partialDestFile?.let { if (it.exists()) it.delete() }
                        isCompressing = false
                    }
                    onDismissRequest()
                }
            ) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}
