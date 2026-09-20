package com.techflyers.compose.file.explorer.screen.main.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Tab
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.techflyers.compose.file.explorer.App.Companion.globalClass
import com.techflyers.compose.file.explorer.R
import com.techflyers.compose.file.explorer.common.detectVerticalSwipe
import com.techflyers.compose.file.explorer.common.emptyString
import com.techflyers.compose.file.explorer.common.fromJson
import com.techflyers.compose.file.explorer.common.isNot
import com.techflyers.compose.file.explorer.common.showMsg
import com.techflyers.compose.file.explorer.common.toJson
import com.techflyers.compose.file.explorer.common.ui.Space
import com.techflyers.compose.file.explorer.screen.main.model.ClosedTabEntry
import com.techflyers.compose.file.explorer.screen.main.startup.StartupTab
import com.techflyers.compose.file.explorer.screen.main.startup.StartupTabType
import com.techflyers.compose.file.explorer.screen.main.startup.StartupTabs
import com.techflyers.compose.file.explorer.screen.main.tab.Tab
import com.techflyers.compose.file.explorer.screen.main.tab.apps.AppsTab
import com.techflyers.compose.file.explorer.screen.main.tab.files.FilesTab
import com.techflyers.compose.file.explorer.screen.main.tab.files.holder.LocalFileHolder
import com.techflyers.compose.file.explorer.screen.main.tab.home.HomeTab
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@Composable
fun TabLayout(
    tabLayoutState: LazyListState,
    tabs: List<Tab>,
    selectedTabIndex: Int,
    onReorder: (Int, Int) -> Unit,
    onAddNewTab: () -> Unit,
    isAtBottom: Boolean = false
) {
    val selectedTabBackgroundColor = MaterialTheme.colorScheme.surfaceContainerLowest
    val unselectedTabBackgroundColor =
        MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.5f)
    val draggedTabBackgroundColor = MaterialTheme.colorScheme.primary
    val draggedTabTextColor = MaterialTheme.colorScheme.surfaceContainerLowest
    val hideToolbar = globalClass.preferencesManager.hideToolbar
    val tabShape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)

    val mainActivityManager = globalClass.mainActivityManager

    var from by remember { mutableIntStateOf(-1) }
    var to by remember { mutableIntStateOf(-1) }
    var draggedItem by remember { mutableIntStateOf(-1) }
    val list = remember { mutableStateListOf<Int>() }
    var showClosedTabsHistoryDialog by remember { mutableStateOf(false) }

    val reorderableLazyListState = rememberReorderableLazyListState(tabLayoutState) { old, new ->
        if (draggedItem > -1) {
            if (from < 0) from = old.index
            to = new.index

            list.add(new.index, list.removeAt(old.index))
        }
    }

    LaunchedEffect(draggedItem) {
        if (draggedItem < 0 && from > -1 && to > -1) {
            onReorder(from, to)
            from = -1
            to = -1
        }
    }

    LaunchedEffect(tabs) {
        list.clear()
        list.addAll(tabs.map { it.id })
    }

    Row(
        Modifier
            .fillMaxWidth()
            .height(if (hideToolbar) 50.dp else 42.dp)
            .background(color = MaterialTheme.colorScheme.surfaceContainer),
        verticalAlignment = Alignment.Bottom
    ) {
        if (!hideToolbar) {
            IconButton(
                onClick = onAddNewTab
            ) {
                Icon(imageVector = Icons.Rounded.Add, contentDescription = null)
            }
        }
        LazyRow(
            modifier = Modifier
                .weight(1f)
                .heightIn(max = 42.dp),
            state = tabLayoutState,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(start = if (hideToolbar) 8.dp else 0.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            itemsIndexed(list, key = { _, item -> item }) { index, id ->
                tabs.find { it.id == id }?.let { tab ->
                    ReorderableItem(reorderableLazyListState, key = tab.id) { isDragged ->
                        if (isDragged && draggedItem isNot id) {
                            draggedItem = id
                        } else if (!isDragged && draggedItem == id) {
                            draggedItem = -1
                        }

                        val isSelected = selectedTabIndex == index

                        val backgroundColorAnim = animateColorAsState(
                            targetValue =
                                if (isDragged) {
                                    draggedTabBackgroundColor
                                } else if (isSelected) {
                                    selectedTabBackgroundColor
                                } else {
                                    unselectedTabBackgroundColor
                                }
                        )

                        val textColorAnim = animateColorAsState(
                            targetValue =
                                if (isDragged) {
                                    draggedTabTextColor
                                } else {
                                    Color.Unspecified
                                }
                        )

                        val alphaAnim = animateFloatAsState(
                            targetValue = if (isSelected && draggedItem == -1) 1f else 0.5f
                        )

                        var showTabHeaderMenu by remember(tab.id) {
                            mutableStateOf(false)
                        }
                        var lastClickTime by remember(tab.id) {
                            mutableLongStateOf(0L)
                        }

                        Row(
                            modifier = Modifier
                                .defaultMinSize(minWidth = 100.dp)
                                .fillMaxHeight()
                                .padding(end = 4.dp)
                                .background(
                                    color = backgroundColorAnim.value,
                                    shape = tabShape
                                )
                                .clip(tabShape)
                                .combinedClickable(
                                    indication = null,
                                    interactionSource = remember { MutableInteractionSource() },
                                    onClick = {
                                        val currentTime = android.os.SystemClock.uptimeMillis()
                                        if (currentTime - lastClickTime < 300L) {
                                            lastClickTime = 0L
                                            showTabHeaderMenu = false
                                            if (tabs.size > 1) {
                                                mainActivityManager.removeTabAt(index)
                                            } else if (tab !is HomeTab) {
                                                mainActivityManager.replaceCurrentTabWith(HomeTab())
                                            }
                                        } else {
                                            lastClickTime = currentTime
                                            if (!isSelected) {
                                                mainActivityManager.selectTabAt(index)
                                            } else {
                                                showTabHeaderMenu = true
                                            }
                                        }
                                    }
                                )
                                .padding(horizontal = 20.dp)
                                .longPressDraggableHandle()
                                .then(
                                    if (!globalClass.preferencesManager.disableNavigationGestures) {
                                        Modifier.detectVerticalSwipe(
                                            onSwipeDown = {
                                                mainActivityManager.removeTabAt(index)
                                            }
                                        )
                                    } else Modifier
                                ),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text(
                                modifier = Modifier.alpha(alphaAnim.value),
                                text = tab.header,
                                fontSize = 14.sp,
                                color = textColorAnim.value
                            )
                            if (showTabHeaderMenu) {
                                OptionsMenu(
                                    tab = tab,
                                    index = index,
                                    onDismiss = { showTabHeaderMenu = false },
                                    onShowClosedTabsHistory = { showClosedTabsHistoryDialog = true }
                                )
                            }
                        }
                    }
                }
            }
        }
        if (globalClass.preferencesManager.hideToolbar) {
            IconButton(
                onClick = onAddNewTab
            ) {
                Icon(imageVector = Icons.Rounded.Add, contentDescription = null)
            }
            MoreOptionsButton()
        }
    }

    if (showClosedTabsHistoryDialog) {
        ClosedTabsHistoryDialog(onDismiss = { showClosedTabsHistoryDialog = false })
    }
}

@Composable
fun OptionsMenu(
    tab: Tab,
    index: Int,
    onDismiss: () -> Unit,
    onShowClosedTabsHistory: () -> Unit = {}
) {
    DropdownMenu(
        expanded = true,
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        val startupTabsObj = remember {
            (fromJson<StartupTabs>(globalClass.preferencesManager.startupTabs)
                ?: StartupTabs.default())
        }
        val startupTabs = remember {
            arrayListOf<StartupTab>().apply {
                addAll(startupTabsObj.tabs)
            }
        }

        @Composable
        fun addStartupTabMenuItems(
            tabType: StartupTabType,
            extra: String = emptyString,
            canAdd: Boolean,
            canRemove: Boolean
        ) {
            if (canAdd) {
                DropdownMenuItem(
                    text = { Text(text = stringResource(R.string.add_to_startup)) },
                    onClick = {
                        startupTabs.add(StartupTab(tabType, extra))
                        globalClass.preferencesManager.startupTabs =
                            startupTabsObj.copy(tabs = startupTabs).toJson()
                        onDismiss()
                        showMsg(globalClass.getString(R.string.added_as_startup_tab))
                    }
                )
            }

            if (canRemove && startupTabs.size > 1) {
                DropdownMenuItem(
                    text = { Text(text = stringResource(R.string.remove_from_startup)) },
                    onClick = {
                        startupTabs.removeIf {
                            it.type == tabType && (extra == emptyString || it.extra == extra)
                        }
                        globalClass.preferencesManager.startupTabs =
                            startupTabsObj.copy(tabs = startupTabs).toJson()
                        onDismiss()
                        showMsg(globalClass.getString(R.string.removed_from_startup_tabs))
                    }
                )
            }
        }

        when (tab) {
            is HomeTab -> {
                val homeTabs = startupTabs.filter { it.type == StartupTabType.HOME }
                addStartupTabMenuItems(
                    tabType = StartupTabType.HOME,
                    canAdd = homeTabs.isEmpty(),
                    canRemove = homeTabs.isNotEmpty()
                )
            }

            is AppsTab -> {
                val appsTabs = startupTabs.filter { it.type == StartupTabType.APPS }
                addStartupTabMenuItems(
                    tabType = StartupTabType.APPS,
                    canAdd = appsTabs.isEmpty(),
                    canRemove = appsTabs.isNotEmpty()
                )
            }

            is FilesTab -> {
                if (tab.activeFolder is LocalFileHolder) {
                    val uniquePath = (tab.activeFolder as LocalFileHolder).uniquePath
                    val filesTabsPaths = startupTabs
                        .filter { it.type == StartupTabType.FILES }
                        .map { it.extra }

                    addStartupTabMenuItems(
                        tabType = StartupTabType.FILES,
                        extra = uniquePath,
                        canAdd = true, // Files tabs can always be added
                        canRemove = filesTabsPaths.contains(uniquePath)
                    )
                }
            }
        }

        if (index > 0) {
            DropdownMenuItem(
                text = { Text(text = stringResource(R.string.close)) },
                onClick = {
                    globalClass.mainActivityManager.removeTabAt(index)
                    onDismiss()
                }
            )
        }

        DropdownMenuItem(
            text = { Text(text = stringResource(R.string.close_others)) },
            onClick = {
                globalClass.mainActivityManager.removeOtherTabs(index)
                onDismiss()
            }
        )

        DropdownMenuItem(
            text = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = stringResource(R.string.reopen_closed_tab),
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    IconButton(
                        onClick = {
                            onDismiss()
                            onShowClosedTabsHistory()
                        },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.History,
                            contentDescription = stringResource(R.string.closed_tabs_history),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            },
            onClick = {
                val reopened = globalClass.mainActivityManager.reopenLastClosedTab()
                if (!reopened) {
                    showMsg(globalClass.getString(R.string.no_closed_tabs))
                }
                onDismiss()
            }
        )

        if (tab !is HomeTab) {
            DropdownMenuItem(
                text = { Text(text = stringResource(R.string.home_tab_title)) },
                onClick = {
                    globalClass.mainActivityManager.replaceCurrentTabWith(HomeTab())
                    onDismiss()
                }
            )
        }
    }
}

@Composable
fun ClosedTabsHistoryDialog(
    onDismiss: () -> Unit
) {
    val mainActivityManager = globalClass.mainActivityManager
    var history by remember {
        mutableStateOf(mainActivityManager.getClosedTabsHistorySnapshot())
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Rounded.History,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text(
                text = stringResource(R.string.closed_tabs_history),
                style = MaterialTheme.typography.titleMedium
            )
        },
        text = {
            if (history.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.no_closed_tabs),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 350.dp)
                ) {
                    items(history.size) { i ->
                        val entry = history[i]
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .combinedClickable(
                                    indication = null,
                                    interactionSource = remember { MutableInteractionSource() },
                                    onClick = {
                                        mainActivityManager.reopenClosedTab(entry)
                                        onDismiss()
                                    }
                                )
                                .padding(horizontal = 8.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Tab,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Space(10.dp)
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = entry.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (entry.subtitle.isNotEmpty() && entry.subtitle != entry.title) {
                                    Text(
                                        text = entry.subtitle,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            IconButton(
                                onClick = {
                                    mainActivityManager.removeClosedTab(entry)
                                    history = mainActivityManager.getClosedTabsHistorySnapshot()
                                },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = stringResource(R.string.close),
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        if (i < history.lastIndex) {
                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 4.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (history.isNotEmpty()) {
                TextButton(
                    onClick = {
                        mainActivityManager.clearClosedTabsHistory()
                        history = emptyList()
                    }
                ) {
                    Text(stringResource(R.string.clear_history))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.close))
            }
        }
    )
}