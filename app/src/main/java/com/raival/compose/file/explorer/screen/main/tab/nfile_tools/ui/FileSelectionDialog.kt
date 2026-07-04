package com.raival.compose.file.explorer.screen.main.tab.nfile_tools.ui

import android.os.Environment
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.InsertDriveFile
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileSelectionDialog(
    show: Boolean,
    onDismissRequest: () -> Unit,
    onItemsSelected: (List<File>) -> Unit
) {
    if (!show) return

    var currentDir by remember {
        mutableStateOf(Environment.getExternalStorageDirectory())
    }

    val files = remember(currentDir) {
        val list = currentDir.listFiles() ?: emptyArray()
        list.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
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
                        Text(
                            text = currentDir.name.ifEmpty { "Internal Storage" },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    navigationIcon = {
                        if (currentDir.absolutePath != Environment.getExternalStorageDirectory().absolutePath) {
                            IconButton(onClick = {
                                val parent = currentDir.parentFile
                                if (parent != null) {
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
                                        currentDir = file
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
                            onItemsSelected(selectedFiles.toList())
                            onDismissRequest()
                        },
                        enabled = selectedFiles.isNotEmpty()
                    ) {
                        Text("Select (${selectedFiles.size})")
                    }
                }
            }
        }
    }
}
