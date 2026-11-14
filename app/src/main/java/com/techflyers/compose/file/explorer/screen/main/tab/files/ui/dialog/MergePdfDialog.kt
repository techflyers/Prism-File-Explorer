package com.techflyers.compose.file.explorer.screen.main.tab.files.ui.dialog

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DragHandle
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.techflyers.compose.file.explorer.common.ui.FilenameExtensionInput
import com.techflyers.compose.file.explorer.screen.main.tab.files.FilesTab
import com.techflyers.compose.file.explorer.screen.main.tab.files.holder.ContentHolder
import com.techflyers.compose.file.explorer.screen.main.tab.files.holder.LocalFileHolder
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.multipdf.PDFMergerUtility
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun MergePdfDialog(
    show: Boolean,
    targetFiles: List<ContentHolder>,
    tab: FilesTab,
    onDismissRequest: () -> Unit
) {
    if (!show) return

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var isProcessing by remember { mutableStateOf(false) }
    var orderedFiles by remember { mutableStateOf(targetFiles.filterIsInstance<LocalFileHolder>()) }
    var outputName by remember { mutableStateOf("merged_${System.currentTimeMillis()}.pdf") }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text("Merge PDF Documents") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "Document Order (${orderedFiles.size} PDFs):",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Arrange documents in the order they should appear.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))

                val listState = rememberLazyListState()
                val reorderableState = rememberReorderableLazyListState(listState) { from, to ->
                    val list = orderedFiles.toMutableList()
                    val item = list.removeAt(from.index)
                    list.add(to.index, item)
                    orderedFiles = list
                }

                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 240.dp)
                ) {
                    itemsIndexed(orderedFiles, key = { _, fileHolder -> fileHolder.uniquePath }) { index, fileHolder ->
                        ReorderableItem(reorderableState, key = fileHolder.uniquePath) { isDragging ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.DragHandle,
                                    contentDescription = "Reorder",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier
                                        .draggableHandle()
                                        .padding(end = 6.dp)
                                        .size(20.dp)
                                )
                                Text(
                                    text = "${index + 1}.",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.width(24.dp)
                                )
                                Text(
                                    text = fileHolder.displayName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(
                                    onClick = {
                                        val list = orderedFiles.toMutableList()
                                        list.removeAt(index)
                                        orderedFiles = list
                                    },
                                    enabled = orderedFiles.size > 2 && !isProcessing
                                ) {
                                    Icon(Icons.Rounded.Close, contentDescription = "Remove")
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                FilenameExtensionInput(
                    initialFullName = outputName,
                    onFullNameChanged = { outputName = it },
                    label = "Output filename",
                    outlined = true,
                    isFolder = false,
                    modifier = Modifier.fillMaxWidth()
                )

                if (isProcessing) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Merging PDF files...")
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    isProcessing = true
                    scope.launch {
                        try {
                            val localPaths = orderedFiles.map { it.file }
                            if (localPaths.size < 2) {
                                throw Exception("Select at least 2 PDF documents to merge")
                            }

                            val parentDir = localPaths.first().parentFile ?: File("/")
                            val finalOutputName = if (outputName.isBlank()) "merged_${System.currentTimeMillis()}.pdf" else outputName.trim()
                            val outputFile = File(parentDir, if (finalOutputName.endsWith(".pdf", ignoreCase = true)) finalOutputName else "$finalOutputName.pdf")

                            withContext(Dispatchers.IO) {
                                PDFBoxResourceLoader.init(context)
                                val merger = PDFMergerUtility()
                                merger.destinationFileName = outputFile.absolutePath
                                for (file in localPaths) {
                                    merger.addSource(file)
                                }
                                merger.mergeDocuments(MemoryUsageSetting.setupMainMemoryOnly())
                            }

                            withContext(Dispatchers.Main) {
                                isProcessing = false
                                Toast.makeText(context, "PDFs merged successfully: ${outputFile.name}", Toast.LENGTH_LONG).show()
                                tab.unselectAllFiles()
                                tab.reloadFiles()
                                onDismissRequest()
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                            withContext(Dispatchers.Main) {
                                isProcessing = false
                                Toast.makeText(context, "PDF merge failed: ${e.message}", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                },
                enabled = !isProcessing && orderedFiles.size >= 2
            ) {
                Text("Merge")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismissRequest,
                enabled = !isProcessing
            ) {
                Text("Cancel")
            }
        }
    )
}
