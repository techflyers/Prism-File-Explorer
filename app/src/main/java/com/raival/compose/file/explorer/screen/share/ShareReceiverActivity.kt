package com.raival.compose.file.explorer.screen.share

import android.content.ClipData
import android.content.Intent
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
import com.raival.compose.file.explorer.common.ui.autoShowKeyboard
import com.raival.compose.file.explorer.common.ui.fastScrollbar
import com.raival.compose.file.explorer.screen.main.MainActivity
import com.raival.compose.file.explorer.screen.main.tab.files.holder.ContentHolder
import com.raival.compose.file.explorer.screen.main.tab.files.holder.LocalFileHolder
import com.raival.compose.file.explorer.screen.main.tab.files.holder.RemoteFileHolder
import com.raival.compose.file.explorer.screen.main.tab.files.holder.StorageDevice
import com.raival.compose.file.explorer.screen.main.tab.files.provider.StorageProvider
import com.raival.compose.file.explorer.screen.main.tab.files.service.remote.NetworkConnectionsService
import com.raival.compose.file.explorer.screen.main.tab.files.service.remote.RemotePaths
import com.raival.compose.file.explorer.screen.preferences.SaveLocationHistoryItem
import com.raival.compose.file.explorer.theme.FileExplorerTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

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
                Intent.ACTION_SEND -> {
                    val uri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
                    if (uri != null) sharedUris.add(uri)
                    else sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
                }
                Intent.ACTION_SEND_MULTIPLE -> {
                    intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)
                        ?.filterNotNull()
                        ?.forEach { sharedUris.add(it) }
                }
            }
        }
    }

    @Composable
    fun ShareReceiverScreen() {
        var currentHolder by remember {
            mutableStateOf<ContentHolder>(LocalFileHolder(Environment.getExternalStorageDirectory()))
        }
        val showHidden = remember { globalClass.preferencesManager.showHiddenFiles }

        var folders by remember { mutableStateOf<List<ContentHolder>>(emptyList()) }
        var existingFiles by remember { mutableStateOf<List<ContentHolder>>(emptyList()) }
        var isLoadingFolder by remember { mutableStateOf(false) }

        // Refresh contents when currentHolder changes
        LaunchedEffect(currentHolder, showHidden) {
            isLoadingFolder = true
            withContext(Dispatchers.IO) {
                try {
                    val items = currentHolder.listContent()
                    val filtered = if (showHidden) items else items.filter { !it.displayName.startsWith(".") }
                    folders = filtered.filter { it.isFolder }.sortedWith(compareBy { it.displayName.lowercase() })
                    existingFiles = filtered.filter { !it.isFolder }.sortedWith(compareBy { it.displayName.lowercase() })
                } catch (e: Exception) {
                    folders = emptyList()
                    existingFiles = emptyList()
                }
            }
            isLoadingFolder = false
        }

        // Breadcrumbs path list
        var breadcrumbs by remember { mutableStateOf<List<ContentHolder>>(emptyList()) }
        LaunchedEffect(currentHolder) {
            withContext(Dispatchers.IO) {
                val list = mutableListOf<ContentHolder>()
                var cur: ContentHolder? = currentHolder
                while (cur != null) {
                    list.add(0, cur)
                    cur = cur.getParent()
                }
                breadcrumbs = list
            }
        }

        var storageDevices by remember { mutableStateOf<List<StorageDevice>>(emptyList()) }
        LaunchedEffect(Unit) {
            storageDevices = StorageProvider.getStorageDevices(this@ShareReceiverActivity)
        }

        val recentLocations = remember {
            globalClass.preferencesManager.saveToPrismHistory
        }

        // Per-file rename state: map from index to desired filename
        val fileNames = remember(sharedUris.size) {
            mutableStateMapOf<Int, String>()
        }
        LaunchedEffect(sharedUris.size) {
            sharedUris.forEachIndexed { idx, uri ->
                if (!fileNames.containsKey(idx)) {
                    val info = withContext(Dispatchers.IO) { uri.getUriInfo(this@ShareReceiverActivity) }
                    fileNames[idx] = info.name ?: "shared_file_${System.currentTimeMillis()}"
                }
            }
        }
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
                        IconButton(onClick = { openFullExplorer() }) {
                            Icon(
                                imageVector = Icons.Rounded.FolderOpen,
                                contentDescription = getString(R.string.browse_in_explorer)
                            )
                        }
                        IconButton(onClick = { finish() }) {
                            Icon(Icons.Rounded.Close, contentDescription = "Close")
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // ── Rename fields for incoming content ─────────────
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
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    // ── Recent Location History (Item 4) ────────────────
                    if (recentLocations.isNotEmpty()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.History,
                                contentDescription = "Recent Locations",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Recent",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                items(recentLocations) { historyItem ->
                                    val isCurrent = historyItem.path == currentHolder.uniquePath
                                    SuggestionChip(
                                        onClick = {
                                            navigateToHistoryLocation(historyItem) { holder ->
                                                currentHolder = holder
                                            }
                                        },
                                        label = {
                                            Text(
                                                text = historyItem.title.ifEmpty { historyItem.path.substringAfterLast('/') },
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        },
                                        colors = SuggestionChipDefaults.suggestionChipColors(
                                            containerColor = if (isCurrent)
                                                MaterialTheme.colorScheme.primaryContainer
                                            else MaterialTheme.colorScheme.surfaceContainerHighest
                                        )
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                    }

                    // ── Storage Devices Switcher (Item 1: Remote & Local) ─
                    if (storageDevices.isNotEmpty()) {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(storageDevices) { device ->
                                val deviceHolder = device.contentHolder
                                val isSelected = when {
                                    currentHolder is RemoteFileHolder && deviceHolder is RemoteFileHolder ->
                                        (currentHolder as RemoteFileHolder).connection.id == deviceHolder.connection.id
                                    currentHolder is LocalFileHolder && deviceHolder is LocalFileHolder ->
                                        currentHolder.uniquePath.startsWith(deviceHolder.uniquePath)
                                    else -> currentHolder.uniquePath == deviceHolder.uniquePath
                                }

                                FilterChip(
                                    selected = isSelected,
                                    onClick = { currentHolder = deviceHolder },
                                    label = { Text(device.title) },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = when {
                                                device.title.contains("SD", ignoreCase = true) -> Icons.Rounded.SdCard
                                                deviceHolder is RemoteFileHolder -> Icons.Rounded.Cloud
                                                else -> Icons.Rounded.Storage
                                            },
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
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
                                val isCurrent = folder.uniquePath == currentHolder.uniquePath
                                val name = folder.displayName.ifEmpty { "/" }
                                Text(
                                    text = name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                    modifier = Modifier.clickable { currentHolder = folder }
                                )
                                if (!isCurrent) {
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
                        if (isLoadingFolder) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(modifier = Modifier.size(32.dp))
                            }
                        } else {
                            val listState = rememberLazyListState()
                            LazyColumn(
                                state = listState,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .fastScrollbar(listState)
                            ) {
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
                                            .clickable { currentHolder = folder }
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
                                            text = folder.displayName,
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }

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
                                saveSharedContent(currentHolder, names)
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

        // ── New Folder Dialog (Item 6: quick folder creation) ─────────
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
                        placeholder = { Text("New Folder") },
                        supportingText = { Text("Leave empty for auto-generated name") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .autoShowKeyboard()
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val baseName = newFolderName.trim().ifEmpty { "New Folder" }
                            lifecycleScope.launch {
                                val uniqueName = resolveUniqueFolderName(currentHolder, baseName)
                                currentHolder.createSubFolder(uniqueName) { created ->
                                    if (created != null) {
                                        currentHolder = created
                                    } else {
                                        showMsg(getString(R.string.failed_to_create_folder))
                                    }
                                }
                            }
                            showNewFolderDialog = false
                            newFolderName = ""
                        }
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

    private suspend fun resolveUniqueFolderName(parent: ContentHolder, baseName: String): String {
        return withContext(Dispatchers.IO) {
            if (parent.findFile(baseName) == null) return@withContext baseName
            var counter = 1
            var candidate = "$baseName ($counter)"
            while (parent.findFile(candidate) != null) {
                counter++
                candidate = "$baseName ($counter)"
            }
            candidate
        }
    }

    private fun navigateToHistoryLocation(
        historyItem: SaveLocationHistoryItem,
        onResolved: (ContentHolder) -> Unit
    ) {
        lifecycleScope.launch {
            val resolved = withContext(Dispatchers.IO) {
                if (historyItem.isRemote) {
                    val connections = NetworkConnectionsService.getConnections(this@ShareReceiverActivity)
                    val conn = connections.find { historyItem.path.startsWith("remote://${it.id}") }
                    if (conn != null) {
                        val subPath = historyItem.path.removePrefix("remote://${conn.id}").ifEmpty { conn.rootPath }
                        RemoteFileHolder(conn, RemotePaths.normalize(subPath))
                    } else null
                } else {
                    val file = File(historyItem.path)
                    if (file.exists() && file.isDirectory) LocalFileHolder(file) else null
                }
            }
            if (resolved != null) {
                onResolved(resolved)
            } else {
                showMsg("Location is no longer available")
            }
        }
    }

    @Composable
    private fun RenameField(
        value: String,
        onValueChange: (String) -> Unit,
        existingFiles: List<ContentHolder>,
        label: String
    ) {
        val conflict = remember(value, existingFiles) {
            existingFiles.any { it.displayName.equals(value.trim(), ignoreCase = false) }
        }
        OutlinedTextField(
            value = value,
            onValueChange = { onValueChange(it.replace("/", "").replace("\\", "")) },
            label = { Text(label) },
            isError = conflict,
            supportingText = if (conflict) {
                { Text("Name exists — will be auto-renamed.", color = MaterialTheme.colorScheme.primary) }
            } else null,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .autoShowKeyboard()
        )
    }

    @Composable
    private fun ExistingFileRow(file: ContentHolder, incomingNames: List<String>) {
        val isConflict = incomingNames.any { it.trim().equals(file.displayName, ignoreCase = false) }
        val rowAlpha = if (isConflict) 1f else 0.4f
        val rowColor = if (isConflict) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else Color.Transparent

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
                tint = if (isConflict) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = file.displayName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (isConflict) {
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "auto-rename",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }

    private fun buildIncomingNames(
        fileNames: Map<Int, String>,
        textFileName: String,
        sharedText: String?
    ): List<String> = if (!sharedText.isNullOrEmpty()) {
        listOf(textFileName)
    } else {
        fileNames.values.toList()
    }

    private fun saveSharedContent(destHolder: ContentHolder, names: List<String>) {
        lifecycleScope.launch {
            val success = withContext(Dispatchers.IO) {
                var allSuccess = true
                when (destHolder) {
                    is LocalFileHolder -> {
                        val destFolder = destHolder.file
                        if (sharedUris.isNotEmpty()) {
                            sharedUris.forEachIndexed { idx, uri ->
                                try {
                                    val customName = names.getOrNull(idx)?.trim()?.ifBlank { null }
                                    val fallback = uri.getUriInfo(this@ShareReceiverActivity).name
                                        ?: "shared_file_${System.currentTimeMillis()}"
                                    val targetName = customName ?: fallback
                                    val destFile = getUniqueLocalFile(destFolder, targetName)
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
                                val textFile = getUniqueLocalFile(destFolder, name)
                                textFile.writeText(sharedText!!)
                            } catch (e: Exception) {
                                allSuccess = false
                                e.printStackTrace()
                            }
                        } else {
                            allSuccess = false
                        }
                    }
                    is RemoteFileHolder -> {
                        val client = destHolder.client
                        if (sharedUris.isNotEmpty()) {
                            sharedUris.forEachIndexed { idx, uri ->
                                try {
                                    val customName = names.getOrNull(idx)?.trim()?.ifBlank { null }
                                    val fallback = uri.getUriInfo(this@ShareReceiverActivity).name
                                        ?: "shared_file_${System.currentTimeMillis()}"
                                    val targetName = customName ?: fallback
                                    val remoteTarget = getUniqueRemoteFile(client, RemotePaths.join(destHolder.remotePath, targetName))
                                    val temp = File(globalClass.cleanOnExitDir.file, "share_up_${UUID.randomUUID()}")
                                    try {
                                        contentResolver.openInputStream(uri)?.use { input ->
                                            temp.outputStream().use { output -> input.copyTo(output) }
                                        }
                                        if (temp.exists() && temp.length() > 0) {
                                            client.uploadFile(temp.absolutePath, remoteTarget) {}
                                        } else {
                                            allSuccess = false
                                        }
                                    } finally {
                                        temp.delete()
                                    }
                                } catch (e: Exception) {
                                    allSuccess = false
                                    e.printStackTrace()
                                }
                            }
                        } else if (!sharedText.isNullOrEmpty()) {
                            try {
                                val name = names.firstOrNull()?.trim()?.ifBlank { null }
                                    ?: "shared_text_${System.currentTimeMillis()}.txt"
                                val remoteTarget = getUniqueRemoteFile(client, RemotePaths.join(destHolder.remotePath, name))
                                val temp = File(globalClass.cleanOnExitDir.file, "share_txt_${UUID.randomUUID()}.txt")
                                try {
                                    temp.writeText(sharedText!!)
                                    client.uploadFile(temp.absolutePath, remoteTarget) {}
                                } finally {
                                    temp.delete()
                                }
                            } catch (e: Exception) {
                                allSuccess = false
                                e.printStackTrace()
                            }
                        } else {
                            allSuccess = false
                        }
                    }
                    else -> {
                        allSuccess = false
                    }
                }
                allSuccess
            }

            if (success) {
                globalClass.preferencesManager.addSaveLocationToHistory(
                    destHolder.uniquePath,
                    destHolder.displayName,
                    destHolder is RemoteFileHolder
                )
                showMsg(getString(R.string.share_files_saved))
                finish()
            } else {
                showMsg(getString(R.string.share_failed_to_save))
            }
        }
    }

    private fun openFullExplorer() {
        globalClass.isShareMode = true
        globalClass.shareUris = sharedUris.toList()
        globalClass.shareText = sharedText
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP or
                Intent.FLAG_GRANT_READ_URI_PERMISSION
        }
        if (sharedUris.isNotEmpty()) {
            intent.clipData = ClipData.newUri(contentResolver, "shared", sharedUris[0]).also { clip ->
                sharedUris.drop(1).forEach { clip.addItem(ClipData.Item(it)) }
            }
        }
        startActivity(intent)
        finish()
    }

    private fun getUniqueLocalFile(parentDir: File, name: String): File {
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

    private fun getUniqueRemoteFile(client: com.raival.compose.file.explorer.screen.main.tab.files.service.remote.RemoteClient, remotePath: String): String {
        if (!client.exists(remotePath)) return remotePath
        val parent = RemotePaths.parent(remotePath) ?: "/"
        val name = RemotePaths.name(remotePath)
        val baseName = name.substringBeforeLast(".")
        val extension = name.substringAfterLast(".", "")
        val extSuffix = if (extension.isNotEmpty() && extension != name) ".$extension" else ""
        var count = 1
        var candidate = RemotePaths.join(parent, "$baseName ($count)$extSuffix")
        while (client.exists(candidate)) {
            count++
            candidate = RemotePaths.join(parent, "$baseName ($count)$extSuffix")
        }
        return candidate
    }
}
