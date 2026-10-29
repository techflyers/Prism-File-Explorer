package com.techflyers.compose.file.explorer.screen.main.tab.files.ui.dialog

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.EnhancedEncryption
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.techflyers.compose.file.explorer.App.Companion.globalClass
import com.techflyers.compose.file.explorer.R
import com.techflyers.compose.file.explorer.common.showMsg
import com.techflyers.compose.file.explorer.common.toFormattedSize
import com.techflyers.compose.file.explorer.screen.main.tab.files.service.FolderLockStore
import com.techflyers.compose.file.explorer.screen.main.tab.files.service.FolderLockType
import com.techflyers.compose.file.explorer.screen.main.tab.nfile_tools.ui.PinKeypad
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun FolderLockDialog(
    path: String,
    mode: FolderLockDialogMode,
    onUnlocked: () -> Unit,
    onDismiss: () -> Unit
) {
    val prefs = globalClass.preferencesManager
    val scope = rememberCoroutineScope()
    val lock = remember(path) { FolderLockStore.matchingLock(path) }
    var usePattern by remember {
        mutableStateOf(lock?.type == FolderLockType.PATTERN || mode == FolderLockDialogMode.CREATE)
    }
    var pin by remember { mutableStateOf("") }
    var pattern by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    var isProcessing by remember { mutableStateOf(false) }

    var folderSizeBytes by remember(path) { mutableLongStateOf(lock?.folderSizeBytes ?: 0L) }
    var isCalculatingSize by remember(path) { mutableStateOf(mode == FolderLockDialogMode.CREATE && path.isNotBlank()) }
    var encryptFolder by remember(path) {
        mutableStateOf(lock?.isEncrypted ?: prefs.encryptLockedFolders)
    }
    var autoEncryptedTriggered by remember(path) {
        mutableStateOf(lock?.autoEncryptedBySize == true)
    }

    LaunchedEffect(path, mode) {
        if (path.isNotBlank() && mode == FolderLockDialogMode.CREATE) {
            isCalculatingSize = true
            val computed = withContext(Dispatchers.IO) {
                FolderLockStore.calculateFolderSize(path)
            }
            folderSizeBytes = computed
            isCalculatingSize = false
            if (FolderLockStore.shouldAutoEncrypt(computed)) {
                encryptFolder = true
                autoEncryptedTriggered = true
            }
        }
    }

    AlertDialog(
        onDismissRequest = { if (!isProcessing) onDismiss() },
        icon = {
            Icon(
                imageVector = if (encryptFolder || lock?.isEncrypted == true) {
                    Icons.Rounded.EnhancedEncryption
                } else {
                    Icons.Rounded.Lock
                },
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text(
                when (mode) {
                    FolderLockDialogMode.UNLOCK -> stringResource(R.string.unlock_folder)
                    FolderLockDialogMode.CREATE -> stringResource(R.string.lock_folder)
                    FolderLockDialogMode.RESET_CONFIRM -> stringResource(R.string.reset_folder_locks)
                    FolderLockDialogMode.REMOVE -> stringResource(R.string.remove_folder_lock)
                }
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // Folder Access & Encryption Status Banner
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.Security,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = when (mode) {
                                FolderLockDialogMode.CREATE -> {
                                    val sizeLabel = if (isCalculatingSize) "Calculating size…" else folderSizeBytes.toFormattedSize()
                                    "Folder Size: $sizeLabel"
                                }
                                FolderLockDialogMode.RESET_CONFIRM -> FolderLockStore.getStatusSummary()
                                else -> FolderLockStore.getDetailedFolderSecurityStatus(path)
                            },
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    if (mode == FolderLockDialogMode.CREATE) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Encrypt folder contents (AES-256)",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = if (autoEncryptedTriggered) {
                                        "Auto-enabled because folder size is ≥ 100 MB. Prevents other apps from accessing contents."
                                    } else if (encryptFolder) {
                                        "Contents will be encrypted so other apps cannot access them."
                                    } else {
                                        "PIN/Pattern lock inside Prism only (files remain accessible to other apps)."
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    fontSize = 11.sp,
                                    color = if (autoEncryptedTriggered) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                            }
                            Switch(
                                checked = encryptFolder,
                                onCheckedChange = {
                                    encryptFolder = it
                                    if (!it) autoEncryptedTriggered = false
                                }
                            )
                        }
                    } else if (lock != null) {
                        Text(
                            text = if (lock.isEncrypted) {
                                "This folder is AES-256 encrypted. Unlocking decrypts its files for access."
                            } else {
                                "This folder is protected by a Prism ${lock.type.name} lock."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (mode == FolderLockDialogMode.CREATE || mode == FolderLockDialogMode.RESET_CONFIRM) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { usePattern = false }) {
                            Text(
                                text = stringResource(R.string.pin),
                                fontWeight = if (!usePattern) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                        TextButton(onClick = { usePattern = true }) {
                            Text(
                                text = stringResource(R.string.pattern),
                                fontWeight = if (usePattern) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }

                if (isProcessing) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.5.dp)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = if (mode == FolderLockDialogMode.CREATE) {
                                "Encrypting & locking folder…"
                            } else {
                                "Decrypting & unlocking folder…"
                            },
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                } else if (usePattern && (mode == FolderLockDialogMode.CREATE || mode == FolderLockDialogMode.RESET_CONFIRM || lock?.type == FolderLockType.PATTERN)) {
                    PatternPad(pattern) { index ->
                        if (!pattern.contains(index.toString())) pattern += index.toString()
                    }
                    TextButton(onClick = { pattern = "" }) { Text(stringResource(R.string.clear)) }
                } else {
                    OutlinedTextField(
                        value = pin,
                        onValueChange = { if (it.length <= 8 && it.all(Char::isDigit)) pin = it },
                        label = { Text(stringResource(R.string.pin)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    PinKeypad(
                        onKeyPress = { if (pin.length < 8) pin += it },
                        onBackspace = { if (pin.isNotEmpty()) pin = pin.dropLast(1) }
                    )
                }
                if (error) {
                    Text(
                        text = stringResource(R.string.incorrect_pin_or_pattern),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !isProcessing,
                onClick = {
                    val secret = if (usePattern || lock?.type == FolderLockType.PATTERN) pattern else pin
                    if (secret.isBlank()) {
                        error = true
                        return@TextButton
                    }
                    isProcessing = true
                    error = false
                    scope.launch {
                        when (mode) {
                            FolderLockDialogMode.CREATE -> {
                                val entry = withContext(Dispatchers.IO) {
                                    FolderLockStore.lock(
                                        path = path,
                                        secret = secret,
                                        type = if (usePattern) FolderLockType.PATTERN else FolderLockType.PIN,
                                        encryptContents = encryptFolder,
                                        precomputedSizeBytes = folderSizeBytes
                                    )
                                }
                                isProcessing = false
                                showMsg(
                                    if (entry.isEncrypted) {
                                        "Folder locked & AES-256 encrypted (${entry.encryptedFileCount} files protected)"
                                    } else {
                                        "Folder locked"
                                    }
                                )
                                onUnlocked()
                            }
                            FolderLockDialogMode.UNLOCK -> {
                                val ok = withContext(Dispatchers.IO) {
                                    FolderLockStore.unlock(path, secret)
                                }
                                isProcessing = false
                                if (ok) {
                                    onUnlocked()
                                } else {
                                    error = true
                                }
                            }
                            FolderLockDialogMode.REMOVE -> {
                                val ok = withContext(Dispatchers.IO) {
                                    if (FolderLockStore.unlock(path, secret)) {
                                        FolderLockStore.remove(path, secret)
                                        true
                                    } else {
                                        false
                                    }
                                }
                                isProcessing = false
                                if (ok) {
                                    showMsg("Folder lock removed and contents decrypted")
                                    onUnlocked()
                                } else {
                                    error = true
                                }
                            }
                            FolderLockDialogMode.RESET_CONFIRM -> {
                                val ok = withContext(Dispatchers.IO) {
                                    if (FolderLockStore.verifyAnyLock(secret)) {
                                        FolderLockStore.reset(secret)
                                        true
                                    } else {
                                        false
                                    }
                                }
                                isProcessing = false
                                if (ok) {
                                    onUnlocked()
                                } else {
                                    error = true
                                }
                            }
                        }
                    }
                }
            ) {
                Text(stringResource(android.R.string.ok))
            }
        },
        dismissButton = {
            TextButton(
                enabled = !isProcessing,
                onClick = onDismiss
            ) {
                Text(stringResource(android.R.string.cancel))
            }
        }
    )
}

enum class FolderLockDialogMode { UNLOCK, CREATE, RESET_CONFIRM, REMOVE }

@Composable
private fun PatternPad(current: String, onDot: (Int) -> Unit) {
    val dots = (0..8).toList()
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = Modifier.size(180.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        itemsIndexed(dots) { index, _ ->
            val selected = current.contains(index.toString())
            Box(
                modifier = Modifier
                    .aspectRatio(1f)
                    .clip(CircleShape)
                    .background(
                        if (selected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceVariant
                    )
                    .clickable { onDot(index) },
                contentAlignment = Alignment.Center
            ) {}
        }
    }
}
