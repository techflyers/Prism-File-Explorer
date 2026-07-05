package com.raival.compose.file.explorer.screen.share

import android.net.Uri
import android.os.Bundle
import android.os.Environment
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.SdCard
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.lifecycleScope
import com.raival.compose.file.explorer.App.Companion.globalClass
import com.raival.compose.file.explorer.R
import com.raival.compose.file.explorer.base.BaseActivity
import com.raival.compose.file.explorer.common.getUriInfo
import com.raival.compose.file.explorer.common.showMsg
import com.raival.compose.file.explorer.common.ui.SafeSurface
import com.raival.compose.file.explorer.screen.main.tab.files.holder.StorageDevice
import com.raival.compose.file.explorer.screen.main.tab.files.provider.StorageProvider
import com.raival.compose.file.explorer.theme.FileExplorerTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class ShareReceiverActivity : BaseActivity() {

    private var sharedUris = mutableStateListOf<Uri>()
    private var sharedText by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Extract shared data from intent
        extractSharedData()

        // Check storage access permission
        checkPermissions()
    }

    override fun onPermissionGranted() {
        setContent {
            FileExplorerTheme {
                SafeSurface {
                    ShareReceiverScreen()
                }
            }
        }
    }

    private fun extractSharedData() {
        intent?.let { intent ->
            when (intent.action) {
                android.content.Intent.ACTION_SEND -> {
                    val uri = intent.getParcelableExtra<Uri>(android.content.Intent.EXTRA_STREAM)
                    if (uri != null) {
                        sharedUris.add(uri)
                    } else {
                        sharedText = intent.getStringExtra(android.content.Intent.EXTRA_TEXT)
                    }
                }
                android.content.Intent.ACTION_SEND_MULTIPLE -> {
                    intent.getParcelableArrayListExtra<Uri>(android.content.Intent.EXTRA_STREAM)?.forEach { uri ->
                        if (uri != null) {
                            sharedUris.add(uri)
                        }
                    }
                }
            }
        }
    }

    @Composable
    fun ShareReceiverScreen() {
        var currentDir by remember {
            mutableStateOf(Environment.getExternalStorageDirectory())
        }

        val showHidden = remember { globalClass.preferencesManager.showHiddenFiles }
        val folders = remember(currentDir) {
            currentDir.listFiles()?.filter { 
                it.isDirectory && (showHidden || !it.name.startsWith(".")) 
            }?.sortedWith(compareBy { it.name.lowercase() }) ?: emptyList()
        }

        val breadcrumbs = remember(currentDir) {
            val list = mutableListOf<File>()
            var current: File? = currentDir
            while (current != null) {
                list.add(0, current)
                current = current.parentFile
            }
            list
        }

        var storageDevices by remember { mutableStateOf<List<StorageDevice>>(emptyList()) }
        LaunchedEffect(Unit) {
            storageDevices = StorageProvider.getStorageDevices(this@ShareReceiverActivity)
        }

        var showNewFolderDialog by remember { mutableStateOf(false) }
        var newFolderName by remember { mutableStateOf("") }
        var isSaving by remember { mutableStateOf(false) }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f))
                .clickable { finish() },
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .fillMaxHeight(0.8f)
                    .clickable(enabled = false) {},
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp)
                ) {
                    // Header Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = getString(R.string.share_title_save_to),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            val summary = remember(sharedUris.size, sharedText) {
                                when {
                                    sharedUris.size > 1 -> getString(R.string.share_saving_files).format(sharedUris.size)
                                    sharedUris.size == 1 -> {
                                        val info = sharedUris[0].getUriInfo(this@ShareReceiverActivity)
                                        getString(R.string.share_saving_file).format(info.name ?: "file")
                                    }
                                    !sharedText.isNullOrEmpty() -> getString(R.string.share_saving_file).format("text content")
                                    else -> getString(R.string.share_no_files_found)
                                }
                            }
                            Text(
                                text = summary,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        IconButton(onClick = { finish() }) {
                            Icon(Icons.Rounded.Close, contentDescription = "Close")
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Storage Devices Switcher
                    if (storageDevices.isNotEmpty()) {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(storageDevices) { device ->
                                val rootPath = File(device.contentHolder.uniquePath)
                                val isSelected = currentDir.absolutePath.startsWith(rootPath.absolutePath)
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { currentDir = rootPath },
                                    label = { Text(device.title) },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = if (device.title.contains("SD", ignoreCase = true)) Icons.Rounded.SdCard else Icons.Rounded.Storage,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    // Breadcrumbs Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        LazyRow(
                            modifier = Modifier.weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            items(breadcrumbs) { folder ->
                                val name = if (folder.parentFile == null) "/" else folder.name
                                Text(
                                    text = name.ifEmpty { "/" },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (folder == currentDir) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = if (folder == currentDir) FontWeight.Bold else FontWeight.Normal,
                                    modifier = Modifier.clickable { currentDir = folder }
                                )
                                if (folder != currentDir) {
                                    Icon(
                                        imageVector = Icons.Rounded.KeyboardArrowRight,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }

                        IconButton(onClick = { showNewFolderDialog = true }) {
                            Icon(
                                imageVector = Icons.Rounded.CreateNewFolder,
                                contentDescription = getString(R.string.share_new_folder),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 8.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )

                    // Folders List
                    Box(modifier = Modifier.weight(1f)) {
                        if (folders.isEmpty()) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = getString(R.string.empty_folder),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            LazyColumn(modifier = Modifier.fillMaxSize()) {
                                items(folders) { folder ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(12.dp))
                                            .clickable { currentDir = folder }
                                            .padding(vertical = 12.dp, horizontal = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Folder,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(32.dp)
                                        )
                                        Spacer(modifier = Modifier.width(16.dp))
                                        Text(
                                            text = folder.name,
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Bottom Action Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = { finish() }) {
                            Text(getString(R.string.cancel))
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Button(
                            onClick = {
                                isSaving = true
                                saveSharedContent(currentDir)
                            },
                            enabled = sharedUris.isNotEmpty() || !sharedText.isNullOrEmpty(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Text(getString(R.string.share_button_save_here))
                        }
                    }
                }
            }

            // Copying Progress Overlay
            if (isSaving) {
                Dialog(
                    onDismissRequest = {},
                    properties = DialogProperties(
                        dismissOnBackPress = false,
                        dismissOnClickOutside = false
                    )
                ) {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                        ),
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = getString(R.string.saving),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }

        // New Folder Dialog
        if (showNewFolderDialog) {
            AlertDialog(
                onDismissRequest = { 
                    showNewFolderDialog = false
                    newFolderName = ""
                },
                title = { Text(getString(R.string.share_create_folder)) },
                text = {
                    OutlinedTextField(
                        value = newFolderName,
                        onValueChange = { newFolderName = it },
                        label = { Text(getString(R.string.share_folder_name)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (newFolderName.isNotBlank()) {
                                val newFolder = File(currentDir, newFolderName.trim())
                                if (newFolder.mkdir()) {
                                    currentDir = newFolder
                                } else {
                                    showMsg(getString(R.string.failed_to_create_folder))
                                }
                            }
                            showNewFolderDialog = false
                            newFolderName = ""
                        },
                        enabled = newFolderName.isNotBlank()
                    ) {
                        Text(getString(R.string.create))
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            showNewFolderDialog = false
                            newFolderName = ""
                        }
                    ) {
                        Text(getString(R.string.cancel))
                    }
                }
            )
        }
    }

    private fun saveSharedContent(destFolder: File) {
        lifecycleScope.launch {
            val success = withContext(Dispatchers.IO) {
                var allSuccess = true
                if (sharedUris.isNotEmpty()) {
                    for (uri in sharedUris) {
                        try {
                            val info = uri.getUriInfo(this@ShareReceiverActivity)
                            val name = info.name ?: "shared_file_${System.currentTimeMillis()}"
                            val destFile = getUniqueFile(destFolder, name)
                            
                            contentResolver.openInputStream(uri)?.use { input ->
                                destFile.outputStream().use { output ->
                                    input.copyTo(output)
                                }
                            } ?: run { allSuccess = false }
                        } catch (e: Exception) {
                            allSuccess = false
                            e.printStackTrace()
                        }
                    }
                } else if (!sharedText.isNullOrEmpty()) {
                    try {
                        val textFile = getUniqueFile(destFolder, "shared_text_${System.currentTimeMillis()}.txt")
                        textFile.writeText(sharedText!!)
                    } catch (e: Exception) {
                        allSuccess = false
                        e.printStackTrace()
                    }
                } else {
                    allSuccess = false
                }
                allSuccess
            }

            if (success) {
                showMsg(getString(R.string.share_files_saved))
                finish()
            } else {
                showMsg(getString(R.string.share_failed_to_save))
            }
        }
    }

    private fun getUniqueFile(parentDir: File, name: String): File {
        var file = File(parentDir, name)
        if (!file.exists()) return file

        val baseName = name.substringBeforeLast(".")
        val extension = name.substringAfterLast(".", "")
        val extSuffix = if (extension.isNotEmpty()) ".$extension" else ""

        var count = 1
        while (file.exists()) {
            val newName = "$baseName ($count)$extSuffix"
            file = File(parentDir, newName)
            count++
        }
        return file
    }
}
