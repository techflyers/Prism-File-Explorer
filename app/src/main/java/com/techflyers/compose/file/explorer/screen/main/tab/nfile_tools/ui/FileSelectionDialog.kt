package com.techflyers.compose.file.explorer.screen.main.tab.nfile_tools.ui

import android.os.Environment
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.SdCard
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.techflyers.compose.file.explorer.screen.main.tab.files.provider.StorageProvider
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileSelectionDialog(
    show: Boolean,
    initialDirectory: File? = null,
    onDismissRequest: () -> Unit,
    onItemsSelected: (List<File>) -> Unit
) {
    if (!show) return

    val context = LocalContext.current
    var currentDir by remember(initialDirectory) {
        mutableStateOf(
            if (initialDirectory != null && initialDirectory.exists() && initialDirectory.isDirectory) {
                initialDirectory
            } else {
                Environment.getExternalStorageDirectory()
            }
        )
    }

    val storageRoots = remember(context) {
        StorageProvider.getAvailableStorageRoots(context)
    }

    val files: List<File> = remember(currentDir) {
        try {
            val list = currentDir.listFiles() ?: emptyArray()
            list.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
        } catch (_: Exception) {
            emptyList()
        }
    }

    val selectedFiles = remember { mutableStateListOf<File>() }

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.background,
            tonalElevation = 6.dp
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Top App Bar
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = if (currentDir.absolutePath == "/") "Root (/)" else currentDir.name.ifEmpty { "Storage" },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = currentDir.absolutePath,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    navigationIcon = {
                        val parent = currentDir.parentFile
                        if (parent != null && currentDir.absolutePath != "/") {
                            IconButton(onClick = {
                                if (parent.canRead()) {
                                    currentDir = parent
                                }
                            }) {
                                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                            }
                        }
                    },
                    actions = {
                        TextButton(onClick = {
                            selectedFiles.clear()
                        }) {
                            Text("Clear")
                        }
                    }
                )

                // Storage Roots Quick Switcher
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    storageRoots.forEach { (name, rootFile) ->
                        val isCurrentRoot = if (rootFile.absolutePath == "/") {
                            currentDir.absolutePath == "/"
                        } else {
                            currentDir.absolutePath.startsWith(rootFile.absolutePath)
                        }
                        FilterChip(
                            selected = isCurrentRoot,
                            onClick = {
                                if (rootFile.exists() && rootFile.canRead()) {
                                    currentDir = rootFile
                                    selectedFiles.clear()
                                } else {
                                    Toast.makeText(context, "Storage location not accessible", Toast.LENGTH_SHORT).show()
                                }
                            },
                            label = { Text(name, style = MaterialTheme.typography.labelMedium) },
                            leadingIcon = {
                                Icon(
                                    imageVector = if (rootFile.absolutePath == "/") Icons.Rounded.Storage else Icons.Rounded.SdCard,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        )
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                // Files List
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp)
                ) {
                    items(files) { file ->
                        val isSelected = selectedFiles.contains(file)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (file.isDirectory) {
                                        if (file.canRead()) {
                                            currentDir = file
                                        } else {
                                            Toast.makeText(context, "Cannot access folder (Permission denied)", Toast.LENGTH_SHORT).show()
                                        }
                                    } else {
                                        if (isSelected) selectedFiles.remove(file)
                                        else selectedFiles.add(file)
                                    }
                                }
                                .padding(vertical = 12.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (file.isDirectory) Icons.Rounded.Folder else Icons.Rounded.InsertDriveFile,
                                contentDescription = null,
                                tint = if (file.isDirectory) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                modifier = Modifier.size(32.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = file.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (!file.isDirectory) {
                                    Text(
                                        text = "${file.length() / 1024} KB",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            // Checkbox for files & folders selection
                            Checkbox(
                                checked = isSelected,
                                onCheckedChange = { checked ->
                                    if (checked) {
                                        selectedFiles.add(file)
                                    } else {
                                        selectedFiles.remove(file)
                                    }
                                }
                            )
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    }
                }

                // Action Buttons
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismissRequest) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val selection = selectedFiles.ifEmpty { listOf(currentDir) }
                            onItemsSelected(selection)
                            onDismissRequest()
                        }
                    ) {
                        Text(
                            if (selectedFiles.isEmpty()) {
                                "Select this folder"
                            } else {
                                "Select (${selectedFiles.size})"
                            }
                        )
                    }
                }
            }
        }
    }
}
