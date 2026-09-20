package com.techflyers.compose.file.explorer.screen.main.tab.nfile_tools.ui

import android.content.Context
import android.os.Environment
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.InsertDriveFile
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
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
import com.techflyers.compose.file.explorer.App.Companion.globalClass
import com.techflyers.compose.file.explorer.R
import com.techflyers.compose.file.explorer.common.toFormattedSize
import com.techflyers.compose.file.explorer.common.ui.CheckableText
import com.techflyers.compose.file.explorer.screen.main.tab.files.FilesTab
import com.techflyers.compose.file.explorer.screen.main.tab.files.holder.ContentHolder
import com.techflyers.compose.file.explorer.screen.main.tab.files.holder.LocalFileHolder
import com.techflyers.compose.file.explorer.screen.main.ui.TooltipIconButton
import com.techflyers.compose.file.explorer.screen.main.tab.files.task.CopyTask
import com.techflyers.compose.file.explorer.screen.main.tab.files.task.CopyTaskParameters
import com.techflyers.compose.file.explorer.screen.main.tab.files.task.DeleteTask
import com.techflyers.compose.file.explorer.screen.main.tab.files.task.DeleteTaskParameters
import com.techflyers.compose.file.explorer.screen.main.tab.nfile_tools.DuplicateFileItem
import com.techflyers.compose.file.explorer.screen.main.tab.nfile_tools.DuplicateFinderTab
import com.techflyers.compose.file.explorer.screen.main.tab.nfile_tools.DuplicateGroup
import com.techflyers.compose.file.explorer.screen.main.tab.nfile_tools.MatchMode
import com.techflyers.compose.file.explorer.screen.main.tab.nfile_tools.MinFileSizeFilter
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DuplicateFinderScreen(tab: DuplicateFinderTab) {
    val context = LocalContext.current
    val manager = globalClass.mainActivityManager
    val scope = rememberCoroutineScope()

    val scanTargetFolder = tab.scanTargetFolder
    val minSizeBytes = tab.minSizeBytes
    val isCustomSize = tab.isCustomSize
    val customSizeLabel = tab.customSizeLabel
    val matchMode = tab.matchMode

    var showFolderPickerDialog by remember { mutableStateOf(false) }
    var showCustomSizeDialog by remember { mutableStateOf(false) }

    val preferencesManager = globalClass.preferencesManager
    var moveToRecycleBin by remember { mutableStateOf(preferencesManager.moveToRecycleBin) }
    var showRememberChoice by remember { mutableStateOf(false) }
    var rememberChoice by remember { mutableStateOf(false) }

    val isScanning = tab.isScanning
    val scannedFilesCount = tab.scannedFilesCount
    val currentScanningFile = tab.currentScanningFile
    val duplicateGroups = tab.duplicateGroups
    val hasScanned = tab.hasScanned
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    val startScan = {
        tab.startScan { folder, size, mode, onProgress ->
            findDuplicates(folder, size, mode, onProgress)
        }
    }

    val stopScan = {
        tab.stopScan()
    }

    // Selection helpers
    val selectExceptNewest = {
        tab.duplicateGroups = tab.duplicateGroups.map { group ->
            val newest = group.files.maxByOrNull { it.lastModified }
            val updatedFiles = group.files.map { item ->
                item.copy(isSelectedForDeletion = item != newest)
            }.toMutableList()
            group.copy(files = updatedFiles)
        }
    }

    val selectExceptOldest = {
        tab.duplicateGroups = tab.duplicateGroups.map { group ->
            val oldest = group.files.minByOrNull { it.lastModified }
            val updatedFiles = group.files.map { item ->
                item.copy(isSelectedForDeletion = item != oldest)
            }.toMutableList()
            group.copy(files = updatedFiles)
        }
    }

    val selectExceptFirst = {
        tab.duplicateGroups = tab.duplicateGroups.map { group ->
            val updatedFiles = group.files.mapIndexed { index, item ->
                item.copy(isSelectedForDeletion = index > 0)
            }.toMutableList()
            group.copy(files = updatedFiles)
        }
    }

    val deselectAll = {
        tab.duplicateGroups = tab.duplicateGroups.map { group ->
            val updatedFiles = group.files.map { item ->
                item.copy(isSelectedForDeletion = false)
            }.toMutableList()
            group.copy(files = updatedFiles)
        }
    }

    val selectedFilesCount = duplicateGroups.sumOf { g -> g.files.count { it.isSelectedForDeletion } }
    val selectedFilesSize = duplicateGroups.sumOf { g -> g.files.filter { it.isSelectedForDeletion }.sumOf { it.size } }

    val deleteSelectedFiles = {
        scope.launch {
            val selectedItems = duplicateGroups.flatMap { g -> g.files.filter { it.isSelectedForDeletion } }
            if (selectedItems.isEmpty()) return@launch

            if (showRememberChoice && rememberChoice) {
                preferencesManager.moveToRecycleBin = moveToRecycleBin
            }

            val filesToDelete = selectedItems.map { it.file }
            val contentHolders: List<ContentHolder> = filesToDelete.map { LocalFileHolder(it) }

            if (moveToRecycleBin) {
                val timestamp = System.currentTimeMillis().toString()
                globalClass.recycleBinDir.createSubFolder(timestamp) { newDir ->
                    if (newDir != null) {
                        saveRecycleBinMetadata(newDir, contentHolders)
                        globalClass.taskManager.addTaskAndRun(
                            CopyTask(contentHolders, true),
                            CopyTaskParameters(newDir)
                        )
                    } else {
                        globalClass.showMsg(globalClass.getString(R.string.unable_to_move_to_recycle_bin))
                    }
                }
            } else {
                globalClass.taskManager.addTaskAndRun(
                    DeleteTask(contentHolders),
                    DeleteTaskParameters()
                )
            }

            // Update UI list by removing deleted files
            val updatedGroups = tab.duplicateGroups.mapNotNull { group ->
                val remaining = group.files.filter { !it.isSelectedForDeletion }.toMutableList()
                if (remaining.size >= 2) group.copy(files = remaining) else null
            }
            tab.duplicateGroups = updatedGroups
            showDeleteConfirmDialog = false
            Toast.makeText(context, context.getString(R.string.duplicates_deleted, filesToDelete.size), Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        bottomBar = {
            if (selectedFilesCount > 0) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    tonalElevation = 6.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "$selectedFilesCount files selected",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${selectedFilesSize.toFormattedSize()} reclaimable",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        Button(
                            onClick = { showDeleteConfirmDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        ) {
                            Icon(Icons.Rounded.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.delete_selected, selectedFilesCount, selectedFilesSize.toFormattedSize()))
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Setup & Configuration Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.Difference,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.duplicate_finder),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Scan Scope: Folder Selector
                    Text(
                        text = "Scan Folder: ${scanTargetFolder.name.ifEmpty { "Storage" }}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = scanTargetFolder.absolutePath,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Quick Folder Chips
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val storageRoot = Environment.getExternalStorageDirectory()
                        val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                        val dcim = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM)
                        val isPresetFolder = scanTargetFolder == storageRoot || scanTargetFolder == downloads || scanTargetFolder == dcim

                        FilterChip(
                            selected = scanTargetFolder == storageRoot,
                            onClick = { tab.scanTargetFolder = storageRoot },
                            label = { Text("All Storage", fontSize = 12.sp) }
                        )
                        FilterChip(
                            selected = scanTargetFolder == downloads,
                            onClick = { tab.scanTargetFolder = downloads },
                            label = { Text("Downloads", fontSize = 12.sp) }
                        )
                        FilterChip(
                            selected = scanTargetFolder == dcim,
                            onClick = { tab.scanTargetFolder = dcim },
                            label = { Text("DCIM", fontSize = 12.sp) }
                        )
                        FilterChip(
                            selected = !isPresetFolder,
                            onClick = { showFolderPickerDialog = true },
                            label = {
                                Text(
                                    if (!isPresetFolder) "Custom: ${scanTargetFolder.name}" else stringResource(R.string.custom_folder),
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Size Filter Chips
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "Min Size:",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        MinFileSizeFilter.values().forEach { filter ->
                            FilterChip(
                                selected = minSizeBytes == filter.bytes && !isCustomSize,
                                onClick = {
                                    tab.minSizeBytes = filter.bytes
                                    tab.isCustomSize = false
                                },
                                label = { Text(stringResource(filter.labelRes), fontSize = 11.sp) }
                            )
                        }
                        FilterChip(
                            selected = isCustomSize,
                            onClick = { showCustomSizeDialog = true },
                            label = {
                                Text(
                                    if (isCustomSize && customSizeLabel.isNotEmpty()) customSizeLabel else stringResource(R.string.custom_size_limit),
                                    fontSize = 11.sp
                                )
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Action Button: Start or Stop Scan
                    if (isScanning) {
                        Button(
                            onClick = { stopScan() },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Rounded.Close, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.stop_scan))
                        }
                    } else {
                        Button(
                            onClick = { startScan() },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Rounded.Search, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.start_scan))
                        }
                    }
                }
            }

            // Scanning Progress indicator
            if (isScanning) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.5.dp)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = stringResource(R.string.scanning_progress, scannedFilesCount, duplicateGroups.size),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = currentScanningFile,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }

            // Results Section
            if (!isScanning && hasScanned) {
                if (duplicateGroups.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Rounded.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(56.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = stringResource(R.string.no_duplicates_found),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                } else {
                    val totalWasted = duplicateGroups.sumOf { (it.files.size - 1) * it.size }
                    val totalDupFiles = duplicateGroups.sumOf { it.files.size }

                    Column(modifier = Modifier.fillMaxSize()) {
                        // Summary Banner
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.duplicates_summary, duplicateGroups.size, totalDupFiles, totalWasted.toFormattedSize()),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        // Selection Action Chips
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            AssistChip(
                                onClick = { selectExceptNewest() },
                                label = { Text(stringResource(R.string.select_except_newest), fontSize = 11.sp) }
                            )
                            AssistChip(
                                onClick = { selectExceptOldest() },
                                label = { Text(stringResource(R.string.select_except_oldest), fontSize = 11.sp) }
                            )
                            AssistChip(
                                onClick = { deselectAll() },
                                label = { Text(stringResource(R.string.deselect_all), fontSize = 11.sp) }
                            )
                        }

                        // Duplicate Groups List
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(duplicateGroups, key = { it.id }) { group ->
                                DuplicateGroupCard(
                                    group = group,
                                    onToggleFileSelection = { fileItem ->
                                        tab.duplicateGroups = tab.duplicateGroups.map { g ->
                                            if (g.id == group.id) {
                                                val updated = g.files.map { f ->
                                                    if (f.file.absolutePath == fileItem.file.absolutePath) {
                                                        f.copy(isSelectedForDeletion = !f.isSelectedForDeletion)
                                                    } else f
                                                }.toMutableList()
                                                g.copy(files = updated)
                                            } else g
                                        }
                                    },
                                    onOpenFile = { fileItem ->
                                        manager.addTabAndSelect(FilesTab(LocalFileHolder(fileItem.file)))
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        // Delete Confirmation Dialog with Recycle Bin support
        if (showDeleteConfirmDialog) {
            AlertDialog(
                onDismissRequest = { showDeleteConfirmDialog = false },
                title = { Text(stringResource(R.string.confirm_delete_duplicates, selectedFilesCount)) },
                text = {
                    Column(Modifier.fillMaxWidth()) {
                        Text("This will remove $selectedFilesCount duplicate files and reclaim ${selectedFilesSize.toFormattedSize()} of storage space.")
                        Spacer(modifier = Modifier.height(12.dp))

                        CheckableText(
                            modifier = Modifier.fillMaxWidth(),
                            checked = moveToRecycleBin,
                            onCheckedChange = {
                                moveToRecycleBin = it
                                showRememberChoice = true
                            },
                            text = {
                                Text(
                                    modifier = Modifier.alpha(0.85f),
                                    text = stringResource(R.string.move_to_recycle_bin)
                                )
                            }
                        )

                        if (showRememberChoice) {
                            Spacer(modifier = Modifier.height(4.dp))
                            CheckableText(
                                modifier = Modifier.fillMaxWidth(),
                                checked = rememberChoice,
                                onCheckedChange = { rememberChoice = it },
                                text = {
                                    Text(
                                        modifier = Modifier.alpha(0.7f),
                                        text = stringResource(R.string.remember_this_choice)
                                    )
                                }
                            )
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = { deleteSelectedFiles() },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text(stringResource(R.string.delete))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteConfirmDialog = false }) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            )
        }

        if (showFolderPickerDialog) {
            FolderPickerDialog(
                initialFolder = scanTargetFolder,
                onFolderSelected = {
                    tab.scanTargetFolder = it
                    showFolderPickerDialog = false
                },
                onDismiss = { showFolderPickerDialog = false }
            )
        }

        if (showCustomSizeDialog) {
            CustomSizeLimitDialog(
                currentSizeBytes = minSizeBytes,
                onSizeSelected = { bytes, label ->
                    tab.minSizeBytes = bytes
                    tab.customSizeLabel = label
                    tab.isCustomSize = true
                    showCustomSizeDialog = false
                },
                onDismiss = { showCustomSizeDialog = false }
            )
        }
    }
}

@Composable
private fun DuplicateGroupCard(
    group: DuplicateGroup,
    onToggleFileSelection: (DuplicateFileItem) -> Unit,
    onOpenFile: (DuplicateFileItem) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Group Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.InsertDriveFile,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = group.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = "${group.size.toFormattedSize()} • ${group.files.size} copies",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium
                )
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 8.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest
            )

            // File items list
            group.files.forEach { item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onToggleFileSelection(item) }
                        .padding(vertical = 6.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = item.isSelectedForDeletion,
                        onCheckedChange = { onToggleFileSelection(item) }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = item.file.parentFile?.absolutePath ?: item.file.absolutePath,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = if (item.isSelectedForDeletion) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = item.file.name,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    TooltipIconButton(
                        tooltip = stringResource(R.string.open_containing_folder),
                        onClick = { onOpenFile(item) },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.OpenInNew,
                            contentDescription = stringResource(R.string.open_containing_folder),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Scanning engine with multi-phase hashing.
 */
internal suspend fun findDuplicates(
    rootFolder: File,
    minSizeBytes: Long,
    mode: MatchMode,
    onProgress: (Int, String) -> Unit
): List<DuplicateGroup> = withContext(Dispatchers.IO) {
    if (!rootFolder.exists() || !rootFolder.canRead()) return@withContext emptyList()

    val candidateFiles = mutableListOf<File>()
    var count = 0

    // Phase 1: File collection & size filter
    try {
        rootFolder.walkTopDown()
            .maxDepth(15)
            .onFail { _, _ -> }
            .forEach { file ->
                if (!coroutineContext.isActive) return@withContext emptyList()
                if (file.isFile && file.length() >= minSizeBytes) {
                    candidateFiles.add(file)
                    count++
                    if (count % 50 == 0) {
                        onProgress(count, file.name)
                    }
                }
            }
    } catch (_: Exception) {}

    // Group by size first — unique sizes can never be duplicates
    val sizeGroups = candidateFiles.groupBy { it.length() }.filter { it.value.size >= 2 }

    val results = mutableListOf<DuplicateGroup>()

    if (mode == MatchMode.FAST_NAME_SIZE) {
        // Fast mode: Name + Size match
        for ((size, files) in sizeGroups) {
            val nameGroups = files.groupBy { it.name }.filter { it.value.size >= 2 }
            for ((name, matchingFiles) in nameGroups) {
                val groupItems = matchingFiles.map { DuplicateFileItem(it, size, it.lastModified()) }.toMutableList()
                results.add(DuplicateGroup(id = "${name}_$size", name = name, size = size, files = groupItems))
            }
        }
    } else {
        // Accurate mode: Multi-phase hash
        for ((size, files) in sizeGroups) {
            if (!coroutineContext.isActive) return@withContext emptyList()

            // Partial hash (first 8 KB)
            val partialHashGroups = files.groupBy { file ->
                computePartialHash(file)
            }.filter { it.value.size >= 2 && it.key.isNotEmpty() }

            // Full hash for surviving candidates
            for ((_, partialMatchingFiles) in partialHashGroups) {
                if (!coroutineContext.isActive) return@withContext emptyList()

                val fullHashGroups = partialMatchingFiles.groupBy { file ->
                    computeFullHash(file)
                }.filter { it.value.size >= 2 && it.key.isNotEmpty() }

                for ((hash, matchingFiles) in fullHashGroups) {
                    val first = matchingFiles.first()
                    val groupItems = matchingFiles.map { DuplicateFileItem(it, size, it.lastModified()) }.toMutableList()
                    results.add(DuplicateGroup(id = hash, name = first.name, size = size, files = groupItems))
                }
            }
        }
    }

    return@withContext results.sortedByDescending { (it.files.size - 1) * it.size }
}

private fun computePartialHash(file: File): String {
    return try {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { fis ->
            val buffer = ByteArray(8192)
            val bytesRead = fis.read(buffer)
            if (bytesRead > 0) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        digest.digest().joinToString("") { "%02x".format(it) }
    } catch (_: Exception) { "" }
}

private fun computeFullHash(file: File): String {
    return try {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { fis ->
            val buffer = ByteArray(65536)
            var bytesRead: Int
            while (fis.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        digest.digest().joinToString("") { "%02x".format(it) }
    } catch (_: Exception) { "" }
}

@Composable
private fun FolderPickerDialog(
    initialFolder: File,
    onFolderSelected: (File) -> Unit,
    onDismiss: () -> Unit
) {
    var currentDir by remember {
        mutableStateOf(
            if (initialFolder.exists() && initialFolder.isDirectory) initialFolder
            else Environment.getExternalStorageDirectory()
        )
    }
    var directPathInput by remember { mutableStateOf(currentDir.absolutePath) }
    var isManualInput by remember { mutableStateOf(false) }

    val subfolders = remember(currentDir) {
        try {
            currentDir.listFiles { file -> file.isDirectory && !file.isHidden }
                ?.sortedBy { it.name.lowercase() }
                ?.toList() ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.custom_folder_picker),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val hasParent = currentDir.parentFile != null && currentDir.parentFile?.canRead() == true
                    IconButton(
                        onClick = {
                            currentDir.parentFile?.let {
                                currentDir = it
                                directPathInput = it.absolutePath
                            }
                        },
                        enabled = hasParent,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Up",
                            tint = if (hasParent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = currentDir.name.ifEmpty { currentDir.absolutePath },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = { isManualInput = !isManualInput },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Edit,
                            contentDescription = "Edit Path",
                            modifier = Modifier.size(18.dp),
                            tint = if (isManualInput) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Text(
                    text = currentDir.absolutePath,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (isManualInput) {
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = directPathInput,
                        onValueChange = {
                            directPathInput = it
                            val f = File(it.trim())
                            if (f.exists() && f.isDirectory) {
                                currentDir = f
                            }
                        },
                        label = { Text("Folder Path") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(4.dp))

                if (subfolders.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No subfolders inside",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                    ) {
                        items(subfolders) { folder ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable {
                                        currentDir = folder
                                        directPathInput = folder.absolutePath
                                    }
                                    .padding(vertical = 8.dp, horizontal = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Folder,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = folder.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val target = if (isManualInput) {
                        val f = File(directPathInput.trim())
                        if (f.exists() && f.isDirectory) f else currentDir
                    } else currentDir
                    onFolderSelected(target)
                }
            ) {
                Text(stringResource(R.string.select_this_folder))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
private fun CustomSizeLimitDialog(
    currentSizeBytes: Long,
    onSizeSelected: (Long, String) -> Unit,
    onDismiss: () -> Unit
) {
    var sizeInput by remember { mutableStateOf("10") }
    var selectedUnit by remember { mutableStateOf("MB") }
    val units = listOf("KB", "MB", "GB")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.custom_size_limit)) },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.enter_size_limit),
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = sizeInput,
                        onValueChange = { sizeInput = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Size") },
                        singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                        ),
                        modifier = Modifier.weight(1f)
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        units.forEach { unit ->
                            FilterChip(
                                selected = selectedUnit == unit,
                                onClick = { selectedUnit = unit },
                                label = { Text(unit, fontSize = 12.sp) }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val num = sizeInput.toLongOrNull() ?: 0L
                    val multiplier = when (selectedUnit) {
                        "KB" -> 1024L
                        "MB" -> 1024L * 1024L
                        "GB" -> 1024L * 1024L * 1024L
                        else -> 1024L * 1024L
                    }
                    val totalBytes = num * multiplier
                    val label = "> $num $selectedUnit"
                    onSizeSelected(totalBytes, label)
                }
            ) {
                Text(stringResource(R.string.confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

private fun saveRecycleBinMetadata(recycleBinFolder: ContentHolder, files: List<ContentHolder>) {
    try {
        if (recycleBinFolder is LocalFileHolder) {
            val metadataFile = File(recycleBinFolder.file, "metadata.json")
            val jsonArray = JSONArray()
            files.forEach { file ->
                val entry = JSONObject().apply {
                    put("name", file.displayName)
                    put("originalPath", file.uniquePath)
                    put("isDirectory", file.isFolder)
                    put("deletedAt", System.currentTimeMillis())
                }
                jsonArray.put(entry)
            }
            val root = JSONObject().apply {
                put("items", jsonArray)
            }
            metadataFile.writeText(root.toString(2))
        }
    } catch (_: Exception) {}
}
