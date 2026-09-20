package com.techflyers.compose.file.explorer.screen.main.tab.files.ui

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.OverscrollConfiguration
import androidx.compose.foundation.LocalOverscrollConfiguration
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.SubdirectoryArrowLeft
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material.icons.automirrored.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Info
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Surface
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import com.techflyers.compose.file.explorer.common.toFormattedSize
import com.techflyers.compose.file.explorer.screen.main.tab.files.task.CopyTask
import com.techflyers.compose.file.explorer.screen.main.tab.files.task.CopyTaskParameters
import com.techflyers.compose.file.explorer.screen.main.tab.files.holder.ContentHolder
import com.techflyers.compose.file.explorer.screen.main.tab.files.holder.LocalFileHolder
import com.techflyers.compose.file.explorer.screen.main.tab.files.holder.VirtualFileHolder
import com.techflyers.compose.file.explorer.screen.main.tab.files.misc.SourceFolderInfo
import com.techflyers.compose.file.explorer.screen.main.tab.files.misc.SourceFolderResolver
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.withContext
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.techflyers.compose.file.explorer.App.Companion.globalClass
import com.techflyers.compose.file.explorer.R
import com.techflyers.compose.file.explorer.common.emptyString
import com.techflyers.compose.file.explorer.common.showMsg
import com.techflyers.compose.file.explorer.common.ui.Isolate
import com.techflyers.compose.file.explorer.common.ui.Space
import com.techflyers.compose.file.explorer.common.ui.fastScrollbar
import com.techflyers.compose.file.explorer.screen.main.tab.files.FilesTab
import com.techflyers.compose.file.explorer.screen.main.tab.files.coil.canUseCoil
import com.techflyers.compose.file.explorer.screen.main.tab.files.misc.FileMimeType.imageFileType
import com.techflyers.compose.file.explorer.screen.main.tab.files.misc.FileMimeType.videoFileType
import com.techflyers.compose.file.explorer.screen.main.tab.files.misc.ViewConfigs
import com.techflyers.compose.file.explorer.screen.main.tab.files.misc.ViewType
import com.techflyers.compose.file.explorer.screen.preferences.constant.FileItemSizeMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ColumnScope.FilesList(tab: FilesTab) {
    val preferencesManager = globalClass.preferencesManager
    val coroutineScope = rememberCoroutineScope()
    var isRefreshing by remember { mutableStateOf(false) }

    // Resolve parent folder once for the `..` entry
    var parentFolder by remember(tab.activeFolder.uniquePath) {
        mutableStateOf<ContentHolder?>(null)
    }
    LaunchedEffect(tab.activeFolder.uniquePath) {
        parentFolder = withContext(IO) {
            if (preferencesManager.showParentDirectoryEntry
                && tab.activeFolder !is VirtualFileHolder
            ) {
                val p = tab.activeFolder.getParent()
                if (p != null && p.isValid() && p.canRead) p else null
            } else null
        }
    }

    Box(Modifier.weight(1f)) {
        if (tab.activeFolderContent.isEmpty() && !tab.isLoading) {
            EmptyFolderContent(tab)
        }

        // Disable elastic overscroll bounce when spring effect disabled
        val overscrollConfig = if (preferencesManager.disableSpringEffect) null else OverscrollConfiguration()
        CompositionLocalProvider(LocalOverscrollConfiguration provides overscrollConfig) {
        if (preferencesManager.disablePullDownToRefresh) {
            FilesListContent(tab, parentFolder)
        } else if (preferencesManager.disableSpringEffect) {
            val noSpringState = rememberPullToRefreshState()
            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = {
                    isRefreshing = true
                    tab.openFolder(tab.activeFolder, true, true)
                    coroutineScope.launch {
                        delay(100)
                        isRefreshing = false
                    }
                },
                modifier = Modifier.fillMaxSize(),
                state = noSpringState,
                indicator = {
                    PullToRefreshDefaults.Indicator(
                        state = noSpringState,
                        isRefreshing = isRefreshing,
                        modifier = Modifier.align(Alignment.TopCenter)
                    )
                }
            ) {
                FilesListContent(tab, parentFolder)
            }
        } else {
            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = {
                    isRefreshing = true
                    tab.openFolder(tab.activeFolder, true, true)
                    coroutineScope.launch {
                        delay(100)
                        isRefreshing = false
                    }
                },
                modifier = Modifier.fillMaxSize(),
            ) {
                FilesListContent(tab, parentFolder)
            }
        }
        } // end CompositionLocalProvider

        LoadingOverlay(tab)

        val taskManager = globalClass.taskManager
        val version = taskManager.pendingTasksVersion
        val mostRecentCopyTask = remember(version) {
            taskManager.pendingTasks.filterIsInstance<CopyTask>().lastOrNull()
        }

        if (mostRecentCopyTask != null && tab.activeFolder.canAddNewContent && tab.activeFolder !is VirtualFileHolder) {
            var showCancelConfirmation by remember { mutableStateOf(false) }
            var showTaskInfoDialog by remember { mutableStateOf(false) }

            if (showCancelConfirmation) {
                androidx.compose.material3.AlertDialog(
                    onDismissRequest = { showCancelConfirmation = false },
                    title = {
                        Text(
                            if (mostRecentCopyTask.deleteSourceFiles)
                                stringResource(R.string.cancel_move_task)
                            else
                                stringResource(R.string.cancel_copy_task)
                        )
                    },
                    text = { Text(stringResource(R.string.cancel_task_confirmation)) },
                    confirmButton = {
                        androidx.compose.material3.TextButton(onClick = {
                            taskManager.removeTask(mostRecentCopyTask.id)
                            showCancelConfirmation = false
                        }) { Text(stringResource(R.string.cancel_task)) }
                    },
                    dismissButton = {
                        androidx.compose.material3.TextButton(onClick = { showCancelConfirmation = false }) {
                            Text(stringResource(R.string.keep))
                        }
                    }
                )
            }

            if (showTaskInfoDialog) {
                val isSingleFile = mostRecentCopyTask.sourceFiles.size == 1
                val firstFile = mostRecentCopyTask.sourceFiles.firstOrNull()
                var renameInput by remember(mostRecentCopyTask.id) {
                    mutableStateOf(
                        firstFile?.let {
                            mostRecentCopyTask.customTargetNames[it.uniquePath] ?: it.displayName
                        } ?: ""
                    )
                }
                var selectedFileForRename by remember { mutableStateOf<ContentHolder?>(null) }
                var multiRenameInput by remember { mutableStateOf("") }

                androidx.compose.material3.AlertDialog(
                    onDismissRequest = { showTaskInfoDialog = false },
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.task_details_title),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    },
                    text = {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = if (mostRecentCopyTask.deleteSourceFiles) stringResource(R.string.move) else stringResource(R.string.copy),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = " → ${tab.activeFolder.displayName}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            if (isSingleFile && firstFile != null) {
                                Text(
                                    text = stringResource(R.string.rename_destination_file),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium
                                )
                                OutlinedTextField(
                                    value = renameInput,
                                    onValueChange = { renameInput = it },
                                    label = { Text(stringResource(R.string.rename_file_hint)) },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Text(
                                    text = "Original: ${firstFile.displayName} (${firstFile.size.toFormattedSize()})",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )
                            } else {
                                Text(
                                    text = stringResource(R.string.pending_task_files, mostRecentCopyTask.sourceFiles.size),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                LazyColumn(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = 220.dp),
                                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(4.dp)
                                ) {
                                    items(mostRecentCopyTask.sourceFiles) { file ->
                                        val customName = mostRecentCopyTask.customTargetNames[file.uniquePath]
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(6.dp))
                                                .clickable {
                                                    selectedFileForRename = file
                                                    multiRenameInput = customName ?: file.displayName
                                                }
                                                .padding(6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = if (file.isFolder) Icons.Rounded.Folder else Icons.AutoMirrored.Rounded.InsertDriveFile,
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp),
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = customName ?: file.displayName,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    fontWeight = if (customName != null) FontWeight.Bold else FontWeight.Normal,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                if (customName != null) {
                                                    Text(
                                                        text = "Orig: ${file.displayName}",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }
                                            Icon(
                                                imageVector = Icons.Rounded.Edit,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp),
                                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                                            )
                                        }
                                    }
                                }

                                if (selectedFileForRename != null) {
                                    HorizontalDivider()
                                    OutlinedTextField(
                                        value = multiRenameInput,
                                        onValueChange = { multiRenameInput = it },
                                        label = { Text("Rename selected file") },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                                        androidx.compose.material3.TextButton(
                                            onClick = {
                                                selectedFileForRename?.let { f ->
                                                    mostRecentCopyTask.setCustomTargetName(f.uniquePath, multiRenameInput)
                                                }
                                                selectedFileForRename = null
                                            }
                                        ) {
                                            Text(stringResource(R.string.apply))
                                        }
                                    }
                                }
                            }
                        }
                    },
                    confirmButton = {
                        androidx.compose.material3.TextButton(
                            onClick = {
                                if (isSingleFile && firstFile != null && renameInput.isNotBlank()) {
                                    mostRecentCopyTask.setCustomTargetName(firstFile.uniquePath, renameInput)
                                }
                                showTaskInfoDialog = false
                            }
                        ) {
                            Text(stringResource(R.string.apply))
                        }
                    },
                    dismissButton = {
                        androidx.compose.material3.TextButton(onClick = { showTaskInfoDialog = false }) {
                            Text(stringResource(R.string.dismiss))
                        }
                    }
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                contentAlignment = Alignment.BottomEnd
            ) {
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Task filename pill with rename capability
                        val firstSourceFile = mostRecentCopyTask.sourceFiles.firstOrNull()
                        val rawPendingName = firstSourceFile?.let {
                            mostRecentCopyTask.customTargetNames[it.uniquePath] ?: it.displayName
                        } ?: ""
                        val taskFileNameDisplay = if (mostRecentCopyTask.sourceFiles.size > 1) {
                            "$rawPendingName (+${mostRecentCopyTask.sourceFiles.size - 1})"
                        } else {
                            rawPendingName
                        }

                        TooltipBox(
                            positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                            tooltip = {
                                PlainTooltip {
                                    Text(rawPendingName.ifEmpty { stringResource(R.string.task_info_rename) })
                                }
                            },
                            state = rememberTooltipState()
                        ) {
                            Surface(
                                onClick = { showTaskInfoDialog = true },
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                shadowElevation = 3.dp,
                                modifier = Modifier.height(40.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Edit,
                                        contentDescription = stringResource(R.string.task_info_rename),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = taskFileNameDisplay,
                                        style = MaterialTheme.typography.labelMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.widthIn(max = 140.dp)
                                    )
                                }
                            }
                        }

                        // Small cancel FAB
                        androidx.compose.material3.SmallFloatingActionButton(
                            onClick = { showCancelConfirmation = true },
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = stringResource(R.string.cancel_task),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    // Main Paste / Move Here FAB
                    ExtendedFloatingActionButton(
                        onClick = {
                            taskManager.runTask(
                                mostRecentCopyTask.id,
                                CopyTaskParameters(tab.activeFolder)
                            )
                        },
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        icon = {
                            Icon(
                                imageVector = Icons.Rounded.ContentPaste,
                                contentDescription = null
                            )
                        },
                        text = {
                            Text(
                                text = if (mostRecentCopyTask.deleteSourceFiles) {
                                    stringResource(R.string.move_here)
                                } else {
                                    stringResource(R.string.paste_here)
                                }
                            )
                        }
                    )
                }
            }
        }

    }
}

@Composable
private fun EmptyFolderContent(tab: FilesTab) {
    val preferencesManager = globalClass.preferencesManager

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (!tab.activeFolder.canRead) {
            Text(
                modifier = Modifier
                    .fillMaxWidth()
                    .alpha(0.4f),
                text = stringResource(R.string.cant_access_content),
                fontSize = 20.sp,
                textAlign = TextAlign.Center
            )
        } else {
            val hasHiddenItems = tab.foldersCount + tab.filesCount > 0
            if (!hasHiddenItems) {
                Icon(
                    imageVector = Icons.Rounded.Block,
                    contentDescription = null,
                    modifier = Modifier
                        .size(64.dp)
                        .alpha(0.35f),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else if (!preferencesManager.showHiddenFiles) {
                Icon(
                    imageVector = Icons.Rounded.VisibilityOff,
                    contentDescription = null,
                    modifier = Modifier
                        .size(48.dp)
                        .alpha(0.35f),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Space(12.dp)
                Text(
                    modifier = Modifier
                        .fillMaxWidth()
                        .alpha(0.4f),
                    text = stringResource(R.string.empty_without_hidden_files),
                    fontSize = 16.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun LoadingOverlay(tab: FilesTab) {
    AnimatedVisibility(
        modifier = Modifier.fillMaxSize(),
        visible = tab.isLoading
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(colorScheme.surface.copy(alpha = 0.4f))
                .clickable(
                    interactionSource = null,
                    indication = null,
                    onClick = { }
                ),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
    }
}

@Composable
private fun FilesListContent(tab: FilesTab, parentFolder: ContentHolder?) {
    when (tab.viewConfig.viewType) {
        ViewType.LIST -> FilesListColumns(tab, parentFolder)
        ViewType.GRID -> FilesListGrid(tab, parentFolder)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FilesListColumns(tab: FilesTab, parentFolder: ContentHolder?) {
    val context = LocalContext.current
    val selectionHighlightColor = colorScheme.surfaceContainerHigh.copy(alpha = 1f)
    val highlightColor = colorScheme.primary.copy(alpha = 0.05f)

    LazyVerticalGrid(
        modifier = Modifier
            .fillMaxSize()
            .fastScrollbar(tab.activeListState),
        state = tab.activeListState,
        columns = GridCells.Fixed(tab.viewConfig.columnCount),
    ) {
        // `..` parent directory entry
        if (parentFolder != null) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                ParentDirectoryItem(tab = tab, parentFolder = parentFolder)
            }
        }

        itemsIndexed(
            tab.activeFolderContent,
            key = { _, item -> item.uniquePath }
        ) { index, item ->
            val currentItemPath = item.uniquePath
            val isAlreadySelected = tab.selectedFiles.containsKey(currentItemPath)
            var isSelectedItem by remember(isAlreadySelected) { mutableStateOf(isAlreadySelected) }
            ColumnFileItem(
                item = item,
                index = index,
                tab = tab,
                selectionHighlightColor = selectionHighlightColor,
                highlightColor = highlightColor,
                context = context,
                viewConfigs = tab.viewConfig,
                isSelectedItem = isSelectedItem,
                onSelection = { isSelectedItem = it }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FilesListGrid(tab: FilesTab, parentFolder: ContentHolder?) {
    val context = LocalContext.current
    val selectionHighlightColor = colorScheme.surfaceContainerHigh.copy(alpha = 1f)
    val highlightColor = colorScheme.primary.copy(alpha = 0.05f)

    LazyVerticalGrid(
        state = tab.activeListState,
        columns = GridCells.Fixed(tab.viewConfig.columnCount),
        modifier = Modifier
            .fillMaxSize()
            .fastScrollbar(tab.activeListState)
    ) {
        // `..` parent directory entry (spans full width)
        if (parentFolder != null) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                ParentDirectoryItem(tab = tab, parentFolder = parentFolder)
            }
        }

        itemsIndexed(
            tab.activeFolderContent,
            key = { _, item -> item.uniquePath }
        ) { index, item ->
            val currentItemPath = item.uniquePath
            val isAlreadySelected = tab.selectedFiles.containsKey(currentItemPath)
            var isSelectedItem by remember(isAlreadySelected) { mutableStateOf(isAlreadySelected) }
            GridFileItem(
                itemPath = currentItemPath,
                isSelected = isSelectedItem,
                item = item,
                index = index,
                tab = tab,
                selectionHighlightColor = selectionHighlightColor,
                highlightColor = highlightColor,
                context = context,
                viewConfigs = tab.viewConfig,
                isSelectedItem = isSelectedItem,
                onSelection = { isSelectedItem = it }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ColumnFileItem(
    item: ContentHolder,
    index: Int,
    tab: FilesTab,
    selectionHighlightColor: Color,
    highlightColor: Color,
    context: Context,
    viewConfigs: ViewConfigs,
    isSelectedItem: Boolean,
    onSelection: (Boolean) -> Unit
) {
    val currentItemPath = item.uniquePath
    val isSelected = isSelectedItem || tab.selectedFiles.containsKey(currentItemPath)

    fun toggleSelection() {
        if (isSelected) {
            tab.selectedFiles.remove(currentItemPath)
            tab.lastSelectedFileIndex = -1
            onSelection(false)
        } else {
            tab.selectedFiles[currentItemPath] = item
            tab.lastSelectedFileIndex = index
            onSelection(true)
        }
        tab.selectedFilesCount = tab.selectedFiles.size
        tab.onSelectionChange()
    }

    val showSourceBadge = tab.activeFolder is VirtualFileHolder && globalClass.preferencesManager.showSourceBadges
    val sourceInfo = if (showSourceBadge) {
        remember(currentItemPath) {
            SourceFolderResolver.resolve(item)
        }
    } else null

    Column(
        Modifier
            .fillMaxWidth()
            .background(
                color = if (isSelected) {
                    selectionHighlightColor
                } else if (tab.highlightedFiles.contains(currentItemPath)) {
                    highlightColor
                } else {
                    Color.Unspecified
                }
            )
            .combinedClickable(
                onClick = {
                    if (tab.selectedFiles.isNotEmpty()) {
                        toggleSelection()
                    } else {
                        if (item.isSymbolicLink && item.isSymbolicLinkBroken) {
                            showMsg(globalClass.getString(R.string.symbolic_link_broken))
                        } else if (item.isFile()) {
                            tab.openFile(context, item)
                        } else {
                            tab.openFolder(item, false)
                        }
                    }
                },
                onLongClick = {
                    handleLongClick(tab, currentItemPath, item, index)
                }
            )
    ) {
        Space(size = FileItemSizeMap.getSpace(viewConfigs.itemSize).dp)

        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FileIcon(
                item = item,
                size = FileItemSizeMap.getIconSize(viewConfigs.itemSize).dp,
                viewConfigs = viewConfigs,
                onClick = { toggleSelection() },
                onLongClick = { handleLongClick(tab, currentItemPath, item, index) },
                sourceInfo = sourceInfo
            )

            Space(size = 8.dp)

            Column(Modifier.weight(1f)) {
                val fontSize = FileItemSizeMap.getFontSize(viewConfigs.itemSize)
                // Hide extension from display name if setting is enabled
                val prefs = globalClass.preferencesManager
                val displayText = if (prefs.hideFileExtensions && item.isFile() && item.extension.isNotEmpty()) {
                    item.displayName.substringBeforeLast(".")
                } else {
                    item.displayName
                }

                MiddleEllipsisText(
                    text = displayText,
                    isFolder = item.isFolder || (prefs.hideFileExtensions && item.isFile()),
                    isSelected = isSelected || tab.highlightedFiles.contains(currentItemPath),
                    fontSize = fontSize.sp,
                    lineHeight = (fontSize + 2).sp,
                    color = if (isSelected || tab.highlightedFiles.contains(currentItemPath)) {
                        colorScheme.primary
                    } else {
                        Color.Unspecified
                    }
                )

                FileDetails(
                    item = item,
                    currentItemPath = currentItemPath,
                    fontSize = fontSize,
                    isHighlighted = isSelected || tab.highlightedFiles.contains(currentItemPath),
                    sourceInfo = sourceInfo
                )
            }
        }

        Space(size = FileItemSizeMap.getSpace(viewConfigs.itemSize).dp)

        HorizontalDivider(
            modifier = Modifier.padding(start = 56.dp),
            thickness = 0.5.dp
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GridFileItem(
    itemPath: String,
    isSelected: Boolean,
    item: ContentHolder,
    index: Int,
    tab: FilesTab,
    selectionHighlightColor: Color,
    highlightColor: Color,
    context: Context,
    viewConfigs: ViewConfigs,
    isSelectedItem: Boolean,
    onSelection: (Boolean) -> Unit
) {
    fun toggleSelection() {
        if (isSelected) {
            tab.selectedFiles.remove(itemPath)
            tab.lastSelectedFileIndex = -1
            onSelection(false)
        } else {
            tab.selectedFiles[itemPath] = item
            tab.lastSelectedFileIndex = index
            onSelection(true)
        }
        tab.selectedFilesCount = tab.selectedFiles.size
        tab.onSelectionChange()
    }

    val showSourceBadge = tab.activeFolder is VirtualFileHolder && globalClass.preferencesManager.showSourceBadges
    val sourceInfo = if (showSourceBadge) {
        remember(itemPath) {
            SourceFolderResolver.resolve(item)
        }
    } else null

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (viewConfigs.galleryMode) Modifier.aspectRatio(1f) else Modifier)
            .combinedClickable(
                onClick = {
                    if (tab.selectedFiles.isNotEmpty()) {
                        toggleSelection()
                    } else {
                        if (item.isSymbolicLink && item.isSymbolicLinkBroken) {
                            showMsg(globalClass.getString(R.string.symbolic_link_broken))
                        } else if (item.isFile()) {
                            tab.openFile(context, item)
                        } else {
                            tab.openFolder(item, false)
                        }
                    }
                },
                onLongClick = {
                    handleLongClick(tab, itemPath, item, index)
                }
            )
            .background(
                color = if (isSelected) {
                    selectionHighlightColor
                } else if (tab.highlightedFiles.contains(itemPath)) {
                    highlightColor
                } else {
                    Color.Unspecified
                }
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(if (viewConfigs.galleryMode) 1.dp else 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = if (viewConfigs.galleryMode) Modifier
                    .fillMaxWidth()
                    .weight(1f)
                else Modifier
            ) {
                FileIcon(
                    item = item,
                    size = (FileItemSizeMap.getIconSize(viewConfigs.itemSize) * 1.5).dp,
                    onClick = {
                        if (tab.selectedFiles.isNotEmpty()) {
                            toggleSelection()
                        } else {
                            if (item.isSymbolicLink && item.isSymbolicLinkBroken) {
                                showMsg(globalClass.getString(R.string.symbolic_link_broken))
                            } else if (item.isFile()) {
                                tab.openFile(context, item)
                            } else {
                                tab.openFolder(item, false)
                            }
                        }
                    },
                    viewConfigs = viewConfigs,
                    onLongClick = {
                        handleLongClick(tab, itemPath, item, index)
                    },
                    sourceInfo = sourceInfo
                )
                if (isSelected) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(color = colorScheme.surface.copy(alpha = 0.5f))
                    ) {
                        Icon(
                            modifier = Modifier.align(Alignment.Center),
                            imageVector = Icons.Rounded.CheckCircle,
                            tint = colorScheme.primary,
                            contentDescription = null
                        )
                    }
                }
                if (viewConfigs.galleryMode && (item.isFolder || (item.isFile() && ((!videoFileType.contains(
                        item.extension
                    ) && !imageFileType.contains(item.extension)) || !viewConfigs.hideMediaNames)))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                            .background(
                                color = colorScheme.surface.copy(alpha = 0.6f)
                            )
                            .padding(4.dp)
                    ) {
                        val fontSize = FileItemSizeMap.getFontSize(viewConfigs.itemSize) * 0.8
                        MiddleEllipsisText(
                            text = item.displayName,
                            isFolder = item.isFolder,
                            isSelected = isSelected || tab.highlightedFiles.contains(itemPath),
                            fontSize = fontSize.sp,
                            fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
                            maxLines = 2,
                            lineHeight = (fontSize + 2).sp,
                            textAlign = TextAlign.Center,
                            color = if (tab.highlightedFiles.contains(itemPath)) {
                                colorScheme.primary
                            } else {
                                colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
            if (!viewConfigs.galleryMode) {
                Space(size = 4.dp)
                MiddleEllipsisText(
                    text = item.displayName,
                    isFolder = item.isFolder,
                    isSelected = isSelected || tab.highlightedFiles.contains(itemPath),
                    fontSize = (FileItemSizeMap.getFontSize(viewConfigs.itemSize) - 3).sp,
                    maxLines = 2,
                    textAlign = TextAlign.Center,
                    color = if (isSelected || tab.highlightedFiles.contains(itemPath)) {
                        colorScheme.primary
                    } else {
                        colorScheme.onSurface
                    }
                )
                Space(size = 2.dp)
                FileDetailsCompact(
                    item = item,
                    currentItemPath = itemPath,
                    isHighlighted = isSelected
                )
            }
        }
    }
}

@Composable
private fun FileIcon(
    item: ContentHolder,
    size: Dp,
    viewConfigs: ViewConfigs,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    sourceInfo: SourceFolderInfo? = null
) {
    val sizeModifier = if (viewConfigs.viewType == ViewType.GRID && viewConfigs.galleryMode) {
        Modifier.fillMaxSize()
    } else {
        Modifier.size(size)
    }

    Isolate {
        Box(
            modifier = Modifier
                .then(sizeModifier)
                .clip(RoundedCornerShape(4.dp))
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick
                )
                .graphicsLayer { alpha = if (item.isHidden()) 0.4f else 1f },
        ) {
            var useCoil by remember(item.uniquePath) {
                mutableStateOf(canUseCoil(item))
            }

            if (useCoil) {
                AsyncImage(
                    modifier = sizeModifier,
                    model = ImageRequest
                        .Builder(globalClass)
                        .data(item)
                        .build(),
                    filterQuality = FilterQuality.Low,
                    contentScale = if (viewConfigs.cropThumbnails) ContentScale.Crop else ContentScale.Fit,
                    contentDescription = null,
                    onError = { useCoil = false }
                )
            } else {
                FileContentIcon(item)
            }

            if (sourceInfo != null) {
                val isGallery = viewConfigs.viewType == ViewType.GRID && viewConfigs.galleryMode
                val badgeSize = if (isGallery) 22.dp else (size.value * 0.42f).coerceIn(14f, 22f).dp
                val badgeIconSize = if (isGallery) 15.dp else (badgeSize.value * 0.72f).dp
                SourceFolderBadge(
                    sourceInfo = sourceInfo,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(if (isGallery) 4.dp else 2.dp),
                    badgeSize = badgeSize,
                    iconSize = badgeIconSize
                )
            }

            if (item.isSymbolicLink) {
                val isGallery = viewConfigs.viewType == ViewType.GRID && viewConfigs.galleryMode
                val badgeSize = if (isGallery) 22.dp else (size.value * 0.42f).coerceIn(14f, 22f).dp
                val badgeIconSize = if (isGallery) 15.dp else (badgeSize.value * 0.72f).dp
                SymbolicLinkBadge(
                    isBroken = item.isSymbolicLinkBroken,
                    modifier = Modifier
                        .align(if (sourceInfo != null) Alignment.BottomStart else Alignment.BottomEnd)
                        .padding(if (isGallery) 4.dp else 2.dp),
                    badgeSize = badgeSize,
                    iconSize = badgeIconSize
                )
            }

            if (!item.canRead) {
                Icon(
                    modifier = Modifier
                        .size(14.dp)
                        .align(if (sourceInfo != null) Alignment.TopEnd else Alignment.BottomEnd)
                        .alpha(if (item.isHidden()) 0.4f else 1f),
                    imageVector = Icons.Rounded.Lock,
                    tint = Color.Red,
                    contentDescription = null
                )
            }
        }
    }
}

@Composable
private fun FileDetails(
    item: ContentHolder,
    currentItemPath: String,
    fontSize: Int,
    isHighlighted: Boolean,
    sourceInfo: SourceFolderInfo? = null
) {
    val detailsKey = "${item.uniquePath}_${item.lastModified}_${item.size}"
    val initialDetails = remember(detailsKey) {
        if (item is LocalFileHolder && item.details.isNotEmpty()) {
            item.details
        } else if (item is LocalFileHolder) {
            LocalFileHolder.getCachedDetails(item.file, item.lastModified).orEmpty()
        } else {
            emptyString
        }
    }

    var details by remember(detailsKey) { mutableStateOf(initialDetails) }

    LaunchedEffect(detailsKey) {
        if (details.isEmpty()) {
            val det = withContext(IO) {
                item.getDetails()
            }
            details = det
        }
    }

    // Parse dual-aligned format: "leftInfo\trightDate"
    val parts = details.split("\t", limit = 2)
    val rawLeftText = parts.getOrNull(0)?.trim().orEmpty()
    val leftText = if (sourceInfo != null && rawLeftText.isNotEmpty()) {
        "${sourceInfo.folderName} • $rawLeftText"
    } else if (sourceInfo != null) {
        sourceInfo.folderName
    } else {
        rawLeftText
    }
    val rightText = parts.getOrNull(1)?.trim().orEmpty()
    val smallFontSize = (fontSize - 4).sp
    val textColor = if (isHighlighted) colorScheme.primary else Color.Unspecified

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(0.7f),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        if (leftText.isNotEmpty()) {
            FileDetailsText(
                details = leftText,
                fontSize = smallFontSize,
                color = textColor,
                modifier = Modifier.weight(1f, fill = false)
            )
        }
        if (rightText.isNotEmpty()) {
            Text(
                text = rightText,
                fontSize = smallFontSize,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = textColor,
                textAlign = TextAlign.End
            )
        }
    }
}

private val folderCountPattern = Regex("(\\d+)\\s+folders?")
private val fileCountPattern = Regex("(\\d+)\\s+files?")
private val nullPattern = Regex("Ø")
private val nullNoStrikePattern = Regex("○")

@Composable
private fun FileDetailsText(
    details: String,
    fontSize: androidx.compose.ui.unit.TextUnit,
    modifier: Modifier = Modifier,
    maxLines: Int = 1,
    textAlign: TextAlign? = null,
    color: Color = Color.Unspecified
) {
    val matches = (folderCountPattern.findAll(details).map { it to "folder" } +
        fileCountPattern.findAll(details).map { it to "file" } +
        nullPattern.findAll(details).map { it to "null" } +
        nullNoStrikePattern.findAll(details).map { it to "null_no_strike" })
        .sortedBy { it.first.range.first }
        .toList()

    if (matches.isEmpty()) {
        Text(
            modifier = modifier,
            text = details,
            fontSize = fontSize,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
            textAlign = textAlign,
            color = color
        )
        return
    }

    val inlineContent = mapOf(
        "folder-count-icon" to InlineTextContent(
            placeholder = androidx.compose.ui.text.Placeholder(14.sp, 14.sp, androidx.compose.ui.text.PlaceholderVerticalAlign.TextCenter)
        ) {
            Icon(Icons.Rounded.Folder, contentDescription = "Folders", modifier = Modifier.size(14.dp))
        },
        "file-count-icon" to InlineTextContent(
            placeholder = androidx.compose.ui.text.Placeholder(14.sp, 14.sp, androidx.compose.ui.text.PlaceholderVerticalAlign.TextCenter)
        ) {
            Icon(Icons.Rounded.InsertDriveFile, contentDescription = "Files", modifier = Modifier.size(14.dp))
        },
        "null-count-icon" to InlineTextContent(
            placeholder = androidx.compose.ui.text.Placeholder(11.sp, 11.sp, androidx.compose.ui.text.PlaceholderVerticalAlign.TextCenter)
        ) {
            Icon(
                imageVector = Icons.Rounded.Block,
                contentDescription = "Empty",
                modifier = Modifier.size(11.dp),
                tint = color.takeIf { it != Color.Unspecified } ?: MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        },
        "null-no-strike-icon" to InlineTextContent(
            placeholder = androidx.compose.ui.text.Placeholder(11.sp, 11.sp, androidx.compose.ui.text.PlaceholderVerticalAlign.TextCenter)
        ) {
            Icon(
                imageVector = Icons.Rounded.RadioButtonUnchecked,
                contentDescription = "Empty within",
                modifier = Modifier.size(11.dp),
                tint = color.takeIf { it != Color.Unspecified } ?: MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        }
    )
    val annotated = androidx.compose.ui.text.buildAnnotatedString {
        var cursor = 0
        matches.forEach { (match, type) ->
            append(details.substring(cursor, match.range.first))
            when (type) {
                "null" -> appendInlineContent("null-count-icon", "[empty]")
                "null_no_strike" -> appendInlineContent("null-no-strike-icon", "[empty within]")
                else -> {
                    appendInlineContent("$type-count-icon", "[$type icon]")
                    append(" ${match.groupValues[1]}")
                }
            }
            cursor = match.range.last + 1
        }
        append(details.substring(cursor))
    }

    Text(
        modifier = modifier,
        text = annotated,
        inlineContent = inlineContent,
        fontSize = fontSize,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        textAlign = textAlign,
        color = color
    )
}

// `..` parent directory navigation row
@Composable
fun ParentDirectoryItem(tab: FilesTab, parentFolder: ContentHolder) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { tab.openFolder(parentFolder, false) }
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .alpha(0.55f),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Rounded.SubdirectoryArrowLeft,
            contentDescription = null,
            modifier = Modifier.size(22.dp)
        )
        Space(size = 8.dp)
        Text(
            text = "..",
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )
    }
    HorizontalDivider(thickness = 0.5.dp)
}

@Composable
private fun FileDetailsCompact(
    item: ContentHolder,
    currentItemPath: String,
    isHighlighted: Boolean
) {
    Isolate {
        val detailsKey = "${item.uniquePath}_${item.lastModified}_${item.size}"
        val initialDetails = remember(detailsKey) {
            if (item is LocalFileHolder && item.details.isNotEmpty()) {
                item.details
            } else if (item is LocalFileHolder) {
                LocalFileHolder.getCachedDetails(item.file, item.lastModified).orEmpty()
            } else {
                emptyString
            }
        }

        var details by remember(detailsKey) { mutableStateOf(initialDetails) }

        LaunchedEffect(detailsKey) {
            if (details.isEmpty()) {
                val det = withContext(IO) {
                    item.getDetails()
                }
                details = det
            }
        }

                    FileDetailsText(
                modifier = Modifier.alpha(0.6f),
                details = details,
                fontSize = 10.sp,
                maxLines = 1,
                textAlign = TextAlign.Center,
                color = if (isHighlighted) {
                    colorScheme.primary
                } else {
                    colorScheme.onSurfaceVariant
                }
            )

    }
}

private fun handleLongClick(
    tab: FilesTab,
    currentItemPath: String,
    item: ContentHolder,
    index: Int
) {
    val preferencesManager = globalClass.preferencesManager
    val isFirstSelection = tab.selectedFiles.isEmpty()
    val isAlreadySelected = tab.selectedFiles.containsKey(currentItemPath)
    val isNewSelection = !isAlreadySelected

    tab.selectedFiles[currentItemPath] = item

    if ((isFirstSelection && preferencesManager.showFileOptionMenuOnLongClick)
        || !isNewSelection
    ) {
        tab.toggleFileOptionsMenu(item)
    }

    if (isNewSelection) {
        if (tab.lastSelectedFileIndex >= 0) {
            if (tab.lastSelectedFileIndex > index) {
                for (i in tab.lastSelectedFileIndex downTo index) {
                    tab.selectedFiles[tab.activeFolderContent[i].uniquePath] =
                        tab.activeFolderContent[i]
                }
            } else {
                for (i in tab.lastSelectedFileIndex..index) {
                    tab.selectedFiles[tab.activeFolderContent[i].uniquePath] =
                        tab.activeFolderContent[i]
                }
            }
        }
    }
    tab.lastSelectedFileIndex = index
    tab.selectedFilesCount = tab.selectedFiles.size
    tab.quickReloadFiles()
}