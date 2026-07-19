package com.raival.compose.file.explorer.screen.share

import android.net.Uri
import android.os.Bundle
import android.os.Environment
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
        extractSharedData()
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
                    if (uri != null) sharedUris.add(uri)
                    else sharedText = intent.getStringExtra(android.content.Intent.EXTRA_TEXT)
                }
                android.content.Intent.ACTION_SEND_MULTIPLE -> {
                    intent.getParcelableArrayListExtra<Uri>(android.content.Intent.EXTRA_STREAM)
                        ?.filterNotNull()
                        ?.forEach { sharedUris.add(it) }
                }
            }
        }
    }

    // ─────────────────────────────────────────────────────────
    // Main screen
    // ─────────────────────────────────────────────────────────
    @Composable
    fun ShareReceiverScreen() {
        var currentDir by remember { mutableStateOf(Environment.getExternalStorageDirectory()) }
        val showHidden = remember { globalClass.preferencesManager.showHiddenFiles }

        // Subdirectories in currentDir (for navigation)
        val folders by remember(currentDir) {
            derivedStateOf {
                currentDir.listFiles()
                    ?.filter { it.isDirectory && (showHidden || !it.name.startsWith(".")) }
                    ?.sortedWith(compareBy { it.name.lowercase() })
                    ?: emptyList()
            }
        }

        // Existing files in currentDir shown greyed-out for conflict context
        val existingFiles by remember(currentDir) {
            derivedStateOf {
                currentDir.listFiles()
                    ?.filter { it.isFile && (showHidden || !it.name.startsWith(".")) }
                    ?.sortedWith(compareBy { it.name.lowercase() })
                    ?: emptyList()
            }
        }

        // Breadcrumb path list
        val breadcrumbs by remember(currentDir) {
            derivedStateOf {
                val list = mutableListOf<File>()
                var cur: File? = currentDir
                while (cur != null) { list.add(0, cur); cur = cur.parentFile }
                list
            }
        }

        var storageDevices by remember { mutableStateOf<List<StorageDevice>>(emptyList()) }
        LaunchedEffect(Unit) {
            storageDevices = StorageProvider.getStorageDevices(this@ShareReceiverActivity)
        }

        // Per-file rename state: map from index to desired filename
        val fileNames = remember(sharedUris.size) {
            mutableStateMapOf<Int, String>()
        }
        // Initialise names from URI info
        LaunchedEffect(sharedUris.size) {
            sharedUris.forEachIndexed { idx, uri ->
                if (!fileNames.containsKey(idx)) {
                    val info = withContext(Dispatchers.IO) { uri.getUriInfo(this@ShareReceiverActivity) }
                    fileNames[idx] = info.name ?: "shared_file_${System.currentTimeMillis()}"
                }
            }
        }
        // For text content
        val textFileName = remember { mutableStateOf("shared_text_${System.currentTimeMillis()}.txt") }

        var showNewFolderDialog by remember { mutableStateOf(false) }
        var newFolderName by remember { mutableStateOf("") }
        var isSaving by remember { mutableStateOf(false) }
        var showExistingFiles by remember { mutableStateOf(true) }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.55f))
                .clickable { finish() },
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth(0.94f)
                    .fillMaxHeight(0.88f)
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
                    // ── Header ──────────────────────────────────────────
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
                                    sharedUris.size > 1  -> getString(R.string.share_saving_files).format(sharedUris.size)
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

                    // ── Rename fields for each incoming file ─────────────
                    if (sharedUris.isNotEmpty() || !sharedText.isNullOrEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = "Save as",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )
                                if (!sharedText.isNullOrEmpty()) {
                                    // Single text file rename
                                    RenameField(
                                        value = textFileName.value,
                                        onValueChange = { textFileName.value = it },
                                        existingFiles = existingFiles,
                                        label = "Text file name"
                                    )
                                } else {
                                    sharedUris.forEachIndexed { idx, _ ->
                                        val name = fileNames[idx] ?: ""
                                        RenameField(
                                            value = name,
                                            onValueChange = { fileNames[idx] = it },
                                            existingFiles = existingFiles,
                                            label = if (sharedUris.size > 1) "File ${idx + 1}" else "File name"
                                        )
                                        if (idx < sharedUris.size - 1) {
                                            Spacer(modifier = Modifier.height(6.dp))
                                        }
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                    }

                    // ── Storage Devices Switcher ─────────────────────────
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
                                            imageVector = if (device.title.contains("SD", ignoreCase = true))
                                                Icons.Rounded.SdCard else Icons.Rounded.Storage,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    // ── Breadcrumbs + New Folder ─────────────────────────
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
                                    color = if (folder == currentDir)
                                        MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
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
                        modifier = Modifier.padding(vertical = 6.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )

                    // ── Directory browser + existing files list ──────────
                    Box(modifier = Modifier.weight(1f)) {
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            // Navigable sub-folders
                            if (folders.isEmpty() && existingFiles.isEmpty()) {
                                item {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 32.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = getString(R.string.empty_folder),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }

                            items(folders) { folder ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable { currentDir = folder }
                                        .padding(vertical = 10.dp, horizontal = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Folder,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(30.dp)
                                    )
                                    Spacer(modifier = Modifier.width(14.dp))
                                    Text(
                                        text = folder.name,
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            // Section header + existing files (greyed-out)
                            if (existingFiles.isNotEmpty()) {
                                item {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { showExistingFiles = !showExistingFiles }
                                            .padding(vertical = 6.dp, horizontal = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Rounded.Description,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "${existingFiles.size} existing file${if (existingFiles.size != 1) "s" else ""}",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Icon(
                                            imageVector = if (showExistingFiles) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    HorizontalDivider(
                                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                                    )
                                }

                                if (showExistingFiles) {
                                    items(existingFiles) { file ->
                                        ExistingFileRow(
                                            file = file,
                                            incomingNames = buildIncomingNames(fileNames, textFileName.value, sharedText)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // ── Actions ─────────────────────────────────────────
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
                                val names = buildIncomingNames(fileNames, textFileName.value, sharedText)
                                saveSharedContent(currentDir, names)
                            },
                            enabled = (sharedUris.isNotEmpty() || !sharedText.isNullOrEmpty()) && !isSaving,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.SaveAlt,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(getString(R.string.share_button_save_here))
                        }
                    }
                }
            }

            // ── Saving progress overlay ──────────────────────────────
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

        // ── New Folder Dialog ────────────────────────────────────────
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
                                if (newFolder.mkdir()) currentDir = newFolder
                                else showMsg(getString(R.string.failed_to_create_folder))
                            }
                            showNewFolderDialog = false
                            newFolderName = ""
                        },
                        enabled = newFolderName.isNotBlank()
                    ) { Text(getString(R.string.create)) }
                },
                dismissButton = {
                    TextButton(onClick = {
                        showNewFolderDialog = false
                        newFolderName = ""
                    }) { Text(getString(R.string.cancel)) }
                }
            )
        }
    }

    // ─────────────────────────────────────────────────────────
    // Rename text field with live conflict detection
    // ─────────────────────────────────────────────────────────
    @Composable
    private fun RenameField(
        value: String,
        onValueChange: (String) -> Unit,
        existingFiles: List<File>,
        label: String
    ) {
        val conflict = remember(value, existingFiles) {
            existingFiles.any { it.name.equals(value.trim(), ignoreCase = false) }
        }
        OutlinedTextField(
            value = value,
            onValueChange = { onValueChange(it.replace("/", "").replace("\\", "")) },
            label = { Text(label) },
            isError = conflict,
            supportingText = if (conflict) {
                { Text("A file with this name already exists — it will be overwritten or auto-renamed.", color = MaterialTheme.colorScheme.error) }
            } else null,
            trailingIcon = if (conflict) {
                { Icon(Icons.Rounded.Warning, contentDescription = "Conflict", tint = MaterialTheme.colorScheme.error) }
            } else null,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
    }

    // ─────────────────────────────────────────────────────────
    // Single greyed-out existing file row with conflict highlight
    // ─────────────────────────────────────────────────────────
    @Composable
    private fun ExistingFileRow(file: File, incomingNames: List<String>) {
        val isConflict = incomingNames.any { it.trim().equals(file.name, ignoreCase = false) }
        val rowAlpha = if (isConflict) 1f else 0.4f
        val rowColor = if (isConflict)
            MaterialTheme.colorScheme.errorContainer
        else Color.Transparent

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(rowColor)
                .padding(vertical = 7.dp, horizontal = 8.dp)
                .alpha(rowAlpha),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (isConflict) Icons.Rounded.Warning else Icons.Rounded.InsertDriveFile,
                contentDescription = null,
                tint = if (isConflict)
                    MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = file.name,
                style = MaterialTheme.typography.bodySmall,
                color = if (isConflict)
                    MaterialTheme.colorScheme.onErrorContainer
                else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (isConflict) {
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "conflict",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }

    // ─────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────
    private fun buildIncomingNames(
        fileNames: Map<Int, String>,
        textFileName: String,
        sharedText: String?
    ): List<String> = if (!sharedText.isNullOrEmpty()) {
        listOf(textFileName)
    } else {
        fileNames.values.toList()
    }

    private fun saveSharedContent(destFolder: File, names: List<String>) {
        lifecycleScope.launch {
            val success = withContext(Dispatchers.IO) {
                var allSuccess = true
                if (sharedUris.isNotEmpty()) {
                    sharedUris.forEachIndexed { idx, uri ->
                        try {
                            val customName = names.getOrNull(idx)?.trim()?.ifBlank { null }
                            val fallback = uri.getUriInfo(this@ShareReceiverActivity).name
                                ?: "shared_file_${System.currentTimeMillis()}"
                            val targetName = customName ?: fallback
                            val destFile = getUniqueFile(destFolder, targetName)
                            contentResolver.openInputStream(uri)?.use { input ->
                                destFile.outputStream().use { output -> input.copyTo(output) }
                            } ?: run { allSuccess = false }
                        } catch (e: Exception) {
                            allSuccess = false
                            e.printStackTrace()
                        }
                    }
                } else if (!sharedText.isNullOrEmpty()) {
                    try {
                        val name = names.firstOrNull()?.trim()?.ifBlank { null }
                            ?: "shared_text_${System.currentTimeMillis()}.txt"
                        val textFile = getUniqueFile(destFolder, name)
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
            file = File(parentDir, "$baseName ($count)$extSuffix")
            count++
        }
        return file
    }
}
