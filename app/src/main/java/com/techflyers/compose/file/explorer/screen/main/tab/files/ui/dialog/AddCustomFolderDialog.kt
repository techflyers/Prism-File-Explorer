package com.techflyers.compose.file.explorer.screen.main.tab.files.ui.dialog

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.techflyers.compose.file.explorer.R
import java.io.File

@Composable
fun AddCustomFolderDialog(
    show: Boolean,
    initialPath: String = "",
    onDismissRequest: () -> Unit,
    onConfirm: (String) -> Unit
) {
    if (!show) return

    var folderPath by remember(initialPath) { mutableStateOf(initialPath) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        icon = {
            Icon(
                imageVector = Icons.Rounded.Folder,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text(text = stringResource(R.string.add_folder_path))
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = folderPath,
                    onValueChange = {
                        folderPath = it
                        errorMessage = null
                    },
                    label = { Text(stringResource(R.string.enter_folder_path)) },
                    isError = errorMessage != null,
                    supportingText = errorMessage?.let { { Text(it) } },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            val folderNotExistStr = stringResource(R.string.folder_does_not_exist)
            TextButton(
                onClick = {
                    val cleanPath = folderPath.trim()
                    if (cleanPath.isEmpty()) return@TextButton
                    val target = File(cleanPath)
                    if (!target.exists() || !target.isDirectory) {
                        errorMessage = folderNotExistStr
                        return@TextButton
                    }
                    onConfirm(cleanPath)
                    onDismissRequest()
                }
            ) {
                Text(stringResource(R.string.confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}
