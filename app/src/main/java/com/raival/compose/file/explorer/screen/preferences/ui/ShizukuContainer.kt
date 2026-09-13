package com.raival.compose.file.explorer.screen.preferences.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import com.raival.compose.file.explorer.screen.main.tab.files.shizuku.ShizukuManager

@Composable
fun ShizukuContainer() {
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        ShizukuManager.checkStatus()
        ShizukuManager.probeRoot()
    }

    Container(title = "Privileged Access") {
        val managerName = ShizukuManager.detectedManagerName

        // Status overview
        val statusText = when (ShizukuManager.accessMode) {
            ShizukuManager.AccessMode.SHIZUKU -> "Active via $managerName"
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
            ShizukuManager.isShizukuGranted -> "Connected and granted ($managerName)"
            ShizukuManager.isBinderAlive -> "Service is running — tap to grant permission"
            ShizukuManager.isShizukuInstalled -> "$managerName is installed — tap to open and start service"
            else -> "Not installed — tap to download Shevery or Shizuku"
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
                        ShizukuManager.openDownloadPage(context)
                    }
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
                    "Both $managerName and Root available — tap to switch"
                ShizukuManager.isShizukuGranted -> "Using $managerName"
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
}
