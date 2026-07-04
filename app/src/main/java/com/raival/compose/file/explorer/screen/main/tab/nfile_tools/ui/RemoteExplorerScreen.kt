package com.raival.compose.file.explorer.screen.main.tab.nfile_tools.ui

import android.os.Environment
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.raival.compose.file.explorer.screen.main.tab.files.holder.LocalFileHolder
import com.raival.compose.file.explorer.screen.main.tab.files.service.remote.*
import com.raival.compose.file.explorer.screen.main.tab.nfile_tools.RemoteExplorerTab
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemoteExplorerScreen(tab: RemoteExplorerTab) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val conn = tab.connection

    var currentPath by remember { mutableStateOf(conn.rootPath) }
    var fileItems by remember { mutableStateOf<List<RemoteFileItem>>(emptyList()) }
    var isConnected by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var isBusy by remember { mutableStateOf(false) }
    var progressText by remember { mutableStateOf("") }

    var showCreateDirDialog by remember { mutableStateOf(false) }
    var showLocalFilePicker by remember { mutableStateOf(false) }
    var newDirName by remember { mutableStateOf("") }

    // Remote client instance
    val client = remember {
        when (conn.type) {
            "FTP" -> FtpRemoteClient(conn)
            "SFTP" -> SftpRemoteClient(conn)
            "WebDav" -> WebDavRemoteClient(conn)
            else -> LanRemoteClient(context, conn)
        }
    }

    // Connect & Load listing
    LaunchedEffect(currentPath) {
        isLoading = true
        scope.launch {
            try {
                if (!isConnected) {
                    withContext(Dispatchers.IO) {
                        client.connect()
                    }
                    isConnected = true
                }
                val items = withContext(Dispatchers.IO) {
                    client.listDirectory(currentPath)
                }
                withContext(Dispatchers.Main) {
                    fileItems = items
                    isLoading = false
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    isLoading = false
                    Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // Disconnect when leaving tab
    DisposableEffect(Unit) {
        onDispose {
            scope.launch(Dispatchers.IO) {
                try {
                    client.disconnect()
                } catch (_: Exception) {}
            }
        }
    }

    Scaffold(
        floatingActionButton = {
            if (isConnected) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    FloatingActionButton(
                        onClick = { showCreateDirDialog = true },
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Icon(Icons.Rounded.CreateNewFolder, contentDescription = "Create Directory")
                    }
                    FloatingActionButton(
                        onClick = { showLocalFilePicker = true }
                    ) {
                        Icon(Icons.Rounded.Upload, contentDescription = "Upload File")
                    }
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Header navigation bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (currentPath != conn.rootPath && currentPath != "/") {
                    IconButton(onClick = {
                        val parent = currentPath.substringBeforeLast("/", "")
                        currentPath = if (parent.isEmpty()) "/" else parent
                    }) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = conn.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = currentPath,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            HorizontalDivider()

            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else if (fileItems.isEmpty() && isConnected) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Rounded.CloudQueue,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                            modifier = Modifier.size(72.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "No files found",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(fileItems) { item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (item.isDirectory) {
                                        currentPath = item.path
                                    } else {
                                        // Download to cache and open
                                        isBusy = true
                                        progressText = "Downloading file..."
                                        scope.launch {
                                            try {
                                                val localCacheFile = File(context.cacheDir, "remote_${item.name}")
                                                withContext(Dispatchers.IO) {
                                                    client.downloadFile(item.path, localCacheFile.absolutePath) {}
                                                }
                                                withContext(Dispatchers.Main) {
                                                    isBusy = false
                                                    val localHolder = LocalFileHolder(localCacheFile)
                                                    localHolder.open(context, false, false, null)
                                                }
                                            } catch (e: Exception) {
                                                e.printStackTrace()
                                                withContext(Dispatchers.Main) {
                                                    isBusy = false
                                                    Toast.makeText(context, "Download failed: ${e.message}", Toast.LENGTH_LONG).show()
                                                }
                                            }
                                        }
                                    }
                                }
                                .padding(vertical = 12.dp, horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (item.isDirectory) Icons.Rounded.Folder else Icons.Rounded.InsertDriveFile,
                                contentDescription = null,
                                tint = if (item.isDirectory) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (!item.isDirectory) {
                                    Text(
                                        text = item.formattedSize,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            // Download action
                            if (!item.isDirectory) {
                                IconButton(onClick = {
                                    isBusy = true
                                    progressText = "Saving to Downloads..."
                                    scope.launch {
                                        try {
                                            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                                            val destFile = File(downloadsDir, item.name)
                                            withContext(Dispatchers.IO) {
                                                client.downloadFile(item.path, destFile.absolutePath) {}
                                            }
                                            withContext(Dispatchers.Main) {
                                                isBusy = false
                                                Toast.makeText(context, "Saved to Downloads: ${item.name}", Toast.LENGTH_LONG).show()
                                            }
                                        } catch (e: Exception) {
                                            withContext(Dispatchers.Main) {
                                                isBusy = false
                                                Toast.makeText(context, "Download failed: ${e.message}", Toast.LENGTH_LONG).show()
                                            }
                                        }
                                    }
                                }) {
                                    Icon(Icons.Rounded.Download, contentDescription = "Download")
                                }
                            }

                            // Delete action
                            IconButton(onClick = {
                                isBusy = true
                                progressText = "Deleting remote item..."
                                scope.launch {
                                    try {
                                        withContext(Dispatchers.IO) {
                                            client.delete(item.path, item.isDirectory)
                                        }
                                        val items = withContext(Dispatchers.IO) {
                                            client.listDirectory(currentPath)
                                        }
                                        withContext(Dispatchers.Main) {
                                            fileItems = items
                                            isBusy = false
                                            Toast.makeText(context, "Deleted remote item", Toast.LENGTH_SHORT).show()
                                        }
                                    } catch (e: Exception) {
                                        withContext(Dispatchers.Main) {
                                            isBusy = false
                                            Toast.makeText(context, "Delete failed: ${e.message}", Toast.LENGTH_LONG).show()
                                        }
                                    }
                                }
                            }) {
                                Icon(Icons.Rounded.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    }
                }
            }
        }

        if (isBusy) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.4f)),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    modifier = Modifier.padding(24.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(text = progressText, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }

    if (showCreateDirDialog) {
        AlertDialog(
            onDismissRequest = { showCreateDirDialog = false },
            title = { Text("Create Directory") },
            text = {
                OutlinedTextField(
                    value = newDirName,
                    onValueChange = { newDirName = it },
                    label = { Text("Directory Name") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val name = newDirName.trim()
                        if (name.isNotEmpty()) {
                            showCreateDirDialog = false
                            isBusy = true
                            progressText = "Creating directory..."
                            scope.launch {
                                try {
                                    val fullPath = if (currentPath.endsWith("/")) "$currentPath$name" else "$currentPath/$name"
                                    withContext(Dispatchers.IO) {
                                        client.createDirectory(fullPath)
                                    }
                                    val items = withContext(Dispatchers.IO) {
                                        client.listDirectory(currentPath)
                                    }
                                    withContext(Dispatchers.Main) {
                                        fileItems = items
                                        isBusy = false
                                        newDirName = ""
                                    }
                                } catch (e: Exception) {
                                    withContext(Dispatchers.Main) {
                                        isBusy = false
                                        Toast.makeText(context, "Failed: ${e.message}", Toast.LENGTH_LONG).show()
                                    }
                                }
                            }
                        }
                    },
                    enabled = newDirName.isNotEmpty()
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDirDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    FileSelectionDialog(
        show = showLocalFilePicker,
        onDismissRequest = { showLocalFilePicker = false },
        onItemsSelected = { selected ->
            if (selected.isNotEmpty()) {
                isBusy = true
                progressText = "Uploading ${selected.size} files..."
                scope.launch {
                    try {
                        withContext(Dispatchers.IO) {
                            for (file in selected) {
                                val remoteFile = if (currentPath.endsWith("/")) "$currentPath${file.name}" else "$currentPath/${file.name}"
                                client.uploadFile(file.absolutePath, remoteFile) {}
                            }
                        }
                        val items = withContext(Dispatchers.IO) {
                            client.listDirectory(currentPath)
                        }
                        withContext(Dispatchers.Main) {
                            fileItems = items
                            isBusy = false
                            Toast.makeText(context, "Uploaded successfully", Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            isBusy = false
                            Toast.makeText(context, "Upload failed: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
        }
    )
}
