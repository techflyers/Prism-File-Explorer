package com.techflyers.compose.file.explorer.screen.preferences.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoDelete
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.techflyers.compose.file.explorer.App.Companion.globalClass
import com.techflyers.compose.file.explorer.R
import com.techflyers.compose.file.explorer.common.toFormattedSize
import com.techflyers.compose.file.explorer.screen.main.tab.files.service.RecycleBinManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun FileOperationContainer() {
    val preferences = globalClass.preferencesManager
    val scope = rememberCoroutineScope()

    var recycleBinSize by remember { mutableLongStateOf(0L) }
    var recycleBinCount by remember { mutableIntStateOf(0) }
    var showEmptyRecycleBinDialog by remember { mutableStateOf(false) }
    var showRetentionDaysDialog by remember { mutableStateOf(false) }
    var retentionDaysInput by remember { mutableStateOf(preferences.recycleBinRetentionDays.toString()) }

    val refreshRecycleBinStats = {
        scope.launch(Dispatchers.IO) {
            recycleBinSize = RecycleBinManager.getRecycleBinSize()
            recycleBinCount = RecycleBinManager.getRecycleBinItemCount()
        }
    }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            recycleBinSize = RecycleBinManager.getRecycleBinSize()
            recycleBinCount = RecycleBinManager.getRecycleBinItemCount()
        }
    }

    Container(title = stringResource(R.string.file_operation)) {
        PreferenceItem(
            label = stringResource(R.string.auto_sign_merged_apk_bundle_files),
            supportingText = stringResource(R.string.auto_sign_merged_apk_bundle_files_description),
            icon = Icons.Rounded.Key,
            switchState = preferences.signMergedApkBundleFiles,
            onSwitchChange = { preferences.signMergedApkBundleFiles = it }
        )

        HorizontalDivider(
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            thickness = 3.dp
        )

        PreferenceItem(
            label = stringResource(R.string.move_to_recycle_bin),
            supportingText = "Move deleted files to recycle bin by default instead of permanently deleting them",
            icon = Icons.Rounded.DeleteSweep,
            switchState = preferences.moveToRecycleBin,
            onSwitchChange = { preferences.moveToRecycleBin = it }
        )

        HorizontalDivider(
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            thickness = 3.dp
        )

        PreferenceItem(
            label = stringResource(R.string.auto_empty_recycle_bin),
            supportingText = if (preferences.autoEmptyRecycleBin) {
                stringResource(R.string.auto_empty_recycle_bin_enabled_desc, preferences.recycleBinRetentionDays)
            } else {
                stringResource(R.string.auto_empty_recycle_bin_disabled_desc)
            },
            icon = Icons.Rounded.AutoDelete,
            switchState = preferences.autoEmptyRecycleBin,
            onSwitchChange = { enabled ->
                preferences.autoEmptyRecycleBin = enabled
                if (enabled) {
                    RecycleBinManager.schedulePeriodicCleanup(globalClass)
                    scope.launch(Dispatchers.IO) {
                        RecycleBinManager.purgeExpiredFiles(preferences.recycleBinRetentionDays)
                        refreshRecycleBinStats()
                    }
                } else {
                    RecycleBinManager.cancelPeriodicCleanup(globalClass)
                }
            }
        )

        if (preferences.autoEmptyRecycleBin) {
            HorizontalDivider(
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                thickness = 3.dp
            )

            PreferenceItem(
                label = stringResource(R.string.recycle_bin_retention),
                supportingText = stringResource(R.string.recycle_bin_retention_days_format, preferences.recycleBinRetentionDays),
                icon = Icons.Rounded.Schedule,
                onClick = {
                    retentionDaysInput = preferences.recycleBinRetentionDays.toString()
                    showRetentionDaysDialog = true
                }
            )

            if (showRetentionDaysDialog) {
                AlertDialog(
                    onDismissRequest = { showRetentionDaysDialog = false },
                    title = { Text(stringResource(R.string.recycle_bin_retention)) },
                    text = {
                        Column {
                            Text(
                                text = stringResource(R.string.recycle_bin_retention_desc),
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(bottom = 12.dp)
                            )
                            OutlinedTextField(
                                value = retentionDaysInput,
                                onValueChange = { retentionDaysInput = it.filter { c -> c.isDigit() }.take(4) },
                                label = { Text("Days") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                val days = retentionDaysInput.toIntOrNull()?.coerceIn(1, 3650) ?: 30
                                preferences.recycleBinRetentionDays = days
                                showRetentionDaysDialog = false
                                scope.launch(Dispatchers.IO) {
                                    RecycleBinManager.purgeExpiredFiles(days)
                                    refreshRecycleBinStats()
                                }
                            }
                        ) {
                            Text(stringResource(R.string.save))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showRetentionDaysDialog = false }) {
                            Text(stringResource(R.string.cancel))
                        }
                    }
                )
            }
        }

        HorizontalDivider(
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            thickness = 3.dp
        )

        PreferenceItem(
            label = stringResource(R.string.empty_recycle_bin),
            supportingText = if (recycleBinCount > 0) {
                "${recycleBinSize.toFormattedSize()} ($recycleBinCount items)"
            } else {
                stringResource(R.string.recycle_bin_empty)
            },
            icon = Icons.Rounded.DeleteForever,
            onClick = {
                if (recycleBinCount == 0 && recycleBinSize == 0L) {
                    globalClass.showMsg(globalClass.getString(R.string.recycle_bin_already_empty))
                } else {
                    showEmptyRecycleBinDialog = true
                }
            }
        )

        if (showEmptyRecycleBinDialog) {
            AlertDialog(
                onDismissRequest = { showEmptyRecycleBinDialog = false },
                title = { Text(stringResource(R.string.empty_recycle_bin)) },
                text = { Text(stringResource(R.string.empty_recycle_bin_warning)) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showEmptyRecycleBinDialog = false
                            scope.launch {
                                val freed = withContext(Dispatchers.IO) {
                                    RecycleBinManager.emptyRecycleBin()
                                }
                                recycleBinSize = 0L
                                recycleBinCount = 0
                                globalClass.showMsg(
                                    "Recycle Bin emptied (${freed.toFormattedSize()} freed)"
                                )
                            }
                        }
                    ) {
                        Text(
                            stringResource(R.string.empty),
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showEmptyRecycleBinDialog = false }) {
                        Text(stringResource(android.R.string.cancel))
                    }
                }
            )
        }

        HorizontalDivider(
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            thickness = 3.dp
        )

        PreferenceItem(
            label = "Clear Temporary Files",
            supportingText = "Delete cached and temporary files created by Prism",
            icon = Icons.Rounded.DeleteSweep,
            onClick = {
                scope.launch {
                    val freed = withContext(Dispatchers.IO) {
                        preferences.clearTemporaryFiles()
                    }
                    globalClass.showMsg("Cleared ${freed.toFormattedSize()} of temporary files")
                }
            }
        )

        HorizontalDivider(
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            thickness = 3.dp
        )

        var showResetLockDialog by remember { mutableStateOf(false) }
        var showResetVaultDialog by remember { mutableStateOf(false) }

        PreferenceItem(
            label = stringResource(R.string.reset_folder_locks),
            supportingText = "Clear all protected folder PINs and patterns",
            icon = Icons.Rounded.Lock,
            onClick = {
                if (!com.techflyers.compose.file.explorer.screen.main.tab.files.service.FolderLockStore.hasAnyLocks()) {
                    globalClass.showMsg("No folders are currently locked")
                } else {
                    showResetLockDialog = true
                }
            }
        )

        if (showResetLockDialog) {
            com.techflyers.compose.file.explorer.screen.main.tab.files.ui.dialog.FolderLockDialog(
                path = "",
                mode = com.techflyers.compose.file.explorer.screen.main.tab.files.ui.dialog.FolderLockDialogMode.RESET_CONFIRM,
                onUnlocked = {
                    showResetLockDialog = false
                    globalClass.showMsg("Folder locks reset successfully")
                },
                onDismiss = { showResetLockDialog = false }
            )
        }

        HorizontalDivider(
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            thickness = 3.dp
        )

        PreferenceItem(
            label = stringResource(R.string.reset_vault),
            supportingText = "Reset secure vault credentials and stored files",
            icon = Icons.Rounded.Key,
            onClick = { showResetVaultDialog = true }
        )

        if (showResetVaultDialog) {
            AlertDialog(
                onDismissRequest = { showResetVaultDialog = false },
                title = { Text(stringResource(R.string.reset_vault)) },
                text = { Text(stringResource(R.string.reset_vault_warning)) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showResetVaultDialog = false
                            com.techflyers.compose.file.explorer.screen.main.tab.files.service.VaultService.resetVault(globalClass)
                            globalClass.showMsg("Vault reset successfully")
                        }
                    ) {
                        Text(stringResource(android.R.string.ok), color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showResetVaultDialog = false }) {
                        Text(stringResource(android.R.string.cancel))
                    }
                }
            )
        }
    }
}