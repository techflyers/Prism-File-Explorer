package com.raival.compose.file.explorer.screen.preferences.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.raival.compose.file.explorer.App.Companion.globalClass
import com.raival.compose.file.explorer.R
import com.raival.compose.file.explorer.screen.terminal.isTerminalInstalled
import com.raival.compose.file.explorer.screen.terminal.uninstallTerminal

@Composable
fun TerminalContainer() {
    val context = LocalContext.current
    var confirmUninstall by remember { mutableStateOf(false) }
    var showCodeRunners by remember { mutableStateOf(false) }
    val installed = remember { isTerminalInstalled(context) }

    Container(title = stringResource(R.string.terminal)) {
        PreferenceItem(
            label = stringResource(R.string.terminal_status),
            supportingText = if (installed) {
                stringResource(R.string.terminal_installed)
            } else {
                stringResource(R.string.terminal_not_installed)
            },
            icon = Icons.Rounded.Terminal
        )

        HorizontalDivider(
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            thickness = 3.dp
        )

        PreferenceItem(
            label = stringResource(R.string.custom_code_runners),
            supportingText = stringResource(R.string.custom_code_runners_desc),
            icon = Icons.Rounded.Code,
            onClick = { showCodeRunners = true }
        )

        HorizontalDivider(
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            thickness = 3.dp
        )

        PreferenceItem(
            label = stringResource(R.string.uninstall_terminal),
            supportingText = stringResource(R.string.uninstall_terminal_desc),
            icon = Icons.Rounded.DeleteForever,
            onClick = { confirmUninstall = true }
        )
    }

    if (showCodeRunners) {
        CustomCodeRunnersDialog(
            onDismiss = { showCodeRunners = false }
        )
    }

    if (confirmUninstall) {
        AlertDialog(
            onDismissRequest = { confirmUninstall = false },
            icon = { Icon(Icons.Rounded.Warning, contentDescription = null) },
            title = { Text(stringResource(R.string.uninstall_terminal)) },
            text = { Text(stringResource(R.string.uninstall_terminal_warning)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmUninstall = false
                    uninstallTerminal(context)
                    globalClass.showMsg(R.string.terminal_uninstalled)
                }) { Text(stringResource(R.string.uninstall)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmUninstall = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

private data class RunnerEntry(val ext: String, val command: String)

@Composable
private fun CustomCodeRunnersDialog(onDismiss: () -> Unit) {
    val prefs = globalClass.preferencesManager
    val runners = remember {
        val json = prefs.customCodeRunners
        val map: Map<String, String> = try {
            if (json.isBlank() || json == "{}") emptyMap()
            else Gson().fromJson(json, object : TypeToken<Map<String, String>>() {}.type)
        } catch (_: Exception) { emptyMap() }
        mutableStateListOf(*map.map { RunnerEntry(it.key, it.value) }.toTypedArray())
    }

    var newExt by remember { mutableStateOf("") }
    var newCmd by remember { mutableStateOf("") }

    fun saveRunners() {
        val map = runners.associate { it.ext.lowercase().trim() to it.command.trim() }
        prefs.customCodeRunners = Gson().toJson(map)
    }

    AlertDialog(
        onDismissRequest = {
            saveRunners()
            onDismiss()
        },
        title = { Text(stringResource(R.string.custom_code_runners)) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Use {file} for file path, {dir} for directory, {name} for filename without extension",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))

                if (runners.isEmpty()) {
                    Text(
                        text = stringResource(R.string.no_custom_runners),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.height(200.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(runners.toList(), key = { it.ext }) { entry ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = ".${entry.ext}",
                                        style = MaterialTheme.typography.titleSmall
                                    )
                                    Text(
                                        text = entry.command,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton(onClick = {
                                    runners.remove(entry)
                                    saveRunners()
                                }) {
                                    Icon(
                                        Icons.Rounded.DeleteForever,
                                        contentDescription = stringResource(R.string.delete_runner),
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 8.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow
                )

                Text(
                    text = stringResource(R.string.add_runner),
                    style = MaterialTheme.typography.titleSmall
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = newExt,
                        onValueChange = { newExt = it.replace(".", "").take(10) },
                        label = { Text(stringResource(R.string.extension_label)) },
                        singleLine = true,
                        modifier = Modifier.width(110.dp)
                    )
                    OutlinedTextField(
                        value = newCmd,
                        onValueChange = { newCmd = it },
                        label = { Text(stringResource(R.string.command_template_label)) },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        },
        confirmButton = {
            Row {
                if (newExt.isNotBlank() && newCmd.isNotBlank()) {
                    TextButton(onClick = {
                        val ext = newExt.lowercase().trim()
                        runners.removeAll { it.ext == ext }
                        runners.add(RunnerEntry(ext, newCmd.trim()))
                        newExt = ""
                        newCmd = ""
                        saveRunners()
                    }) { Text(stringResource(R.string.add_runner)) }
                }
                TextButton(onClick = {
                    saveRunners()
                    onDismiss()
                }) { Text(stringResource(R.string.save)) }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}
