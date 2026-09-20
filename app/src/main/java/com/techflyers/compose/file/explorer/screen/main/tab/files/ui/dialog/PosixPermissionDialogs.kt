package com.techflyers.compose.file.explorer.screen.main.tab.files.ui.dialog

import android.system.Os
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.techflyers.compose.file.explorer.App.Companion.globalClass
import com.techflyers.compose.file.explorer.R
import com.techflyers.compose.file.explorer.common.showMsg
import com.techflyers.compose.file.explorer.common.ui.Space
import com.techflyers.compose.file.explorer.screen.main.tab.files.holder.ContentHolder
import com.techflyers.compose.file.explorer.screen.main.tab.files.holder.LocalFileHolder
import com.techflyers.compose.file.explorer.screen.main.tab.files.posix.PosixFileMode
import com.techflyers.compose.file.explorer.screen.main.tab.files.posix.PosixFileModeBit
import com.techflyers.compose.file.explorer.screen.main.tab.files.posix.PosixPrincipalLookup
import com.techflyers.compose.file.explorer.screen.main.tab.files.posix.SELinuxManager
import com.techflyers.compose.file.explorer.screen.main.tab.files.posix.toInt
import com.techflyers.compose.file.explorer.screen.main.tab.files.posix.toModeString
import com.techflyers.compose.file.explorer.screen.main.tab.files.shizuku.ShizukuManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.EnumSet

@Composable
fun SetModeDialog(
    file: ContentHolder,
    onDismissRequest: () -> Unit,
    onPermissionsChanged: () -> Unit
) {
    val initialModeInt = (file as? LocalFileHolder)?.posixMode ?: 0b111101101
    val initialBits = PosixFileMode.fromInt(initialModeInt)

    var modeBits by remember { mutableStateOf(EnumSet.copyOf(initialBits)) }
    var recursive by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    val currentModeInt = modeBits.toInt()
    val octalString = String.format("%04o", currentModeInt and 0xFFF)
    val modeString = modeBits.toModeString()

    fun toggleBit(bit: PosixFileModeBit) {
        val newSet = EnumSet.copyOf(modeBits)
        if (newSet.contains(bit)) {
            newSet.remove(bit)
        } else {
            newSet.add(bit)
        }
        modeBits = newSet
    }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = {
            Text(
                text = stringResource(R.string.file_properties_permission_set_mode_title),
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Live preview badge
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            MaterialTheme.colorScheme.surfaceContainerHigh,
                            RoundedCornerShape(8.dp)
                        )
                        .padding(12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "$modeString ($octalString)",
                        style = MaterialTheme.typography.titleMedium,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Space(4.dp)

                // Owner row
                Text(
                    text = stringResource(R.string.file_properties_permission_owner),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
                PermissionBitRow(
                    readChecked = modeBits.contains(PosixFileModeBit.OWNER_READ),
                    writeChecked = modeBits.contains(PosixFileModeBit.OWNER_WRITE),
                    executeChecked = modeBits.contains(PosixFileModeBit.OWNER_EXECUTE),
                    onToggleRead = { toggleBit(PosixFileModeBit.OWNER_READ) },
                    onToggleWrite = { toggleBit(PosixFileModeBit.OWNER_WRITE) },
                    onToggleExecute = { toggleBit(PosixFileModeBit.OWNER_EXECUTE) }
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                // Group row
                Text(
                    text = stringResource(R.string.file_properties_permission_group),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
                PermissionBitRow(
                    readChecked = modeBits.contains(PosixFileModeBit.GROUP_READ),
                    writeChecked = modeBits.contains(PosixFileModeBit.GROUP_WRITE),
                    executeChecked = modeBits.contains(PosixFileModeBit.GROUP_EXECUTE),
                    onToggleRead = { toggleBit(PosixFileModeBit.GROUP_READ) },
                    onToggleWrite = { toggleBit(PosixFileModeBit.GROUP_WRITE) },
                    onToggleExecute = { toggleBit(PosixFileModeBit.GROUP_EXECUTE) }
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                // Others row
                Text(
                    text = stringResource(R.string.others),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
                PermissionBitRow(
                    readChecked = modeBits.contains(PosixFileModeBit.OTHERS_READ),
                    writeChecked = modeBits.contains(PosixFileModeBit.OTHERS_WRITE),
                    executeChecked = modeBits.contains(PosixFileModeBit.OTHERS_EXECUTE),
                    onToggleRead = { toggleBit(PosixFileModeBit.OTHERS_READ) },
                    onToggleWrite = { toggleBit(PosixFileModeBit.OTHERS_WRITE) },
                    onToggleExecute = { toggleBit(PosixFileModeBit.OTHERS_EXECUTE) }
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                // Special bits
                Text(
                    text = stringResource(R.string.special),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SpecialBitCheck(
                        label = stringResource(R.string.set_uid),
                        checked = modeBits.contains(PosixFileModeBit.SET_USER_ID),
                        onCheckedChange = { toggleBit(PosixFileModeBit.SET_USER_ID) }
                    )
                    SpecialBitCheck(
                        label = stringResource(R.string.set_gid),
                        checked = modeBits.contains(PosixFileModeBit.SET_GROUP_ID),
                        onCheckedChange = { toggleBit(PosixFileModeBit.SET_GROUP_ID) }
                    )
                    SpecialBitCheck(
                        label = stringResource(R.string.sticky_bit),
                        checked = modeBits.contains(PosixFileModeBit.STICKY),
                        onCheckedChange = { toggleBit(PosixFileModeBit.STICKY) }
                    )
                }

                if (file.isFolder) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Checkbox(
                            checked = recursive,
                            onCheckedChange = { recursive = it }
                        )
                        Text(
                            text = stringResource(R.string.file_properties_permission_recursive),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    coroutineScope.launch(Dispatchers.IO) {
                        var success = false
                        try {
                            Os.chmod(file.uniquePath, currentModeInt)
                            success = true
                            if (recursive && file.isFolder) {
                                File(file.uniquePath).walkTopDown().drop(1).forEach {
                                    try { Os.chmod(it.absolutePath, currentModeInt) } catch (_: Exception) {}
                                }
                            }
                        } catch (_: Exception) {
                            success = false
                        }

                        if (!success && ShizukuManager.isPrivileged) {
                            success = ShizukuManager.chmod(file.uniquePath, octalString, recursive)
                        }

                        withContext(Dispatchers.Main) {
                            if (success) {
                                showMsg(globalClass.getString(R.string.done))
                                onPermissionsChanged()
                                onDismissRequest()
                            } else {
                                showMsg("Failed to change permissions (permission denied)")
                            }
                        }
                    }
                }
            ) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
private fun PermissionBitRow(
    readChecked: Boolean,
    writeChecked: Boolean,
    executeChecked: Boolean,
    onToggleRead: () -> Unit,
    onToggleWrite: () -> Unit,
    onToggleExecute: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = readChecked, onCheckedChange = { onToggleRead() })
            Text(stringResource(R.string.read), fontSize = 13.sp)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = writeChecked, onCheckedChange = { onToggleWrite() })
            Text(stringResource(R.string.write), fontSize = 13.sp)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = executeChecked, onCheckedChange = { onToggleExecute() })
            Text(stringResource(R.string.execute), fontSize = 13.sp)
        }
    }
}

@Composable
private fun SpecialBitCheck(
    label: String,
    checked: Boolean,
    onCheckedChange: () -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = { onCheckedChange() })
        Text(label, fontSize = 12.sp)
    }
}

@Composable
fun SetOwnerGroupDialog(
    file: ContentHolder,
    isOwner: Boolean,
    onDismissRequest: () -> Unit,
    onChanged: () -> Unit
) {
    val localHolder = file as? LocalFileHolder
    val initialId = if (isOwner) (localHolder?.posixUid ?: 0) else (localHolder?.posixGid ?: 0)
    var idText by remember { mutableStateOf(initialId.toString()) }
    var recursive by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    val parsedId = idText.toIntOrNull()
    val resolvedName = parsedId?.let {
        if (isOwner) PosixPrincipalLookup.getUserName(it)
        else PosixPrincipalLookup.getGroupName(it)
    } ?: ""

    val titleRes = if (isOwner) R.string.file_properties_permission_set_owner_title
    else R.string.file_properties_permission_set_group_title

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(stringResource(titleRes)) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = idText,
                    onValueChange = { idText = it.filter { c -> c.isDigit() } },
                    label = { Text("ID") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                if (resolvedName.isNotEmpty()) {
                    Text(
                        text = "Name: $resolvedName",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium
                    )
                }

                Text(
                    text = "Quick Select:",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    SuggestionChip(
                        onClick = { idText = "0" },
                        label = { Text("root (0)", fontSize = 11.sp) }
                    )
                    SuggestionChip(
                        onClick = { idText = "1000" },
                        label = { Text("system (1000)", fontSize = 11.sp) }
                    )
                    SuggestionChip(
                        onClick = { idText = "9997" },
                        label = { Text("everybody (9997)", fontSize = 11.sp) }
                    )
                }

                if (file.isFolder) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Checkbox(checked = recursive, onCheckedChange = { recursive = it })
                        Text(
                            text = stringResource(R.string.file_properties_permission_recursive),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = parsedId != null,
                onClick = {
                    val targetId = parsedId ?: return@Button
                    coroutineScope.launch(Dispatchers.IO) {
                        var success = false
                        try {
                            val uid = if (isOwner) targetId else -1
                            val gid = if (!isOwner) targetId else -1
                            Os.chown(file.uniquePath, uid, gid)
                            success = true
                            if (recursive && file.isFolder) {
                                File(file.uniquePath).walkTopDown().drop(1).forEach {
                                    try { Os.chown(it.absolutePath, uid, gid) } catch (_: Exception) {}
                                }
                            }
                        } catch (_: Exception) {
                            success = false
                        }

                        if (!success && ShizukuManager.isPrivileged) {
                            val uid = if (isOwner) targetId else (localHolder?.posixUid ?: 0)
                            val gid = if (!isOwner) targetId else (localHolder?.posixGid ?: -1)
                            success = ShizukuManager.chown(file.uniquePath, uid, gid, recursive)
                        }

                        withContext(Dispatchers.Main) {
                            if (success) {
                                showMsg(globalClass.getString(R.string.done))
                                onChanged()
                                onDismissRequest()
                            } else {
                                showMsg("Failed to change ownership (permission denied)")
                            }
                        }
                    }
                }
            ) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
fun SetSeLinuxContextDialog(
    file: ContentHolder,
    onDismissRequest: () -> Unit,
    onContextChanged: () -> Unit
) {
    val initialContext = remember { SELinuxManager.getFileContext(file.uniquePath) ?: "" }
    var contextText by remember { mutableStateOf(initialContext) }
    var recursive by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismissRequest,
        icon = { Icon(Icons.Rounded.Security, contentDescription = null) },
        title = {
            Text(stringResource(R.string.file_properties_permission_set_selinux_context_title))
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = contextText,
                    onValueChange = { contextText = it },
                    label = { Text(stringResource(R.string.file_properties_permission_selinux_context)) },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3
                )

                if (file.isFolder) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Checkbox(checked = recursive, onCheckedChange = { recursive = it })
                        Text(
                            text = stringResource(R.string.file_properties_permission_recursive),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }

                OutlinedButton(
                    onClick = {
                        coroutineScope.launch(Dispatchers.IO) {
                            val success = SELinuxManager.restorecon(file.uniquePath, recursive)
                            withContext(Dispatchers.Main) {
                                if (success) {
                                    showMsg(globalClass.getString(R.string.done))
                                    onContextChanged()
                                    onDismissRequest()
                                } else {
                                    showMsg("Failed to restore SELinux context")
                                }
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Rounded.Restore, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                    Text(stringResource(R.string.file_properties_permission_set_selinux_context_restore))
                }
            }
        },
        confirmButton = {
            Button(
                enabled = contextText.isNotBlank(),
                onClick = {
                    coroutineScope.launch(Dispatchers.IO) {
                        val success = SELinuxManager.setFileContext(file.uniquePath, contextText.trim(), recursive)
                        withContext(Dispatchers.Main) {
                            if (success) {
                                showMsg(globalClass.getString(R.string.done))
                                onContextChanged()
                                onDismissRequest()
                            } else {
                                showMsg("Failed to set SELinux context (permission denied)")
                            }
                        }
                    }
                }
            ) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}
