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
                // Section 0: Open Tabs
                if (preferencesManager.showTabsInDrawer) {
                    val tabs = mainActivityState.tabs
                    val tabsTitle = stringResource(R.string.tabs)
                    var isReorderingTabs by remember { mutableStateOf(false) }

                    CollapsibleDrawerSection(
                        sectionId = "tabs",
                        title = "$tabsTitle (${tabs.size})",
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
                            val isActive = index == mainActivityState.selectedTabIndex
                            DrawerTabItem(
                                label = label,
                                isActive = isActive,
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
                        DrawerItem(
                            icon = Icons.Rounded.Add,
                            label = stringResource(R.string.new_tab),
                            tint = MaterialTheme.colorScheme.secondary,
                            textColor = MaterialTheme.colorScheme.secondary
                        ) {
                            manager.addDefaultOrOverriddenNewTab()
                            onNavigate()
                        }
                    }
                }

                // Section 0b: Bookmarks
                if (preferencesManager.showBookmarksInDrawer) {
                    val bookmarks = preferencesManager.bookmarks
                    val bookmarksTitle = stringResource(R.string.bookmarks)
                    CollapsibleDrawerSection(
                        sectionId = "bookmarks",
                        title = "$bookmarksTitle (${bookmarks.size})"
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
                                val file = File(path)
                                DrawerItem(
                                    icon = Icons.Rounded.Bookmark,
                                    label = file.name.ifEmpty { path }
                                ) {
                                    manager.replaceCurrentTabWith(FilesTab(LocalFileHolder(file)))
                                    onNavigate()
                                }
                            }
                        }
                    }
                }

                // Section 1: Dashboard Navigation
                CollapsibleDrawerSection(sectionId = "navigation", title = "Navigation") {
                    DrawerItem(
                        icon = Icons.Rounded.Home,
                        label = "Dashboard Home"
                    ) {
                        manager.replaceCurrentTabWith(HomeTab())
                        onNavigate()
                    }

                    DrawerItem(
                        icon = Icons.Rounded.Dns,
                        label = "System Root"
                    ) {
                        manager.replaceCurrentTabWith(FilesTab(LocalFileHolder(File("/"))))
                        onNavigate()
                    }

                    DrawerItem(
                        icon = Icons.Rounded.DeleteSweep,
                        label = "Recycle Bin"
                    ) {
                        manager.replaceCurrentTabWith(FilesTab(globalClass.recycleBinDir))
                        onNavigate()
                    }
                } // end Navigation section

                // Section 2: Storage Devices
                if (storageList.isNotEmpty()) {
                    CollapsibleDrawerSection(sectionId = "storage", title = "Storage Devices") {
                        for (device in storageList) {
                            StorageDrawerItem(device = device) {
                                manager.replaceCurrentTabWith(FilesTab(device.contentHolder))
                                onNavigate()
                            }
                        }
                    } // end Storage section
                }

                // Section 3: Servers & Tools
                CollapsibleDrawerSection(sectionId = "tools", title = "Servers & Tools") {

                    DrawerItem(
                        icon = Icons.Rounded.VpnKey,
                        label = "Private Wallet"
                    ) {
                        manager.replaceCurrentTabWith(VaultTab())
                        onNavigate()
                    }

                    DrawerItem(
                        icon = Icons.Rounded.SettingsEthernet,
                        label = "FTP Server"
                    ) {
                        manager.replaceCurrentTabWith(FtpServerTab())
                        onNavigate()
                    }

                    DrawerItem(
                        icon = Icons.Rounded.Share,
                        label = "Web Sharing"
                    ) {
                        manager.replaceCurrentTabWith(WebSharingTab())
                        onNavigate()
                    }

                    DrawerItem(
                        icon = Icons.Rounded.PieChart,
                        label = stringResource(R.string.storage_analysis)
                    ) {
                        manager.replaceCurrentTabWith(StorageAnalysisTab())
                        onNavigate()
                    }

                    DrawerItem(
                        icon = Icons.Rounded.Difference,
                        label = stringResource(R.string.duplicate_finder)
                    ) {
                        manager.replaceCurrentTabWith(DuplicateFinderTab())
                        onNavigate()
                    }
                } // end Tools section

                // Section 4: Remote Connections
                CollapsibleDrawerSection(sectionId = "remote", title = "Remote Connections") {

                    for (conn in remoteConnections) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 2.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    manager.replaceCurrentTabWith(FilesTab(RemoteFileHolder.rootHolder(conn)))
                                    onNavigate()
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
                            // Delete Connection Icon
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

                    // Add Remote Connection button
                    DrawerItem(
                        icon = Icons.Rounded.AddLink,
                        label = "Add Remote Connection",
                        tint = MaterialTheme.colorScheme.secondary
                    ) {
                        manager.replaceCurrentTabWith(NetworkConnectionWizardTab())
                        onNavigate()
                    }
                } // end Remote section

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
        if (action != null) {
            action()
            Spacer(modifier = Modifier.width(6.dp))
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
        visible = !isCollapsed,
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
            imageVector = Icons.Rounded.Tab,
            contentDescription = null,
            tint = if (isActive) MaterialTheme.colorScheme.primary
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

        if (totalTabs > 1) {
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
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() }
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
    }
}

@Composable
private fun StorageDrawerItem(
    device: StorageDevice,
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
            .clickable { onClick() }
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
    }
}
