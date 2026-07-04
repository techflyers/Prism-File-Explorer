package com.raival.compose.file.explorer.screen.main.tab.nfile_tools.ui

import android.content.Intent
import android.os.Environment
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.raival.compose.file.explorer.screen.main.tab.files.service.FtpForegroundService
import com.raival.compose.file.explorer.screen.main.tab.files.service.FtpServer
import com.raival.compose.file.explorer.screen.main.tab.nfile_tools.FtpServerTab
import java.io.File

@Composable
fun FtpServerTabContentView(tab: FtpServerTab) {
    val context = LocalContext.current

    var port by remember { mutableStateOf(FtpServer.getPort().toString()) }
    var username by remember { mutableStateOf(FtpServer.getUsername()) }
    var isAnonymous by remember { mutableStateOf(FtpServer.isAnonymous()) }
    var homePath by remember { mutableStateOf(FtpServer.getHomeDir()) }
    var isRunning by remember { mutableStateOf(FtpServer.isRunning()) }

    var showFolderPicker by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        // Status Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isRunning) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (isRunning) Icons.Rounded.SettingsEthernet else Icons.Rounded.PortableWifiOff,
                    contentDescription = null,
                    tint = if (isRunning) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(52.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        text = if (isRunning) "Server Status: ACTIVE" else "Server Status: INACTIVE",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (isRunning) "ftp://${FtpServer.getLocalIpAddress()}:${FtpServer.getPort()}"
                        else "Server is stopped. Click start below to share files.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Configuration Form
        OutlinedTextField(
            value = port,
            onValueChange = { if (!isRunning) port = it },
            label = { Text("FTP Connection Port") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            enabled = !isRunning,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = isAnonymous,
                onCheckedChange = { if (!isRunning) isAnonymous = it },
                enabled = !isRunning
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Enable Anonymous Access")
        }

        if (!isAnonymous) {
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = username,
                onValueChange = { if (!isRunning) username = it },
                label = { Text("FTP Username") },
                enabled = !isRunning,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Directory Picker Box
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                .clickable(enabled = !isRunning) { showFolderPicker = true }
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Rounded.FolderOpen, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Shared Root Directory",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = homePath,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
            if (!isRunning) {
                Icon(Icons.Rounded.ChevronRight, contentDescription = null)
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // Control Button
        Button(
            onClick = {
                if (isRunning) {
                    val serviceIntent = Intent(context, FtpForegroundService::class.java)
                    context.stopService(serviceIntent)
                    isRunning = false
                } else {
                    val portVal = port.toIntOrNull() ?: 9999
                    FtpServer.configure(portVal, homePath, username, isAnonymous)
                    val serviceIntent = Intent(context, FtpForegroundService::class.java)
                    context.startService(serviceIntent)
                    isRunning = true
                    Toast.makeText(context, "FTP Server started successfully", Toast.LENGTH_SHORT).show()
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isRunning) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.primary
            )
        ) {
            Icon(
                imageVector = if (isRunning) Icons.Rounded.Stop else Icons.Rounded.PlayArrow,
                contentDescription = null
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(if (isRunning) "Stop Server" else "Start Server")
        }
    }

    // Directory selection dialog
    FileSelectionDialog(
        show = showFolderPicker,
        onDismissRequest = { showFolderPicker = false },
        onItemsSelected = { selected ->
            val first = selected.firstOrNull()
            if (first != null && first.isDirectory) {
                homePath = first.absolutePath
            } else {
                Toast.makeText(context, "Please select folders only", Toast.LENGTH_SHORT).show()
            }
        }
    )
}
