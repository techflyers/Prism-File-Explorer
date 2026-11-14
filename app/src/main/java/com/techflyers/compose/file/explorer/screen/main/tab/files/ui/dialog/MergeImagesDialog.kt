package com.techflyers.compose.file.explorer.screen.main.tab.files.ui.dialog

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DragHandle
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.techflyers.compose.file.explorer.common.ui.FilenameExtensionInput
import com.techflyers.compose.file.explorer.screen.main.tab.files.FilesTab
import com.techflyers.compose.file.explorer.screen.main.tab.files.holder.ContentHolder
import com.techflyers.compose.file.explorer.screen.main.tab.files.holder.LocalFileHolder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun MergeImagesDialog(
    show: Boolean,
    targetFiles: List<ContentHolder>,
    tab: FilesTab,
    onDismissRequest: () -> Unit
) {
    if (!show) return

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var isHorizontal by remember { mutableStateOf(false) }
    var quality by remember { mutableFloatStateOf(90f) }
    var isProcessing by remember { mutableStateOf(false) }
    var orderedFiles by remember { mutableStateOf(targetFiles.filterIsInstance<LocalFileHolder>()) }
    var outputName by remember { mutableStateOf("merged_${System.currentTimeMillis()}.jpg") }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text("Merge Selected Images") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "Merge orientation:",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(8.dp))

                Column(modifier = Modifier.selectableGroup()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = !isHorizontal,
                                onClick = { isHorizontal = false },
                                role = Role.RadioButton
                            )
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = !isHorizontal, onClick = null)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Stitch Vertically (Stack Top-to-Bottom)")
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = isHorizontal,
                                onClick = { isHorizontal = true },
                                role = Role.RadioButton
                            )
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = isHorizontal, onClick = null)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Stitch Horizontally (Stack Left-to-Right)")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Image Order (${orderedFiles.size} images):",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(4.dp))
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
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
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

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Output Quality: ${quality.toInt()}%",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Slider(
                    value = quality,
                    onValueChange = { quality = it },
                    valueRange = 10f..100f,
                    steps = 9
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
                        Text("Stitching canvas bitmaps...")
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
                                throw Exception("Select at least 2 local images")
                            }

                            val parentDir = localPaths.first().parentFile ?: File("/")
                            val finalOutputName = if (outputName.isBlank()) "merged_${System.currentTimeMillis()}.jpg" else outputName.trim()
                            val outputFile = File(parentDir, if (finalOutputName.endsWith(".jpg", ignoreCase = true) || finalOutputName.endsWith(".jpeg", ignoreCase = true)) finalOutputName else "$finalOutputName.jpg")

                            withContext(Dispatchers.IO) {
                                // Load bitmaps
                                val bitmaps = localPaths.map { file ->
                                    BitmapFactory.decodeFile(file.absolutePath)
                                        ?: throw Exception("Could not decode image: ${file.name}")
                                }

                                // Merge them
                                val mergedBitmap = mergeBitmaps(bitmaps, isHorizontal)

                                // Save output
                                outputFile.outputStream().use { out ->
                                    mergedBitmap.compress(Bitmap.CompressFormat.JPEG, quality.toInt(), out)
                                }

                                // Clean up bitmaps
                                for (bmp in bitmaps) {
                                    bmp.recycle()
                                }
                                mergedBitmap.recycle()
                            }

                            withContext(Dispatchers.Main) {
                                isProcessing = false
                                Toast.makeText(context, "Images merged: ${outputFile.name}", Toast.LENGTH_LONG).show()
                                tab.unselectAllFiles()
                                tab.reloadFiles()
                                onDismissRequest()
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                            withContext(Dispatchers.Main) {
                                isProcessing = false
                                Toast.makeText(context, "Merge failed: ${e.message}", Toast.LENGTH_LONG).show()
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

private fun mergeBitmaps(bitmaps: List<Bitmap>, horizontal: Boolean): Bitmap {
    var totalWidth = 0
    var totalHeight = 0

    if (horizontal) {
        totalHeight = bitmaps.maxOf { it.height }
        totalWidth = bitmaps.sumOf { it.width }
    } else {
        totalWidth = bitmaps.maxOf { it.width }
        totalHeight = bitmaps.sumOf { it.height }
    }

    val result = Bitmap.createBitmap(totalWidth, totalHeight, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(result)
    canvas.drawColor(android.graphics.Color.WHITE)

    var offset = 0
    val paint = Paint(Paint.FILTER_BITMAP_FLAG)

    for (bitmap in bitmaps) {
        if (horizontal) {
            val y = (totalHeight - bitmap.height) / 2
            canvas.drawBitmap(bitmap, offset.toFloat(), y.toFloat(), paint)
            offset += bitmap.width
        } else {
            val x = (totalWidth - bitmap.width) / 2
            canvas.drawBitmap(bitmap, x.toFloat(), offset.toFloat(), paint)
            offset += bitmap.height
        }
    }

    return result
}
