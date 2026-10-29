package com.techflyers.compose.file.explorer.screen.main.tab.files.ui

import android.os.Environment
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.techflyers.compose.file.explorer.App.Companion.globalClass
import com.techflyers.compose.file.explorer.R
import com.techflyers.compose.file.explorer.common.copyToClipboard
import com.techflyers.compose.file.explorer.common.orIf
import com.techflyers.compose.file.explorer.common.showMsg
import com.techflyers.compose.file.explorer.common.ui.Space
import com.techflyers.compose.file.explorer.screen.main.tab.files.FilesTab
import com.techflyers.compose.file.explorer.screen.main.tab.files.holder.ContentHolder
import com.techflyers.compose.file.explorer.screen.main.tab.files.holder.LocalFileHolder
import com.techflyers.compose.file.explorer.screen.main.tab.files.holder.RootFileHolder
import com.techflyers.compose.file.explorer.screen.main.tab.files.holder.VirtualFileHolder
import com.techflyers.compose.file.explorer.screen.main.tab.files.provider.StorageProvider
import com.techflyers.compose.file.explorer.screen.main.tab.home.HomeTab
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max

fun navigateBreadcrumbHomeTap() {
    val manager = globalClass.mainActivityManager
    manager.replaceCurrentTabWith(HomeTab(), keepCurrentTabAsParent = true)
}

fun navigateBreadcrumbHomeLongPress(tab: FilesTab) {
    val internalRoot = StorageProvider.getPrimaryInternalStorage(globalClass).contentHolder
    val targetRoot = if (!globalClass.preferencesManager.hideRootStorage &&
        tab.activeFolder.uniquePath == internalRoot.uniquePath
    ) {
        RootFileHolder()
    } else {
        internalRoot
    }
    tab.openFolder(targetRoot, rememberListState = false)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BreadcrumbBar(tab: FilesTab) {
    val highlightedPathListItemColor = MaterialTheme.colorScheme.primary

    if (tab.showCategories) {
        CategoriesRow(tab)
    } else if (tab.activeFolder !is VirtualFileHolder) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val isHomeCurrentFolder = tab.homeDir.uniquePath == tab.activeFolder.uniquePath
            val isHomeHovered = tab.dragDropSession?.hoveredTargetDirectory == tab.homeDir.uniquePath
            val isHomeValid = tab.dragDropSession?.isValidDropTarget(tab.homeDir, tab.activeFolder) == true

            Box(
                modifier = Modifier
                    .padding(horizontal = 4.dp)
                    .size(40.dp)
                    .clip(CircleShape)
                    .onGloballyPositioned { coordinates ->
                        if (!isHomeCurrentFolder) {
                            tab.registerDropTarget(tab.homeDir, coordinates)
                        }
                    }
                    .then(
                        if (isHomeHovered && isHomeValid) {
                            Modifier
                                .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(8.dp))
                                .border(1.5.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))
                        } else if (isHomeHovered && !isHomeValid) {
                            Modifier
                                .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                .border(1.5.dp, MaterialTheme.colorScheme.error, RoundedCornerShape(8.dp))
                        } else Modifier
                    )
                    .combinedClickable(
                        onClick = { navigateBreadcrumbHomeTap() },
                        onLongClick = { navigateBreadcrumbHomeLongPress(tab) }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Home,
                    contentDescription = stringResource(id = R.string.home_tab_title)
                )
            }

            val highlightedIndex = tab.currentPathSegments.indexOfFirst {
                tab.breadcrumbSegmentKey(it) == tab.breadcrumbSegmentKey(tab.highlightedPathSegment)
            }

            LaunchedEffect(key1 = tab.highlightedPathSegment, key2 = tab.currentPathSegments.size) {
                val index = tab.currentPathSegments.indexOfFirst {
                    tab.breadcrumbSegmentKey(it) == tab.breadcrumbSegmentKey(tab.highlightedPathSegment)
                }
                val targetIndex = max(index, 0)
                withFrameNanos { }
                val listState = tab.currentPathSegmentsListState
                val layoutInfo = listState.layoutInfo
                val visibleItem = layoutInfo.visibleItemsInfo.find { it.index == targetIndex }
                if (visibleItem == null) {
                    listState.animateScrollToItem(targetIndex)
                    withFrameNanos { }
                    val updatedLayout = listState.layoutInfo
                    val newlyVisible = updatedLayout.visibleItemsInfo.find { it.index == targetIndex }
                    if (newlyVisible != null) {
                        val endBound = updatedLayout.viewportEndOffset - updatedLayout.afterContentPadding
                        val itemEnd = newlyVisible.offset + newlyVisible.size
                        if (itemEnd > endBound) {
                            listState.animateScrollBy((itemEnd - endBound).toFloat())
                        }
                    }
                } else {
                    val startBound = 0
                    val endBound = layoutInfo.viewportEndOffset - layoutInfo.afterContentPadding
                    val itemStart = visibleItem.offset
                    val itemEnd = visibleItem.offset + visibleItem.size
                    if (itemStart < startBound) {
                        listState.animateScrollBy((itemStart - startBound).toFloat())
                    } else if (itemEnd > endBound) {
                        listState.animateScrollBy((itemEnd - endBound).toFloat())
                    }
                }
            }

            // Consume leftover horizontal scroll and post-fling velocity so the outer HorizontalPager
            // does not intercept swipes while keeping full momentum fling on the breadcrumb LazyRow.
            val consumeLeftoverScroll = remember {
                object : NestedScrollConnection {
                    override fun onPostScroll(
                        consumed: Offset,
                        available: Offset,
                        source: NestedScrollSource
                    ): Offset {
                        return Offset(available.x, 0f)
                    }

                    override suspend fun onPostFling(
                        consumed: Velocity,
                        available: Velocity
                    ): Velocity {
                        return Velocity(available.x, 0f)
                    }
                }
            }

            LazyRow(
                modifier = Modifier
                    .weight(1f)
                    .nestedScroll(consumeLeftoverScroll),
                state = tab.currentPathSegmentsListState,
                verticalAlignment = Alignment.CenterVertically
            ) {
                itemsIndexed(tab.currentPathSegments, key = { _, it -> tab.breadcrumbSegmentKey(it) }) { index, item ->
                    val isHighlighted = index == highlightedIndex ||
                            (highlightedIndex < 0 && tab.breadcrumbSegmentKey(item) == tab.breadcrumbSegmentKey(tab.highlightedPathSegment))
                    val isHistorySegment = highlightedIndex >= 0 && index > highlightedIndex
                    val isCurrentFolder = tab.breadcrumbSegmentKey(item) == tab.breadcrumbSegmentKey(tab.activeFolder)
                    val isCopyHereTarget = tab.copyHereTargetFolder?.uniquePath == item.uniquePath
                    val isDropTarget = !isCurrentFolder
                    val isHovered = tab.dragDropSession?.hoveredTargetDirectory == item.uniquePath
                    val isValidTarget = tab.dragDropSession?.isValidDropTarget(item, tab.activeFolder) == true

                    DisposableEffect(item.uniquePath) {
                        onDispose {
                            tab.unregisterDropTarget(item.uniquePath)
                        }
                    }

                    Row(
                        modifier = Modifier,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.alpha(if (isHistorySegment) 0.42f else 1f)) {
                            BreadcrumbSeparator(
                                index = index,
                                currentItem = item,
                                pathSegments = tab.currentPathSegments,
                                tab = tab
                            )
                        }
                        Box(
                            modifier = Modifier
                                .onGloballyPositioned { coordinates ->
                                    if (isDropTarget) {
                                        tab.registerDropTarget(item, coordinates)
                                    }
                                }
                                .then(
                                    if (isHovered && isValidTarget) {
                                        Modifier
                                            .background(
                                                MaterialTheme.colorScheme.primaryContainer,
                                                RoundedCornerShape(8.dp)
                                            )
                                            .border(
                                                1.5.dp,
                                                MaterialTheme.colorScheme.primary,
                                                RoundedCornerShape(8.dp)
                                            )
                                    } else if (isHovered && !isValidTarget) {
                                        Modifier
                                            .background(
                                                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                                                RoundedCornerShape(8.dp)
                                            )
                                            .border(
                                                1.5.dp,
                                                MaterialTheme.colorScheme.error,
                                                RoundedCornerShape(8.dp)
                                            )
                                    } else if (isCopyHereTarget) {
                                        Modifier
                                            .background(
                                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f),
                                                RoundedCornerShape(8.dp)
                                            )
                                            .border(
                                                1.dp,
                                                MaterialTheme.colorScheme.primary,
                                                RoundedCornerShape(8.dp)
                                            )
                                    } else Modifier
                                )
                                .padding(horizontal = 2.dp)
                        ) {
                            Text(
                                modifier = Modifier
                                    .widthIn(max = 220.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .combinedClickable(
                                        onClick = {
                                            tab.scope.launch {
                                                if (tab.isBreadcrumbSegmentValid(item)) {
                                                    tab.openFolderImpl(
                                                        item = item,
                                                        rememberSelectedFiles = true,
                                                    )
                                                } else {
                                                    tab.pruneInvalidBreadcrumbSegments()
                                                    tab.reloadFiles()
                                                }
                                            }
                                        },
                                        onDoubleClick = {
                                            if (item.canAddNewContent) {
                                                tab.toggleCopyHereMode(item)
                                            }
                                        },
                                        onLongClick = {
                                            item.uniquePath.copyToClipboard()
                                            showMsg(R.string.path_copied_to_clipboard)
                                        }
                                    )
                                    .padding(horizontal = 8.dp, vertical = 6.dp)
                                    .alpha(
                                        when {
                                            isCopyHereTarget || isHighlighted -> 1f
                                            isHistorySegment -> 0.45f
                                            else -> 0.8f
                                        }
                                    ),
                                text = item.displayName
                                    .orIf(stringResource(id = R.string.internal_storage)) {
                                        item.uniquePath == Environment.getExternalStorageDirectory().absolutePath
                                    }
                                    .orIf(stringResource(id = R.string.root)) {
                                        item.uniquePath == File.separator
                                    },
                                fontSize = 14.sp,
                                lineHeight = 18.sp,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                                fontWeight = if (isHighlighted || isCopyHereTarget) FontWeight.Medium else FontWeight.Normal,
                                color = when {
                                    isHighlighted || isCopyHereTarget -> highlightedPathListItemColor
                                    isHistorySegment -> MaterialTheme.colorScheme.onSurfaceVariant
                                    else -> MaterialTheme.colorScheme.onSurface
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
private fun BreadcrumbSeparator(
    index: Int,
    currentItem: ContentHolder,
    pathSegments: List<ContentHolder>,
    tab: FilesTab
) {
    var showDropdown by remember { mutableStateOf(false) }
    var openedByDrag by remember { mutableStateOf(false) }
    var subfolders by remember { mutableStateOf<List<ContentHolder>>(emptyList()) }
    var isLoadingSubfolders by remember { mutableStateOf(false) }
    var separatorCoords by remember { mutableStateOf<androidx.compose.ui.layout.LayoutCoordinates?>(null) }
    val scope = rememberCoroutineScope()
    val mainView = androidx.compose.ui.platform.LocalView.current

    val loadSubfolders: () -> Unit = {
        scope.launch {
            isLoadingSubfolders = true
            val list = withContext(Dispatchers.IO) {
                val parentFolder = if (index > 0) {
                    pathSegments.getOrNull(index - 1)
                } else {
                    runCatching { currentItem.getParent() }.getOrNull()
                }
                if (parentFolder == null || runCatching { !parentFolder.isValid() }.getOrDefault(true)) {
                    emptyList()
                } else if (parentFolder is LocalFileHolder) {
                    val files = parentFolder.file.listFiles() ?: emptyArray()
                    files.filter { it.isDirectory && (globalClass.preferencesManager.showHiddenFiles || !it.name.startsWith(".")) }
                        .sortedBy { it.name.lowercase() }
                        .map { LocalFileHolder(it) }
                } else {
                    runCatching { parentFolder.listContent().filter { it.isFolder } }.getOrNull() ?: emptyList()
                }
            }
            subfolders = list
            isLoadingSubfolders = false
        }
    }

    val dragSession = tab.dragDropSession
    val currentWinOffset = dragSession?.currentWindowOffset
    val isHoveringSeparator = remember(dragSession, currentWinOffset, separatorCoords) {
        if (dragSession == null || currentWinOffset == null) {
            false
        } else {
            val coords = separatorCoords
            if (coords != null && coords.isAttached) {
                coords.boundsInWindow().inflate(10f).contains(currentWinOffset)
            } else {
                false
            }
        }
    }

    LaunchedEffect(isHoveringSeparator, dragSession != null) {
        if (dragSession == null) {
            if (openedByDrag) {
                showDropdown = false
                openedByDrag = false
            }
        } else if (isHoveringSeparator && !showDropdown) {
            kotlinx.coroutines.delay(220)
            tab.activeBreadcrumbDropdownIndex = index
            openedByDrag = true
            showDropdown = true
            loadSubfolders()
        }
    }

    LaunchedEffect(tab.activeBreadcrumbDropdownIndex) {
        if (openedByDrag && tab.activeBreadcrumbDropdownIndex != index && showDropdown) {
            showDropdown = false
            openedByDrag = false
        }
    }

    Box(
        modifier = Modifier
            .clip(CircleShape)
            .onGloballyPositioned { separatorCoords = it }
            .then(
                if (isHoveringSeparator) {
                    Modifier.background(
                        MaterialTheme.colorScheme.primaryContainer,
                        CircleShape
                    )
                } else Modifier
            )
            .clickable {
                openedByDrag = false
                showDropdown = true
                loadSubfolders()
            }
            .padding(4.dp),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            modifier = Modifier.size(16.dp),
            imageVector = Icons.Rounded.PlayArrow,
            contentDescription = stringResource(R.string.folders),
            tint = if (isHoveringSeparator) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
        )

        DropdownMenu(
            expanded = showDropdown,
            onDismissRequest = {
                showDropdown = false
                openedByDrag = false
            },
            properties = androidx.compose.ui.window.PopupProperties(
                focusable = dragSession == null
            ),
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            val popupView = androidx.compose.ui.platform.LocalView.current
            if (isLoadingSubfolders) {
                DropdownMenuItem(
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                            Space(8.dp)
                            Text(stringResource(R.string.loading))
                        }
                    },
                    onClick = {}
                )
            } else if (subfolders.isEmpty()) {
                DropdownMenuItem(
                    text = {
                        Text(
                            text = stringResource(R.string.no_subfolders_found),
                            color = MaterialTheme.colorScheme.outline
                        )
                    },
                    onClick = { showDropdown = false }
                )
            } else {
                subfolders.forEach { folder ->
                    val isCurrent = folder.uniquePath == currentItem.uniquePath
                    val isHovered = dragSession?.hoveredTargetDirectory == folder.uniquePath
                    val isValidTarget = dragSession?.isValidDropTarget(folder, tab.activeFolder) == true

                    DisposableEffect(folder.uniquePath) {
                        onDispose {
                            tab.unregisterPopupDropTarget(folder.uniquePath)
                        }
                    }

                    DropdownMenuItem(
                        modifier = Modifier
                            .onGloballyPositioned { itemCoords ->
                                tab.registerPopupDropTarget(folder) {
                                    if (!itemCoords.isAttached || !mainView.isAttachedToWindow || !popupView.isAttachedToWindow) {
                                        null
                                    } else {
                                        val mainLoc = IntArray(2)
                                        val popupLoc = IntArray(2)
                                        mainView.getLocationOnScreen(mainLoc)
                                        popupView.getLocationOnScreen(popupLoc)
                                        val dx = (popupLoc[0] - mainLoc[0]).toFloat()
                                        val dy = (popupLoc[1] - mainLoc[1]).toFloat()
                                        itemCoords.boundsInWindow().translate(dx, dy)
                                    }
                                }
                            }
                            .then(
                                if (isHovered && isValidTarget) {
                                    Modifier
                                        .background(
                                            MaterialTheme.colorScheme.primaryContainer,
                                            RoundedCornerShape(8.dp)
                                        )
                                        .border(
                                            1.5.dp,
                                            MaterialTheme.colorScheme.primary,
                                            RoundedCornerShape(8.dp)
                                        )
                                } else if (isHovered && !isValidTarget) {
                                    Modifier
                                        .background(
                                            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                                            RoundedCornerShape(8.dp)
                                        )
                                        .border(
                                            1.5.dp,
                                            MaterialTheme.colorScheme.error,
                                            RoundedCornerShape(8.dp)
                                        )
                                } else Modifier
                            ),
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Rounded.Folder,
                                contentDescription = null,
                                tint = if (isCurrent || (isHovered && isValidTarget)) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        text = {
                            Text(
                                text = folder.displayName,
                                fontWeight = if (isCurrent || isHovered) FontWeight.Bold else FontWeight.Normal,
                                color = if (isCurrent || (isHovered && isValidTarget)) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        },
                        onClick = {
                            showDropdown = false
                            openedByDrag = false
                            tab.openFolder(folder, rememberSelectedFiles = true)
                        }
                    )
                }
            }
        }
    }
}
