package com.techflyers.compose.file.explorer.screen.main.ui

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
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.techflyers.compose.file.explorer.App.Companion.globalClass
import com.techflyers.compose.file.explorer.R
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
import java.io.File

@Composable
fun NFileDrawerContent(
    drawerState: DrawerState,
    onNavigate: () -> Unit
) {
    val context = LocalContext.current
    val manager = globalClass.mainActivityManager
    val scope = rememberCoroutineScope()

    val storageList = remember { mutableStateListOf<StorageDevice>() }
    var remoteConnections by remember {
        mutableStateOf(NetworkConnectionsService.getConnections(context))
    }

    LaunchedEffect(Unit) {
        storageList.addAll(StorageProvider.getStorageDevices(globalClass))
    }

    // Refresh connections list when drawer opens
    LaunchedEffect(drawerState.isOpen) {
        if (drawerState.isOpen) {
            remoteConnections = NetworkConnectionsService.getConnections(context)
        }
    }

    ModalDrawerSheet(
        modifier = Modifier
            .fillMaxHeight()
            .width(320.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            // Section 1: Dashboard Navigation
            DrawerSectionHeader("Navigation")

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

            // Section 2: Storage Devices
            if (storageList.isNotEmpty()) {
                DrawerSectionHeader("Storage Devices")
                for (device in storageList) {
                    StorageDrawerItem(device = device) {
                        manager.replaceCurrentTabWith(FilesTab(device.contentHolder))
                        onNavigate()
                    }
                }
            }

            // Section 3: Servers & Tools
            DrawerSectionHeader("Servers & Tools")

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

            // Section 4: Remote Connections
            DrawerSectionHeader("Remote Connections")

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
            color = MaterialTheme.colorScheme.onSurface
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
                    progress = { progress },
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
