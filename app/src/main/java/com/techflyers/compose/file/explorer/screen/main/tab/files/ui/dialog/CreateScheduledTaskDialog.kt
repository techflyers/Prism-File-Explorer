package com.techflyers.compose.file.explorer.screen.main.tab.files.ui.dialog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.techflyers.compose.file.explorer.R
import com.techflyers.compose.file.explorer.common.ui.BottomSheetDialog
import com.techflyers.compose.file.explorer.screen.main.tab.files.task.ScheduledFileTask
import com.techflyers.compose.file.explorer.screen.main.tab.files.task.ScheduledTaskStore

@Composable
fun CreateScheduledTaskDialog(
    show: Boolean,
    defaultSource: String,
    defaultDest: String,
    onDismiss: () -> Unit
) {
    if (!show) return
    var name by remember { mutableStateOf("") }
    var move by remember { mutableStateOf(false) }
    var source by remember { mutableStateOf(defaultSource) }
    var dest by remember { mutableStateOf(defaultDest) }
    var regex by remember { mutableStateOf(".*") }
    var destPattern by remember { mutableStateOf("\${name}.\${ext}") }
    var delayMinutes by remember { mutableStateOf("0") }
    var periodicMinutes by remember { mutableStateOf("0") }
    var enabled by remember { mutableStateOf(true) }

    BottomSheetDialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(stringResource(R.string.create_task), style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(stringResource(R.string.name)) }, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = !move, onClick = { move = false }, label = { Text(stringResource(R.string.copy)) })
                FilterChip(selected = move, onClick = { move = true }, label = { Text(stringResource(R.string.move)) })
            }
            OutlinedTextField(value = source, onValueChange = { source = it }, label = { Text(stringResource(R.string.source_folder)) }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = dest, onValueChange = { dest = it }, label = { Text(stringResource(R.string.destination_folder)) }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = regex, onValueChange = { regex = it }, label = { Text(stringResource(R.string.filename_regex)) }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = destPattern, onValueChange = { destPattern = it }, label = { Text(stringResource(R.string.dest_name_pattern)) }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = delayMinutes, onValueChange = { delayMinutes = it.filter(Char::isDigit) }, label = { Text(stringResource(R.string.delay_minutes)) }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = periodicMinutes, onValueChange = { periodicMinutes = it.filter(Char::isDigit) }, label = { Text(stringResource(R.string.periodic_minutes)) }, modifier = Modifier.fillMaxWidth())
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.enabled), modifier = Modifier.weight(1f))
                Switch(checked = enabled, onCheckedChange = { enabled = it })
            }
            Button(
                onClick = {
                    ScheduledTaskStore.upsert(
                        ScheduledFileTask(
                            name = name.ifBlank { if (move) "Move" else "Copy" },
                            move = move,
                            sourcePath = source,
                            destPath = dest,
                            regex = regex.ifBlank { ".*" },
                            destPattern = destPattern.ifBlank { "\${name}.\${ext}" },
                            delayMinutes = delayMinutes.toLongOrNull() ?: 0L,
                            periodicMinutes = periodicMinutes.toLongOrNull() ?: 0L,
                            enabled = enabled
                        )
                    )
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.save))
            }
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(android.R.string.cancel))
            }
        }
    }
}
