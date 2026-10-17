package com.techflyers.compose.file.explorer.screen.main.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalOverscrollConfiguration
import androidx.compose.foundation.OverscrollConfiguration
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.techflyers.compose.file.explorer.App.Companion.globalClass
import com.techflyers.compose.file.explorer.R
import com.techflyers.compose.file.explorer.screen.main.tab.Tab
import com.techflyers.compose.file.explorer.screen.main.tab.home.HomeTab
import com.techflyers.compose.file.explorer.screen.main.tab.files.FilesTab
import com.techflyers.compose.file.explorer.screen.main.tab.files.holder.LocalFileHolder
import com.techflyers.compose.file.explorer.screen.main.tab.files.holder.VirtualFileHolder
import com.techflyers.compose.file.explorer.screen.main.tab.files.holder.RemoteFileHolder
import com.techflyers.compose.file.explorer.screen.main.tab.files.service.remote.NetworkConnectionModel
import com.techflyers.compose.file.explorer.screen.main.tab.files.service.remote.NetworkConnectionsService
import com.techflyers.compose.file.explorer.screen.main.tab.files.holder.StorageDevice
import com.techflyers.compose.file.explorer.screen.main.tab.files.provider.StorageProvider
import com.techflyers.compose.file.explorer.screen.main.tab.nfile_tools.*
import com.techflyers.compose.file.explorer.screen.preferences.PreferencesActivity
import com.techflyers.compose.file.explorer.common.toFormattedSize
import com.techflyers.compose.file.explorer.screen.main.tab.files.misc.StorageDeviceType.INTERNAL_STORAGE
import com.techflyers.compose.file.explorer.screen.main.tab.files.misc.StorageDeviceType.REMOTE_STORAGE
import com.techflyers.compose.file.explorer.screen.main.tab.files.misc.StorageDeviceType.ROOT
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.sp
import android.content.Intent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun NFileDrawerContent(
    drawerState: DrawerState,
    onNavigate: () -> Unit
) {
    val context = LocalContext.current
    val manager = globalClass.mainActivityManager
    val preferencesManager = globalClass.preferencesManager
    val scope = rememberCoroutineScope()

    val mainActivityState by manager.state.collectAsState()
    val storageList = mainActivityState.storageDevices
    var remoteConnections by remember {
        mutableStateOf(NetworkConnectionsService.getConnections(context))
    }
    var isRefreshing by remember { mutableStateOf(false) }
    val editingSections = remember { mutableStateMapOf<String, Boolean>() }

    val refreshDrawer: () -> Unit = {
        isRefreshing = true
        scope.launch {
            withContext(Dispatchers.IO) {
                val d1 = async { manager.updateStorageDevices() }
                val d2 = async { remoteConnections = NetworkConnectionsService.getConnections(context) }
                d1.await()
                d2.await()
            }
            delay(150)
            isRefreshing = false
        }
    }

    // Refresh connections & storage when drawer opens, and auto-update storage periodically while open
    LaunchedEffect(drawerState.isOpen) {
        if (drawerState.isOpen) {
            withContext(Dispatchers.IO) {
                remoteConnections = NetworkConnectionsService.getConnections(context)
                manager.updateStorageDevices()
            }
            while (isActive && drawerState.isOpen) {
                delay(3000)
                manager.updateStorageDevices()
            }
        }
    }

    fun toggleItemHidden(itemId: String) {
        val current = preferencesManager.drawerHiddenItems.toMutableSet()
        if (itemId in current) current.remove(itemId) else current.add(itemId)
        preferencesManager.drawerHiddenItems = current
    }

    fun moveSection(sectionId: String, delta: Int) {
        val currentOrder = preferencesManager.drawerSectionsOrder.toMutableList()
        val idx = currentOrder.indexOf(sectionId)
        if (idx == -1) return
        val targetIdx = (idx + delta).coerceIn(0, currentOrder.lastIndex)
        if (idx != targetIdx) {
            currentOrder.add(targetIdx, currentOrder.removeAt(idx))
            preferencesManager.drawerSectionsOrder = currentOrder
        }
    }

    ModalDrawerSheet(
        modifier = Modifier
            .fillMaxHeight()
            .width(320.dp)
    ) {
        val drawerScroll = rememberScrollState()
        val overscrollConfig = if (preferencesManager.disableSpringEffect) null else OverscrollConfiguration()

        val drawerContent = @Composable {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(drawerScroll)
            ) {
                val sectionOrder = preferencesManager.drawerSectionsOrder
                val hiddenItems = preferencesManager.drawerHiddenItems

                sectionOrder.forEachIndexed { sectionIndex, sectionId ->
                    val isEditMode = editingSections[sectionId] == true
                    val canMoveUp = sectionIndex > 0
                    val canMoveDown = sectionIndex < sectionOrder.lastIndex

                    when (sectionId) {
                "tabs" -> {
                if (preferencesManager.showTabsInDrawer) {
                    val tabs = mainActivityState.tabs
                    val tabsTitle = stringResource(R.string.tabs)
                    var isReorderingTabs by remember { mutableStateOf(false) }

                    CollapsibleDrawerSection(
                        sectionId = "tabs",
                        title = "$tabsTitle (${tabs.size})",
                        isEditMode = isEditMode,
                        onToggleEditMode = { editingSections["tabs"] = !isEditMode },
                        onMoveSectionUp = if (canMoveUp) ({ moveSection("tabs", -1) }) else null,
                        onMoveSectionDown = if (canMoveDown) ({ moveSection("tabs", 1) }) else null,
                        action = if (tabs.size > 1) {
                            {
                                IconButton(
                                    onClick = { isReorderingTabs = !isReorderingTabs },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.SwapVert,
                                        contentDescription = stringResource(R.string.rearrange_tabs),
                                        modifier = Modifier.size(18.dp),
                                        tint = if (isReorderingTabs) MaterialTheme.colorScheme.primary
                                               else MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                                    )
                                }
                            }
                        } else null
                    ) {
                        tabs.forEachIndexed { index, tab ->
                            val label = (tab as? FilesTab)?.tabViewLabel ?: tab.header
                            val isCopyHereActive = (tab as? FilesTab)?.copyHereTargetFolder != null
                            val isActive = index == mainActivityState.selectedTabIndex
                            DrawerTabItem(
                                label = label,
                                isActive = isActive,
                                isPinned = tab.isPinned,
                                isCopyHereDestination = isCopyHereActive,
                                index = index,
                                totalTabs = tabs.size,
                                isReorderMode = isReorderingTabs,
                                onSelect = {
                                    manager.selectTabAt(index, true)
                                    onNavigate()
                                },
                                onClose = {
                                    manager.removeTabAt(index)
                                },
                                onMoveUp = {
                                    if (index > 0) {
                                        manager.reorderTabs(index, index - 1)
                                    }
                                },
                                onMoveDown = {
                                    if (index < tabs.size - 1) {
                                        manager.reorderTabs(index, index + 1)
                                    }
                                }
                            )
                        }
                        val newTabItemId = "tabs_new_tab"
                        val isNewTabHidden = newTabItemId in hiddenItems
                        if (isEditMode || !isNewTabHidden) {
                            DrawerItem(
                                icon = Icons.Rounded.Add,
                                label = stringResource(R.string.new_tab),
                                tint = MaterialTheme.colorScheme.secondary,
                                textColor = MaterialTheme.colorScheme.secondary,
                                isEditMode = isEditMode,
                                isHiddenItem = isNewTabHidden,
                                onToggleVisibility = { toggleItemHidden(newTabItemId) }
                            ) {
                                manager.addDefaultOrOverriddenNewTab()
                                onNavigate()
                            }
                        }
                    }
                }
                }

                "bookmarks" -> {
                if (preferencesManager.showBookmarksInDrawer) {
                    var showAddBookmarkDialog by remember { mutableStateOf(false) }
                    val bookmarks = preferencesManager.bookmarks
                    val bookmarksTitle = stringResource(R.string.bookmarks)

                    if (showAddBookmarkDialog) {
                        val currentTabPath = (manager.getActiveTab() as? FilesTab)?.activeFolder?.uniquePath ?: ""
                        com.techflyers.compose.file.explorer.screen.main.tab.files.ui.dialog.AddCustomFolderDialog(
                            show = showAddBookmarkDialog,
                            initialPath = currentTabPath,
                            onDismissRequest = { showAddBookmarkDialog = false },
                            onConfirm = { path ->
                                preferencesManager.bookmarks = preferencesManager.bookmarks + path
                            }
                        )
                    }

                    CollapsibleDrawerSection(
                        sectionId = "bookmarks",
                        title = "$bookmarksTitle (${bookmarks.size})",
                        isEditMode = isEditMode,
                        onToggleEditMode = { editingSections["bookmarks"] = !isEditMode },
                        onMoveSectionUp = if (canMoveUp) ({ moveSection("bookmarks", -1) }) else null,
                        onMoveSectionDown = if (canMoveDown) ({ moveSection("bookmarks", 1) }) else null,
                        action = {
                            IconButton(
                                onClick = { showAddBookmarkDialog = true },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Add,
                                    contentDescription = stringResource(R.string.add_to_bookmarks),
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    ) {
                        if (bookmarks.isEmpty()) {
                            Text(
                                text = stringResource(R.string.no_bookmarks_available),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                            )
                        } else {
                            bookmarks.forEach { path ->
                                val itemId = "bookmark_$path"
                                val isHidden = itemId in hiddenItems
                                if (isEditMode || !isHidden) {
                                    val file = File(path)
                                    DrawerItem(
                                        icon = Icons.Rounded.Bookmark,
                                        label = file.name.ifEmpty { path },
                                        isEditMode = isEditMode,
                                        isHiddenItem = isHidden,
                                        onToggleVisibility = { toggleItemHidden(itemId) }
                                    ) {
                                        manager.replaceCurrentTabWith(
                                            FilesTab(LocalFileHolder(file)),
                                            keepCurrentTabAsParent = true
                                        )
                                        onNavigate()
                                    }
                                }
                            }
                        }
                    }
                }
                }

                "navigation" -> {
                CollapsibleDrawerSection(
                    sectionId = "navigation",
                    title = "Navigation",
                    isEditMode = isEditMode,
                    onToggleEditMode = { editingSections["navigation"] = !isEditMode },
                    onMoveSectionUp = if (canMoveUp) ({ moveSection("navigation", -1) }) else null,
                    onMoveSectionDown = if (canMoveDown) ({ moveSection("navigation", 1) }) else null
                ) {
                    val navHomeHidden = "nav_home" in hiddenItems
                    if (isEditMode || !navHomeHidden) {
                        DrawerItem(
                            icon = Icons.Rounded.Home,
                            label = "Dashboard Home",
                            isEditMode = isEditMode,
                            isHiddenItem = navHomeHidden,
                            onToggleVisibility = { toggleItemHidden("nav_home") }
                        ) {
                            manager.replaceCurrentTabWith(HomeTab(), keepCurrentTabAsParent = true)
                            onNavigate()
                        }
                    }

                    if (preferencesManager.showRecentInSidebar) {
                        val navRecentHidden = "nav_recent_files" in hiddenItems
                        if (isEditMode || !navRecentHidden) {
                            DrawerItem(
                                icon = Icons.Rounded.History,
                                label = stringResource(R.string.recent_files),
                                isEditMode = isEditMode,
                                isHiddenItem = navRecentHidden,
                                onToggleVisibility = { toggleItemHidden("nav_recent_files") }
                            ) {
                                manager.replaceCurrentTabWith(
                                    FilesTab(VirtualFileHolder(VirtualFileHolder.RECENT)),
                                    keepCurrentTabAsParent = true
                                )
                                onNavigate()
                            }
                        }
                    }

                    val navAllFilesHidden = "nav_all_files" in hiddenItems
                    if (isEditMode || !navAllFilesHidden) {
                        DrawerItem(
                            icon = Icons.Rounded.FolderCopy,
                            label = "All Files",
                            isEditMode = isEditMode,
                            isHiddenItem = navAllFilesHidden,
                            onToggleVisibility = { toggleItemHidden("nav_all_files") }
                        ) {
                            manager.replaceCurrentTabWith(
                                FilesTab(VirtualFileHolder(VirtualFileHolder.ALL_FILES)),
                                keepCurrentTabAsParent = true
                            )
                            onNavigate()
                        }
                    }

                    val navRootHidden = "nav_root" in hiddenItems
                    if (isEditMode || !navRootHidden) {
                        DrawerItem(
                            icon = Icons.Rounded.Dns,
                            label = "System Root",
                            isEditMode = isEditMode,
                            isHiddenItem = navRootHidden,
                            onToggleVisibility = { toggleItemHidden("nav_root") }
                        ) {
                            manager.replaceCurrentTabWith(
                                FilesTab(LocalFileHolder(File("/"))),
                                keepCurrentTabAsParent = true
                            )
                            onNavigate()
                        }
                    }

                    val navBinHidden = "nav_recycle_bin" in hiddenItems
                    if (isEditMode || !navBinHidden) {
                        DrawerItem(
                            icon = Icons.Rounded.DeleteSweep,
                            label = "Recycle Bin",
                            isEditMode = isEditMode,
                            isHiddenItem = navBinHidden,
                            onToggleVisibility = { toggleItemHidden("nav_recycle_bin") }
                        ) {
                            manager.replaceCurrentTabWith(
                                FilesTab(globalClass.recycleBinDir),
                                keepCurrentTabAsParent = true
                            )
                            onNavigate()
                        }
                    }
                }
                }

                "storage" -> {
                if (storageList.isNotEmpty()) {
                    CollapsibleDrawerSection(
                        sectionId = "storage",
                        title = "Storage Devices",
                        isEditMode = isEditMode,
                        onToggleEditMode = { editingSections["storage"] = !isEditMode },
                        onMoveSectionUp = if (canMoveUp) ({ moveSection("storage", -1) }) else null,
                        onMoveSectionDown = if (canMoveDown) ({ moveSection("storage", 1) }) else null
                    ) {
                        for (device in storageList) {
                            val itemId = "storage_${device.title}"
                            val isHidden = itemId in hiddenItems
                            if (isEditMode || !isHidden) {
                                StorageDrawerItem(
                                    device = device,
                                    isEditMode = isEditMode,
                                    isHiddenItem = isHidden,
                                    onToggleVisibility = { toggleItemHidden(itemId) }
                                ) {
                                    manager.replaceCurrentTabWith(
                                        FilesTab(device.contentHolder),
                                        keepCurrentTabAsParent = true
                                    )
                                    onNavigate()
                                }
                            }
                        }
                    }
                }
                }

                "tools" -> {
                CollapsibleDrawerSection(
                    sectionId = "tools",
                    title = "Servers & Tools",
                    isEditMode = isEditMode,
                    onToggleEditMode = { editingSections["tools"] = !isEditMode },
                    onMoveSectionUp = if (canMoveUp) ({ moveSection("tools", -1) }) else null,
                    onMoveSectionDown = if (canMoveDown) ({ moveSection("tools", 1) }) else null
                ) {
                    val vaultHidden = "tool_vault" in hiddenItems
                    if (isEditMode || !vaultHidden) {
                        DrawerItem(
                            icon = Icons.Rounded.VpnKey,
                            label = "Private Wallet",
                            isEditMode = isEditMode,
                            isHiddenItem = vaultHidden,
                            onToggleVisibility = { toggleItemHidden("tool_vault") }
                        ) {
                            manager.openToolInNewTab(VaultTab())
                            onNavigate()
                        }
                    }

                    val ftpHidden = "tool_ftp" in hiddenItems
                    if (isEditMode || !ftpHidden) {
                        DrawerItem(
                            icon = Icons.Rounded.SettingsEthernet,
                            label = "FTP Server",
                            isEditMode = isEditMode,
                            isHiddenItem = ftpHidden,
                            onToggleVisibility = { toggleItemHidden("tool_ftp") }
                        ) {
                            manager.openToolInNewTab(FtpServerTab())
                            onNavigate()
                        }
                    }

                    val webShareHidden = "tool_web_share" in hiddenItems
                    if (isEditMode || !webShareHidden) {
                        DrawerItem(
                            icon = Icons.Rounded.Share,
                            label = "Web Sharing",
                            isEditMode = isEditMode,
                            isHiddenItem = webShareHidden,
                            onToggleVisibility = { toggleItemHidden("tool_web_share") }
                        ) {
                            manager.openToolInNewTab(WebSharingTab())
                            onNavigate()
                        }
                    }

                    val storageAnalysisHidden = "tool_storage_analysis" in hiddenItems
                    if (isEditMode || !storageAnalysisHidden) {
                        DrawerItem(
                            icon = Icons.Rounded.PieChart,
                            label = stringResource(R.string.storage_analysis),
                            isEditMode = isEditMode,
                            isHiddenItem = storageAnalysisHidden,
                            onToggleVisibility = { toggleItemHidden("tool_storage_analysis") }
                        ) {
                            manager.openToolInNewTab(StorageAnalysisTab())
                            onNavigate()
                        }
                    }

                    val duplicateFinderHidden = "tool_duplicate_finder" in hiddenItems
                    if (isEditMode || !duplicateFinderHidden) {
                        DrawerItem(
                            icon = Icons.Rounded.Difference,
                            label = stringResource(R.string.duplicate_finder),
                            isEditMode = isEditMode,
                            isHiddenItem = duplicateFinderHidden,
                            onToggleVisibility = { toggleItemHidden("tool_duplicate_finder") }
                        ) {
                            manager.openToolInNewTab(DuplicateFinderTab())
                            onNavigate()
                        }
                    }
                }
                }

                "remote" -> {
                CollapsibleDrawerSection(
                    sectionId = "remote",
                    title = "Remote Connections",
                    isEditMode = isEditMode,
                    onToggleEditMode = { editingSections["remote"] = !isEditMode },
                    onMoveSectionUp = if (canMoveUp) ({ moveSection("remote", -1) }) else null,
                    onMoveSectionDown = if (canMoveDown) ({ moveSection("remote", 1) }) else null
                ) {
                    for (conn in remoteConnections) {
                        val itemId = "remote_${conn.id}"
                        val isHidden = itemId in hiddenItems
                        if (isEditMode || !isHidden) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 2.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .alpha(if (isHidden) 0.45f else 1f)
                                    .clickable {
                                        if (isEditMode) {
                                            toggleItemHidden(itemId)
                                        } else {
                                            manager.replaceCurrentTabWith(
                                                FilesTab(RemoteFileHolder.rootHolder(conn)),
                                                keepCurrentTabAsParent = true
                                            )
                                            onNavigate()
                                        }
                                    }
                                    .padding(vertical = 10.dp, horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = when (conn.type) {
                                        "FTP" -> Icons.Rounded.SettingsEthernet
                                        "SFTP" -> Icons.Rounded.Dns
                                        "WebDav" -> Icons.Rounded.Cloud
                                        else -> Icons.Rounded.NetworkWifi
                                    },
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(16.dp))
                                Text(
                                    text = conn.name,
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                if (isEditMode) {
                                    IconButton(
                                        onClick = { toggleItemHidden(itemId) },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (isHidden) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                            contentDescription = if (isHidden) "Show item" else "Hide item",
                                            tint = if (isHidden) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(4.dp))
                                }
                                IconButton(
                                    onClick = {
                                        NetworkConnectionsService.deleteConnection(context, conn.id)
                                        remoteConnections = NetworkConnectionsService.getConnections(context)
                                    },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Delete,
                                        contentDescription = "Delete Connection",
                                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }

                    val remoteAddHidden = "remote_add" in hiddenItems
                    if (isEditMode || !remoteAddHidden) {
                        DrawerItem(
                            icon = Icons.Rounded.AddLink,
                            label = "Add Remote Connection",
                            tint = MaterialTheme.colorScheme.secondary,
                            isEditMode = isEditMode,
                            isHiddenItem = remoteAddHidden,
                            onToggleVisibility = { toggleItemHidden("remote_add") }
                        ) {
                            manager.openToolInNewTab(NetworkConnectionWizardTab())
                            onNavigate()
                        }
                    }
                }
                }
                    }
                }

                // Settings / Preferences button
                DrawerItem(
                    icon = Icons.Rounded.Settings,
                    label = stringResource(R.string.preferences),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                ) {
                    context.startActivity(Intent(context, PreferencesActivity::class.java))
                    onNavigate()
                }

                // About button
                DrawerItem(
                    icon = Icons.Rounded.Info,
                    label = "About App",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                ) {
                    manager.toggleAppInfoDialog(true)
                    onNavigate()
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }

        CompositionLocalProvider(LocalOverscrollConfiguration provides overscrollConfig) {
            if (preferencesManager.disablePullDownToRefresh) {
                drawerContent()
            } else if (preferencesManager.disableSpringEffect) {
                val noSpringState = rememberPullToRefreshState()
                PullToRefreshBox(
                    isRefreshing = isRefreshing,
                    onRefresh = refreshDrawer,
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
                    drawerContent()
                }
            } else {
                PullToRefreshBox(
                    isRefreshing = isRefreshing,
                    onRefresh = refreshDrawer,
                    modifier = Modifier.fillMaxSize()
                ) {
                    drawerContent()
                }
            }
        }
    }
}

@Composable
private fun CollapsibleDrawerSection(
    sectionId: String,
    title: String,
    isEditMode: Boolean = false,
    onToggleEditMode: (() -> Unit)? = null,
    onMoveSectionUp: (() -> Unit)? = null,
    onMoveSectionDown: (() -> Unit)? = null,
    action: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val prefs = globalClass.preferencesManager
    val isCollapsed = sectionId in prefs.drawerCollapsedSections
    val arrowRotation by animateFloatAsState(
        targetValue = if (isCollapsed) -90f else 0f,
        animationSpec = tween(200),
        label = "arrowRotation_$sectionId"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                val current = prefs.drawerCollapsedSections.toMutableSet()
                if (isCollapsed) current.remove(sectionId) else current.add(sectionId)
                prefs.drawerCollapsedSections = current
            }
            .padding(horizontal = 24.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
            modifier = Modifier.weight(1f)
        )
        if (isEditMode) {
            IconButton(
                onClick = { onMoveSectionUp?.invoke() },
                enabled = onMoveSectionUp != null,
                modifier = Modifier.size(22.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.KeyboardArrowUp,
                    contentDescription = "Move section up",
                    modifier = Modifier.size(16.dp),
                    tint = if (onMoveSectionUp != null) MaterialTheme.colorScheme.primary
                           else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                )
            }
            IconButton(
                onClick = { onMoveSectionDown?.invoke() },
                enabled = onMoveSectionDown != null,
                modifier = Modifier.size(22.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.KeyboardArrowDown,
                    contentDescription = "Move section down",
                    modifier = Modifier.size(16.dp),
                    tint = if (onMoveSectionDown != null) MaterialTheme.colorScheme.primary
                           else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
        }
        if (action != null) {
            action()
            Spacer(modifier = Modifier.width(6.dp))
        }
        if (onToggleEditMode != null) {
            IconButton(
                onClick = onToggleEditMode,
                modifier = Modifier.size(22.dp)
            ) {
                Icon(
                    imageVector = if (isEditMode) Icons.Rounded.Visibility else Icons.Rounded.VisibilityOff,
                    contentDescription = "Edit section items and order",
                    modifier = Modifier.size(15.dp),
                    tint = if (isEditMode) MaterialTheme.colorScheme.primary
                           else MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
        }
        Icon(
            imageVector = Icons.Rounded.ExpandMore,
            contentDescription = null,
            modifier = Modifier
                .size(16.dp)
                .rotate(arrowRotation),
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
        )
    }

    AnimatedVisibility(
        visible = !isCollapsed || isEditMode,
        enter = expandVertically(),
        exit = shrinkVertically()
    ) {
        Column { content() }
    }
}

@Composable
private fun DrawerTabItem(
    label: String,
    isActive: Boolean,
    isPinned: Boolean = false,
    isCopyHereDestination: Boolean = false,
    index: Int,
    totalTabs: Int,
    isReorderMode: Boolean,
    onSelect: () -> Unit,
    onClose: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (isActive) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                else Color.Transparent
            )
            .clickable { onSelect() }
            .padding(vertical = 4.dp, horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = when {
                isCopyHereDestination -> Icons.Rounded.MoveToInbox
                isPinned -> Icons.Rounded.PushPin
                else -> Icons.Rounded.Tab
            },
            contentDescription = null,
            tint = if (isActive || isCopyHereDestination) MaterialTheme.colorScheme.primary
                   else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (isActive) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface,
            fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )

        if (isReorderMode && totalTabs > 1) {
            IconButton(
                onClick = onMoveUp,
                enabled = index > 0,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.KeyboardArrowUp,
                    contentDescription = stringResource(R.string.move_tab_up),
                    modifier = Modifier.size(18.dp),
                    tint = if (index > 0) MaterialTheme.colorScheme.primary
                           else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                )
            }
            IconButton(
                onClick = onMoveDown,
                enabled = index < totalTabs - 1,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.KeyboardArrowDown,
                    contentDescription = stringResource(R.string.move_tab_down),
                    modifier = Modifier.size(18.dp),
                    tint = if (index < totalTabs - 1) MaterialTheme.colorScheme.primary
                           else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                )
            }
        }

        if (totalTabs > 1 && !isPinned) {
            IconButton(
                onClick = onClose,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = stringResource(R.string.close_tab),
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@Composable
private fun DrawerSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.bodySmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 8.dp)
    )
}

@Composable
private fun DrawerItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    textColor: Color = MaterialTheme.colorScheme.onSurface,
    fontWeight: FontWeight? = null,
    isEditMode: Boolean = false,
    isHiddenItem: Boolean = false,
    onToggleVisibility: (() -> Unit)? = null,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(8.dp))
            .alpha(if (isHiddenItem) 0.45f else 1f)
            .clickable {
                if (isEditMode && onToggleVisibility != null) {
                    onToggleVisibility()
                } else {
                    onClick()
                }
            }
            .padding(vertical = 12.dp, horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = textColor,
            fontWeight = fontWeight,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (isEditMode && onToggleVisibility != null) {
            IconButton(
                onClick = onToggleVisibility,
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    imageVector = if (isHiddenItem) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                    contentDescription = if (isHiddenItem) "Show item" else "Hide item",
                    tint = if (isHiddenItem) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun StorageDrawerItem(
    device: StorageDevice,
    isEditMode: Boolean = false,
    isHiddenItem: Boolean = false,
    onToggleVisibility: (() -> Unit)? = null,
    onClick: () -> Unit
) {
    val progress = if (device.totalSize > 0) {
        (device.usedSize.toFloat() / device.totalSize).coerceIn(0f, 1f)
    } else 0f
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(durationMillis = 450, easing = FastOutSlowInEasing),
        label = "storageProgress"
    )
    val freeSize = (device.totalSize - device.usedSize).coerceAtLeast(0L)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(8.dp))
            .alpha(if (isHiddenItem) 0.45f else 1f)
            .clickable {
                if (isEditMode && onToggleVisibility != null) {
                    onToggleVisibility()
                } else {
                    onClick()
                }
            }
            .padding(vertical = 10.dp, horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = when (device.type) {
                INTERNAL_STORAGE -> Icons.Rounded.FolderOpen
                ROOT -> Icons.Rounded.Dns
                REMOTE_STORAGE -> Icons.Rounded.Cloud
                else -> Icons.Rounded.SdStorage
            },
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = device.title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (device.totalSize > 0) {
                Spacer(modifier = Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { animatedProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(5.dp),
                    strokeCap = StrokeCap.Round,
                    color = if (progress > 0.85f) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    }
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${device.usedSize.toFormattedSize()} used • ${freeSize.toFormattedSize()} free",
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (isEditMode && onToggleVisibility != null) {
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(
                onClick = onToggleVisibility,
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    imageVector = if (isHiddenItem) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                    contentDescription = if (isHiddenItem) "Show item" else "Hide item",
                    tint = if (isHiddenItem) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
