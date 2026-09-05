package com.raival.compose.file.explorer.screen.main.tab.nfile_tools.ui

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.NetworkWifi
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.raival.compose.file.explorer.App.Companion.globalClass
import com.raival.compose.file.explorer.screen.main.tab.home.HomeTab
import com.raival.compose.file.explorer.screen.main.tab.files.service.remote.*
import com.raival.compose.file.explorer.screen.main.tab.nfile_tools.NetworkConnectionWizardTab
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

@Composable
fun NetworkConnectionWizardScreen(tab: NetworkConnectionWizardTab) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var name by remember { mutableStateOf("") }
    var host by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("21") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var rootPath by remember { mutableStateOf("/") }
    var selectedType by remember { mutableStateOf("FTP") }
    var webdavProtocol by remember { mutableStateOf("http") }

    var isTesting by remember { mutableStateOf(false) }

    val smbScanner = remember { LanSmbScanner(context) }
    val isScanningSmb by smbScanner.isScanning.collectAsState()
    val discoveredServers by smbScanner.servers.collectAsState()

    DisposableEffect(Unit) {
        onDispose {
            smbScanner.stopScan()
        }
    }

    LaunchedEffect(selectedType) {
        if (selectedType == "LAN/SMB" && discoveredServers.isEmpty() && !isScanningSmb) {
            smbScanner.startScan(scope)
        }
    }

    val connectionTypes = listOf("FTP", "SFTP", "LAN/SMB", "WebDav")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        // Connection Type selector
        Text(
            text = "Select Server Protocol Type",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.align(Alignment.Start)
        )
        Spacer(modifier = Modifier.height(8.dp))

        Column(modifier = Modifier.selectableGroup()) {
            connectionTypes.forEach { type ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(
                            selected = (type == selectedType),
                            onClick = {
                                selectedType = type
                                // Auto-fill default port based on selected type
                                port = when (type) {
                                    "FTP" -> "21"
                                    "SFTP" -> "22"
                                    "LAN/SMB" -> "445"
                                    "WebDav" -> if (webdavProtocol == "https") "443" else "80"
                                    else -> "21"
                                }
                            },
                            role = Role.RadioButton
                        )
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = (type == selectedType),
                        onClick = null // Selected handles click
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(text = type)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // SMB Local Network Scanner
        if (selectedType == "LAN/SMB") {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Dns,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(26.dp)
                            )
                            Column {
                                Text(
                                    text = "Auto-scan Local Network",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = if (isScanningSmb) "Searching local network for SMB servers..."
                                    else if (discoveredServers.isNotEmpty()) "${discoveredServers.size} server(s) found"
                                    else "Find your computer or NAS automatically",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        if (isScanningSmb) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.5.dp
                            )
                        } else {
                            FilledTonalButton(
                                onClick = { smbScanner.startScan(scope) },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Refresh,
                                    contentDescription = "Scan",
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(if (discoveredServers.isEmpty()) "Scan" else "Rescan")
                            }
                        }
                    }

                    if (isScanningSmb) {
                        Spacer(modifier = Modifier.height(12.dp))
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(3.dp)
                                .clip(RoundedCornerShape(2.dp))
                        )
                    }

                    if (discoveredServers.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Tap to select and auto-fill details:",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            discoveredServers.forEach { server ->
                                val isSelected = host == server.address.hostAddress || host == server.host
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable {
                                            host = server.address.hostAddress
                                            if (name.isBlank() || name.startsWith("LAN/SMB")) {
                                                name = server.displayName
                                            }
                                            port = "445"
                                            Toast.makeText(
                                                context,
                                                "Selected ${server.displayName} (${server.address.hostAddress})",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        },
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (isSelected) {
                                        MaterialTheme.colorScheme.primaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.surface
                                    },
                                    tonalElevation = if (isSelected) 4.dp else 1.dp,
                                    border = if (isSelected) {
                                        BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
                                    } else null
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 14.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Computer,
                                            contentDescription = null,
                                            tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(28.dp)
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = server.displayName,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = "${server.address.hostAddress} • via ${server.discoveryMethod}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        if (isSelected) {
                                            Icon(
                                                imageVector = Icons.Rounded.CheckCircle,
                                                contentDescription = "Selected",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    } else if (!isScanningSmb) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "No SMB servers auto-detected yet. Check Wi-Fi connection and SMB sharing on your computer, or enter IP manually below.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
        }

        // Form Fields
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Friendly Connection Name (e.g. Home Server)") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = host,
            onValueChange = { host = it },
            label = { Text("Server Hostname / IP Address") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = port,
            onValueChange = { port = it },
            label = { Text("Port") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = username,
            onValueChange = { username = it },
            label = { Text("Username") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = rootPath,
            onValueChange = { rootPath = it },
            label = { Text("Root Path Directory") },
            modifier = Modifier.fillMaxWidth()
        )

        if (selectedType == "WebDav") {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "WebDav Protocol Scheme",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.align(Alignment.Start)
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable {
                        webdavProtocol = "http"
                        if (port == "443") port = "80"
                    }
                ) {
                    RadioButton(
                        selected = webdavProtocol == "http",
                        onClick = {
                            webdavProtocol = "http"
                            if (port == "443") port = "80"
                        }
                    )
                    Text("HTTP")
                }
                Spacer(modifier = Modifier.width(20.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable {
                        webdavProtocol = "https"
                        if (port == "80") port = "443"
                    }
                ) {
                    RadioButton(
                        selected = webdavProtocol == "https",
                        onClick = {
                            webdavProtocol = "https"
                            if (port == "80") port = "443"
                        }
                    )
                    Text("HTTPS")
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Test Connection button
            Button(
                onClick = {
                    isTesting = true
                    scope.launch {
                        val defaultPort = when (selectedType) {
                            "FTP" -> 21
                            "SFTP" -> 22
                            "LAN/SMB" -> 445
                            "WebDav" -> if (webdavProtocol == "https") 443 else 80
                            else -> 21
                        }
                        val connectionModel = NetworkConnectionModel(
                            id = UUID.randomUUID().toString(),
                            name = name.ifEmpty { "Test Server" },
                            type = selectedType,
                            host = host,
                            port = port.toIntOrNull() ?: defaultPort,
                            username = username,
                            password = password,
                            rootPath = rootPath,
                            protocol = webdavProtocol
                        )

                        val client: RemoteClient = when (selectedType) {
                            "FTP" -> FtpRemoteClient(connectionModel)
                            "SFTP" -> SftpRemoteClient(connectionModel)
                            "WebDav" -> WebDavRemoteClient(connectionModel)
                            else -> LanRemoteClient(context, connectionModel)
                        }

                        var testError: String? = null
                        val success = withContext(Dispatchers.IO) {
                            try {
                                client.connect()
                                client.disconnect()
                                true
                            } catch (e: Exception) {
                                e.printStackTrace()
                                testError = e.message ?: e.javaClass.simpleName
                                false
                            }
                        }

                        withContext(Dispatchers.Main) {
                            isTesting = false
                            if (success) {
                                Toast.makeText(context, "Connection Successful!", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "Connection Failed: ${testError ?: "Server unreachable"}", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp),
                enabled = !isTesting && host.isNotEmpty()
            ) {
                if (isTesting) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
                } else {
                    Text("Test Connect")
                }
            }

            // Save Connection button
            Button(
                onClick = {
                    val finalName = name.ifEmpty { "$selectedType - $host" }
                    val defaultPort = when (selectedType) {
                        "FTP" -> 21
                        "SFTP" -> 22
                        "LAN/SMB" -> 445
                        "WebDav" -> if (webdavProtocol == "https") 443 else 80
                        else -> 21
                    }
                    val connectionModel = NetworkConnectionModel(
                        id = UUID.randomUUID().toString(),
                        name = finalName,
                        type = selectedType,
                        host = host,
                        port = port.toIntOrNull() ?: defaultPort,
                        username = username,
                        password = password,
                        rootPath = rootPath,
                        protocol = webdavProtocol
                    )
                    NetworkConnectionsService.saveConnection(context, connectionModel)
                    Toast.makeText(context, "Connection Saved!", Toast.LENGTH_SHORT).show()
                    // Go back to home
                    globalClass.mainActivityManager.replaceCurrentTabWith(HomeTab())
                },
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp),
                enabled = host.isNotEmpty()
            ) {
                Text("Save Server")
            }
        }
    }
}
