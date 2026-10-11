package com.techflyers.compose.file.explorer.screen.main.tab.files.ui.dialog

import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Transform
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.techflyers.compose.file.explorer.screen.main.tab.files.FilesTab
import com.techflyers.compose.file.explorer.screen.main.tab.files.holder.ContentHolder
import com.techflyers.compose.file.explorer.screen.main.tab.files.holder.LocalFileHolder
import com.techflyers.compose.file.explorer.screen.viewer.pdf.operations.PdfOperations
import com.techflyers.compose.file.explorer.screen.viewer.pdf.operations.TargetImageFormat
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun ConvertImageFormatDialog(
    show: Boolean,
    targetFiles: List<ContentHolder>,
    tab: FilesTab? = null,
    onSuccess: ((List<File>) -> Unit)? = null,
    onDismissRequest: () -> Unit
) {
    if (!show) return

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val localFiles = remember(targetFiles) {
        targetFiles.filterIsInstance<LocalFileHolder>().map { it.file }
    }

    var selectedFormat by remember { mutableStateOf(TargetImageFormat.PNG) }
    var quality by remember { mutableFloatStateOf(90f) }
    var isProcessing by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val isBatch = localFiles.size > 1

    AlertDialog(
        onDismissRequest = { if (!isProcessing) onDismissRequest() },
        icon = {
            Icon(
                imageVector = Icons.Rounded.Transform,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
        },
        title = {
            Text(
                text = if (isBatch) "Batch Convert Images" else "Convert Image Format",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = if (isBatch) "Convert ${localFiles.size} images to a different format:" else "Convert ${localFiles.firstOrNull()?.name ?: "image"} to:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )

                if (isProcessing) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                        )
                        Text(
                            text = "Converting images… ${(progress * 100).toInt()}%",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                } else {
                    // Format selection cards
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TargetImageFormat.values().forEach { fmt ->
                            Card(
                                onClick = { selectedFormat = fmt },
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (selectedFormat == fmt)
                                        MaterialTheme.colorScheme.primaryContainer
                                    else
                                        MaterialTheme.colorScheme.surfaceContainer
                                ),
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 12.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = fmt.name,
                                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                        color = if (selectedFormat == fmt)
                                            MaterialTheme.colorScheme.onPrimaryContainer
                                        else
                                            MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }

                    // Quality slider for lossy formats
                    if (selectedFormat != TargetImageFormat.PNG) {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Quality", style = MaterialTheme.typography.bodyMedium)
                                Text("${quality.toInt()}%", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                            }
                            Slider(
                                value = quality,
                                onValueChange = { quality = it },
                                valueRange = 20f..100f,
                                steps = 16
                            )
                        }
                    }

                    if (errorMessage != null) {
                        Text(
                            text = errorMessage!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (!isProcessing) {
                Button(
                    onClick = {
                        if (localFiles.isEmpty()) return@Button
                        isProcessing = true
                        errorMessage = null

                        scope.launch {
                            val convertedFiles = mutableListOf<File>()
                            var count = 0
                            for (src in localFiles) {
                                val parent = src.parentFile ?: continue
                                val baseName = src.nameWithoutExtension
                                val targetFile = File(parent, "$baseName.${selectedFormat.extension}")
                                val ok = PdfOperations.convertImageFormat(
                                    context = context,
                                    sourceUri = Uri.fromFile(src),
                                    destinationFile = targetFile,
                                    targetFormat = selectedFormat,
                                    quality = quality.toInt()
                                )
                                if (ok && targetFile.exists()) {
                                    convertedFiles.add(targetFile)
                                    count++
                                }
                                progress = count.toFloat() / localFiles.size
                            }
                            isProcessing = false
                            if (convertedFiles.isNotEmpty()) {
                                Toast.makeText(
                                    context,
                                    if (isBatch) "Converted ${convertedFiles.size} images to ${selectedFormat.name}" else "Saved as ${convertedFiles.first().name}",
                                    Toast.LENGTH_SHORT
                                ).show()
                                tab?.unselectAllFiles()
                                tab?.reloadFiles()
                                onSuccess?.invoke(convertedFiles)
                                onDismissRequest()
                            } else {
                                errorMessage = "Failed to convert image(s)"
                            }
                        }
                    },
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("Convert")
                }
            }
        },
        dismissButton = {
            if (!isProcessing) {
                OutlinedButton(
                    onClick = onDismissRequest,
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("Cancel")
                }
            }
        }
    )
}
