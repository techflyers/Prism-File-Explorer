package com.techflyers.compose.file.explorer.screen.main.tab.nfile_tools.ui

import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.InsertDriveFile
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.techflyers.compose.file.explorer.App.Companion.globalClass
import com.techflyers.compose.file.explorer.R
import com.techflyers.compose.file.explorer.common.toFormattedSize
import com.techflyers.compose.file.explorer.screen.main.tab.files.FilesTab
import com.techflyers.compose.file.explorer.screen.main.tab.files.holder.LocalFileHolder
import com.techflyers.compose.file.explorer.screen.main.tab.files.holder.StorageDevice
import com.techflyers.compose.file.explorer.screen.main.tab.files.holder.VirtualFileHolder
import com.techflyers.compose.file.explorer.screen.main.tab.files.provider.StorageProvider
import com.techflyers.compose.file.explorer.screen.main.tab.nfile_tools.StorageAnalysisTab
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max

data class StorageCategoryItem(
    val id: String,
    val title: String,
    val size: Long,
    val count: Int,
    val color: Color,
    val icon: ImageVector,
    val onClick: (() -> Unit)? = null
)

data class FolderSizeEntry(
    val file: File,
    val name: String,
    val size: Long,
    val isDirectory: Boolean,
    val itemCount: Int
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StorageAnalysisScreen(tab: StorageAnalysisTab) {
    val context = LocalContext.current
    val manager = globalClass.mainActivityManager
    val scope = rememberCoroutineScope()

    var storageDevices by remember { mutableStateOf<List<StorageDevice>>(emptyList()) }
    var selectedDeviceIndex by remember { mutableIntStateOf(0) }
    var selectedViewTab by remember { mutableIntStateOf(0) } // 0: Categories, 1: Folder Breakdown

    // Category breakdown state
    var isAnalyzingCategories by remember { mutableStateOf(true) }
    var categories by remember { mutableStateOf<List<StorageCategoryItem>>(emptyList()) }

    // Folder breakdown state
    var currentFolder by remember { mutableStateOf(Environment.getExternalStorageDirectory()) }
    var folderHistory by remember { mutableStateOf<List<File>>(emptyList()) }
    var folderEntries by remember { mutableStateOf<List<FolderSizeEntry>>(emptyList()) }
    var isAnalyzingFolders by remember { mutableStateOf(false) }
    val folderSizeCache = remember { mutableMapOf<String, Long>() }

    // Load storage devices
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val devices = StorageProvider.getStorageDevices(context)
            storageDevices = devices
        }
    }

    val activeDevice = storageDevices.getOrNull(selectedDeviceIndex)
        ?: StorageProvider.getPrimaryInternalStorage(context)
    val deviceRootFile = (activeDevice.contentHolder as? LocalFileHolder)?.file
        ?: Environment.getExternalStorageDirectory()

    // Function to calculate categories via MediaStore
    val refreshCategories: () -> Unit = {
        isAnalyzingCategories = true
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                calculateStorageCategories(context, activeDevice)
            }
            categories = result
            isAnalyzingCategories = false
        }
    }

    // Function to calculate folder entries
    val refreshFolderBreakdown: (File) -> Unit = { folder ->
        isAnalyzingFolders = true
        scope.launch {
            val entries = withContext(Dispatchers.IO) {
                calculateFolderSizes(folder, folderSizeCache)
            }
            folderEntries = entries
            isAnalyzingFolders = false
        }
    }

    // Trigger categories scan on device change
    LaunchedEffect(activeDevice) {
        refreshCategories()
        currentFolder = deviceRootFile
        folderHistory = emptyList()
        refreshFolderBreakdown(deviceRootFile)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Storage Overview Card
        StorageOverviewCard(
            device = activeDevice,
            categories = categories,
            devices = storageDevices,
            selectedDeviceIndex = selectedDeviceIndex,
            onSelectDevice = { selectedDeviceIndex = it }
        )

        // Tab Selector: Categories vs Folder Breakdown
        TabRow(
            selectedTabIndex = selectedViewTab,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.primary
        ) {
            Tab(
                selected = selectedViewTab == 0,
                onClick = { selectedViewTab = 0 },
                text = { Text(stringResource(R.string.storage_breakdown), fontWeight = FontWeight.SemiBold) },
                icon = { Icon(Icons.Rounded.PieChart, contentDescription = null, modifier = Modifier.size(20.dp)) }
            )
            Tab(
                selected = selectedViewTab == 1,
                onClick = { selectedViewTab = 1 },
                text = { Text(stringResource(R.string.folder_analysis), fontWeight = FontWeight.SemiBold) },
                icon = { Icon(Icons.Rounded.FolderOpen, contentDescription = null, modifier = Modifier.size(20.dp)) }
            )
        }

        if (selectedViewTab == 0) {
            // Category Breakdown List
            if (isAnalyzingCategories) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(modifier = Modifier.size(48.dp))
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = stringResource(R.string.calculating),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(categories) { category ->
                        CategoryCard(
                            category = category,
                            totalUsed = activeDevice.usedSize
                        )
                    }
                }
            }
        } else {
            // Folder Breakdown Explorer
            Column(modifier = Modifier.fillMaxSize()) {
                // Navigation Bar / Breadcrumb
                FolderNavigationBar(
                    currentFolder = currentFolder,
                    rootFolder = deviceRootFile,
                    canGoBack = folderHistory.isNotEmpty(),
                    onGoBack = {
                        if (folderHistory.isNotEmpty()) {
                            val previous = folderHistory.last()
                            folderHistory = folderHistory.dropLast(1)
                            currentFolder = previous
                            refreshFolderBreakdown(previous)
                        }
                    },
                    onOpenInFiles = {
                        manager.addTabAndSelect(FilesTab(LocalFileHolder(currentFolder)))
                    }
                )

                if (isAnalyzingFolders) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(modifier = Modifier.size(48.dp))
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = stringResource(R.string.calculating),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else if (folderEntries.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.no_subfolders_found),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    val maxEntrySize = folderEntries.firstOrNull()?.size?.coerceAtLeast(1L) ?: 1L
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(folderEntries) { entry ->
                            FolderSizeRow(
                                entry = entry,
                                maxEntrySize = maxEntrySize,
                                onClick = {
                                    if (entry.isDirectory) {
                                        folderHistory = folderHistory + currentFolder
                                        currentFolder = entry.file
                                        refreshFolderBreakdown(entry.file)
                                    } else {
                                        manager.addTabAndSelect(FilesTab(LocalFileHolder(entry.file)))
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StorageOverviewCard(
    device: StorageDevice,
    categories: List<StorageCategoryItem>,
    devices: List<StorageDevice>,
    selectedDeviceIndex: Int,
    onSelectDevice: (Int) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header: Device name & selector
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.Storage,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = device.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (devices.size > 1) {
                    var expanded by remember { mutableStateOf(false) }
                    Box {
                        TextButton(onClick = { expanded = true }) {
                            Text(stringResource(R.string.storage_device))
                            Icon(Icons.Rounded.ArrowDropDown, contentDescription = null)
                        }
                        DropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            devices.forEachIndexed { index, d ->
                                DropdownMenuItem(
                                    text = { Text(d.title) },
                                    onClick = {
                                        onSelectDevice(index)
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Capacity summary: "64 GB of 128 GB used" + "64 GB free"
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Column {
                    Text(
                        text = stringResource(
                            R.string.used_of_total,
                            device.usedSize.toFormattedSize(),
                            device.totalSize.toFormattedSize()
                        ),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Text(
                    text = stringResource(R.string.free_storage, (device.totalSize - device.usedSize).coerceAtLeast(0L).toFormattedSize()),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Proportional Multi-colored Segmented Bar
            val totalSize = device.totalSize.coerceAtLeast(1L)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(14.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            ) {
                if (categories.isNotEmpty()) {
                    for (cat in categories) {
                        val weight = (cat.size.toFloat() / totalSize.toFloat()).coerceAtLeast(0f)
                        if (weight > 0.005f) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .weight(weight)
                                    .background(cat.color)
                            )
                        }
                    }
                    val freeWeight = ((device.totalSize - device.usedSize).toFloat() / totalSize.toFloat()).coerceAtLeast(0f)
                    if (freeWeight > 0.005f) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .weight(freeWeight)
                                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                        )
                    }
                } else {
                    val usedFraction = (device.usedSize.toFloat() / totalSize.toFloat()).coerceIn(0f, 1f)
                    if (usedFraction > 0f) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .weight(usedFraction)
                                .background(MaterialTheme.colorScheme.primary)
                        )
                    }
                    if (usedFraction < 1f) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .weight(1f - usedFraction)
                                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryCard(
    category: StorageCategoryItem,
    totalUsed: Long
) {
    val pct = if (totalUsed > 0L) {
        ((category.size.toDouble() / totalUsed.toDouble()) * 100).toInt()
    } else 0

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable {
                category.onClick?.invoke()
            },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Category Icon with colored background
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(category.color.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = category.icon,
                    contentDescription = null,
                    tint = category.color,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = category.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "${category.count} files • $pct%",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text(
                text = category.size.toFormattedSize(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            if (category.onClick != null) {
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun FolderNavigationBar(
    currentFolder: File,
    rootFolder: File,
    canGoBack: Boolean,
    onGoBack: () -> Unit,
    onOpenInFiles: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (canGoBack) {
            IconButton(onClick = onGoBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
            }
        } else {
            Icon(
                imageVector = Icons.Rounded.Folder,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(8.dp).size(24.dp)
            )
        }

        Text(
            text = if (currentFolder == rootFolder) "Root: ${currentFolder.name.ifEmpty { "Storage" }}" else currentFolder.name,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )

        IconButton(onClick = onOpenInFiles) {
            Icon(
                imageVector = Icons.Rounded.OpenInNew,
                contentDescription = stringResource(R.string.explore_in_files),
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun FolderSizeRow(
    entry: FolderSizeEntry,
    maxEntrySize: Long,
    onClick: () -> Unit
) {
    val relativeFraction = (entry.size.toFloat() / maxEntrySize.toFloat()).coerceIn(0f, 1f)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = if (entry.isDirectory) Icons.Rounded.Folder else Icons.AutoMirrored.Rounded.InsertDriveFile,
                    contentDescription = null,
                    tint = if (entry.isDirectory) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = entry.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = entry.size.toFormattedSize(),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Percentage bar
            LinearProgressIndicator(
                progress = { relativeFraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = if (entry.isDirectory) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
            )
        }
    }
}

/**
 * Queries MediaStore to aggregate files by category.
 */
private fun calculateStorageCategories(
    context: Context,
    device: StorageDevice
): List<StorageCategoryItem> {
    val manager = globalClass.mainActivityManager
    var imagesSize = 0L; var imagesCount = 0
    var videosSize = 0L; var videosCount = 0
    var audioSize = 0L; var audioCount = 0
    var docsSize = 0L; var docsCount = 0
    var apksSize = 0L; var apksCount = 0
    var archivesSize = 0L; var archivesCount = 0
    var otherSize = 0L; var otherCount = 0

    val docExtensions = setOf("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "epub", "rtf", "odt")
    val archiveExtensions = setOf("zip", "rar", "7z", "tar", "gz", "tgz", "bz2")

    try {
        val uri: Uri = MediaStore.Files.getContentUri("external")
        val projection = arrayOf(
            MediaStore.Files.FileColumns.SIZE,
            MediaStore.Files.FileColumns.MIME_TYPE,
            MediaStore.Files.FileColumns.DATA
        )

        val cursor: Cursor? = context.contentResolver.query(
            uri,
            projection,
            "${MediaStore.Files.FileColumns.SIZE} > 0",
            null,
            null
        )

        cursor?.use { c ->
            val sizeCol = c.getColumnIndex(MediaStore.Files.FileColumns.SIZE)
            val mimeCol = c.getColumnIndex(MediaStore.Files.FileColumns.MIME_TYPE)
            val dataCol = c.getColumnIndex(MediaStore.Files.FileColumns.DATA)

            while (c.moveToNext()) {
                val size = if (sizeCol >= 0) c.getLong(sizeCol) else 0L
                val mime = if (mimeCol >= 0) c.getString(mimeCol)?.lowercase() ?: "" else ""
                val path = if (dataCol >= 0) c.getString(dataCol) ?: "" else ""
                val ext = File(path).extension.lowercase()

                when {
                    mime.startsWith("image/") -> {
                        imagesSize += size
                        imagesCount++
                    }
                    mime.startsWith("video/") -> {
                        videosSize += size
                        videosCount++
                    }
                    mime.startsWith("audio/") -> {
                        audioSize += size
                        audioCount++
                    }
                    mime == "application/vnd.android.package-archive" || ext in setOf("apk", "xapk", "apks") -> {
                        apksSize += size
                        apksCount++
                    }
                    ext in docExtensions || mime.startsWith("text/") || mime.contains("pdf") || mime.contains("document") -> {
                        docsSize += size
                        docsCount++
                    }
                    ext in archiveExtensions || mime.contains("zip") || mime.contains("tar") || mime.contains("compressed") -> {
                        archivesSize += size
                        archivesCount++
                    }
                    else -> {
                        otherSize += size
                        otherCount++
                    }
                }
            }
        }
    } catch (_: Exception) {}

    val catalogedSum = imagesSize + videosSize + audioSize + docsSize + apksSize + archivesSize + otherSize
    val systemSize = max(0L, device.usedSize - catalogedSum)

    return listOf(
        StorageCategoryItem(
            id = "images",
            title = context.getString(R.string.images),
            size = imagesSize,
            count = imagesCount,
            color = Color(0xFF4CAF50), // Green
            icon = Icons.Rounded.Image,
            onClick = { manager.addTabAndSelect(FilesTab(VirtualFileHolder(VirtualFileHolder.IMAGE))) }
        ),
        StorageCategoryItem(
            id = "videos",
            title = context.getString(R.string.videos),
            size = videosSize,
            count = videosCount,
            color = Color(0xFFFF9800), // Orange
            icon = Icons.Rounded.VideoFile,
            onClick = { manager.addTabAndSelect(FilesTab(VirtualFileHolder(VirtualFileHolder.VIDEO))) }
        ),
        StorageCategoryItem(
            id = "audio",
            title = context.getString(R.string.audios),
            size = audioSize,
            count = audioCount,
            color = Color(0xFFE91E63), // Pink
            icon = Icons.Rounded.AudioFile,
            onClick = { manager.addTabAndSelect(FilesTab(VirtualFileHolder(VirtualFileHolder.AUDIO))) }
        ),
        StorageCategoryItem(
            id = "documents",
            title = context.getString(R.string.documents),
            size = docsSize,
            count = docsCount,
            color = Color(0xFF2196F3), // Blue
            icon = Icons.Rounded.Description,
            onClick = { manager.addTabAndSelect(FilesTab(VirtualFileHolder(VirtualFileHolder.DOCUMENT))) }
        ),
        StorageCategoryItem(
            id = "apks",
            title = context.getString(R.string.apks_and_apps),
            size = apksSize,
            count = apksCount,
            color = Color(0xFF00BCD4), // Cyan
            icon = Icons.Rounded.Android,
            onClick = null
        ),
        StorageCategoryItem(
            id = "archives",
            title = context.getString(R.string.archives),
            size = archivesSize,
            count = archivesCount,
            color = Color(0xFF9C27B0), // Purple
            icon = Icons.Rounded.Archive,
            onClick = { manager.addTabAndSelect(FilesTab(VirtualFileHolder(VirtualFileHolder.ARCHIVE))) }
        ),
        StorageCategoryItem(
            id = "other",
            title = context.getString(R.string.other_files),
            size = otherSize,
            count = otherCount,
            color = Color(0xFFFFC107), // Amber
            icon = Icons.Rounded.FolderZip,
            onClick = null
        ),
        StorageCategoryItem(
            id = "system",
            title = context.getString(R.string.system_and_apps),
            size = systemSize,
            count = 1,
            color = Color(0xFF78909C), // Blue Grey
            icon = Icons.Rounded.Dns,
            onClick = null
        )
    )
}

/**
 * Calculates sizes of direct children of [folder].
 */
private fun calculateFolderSizes(
    folder: File,
    cache: MutableMap<String, Long>
): List<FolderSizeEntry> {
    if (!folder.exists() || !folder.canRead()) return emptyList()

    val children = folder.listFiles() ?: return emptyList()
    val entries = mutableListOf<FolderSizeEntry>()

    for (child in children) {
        if (child.isDirectory) {
            val cachedSize = cache[child.absolutePath]
            val totalSize = if (cachedSize != null) {
                cachedSize
            } else {
                var sum = 0L
                try {
                    child.walkTopDown()
                        .maxDepth(12)
                        .onFail { _, _ -> }
                        .forEach { f ->
                            if (f.isFile) sum += f.length()
                        }
                } catch (_: Exception) {}
                cache[child.absolutePath] = sum
                sum
            }
            val subCount = child.list()?.size ?: 0
            entries.add(FolderSizeEntry(child, child.name, totalSize, true, subCount))
        } else {
            entries.add(FolderSizeEntry(child, child.name, child.length(), false, 0))
        }
    }

    return entries.sortedByDescending { it.size }
}
