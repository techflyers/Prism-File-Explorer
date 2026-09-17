package com.techflyers.compose.file.explorer.screen.main.ui

import android.content.Intent
import android.content.res.Configuration
import android.os.Environment
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.techflyers.compose.file.explorer.common.icons.PrismIcons
import com.techflyers.compose.file.explorer.common.icons.Upgrade
import com.techflyers.compose.file.explorer.common.orIf
import com.techflyers.compose.file.explorer.common.showMsg
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
import java.io.File
import kotlin.math.max

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
            IconButton(
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
                val filesTab = activeTab as FilesTab
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

        // Inline breadcrumb bar in landscape mode (between title and action buttons)
        if (isLandscape && isFilesTab) {
            val filesTab = activeTab as FilesTab
            InlineToolbarBreadcrumb(
                tab = filesTab,
                modifier = Modifier.weight(1f)
            )
        }

        if (isFilesTab) {
            val filesTab = activeTab as FilesTab
            val enableBottomToolbar = globalClass.preferencesManager.enableBottomToolbar && !isLandscape

            if (!enableBottomToolbar) {
                // Dedicated search button
                IconButton(
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
                    IconButton(
                        onClick = { filesTab.toggleCreateNewFileDialog(true) },
                        modifier = buttonModifier
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = stringResource(R.string.create)
                        )
                    }
                }

                // Quick sort button
                QuickSortButton(filesTab, modifier = buttonModifier)
            }

            // Overflow menu
            MoreOptionsButton(modifier = buttonModifier)
        } else {
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

@Composable
fun MoreOptionsButton(modifier: Modifier = Modifier) {
    val mainActivityManager = globalClass.mainActivityManager
    val context = LocalContext.current

    var showOptionsMenu by remember { mutableStateOf(false) }

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
                if (globalClass.preferencesManager.disableTabBar && !isRecycleBin) {
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

                // Tasks panel
                DropdownMenuItem(
                    text = { Text(text = stringResource(R.string.tasks)) },
                    onClick = {
                        activeTab.toggleTasksPanel(true)
                        showOptionsMenu = false
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.AddTask,
                            contentDescription = null
                        )
                    }
                )

                // Bookmarks
                DropdownMenuItem(
                    text = { Text(text = stringResource(R.string.bookmarks)) },
                    onClick = {
                        activeTab.toggleBookmarksDialog(true)
                        showOptionsMenu = false
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.Bookmark,
                            contentDescription = null
                        )
                    }
                )

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
}

@Composable
fun QuickSortButton(tab: FilesTab, modifier: Modifier = Modifier) {
    val prefs = globalClass.preferencesManager
    var showMenu by remember { mutableStateOf(false) }

    // Local observable state — initialized from prefs each time menu opens, updated on click
    var applyForThisFolder by remember { mutableStateOf(false) }
    var currentMethod by remember { mutableStateOf(prefs.defaultSortMethod) }
    var currentReverse by remember { mutableStateOf(prefs.reverse) }
    var currentFoldersFirst by remember { mutableStateOf(prefs.showFoldersFirst) }

    // Helper: load latest values from prefs into local state
    fun loadFromPrefs() {
        val specific = prefs.getSortingPrefsFor(tab.activeFolder)
        applyForThisFolder = specific.applyForThisFileOnly
        if (applyForThisFolder) {
            currentMethod = specific.sortMethod
            currentReverse = specific.reverseSorting
            currentFoldersFirst = specific.showFoldersFirst
        } else {
            currentMethod = prefs.defaultSortMethod
            currentReverse = prefs.reverse
            currentFoldersFirst = prefs.showFoldersFirst
        }
    }

    val sortOptions = listOf(
        Triple(
            SortingMethod.SORT_BY_NAME,
            if (currentReverse) stringResource(R.string.name_z_a) else stringResource(R.string.name_a_z),
            Icons.Rounded.SortByAlpha
        ),
        Triple(
            SortingMethod.SORT_BY_DATE,
            if (currentReverse) stringResource(R.string.date_older) else stringResource(R.string.date_newer),
            Icons.Rounded.DateRange
        ),
        Triple(
            SortingMethod.SORT_BY_SIZE,
            if (currentReverse) stringResource(R.string.size_larger) else stringResource(R.string.size_smaller),
            Icons.AutoMirrored.Rounded.Sort
        ),
        Triple(
            SortingMethod.SORT_BY_TYPE,
            stringResource(R.string.type),
            Icons.AutoMirrored.Rounded.InsertDriveFile
        )
    )

    Box {
        IconButton(
            onClick = {
                loadFromPrefs()   // always fresh when menu opens
                showMenu = true
            },
            modifier = modifier
        ) {
            Icon(
                imageVector = Icons.Rounded.SortByAlpha,
                contentDescription = stringResource(R.string.sort)
            )
        }

        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            Text(
                text = stringResource(R.string.sort_by),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            sortOptions.forEach { (method, label, icon) ->
                val isCurrent = currentMethod == method
                DropdownMenuItem(
                    text = { Text(text = label) },
                    onClick = {
                        currentMethod = method          // update local state immediately
                        if (applyForThisFolder) {
                            prefs.setSortingPrefsFor(
                                tab.activeFolder,
                                FileSortingPrefs(
                                    sortMethod = method,
                                    showFoldersFirst = currentFoldersFirst,
                                    reverseSorting = currentReverse,
                                    applyForThisFileOnly = true
                                )
                            )
                        } else {
                            prefs.defaultSortMethod = method
                        }
                        tab.openFolder(tab.activeFolder, rememberListState = false)
                    },
                    leadingIcon = {
                        Icon(imageVector = icon, contentDescription = null)
                    },
                    trailingIcon = {
                        if (isCurrent) {
                            Icon(imageVector = Icons.Rounded.Check, contentDescription = null)
                        }
                    }
                )
            }

            HorizontalDivider()

            // Direction Toggle
            DropdownMenuItem(
                text = { Text(text = stringResource(R.string.reverse)) },
                onClick = {
                    val newReverse = !currentReverse
                    currentReverse = newReverse         // update local state immediately
                    if (applyForThisFolder) {
                        prefs.setSortingPrefsFor(
                            tab.activeFolder,
                            FileSortingPrefs(
                                sortMethod = currentMethod,
                                showFoldersFirst = currentFoldersFirst,
                                reverseSorting = newReverse,
                                applyForThisFileOnly = true
                            )
                        )
                    } else {
                        prefs.reverse = newReverse
                    }
                    tab.openFolder(tab.activeFolder, rememberListState = false)
                },
                leadingIcon = {
                    Icon(
                        imageVector = if (currentReverse) Icons.Rounded.ArrowDownward else Icons.Rounded.ArrowUpward,
                        contentDescription = null
                    )
                },
                trailingIcon = {
                    Icon(
                        imageVector = if (currentReverse) Icons.Rounded.CheckBox else Icons.Rounded.CheckBoxOutlineBlank,
                        contentDescription = null
                    )
                }
            )

            // Folders First Toggle
            DropdownMenuItem(
                text = { Text(text = stringResource(R.string.folders_first)) },
                onClick = {
                    val newFoldersFirst = !currentFoldersFirst
                    currentFoldersFirst = newFoldersFirst  // update local state immediately
                    if (applyForThisFolder) {
                        prefs.setSortingPrefsFor(
                            tab.activeFolder,
                            FileSortingPrefs(
                                sortMethod = currentMethod,
                                showFoldersFirst = newFoldersFirst,
                                reverseSorting = currentReverse,
                                applyForThisFileOnly = true
                            )
                        )
                    } else {
                        prefs.showFoldersFirst = newFoldersFirst
                    }
                    tab.openFolder(tab.activeFolder, rememberListState = false)
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
                    val newApply = !applyForThisFolder
                    applyForThisFolder = newApply       // update local state immediately
                    if (newApply) {
                        prefs.setSortingPrefsFor(
                            tab.activeFolder,
                            FileSortingPrefs(
                                sortMethod = currentMethod,
                                showFoldersFirst = currentFoldersFirst,
                                reverseSorting = currentReverse,
                                applyForThisFileOnly = true
                            )
                        )
                    } else {
                        prefs.deleteSortingPrefsFor(tab.activeFolder)
                    }
                    tab.openFolder(tab.activeFolder, rememberListState = false)
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
        }
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
        // No breadcrumb for categories/virtual folders
        return
    }

    val animationScope = rememberCoroutineScope()

    LaunchedEffect(key1 = tab.highlightedPathSegment) {
        val index =
            tab.currentPathSegments.getIndexIf { uniquePath == tab.highlightedPathSegment.uniquePath }
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

            override suspend fun onPreFling(available: Velocity): Velocity {
                return Velocity(available.x, 0f)
            }
        }
    }

    Row(
        modifier = modifier.padding(horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = { tab.openFolder(tab.homeDir, false) },
            modifier = Modifier.size(28.dp)
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
            itemsIndexed(tab.currentPathSegments, key = { _, it -> it.uid }) { _, item ->
                val isHighlighted = item.uniquePath == tab.highlightedPathSegment.uniquePath
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        modifier = Modifier.size(12.dp),
                        imageVector = Icons.Rounded.PlayArrow,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                    Text(
                        modifier = Modifier
                            .clip(CircleShape)
                            .combinedClickable(
                                onClick = {
                                    tab.openFolder(
                                        item = item,
                                        rememberSelectedFiles = true,
                                    )
                                },
                                onLongClick = {
                                    item.uniquePath.copyToClipboard()
                                    showMsg(R.string.path_copied_to_clipboard)
                                }
                            )
                            .padding(horizontal = 4.dp, vertical = 2.dp),
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