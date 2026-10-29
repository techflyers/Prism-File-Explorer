package com.techflyers.compose.file.explorer.screen.main.ui

import android.content.Intent
import android.content.res.Configuration
import android.os.Environment
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.InsertDriveFile
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AddCircleOutline
import androidx.compose.material.icons.rounded.AddTask
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.AutoAwesomeMotion
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckBox
import androidx.compose.material.icons.rounded.CheckBoxOutlineBlank
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.DateRange
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.EditAttributes
import androidx.compose.material.icons.rounded.FileCopy
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderSpecial
import androidx.compose.material.icons.rounded.FormatColorText
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.RemoveRedEye
import androidx.compose.material.icons.rounded.RestoreFromTrash
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.SortByAlpha
import androidx.compose.material.icons.rounded.ViewComfy
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.rememberTooltipState
import androidx.compose.foundation.border
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.techflyers.compose.file.explorer.App.Companion.globalClass
import com.techflyers.compose.file.explorer.R
import com.techflyers.compose.file.explorer.common.copyToClipboard
import com.techflyers.compose.file.explorer.common.getIndexIf
import com.techflyers.compose.file.explorer.common.toFormattedSize
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Update
import com.techflyers.compose.file.explorer.common.icons.PrismIcons
import com.techflyers.compose.file.explorer.common.icons.Upgrade
import com.techflyers.compose.file.explorer.common.icons.SortNameAscending
import com.techflyers.compose.file.explorer.common.icons.SortNameDescending
import com.techflyers.compose.file.explorer.common.icons.SortSizeSmaller
import com.techflyers.compose.file.explorer.common.icons.SortSizeLarger
import com.techflyers.compose.file.explorer.common.icons.SortTypeAscending
import com.techflyers.compose.file.explorer.common.icons.SortTypeDescending
import com.techflyers.compose.file.explorer.common.icons.getSortIcon
import com.techflyers.compose.file.explorer.screen.main.tab.files.ui.dialog.FileSortingMenuDialog
import com.techflyers.compose.file.explorer.common.orIf
import com.techflyers.compose.file.explorer.common.showMsg
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.FilterListOff
import androidx.compose.material.icons.rounded.MoveToInbox
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import com.techflyers.compose.file.explorer.common.detectVerticalSwipe
import com.techflyers.compose.file.explorer.common.ui.Space
import com.techflyers.compose.file.explorer.screen.main.tab.Tab
import com.techflyers.compose.file.explorer.screen.main.tab.files.FilesTab
import com.techflyers.compose.file.explorer.screen.main.tab.files.holder.LocalFileHolder
import com.techflyers.compose.file.explorer.screen.main.tab.files.holder.VirtualFileHolder
import com.techflyers.compose.file.explorer.screen.main.tab.files.misc.FileSortingPrefs
import com.techflyers.compose.file.explorer.screen.main.tab.files.misc.SortingMethod
import com.techflyers.compose.file.explorer.screen.main.tab.files.task.CopyTask
import com.techflyers.compose.file.explorer.screen.main.tab.files.task.CopyTaskParameters
import com.techflyers.compose.file.explorer.screen.main.tab.home.HomeTab
import com.techflyers.compose.file.explorer.screen.preferences.PreferencesActivity
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import java.io.File
import kotlin.math.max

/**
 * A convenience wrapper that adds a [PlainTooltip] (shown on long-press) to an [IconButton].
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun TooltipIconButton(
    tooltip: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = { PlainTooltip { Text(tooltip) } },
        state = rememberTooltipState()
    ) {
        IconButton(onClick = onClick, modifier = modifier) {
            content()
        }
    }
}

@Composable
fun Toolbar(
    title: String,
    subtitle: String,
    onToggleAppInfoDialog: (Boolean) -> Unit,
    hasNewUpdate: Boolean
) {
    val mainActivityManager = globalClass.mainActivityManager
    val state by mainActivityManager.state.collectAsState()
    val activeTab = state.tabs.getOrNull(state.selectedTabIndex)
    val isFilesTab = activeTab is FilesTab
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    // In landscape, force single-row mode (disable tab bar) and disable bottom toolbar
    val disableTabBar = globalClass.preferencesManager.disableTabBar || isLandscape
    val buttonModifier = if (disableTabBar) Modifier.size(44.dp) else Modifier

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.surfaceContainer
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box {
            TooltipIconButton(
                tooltip = stringResource(R.string.navigation_drawer),
                onClick = { onToggleAppInfoDialog(true) },
                modifier = buttonModifier
            ) {
                if (hasNewUpdate) {
                    Icon(
                        imageVector = PrismIcons.Upgrade,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error
                    )
                } else {
                    Icon(imageVector = Icons.Rounded.Menu, contentDescription = null)
                }
            }
        }

        if (isFilesTab) {
            val filesTab = activeTab as FilesTab
            val isSelectionActive = filesTab.selectedFiles.isNotEmpty() || filesTab.selectedFilesCount > 0
            if (isSelectionActive) {
                TooltipIconButton(
                    tooltip = stringResource(R.string.deselect_all),
                    onClick = { filesTab.unselectAllFiles() },
                    modifier = buttonModifier
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = stringResource(R.string.deselect_all),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            } else if (filesTab.canGoForward()) {
                TooltipIconButton(
                    tooltip = stringResource(R.string.forward),
                    onClick = { filesTab.goForward() },
                    modifier = buttonModifier
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                        contentDescription = stringResource(R.string.forward),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        if (isLandscape) {
            val isTabBarDisabledPref = globalClass.preferencesManager.disableTabBar

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                if (isTabBarDisabledPref) {
                    SingleRowTabsControl(state.tabs, state.selectedTabIndex)
                } else {
                    InlineLandscapeTabBar(
                        tabLayoutState = state.tabLayoutState,
                        tabs = state.tabs,
                        selectedTabIndex = state.selectedTabIndex,
                        onReorder = { from, to -> mainActivityManager.reorderTabs(from, to) },
                        onAddNewTab = { mainActivityManager.addDefaultOrOverriddenNewTab() }
                    )
                }
                if (isFilesTab) {
                    FilesTabSubtitleIndicator(activeTab as FilesTab)
                } else {
                    AnimatedVisibility(visible = subtitle.isNotEmpty()) {
                        Text(
                            modifier = Modifier.alpha(0.7f),
                            text = subtitle,
                            fontSize = 10.sp,
                            maxLines = 1,
                            lineHeight = 16.sp,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        } else {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                if (disableTabBar) {
                    SingleRowTabsControl(state.tabs, state.selectedTabIndex)
                } else {
                    Text(
                        text = title,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        lineHeight = 20.sp,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (isFilesTab) {
                    FilesTabSubtitleIndicator(activeTab as FilesTab)
                } else {
                    AnimatedVisibility(visible = subtitle.isNotEmpty()) {
                        Text(
                            modifier = Modifier.alpha(0.7f),
                            text = subtitle,
                            fontSize = 10.sp,
                            maxLines = 1,
                            lineHeight = 16.sp,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        if (isFilesTab) {
            val filesTab = activeTab as FilesTab
            val enableBottomToolbar = globalClass.preferencesManager.enableBottomToolbar && !isLandscape

            if (!enableBottomToolbar) {
                // Dedicated search button
                TooltipIconButton(
                    tooltip = stringResource(R.string.search),
                    onClick = { filesTab.toggleSearchPenal(true) },
                    modifier = buttonModifier
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Search,
                        contentDescription = stringResource(R.string.search)
                    )
                }

                // Normal plus create button (moves into overflow when disableTabBar is true)
                if (!disableTabBar) {
                    TooltipIconButton(
                        tooltip = stringResource(R.string.create_new),
                        onClick = { filesTab.toggleCreateNewFileDialog(true) },
                        modifier = buttonModifier
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = stringResource(R.string.create)
                        )
                    }
                }
            }

            if (filesTab.activeFolder !is VirtualFileHolder) {
                val prefs = globalClass.preferencesManager
                TooltipIconButton(
                    tooltip = stringResource(R.string.show_directory_type_filter_ribbon),
                    onClick = {
                        prefs.showDirectoryTypeFilterRibbon = !prefs.showDirectoryTypeFilterRibbon
                    },
                    modifier = buttonModifier
                ) {
                    Icon(
                        imageVector = if (prefs.showDirectoryTypeFilterRibbon) {
                            Icons.Rounded.FilterList
                        } else {
                            Icons.Rounded.FilterListOff
                        },
                        contentDescription = stringResource(R.string.show_directory_type_filter_ribbon),
                        tint = if (prefs.showDirectoryTypeFilterRibbon) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }

            // Keep quick-sort popup in the top toolbar at all times
            QuickSortButton(filesTab, modifier = buttonModifier)

            // Overflow menu
            MoreOptionsButton(modifier = buttonModifier)
        } else {
            if (activeTab is HomeTab && globalClass.preferencesManager.hideHomeSearchBar) {
                TooltipIconButton(
                    tooltip = stringResource(R.string.search),
                    onClick = { activeTab.showSearchDialog = true },
                    modifier = buttonModifier
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Search,
                        contentDescription = stringResource(R.string.search)
                    )
                }
            }
            MoreOptionsButton(modifier = buttonModifier)
        }
    }
}

@Composable
fun SingleRowTabsControl(
    tabs: List<Tab>,
    selectedTabIndex: Int
) {
    val mainActivityManager = globalClass.mainActivityManager
    var showTabDropdown by remember { mutableStateOf(false) }
    val currentTab = tabs.getOrNull(selectedTabIndex)
    val currentLabel = (currentTab as? FilesTab)?.tabViewLabel ?: currentTab?.header ?: ""
    val isCurrentCopyHere = (currentTab as? FilesTab)?.copyHereTargetFolder != null

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .padding(end = 4.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Box {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clickable { showTabDropdown = true }
                    .padding(horizontal = 6.dp, vertical = 4.dp)
            ) {
                if (isCurrentCopyHere) {
                    Icon(
                        imageVector = Icons.Rounded.ContentPaste,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(15.dp).padding(end = 3.dp)
                    )
                }
                Text(
                    text = currentLabel,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 140.dp)
                )
                Icon(
                    imageVector = Icons.Rounded.ArrowDropDown,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
            }

            DropdownMenu(
                expanded = showTabDropdown,
                onDismissRequest = { showTabDropdown = false },
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
                tabs.forEachIndexed { index, tabItem ->
                    val isSelected = index == selectedTabIndex
                    val tabLabel = (tabItem as? FilesTab)?.tabViewLabel ?: tabItem.header
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = tabLabel,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Unspecified
                            )
                        },
                        onClick = {
                            mainActivityManager.selectTabAt(index, true)
                            showTabDropdown = false
                        },
                        leadingIcon = if (isSelected) {
                            {
                                Icon(
                                    imageVector = Icons.Rounded.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        } else null,
                        trailingIcon = if (tabs.size > 1) {
                            {
                                IconButton(
                                    onClick = {
                                        mainActivityManager.removeTabAt(index)
                                        // Keep dropdown open if there are still tabs
                                        if (tabs.size <= 1) showTabDropdown = false
                                    },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Close,
                                        contentDescription = "Close tab",
                                        modifier = Modifier.size(14.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                    )
                                }
                            }
                        } else null
                    )
                }
            }
        }

        IconButton(
            modifier = Modifier.size(28.dp),
            onClick = { mainActivityManager.addDefaultOrOverriddenNewTab() }
        ) {
            Icon(
                imageVector = Icons.Rounded.Add,
                contentDescription = "New Tab",
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun MoreOptionsButton(modifier: Modifier = Modifier) {
    val mainActivityManager = globalClass.mainActivityManager
    val context = LocalContext.current

    var showOptionsMenu by remember { mutableStateOf(false) }

    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = { PlainTooltip { Text(stringResource(R.string.more_options)) } },
        state = rememberTooltipState()
    ) {
    Box {
        IconButton(
            onClick = { showOptionsMenu = true },
            modifier = modifier
        ) {
            Icon(imageVector = Icons.Rounded.MoreVert, contentDescription = null)
        }

        DropdownMenu(
            expanded = showOptionsMenu,
            onDismissRequest = { showOptionsMenu = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            val state by mainActivityManager.state.collectAsState()
            val activeTab = mainActivityManager.getActiveTab()

            val taskManager = globalClass.taskManager
            val version = taskManager.pendingTasksVersion
            val mostRecentCopyTask = remember(version) {
                taskManager.pendingTasks.filterIsInstance<CopyTask>().lastOrNull()
            }

            if (state.hasNewUpdate && globalClass.preferencesManager.hideToolbar) {
                DropdownMenuItem(
                    text = {
                        Text(text = stringResource(R.string.new_update_available))
                    },
                    onClick = {
                        mainActivityManager.toggleAppInfoDialog(true)
                        showOptionsMenu = false
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = PrismIcons.Upgrade,
                            contentDescription = null
                        )
                    }
                )
            }

            if (activeTab is FilesTab) {
                if (activeTab.canGoForward()) {
                    DropdownMenuItem(
                        text = { Text(text = stringResource(R.string.forward)) },
                        onClick = {
                            activeTab.goForward()
                            showOptionsMenu = false
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    )
                }

                val isRecycleBin = activeTab.activeFolder is LocalFileHolder &&
                        ((activeTab.activeFolder as LocalFileHolder).hasParent(globalClass.recycleBinDir) ||
                                activeTab.activeFolder.uniquePath == globalClass.recycleBinDir.uniquePath)

                val isAllSelected = activeTab.activeFolderContent.isNotEmpty() &&
                        activeTab.selectedFiles.size == activeTab.activeFolderContent.size
                DropdownMenuItem(
                    text = {
                        Text(text = stringResource(if (isAllSelected) R.string.deselect_all else R.string.select_all))
                    },
                    onClick = {
                        if (isAllSelected) {
                            activeTab.unselectAllFiles()
                        } else {
                            activeTab.unselectAllFiles(false)
                            activeTab.activeFolderContent.forEach {
                                activeTab.selectedFiles[it.uniquePath] = it
                            }
                            activeTab.quickReloadFiles()
                        }
                        showOptionsMenu = false
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.SelectAll,
                            contentDescription = null
                        )
                    }
                )

                if (isRecycleBin) {
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = stringResource(R.string.empty),
                                color = MaterialTheme.colorScheme.error
                            )
                        },
                        onClick = {
                            activeTab.unselectAllFiles(false)
                            activeTab.activeFolderContent.forEach {
                                activeTab.selectedFiles[it.uniquePath] = it
                            }
                            activeTab.quickReloadFiles()
                            activeTab.toggleDeleteConfirmationDialog(true)
                            showOptionsMenu = false
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Rounded.DeleteSweep,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    )
                }

                if (mostRecentCopyTask != null) {
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = stringResource(
                                    if (mostRecentCopyTask.deleteSourceFiles) R.string.move_here else R.string.paste_here
                                )
                            )
                        },
                        onClick = {
                            taskManager.runTask(
                                mostRecentCopyTask.id,
                                CopyTaskParameters(activeTab.activeFolder)
                            )
                            activeTab.unselectAllFiles()
                            showOptionsMenu = false
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Rounded.ContentPaste,
                                contentDescription = null
                            )
                        }
                    )
                }

                // If single row navigation mode is active, Create New File moves into overflow
                val isLandscapeMenu = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
                if ((globalClass.preferencesManager.disableTabBar || isLandscapeMenu) && !isRecycleBin) {
                    DropdownMenuItem(
                        text = { Text(text = stringResource(R.string.create_new)) },
                        onClick = {
                            activeTab.toggleCreateNewFileDialog(true)
                            showOptionsMenu = false
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Rounded.Add,
                                contentDescription = null
                            )
                        }
                    )
                }

                val bottomToolbarVisible = globalClass.preferencesManager.enableBottomToolbar
                val inSelection = activeTab.selectedFiles.isNotEmpty()
                val showTasksBookmarksInOverflow = !bottomToolbarVisible || inSelection || isRecycleBin
                if (showTasksBookmarksInOverflow) {
                    DropdownMenuItem(
                        text = { Text(text = stringResource(R.string.tasks)) },
                        onClick = {
                            activeTab.toggleTasksPanel(true)
                            showOptionsMenu = false
                        },
                        leadingIcon = {
                            Icon(imageVector = Icons.Rounded.AddTask, contentDescription = null)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(text = stringResource(R.string.bookmarks)) },
                        onClick = {
                            activeTab.toggleBookmarksDialog(true)
                            showOptionsMenu = false
                        },
                        leadingIcon = {
                            Icon(imageVector = Icons.Rounded.Bookmark, contentDescription = null)
                        }
                    )
                }

                HorizontalDivider()

                val showHiddenFiles = globalClass.preferencesManager.showHiddenFiles
                DropdownMenuItem(
                    text = {
                        Text(
                            text = stringResource(R.string.show_hidden_files)
                        )
                    },
                    onClick = {
                        globalClass.preferencesManager.showHiddenFiles =
                            !showHiddenFiles
                        activeTab.onTabResumed()
                        showOptionsMenu = false
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.RemoveRedEye,
                            contentDescription = null
                        )
                    },
                    trailingIcon = {
                        Icon(
                            imageVector = if (showHiddenFiles)
                                Icons.Rounded.CheckBox else Icons.Rounded.CheckBoxOutlineBlank,
                            contentDescription = null
                        )
                    }
                )

                DropdownMenuItem(
                    text = {
                        Text(text = stringResource(R.string.view_type))
                    },
                    onClick = {
                        activeTab.toggleViewConfigDialog(true)
                        showOptionsMenu = false
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.ViewComfy,
                            contentDescription = null
                        )
                    }
                )

                HorizontalDivider()
            }

            if (activeTab is HomeTab) {
                DropdownMenuItem(
                    text = { Text(text = stringResource(R.string.customize_home_tab)) },
                    onClick = {
                        activeTab.showCustomizeHomeTabDialog = true
                        showOptionsMenu = false
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.EditAttributes,
                            contentDescription = null
                        )
                    }
                )
            }

            DropdownMenuItem(
                text = { Text(text = stringResource(R.string.manage_startup_tabs)) },
                onClick = {
                    mainActivityManager.toggleStartupTabsDialog(true)
                    showOptionsMenu = false
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.AutoAwesomeMotion,
                        contentDescription = null
                    )
                }
            )

            DropdownMenuItem(
                text = { Text(text = stringResource(R.string.preferences)) },
                onClick = {
                    context.startActivity(Intent(context, PreferencesActivity::class.java))
                    showOptionsMenu = false
                },
                leadingIcon = {
                    Icon(imageVector = Icons.Rounded.Settings, contentDescription = null)
                }
            )

            if (globalClass.preferencesManager.hideToolbar) {
                DropdownMenuItem(
                    text = { Text(text = stringResource(R.string.about)) },
                    onClick = {
                        mainActivityManager.toggleAppInfoDialog(true)
                        showOptionsMenu = false
                    },
                    leadingIcon = {
                        Icon(imageVector = Icons.Rounded.Info, contentDescription = null)
                    }
                )
            }
        }
    }
    } // end TooltipBox
}

private data class SortMenuItem(
    val method: Int,
    val reverse: Boolean,
    val titleRes: Int,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
)

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun QuickSortButton(tab: FilesTab, modifier: Modifier = Modifier) {
    val prefs = globalClass.preferencesManager
    var showMenu by remember { mutableStateOf(false) }
    var showOptionsDialog by remember { mutableStateOf(false) }

    LaunchedEffect(
        tab.activeFolder.uniquePath,
        prefs.defaultSortMethod,
        prefs.reverse,
        prefs.showFoldersFirst
    ) {
        tab.updateSortingConfig()
    }

    val sortingPrefs = tab.sortingPrefs
    val currentMethod = sortingPrefs.sortMethod
    val currentReverse = sortingPrefs.reverseSorting
    val currentFoldersFirst = sortingPrefs.showFoldersFirst
    val applyForThisFolder = sortingPrefs.applyForThisFileOnly

    val currentSortIcon = remember(currentMethod, currentReverse) {
        getSortIcon(currentMethod, currentReverse)
    }

    val sortItems = remember {
        listOf(
            SortMenuItem(SortingMethod.SORT_BY_NAME, false, R.string.name_a_z, PrismIcons.SortNameAscending),
            SortMenuItem(SortingMethod.SORT_BY_NAME, true, R.string.name_z_a, PrismIcons.SortNameDescending),
            SortMenuItem(SortingMethod.SORT_BY_SIZE, false, R.string.size_smaller, PrismIcons.SortSizeSmaller),
            SortMenuItem(SortingMethod.SORT_BY_SIZE, true, R.string.size_larger, PrismIcons.SortSizeLarger),
            SortMenuItem(SortingMethod.SORT_BY_DATE, true, R.string.date_older, Icons.Rounded.History),
            SortMenuItem(SortingMethod.SORT_BY_DATE, false, R.string.date_newer, Icons.Rounded.Update),
            SortMenuItem(SortingMethod.SORT_BY_TYPE, false, R.string.type_ascending, PrismIcons.SortTypeAscending),
            SortMenuItem(SortingMethod.SORT_BY_TYPE, true, R.string.type_descending, PrismIcons.SortTypeDescending),
            SortMenuItem(SortingMethod.SORT_BY_SHUFFLED, false, R.string.shuffled, Icons.Default.Shuffle)
        )
    }

    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = { PlainTooltip { Text(stringResource(R.string.sort)) } },
        state = rememberTooltipState()
    ) {
        Box {
            IconButton(
                onClick = {
                    tab.updateSortingConfig()
                    showMenu = true
                },
                modifier = modifier
            ) {
                Icon(
                    imageVector = currentSortIcon,
                    contentDescription = stringResource(R.string.sort)
                )
            }

            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false },
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
                Text(
                    text = stringResource(R.string.sort_by_first),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )

                sortItems.forEach { item ->
                    val isCurrent = currentMethod == item.method &&
                            (item.method == SortingMethod.SORT_BY_SHUFFLED || currentReverse == item.reverse)

                    DropdownMenuItem(
                        text = {
                            Text(
                                text = stringResource(item.titleRes),
                                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        },
                        onClick = {
                            val previousPrefs = tab.sortingPrefs
                            if (applyForThisFolder) {
                                val newPrefs = FileSortingPrefs(
                                    sortMethod = item.method,
                                    showFoldersFirst = currentFoldersFirst,
                                    reverseSorting = item.reverse,
                                    applyForThisFileOnly = true
                                )
                                prefs.setSortingPrefsFor(tab.activeFolder, newPrefs)
                            } else {
                                prefs.defaultSortMethod = item.method
                                prefs.reverse = item.reverse
                            }
                            tab.applySortingChange(previousPrefs)
                            showMenu = false
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = item.icon,
                                contentDescription = null,
                                tint = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        trailingIcon = {
                            if (isCurrent) {
                                Icon(
                                    imageVector = Icons.Rounded.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    )
                }

                HorizontalDivider()

                // Folders First Toggle
                DropdownMenuItem(
                    text = { Text(text = stringResource(R.string.folders_first)) },
                    onClick = {
                        val previousPrefs = tab.sortingPrefs
                        val newFoldersFirst = !currentFoldersFirst
                        if (applyForThisFolder) {
                            val newPrefs = FileSortingPrefs(
                                sortMethod = currentMethod,
                                showFoldersFirst = newFoldersFirst,
                                reverseSorting = currentReverse,
                                applyForThisFileOnly = true
                            )
                            prefs.setSortingPrefsFor(tab.activeFolder, newPrefs)
                        } else {
                            prefs.showFoldersFirst = newFoldersFirst
                        }
                        tab.applySortingChange(previousPrefs)
                    },
                    leadingIcon = {
                        Icon(imageVector = Icons.Rounded.Folder, contentDescription = null)
                    },
                    trailingIcon = {
                        Icon(
                            imageVector = if (currentFoldersFirst) Icons.Rounded.CheckBox else Icons.Rounded.CheckBoxOutlineBlank,
                            contentDescription = null
                        )
                    }
                )

                // Apply to this folder only Toggle
                DropdownMenuItem(
                    text = { Text(text = stringResource(R.string.apply_to_this_folder_only)) },
                    onClick = {
                        val previousPrefs = tab.sortingPrefs
                        val newApply = !applyForThisFolder
                        if (newApply) {
                            val newPrefs = FileSortingPrefs(
                                sortMethod = currentMethod,
                                showFoldersFirst = currentFoldersFirst,
                                reverseSorting = currentReverse,
                                applyForThisFileOnly = true
                            )
                            prefs.setSortingPrefsFor(tab.activeFolder, newPrefs)
                        } else {
                            prefs.deleteSortingPrefsFor(tab.activeFolder)
                        }
                        tab.applySortingChange(previousPrefs)
                    },
                    leadingIcon = {
                        Icon(imageVector = Icons.Rounded.FolderSpecial, contentDescription = null)
                    },
                    trailingIcon = {
                        Icon(
                            imageVector = if (applyForThisFolder) Icons.Rounded.CheckBox else Icons.Rounded.CheckBoxOutlineBlank,
                            contentDescription = null
                        )
                    }
                )

                HorizontalDivider()

                // Options...
                DropdownMenuItem(
                    text = {
                        Text(
                            text = stringResource(R.string.options_ellipsis),
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium
                        )
                    },
                    onClick = {
                        showMenu = false
                        showOptionsDialog = true
                    }
                )
            }
        }
    } // end TooltipBox

    if (showOptionsDialog) {
        FileSortingMenuDialog(
            show = true,
            tab = tab,
            onDismissRequest = {
                showOptionsDialog = false
            }
        )
    }
}

@Composable
fun FilesTabSubtitleIndicator(filesTab: FilesTab) {
    val selectedCount = filesTab.selectedFilesCount.coerceAtLeast(filesTab.selectedFiles.size)
    if (selectedCount > 0) {
        val selectedFolders = filesTab.selectedFiles.values.count { it.isFolder }
        val selectedFilesCount = filesTab.selectedFiles.values.count { !it.isFolder }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.alpha(0.9f)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Folder,
                    contentDescription = null,
                    modifier = Modifier.size(9.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "$selectedFolders",
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.InsertDriveFile,
                    contentDescription = null,
                    modifier = Modifier.size(9.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "$selectedFilesCount",
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = "•",
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                )
                Text(
                    text = filesTab.selectedFilesTotalSize.toFormattedSize() +
                            if (filesTab.isCalculatingSelectedSize) "…" else "",
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
            }
        }
    } else if (filesTab.foldersCount == 0 && filesTab.filesCount == 0) {
        Icon(
            imageVector = Icons.Rounded.Block,
            contentDescription = null,
            modifier = Modifier.size(10.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        )
    } else if (globalClass.preferencesManager.deepEmptyFolderCheck &&
        filesTab.foldersCount > 0 && filesTab.filesCount == 0 &&
        filesTab.activeFolder is LocalFileHolder &&
        com.techflyers.compose.file.explorer.screen.main.tab.files.misc.FolderHierarchyChecker.isFolderEmptyWithin((filesTab.activeFolder as LocalFileHolder).file)
    ) {
        Icon(
            imageVector = Icons.Rounded.RadioButtonUnchecked,
            contentDescription = null,
            modifier = Modifier.size(10.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        )
    } else {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.alpha(0.7f)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Folder,
                    contentDescription = null,
                    modifier = Modifier.size(9.dp)
                )
                Text(
                    text = "${filesTab.foldersCount}",
                    fontSize = 11.sp,
                    lineHeight = 16.sp
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.InsertDriveFile,
                    contentDescription = null,
                    modifier = Modifier.size(9.dp)
                )
                Text(
                    text = "${filesTab.filesCount}",
                    fontSize = 11.sp,
                    lineHeight = 16.sp
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun InlineLandscapeTabBar(
    tabLayoutState: LazyListState,
    tabs: List<Tab>,
    selectedTabIndex: Int,
    onReorder: (Int, Int) -> Unit,
    onAddNewTab: () -> Unit,
    modifier: Modifier = Modifier
) {
    val selectedTabBackgroundColor = MaterialTheme.colorScheme.surfaceContainerLowest
    val unselectedTabBackgroundColor =
        MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.5f)
    val draggedTabBackgroundColor = MaterialTheme.colorScheme.primary
    val draggedTabTextColor = MaterialTheme.colorScheme.surfaceContainerLowest
    val tabShape = RoundedCornerShape(8.dp)

    val mainActivityManager = globalClass.mainActivityManager
    var from by remember { mutableIntStateOf(-1) }
    var to by remember { mutableIntStateOf(-1) }
    var draggedItem by remember { mutableIntStateOf(-1) }
    var isDraggingAnyTab by remember { mutableStateOf(false) }
    var lastDragFinishedTime by remember { mutableLongStateOf(0L) }
    val list = remember { mutableStateListOf<Int>() }
    var showClosedTabsHistoryDialog by remember { mutableStateOf(false) }

    val reorderableLazyListState = rememberReorderableLazyListState(tabLayoutState) { old, new ->
        if (draggedItem > -1 || isDraggingAnyTab) {
            if (from < 0) from = old.index
            to = new.index
            list.add(new.index, list.removeAt(old.index))
        }
    }

    LaunchedEffect(draggedItem, isDraggingAnyTab) {
        if (draggedItem < 0 && !isDraggingAnyTab && from > -1 && to > -1) {
            lastDragFinishedTime = android.os.SystemClock.uptimeMillis()
            onReorder(from, to)
            from = -1
            to = -1
        }
    }

    if (!isDraggingAnyTab && draggedItem < 0) {
        if (list.size == tabs.size) {
            tabs.forEachIndexed { idx, tab ->
                if (list[idx] != tab.slotId) {
                    list[idx] = tab.slotId
                }
            }
        } else {
            list.clear()
            list.addAll(tabs.map { it.slotId })
        }
    }

    val plusOnLeft = globalClass.preferencesManager.tabBarPlusButtonOnLeft

    Row(
        modifier = modifier.height(34.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (plusOnLeft) {
            IconButton(
                onClick = onAddNewTab,
                modifier = Modifier.size(30.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = "New Tab",
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        LazyRow(
            modifier = Modifier
                .weight(1f, fill = false)
                .heightIn(max = 32.dp),
            state = tabLayoutState,
            verticalAlignment = Alignment.CenterVertically
        ) {
            itemsIndexed(list, key = { _, item -> item }) { index, slotId ->
                tabs.find { it.slotId == slotId }?.let { tab ->
                    ReorderableItem(reorderableLazyListState, key = tab.slotId) { isDragged ->
                        if (isDragged && draggedItem != slotId) {
                            draggedItem = slotId
                            isDraggingAnyTab = true
                        } else if (!isDragged && draggedItem == slotId) {
                            draggedItem = -1
                            isDraggingAnyTab = false
                            lastDragFinishedTime = android.os.SystemClock.uptimeMillis()
                        }

                        val isSelected = selectedTabIndex == index
                        val isCopyHereDestination = (tab as? FilesTab)?.copyHereTargetFolder != null
                        val isDragHovered = mainActivityManager.activeDragSourceTab != null &&
                                mainActivityManager.hoveredTabDropIndex == index

                        androidx.compose.runtime.DisposableEffect(index, tab.id) {
                            onDispose {
                                mainActivityManager.unregisterTabDropTarget(index)
                            }
                        }

                        val backgroundColorAnim = animateColorAsState(
                            targetValue =
                                if (isDragged || isDragHovered) {
                                    draggedTabBackgroundColor
                                } else if (isCopyHereDestination) {
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = if (isSelected) 0.9f else 0.55f)
                                } else if (isSelected) {
                                    selectedTabBackgroundColor
                                } else {
                                    unselectedTabBackgroundColor
                                },
                            label = "landscapeTabBg"
                        )

                        val textColorAnim = animateColorAsState(
                            targetValue =
                                if (isDragged || isDragHovered) {
                                    draggedTabTextColor
                                } else if (isCopyHereDestination) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    Color.Unspecified
                                },
                            label = "landscapeTabText"
                        )

                        val alphaAnim = animateFloatAsState(
                            targetValue = if ((isSelected || isCopyHereDestination || isDragHovered) && draggedItem == -1) 1f else 0.55f,
                            label = "landscapeTabAlpha"
                        )

                        var showTabHeaderMenu by remember(tab.id) {
                            mutableStateOf(false)
                        }

                        LaunchedEffect(isDragged, isDraggingAnyTab, draggedItem) {
                            if (isDragged || isDraggingAnyTab || draggedItem != -1) {
                                showTabHeaderMenu = false
                            }
                        }

                        Row(
                            modifier = Modifier
                                .defaultMinSize(minWidth = 76.dp)
                                .fillMaxHeight()
                                .padding(end = 4.dp)
                                .onGloballyPositioned { coords ->
                                    mainActivityManager.registerTabDropTarget(index, coords)
                                }
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
                                        if (isDragged || isDraggingAnyTab || draggedItem != -1 ||
                                            (currentTime - lastDragFinishedTime) < 400L
                                        ) {
                                            showTabHeaderMenu = false
                                            return@combinedClickable
                                        }
                                        if (!isSelected) {
                                            mainActivityManager.selectTabAt(index)
                                        } else {
                                            showTabHeaderMenu = true
                                        }
                                    },
                                    onLongClick = {
                                        if (!isDragged && !isDraggingAnyTab && draggedItem == -1) {
                                            if (!isSelected) {
                                                mainActivityManager.selectTabAt(index, true)
                                            }
                                            showTabHeaderMenu = true
                                        }
                                    }
                                )
                                .padding(horizontal = 12.dp)
                                .longPressDraggableHandle(
                                    onDragStarted = {
                                        isDraggingAnyTab = true
                                        showTabHeaderMenu = false
                                    },
                                    onDragStopped = {
                                        val didReorder = from > -1 && to > -1 && from != to
                                        isDraggingAnyTab = false
                                        lastDragFinishedTime = android.os.SystemClock.uptimeMillis()
                                        if (!didReorder) {
                                            if (!isSelected) {
                                                mainActivityManager.selectTabAt(index, true)
                                            }
                                            showTabHeaderMenu = true
                                        } else {
                                            showTabHeaderMenu = false
                                        }
                                    }
                                )
                                .then(
                                    if (!globalClass.preferencesManager.disableNavigationGestures && !tab.isPinned) {
                                        Modifier.detectVerticalSwipe(
                                            onSwipeDown = {
                                                mainActivityManager.removeTabAt(index)
                                            },
                                            onSwipeUp = {
                                                mainActivityManager.removeTabAt(index)
                                            }
                                        )
                                    } else Modifier
                                ),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            if (tab.isPinned) {
                                Icon(
                                    imageVector = Icons.Rounded.PushPin,
                                    contentDescription = "Pinned tab",
                                    modifier = Modifier
                                        .size(13.dp)
                                        .padding(end = 2.dp)
                                        .alpha(alphaAnim.value),
                                    tint = if (isDragged) draggedTabTextColor else MaterialTheme.colorScheme.primary
                                )
                                Space(2.dp)
                            }
                            if (isCopyHereDestination) {
                                Icon(
                                    imageVector = Icons.Rounded.MoveToInbox,
                                    contentDescription = "Copy here destination",
                                    modifier = Modifier
                                        .size(14.dp)
                                        .alpha(alphaAnim.value),
                                    tint = if (isDragged) draggedTabTextColor else MaterialTheme.colorScheme.primary
                                )
                                Space(3.dp)
                            }
                            Text(
                                modifier = Modifier.alpha(alphaAnim.value),
                                text = tab.header,
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                fontWeight = if (isSelected || isCopyHereDestination) FontWeight.SemiBold else FontWeight.Normal,
                                color = textColorAnim.value
                            )
                            if (showTabHeaderMenu && !isDragged && !isDraggingAnyTab && draggedItem == -1) {
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

        if (!plusOnLeft) {
            IconButton(
                onClick = onAddNewTab,
                modifier = Modifier.size(30.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = "New Tab",
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }

    if (showClosedTabsHistoryDialog) {
        ClosedTabsHistoryDialog(onDismiss = { showClosedTabsHistoryDialog = false })
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun InlineToolbarBreadcrumb(
    tab: FilesTab,
    modifier: Modifier = Modifier
) {
    val highlightedColor = MaterialTheme.colorScheme.primary

    if (tab.showCategories || tab.activeFolder is VirtualFileHolder) {
        Column(modifier = modifier) {
            if (tab.showCategories) {
                com.techflyers.compose.file.explorer.screen.main.tab.files.ui.CategoriesRow(tab)
            }
            com.techflyers.compose.file.explorer.screen.main.tab.files.ui.FormatRibbon(tab, compact = true)
        }
        return
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
                return Offset(available.x, 0f)
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                return Velocity(available.x, 0f)
            }
        }
    }

    Row(
        modifier = modifier.padding(horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val isHomeCurrentFolder = tab.homeDir.uniquePath == tab.activeFolder.uniquePath
        val isHomeHovered = tab.dragDropSession?.hoveredTargetDirectory == tab.homeDir.uniquePath
        val isHomeValid = tab.dragDropSession?.isValidDropTarget(tab.homeDir, tab.activeFolder) == true

        Box(
            modifier = Modifier
                .size(28.dp)
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
                    onClick = { com.techflyers.compose.file.explorer.screen.main.tab.files.ui.navigateBreadcrumbHomeTap() },
                    onLongClick = { com.techflyers.compose.file.explorer.screen.main.tab.files.ui.navigateBreadcrumbHomeLongPress(tab) }
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.Home,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
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
                val isDropTarget = !isCurrentFolder
                val isHovered = tab.dragDropSession?.hoveredTargetDirectory == item.uniquePath
                val isValidTarget = tab.dragDropSession?.isValidDropTarget(item, tab.activeFolder) == true

                DisposableEffect(item.uniquePath) {
                    onDispose {
                        tab.unregisterDropTarget(item.uniquePath)
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        modifier = Modifier
                            .size(12.dp)
                            .alpha(if (isHistorySegment) 0.35f else 1f),
                        imageVector = Icons.Rounded.PlayArrow,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
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
                                        .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(8.dp))
                                        .border(1.5.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))
                                } else if (isHovered && !isValidTarget) {
                                    Modifier
                                        .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                        .border(1.5.dp, MaterialTheme.colorScheme.error, RoundedCornerShape(8.dp))
                                } else Modifier
                            )
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
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                                .alpha(
                                    when {
                                        isHighlighted -> 1f
                                        isHistorySegment -> 0.45f
                                        else -> 0.85f
                                    }
                                ),
                            text = item.displayName
                                .orIf(stringResource(id = R.string.internal_storage)) {
                                    item.uniquePath == Environment.getExternalStorageDirectory().absolutePath
                                }
                                .orIf(stringResource(id = R.string.root)) {
                                    item.uniquePath == File.separator
                                },
                            fontSize = 12.sp,
                            maxLines = 1,
                            fontWeight = if (isHighlighted) FontWeight.Medium else FontWeight.Normal,
                            color = if (isHighlighted) highlightedColor else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
