package com.raival.compose.file.explorer.screen.picker

import android.app.Activity
import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.webkit.MimeTypeMap
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.raival.compose.file.explorer.App.Companion.globalClass
import com.raival.compose.file.explorer.R
import com.raival.compose.file.explorer.base.BaseActivity
import com.raival.compose.file.explorer.common.MimeTypeDetector
import com.raival.compose.file.explorer.common.showMsg
import com.raival.compose.file.explorer.common.toFormattedDate
import com.raival.compose.file.explorer.common.toFormattedSize
import com.raival.compose.file.explorer.common.ui.SafeSurface
import com.raival.compose.file.explorer.common.ui.autoShowKeyboard
import com.raival.compose.file.explorer.common.ui.fastScrollbar
import com.raival.compose.file.explorer.screen.main.tab.files.holder.ContentHolder
import com.raival.compose.file.explorer.screen.main.tab.files.holder.LocalFileHolder
import com.raival.compose.file.explorer.screen.main.tab.files.holder.RemoteFileHolder
import com.raival.compose.file.explorer.screen.main.tab.files.holder.StorageDevice
import com.raival.compose.file.explorer.screen.main.tab.files.provider.StorageProvider
import com.raival.compose.file.explorer.screen.main.tab.files.service.remote.NetworkConnectionModel
import com.raival.compose.file.explorer.screen.main.tab.files.service.remote.NetworkConnectionsService
import com.raival.compose.file.explorer.screen.main.tab.files.service.remote.RemoteConnectionPool
import com.raival.compose.file.explorer.screen.main.tab.files.service.remote.RemotePaths
import com.raival.compose.file.explorer.screen.main.tab.files.ui.FileContentIcon
import com.raival.compose.file.explorer.theme.FileExplorerTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

sealed class PickerLocation {
    data class Local(val dir: File) : PickerLocation()
    data class Remote(val connection: NetworkConnectionModel, val path: String) : PickerLocation()
}

class FilePickerActivity : BaseActivity() {

    private var isFolderMode = false
    private var allowMultiple = false
    private var primaryMimeFilter: String = "*/*"
    private var extraMimeFilters: List<String> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        parsePickerIntent()
        checkPermissions()
    }

    private fun parsePickerIntent() {
        intent?.let { intent ->
            val action = intent.action
            isFolderMode = action == Intent.ACTION_OPEN_DOCUMENT_TREE ||
                    intent.getBooleanExtra("android.intent.extra.PICK_DIRECTORY", false) ||
                    intent.getBooleanExtra("folder_mode", false)

            allowMultiple = intent.getBooleanExtra(Intent.EXTRA_ALLOW_MULTIPLE, false)

            primaryMimeFilter = intent.type ?: "*/*"
            extraMimeFilters = intent.getStringArrayExtra(Intent.EXTRA_MIME_TYPES)?.toList() ?: emptyList()
        }
    }

    override fun onPermissionGranted() {
        setContent {
            FileExplorerTheme {
                SafeSurface {
                    FilePickerScreen()
                }
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun FilePickerScreen() {
        val context = LocalContext.current
        var location by remember {
            mutableStateOf<PickerLocation>(
                PickerLocation.Local(Environment.getExternalStorageDirectory())
            )
        }

        val selectedItems = remember { mutableStateListOf<ContentHolder>() }
        val showHidden = remember { globalClass.preferencesManager.showHiddenFiles }

        var searchQuery by remember { mutableStateOf("") }
        var isSearchActive by remember { mutableStateOf(false) }
        var showAllFilesOverride by remember { mutableStateOf(false) }
        var showNewFolderDialog by remember { mutableStateOf(false) }
        var newFolderName by remember { mutableStateOf("") }
        var isDownloading by remember { mutableStateOf(false) }
        var isRemoteLoading by remember { mutableStateOf(false) }
        var remoteLoadError by remember { mutableStateOf<String?>(null) }

        var storageDevices by remember { mutableStateOf<List<StorageDevice>>(emptyList()) }
        var remoteConnections by remember { mutableStateOf<List<NetworkConnectionModel>>(emptyList()) }

        LaunchedEffect(Unit) {
            storageDevices = StorageProvider.getStorageDevices(context)
            remoteConnections = NetworkConnectionsService.getConnections(context)
        }

        // Local or remote items
        var remoteItems by remember { mutableStateOf<List<RemoteFileHolder>>(emptyList()) }

        // Fetch remote directory items when location changes
        LaunchedEffect(location) {
            selectedItems.clear()
            if (location is PickerLocation.Remote) {
                val loc = location as PickerLocation.Remote
                isRemoteLoading = true
                remoteLoadError = null
                withContext(Dispatchers.IO) {
                    try {
                        val client = RemoteConnectionPool.clientFor(loc.connection)
                        val list = client.listDirectory(loc.path).map {
                            RemoteFileHolder(loc.connection, it.path, it)
                        }.sortedWith(
                            compareBy<RemoteFileHolder> { !it.isFolder }.thenBy { it.displayName.lowercase() }
                        )
                        remoteItems = list
                    } catch (e: Exception) {
                        remoteLoadError = e.message ?: context.getString(R.string.remote_connection_error)
                        remoteItems = emptyList()
                    } finally {
                        isRemoteLoading = false
                    }
                }
            }
        }

        // List contents
        val allContents: List<ContentHolder> = when (val loc = location) {
            is PickerLocation.Local -> {
                remember(loc.dir, showHidden) {
                    loc.dir.listFiles()
                        ?.filter { showHidden || !it.name.startsWith(".") }
                        ?.sortedWith(
                            compareBy<File> { !it.isDirectory }.thenBy { it.name.lowercase() }
                        )?.map { LocalFileHolder(it) } ?: emptyList()
                }
            }
            is PickerLocation.Remote -> remoteItems
        }

        // Filtered contents based on search query
        val contents by remember(allContents, searchQuery) {
            derivedStateOf {
                if (searchQuery.isBlank()) {
                    allContents
                } else {
                    val query = searchQuery.trim().lowercase()
                    allContents.filter { it.displayName.lowercase().contains(query) }
                }
            }
        }

        // Back navigation handler
        BackHandler {
            if (isSearchActive) {
                isSearchActive = false
                searchQuery = ""
            } else {
                when (val loc = location) {
                    is PickerLocation.Local -> {
                        if (loc.dir.parentFile != null && loc.dir.absolutePath != Environment.getExternalStorageDirectory().absolutePath) {
                            location = PickerLocation.Local(loc.dir.parentFile!!)
                        } else {
                            setResult(Activity.RESULT_CANCELED)
                            finish()
                        }
                    }
                    is PickerLocation.Remote -> {
                        val parent = RemotePaths.parent(loc.path)
                        if (parent != null) {
                            location = PickerLocation.Remote(loc.connection, parent)
                        } else {
                            location = PickerLocation.Local(Environment.getExternalStorageDirectory())
                        }
                    }
                }
            }
        }

        Scaffold(
            topBar = {
                Column {
                    TopAppBar(
                        title = {
                            Column {
                                Text(
                                    text = when {
                                        isFolderMode -> stringResource(R.string.select_folder)
                                        allowMultiple -> stringResource(R.string.select_files)
                                        else -> stringResource(R.string.select_file)
                                    },
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = when (val loc = location) {
                                        is PickerLocation.Local -> loc.dir.name.ifEmpty { loc.dir.absolutePath }
                                        is PickerLocation.Remote -> "${loc.connection.name}: ${loc.path}"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        },
                        navigationIcon = {
                            IconButton(onClick = {
                                when (val loc = location) {
                                    is PickerLocation.Local -> {
                                        if (loc.dir.parentFile != null && loc.dir.absolutePath != Environment.getExternalStorageDirectory().absolutePath) {
                                            location = PickerLocation.Local(loc.dir.parentFile!!)
                                        } else {
                                            setResult(Activity.RESULT_CANCELED)
                                            finish()
                                        }
                                    }
                                    is PickerLocation.Remote -> {
                                        val parent = RemotePaths.parent(loc.path)
                                        if (parent != null) {
                                            location = PickerLocation.Remote(loc.connection, parent)
                                        } else {
                                            location = PickerLocation.Local(Environment.getExternalStorageDirectory())
                                        }
                                    }
                                }
                            }) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                    contentDescription = "Back"
                                )
                            }
                        },
                        actions = {
                            IconButton(onClick = {
                                isSearchActive = !isSearchActive
                                if (!isSearchActive) searchQuery = ""
                            }) {
                                Icon(
                                    imageVector = if (isSearchActive) Icons.Rounded.Close else Icons.Rounded.Search,
                                    contentDescription = "Search"
                                )
                            }

                            IconButton(onClick = { showNewFolderDialog = true }) {
                                Icon(
                                    imageVector = Icons.Rounded.CreateNewFolder,
                                    contentDescription = "New Folder"
                                )
                            }

                            IconButton(onClick = {
                                setResult(Activity.RESULT_CANCELED)
                                finish()
                            }) {
                                Icon(Icons.Rounded.Close, contentDescription = "Cancel")
                            }
                        }
                    )

                    // Search field
                    if (isSearchActive) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text(stringResource(R.string.search_query)) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp)
                                .autoShowKeyboard(),
                            singleLine = true,
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { searchQuery = "" }) {
                                        Icon(Icons.Rounded.Close, contentDescription = "Clear")
                                    }
                                }
                            }
                        )
                    }

                    // Storage Device & Remote Connection Switcher Chips
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                    ) {
                        items(storageDevices) { device ->
                            val rootPath = File(device.contentHolder.uniquePath)
                            val isSelected = location is PickerLocation.Local &&
                                    (location as PickerLocation.Local).dir.absolutePath.startsWith(rootPath.absolutePath)
                            FilterChip(
                                selected = isSelected,
                                onClick = { location = PickerLocation.Local(rootPath) },
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

                        items(remoteConnections) { conn ->
                            val isSelected = location is PickerLocation.Remote &&
                                    (location as PickerLocation.Remote).connection.id == conn.id
                            FilterChip(
                                selected = isSelected,
                                onClick = { location = PickerLocation.Remote(conn, "/") },
                                label = { Text("${conn.type}: ${conn.name}") },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Rounded.Cloud,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            )
                        }
                    }

                    // Breadcrumb navigation row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        LazyRow(
                            modifier = Modifier.weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            when (val loc = location) {
                                is PickerLocation.Local -> {
                                    val breadcrumbs = mutableListOf<File>()
                                    var cur: File? = loc.dir
                                    while (cur != null) {
                                        breadcrumbs.add(0, cur)
                                        cur = cur.parentFile
                                    }
                                    items(breadcrumbs) { folder ->
                                        val name = if (folder.parentFile == null) "/" else folder.name
                                        Text(
                                            text = name.ifEmpty { "/" },
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = if (folder == loc.dir)
                                                MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontWeight = if (folder == loc.dir) FontWeight.Bold else FontWeight.Normal,
                                            modifier = Modifier.clickable { location = PickerLocation.Local(folder) }
                                        )
                                        if (folder != loc.dir) {
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                                is PickerLocation.Remote -> {
                                    val parts = loc.path.trim('/').split('/').filter { it.isNotEmpty() }
                                    item {
                                        Text(
                                            text = loc.connection.name,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = if (loc.path == "/") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontWeight = if (loc.path == "/") FontWeight.Bold else FontWeight.Normal,
                                            modifier = Modifier.clickable { location = PickerLocation.Remote(loc.connection, "/") }
                                        )
                                        if (parts.isNotEmpty()) {
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                    var accumulated = ""
                                    items(parts) { part ->
                                        accumulated += "/$part"
                                        val thisPath = accumulated
                                        val isCurrent = thisPath == loc.path
                                        Text(
                                            text = part,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                            modifier = Modifier.clickable { location = PickerLocation.Remote(loc.connection, thisPath) }
                                        )
                                        if (!isCurrent) {
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Filter info chip
                        if (!isFolderMode && (primaryMimeFilter != "*/*" || extraMimeFilters.isNotEmpty())) {
                            val activeFilterLabel = if (extraMimeFilters.isNotEmpty()) {
                                extraMimeFilters.joinToString(", ")
                            } else {
                                primaryMimeFilter
                            }
                            AssistChip(
                                onClick = { showAllFilesOverride = !showAllFilesOverride },
                                label = {
                                    Text(
                                        text = if (showAllFilesOverride) stringResource(R.string.all_files)
                                        else stringResource(R.string.filter_files, activeFilterLabel),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = if (showAllFilesOverride) Icons.Rounded.Visibility else Icons.Rounded.FilterAlt,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            )
                        }
                    }

                    HorizontalDivider(
                        modifier = Modifier.padding(top = 4.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                }
            },
            bottomBar = {
                Surface(
                    tonalElevation = 6.dp,
                    shadowElevation = 8.dp,
                    color = MaterialTheme.colorScheme.surfaceContainer
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = {
                            setResult(Activity.RESULT_CANCELED)
                            finish()
                        }) {
                            Text(stringResource(R.string.cancel))
                        }

                        if (isFolderMode) {
                            Button(
                                onClick = {
                                    when (val loc = location) {
                                        is PickerLocation.Local -> confirmSelection(listOf(LocalFileHolder(loc.dir)))
                                        is PickerLocation.Remote -> confirmSelection(listOf(RemoteFileHolder(loc.connection, loc.path, isConnectionRoot = loc.path == "/")))
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.FolderOpen,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(stringResource(R.string.select_this_folder))
                            }
                        } else {
                            val count = selectedItems.size
                            Button(
                                onClick = { confirmSelection(selectedItems) },
                                enabled = count > 0 && !isDownloading,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    if (count > 1) stringResource(R.string.select_items_count, count)
                                    else stringResource(R.string.select_file)
                                )
                            }
                        }
                    }
                }
            }
        ) { paddingValues ->
            val listState = rememberLazyListState()

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                if (isRemoteLoading) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                } else if (remoteLoadError != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = remoteLoadError!!,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(onClick = {
                                val loc = location
                                location = loc // trigger reload
                            }) {
                                Text(stringResource(R.string.retry))
                            }
                        }
                    }
                } else if (contents.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.empty_folder),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .fastScrollbar(listState),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        items(contents, key = { it.uniquePath }) { item ->
                            val isSelected = selectedItems.any { it.uniquePath == item.uniquePath }
                            val matchesFilter = isFolderMode || showAllFilesOverride ||
                                    item.isFolder || isItemMatchingFilter(item)

                            PickerItemRow(
                                item = item,
                                isSelected = isSelected,
                                matchesFilter = matchesFilter,
                                isFolderMode = isFolderMode,
                                allowMultiple = allowMultiple,
                                onClick = {
                                    if (item.isFolder) {
                                        when (item) {
                                            is LocalFileHolder -> location = PickerLocation.Local(item.file)
                                            is RemoteFileHolder -> location = PickerLocation.Remote(item.connection, item.remotePath)
                                        }
                                    } else if (matchesFilter) {
                                        if (allowMultiple) {
                                            if (isSelected) selectedItems.removeAll { it.uniquePath == item.uniquePath }
                                            else selectedItems.add(item)
                                        } else {
                                            selectedItems.clear()
                                            selectedItems.add(item)
                                        }
                                    }
                                }
                            )
                        }
                    }
                }

                // Downloading Progress Dialog
                if (isDownloading) {
                    AlertDialog(
                        onDismissRequest = {},
                        title = { Text(stringResource(R.string.downloading_remote_files)) },
                        text = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                modifier = Modifier.padding(vertical = 8.dp)
                            ) {
                                CircularProgressIndicator()
                                Text(stringResource(R.string.loading))
                            }
                        },
                        confirmButton = {}
                    )
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
                title = { Text(stringResource(R.string.share_create_folder)) },
                text = {
                    OutlinedTextField(
                        value = newFolderName,
                        onValueChange = { newFolderName = it },
                        label = { Text(stringResource(R.string.share_folder_name)) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .autoShowKeyboard()
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (newFolderName.isNotBlank()) {
                                when (val loc = location) {
                                    is PickerLocation.Local -> {
                                        val newFolder = File(loc.dir, newFolderName.trim())
                                        if (newFolder.mkdir()) {
                                            location = PickerLocation.Local(newFolder)
                                        } else {
                                            showMsg(getString(R.string.failed_to_create_folder))
                                        }
                                    }
                                    is PickerLocation.Remote -> {
                                        lifecycleScope.launch(Dispatchers.IO) {
                                            try {
                                                val client = RemoteConnectionPool.clientFor(loc.connection)
                                                val newPath = RemotePaths.join(loc.path, newFolderName.trim())
                                                client.createDirectory(newPath)
                                                withContext(Dispatchers.Main) {
                                                    location = PickerLocation.Remote(loc.connection, newPath)
                                                }
                                            } catch (e: Exception) {
                                                withContext(Dispatchers.Main) {
                                                    showMsg(getString(R.string.failed_to_create_folder))
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            showNewFolderDialog = false
                            newFolderName = ""
                        },
                        enabled = newFolderName.isNotBlank()
                    ) {
                        Text(stringResource(R.string.create))
                    }
                },
                dismissButton = {
                    TextButton(onClick = {
                        showNewFolderDialog = false
                        newFolderName = ""
                    }) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            )
        }
    }

    @Composable
    private fun PickerItemRow(
        item: ContentHolder,
        isSelected: Boolean,
        matchesFilter: Boolean,
        isFolderMode: Boolean,
        allowMultiple: Boolean,
        onClick: () -> Unit
    ) {
        val alpha = if (matchesFilter) 1f else 0.4f

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(
                    if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                    else Color.Transparent
                )
                .clickable(enabled = item.isFolder || matchesFilter) { onClick() }
                .padding(vertical = 8.dp, horizontal = 8.dp)
                .alpha(alpha),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon container
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                FileContentIcon(item)
            }

            Spacer(modifier = Modifier.width(14.dp))

            // Name + info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (item.isFolder) FontWeight.SemiBold else FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = if (item.isFolder) {
                        item.lastModified.toFormattedDate()
                    } else {
                        "${item.size.toFormattedSize()} • ${item.lastModified.toFormattedDate()}"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp
                )
            }

            // Selection indicator or arrow
            if (item.isFolder) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.size(20.dp)
                )
            } else if (!isFolderMode) {
                if (allowMultiple) {
                    Checkbox(
                        checked = isSelected,
                        onCheckedChange = { onClick() },
                        enabled = matchesFilter
                    )
                } else if (isSelected) {
                    Icon(
                        imageVector = Icons.Rounded.CheckCircle,
                        contentDescription = "Selected",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }

    private fun isItemMatchingFilter(item: ContentHolder): Boolean {
        val extension = item.extension.lowercase()
        val mime = if (extension.isNotEmpty()) {
            MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
        } else {
            null
        } ?: if (item is LocalFileHolder) {
            MimeTypeDetector.detect(item.file)?.mimeType
        } else {
            null
        } ?: "application/octet-stream"

        if (matchesMimePattern(mime, primaryMimeFilter)) return true
        for (extra in extraMimeFilters) {
            if (matchesMimePattern(mime, extra)) return true
        }
        return false
    }

    private fun matchesMimePattern(mime: String, pattern: String): Boolean {
        if (pattern == "*/*" || pattern.isBlank()) return true
        if (pattern.endsWith("/*")) {
            val prefix = pattern.substringBefore("/*")
            return mime.startsWith("$prefix/")
        }
        return mime.equals(pattern, ignoreCase = true)
    }

    private fun confirmSelection(items: List<ContentHolder>) {
        if (items.isEmpty()) return

        lifecycleScope.launch {
            val downloadedFiles = mutableListOf<File>()

            for (item in items) {
                when (item) {
                    is LocalFileHolder -> downloadedFiles.add(item.file)
                    is RemoteFileHolder -> {
                        withContext(Dispatchers.IO) {
                            val cacheDir = File(cacheDir, "picker_cache/${item.connection.id}").apply { mkdirs() }
                            val targetFile = File(cacheDir, item.displayName)
                            try {
                                item.client.downloadFile(item.remotePath, targetFile.absolutePath) {}
                                if (targetFile.exists()) downloadedFiles.add(targetFile)
                            } catch (_: Exception) {}
                        }
                    }
                }
            }

            if (downloadedFiles.isEmpty()) return@launch

            val resultIntent = Intent()
            val uris = downloadedFiles.mapNotNull { file ->
                try {
                    FileProvider.getUriForFile(
                        this@FilePickerActivity,
                        "$packageName.provider",
                        file
                    )
                } catch (e: Exception) {
                    Uri.fromFile(file)
                }
            }

            if (uris.isEmpty()) return@launch

            val firstUri = uris.first()
            resultIntent.data = firstUri

            if (uris.size > 1) {
                val clipData = ClipData.newUri(contentResolver, "files", firstUri)
                uris.drop(1).forEach { uri ->
                    clipData.addItem(ClipData.Item(uri))
                }
                resultIntent.clipData = clipData
            }

            resultIntent.addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                        Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
            )

            setResult(Activity.RESULT_OK, resultIntent)
            finish()
        }
    }
}
