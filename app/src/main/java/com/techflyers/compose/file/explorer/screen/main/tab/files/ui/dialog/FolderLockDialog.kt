package com.techflyers.compose.file.explorer.screen.main.tab.files.ui.dialog

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.techflyers.compose.file.explorer.R
import com.techflyers.compose.file.explorer.screen.main.tab.files.service.FolderLockStore
import com.techflyers.compose.file.explorer.screen.main.tab.files.service.FolderLockType
import com.techflyers.compose.file.explorer.screen.main.tab.nfile_tools.ui.PinKeypad

@Composable
fun FolderLockDialog(
    path: String,
    mode: FolderLockDialogMode,
    onUnlocked: () -> Unit,
    onDismiss: () -> Unit
) {
    val lock = FolderLockStore.matchingLock(path)
    var usePattern by remember {
        mutableStateOf(lock?.type == FolderLockType.PATTERN || mode == FolderLockDialogMode.CREATE)
    }
    var pin by remember { mutableStateOf("") }
    var pattern by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
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
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (mode == FolderLockDialogMode.CREATE || mode == FolderLockDialogMode.RESET_CONFIRM) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { usePattern = false }) { Text(stringResource(R.string.pin)) }
                        TextButton(onClick = { usePattern = true }) { Text(stringResource(R.string.pattern)) }
                    }
                }
                if (usePattern && (mode == FolderLockDialogMode.CREATE || mode == FolderLockDialogMode.RESET_CONFIRM || lock?.type == FolderLockType.PATTERN)) {
                    PatternPad(pattern) { index ->
                        if (!pattern.contains(index.toString())) pattern += index.toString()
                    }
                    TextButton(onClick = { pattern = "" }) { Text(stringResource(R.string.clear)) }
                } else {
                    OutlinedTextField(
                        value = pin,
                        onValueChange = { if (it.length <= 8 && it.all(Char::isDigit)) pin = it },
                        label = { Text(stringResource(R.string.pin)) }
                    )
                    PinKeypad(
                        onKeyPress = { if (pin.length < 8) pin += it },
                        onBackspace = { if (pin.isNotEmpty()) pin = pin.dropLast(1) }
                    )
                }
                if (error) {
                    Text(
                        text = stringResource(R.string.incorrect_pin_or_pattern),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val secret = if (usePattern || lock?.type == FolderLockType.PATTERN) pattern else pin
                if (secret.isBlank()) {
                    error = true
                    return@TextButton
                }
                when (mode) {
                    FolderLockDialogMode.CREATE -> {
                        FolderLockStore.lock(
                            path,
                            secret,
                            if (usePattern) FolderLockType.PATTERN else FolderLockType.PIN
                        )
                        onUnlocked()
                    }
                    FolderLockDialogMode.UNLOCK -> {
                        if (FolderLockStore.unlock(path, secret)) {
                            onUnlocked()
                        } else {
                            error = true
                        }
                    }
                    FolderLockDialogMode.REMOVE -> {
                        if (FolderLockStore.unlock(path, secret)) {
                            FolderLockStore.remove(path)
                            onUnlocked()
                        } else {
                            error = true
                        }
                    }
                    FolderLockDialogMode.RESET_CONFIRM -> {
                        if (FolderLockStore.verifyAnyLock(secret)) {
                            FolderLockStore.reset()
                            onUnlocked()
                        } else {
                            error = true
                        }
                    }
                }
            }) {
                Text(stringResource(android.R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) }
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
