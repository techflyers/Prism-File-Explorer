package com.raival.compose.file.explorer.screen.main.tab.nfile_tools.ui

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.NetworkWifi
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
                                    "WebDav" -> "80"
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
                RadioButton(
                    selected = webdavProtocol == "http",
                    onClick = { webdavProtocol = "http" }
                )
                Text("HTTP")
                Spacer(modifier = Modifier.width(20.dp))
                RadioButton(
                    selected = webdavProtocol == "https",
                    onClick = { webdavProtocol = "https" }
                )
                Text("HTTPS")
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
                        val connectionModel = NetworkConnectionModel(
                            id = UUID.randomUUID().toString(),
                            name = name.ifEmpty { "Test Server" },
                            type = selectedType,
                            host = host,
                            port = port.toIntOrNull() ?: 21,
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

                        val success = withContext(Dispatchers.IO) {
                            try {
                                client.connect()
                                client.disconnect()
                                true
                            } catch (e: Exception) {
                                e.printStackTrace()
                                false
                            }
                        }

                        withContext(Dispatchers.Main) {
                            isTesting = false
                            if (success) {
                                Toast.makeText(context, "Connection Successful!", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "Failed to connect to server.", Toast.LENGTH_LONG).show()
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
                    val connectionModel = NetworkConnectionModel(
                        id = UUID.randomUUID().toString(),
                        name = finalName,
                        type = selectedType,
                        host = host,
                        port = port.toIntOrNull() ?: 21,
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
