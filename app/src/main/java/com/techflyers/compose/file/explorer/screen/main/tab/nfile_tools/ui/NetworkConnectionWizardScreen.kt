package com.techflyers.compose.file.explorer.screen.main.tab.nfile_tools.ui

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.techflyers.compose.file.explorer.App.Companion.globalClass
import com.techflyers.compose.file.explorer.screen.main.tab.files.FilesTab
import com.techflyers.compose.file.explorer.screen.main.tab.files.holder.RemoteFileHolder
import com.techflyers.compose.file.explorer.screen.main.tab.files.service.remote.*
import com.techflyers.compose.file.explorer.screen.main.tab.home.HomeTab
import com.techflyers.compose.file.explorer.screen.main.tab.nfile_tools.NetworkConnectionWizardTab
import com.techflyers.compose.file.explorer.screen.main.tab.nfile_tools.RemoteExplorerTab
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

@Composable
fun NetworkConnectionWizardScreen(tab: NetworkConnectionWizardTab) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val editing = tab.editingConnection

    var name by remember(editing?.id) { mutableStateOf(editing?.name ?: "") }
    var host by remember(editing?.id) { mutableStateOf(editing?.host ?: "") }
    var port by remember(editing?.id) { mutableStateOf((editing?.port ?: 21).toString()) }
    var username by remember(editing?.id) { mutableStateOf(editing?.username ?: "") }
    var password by remember(editing?.id) { mutableStateOf(editing?.password ?: "") }
    var showPassword by remember { mutableStateOf(false) }
    var rootPath by remember(editing?.id) { mutableStateOf(editing?.rootPath ?: "/") }
    var selectedType by remember(editing?.id) { mutableStateOf(editing?.type ?: "FTP") }
    var webdavProtocol by remember(editing?.id) { mutableStateOf(editing?.protocol ?: "http") }

    // Granular Protocol & Advanced Options (collapsed by default to keep UI clean)
    var transferMode by remember(editing?.id) { mutableStateOf(editing?.effectiveTransferMode ?: "BINARY") }
    var ftpConnectionMode by remember(editing?.id) { mutableStateOf(editing?.effectiveFtpConnectionMode ?: "PASSIVE") }
    var ftpSecurityMode by remember(editing?.id) { mutableStateOf(editing?.effectiveFtpSecurityMode ?: "PLAIN") }
    var authMethod by remember(editing?.id) {
        mutableStateOf(
            editing?.effectiveAuthMethod ?: when (editing?.type ?: "FTP") {
                "WebDav" -> "BASIC"
                "LAN/SMB" -> "NTLM"
                else -> "PASSWORD"
            }
        )
    }
    var privateKeyPath by remember(editing?.id) { mutableStateOf(editing?.effectivePrivateKeyPath ?: "") }
    var privateKeyPassphrase by remember(editing?.id) { mutableStateOf(editing?.effectivePrivateKeyPassphrase ?: "") }
    var showPassphrase by remember { mutableStateOf(false) }
    var showPrivateKeyPicker by remember { mutableStateOf(false) }
    var keepAliveIntervalSec by remember(editing?.id) { mutableStateOf((editing?.effectiveKeepAliveSec ?: 60).toString()) }
    var connectTimeoutSec by remember(editing?.id) { mutableStateOf((editing?.effectiveConnectTimeoutSec ?: 15).toString()) }
    var readTimeoutSec by remember(editing?.id) { mutableStateOf((editing?.effectiveReadTimeoutSec ?: 30).toString()) }
    var encoding by remember(editing?.id) { mutableStateOf(editing?.effectiveEncoding ?: "UTF-8") }
    var verifySslCert by remember(editing?.id) { mutableStateOf(editing?.effectiveVerifySslCert ?: true) }
    var sftpCompression by remember(editing?.id) { mutableStateOf(editing?.effectiveSftpCompression ?: false) }
    var smbDomain by remember(editing?.id) { mutableStateOf(editing?.effectiveSmbDomain ?: "") }
    var smbEnableSmb2 by remember(editing?.id) { mutableStateOf(editing?.effectiveSmbEnableSmb2 ?: true) }
    var showAdvancedOptions by remember(editing?.id) { mutableStateOf(false) }

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

    FileSelectionDialog(
        show = showPrivateKeyPicker,
        onDismissRequest = { showPrivateKeyPicker = false },
        onItemsSelected = { selected ->
            val first = selected.firstOrNull { it.isFile }
            if (first != null) {
                privateKeyPath = first.absolutePath
            } else {
                Toast.makeText(context, "Please select a private key file", Toast.LENGTH_SHORT).show()
            }
        }
    )

    fun buildCurrentModel(forSaving: Boolean): NetworkConnectionModel {
        val defaultPort = when (selectedType) {
            "FTP" -> if (ftpSecurityMode == "FTPS_IMPLICIT") 990 else 21
            "SFTP" -> 22
            "LAN/SMB" -> 445
            "WebDav" -> if (webdavProtocol == "https") 443 else 80
            else -> 21
        }
        val finalName = if (forSaving) {
            name.trim().ifEmpty { "$selectedType - ${host.trim()}" }
        } else {
            name.trim().ifEmpty { "Test Server" }
        }
        return NetworkConnectionModel(
            id = editing?.id ?: UUID.randomUUID().toString(),
            name = finalName,
            type = selectedType,
            host = host.trim(),
            port = port.trim().toIntOrNull() ?: defaultPort,
            username = username.trim(),
            password = password,
            rootPath = rootPath.trim().ifEmpty { "/" },
            protocol = webdavProtocol,
            transferMode = transferMode,
            ftpConnectionMode = ftpConnectionMode,
            ftpSecurityMode = ftpSecurityMode,
            authMethod = authMethod,
            privateKeyPath = privateKeyPath.trim(),
            privateKeyPassphrase = privateKeyPassphrase,
            keepAliveIntervalSec = keepAliveIntervalSec.toIntOrNull()?.coerceIn(0, 3600) ?: 60,
            connectTimeoutSec = connectTimeoutSec.toIntOrNull()?.coerceIn(3, 300) ?: 15,
            readTimeoutSec = readTimeoutSec.toIntOrNull()?.coerceIn(5, 600) ?: 30,
            encoding = encoding,
            verifySslCert = verifySslCert,
            sftpCompression = sftpCompression,
            smbDomain = smbDomain.trim(),
            smbEnableSmb2 = smbEnableSmb2
        )
    }

    val connectionTypes = listOf("FTP", "SFTP", "LAN/SMB", "WebDav")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Edit Mode Banner
        if (editing != null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.25f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Edit,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(20.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Editing: ${editing.name}",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${editing.type} • ${editing.host}:${editing.port}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                        )
                    }
                }
            }
        }

        // Card 1: Server Details & Protocol
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Section Header
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.Dns,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Column {
                        Text(
                            text = "Server Details",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Select protocol and server address",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Protocol Selector Chips
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    connectionTypes.forEach { type ->
                        val isSelected = (type == selectedType)
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                selectedType = type
                                port = when (type) {
                                    "FTP" -> if (ftpSecurityMode == "FTPS_IMPLICIT") "990" else "21"
                                    "SFTP" -> "22"
                                    "LAN/SMB" -> "445"
                                    "WebDav" -> if (webdavProtocol == "https") "443" else "80"
                                    else -> "21"
                                }
                                authMethod = when (type) {
                                    "FTP" -> if (authMethod in listOf("PASSWORD", "ANONYMOUS")) authMethod else "PASSWORD"
                                    "SFTP" -> if (authMethod in listOf("PASSWORD", "PRIVATE_KEY", "PASSWORD_AND_KEY")) authMethod else "PASSWORD"
                                    "WebDav" -> if (authMethod in listOf("BASIC", "BEARER", "NONE")) authMethod else "BASIC"
                                    "LAN/SMB" -> if (authMethod in listOf("NTLM", "GUEST", "ANONYMOUS")) authMethod else "NTLM"
                                    else -> "PASSWORD"
                                }
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = when (type) {
                                        "FTP" -> Icons.Rounded.SettingsEthernet
                                        "SFTP" -> Icons.Rounded.Terminal
                                        "WebDav" -> Icons.Rounded.Cloud
                                        else -> Icons.Rounded.FolderShared
                                    },
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            label = {
                                Text(
                                    when (type) {
                                        "LAN/SMB" -> "SMB / Windows"
                                        "WebDav" -> "WebDAV"
                                        else -> type
                                    }
                                )
                            }
                        )
                    }
                }

                // LAN/SMB Auto-Scanner Sub-card
                if (selectedType == "LAN/SMB") {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Local Network Discovery",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = if (isScanningSmb) "Searching local Wi-Fi for SMB servers..."
                                        else if (discoveredServers.isNotEmpty()) "${discoveredServers.size} device(s) found"
                                        else "Scan to auto-detect computers & NAS",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                if (isScanningSmb) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    FilledTonalButton(
                                        onClick = { smbScanner.startScan(scope) },
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                        modifier = Modifier.height(32.dp),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Refresh,
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = if (discoveredServers.isEmpty()) "Scan" else "Rescan",
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    }
                                }
                            }

                            if (isScanningSmb) {
                                LinearProgressIndicator(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(2.dp)
                                        .clip(RoundedCornerShape(1.dp))
                                )
                            }

                            if (discoveredServers.isNotEmpty()) {
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    discoveredServers.forEach { server ->
                                        val serverAddr = server.address.hostAddress ?: server.host ?: ""
                                        val isSelected = host == serverAddr || host == server.host
                                        Surface(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(10.dp))
                                                .clickable {
                                                    host = serverAddr
                                                    if (name.isBlank() || name.startsWith("LAN/SMB")) {
                                                        name = server.displayName
                                                    }
                                                    port = "445"
                                                    Toast.makeText(
                                                        context,
                                                        "Selected ${server.displayName} ($serverAddr)",
                                                        Toast.LENGTH_SHORT
                                                    ).show()
                                                },
                                            shape = RoundedCornerShape(10.dp),
                                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                            border = if (isSelected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Rounded.Computer,
                                                    contentDescription = null,
                                                    tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.size(22.dp)
                                                )
                                                Spacer(modifier = Modifier.width(10.dp))
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = server.displayName,
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        fontWeight = FontWeight.SemiBold,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                    Text(
                                                        text = "$serverAddr • ${server.discoveryMethod}",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                                if (isSelected) {
                                                    Icon(
                                                        imageVector = Icons.Rounded.CheckCircle,
                                                        contentDescription = "Selected",
                                                        tint = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Connection Name Field
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Connection Name (Optional)") },
                    placeholder = { Text("e.g. Home NAS or Office Server") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                // Host & Port Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = host,
                        onValueChange = { host = it },
                        label = { Text("Host or IP Address") },
                        placeholder = { Text("192.168.1.100") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = port,
                        onValueChange = { port = it },
                        label = { Text("Port") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.width(95.dp)
                    )
                }

                // Remote Directory Path
                OutlinedTextField(
                    value = rootPath,
                    onValueChange = { rootPath = it },
                    label = { Text("Remote Path") },
                    placeholder = {
                        Text(if (selectedType == "LAN/SMB") "/ShareName (optional)" else "/")
                    },
                    supportingText = if (selectedType == "LAN/SMB") {
                        { Text("Leave '/' for server root (all shares), or specify '/Share' or '/Share/Folder'") }
                    } else null,
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // Card 2: Authentication
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Section Header
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.Key,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Column {
                        Text(
                            text = "Authentication",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Credentials & access control",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Auth Method Chips
                val availableAuthMethods = when (selectedType) {
                    "FTP" -> listOf("PASSWORD" to "Password", "ANONYMOUS" to "Anonymous")
                    "SFTP" -> listOf(
                        "PASSWORD" to "Password",
                        "PRIVATE_KEY" to "SSH Private Key",
                        "PASSWORD_AND_KEY" to "Key + Password"
                    )
                    "WebDav" -> listOf(
                        "BASIC" to "Username & Password",
                        "BEARER" to "Bearer Token",
                        "NONE" to "Anonymous"
                    )
                    "LAN/SMB" -> listOf(
                        "NTLM" to "Username & Password",
                        "GUEST" to "Guest / Anonymous"
                    )
                    else -> listOf("PASSWORD" to "Password")
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    availableAuthMethods.forEach { (methodKey, labelText) ->
                        FilterChip(
                            selected = authMethod == methodKey,
                            onClick = { authMethod = methodKey },
                            label = { Text(labelText) }
                        )
                    }
                }

                if (authMethod !in listOf("ANONYMOUS", "GUEST", "NONE")) {
                    if (authMethod != "BEARER") {
                        OutlinedTextField(
                            value = username,
                            onValueChange = { username = it },
                            label = { Text("Username") },
                            placeholder = { Text("e.g. admin") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    if (authMethod != "PRIVATE_KEY") {
                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            label = { Text(if (authMethod == "BEARER") "Access Token" else "Password") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { showPassword = !showPassword }) {
                                    Icon(
                                        imageVector = if (showPassword) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                        contentDescription = if (showPassword) "Hide password" else "Show password"
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    if (selectedType == "SFTP" && authMethod in listOf("PRIVATE_KEY", "PASSWORD_AND_KEY")) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = privateKeyPath,
                                onValueChange = { privateKeyPath = it },
                                label = { Text("Private Key File") },
                                placeholder = { Text(".pem, id_rsa, id_ed25519") },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f)
                            )
                            FilledTonalIconButton(
                                onClick = { showPrivateKeyPicker = true },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.size(52.dp)
                            ) {
                                Icon(Icons.Rounded.FolderOpen, contentDescription = "Browse Key File")
                            }
                        }

                        OutlinedTextField(
                            value = privateKeyPassphrase,
                            onValueChange = { privateKeyPassphrase = it },
                            label = { Text("Key Passphrase (Optional)") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            visualTransformation = if (showPassphrase) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { showPassphrase = !showPassphrase }) {
                                    Icon(
                                        imageVector = if (showPassphrase) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                        contentDescription = if (showPassphrase) "Hide passphrase" else "Show passphrase"
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }

        // Card 3: Advanced Settings (Collapsible)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showAdvancedOptions = !showAdvancedOptions },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.tertiaryContainer,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Rounded.Tune,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Column {
                            Text(
                                text = "Advanced Settings",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Timeouts, encoding, SSL & transfer modes",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    IconButton(onClick = { showAdvancedOptions = !showAdvancedOptions }) {
                        Icon(
                            imageVector = if (showAdvancedOptions) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                            contentDescription = if (showAdvancedOptions) "Collapse" else "Expand"
                        )
                    }
                }

                AnimatedVisibility(visible = showAdvancedOptions) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                        // Transfer Mode (FTP / WebDAV / SFTP)
                        if (selectedType in listOf("FTP", "WebDav", "SFTP")) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = "Transfer Mode",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Row(
                                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    listOf(
                                        "BINARY" to "Binary (Default)",
                                        "ASCII" to "ASCII (Text)",
                                        "AUTO" to "Auto Detect"
                                    ).forEach { (modeKey, modeLabel) ->
                                        FilterChip(
                                            selected = transferMode == modeKey,
                                            onClick = { transferMode = modeKey },
                                            label = { Text(modeLabel) }
                                        )
                                    }
                                }
                            }
                        }

                        // FTP Specific Options
                        if (selectedType == "FTP") {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = "FTP Connection Mode",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    FilterChip(
                                        selected = ftpConnectionMode == "PASSIVE",
                                        onClick = { ftpConnectionMode = "PASSIVE" },
                                        label = { Text("Passive (PASV)") }
                                    )
                                    FilterChip(
                                        selected = ftpConnectionMode == "ACTIVE",
                                        onClick = { ftpConnectionMode = "ACTIVE" },
                                        label = { Text("Active (PORT)") }
                                    )
                                }
                            }

                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = "FTP Security",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Row(
                                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    listOf(
                                        "PLAIN" to "Plain FTP",
                                        "FTPS_EXPLICIT" to "Explicit FTPS (TLS)",
                                        "FTPS_IMPLICIT" to "Implicit FTPS (Port 990)"
                                    ).forEach { (secKey, secLabel) ->
                                        FilterChip(
                                            selected = ftpSecurityMode == secKey,
                                            onClick = {
                                                ftpSecurityMode = secKey
                                                if (secKey == "FTPS_IMPLICIT" && port == "21") port = "990"
                                                if (secKey != "FTPS_IMPLICIT" && port == "990") port = "21"
                                            },
                                            label = { Text(secLabel) }
                                        )
                                    }
                                }
                            }
                        }

                        // WebDAV Scheme (HTTP / HTTPS)
                        if (selectedType == "WebDav") {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = "WebDAV Scheme",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    FilterChip(
                                        selected = webdavProtocol == "https",
                                        onClick = {
                                            webdavProtocol = "https"
                                            if (port == "80") port = "443"
                                        },
                                        label = { Text("HTTPS (Secure)") }
                                    )
                                    FilterChip(
                                        selected = webdavProtocol == "http",
                                        onClick = {
                                            webdavProtocol = "http"
                                            if (port == "443") port = "80"
                                        },
                                        label = { Text("HTTP") }
                                    )
                                }
                            }
                        }

                        // SSL Certificate Verification
                        if ((selectedType == "FTP" && ftpSecurityMode != "PLAIN") ||
                            (selectedType == "WebDav" && webdavProtocol == "https")
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Verify SSL/TLS Certificate",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = "Allow self-signed certificates when disabled",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = verifySslCert,
                                    onCheckedChange = { verifySslCert = it }
                                )
                            }
                        }

                        // SFTP Compression
                        if (selectedType == "SFTP") {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "SSH Stream Compression (zlib)",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = "Compress data to speed up slower connections",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = sftpCompression,
                                    onCheckedChange = { sftpCompression = it }
                                )
                            }
                        }

                        // SMB Domain & SMB2/3
                        if (selectedType == "LAN/SMB") {
                            OutlinedTextField(
                                value = smbDomain,
                                onValueChange = { smbDomain = it },
                                label = { Text("Domain / Workgroup (Optional)") },
                                placeholder = { Text("WORKGROUP") },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Enable SMB2 / SMB3",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = "Modern SMB dialect (turn off for legacy SMBv1)",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = smbEnableSmb2,
                                    onCheckedChange = { smbEnableSmb2 = it }
                                )
                            }
                        }

                        // Timeouts & Keepalive: 2 clean rows
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "Timeouts & Keepalive",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OutlinedTextField(
                                    value = connectTimeoutSec,
                                    onValueChange = { connectTimeoutSec = it.filter { ch -> ch.isDigit() } },
                                    label = { Text("Connect (sec)") },
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedTextField(
                                    value = readTimeoutSec,
                                    onValueChange = { readTimeoutSec = it.filter { ch -> ch.isDigit() } },
                                    label = { Text("Read (sec)") },
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            OutlinedTextField(
                                value = keepAliveIntervalSec,
                                onValueChange = { keepAliveIntervalSec = it.filter { ch -> ch.isDigit() } },
                                label = { Text("Keepalive Interval (sec)") },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        // Encoding
                        if (selectedType in listOf("FTP", "WebDav", "SFTP")) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = "Character Encoding",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Row(
                                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    listOf("UTF-8", "ISO-8859-1", "Windows-1252").forEach { enc ->
                                        FilterChip(
                                            selected = encoding.equals(enc, ignoreCase = true),
                                            onClick = { encoding = enc },
                                            label = { Text(enc) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Action Buttons (Test & Save/Update)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = {
                    isTesting = true
                    scope.launch {
                        val connectionModel = buildCurrentModel(forSaving = false)
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
                                Toast.makeText(
                                    context,
                                    "Connection Failed: ${testError ?: "Server unreachable"}",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp),
                enabled = !isTesting && host.isNotBlank()
            ) {
                if (isTesting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Testing...")
                } else {
                    Icon(Icons.Rounded.Bolt, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Test Connection")
                }
            }

            Button(
                onClick = {
                    val connectionModel = buildCurrentModel(forSaving = true)
                    NetworkConnectionsService.saveConnection(context, connectionModel)

                    // Update any open tabs referencing this connection ID
                    globalClass.mainActivityManager.state.value.tabs.forEach { openTab ->
                        if (openTab is RemoteExplorerTab && openTab.connection.id == connectionModel.id) {
                            openTab.connection = connectionModel
                        } else if (openTab is FilesTab) {
                            val activeRemote = openTab.activeFolder as? RemoteFileHolder
                            if (activeRemote != null && activeRemote.connection.id == connectionModel.id) {
                                openTab.openFolder(
                                    RemoteFileHolder(
                                        connection = connectionModel,
                                        remotePath = activeRemote.remotePath,
                                        isConnectionRoot = activeRemote.isConnectionRoot
                                    ),
                                    rememberListState = false
                                )
                            }
                        }
                    }

                    Toast.makeText(
                        context,
                        if (editing != null) "Connection Updated!" else "Connection Saved!",
                        Toast.LENGTH_SHORT
                    ).show()

                    if (tab.parentTab != null) {
                        globalClass.mainActivityManager.canExit()
                    } else {
                        globalClass.mainActivityManager.replaceCurrentTabWith(HomeTab())
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp),
                enabled = host.isNotBlank()
            ) {
                Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(if (editing != null) "Update Connection" else "Save Connection")
            }
        }
    }
}
