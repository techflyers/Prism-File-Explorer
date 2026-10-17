package com.techflyers.compose.file.explorer.screen.main.tab.files.ui

import android.os.Environment
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.techflyers.compose.file.explorer.R
import com.techflyers.compose.file.explorer.common.copyToClipboard
import com.techflyers.compose.file.explorer.common.getIndexIf
import com.techflyers.compose.file.explorer.common.orIf
import com.techflyers.compose.file.explorer.common.showMsg
import com.techflyers.compose.file.explorer.screen.main.tab.files.FilesTab
import com.techflyers.compose.file.explorer.screen.main.tab.files.holder.VirtualFileHolder
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.max

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.techflyers.compose.file.explorer.App.Companion.globalClass
import com.techflyers.compose.file.explorer.common.ui.Space
import com.techflyers.compose.file.explorer.screen.main.tab.files.holder.ContentHolder
import com.techflyers.compose.file.explorer.screen.main.tab.files.holder.LocalFileHolder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BreadcrumbBar(tab: FilesTab) {
    val highlightedPathListItemColor = MaterialTheme.colorScheme.primary

    if (tab.showCategories) {
        Column {
            CategoriesRow(tab)
            FormatRibbon(tab)
        }
    } else if (tab.activeFolder !is VirtualFileHolder) {
        TypeFilterRibbon(tab)
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val isHomeCurrentFolder = tab.homeDir.uniquePath == tab.activeFolder.uniquePath
            val isHomeHovered = tab.dragDropSession?.hoveredTargetDirectory == tab.homeDir.uniquePath
            val isHomeValid = tab.dragDropSession?.isValidDropTarget(tab.homeDir, tab.activeFolder) == true

            Box(
                modifier = Modifier
                    .padding(horizontal = 2.dp)
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
            ) {
                IconButton(
                    onClick = {
                        val homeIndex = globalClass.mainActivityManager.state.value.tabs.indexOfFirst {
                            it is com.techflyers.compose.file.explorer.screen.main.tab.home.HomeTab
                        }
                        if (homeIndex != -1) {
                            globalClass.mainActivityManager.selectTabAt(homeIndex)
                        } else {
                            tab.openFolder(tab.homeDir, false)
                        }
                    }
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Home,
                        contentDescription = stringResource(id = R.string.home_tab_title)
                    )
                }
            }

            val animationScope = rememberCoroutineScope()

            val highlightedIndex = tab.currentPathSegments.indexOfFirst {
                tab.breadcrumbSegmentKey(it) == tab.breadcrumbSegmentKey(tab.highlightedPathSegment)
            }

            LaunchedEffect(key1 = tab.highlightedPathSegment, key2 = tab.currentPathSegments.size) {
                val index = tab.currentPathSegments.indexOfFirst {
                    tab.breadcrumbSegmentKey(it) == tab.breadcrumbSegmentKey(tab.highlightedPathSegment)
                }
                animationScope.launch {
                    tab.currentPathSegmentsListState.scrollToItem(max(index, 0))
                }
            }

            val consumeLeftoverScroll = remember {
                object : NestedScrollConnection {
                    override fun onPostScroll(
                        consumed: Offset,
                        available: Offset,
                        source: NestedScrollSource
                    ): Offset {
                        // Only consume the scroll that LazyRow couldn't handle (at boundaries)
                        return Offset(available.x, 0f)
                    }

                    override suspend fun onPreFling(available: Velocity): Velocity {
                        // Consume all horizontal velocity to prevent fling from propagating to parent
                        return Velocity(available.x, 0f)
                    }
                }
            }


            LazyRow(
                modifier = Modifier
                    .weight(1f)
                    .nestedScroll(consumeLeftoverScroll),
                state = tab.currentPathSegmentsListState,
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
                                    .clip(CircleShape)
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
                                    .padding(8.dp)
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
                                fontWeight = if (isHighlighted || isCopyHereTarget) FontWeight.Medium else FontWeight.Normal,
                                color = when {
                                    isHighlighted || isCopyHereTarget -> highlightedPathListItemColor
                                    isHistorySegment -> MaterialTheme.colorScheme.onSurfaceVariant
                                    else -> Color.Unspecified
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
    var subfolders by remember { mutableStateOf<List<ContentHolder>>(emptyList()) }
    var isLoadingSubfolders by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Box(
        modifier = Modifier
            .clip(CircleShape)
            .clickable {
                showDropdown = true
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
            .padding(4.dp),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            modifier = Modifier.size(16.dp),
            imageVector = Icons.Rounded.PlayArrow,
            contentDescription = stringResource(R.string.folders)
        )

        DropdownMenu(
            expanded = showDropdown,
            onDismissRequest = { showDropdown = false }
        ) {
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
                    DropdownMenuItem(
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Rounded.Folder,
                                contentDescription = null,
                                tint = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        text = {
                            Text(
                                text = folder.displayName,
                                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                color = if (isCurrent) MaterialTheme.colorScheme.primary else Color.Unspecified
                            )
                        },
                        onClick = {
                            showDropdown = false
                            tab.openFolder(folder, rememberSelectedFiles = true)
                        }
                    )
                }
            }
        }
    }
}
