package com.techflyers.compose.file.explorer.screen.main.tab.files.ui.dialog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.techflyers.compose.file.explorer.App.Companion.globalClass
import com.techflyers.compose.file.explorer.R
import com.techflyers.compose.file.explorer.screen.main.tab.files.FilesTab
import com.techflyers.compose.file.explorer.screen.main.tab.files.task.CopyTask
import com.techflyers.compose.file.explorer.screen.main.tab.files.task.CopyTaskParameters

@Composable
fun DragDropActionDialog(tab: FilesTab) {
    val transfer = tab.pendingDropTransfer ?: return

    val message = if (transfer.items.size == 1) {
        stringResource(
            R.string.move_or_copy_message_single,
            transfer.items.first().displayName,
            transfer.targetFolder.displayName
        )
    } else {
        stringResource(
            R.string.move_or_copy_message,
            transfer.items.size,
            transfer.targetFolder.displayName
        )
    }

    AlertDialog(
        onDismissRequest = { tab.pendingDropTransfer = null },
        title = {
            Text(
                text = stringResource(R.string.move_or_copy_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        confirmButton = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = {
                        globalClass.taskManager.addTaskAndRun(
                            CopyTask(transfer.items, deleteSourceFiles = false),
                            CopyTaskParameters(transfer.targetFolder)
                        )
                        tab.unselectAllFiles()
                        tab.pendingDropTransfer = null
                    }
                ) {
                    Text(stringResource(R.string.copy))
                }

                Button(
                    onClick = {
                        globalClass.taskManager.addTaskAndRun(
                            CopyTask(transfer.items, deleteSourceFiles = true),
                            CopyTaskParameters(transfer.targetFolder)
                        )
                        tab.unselectAllFiles()
                        tab.pendingDropTransfer = null
                    }
                ) {
                    Text(stringResource(R.string.move))
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = { tab.pendingDropTransfer = null }
            ) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}
