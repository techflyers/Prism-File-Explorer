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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.raival.compose.file.explorer.screen.main.tab.files.shizuku.ShizukuManager

@Composable
fun ShizukuContainer() {
    Container(title = "Privileged Access") {
        // Status overview
        val statusText = when (ShizukuManager.accessMode) {
            ShizukuManager.AccessMode.SHIZUKU -> "Active via Shizuku"
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

        // Shizuku section
        PreferenceItem(
            label = "Shizuku",
            supportingText = when {
                !ShizukuManager.isShizukuInstalled -> "Not installed — install Shizuku from Play Store"
                ShizukuManager.isShizukuGranted -> "Connected and granted"
                else -> "Installed but permission not granted"
            },
            icon = Icons.Rounded.PhoneAndroid,
            onClick = {
                if (!ShizukuManager.isShizukuGranted && ShizukuManager.isShizukuInstalled) {
                    ShizukuManager.requestShizukuPermission()
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
                    "Both Shizuku and Root available — tap to switch"
                ShizukuManager.isShizukuGranted -> "Using Shizuku"
                else -> "Using Root (su)"
            }

            PreferenceItem(
                label = "Access mode",
                supportingText = modeLabel,
                icon = Icons.Rounded.AdminPanelSettings,
                onClick = {
                    // Cycle through available modes
                    val next = when (ShizukuManager.accessMode) {
                        ShizukuManager.AccessMode.NONE ->
                            if (ShizukuManager.isShizukuGranted) ShizukuManager.AccessMode.SHIZUKU
                            else ShizukuManager.AccessMode.ROOT
                        ShizukuManager.AccessMode.SHIZUKU ->
                            if (ShizukuManager.isRootAvailable) ShizukuManager.AccessMode.ROOT
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
