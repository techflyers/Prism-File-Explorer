package com.techflyers.compose.file.explorer.screen.viewer.pdf.ui.dialog

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.RotateRight
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Sort
import androidx.compose.material.icons.rounded.ViewCarousel
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.techflyers.compose.file.explorer.screen.viewer.pdf.misc.DocumentShareHelper
import com.techflyers.compose.file.explorer.screen.viewer.pdf.operations.PdfOperations
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class PageItem(
    val originalIndex: Int,
    var rotation: Int = 0
)

@Composable
fun OrganizePagesDialog(
    visible: Boolean,
    currentFile: File,
    onApplySuccess: (newFile: File) -> Unit,
    onDismiss: () -> Unit
) {
    if (!visible) return

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var pageList by remember { mutableStateOf<List<PageItem>>(emptyList()) }
    var originalPageList by remember { mutableStateOf<List<PageItem>>(emptyList()) }
    val thumbnails = remember { mutableStateMapOf<Int, Bitmap>() }
    var isLoadingThumbnails by remember { mutableStateOf(true) }
    var isApplying by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(currentFile) {
        withContext(Dispatchers.IO) {
            runCatching {
                val pfd = context.contentResolver.openFileDescriptor(Uri.fromFile(currentFile), "r")
                pfd?.use { descriptor ->
                    PdfRenderer(descriptor).use { renderer ->
                        val count = renderer.pageCount
                        val initialList = (0 until count).map { PageItem(originalIndex = it) }
                        withContext(Dispatchers.Main) {
                            pageList = initialList
                            originalPageList = initialList
                        }

                        for (i in 0 until count) {
                            renderer.openPage(i).use { page ->
                                val w = 120
                                val h = ((page.height.toFloat() / page.width) * w).toInt().coerceIn(120, 180)
                                val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                                bmp.eraseColor(android.graphics.Color.WHITE)
                                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                withContext(Dispatchers.Main) {
                                    thumbnails[i] = bmp
                                }
                            }
                        }
                    }
                }
            }
            withContext(Dispatchers.Main) {
                isLoadingThumbnails = false
            }
        }
    }

    Dialog(
        onDismissRequest = { if (!isApplying) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier
                    .widthIn(max = 520.dp)
                    .fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 8.dp,
                shadowElevation = 12.dp
            ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.ViewCarousel,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "Organize Pages",
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "${pageList.size} pages • Reorder, rotate, delete",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // Sort Dropdown
                        Box {
                            IconButton(
                                onClick = { showSortMenu = true },
                                enabled = !isApplying && pageList.isNotEmpty(),
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Sort,
                                    contentDescription = "Sort Pages",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                            DropdownMenu(
                                expanded = showSortMenu,
                                onDismissRequest = { showSortMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Reverse Order") },
                                    onClick = {
                                        pageList = pageList.reversed()
                                        showSortMenu = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Sort Ascending (1..N)") },
                                    onClick = {
                                        pageList = pageList.sortedBy { it.originalIndex }
                                        showSortMenu = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Sort Descending (N..1)") },
                                    onClick = {
                                        pageList = pageList.sortedByDescending { it.originalIndex }
                                        showSortMenu = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Odd Pages First") },
                                    onClick = {
                                        pageList = pageList.filter { (it.originalIndex + 1) % 2 != 0 } +
                                                pageList.filter { (it.originalIndex + 1) % 2 == 0 }
                                        showSortMenu = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Even Pages First") },
                                    onClick = {
                                        pageList = pageList.filter { (it.originalIndex + 1) % 2 == 0 } +
                                                pageList.filter { (it.originalIndex + 1) % 2 != 0 }
                                        showSortMenu = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Reset Original Order") },
                                    onClick = {
                                        pageList = originalPageList
                                        showSortMenu = false
                                    }
                                )
                            }
                        }

                        if (!isApplying) {
                            IconButton(
                                onClick = onDismiss,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = "Close",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                if (isLoadingThumbnails && pageList.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(36.dp))
                    }
                } else if (pageList.isEmpty()) {
                    Text(
                        text = "Document has no pages left.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(vertical = 24.dp)
                    )
                } else {
                    // Page list
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 380.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        itemsIndexed(pageList) { index, item ->
                            Card(
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    // Page Thumbnail
                                    val thumb = thumbnails[item.originalIndex]
                                    Box(
                                        modifier = Modifier
                                            .size(52.dp, 68.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(MaterialTheme.colorScheme.surfaceContainerLowest),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (thumb != null) {
                                            Image(
                                                bitmap = thumb.asImageBitmap(),
                                                contentDescription = "Page ${index + 1}",
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .rotate(item.rotation.toFloat()),
                                                contentScale = ContentScale.Fit
                                            )
                                        } else {
                                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                        }
                                    }

                                    // Page Label
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Page ${index + 1}",
                                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "Original #${item.originalIndex + 1}" + if (item.rotation != 0) " • ${item.rotation}°" else "",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    // Actions
                                    // Move Up
                                    IconButton(
                                        onClick = {
                                            if (index > 0) {
                                                val m = pageList.toMutableList()
                                                val el = m.removeAt(index)
                                                m.add(index - 1, el)
                                                pageList = m
                                            }
                                        },
                                        enabled = index > 0 && !isApplying,
                                        modifier = Modifier.size(34.dp)
                                    ) {
                                        Icon(Icons.Rounded.KeyboardArrowUp, contentDescription = "Move Up", modifier = Modifier.size(20.dp))
                                    }

                                    // Move Down
                                    IconButton(
                                        onClick = {
                                            if (index < pageList.size - 1) {
                                                val m = pageList.toMutableList()
                                                val el = m.removeAt(index)
                                                m.add(index + 1, el)
                                                pageList = m
                                            }
                                        },
                                        enabled = index < pageList.size - 1 && !isApplying,
                                        modifier = Modifier.size(34.dp)
                                    ) {
                                        Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Move Down", modifier = Modifier.size(20.dp))
                                    }

                                    // Rotate 90 deg
                                    IconButton(
                                        onClick = {
                                            val m = pageList.toMutableList()
                                            val current = m[index]
                                            m[index] = current.copy(rotation = (current.rotation + 90) % 360)
                                            pageList = m
                                        },
                                        enabled = !isApplying,
                                        modifier = Modifier.size(34.dp)
                                    ) {
                                        Icon(Icons.Rounded.RotateRight, contentDescription = "Rotate", modifier = Modifier.size(20.dp))
                                    }

                                    // Delete Page
                                    IconButton(
                                        onClick = {
                                            if (pageList.size > 1) {
                                                val m = pageList.toMutableList()
                                                m.removeAt(index)
                                                pageList = m
                                            }
                                        },
                                        enabled = pageList.size > 1 && !isApplying,
                                        modifier = Modifier.size(34.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Delete,
                                            contentDescription = "Delete",
                                            tint = if (pageList.size > 1) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                if (errorMessage != null) {
                    Text(
                        text = errorMessage!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                // Footer Actions
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        enabled = !isApplying,
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text("Cancel")
                    }

                    OutlinedButton(
                        onClick = {
                            isApplying = true
                            errorMessage = null
                            scope.launch {
                                val out = File(context.cacheDir, "organized_share_${System.currentTimeMillis()}.pdf")
                                val order = pageList.map { it.originalIndex }
                                val rotations = pageList.associate { it.originalIndex to it.rotation }
                                val ok = PdfOperations.organizePages(
                                    context = context,
                                    sourceUri = Uri.fromFile(currentFile),
                                    order = order,
                                    rotationByOriginalIndex = rotations,
                                    destinationFile = out
                                )
                                isApplying = false
                                if (ok && out.exists() && out.length() > 0) {
                                    DocumentShareHelper.shareDocument(
                                        context = context,
                                        uri = Uri.fromFile(out),
                                        title = "Share Organized PDF",
                                        desiredFileName = "Organized_${currentFile.name}"
                                    )
                                } else {
                                    errorMessage = "Failed to organize pages for sharing."
                                }
                            }
                        },
                        enabled = pageList.isNotEmpty() && !isApplying,
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Share")
                    }

                    Button(
                        onClick = {
                            isApplying = true
                            errorMessage = null
                            scope.launch {
                                val out = File(context.cacheDir, "organized_${System.currentTimeMillis()}.pdf")
                                val order = pageList.map { it.originalIndex }
                                val rotations = pageList.associate { it.originalIndex to it.rotation }
                                val ok = PdfOperations.organizePages(
                                    context = context,
                                    sourceUri = Uri.fromFile(currentFile),
                                    order = order,
                                    rotationByOriginalIndex = rotations,
                                    destinationFile = out
                                )
                                isApplying = false
                                if (ok && out.exists() && out.length() > 0) {
                                    onApplySuccess(out)
                                } else {
                                    errorMessage = "Failed to organize pages."
                                }
                            }
                        },
                        enabled = pageList.isNotEmpty() && !isApplying,
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        if (isApplying) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                            Spacer(Modifier.width(8.dp))
                        }
                        Text("Apply")
                    }
                }
            }
        }
    }
}
}
