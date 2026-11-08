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
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.DisposableEffect
import kotlinx.coroutines.delay
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerEventTimeoutCancellationException
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.geometry.Offset
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withTimeout
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
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.rounded.Info
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.text.font.FontStyle
import com.techflyers.compose.file.explorer.screen.main.tab.files.service.FolderLockStore
import com.techflyers.compose.file.explorer.screen.main.tab.files.task.DeleteTimestampJournal
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Surface
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import com.techflyers.compose.file.explorer.common.toFormattedDate
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
import androidx.compose.runtime.mutableStateMapOf
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
import androidx.compose.ui.layout.onGloballyPositioned
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

    LaunchedEffect(preferencesManager.showDirectoryTypeFilterRibbon, tab.activeFolder.uniquePath) {
        if (!preferencesManager.showDirectoryTypeFilterRibbon && tab.selectedTypeFilters.isNotEmpty()) {
            tab.selectedTypeFilters = emptySet()
            tab.updateContentSnapshot()
        }
    }

    val isLandscape = androidx.compose.ui.platform.LocalConfiguration.current.orientation ==
            android.content.res.Configuration.ORIENTATION_LANDSCAPE

    // Use pre-resolved parent folder on tab so `..` entry and scroll state are available synchronously on the very first frame
    val hideParentBecauseLandscapeBreadcrumb = isLandscape && preferencesManager.showPathBar
    val parentFolder: ContentHolder? = if (preferencesManager.showParentDirectoryEntry
        && !hideParentBecauseLandscapeBreadcrumb
        && tab.activeFolder !is VirtualFileHolder
    ) {
        tab.activeParentFolder
    } else null
    val parentFolderResolved = true

    val areaSwipeDensity = LocalDensity.current
    val areaSwipeThresholdPx = with(areaSwipeDensity) { 56.dp.toPx() }
    val areaSwipeTouchSlop = LocalViewConfiguration.current.touchSlop
    val middleZonePercent = preferencesManager.middleSwipeZoneWidthPercent

    val subCategoryOffsetAnim = remember { androidx.compose.animation.core.Animatable(0f) }
    val subCategoryAlphaAnim = remember { androidx.compose.animation.core.Animatable(1f) }
    var isDraggingSubCategory by remember { mutableStateOf(false) }

    fun applySubCategoryTension(rawAbs: Float, limit: Float): Float {
        return if (rawAbs < limit) {
            rawAbs * 0.55f
        } else {
            val excess = rawAbs - limit
            val decay = kotlin.math.exp(-excess / limit * 1.8f)
            (limit * 0.55f) + (excess * 0.28f * decay)
        }
    }

    // Trigger quick sweeping slide animation when sub-category is switched via CategoriesRow tap/swipe
    LaunchedEffect(tab.subCategoryTransitionVersion) {
        if (tab.subCategoryTransitionVersion > 0 && !isDraggingSubCategory) {
            val dir = tab.subCategoryTransitionDirection
            val slideDist = with(areaSwipeDensity) { 72.dp.toPx() }
            subCategoryOffsetAnim.snapTo(if (dir >= 0) slideDist else -slideDist)
            subCategoryAlphaAnim.snapTo(0.65f)
            launch {
                subCategoryAlphaAnim.animateTo(
                    targetValue = 1f,
                    animationSpec = androidx.compose.animation.core.tween(180)
                )
            }
            subCategoryOffsetAnim.animateTo(
                targetValue = 0f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            )
        }
    }

    Box(
        Modifier
            .weight(1f)
            .graphicsLayer {
                translationX = subCategoryOffsetAnim.value
                alpha = subCategoryAlphaAnim.value
            }
            .then(
                if (!preferencesManager.disableNavigationGestures && tab.showCategories && tab.categories.isNotEmpty()) {
                    Modifier.pointerInput(
                        tab.showCategories,
                        tab.categories.size,
                        tab.selectedCategory,
                        middleZonePercent
                    ) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                            val middleFraction = (middleZonePercent.coerceIn(20, 70)) / 100f
                            val leftBoundary = size.width * (1f - middleFraction) / 2f
                            val rightBoundary = size.width * (1f + middleFraction) / 2f
                            if (down.position.x !in leftBoundary..rightBoundary) return@awaitEachGesture

                            var totalDx = 0f
                            var totalDy = 0f
                            var lockedHorizontal = false
                            val currentIdx = tab.selectedCategory?.let { tab.categories.indexOf(it) + 1 } ?: 0
                            val maxIdx = tab.categories.size

                            do {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                val change = event.changes.find { it.id == down.id } ?: break
                                val dx = change.position.x - change.previousPosition.x
                                val dy = change.position.y - change.previousPosition.y
                                totalDx += dx
                                totalDy += dy

                                if (!lockedHorizontal) {
                                    if (kotlin.math.abs(totalDy) > areaSwipeTouchSlop && kotlin.math.abs(totalDy) > kotlin.math.abs(totalDx)) {
                                        return@awaitEachGesture
                                    }
                                    if (kotlin.math.abs(totalDx) > areaSwipeTouchSlop && kotlin.math.abs(totalDx) > kotlin.math.abs(totalDy) * 1.2f) {
                                        lockedHorizontal = true
                                        isDraggingSubCategory = true
                                    }
                                }

                                if (lockedHorizontal) {
                                    change.consume()
                                    val atLowerBound = currentIdx == 0 && totalDx > 0f
                                    val atUpperBound = currentIdx == maxIdx && totalDx < 0f
                                    val visualOffset = if (atLowerBound || atUpperBound) {
                                        val sign = if (totalDx >= 0f) 1f else -1f
                                        sign * applySubCategoryTension(kotlin.math.abs(totalDx), areaSwipeThresholdPx * 1.2f)
                                    } else {
                                        (totalDx * 0.65f).coerceIn(-size.width * 0.45f, size.width * 0.45f)
                                    }
                                    coroutineScope.launch {
                                        subCategoryOffsetAnim.snapTo(visualOffset)
                                    }
                                }
                            } while (event.changes.any { it.pressed })

                            if (lockedHorizontal) {
                                val atLowerBound = currentIdx == 0 && totalDx > 0f
                                val atUpperBound = currentIdx == maxIdx && totalDx < 0f
                                if (!atLowerBound && !atUpperBound && kotlin.math.abs(totalDx) >= areaSwipeThresholdPx) {
                                    val nextIdx = if (totalDx < 0f) {
                                        (currentIdx + 1).coerceAtMost(maxIdx)
                                    } else {
                                        (currentIdx - 1).coerceAtLeast(0)
                                    }
                                    if (nextIdx != currentIdx) {
                                        val dir = if (nextIdx > currentIdx) 1 else -1
                                        tab.selectedCategory = if (nextIdx == 0) null else tab.categories.getOrNull(nextIdx - 1)
                                        tab.reloadFiles()
                                        val incomingOffset = if (dir > 0) size.width * 0.28f else -size.width * 0.28f
                                        coroutineScope.launch {
                                            subCategoryOffsetAnim.snapTo(incomingOffset)
                                            subCategoryAlphaAnim.snapTo(0.65f)
                                            launch {
                                                subCategoryAlphaAnim.animateTo(1f, androidx.compose.animation.core.tween(180))
                                            }
                                            subCategoryOffsetAnim.animateTo(
                                                targetValue = 0f,
                                                animationSpec = spring(
                                                    dampingRatio = Spring.DampingRatioLowBouncy,
                                                    stiffness = Spring.StiffnessMediumLow
                                                )
                                            )
                                            isDraggingSubCategory = false
                                        }
                                    } else {
                                        coroutineScope.launch {
                                            subCategoryOffsetAnim.animateTo(
                                                targetValue = 0f,
                                                animationSpec = spring(
                                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                                    stiffness = Spring.StiffnessMedium
                                                )
                                            )
                                            isDraggingSubCategory = false
                                        }
                                    }
                                } else {
                                    // Bouncy snap-back when bounds are reached or swipe is below threshold
                                    coroutineScope.launch {
                                        subCategoryOffsetAnim.animateTo(
                                            targetValue = 0f,
                                            animationSpec = spring(
                                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                                stiffness = Spring.StiffnessMedium
                                            )
                                        )
                                        isDraggingSubCategory = false
                                    }
                                }
                            }
                        }
                    }
                } else Modifier
            )
    ) {
        if (tab.activeFolderContent.isEmpty() && !tab.isLoading && !tab.isCategoryLoading) {
            EmptyFolderContent(tab)
        }

        // Disable elastic overscroll bounce when spring effect disabled
        val overscrollConfig = if (preferencesManager.disableSpringEffect) null else OverscrollConfiguration()
        CompositionLocalProvider(LocalOverscrollConfiguration provides overscrollConfig) {
        if (preferencesManager.disablePullDownToRefresh) {
            FilesListContent(tab, parentFolder, parentFolderResolved)
        } else if (preferencesManager.disableSpringEffect) {
            val noSpringState = rememberPullToRefreshState()
            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = {
                    isRefreshing = true
                    tab.invalidateFolderListingCache(tab.activeFolder.uniquePath)
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
                FilesListContent(tab, parentFolder, parentFolderResolved)
            }
        } else {
            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = {
                    isRefreshing = true
                    tab.invalidateFolderListingCache(tab.activeFolder.uniquePath)
                    tab.openFolder(tab.activeFolder, true, true)
                    coroutineScope.launch {
                        delay(100)
                        isRefreshing = false
                    }
                },
                modifier = Modifier.fillMaxSize(),
            ) {
                FilesListContent(tab, parentFolder, parentFolderResolved)
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

        val copyHereTab = if (tab.copyHereTargetFolder != null) {
            tab
        } else {
            globalClass.mainActivityManager.state.value.tabs
                .filterIsInstance<FilesTab>()
                .firstOrNull { it.copyHereTargetFolder != null }
        }
        val copyHereTarget = copyHereTab?.copyHereTargetFolder
        if (copyHereTarget != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                contentAlignment = Alignment.BottomEnd
            ) {
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (tab.selectedFiles.isNotEmpty() && copyHereTarget.uniquePath != tab.activeFolder.uniquePath) {
                        ExtendedFloatingActionButton(
                            onClick = {
                                tab.requestCopyHereTransfer(
                                    items = tab.selectedFiles.values.toList(),
                                    targetFolder = copyHereTarget,
                                    deleteSourceFiles = null
                                )
                            },
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            icon = {
                                Icon(
                                    imageVector = Icons.Rounded.ContentPaste,
                                    contentDescription = null
                                )
                            },
                            text = {
                                Text("Copy to ${copyHereTarget.displayName}")
                            }
                        )
                    }
                    ExtendedFloatingActionButton(
                        onClick = {
                            copyHereTab.exitCopyHereMode()
                        },
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        icon = {
                            Icon(
                                imageVector = Icons.Rounded.CheckCircle,
                                contentDescription = null
                            )
                        },
                        text = {
                            Text("Done")
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
    val isVirtual = tab.activeFolder is VirtualFileHolder
    if (isVirtual) {
        // Non-blocking fluid percentage progress indicator for Categories and Recent Files
        // Never intercepts touches so UI navigation, swipes, and item clicks remain 100% responsive.
        val isIndexing = (tab.activeFolder as? VirtualFileHolder)?.let {
            it.type == VirtualFileHolder.RECENT || it.type == VirtualFileHolder.ALL_FILES
        } == true && com.techflyers.compose.file.explorer.screen.main.tab.files.provider.StorageProvider.isIndexingRecentFiles

        val effectiveProgress = if (isIndexing) {
            maxOf(
                tab.categoryLoadingProgress,
                com.techflyers.compose.file.explorer.screen.main.tab.files.provider.StorageProvider.indexingProgress
            )
        } else {
            tab.categoryLoadingProgress
        }

        if (tab.activeFolderContent.isEmpty() && (tab.isCategoryLoading || tab.isLoading)) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                com.techflyers.compose.file.explorer.common.ui.FluidPercentProgressCircle(
                    progress = effectiveProgress,
                    size = 64.dp,
                    strokeWidth = 5.dp,
                    label = "Loading ${tab.activeFolder.displayName}…"
                )
            }
        } else {
            AnimatedVisibility(
                visible = tab.isCategoryLoading || isIndexing,
                modifier = Modifier.fillMaxSize()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 10.dp, end = 14.dp),
                    contentAlignment = Alignment.TopEnd
                ) {
                    com.techflyers.compose.file.explorer.common.ui.FluidPercentProgressPill(
                        progress = effectiveProgress,
                        label = "Loading"
                    )
                }
            }
        }
        return
    }

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
private fun FilesListContent(
    tab: FilesTab,
    parentFolder: ContentHolder?,
    parentFolderResolved: Boolean
) {
    when (tab.viewConfig.viewType) {
        ViewType.LIST -> FilesListColumns(tab, parentFolder, parentFolderResolved)
        ViewType.GRID -> FilesListGrid(tab, parentFolder, parentFolderResolved)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FilesListColumns(
    tab: FilesTab,
    parentFolder: ContentHolder?,
    parentFolderResolved: Boolean
) {
    val context = LocalContext.current
    val selectionHighlightColor = colorScheme.surfaceContainerHigh.copy(alpha = 1f)
    val highlightColor = colorScheme.primary.copy(alpha = 0.05f)
    val cols = maxOf(1, tab.viewConfig.columnCount)
    // Take an immutable snapshot of the list ONCE so that all slices and the
    // maxSiblingSize calculation operate on the same stable copy.  Using subList()
    // on a live SnapshotStateList returns a structural view; if the IO thread
    // clears/reloads the list while Compose iterates it, the iterator throws
    // ConcurrentModificationException — the root cause of the repeated crashes.
    val snapshot: List<ContentHolder> = tab.contentSnapshot
    val totalItems = snapshot.size
    val chunkSize = if (totalItems == 0) 1 else kotlin.math.ceil(totalItems.toDouble() / cols).toInt()
    val columnSlices = (0 until cols).map { colIndex ->
        val start = colIndex * chunkSize
        val end = minOf(start + chunkSize, totalItems)
        if (start < totalItems) snapshot.subList(start, end) else emptyList()
    }

    // --- Folder size scanning for size-relative tint (optional, default Disabled) ---
    val sizeTintEnabled = globalClass.preferencesManager.sizeTintEnabled
    val folderScanEnabled = sizeTintEnabled && globalClass.preferencesManager.folderScanForTint
    // Keyed by uniquePath -> scanned recursive size. Populated lazily on IO as each
    // folder's walk completes; writing into a SnapshotStateMap triggers recomposition.
    val folderScanSizes = remember(snapshot) { mutableStateMapOf<String, Long>() }
    if (folderScanEnabled && snapshot.size <= 200) {
        LaunchedEffect(snapshot) {
            folderScanSizes.clear()
            snapshot.take(50).forEach { item ->
                if (item.isFolder) {
                    launch(IO) {
                        val scanned = when (item) {
                            is LocalFileHolder -> {
                                var total = 0L
                                // Guard: walkTopDown throws AssertionError (not Exception) if the
                                // root is not a real directory — common for root mount points like
                                // /proc, /sys, or symlinks that report isFolder=true but aren't
                                // actual directories on the filesystem.
                                if (item.file.isDirectory) {
                                    try {
                                        item.file.walkTopDown().maxDepth(10).onFail { _, _ -> }.forEach { f ->
                                            if (f.isFile) total += f.length()
                                        }
                                    } catch (_: Throwable) {} // AssertionError extends Error, not Exception
                                }
                                total
                            }
                            else -> {
                                var total = 0L
                                try {
                                    suspend fun walk(holder: ContentHolder) {
                                        holder.listContent().forEach { child ->
                                            if (child.isFolder) walk(child) else total += child.size
                                        }
                                    }
                                    walk(item)
                                } catch (_: Throwable) {}
                                total
                            }
                        }
                        folderScanSizes[item.uniquePath] = scanned
                    }
                }
            }
        }
    }

    // Effective size for each item: scanned recursive size for folders (when enabled
    // and already computed), otherwise the raw item.size (0 for folders, file length for files).
    fun effectiveSizeOf(item: ContentHolder): Long =
        if (folderScanEnabled && item.isFolder) folderScanSizes[item.uniquePath] ?: 0L
        else item.size

    val maxSiblingSize = if (folderScanEnabled) (snapshot.maxOfOrNull { effectiveSizeOf(it) }?.coerceAtLeast(1L) ?: tab.maxSiblingSize) else tab.maxSiblingSize
    val primaryColumnState = tab.activeColumnListState
    val listStates = remember(tab.activeFolder.uniquePath, cols, primaryColumnState) {
        List(cols) { colIndex ->
            if (colIndex == 0) primaryColumnState else LazyListState()
        }
    }
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    val viewConfiguration = LocalViewConfiguration.current

    val density = LocalDensity.current
    var autoScrollJob by remember { mutableStateOf<Job?>(null) }

    val taskManager = globalClass.taskManager
    val hasPendingCopyTask = remember(taskManager.pendingTasksVersion, tab.activeFolder.uniquePath) {
        taskManager.pendingTasks.filterIsInstance<CopyTask>().isNotEmpty() &&
                tab.activeFolder.canAddNewContent &&
                tab.activeFolder !is VirtualFileHolder
    }
    val hasCopyHereFab = tab.copyHereTargetFolder != null ||
            globalClass.mainActivityManager.state.value.tabs.any { (it as? FilesTab)?.copyHereTargetFolder != null }
    val bottomContentPadding = if (hasPendingCopyTask || hasCopyHereFab) 88.dp else 0.dp

    val currentRequestScrollAboveRibbon by androidx.compose.runtime.rememberUpdatedState<(Int) -> Unit> { itemIndex ->
        if (!globalClass.preferencesManager.disableAutoScrollOnBottomSelection && itemIndex in 0 until totalItems) {
            val colIndex = (itemIndex / chunkSize).coerceIn(0, listStates.lastIndex)
            val sliceIndex = itemIndex % chunkSize
            val slice = columnSlices.getOrNull(colIndex) ?: return@rememberUpdatedState
            val listState = listStates[colIndex]

            val beforeLayout = listState.layoutInfo
            val beforeViewportEnd = beforeLayout.viewportEndOffset
            val beforeItem = beforeLayout.visibleItemsInfo.find { it.index == sliceIndex }
            val beforeItemBottom = beforeItem?.let { it.offset + it.size }
            val beforeItemSize = beforeItem?.size

            autoScrollJob?.cancel()
            autoScrollJob = coroutineScope.launch {
                // Wait for selection state and BottomOptionsBar layout measurement to settle
                androidx.compose.runtime.withFrameNanos { }
                androidx.compose.runtime.withFrameNanos { }

                val afterLayout = listState.layoutInfo
                val afterViewportEnd = afterLayout.viewportEndOffset
                if (afterViewportEnd <= 0) return@launch

                val afterItem = afterLayout.visibleItemsInfo.find { it.index == sliceIndex }
                val lastVisible = afterLayout.visibleItemsInfo.lastOrNull()

                val itemSize: Int
                val itemBottom: Int
                when {
                    afterItem != null -> {
                        itemSize = afterItem.size
                        itemBottom = afterItem.offset + afterItem.size
                    }
                    beforeItemBottom != null && afterViewportEnd < beforeViewportEnd -> {
                        itemSize = beforeItemSize ?: 0
                        itemBottom = beforeItemBottom
                    }
                    lastVisible != null && sliceIndex > lastVisible.index -> {
                        itemSize = lastVisible.size
                        itemBottom = lastVisible.offset + lastVisible.size +
                                (sliceIndex - lastVisible.index) * lastVisible.size
                    }
                    else -> return@launch
                }

                val hasMoreRowsBelow = sliceIndex < slice.lastIndex
                val scrollDelta = if (!hasMoreRowsBelow) {
                    (itemBottom - afterViewportEnd).toFloat()
                } else {
                    val triggerZonePx = with(density) { 12.dp.toPx() }
                    val peekPx = minOf(itemSize * 0.35f, with(density) { 24.dp.toPx() })
                    if (itemBottom > afterViewportEnd - triggerZonePx) {
                        itemBottom - (afterViewportEnd - peekPx)
                    } else {
                        0f
                    }
                }

                if (scrollDelta > 1f) {
                    try {
                        listState.animateScrollBy(
                            value = scrollDelta,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioNoBouncy,
                                stiffness = Spring.StiffnessMedium
                            )
                        )
                    } catch (_: Exception) {}
                }
            }
        }
    }

    DisposableEffect(tab) {
        tab.onRequestScrollAboveRibbon = { index -> currentRequestScrollAboveRibbon(index) }
        tab.onCancelAutoScroll = { autoScrollJob?.cancel() }
        onDispose {
            autoScrollJob?.cancel()
            tab.onRequestScrollAboveRibbon = null
            tab.onCancelAutoScroll = null
        }
    }

    fun findColumnItemIndex(offset: Offset, viewportWidth: Float, clampToVisible: Boolean = false): Int? {
        if (snapshot.isEmpty()) return null
        val x = offset.x
        val rawY = offset.y.toInt()
        val colWidth = if (cols > 0) viewportWidth / cols else viewportWidth
        val colIndex = (x / colWidth).toInt().coerceIn(0, cols - 1)
        val listState = listStates.getOrNull(colIndex) ?: return null
        val slice = columnSlices.getOrNull(colIndex) ?: return null
        if (slice.isEmpty()) return null

        val visibleItems = listState.layoutInfo.visibleItemsInfo
        if (visibleItems.isEmpty()) return null

        val y = if (clampToVisible) {
            val minY = visibleItems.first().offset
            val maxY = (visibleItems.last().offset + visibleItems.last().size - 1).coerceAtLeast(minY)
            rawY.coerceIn(minY, maxY)
        } else {
            rawY
        }

        val exact = visibleItems.find { info ->
            y >= info.offset && y < info.offset + info.size
        }
        val tolerance = 24
        val itemInfo = exact ?: visibleItems.find { info ->
            y >= info.offset - tolerance && y <= info.offset + info.size + tolerance
        } ?: if (clampToVisible) {
            visibleItems.minByOrNull { info ->
                val center = info.offset + info.size / 2
                kotlin.math.abs(y - center)
            }
        } else null
        if (itemInfo == null) return null

        val itemKey = itemInfo.key as? String
        val sliceIndex = itemInfo.index
        val globalIndex = colIndex * chunkSize + sliceIndex
        if (itemKey != null) {
            if (snapshot.getOrNull(globalIndex)?.uniquePath == itemKey) {
                return globalIndex
            }
            val resolved = snapshot.indexOfFirst { it.uniquePath == itemKey }
            if (resolved >= 0) return resolved
        }
        return if (globalIndex in 0 until totalItems) globalIndex else null
    }

    fun getScrollStateForOffset(offset: Offset, viewportWidth: Float): LazyListState? {
        val colWidth = if (cols > 0) viewportWidth / cols else viewportWidth
        val colIndex = (offset.x / colWidth).toInt().coerceIn(0, cols - 1)
        return listStates.getOrNull(colIndex)
    }

    LaunchedEffect(
        tab.pendingLocateFilePath,
        tab.activeFolder.uniquePath,
        snapshot.size,
        parentFolderResolved,
        tab.viewConfig.columnCount
    ) {
        val path = tab.pendingLocateFilePath
        if (path != null && parentFolderResolved) {
            val targetIndex = snapshot.indexOfFirst { it.uniquePath == path }
            if (targetIndex >= 0) {
                val targetColumn = (targetIndex / chunkSize).coerceIn(0, listStates.lastIndex)
                listStates[targetColumn].scrollToItem(targetIndex % chunkSize)
                tab.pendingScrollRestore = null
                if (tab.pendingLocateFilePath == path) tab.pendingLocateFilePath = null
            }
        }
    }

    LaunchedEffect(
        tab.pendingScrollRestore,
        tab.pendingLocateFilePath,
        tab.activeFolder.uniquePath,
        snapshot.size,
        parentFolderResolved,
        cols
    ) {
        val restore = tab.pendingScrollRestore
        val firstColumnSize = columnSlices.firstOrNull()?.size ?: 0
        if (restore != null && tab.pendingLocateFilePath == null && firstColumnSize > 0 && parentFolderResolved) {
            val targetState = listStates[0]
            val targetIndex = restore.first.coerceIn(0, firstColumnSize - 1)
            targetState.scrollToItem(targetIndex, restore.second.coerceAtLeast(0))
            tab.pendingScrollRestore = null
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        if (parentFolder != null) {
            ParentDirectoryItem(tab = tab, parentFolder = parentFolder)
        }

        Row(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f)
                .unifiedFileGestureDetector(
                    tab = tab,
                    viewConfiguration = viewConfiguration,
                    haptic = haptic,
                    coroutineScope = coroutineScope,
                    findItemIndexAt = { offset, size, clamp -> findColumnItemIndex(offset, size.width.toFloat(), clamp) },
                    scrollBy = { delta, offset, size -> getScrollStateForOffset(offset, size.width.toFloat())?.scrollBy(delta) },
                    onOpenMenu = { item -> tab.toggleFileOptionsMenu(item) }
                )
        ) {
            columnSlices.forEachIndexed { colIndex, slice ->
                val listState = listStates[colIndex]
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .fastScrollbar(listState),
                    state = listState,
                    contentPadding = PaddingValues(bottom = bottomContentPadding)
                ) {
                    itemsIndexed(
                        slice,
                        key = { _, item -> item.uniquePath }
                    ) { sliceIndex, item ->
                        val globalIndex = colIndex * chunkSize + sliceIndex
                        ColumnFileItem(
                            item = item,
                            index = globalIndex,
                            tab = tab,
                            selectionHighlightColor = selectionHighlightColor,
                            highlightColor = highlightColor,
                            context = context,
                            viewConfigs = tab.viewConfig,
                            effectiveSize = effectiveSizeOf(item),
                            maxSiblingSize = maxSiblingSize
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FilesListGrid(tab: FilesTab, parentFolder: ContentHolder?, parentFolderResolved: Boolean) {
    val context = LocalContext.current
    val selectionHighlightColor = colorScheme.surfaceContainerHigh.copy(alpha = 1f)
    val highlightColor = colorScheme.primary.copy(alpha = 0.05f)
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    val viewConfiguration = LocalViewConfiguration.current

    val taskManager = globalClass.taskManager
    val hasPendingCopyTask = remember(taskManager.pendingTasksVersion, tab.activeFolder.uniquePath) {
        taskManager.pendingTasks.filterIsInstance<CopyTask>().isNotEmpty() &&
                tab.activeFolder.canAddNewContent &&
                tab.activeFolder !is VirtualFileHolder
    }
    val hasCopyHereFab = tab.copyHereTargetFolder != null ||
            globalClass.mainActivityManager.state.value.tabs.any { (it as? FilesTab)?.copyHereTargetFolder != null }
    val bottomContentPadding = if (hasPendingCopyTask || hasCopyHereFab) 88.dp else 0.dp

    LaunchedEffect(
        tab.pendingLocateFilePath,
        tab.activeFolder.uniquePath,
        tab.contentSnapshot.size,
        parentFolder,
        parentFolderResolved
    ) {
        val path = tab.pendingLocateFilePath
        if (path != null && parentFolderResolved) {
            val targetIndex = tab.contentSnapshot.indexOfFirst { it.uniquePath == path }
            if (targetIndex >= 0) {
                tab.activeListState.scrollToItem(targetIndex + if (parentFolder != null) 1 else 0)
                tab.pendingScrollRestore = null
                if (tab.pendingLocateFilePath == path) tab.pendingLocateFilePath = null
            } else if (tab.activeFolderContent.any { it.uniquePath == path } && tab.selectedTypeFilters.isNotEmpty()) {
                tab.selectedTypeFilters = emptySet()
                tab.updateContentSnapshot()
            }
        }
    }

    LaunchedEffect(
        tab.pendingScrollRestore,
        tab.pendingLocateFilePath,
        tab.activeFolder.uniquePath,
        tab.contentSnapshot.size,
        parentFolder,
        parentFolderResolved
    ) {
        val restore = tab.pendingScrollRestore
        if (restore != null && tab.pendingLocateFilePath == null && tab.contentSnapshot.isNotEmpty() && parentFolderResolved) {
            val maxValid = tab.contentSnapshot.size + if (parentFolder != null) 0 else -1
            val targetIndex = restore.first.coerceIn(0, maxOf(0, maxValid))
            tab.activeListState.scrollToItem(targetIndex, restore.second.coerceAtLeast(0))
            tab.pendingScrollRestore = null
        }
    }

    fun findGridItemIndex(offset: Offset, clampToVisible: Boolean = false): Int? {
        val snapshot = tab.contentSnapshot
        if (snapshot.isEmpty()) return null
        val x = offset.x.toInt()
        val rawY = offset.y.toInt()
        val visibleItems = tab.activeListState.layoutInfo.visibleItemsInfo
        val fileVisibleItems = if (clampToVisible) {
            visibleItems.filter { it.key != "parent_directory_item" }
        } else {
            visibleItems
        }
        if (fileVisibleItems.isEmpty()) return null

        val y = if (clampToVisible) {
            val minY = fileVisibleItems.first().offset.y
            val maxY = (fileVisibleItems.last().offset.y + fileVisibleItems.last().size.height - 1).coerceAtLeast(minY)
            rawY.coerceIn(minY, maxY)
        } else {
            rawY
        }

        val exact = fileVisibleItems.find { info ->
            x >= info.offset.x && x < info.offset.x + info.size.width &&
            y >= info.offset.y && y < info.offset.y + info.size.height
        }
        val tolerance = 24
        val itemInfo = exact ?: fileVisibleItems.find { info ->
            x >= info.offset.x - tolerance && x <= info.offset.x + info.size.width + tolerance &&
            y >= info.offset.y - tolerance && y <= info.offset.y + info.size.height + tolerance
        } ?: if (clampToVisible) {
            val rowCandidates = fileVisibleItems.filter { info ->
                y >= info.offset.y - tolerance && y <= info.offset.y + info.size.height + tolerance
            }.ifEmpty { fileVisibleItems }
            rowCandidates.minByOrNull { info ->
                val centerX = info.offset.x + info.size.width / 2
                val centerY = info.offset.y + info.size.height / 2
                kotlin.math.abs(y - centerY) * 4 + kotlin.math.abs(x - centerX)
            }
        } else null
        if (itemInfo == null) return null

        val itemKey = itemInfo.key as? String
        if (itemKey == "parent_directory_item") return null
        val contentIndex = if (parentFolder != null) itemInfo.index - 1 else itemInfo.index
        if (itemKey != null) {
            if (snapshot.getOrNull(contentIndex)?.uniquePath == itemKey) {
                return contentIndex
            }
            val resolved = snapshot.indexOfFirst { it.uniquePath == itemKey }
            if (resolved >= 0) return resolved
        }
        return if (contentIndex in snapshot.indices) contentIndex else null
    }

    val density = LocalDensity.current
    var autoScrollJob by remember { mutableStateOf<Job?>(null) }

    val currentRequestScrollAboveRibbon by androidx.compose.runtime.rememberUpdatedState<(Int) -> Unit> { itemIndex ->
        val snapshot = tab.contentSnapshot
        if (!globalClass.preferencesManager.disableAutoScrollOnBottomSelection && itemIndex in snapshot.indices) {
            val gridIndex = itemIndex + if (parentFolder != null) 1 else 0
            val gridState = tab.activeListState

            val beforeLayout = gridState.layoutInfo
            val beforeViewportEnd = beforeLayout.viewportEndOffset
            val beforeItem = beforeLayout.visibleItemsInfo.find { it.index == gridIndex }
            val beforeItemBottom = beforeItem?.let { it.offset.y + it.size.height }
            val beforeItemSize = beforeItem?.size?.height

            autoScrollJob?.cancel()
            autoScrollJob = coroutineScope.launch {
                // Wait for selection state and BottomOptionsBar layout measurement to settle
                androidx.compose.runtime.withFrameNanos { }
                androidx.compose.runtime.withFrameNanos { }

                val afterLayout = gridState.layoutInfo
                val afterViewportEnd = afterLayout.viewportEndOffset
                if (afterViewportEnd <= 0) return@launch

                val afterItem = afterLayout.visibleItemsInfo.find { it.index == gridIndex }
                val lastVisible = afterLayout.visibleItemsInfo.lastOrNull()
                val cols = maxOf(1, tab.viewConfig.columnCount)

                val itemSize: Int
                val itemBottom: Int
                when {
                    afterItem != null -> {
                        itemSize = afterItem.size.height
                        itemBottom = afterItem.offset.y + afterItem.size.height
                    }
                    beforeItemBottom != null && afterViewportEnd < beforeViewportEnd -> {
                        itemSize = beforeItemSize ?: 0
                        itemBottom = beforeItemBottom
                    }
                    lastVisible != null && gridIndex > lastVisible.index -> {
                        val rowsAway = (gridIndex - lastVisible.index + cols - 1) / cols
                        itemSize = lastVisible.size.height
                        itemBottom = lastVisible.offset.y + lastVisible.size.height +
                                rowsAway * lastVisible.size.height
                    }
                    else -> return@launch
                }

                val hasMoreRowsBelow = (itemIndex / cols) < (snapshot.lastIndex / cols)
                val scrollDelta = if (!hasMoreRowsBelow) {
                    (itemBottom - afterViewportEnd).toFloat()
                } else {
                    val triggerZonePx = with(density) { 12.dp.toPx() }
                    val peekPx = minOf(itemSize * 0.35f, with(density) { 24.dp.toPx() })
                    if (itemBottom > afterViewportEnd - triggerZonePx) {
                        itemBottom - (afterViewportEnd - peekPx)
                    } else {
                        0f
                    }
                }

                if (scrollDelta > 1f) {
                    try {
                        gridState.animateScrollBy(
                            value = scrollDelta,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioNoBouncy,
                                stiffness = Spring.StiffnessMedium
                            )
                        )
                    } catch (_: Exception) {}
                }
            }
        }
    }

    DisposableEffect(tab) {
        tab.onRequestScrollAboveRibbon = { index -> currentRequestScrollAboveRibbon(index) }
        tab.onCancelAutoScroll = { autoScrollJob?.cancel() }
        onDispose {
            autoScrollJob?.cancel()
            tab.onRequestScrollAboveRibbon = null
            tab.onCancelAutoScroll = null
        }
    }

    LazyVerticalGrid(
        state = tab.activeListState,
        columns = GridCells.Fixed(tab.viewConfig.columnCount),
        contentPadding = PaddingValues(bottom = bottomContentPadding),
        modifier = Modifier
            .fillMaxSize()
            .fastScrollbar(tab.activeListState)
            .unifiedFileGestureDetector(
                tab = tab,
                viewConfiguration = viewConfiguration,
                haptic = haptic,
                coroutineScope = coroutineScope,
                findItemIndexAt = { offset, _, clamp -> findGridItemIndex(offset, clamp) },
                scrollBy = { delta, _, _ -> tab.activeListState.scrollBy(delta) },
                onOpenMenu = { item -> tab.toggleFileOptionsMenu(item) }
            )
    ) {
        val maxSiblingSize = tab.maxSiblingSize

        // `..` parent directory entry (spans full width)
        if (parentFolder != null) {
            item(
                key = "parent_directory_item",
                span = { GridItemSpan(maxLineSpan) }
            ) {
                ParentDirectoryItem(tab = tab, parentFolder = parentFolder)
            }
        }

        itemsIndexed(
            tab.contentSnapshot,
            key = { _, item -> item.uniquePath }
        ) { index, item ->
            GridFileItem(
                itemPath = item.uniquePath,
                item = item,
                index = index,
                tab = tab,
                selectionHighlightColor = selectionHighlightColor,
                highlightColor = highlightColor,
                context = context,
                viewConfigs = tab.viewConfig,
                maxSiblingSize = maxSiblingSize
            )
        }
    }
}

@Composable
private fun ColumnFileItem(
    item: ContentHolder,
    index: Int,
    tab: FilesTab,
    selectionHighlightColor: Color,
    highlightColor: Color,
    context: Context,
    viewConfigs: ViewConfigs,
    effectiveSize: Long = item.size,
    maxSiblingSize: Long = 1L
) {
    val currentItemPath = item.uniquePath
    val isSelected = tab.dragSelectionVersion >= 0 && tab.selectedFiles.containsKey(currentItemPath)
    val isHighlighted = tab.highlightedFiles.contains(currentItemPath)
    val sizeTintEnabled = globalClass.preferencesManager.sizeTintEnabled

    fun toggleSelection() {
        val selecting = !isSelected
        if (isSelected) {
            tab.selectedFiles.remove(currentItemPath)
            tab.lastSelectedFileIndex = -1
        } else {
            tab.selectedFiles[currentItemPath] = item
            tab.lastSelectedFileIndex = index
        }
        tab.dragSelectionVersion++
        tab.selectedFilesCount = tab.selectedFiles.size
        tab.onSelectionChange()
        if (selecting) {
            tab.scrollItemAboveRibbon(index)
        }
    }

    val showSourceBadge = tab.activeFolder is VirtualFileHolder && globalClass.preferencesManager.showSourceBadges
    val sourceInfo = if (showSourceBadge) {
        remember(currentItemPath) {
            SourceFolderResolver.resolve(item)
        }
    } else null

    val activeDragSession = tab.dragDropSession ?: globalClass.mainActivityManager.activeDragSourceTab?.dragDropSession
    val isDropHovered = item.isFolder && activeDragSession?.hoveredTargetDirectory == currentItemPath
    val isDropValid = item.isFolder && activeDragSession?.isValidDropTarget(item, tab.activeFolder) == true

    if (item.isFolder) {
        DisposableEffect(currentItemPath) {
            onDispose {
                tab.unregisterDropTarget(currentItemPath)
            }
        }
    }

    val tintColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
    Column(
        Modifier
            .fillMaxWidth()
            .then(
                if (item.isFolder) {
                    Modifier.onGloballyPositioned { coords ->
                        tab.registerDropTarget(item, coords)
                    }
                } else Modifier
            )
            .background(
                color = if (isDropHovered && isDropValid) {
                    colorScheme.primaryContainer.copy(alpha = 0.7f)
                } else if (isDropHovered && !isDropValid) {
                    colorScheme.errorContainer.copy(alpha = 0.45f)
                } else if (isSelected) {
                    selectionHighlightColor
                } else if (tab.transientPressHighlightPath == currentItemPath) {
                    colorScheme.primary.copy(alpha = 0.12f)
                } else {
                    Color.Unspecified
                }
            )
            .drawBehind {
                if (sizeTintEnabled && !isSelected && maxSiblingSize > 0L && effectiveSize > 0L) {
                    val fraction = (effectiveSize.toFloat() / maxSiblingSize).coerceIn(0f, 1f)
                    drawRect(
                        color = tintColor,
                        size = androidx.compose.ui.geometry.Size(size.width * fraction, size.height)
                    )
                }
            }
            .clickable {
                if (tab.selectedFiles.isNotEmpty()) {
                    toggleSelection()
                } else {
                    if (item.isSymbolicLink && item.isSymbolicLinkBroken) {
                        showMsg(globalClass.getString(R.string.symbolic_link_broken))
                    } else if (item.isFile()) {
                        tab.openFile(context, item)
                    } else {
                        tab.openFolder(item)
                    }
                }
            }
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
                    isSelected = isSelected,
                    fontSize = fontSize.sp,
                    fontStyle = if (isHighlighted && !isSelected) FontStyle.Italic else FontStyle.Normal,
                    fontWeight = if (isHighlighted && !isSelected) FontWeight.Medium else null,
                    lineHeight = (fontSize + 2).sp,
                    maxLines = viewConfigs.filenameMaxLines,
                    color = if (isSelected) {
                        colorScheme.primary
                    } else {
                        Color.Unspecified
                    }
                )

                FileDetails(
                    item = item,
                    currentItemPath = currentItemPath,
                    fontSize = fontSize,
                    isHighlighted = isSelected,
                    sourceInfo = sourceInfo
                )
            }
        }

        Space(size = FileItemSizeMap.getSpace(viewConfigs.itemSize).dp)

        if (globalClass.preferencesManager.showFileListDivider) {
            HorizontalDivider(
                modifier = Modifier.padding(start = 56.dp),
                thickness = 0.5.dp
            )
        }
    }
}

@Composable
private fun GridFileItem(
    itemPath: String,
    item: ContentHolder,
    index: Int,
    tab: FilesTab,
    selectionHighlightColor: Color,
    highlightColor: Color,
    context: Context,
    viewConfigs: ViewConfigs,
    maxSiblingSize: Long = 1L
) {
    val isSelected = tab.dragSelectionVersion >= 0 && tab.selectedFiles.containsKey(itemPath)
    val isHighlighted = tab.highlightedFiles.contains(itemPath)

    fun toggleSelection() {
        val selecting = !isSelected
        if (isSelected) {
            tab.selectedFiles.remove(itemPath)
            tab.lastSelectedFileIndex = -1
        } else {
            tab.selectedFiles[itemPath] = item
            tab.lastSelectedFileIndex = index
        }
        tab.dragSelectionVersion++
        tab.selectedFilesCount = tab.selectedFiles.size
        tab.onSelectionChange()
        if (selecting) {
            tab.scrollItemAboveRibbon(index)
        }
    }

    val showSourceBadge = tab.activeFolder is VirtualFileHolder && globalClass.preferencesManager.showSourceBadges
    val sourceInfo = if (showSourceBadge) {
        remember(itemPath) {
            SourceFolderResolver.resolve(item)
        }
    } else null

    val activeDragSession = tab.dragDropSession ?: globalClass.mainActivityManager.activeDragSourceTab?.dragDropSession
    val isDropHovered = item.isFolder && activeDragSession?.hoveredTargetDirectory == itemPath
    val isDropValid = item.isFolder && activeDragSession?.isValidDropTarget(item, tab.activeFolder) == true

    if (item.isFolder) {
        DisposableEffect(itemPath) {
            onDispose {
                tab.unregisterDropTarget(itemPath)
            }
        }
    }

    val sizeTintEnabled = globalClass.preferencesManager.sizeTintEnabled
    val tintColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (viewConfigs.galleryMode) Modifier.aspectRatio(1f) else Modifier)
            .then(
                if (item.isFolder) {
                    Modifier.onGloballyPositioned { coords ->
                        tab.registerDropTarget(item, coords)
                    }
                } else Modifier
            )
            .clickable {
                if (tab.selectedFiles.isNotEmpty()) {
                    toggleSelection()
                } else {
                    if (item.isSymbolicLink && item.isSymbolicLinkBroken) {
                        showMsg(globalClass.getString(R.string.symbolic_link_broken))
                    } else if (item.isFile()) {
                        tab.openFile(context, item)
                    } else {
                        tab.openFolder(item)
                    }
                }
            }
            .background(
                color = if (isDropHovered && isDropValid) {
                    colorScheme.primaryContainer.copy(alpha = 0.7f)
                } else if (isDropHovered && !isDropValid) {
                    colorScheme.errorContainer.copy(alpha = 0.45f)
                } else if (isSelected) {
                    selectionHighlightColor
                } else if (tab.transientPressHighlightPath == itemPath) {
                    colorScheme.primary.copy(alpha = 0.12f)
                } else {
                    Color.Unspecified
                }
            )
            .drawBehind {
                if (sizeTintEnabled && !isSelected && maxSiblingSize > 0L && item.size > 0L) {
                    val fraction = (item.size.toFloat() / maxSiblingSize).coerceIn(0f, 1f)
                    drawRect(
                        color = tintColor,
                        size = androidx.compose.ui.geometry.Size(size.width * fraction, size.height)
                    )
                }
            }
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
                                tab.openFolder(item)
                            }
                        }
                    },
                    viewConfigs = viewConfigs,
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
                            isSelected = isSelected,
                            fontSize = fontSize.sp,
                            fontWeight = if (isHighlighted && !isSelected) FontWeight.Medium else FontWeight.Normal,
                            fontStyle = if (isHighlighted && !isSelected) FontStyle.Italic else FontStyle.Normal,
                            maxLines = viewConfigs.filenameMaxLines,
                            lineHeight = (fontSize + 2).sp,
                            textAlign = TextAlign.Center,
                            color = if (isSelected) {
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
                    isSelected = isSelected,
                    fontSize = (FileItemSizeMap.getFontSize(viewConfigs.itemSize) - 3).sp,
                    fontWeight = if (isHighlighted && !isSelected) FontWeight.Medium else null,
                    fontStyle = if (isHighlighted && !isSelected) FontStyle.Italic else FontStyle.Normal,
                    maxLines = viewConfigs.filenameMaxLines,
                    textAlign = TextAlign.Center,
                    color = if (isSelected) {
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
                .clickable(onClick = onClick)
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
            } else if (FolderLockStore.hasLock(item.uniquePath)) {
                Icon(
                    modifier = Modifier
                        .size(14.dp)
                        .align(if (sourceInfo != null) Alignment.TopEnd else Alignment.BottomEnd)
                        .alpha(if (item.isHidden()) 0.4f else 1f),
                    imageVector = Icons.Rounded.Lock,
                    tint = MaterialTheme.colorScheme.primary,
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
            LocalFileHolder.getCachedDetails(item.file, item.lastModified, item.size)
                ?: if (item.isFile()) {
                    "${item.size.toFormattedSize()}\t${item.lastModified.toFormattedDate()}"
                } else {
                    "\t${item.lastModified.toFormattedDate()}"
                }
        } else {
            emptyString
        }
    }

    var details by remember(detailsKey) { mutableStateOf(initialDetails) }

    LaunchedEffect(detailsKey) {
        if (details.isEmpty() || (item.isFolder && details.startsWith("\t"))) {
            val det = withContext(IO) {
                item.getDetails()
            }
            details = det
        }
    }

    // Parse dual-aligned format: "leftInfo\trightDate"
    val parts = details.split("\t", limit = 2)
    val rawLeftText = parts.getOrNull(0)?.trim().orEmpty()
    val securityBadge = if (item.isFolder) FolderLockStore.getFolderSecurityBadgeText(item.uniquePath) else null
    val baseLeft = when {
        securityBadge != null && rawLeftText.isNotEmpty() -> "$securityBadge • $rawLeftText"
        securityBadge != null -> securityBadge
        else -> rawLeftText
    }
    val leftText = if (sourceInfo != null && baseLeft.isNotEmpty()) {
        "${sourceInfo.folderName} • $baseLeft"
    } else if (sourceInfo != null) {
        sourceInfo.folderName
    } else {
        baseLeft
    }
    val rightText = parts.getOrNull(1)?.trim().orEmpty()
    val smallFontSize = (fontSize - 4).sp
    val textColor = if (isHighlighted) colorScheme.primary else Color.Unspecified

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = (fontSize + 2).dp)
            .alpha(0.7f),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (leftText.isNotEmpty()) {
            FileDetailsText(
                details = leftText,
                fontSize = smallFontSize,
                color = textColor,
                modifier = Modifier.weight(1f, fill = false)
            )
        } else {
            Spacer(modifier = Modifier.weight(1f))
        }
        if (rightText.isNotEmpty()) {
            val isItalic = DeleteTimestampJournal.shouldItalicize(item.uniquePath, item.lastModified)
            Text(
                text = rightText,
                fontSize = smallFontSize,
                fontStyle = if (isItalic) FontStyle.Italic else FontStyle.Normal,
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
    val activeDragSession = tab.dragDropSession ?: globalClass.mainActivityManager.activeDragSourceTab?.dragDropSession
    val isDropHovered = activeDragSession?.hoveredTargetDirectory == parentFolder.uniquePath
    val isDropValid = activeDragSession?.isValidDropTarget(parentFolder, tab.activeFolder) == true

    DisposableEffect(parentFolder.uniquePath) {
        onDispose {
            tab.unregisterDropTarget(parentFolder.uniquePath)
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .onGloballyPositioned { coords ->
                tab.registerDropTarget(parentFolder, coords)
            }
            .background(
                color = if (isDropHovered && isDropValid) {
                    colorScheme.primaryContainer.copy(alpha = 0.7f)
                } else if (isDropHovered && !isDropValid) {
                    colorScheme.errorContainer.copy(alpha = 0.45f)
                } else {
                    Color.Unspecified
                }
            )
            .clickable {
                val current = tab.activeFolder
                tab.highlightedFiles.apply {
                    clear()
                    add(current.uniquePath)
                }
                tab.openFolder(parentFolder, rememberListState = true)
            }
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .alpha(if (isDropHovered) 1f else 0.55f),
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
                LocalFileHolder.getCachedDetails(item.file, item.lastModified, item.size).orEmpty()
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

        val displayDetails = details.ifEmpty { if (item.isFile()) item.size.toFormattedSize() else " " }
        FileDetailsText(
            modifier = Modifier
                .heightIn(min = 14.dp)
                .alpha(0.6f),
            details = displayDetails,
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
