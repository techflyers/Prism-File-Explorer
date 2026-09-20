package com.techflyers.compose.file.explorer.screen.preferences.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.techflyers.compose.file.explorer.screen.main.tab.files.shizuku.ShizukuManager

@Composable
fun ShizukuContainer() {
    val context = LocalContext.current
    var showDownloadDialog by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    var showInfoDialog by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }

    LaunchedEffect(Unit) {
        ShizukuManager.checkStatus()
        ShizukuManager.probeRoot()
    }

    Container(title = "Privileged Access") {
        val managerName = if (ShizukuManager.isShizukuInstalled) ShizukuManager.detectedManagerName else "Shizuku / Shevery"

        // Status overview
        val statusText = when (ShizukuManager.accessMode) {
            ShizukuManager.AccessMode.SHIZUKU -> "Active via ${ShizukuManager.detectedManagerName}"
            ShizukuManager.AccessMode.ROOT -> "Active via Root (su)"
            ShizukuManager.AccessMode.NONE -> "Not configured"
        }
        val statusIcon = when (ShizukuManager.accessMode) {
            ShizukuManager.AccessMode.NONE -> Icons.Rounded.Lock
            else -> Icons.Rounded.CheckCircle
        }

        PreferenceItem(
            label = "Privileged access status",
            supportingText = statusText,
            icon = statusIcon,
        )

        HorizontalDivider(
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            thickness = 1.dp
        )

        // Shizuku / Shevery / Fork section
        val shizukuSupportingText = when {
            ShizukuManager.isShizukuGranted -> "Connected and granted (${ShizukuManager.detectedManagerName})"
            ShizukuManager.isBinderAlive -> "Service is running — tap to grant permission"
            ShizukuManager.isShizukuInstalled -> "${ShizukuManager.detectedManagerName} is installed — tap to open and start service"
            else -> "Not installed — tap to choose and download"
        }

        PreferenceItem(
            label = managerName,
            supportingText = shizukuSupportingText,
            icon = Icons.Rounded.PhoneAndroid,
            onClick = {
                when {
                    ShizukuManager.isShizukuGranted -> {
                        ShizukuManager.checkStatus()
                    }
                    ShizukuManager.isBinderAlive -> {
                        ShizukuManager.requestShizukuPermission()
                    }
                    ShizukuManager.isShizukuInstalled -> {
                        val opened = ShizukuManager.openManagerApp(context)
                        if (!opened) {
                            ShizukuManager.requestShizukuPermission()
                        }
                    }
                    else -> {
                        showDownloadDialog = true
                    }
                }
            },
            trailingContent = {
                androidx.compose.material3.IconButton(onClick = { showInfoDialog = true }) {
                    androidx.compose.material3.Icon(
                        imageVector = androidx.compose.material.icons.Icons.Rounded.Info,
                        contentDescription = "About Shizuku and Shevery",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        )

        if (ShizukuManager.isShizukuGranted || ShizukuManager.isRootAvailable) {
            HorizontalDivider(
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                thickness = 1.dp
            )

            // Access mode selector
            val modeLabel = when {
                ShizukuManager.isShizukuGranted && ShizukuManager.isRootAvailable ->
                    "Both ${ShizukuManager.detectedManagerName} and Root available — tap to switch"
                ShizukuManager.isShizukuGranted -> "Using ${ShizukuManager.detectedManagerName}"
                else -> "Using Root (su)"
            }

            PreferenceItem(
                label = "Access mode",
                supportingText = modeLabel,
                icon = Icons.Rounded.AdminPanelSettings,
                onClick = {
                    val rootOk = ShizukuManager.probeRoot()
                    // Cycle through available modes
                    val next = when (ShizukuManager.accessMode) {
                        ShizukuManager.AccessMode.NONE ->
                            if (ShizukuManager.isShizukuGranted) ShizukuManager.AccessMode.SHIZUKU
                            else if (rootOk) ShizukuManager.AccessMode.ROOT
                            else ShizukuManager.AccessMode.NONE
                        ShizukuManager.AccessMode.SHIZUKU ->
                            if (rootOk) ShizukuManager.AccessMode.ROOT
                            else ShizukuManager.AccessMode.NONE
                        ShizukuManager.AccessMode.ROOT -> ShizukuManager.AccessMode.NONE
                    }
                    ShizukuManager.updateAccessMode(next)
                }
            )
        }

        HorizontalDivider(
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            thickness = 1.dp
        )

        // Info note
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Text(
                text = "ℹ️  Privileged access lets Prism browse restricted folders like /Android/data and /data/data that are normally hidden from file managers.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    // Info Dialog
    if (showInfoDialog) {
        AlertDialog(
            onDismissRequest = { showInfoDialog = false },
            title = {
                Text(
                    text = "Shizuku vs Shevery",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                val scrollState = rememberScrollState()
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(scrollState),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Both Shizuku and Shevery allow Prism File Explorer to access restricted system folders (such as /Android/data and /Android/obb) on Android 11+ without rooting your device, using Wireless Debugging (ADB).",
                        style = MaterialTheme.typography.bodyMedium
                    )

                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                        )
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "✦ Shevery (Recommended Fork)",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "A modern, actively maintained open-source fork of Shizuku with dynamic Material 3 design and streamlined wireless debugging setup.",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }

                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                        )
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "✦ Shizuku (Official Standard)",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "The widely adopted, battle-tested original service available on Google Play and GitHub.",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }

                    Text(
                        text = "Prism supports both apps identically. Choose either one based on your preference.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showInfoDialog = false
                    if (!ShizukuManager.isShizukuInstalled) {
                        showDownloadDialog = true
                    }
                }) {
                    Text(if (!ShizukuManager.isShizukuInstalled) "Download" else "OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showInfoDialog = false }) {
                    Text("Close")
                }
            }
        )
    }

    // Download Dialog
    if (showDownloadDialog) {
        AlertDialog(
            onDismissRequest = { showDownloadDialog = false },
            title = {
                Text(
                    text = "Download Privileged Manager",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = "Choose which manager application you would like to download and install:",
                        style = MaterialTheme.typography.bodyMedium
                    )

                    // Option 1: Shevery
                    OutlinedCard(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            showDownloadDialog = false
                            ShizukuManager.openSheveryReleases(context)
                        }
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "Shevery",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                SuggestionChip(
                                    onClick = {},
                                    label = { Text("Recommended", style = MaterialTheme.typography.labelSmall) }
                                )
                            }
                            Text(
                                text = "Modern UI fork with active updates. Download official APK from GitHub Releases.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
                            )
                            Button(
                                onClick = {
                                    showDownloadDialog = false
                                    ShizukuManager.openSheveryReleases(context)
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Download from GitHub")
                            }
                        }
                    }

                    // Option 2: Shizuku
                    OutlinedCard(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "Shizuku",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                SuggestionChip(
                                    onClick = {},
                                    label = { Text("Original", style = MaterialTheme.typography.labelSmall) }
                                )
                            }
                            Text(
                                text = "The official, battle-tested standard service.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                FilledTonalButton(
                                    onClick = {
                                        showDownloadDialog = false
                                        ShizukuManager.openShizukuPlayStore(context)
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Google Play")
                                }
                                OutlinedButton(
                                    onClick = {
                                        showDownloadDialog = false
                                        ShizukuManager.openShizukuReleases(context)
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("GitHub")
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showDownloadDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
