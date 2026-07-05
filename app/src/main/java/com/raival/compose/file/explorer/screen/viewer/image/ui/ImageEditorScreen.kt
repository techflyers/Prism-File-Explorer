package com.raival.compose.file.explorer.screen.viewer.image.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas as ComposeCanvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.RotateLeft
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material.icons.filled.FlipCameraAndroid
import androidx.compose.material.icons.filled.InvertColors
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint as ComposePaint
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.toBitmap
import kotlin.math.max
import kotlin.math.min

enum class EditorTab {
    CROP_ROTATE, FILTERS, ADJUST, DRAW
}

data class DrawPointFraction(
    val x: Float, // Fraction relative to image bounds
    val y: Float
)

data class DrawPathFraction(
    val points: List<DrawPointFraction>,
    val color: Color,
    val strokeWidth: Float,
    val isEraser: Boolean = false
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageEditorScreen(
    originalBitmap: Bitmap,
    onSave: (Bitmap, overwrite: Boolean) -> Unit,
    onCancel: () -> Unit
) {
    // Current state bitmap
    var currentBitmap by remember { mutableStateOf(originalBitmap) }

    // Undo/Redo stacks
    val undoStack = remember { mutableStateListOf<Bitmap>() }
    val redoStack = remember { mutableStateListOf<Bitmap>() }

    fun commitState(newBitmap: Bitmap) {
        undoStack.add(currentBitmap.copy(currentBitmap.config ?: Bitmap.Config.ARGB_8888, true))
        redoStack.clear()
        currentBitmap = newBitmap
    }

    fun undo() {
        if (undoStack.isNotEmpty()) {
            val last = undoStack.removeAt(undoStack.lastIndex)
            redoStack.add(currentBitmap.copy(currentBitmap.config ?: Bitmap.Config.ARGB_8888, true))
            currentBitmap = last
        }
    }

    fun redo() {
        if (redoStack.isNotEmpty()) {
            val next = redoStack.removeAt(redoStack.lastIndex)
            undoStack.add(currentBitmap.copy(currentBitmap.config ?: Bitmap.Config.ARGB_8888, true))
            currentBitmap = next
        }
    }

    // Active tool tab
    var activeTab by remember { mutableStateOf(EditorTab.CROP_ROTATE) }

    // Dialog for Save preferences
    var showSaveDialog by remember { mutableStateOf(false) }

    // Screen dimension measurements
    var canvasWidth by remember { mutableStateOf(0f) }
    var canvasHeight by remember { mutableStateOf(0f) }

    // Coordinates of current visible preview bitmap on screen
    var previewImageRect by remember { mutableStateOf(Rect.Zero) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF121212))
    ) {
        // TOP BAR
        TopEditorBar(
            onBack = onCancel,
            onUndo = ::undo,
            onRedo = ::redo,
            canUndo = undoStack.isNotEmpty(),
            canRedo = redoStack.isNotEmpty(),
            onSave = { showSaveDialog = true }
        )

        // IMAGE PREVIEW / INTERACTIVE WORKSPACE AREA
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(16.dp)
                .onGloballyPositioned { coordinates ->
                    canvasWidth = coordinates.size.width.toFloat()
                    canvasHeight = coordinates.size.height.toFloat()
                },
            contentAlignment = Alignment.Center
        ) {
            // Measure preview bounds
            val bitmapWidth = currentBitmap.width.toFloat()
            val bitmapHeight = currentBitmap.height.toFloat()

            if (canvasWidth > 0 && canvasHeight > 0) {
                val scale = min(canvasWidth / bitmapWidth, canvasHeight / bitmapHeight)
                val viewW = bitmapWidth * scale
                val viewH = bitmapHeight * scale
                val viewX = (canvasWidth - viewW) / 2f
                val viewY = (canvasHeight - viewH) / 2f
                previewImageRect = Rect(viewX, viewY, viewX + viewW, viewY + viewH)
            }

            // Draw image preview or custom tools
            when (activeTab) {
                EditorTab.CROP_ROTATE -> {
                    CropToolView(
                        bitmap = currentBitmap,
                        previewRect = previewImageRect,
                        onCropApplied = { croppedBitmap ->
                            commitState(croppedBitmap)
                        }
                    )
                }
                EditorTab.DRAW -> {
                    DrawToolView(
                        bitmap = currentBitmap,
                        previewRect = previewImageRect,
                        onDrawApplied = { drawnBitmap ->
                            commitState(drawnBitmap)
                        }
                    )
                }
                EditorTab.FILTERS -> {
                    FiltersToolView(
                        bitmap = currentBitmap,
                        previewRect = previewImageRect,
                        onFilterApplied = { filteredBitmap ->
                            commitState(filteredBitmap)
                        }
                    )
                }
                EditorTab.ADJUST -> {
                    AdjustToolView(
                        bitmap = currentBitmap,
                        previewRect = previewImageRect,
                        onAdjustmentApplied = { adjustedBitmap ->
                            commitState(adjustedBitmap)
                        }
                    )
                }
            }
        }

        // BOTTOM TOOL TABS SELECTOR
        BottomTabs(
            activeTab = activeTab,
            onTabSelected = { activeTab = it }
        )
    }

    if (showSaveDialog) {
        SaveChooserDialog(
            onDismiss = { showSaveDialog = false },
            onConfirm = { overwrite ->
                showSaveDialog = false
                onSave(currentBitmap, overwrite)
            }
        )
    }
}

// TOP EDITOR TOOLBAR
@Composable
fun TopEditorBar(
    onBack: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    canUndo: Boolean,
    canRedo: Boolean,
    onSave: () -> Unit
) {
    Surface(
        color = Color(0xFF1E1E1E),
        tonalElevation = 4.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
                .height(64.dp)
                .padding(top = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Cancel",
                    tint = Color.White
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            IconButton(onClick = onUndo, enabled = canUndo) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Undo,
                    contentDescription = "Undo",
                    tint = if (canUndo) Color.White else Color.Gray
                )
            }

            IconButton(onClick = onRedo, enabled = canRedo) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Redo,
                    contentDescription = "Redo",
                    tint = if (canRedo) Color.White else Color.Gray
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            IconButton(onClick = onSave) {
                Icon(
                    imageVector = Icons.Default.Save,
                    contentDescription = "Save",
                    tint = colorScheme.primary
                )
            }
        }
    }
}

// BOTTOM TAB CATEGORIES SELECTOR
@Composable
fun BottomTabs(
    activeTab: EditorTab,
    onTabSelected: (EditorTab) -> Unit
) {
    Surface(
        color = Color(0xFF1E1E1E),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TabItem(
                icon = Icons.Default.Crop,
                label = "Crop/Rotate",
                selected = activeTab == EditorTab.CROP_ROTATE,
                onClick = { onTabSelected(EditorTab.CROP_ROTATE) }
            )
            TabItem(
                icon = Icons.Default.InvertColors,
                label = "Filters",
                selected = activeTab == EditorTab.FILTERS,
                onClick = { onTabSelected(EditorTab.FILTERS) }
            )
            TabItem(
                icon = Icons.Default.Tune,
                label = "Adjust",
                selected = activeTab == EditorTab.ADJUST,
                onClick = { onTabSelected(EditorTab.ADJUST) }
            )
            TabItem(
                icon = Icons.Default.Brush,
                label = "Draw",
                selected = activeTab == EditorTab.DRAW,
                onClick = { onTabSelected(EditorTab.DRAW) }
            )
        }
    }
}

@Composable
fun TabItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (selected) colorScheme.primary else Color.Gray,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            fontSize = 11.sp,
            color = if (selected) Color.White else Color.Gray,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal
        )
    }
}

// 1. CROP & ROTATE TOOL
@Composable
fun CropToolView(
    bitmap: Bitmap,
    previewRect: Rect,
    onCropApplied: (Bitmap) -> Unit
) {
    var cropLeft by remember { mutableFloatStateOf(0f) }
    var cropTop by remember { mutableFloatStateOf(0f) }
    var cropRight by remember { mutableFloatStateOf(1f) }
    var cropBottom by remember { mutableFloatStateOf(1f) }

    // Reset crop bounds on image change
    LaunchedEffect(bitmap) {
        cropLeft = 0f
        cropTop = 0f
        cropRight = 1f
        cropBottom = 1f
    }

    var selectedRatio by remember { mutableStateOf("Free") }

    fun adjustRatio(ratio: String) {
        selectedRatio = ratio
        if (ratio == "Free") {
            cropLeft = 0f; cropTop = 0f; cropRight = 1f; cropBottom = 1f
            return
        }
        val ratioVal = when (ratio) {
            "1:1" -> 1f
            "4:3" -> 4f / 3f
            "16:9" -> 16f / 9f
            else -> 1f
        }
        val imgW = previewRect.width
        val imgH = previewRect.height
        if (imgW / imgH > ratioVal) {
            // Wider image: crop width
            val newWidth = imgH * ratioVal
            val fraction = newWidth / imgW
            cropLeft = (1f - fraction) / 2f
            cropRight = 1f - cropLeft
            cropTop = 0f
            cropBottom = 1f
        } else {
            // Taller image: crop height
            val newHeight = imgW / ratioVal
            val fraction = newHeight / imgH
            cropTop = (1f - fraction) / 2f
            cropBottom = 1f - cropTop
            cropLeft = 0f
            cropRight = 1f
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            // Draw original image
            ComposeCanvas(modifier = Modifier.fillMaxSize()) {
                if (previewRect.width > 0) {
                    val imageBitmap = bitmap.asImageBitmap()
                    drawImage(
                        image = imageBitmap,
                        srcOffset = androidx.compose.ui.unit.IntOffset.Zero,
                        srcSize = androidx.compose.ui.unit.IntSize(imageBitmap.width, imageBitmap.height),
                        dstOffset = androidx.compose.ui.unit.IntOffset(previewRect.left.toInt(), previewRect.top.toInt()),
                        dstSize = androidx.compose.ui.unit.IntSize(previewRect.width.toInt(), previewRect.height.toInt())
                    )
                }
            }

            // Interactive Crop Handles Overlay
            if (previewRect.width > 0) {
                val boxWidth = previewRect.width
                val boxHeight = previewRect.height

                ComposeCanvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(previewRect, selectedRatio) {
                            detectDragGestures { change, dragAmount ->
                                change.consume()
                                val x = change.position.x
                                val y = change.position.y

                                val relX = ((x - previewRect.left) / boxWidth).coerceIn(0f, 1f)
                                val relY = ((y - previewRect.top) / boxHeight).coerceIn(0f, 1f)

                                // Decide which handle is nearest
                                val distTL = Offset(cropLeft * boxWidth + previewRect.left, cropTop * boxHeight + previewRect.top)
                                val distTR = Offset(cropRight * boxWidth + previewRect.left, cropTop * boxHeight + previewRect.top)
                                val distBL = Offset(cropLeft * boxWidth + previewRect.left, cropBottom * boxHeight + previewRect.top)
                                val distBR = Offset(cropRight * boxWidth + previewRect.left, cropBottom * boxHeight + previewRect.top)

                                val currentTouch = change.position
                                val dists = listOf(
                                    currentTouch.minus(distTL).getDistance() to "TL",
                                    currentTouch.minus(distTR).getDistance() to "TR",
                                    currentTouch.minus(distBL).getDistance() to "BL",
                                    currentTouch.minus(distBR).getDistance() to "BR"
                                )

                                val nearest = dists.minByOrNull { it.first }?.second

                                if (selectedRatio == "Free") {
                                    when (nearest) {
                                        "TL" -> {
                                            cropLeft = relX.coerceAtMost(cropRight - 0.1f)
                                            cropTop = relY.coerceAtMost(cropBottom - 0.1f)
                                        }
                                        "TR" -> {
                                            cropRight = relX.coerceAtLeast(cropLeft + 0.1f)
                                            cropTop = relY.coerceAtMost(cropBottom - 0.1f)
                                        }
                                        "BL" -> {
                                            cropLeft = relX.coerceAtMost(cropRight - 0.1f)
                                            cropBottom = relY.coerceAtLeast(cropTop + 0.1f)
                                        }
                                        "BR" -> {
                                            cropRight = relX.coerceAtLeast(cropLeft + 0.1f)
                                            cropBottom = relY.coerceAtLeast(cropTop + 0.1f)
                                        }
                                    }
                                } else {
                                    // Handle locked aspect ratio crops roughly
                                    val r = when (selectedRatio) {
                                        "1:1" -> 1f
                                        "4:3" -> 4f / 3f
                                        "16:9" -> 16f / 9f
                                        else -> 1f
                                    }
                                    when (nearest) {
                                        "TL", "BL", "TL" -> {
                                            cropLeft = relX.coerceAtMost(cropRight - 0.1f)
                                            val w = cropRight - cropLeft
                                            val h = w * (boxWidth / boxHeight) / r
                                            cropBottom = (cropTop + h).coerceIn(0f, 1f)
                                        }
                                        else -> {
                                            cropRight = relX.coerceAtLeast(cropLeft + 0.1f)
                                            val w = cropRight - cropLeft
                                            val h = w * (boxWidth / boxHeight) / r
                                            cropBottom = (cropTop + h).coerceIn(0f, 1f)
                                        }
                                    }
                                }
                            }
                        }
                ) {
                    val leftPx = previewRect.left + cropLeft * boxWidth
                    val topPx = previewRect.top + cropTop * boxHeight
                    val rightPx = previewRect.left + cropRight * boxWidth
                    val bottomPx = previewRect.top + cropBottom * boxHeight

                    // 1. Draw non-cropped dimming overlays
                    // Top
                    drawRect(
                        color = Color.Black.copy(alpha = 0.5f),
                        topLeft = Offset(previewRect.left, previewRect.top),
                        size = Size(boxWidth, topPx - previewRect.top)
                    )
                    // Bottom
                    drawRect(
                        color = Color.Black.copy(alpha = 0.5f),
                        topLeft = Offset(previewRect.left, bottomPx),
                        size = Size(boxWidth, previewRect.bottom - bottomPx)
                    )
                    // Left
                    drawRect(
                        color = Color.Black.copy(alpha = 0.5f),
                        topLeft = Offset(previewRect.left, topPx),
                        size = Size(leftPx - previewRect.left, bottomPx - topPx)
                    )
                    // Right
                    drawRect(
                        color = Color.Black.copy(alpha = 0.5f),
                        topLeft = Offset(rightPx, topPx),
                        size = Size(previewRect.right - rightPx, bottomPx - topPx)
                    )

                    // 2. Draw border
                    drawRect(
                        color = Color.White,
                        topLeft = Offset(leftPx, topPx),
                        size = Size(rightPx - leftPx, bottomPx - topPx),
                        style = Stroke(width = 2.dp.toPx())
                    )

                    // 3. Draw grid (Thirds rule lines)
                    val cropW = rightPx - leftPx
                    val cropH = bottomPx - topPx

                    drawLine(
                        Color.White.copy(alpha = 0.4f),
                        Offset(leftPx + cropW / 3f, topPx),
                        Offset(leftPx + cropW / 3f, bottomPx),
                        strokeWidth = 1.dp.toPx()
                    )
                    drawLine(
                        Color.White.copy(alpha = 0.4f),
                        Offset(leftPx + cropW * 2f / 3f, topPx),
                        Offset(leftPx + cropW * 2f / 3f, bottomPx),
                        strokeWidth = 1.dp.toPx()
                    )
                    drawLine(
                        Color.White.copy(alpha = 0.4f),
                        Offset(leftPx, topPx + cropH / 3f),
                        Offset(rightPx, topPx + cropH / 3f),
                        strokeWidth = 1.dp.toPx()
                    )
                    drawLine(
                        Color.White.copy(alpha = 0.4f),
                        Offset(leftPx, topPx + cropH * 2f / 3f),
                        Offset(rightPx, topPx + cropH * 2f / 3f),
                        strokeWidth = 1.dp.toPx()
                    )

                    // 4. Draw corner handle markers
                    val handleSize = 10.dp.toPx()
                    // TL
                    drawCircle(Color.White, radius = handleSize, center = Offset(leftPx, topPx))
                    // TR
                    drawCircle(Color.White, radius = handleSize, center = Offset(rightPx, topPx))
                    // BL
                    drawCircle(Color.White, radius = handleSize, center = Offset(leftPx, bottomPx))
                    // BR
                    drawCircle(Color.White, radius = handleSize, center = Offset(rightPx, bottomPx))
                }
            }
        }

        // TOOL CONTROLS FOR CROP & ROTATE
        Surface(
            color = Color(0xFF1E1E1E),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp)
            ) {
                // Preset ratios
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    listOf("Free", "1:1", "4:3", "16:9").forEach { ratio ->
                        OutlinedButton(
                            onClick = { adjustRatio(ratio) },
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = if (selectedRatio == ratio) colorScheme.primary else Color.LightGray
                            ),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            border = borderStroke(selectedRatio == ratio)
                        ) {
                            Text(ratio, fontSize = 12.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Action buttons: Rotates, Flips, Apply Crop
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    // Rotate Left
                    IconButton(onClick = {
                        val matrix = Matrix().apply { postRotate(-90f) }
                        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                        onCropApplied(rotated)
                    }) {
                        Icon(Icons.AutoMirrored.Filled.RotateLeft, contentDescription = "Rotate Left", tint = Color.White)
                    }

                    // Rotate Right
                    IconButton(onClick = {
                        val matrix = Matrix().apply { postRotate(90f) }
                        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                        onCropApplied(rotated)
                    }) {
                        Icon(Icons.AutoMirrored.Filled.RotateRight, contentDescription = "Rotate Right", tint = Color.White)
                    }

                    // Flip Horizontally
                    IconButton(onClick = {
                        val matrix = Matrix().apply { postScale(-1f, 1f) }
                        val flipped = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                        onCropApplied(flipped)
                    }) {
                        Icon(Icons.Default.Flip, contentDescription = "Flip Horizontal", tint = Color.White)
                    }

                    // Flip Vertically
                    IconButton(onClick = {
                        val matrix = Matrix().apply { postScale(1f, -1f) }
                        val flipped = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                        onCropApplied(flipped)
                    }) {
                        Icon(Icons.Default.FlipCameraAndroid, contentDescription = "Flip Vertical", tint = Color.White)
                    }

                    // Apply Crop Checkmark
                    IconButton(
                        onClick = {
                            val x = (cropLeft * bitmap.width).toInt().coerceIn(0, bitmap.width - 1)
                            val y = (cropTop * bitmap.height).toInt().coerceIn(0, bitmap.height - 1)
                            val w = ((cropRight - cropLeft) * bitmap.width).toInt().coerceIn(1, bitmap.width - x)
                            val h = ((cropBottom - cropTop) * bitmap.height).toInt().coerceIn(1, bitmap.height - y)
                            val cropped = Bitmap.createBitmap(bitmap, x, y, w, h)
                            onCropApplied(cropped)
                        },
                        modifier = Modifier
                            .background(colorScheme.primary, CircleShape)
                            .size(40.dp)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = "Apply Crop", tint = Color.Black)
                    }
                }
            }
        }
    }
}

@Composable
private fun borderStroke(selected: Boolean) = if (selected) {
    androidx.compose.foundation.BorderStroke(1.5.dp, colorScheme.primary)
} else {
    androidx.compose.foundation.BorderStroke(1.dp, Color.Gray.copy(alpha = 0.5f))
}

// 2. FILTERS TOOL
@Composable
fun FiltersToolView(
    bitmap: Bitmap,
    previewRect: Rect,
    onFilterApplied: (Bitmap) -> Unit
) {
    val filtersList = listOf(
        "Original" to floatArrayOf(
            1f, 0f, 0f, 0f, 0f,
            0f, 1f, 0f, 0f, 0f,
            0f, 0f, 1f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        ),
        "Sepia" to floatArrayOf(
            0.393f, 0.769f, 0.189f, 0f, 0f,
            0.349f, 0.686f, 0.168f, 0f, 0f,
            0.272f, 0.534f, 0.131f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        ),
        "Grayscale" to floatArrayOf(
            0.213f, 0.715f, 0.072f, 0f, 0f,
            0.213f, 0.715f, 0.072f, 0f, 0f,
            0.213f, 0.715f, 0.072f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        ),
        "Invert" to floatArrayOf(
            -1f, 0f, 0f, 0f, 255f,
            0f, -1f, 0f, 0f, 255f,
            0f, 0f, -1f, 0f, 255f,
            0f, 0f, 0f, 1f, 0f
        ),
        "Warm" to floatArrayOf(
            1.2f, 0f, 0f, 0f, 30f,
            0f, 1.0f, 0f, 0f, 0f,
            0f, 0f, 0.8f, 0f, -20f,
            0f, 0f, 0f, 1f, 0f
        ),
        "Cool" to floatArrayOf(
            0.8f, 0f, 0f, 0f, -20f,
            0f, 1.0f, 0f, 0f, 0f,
            0f, 0f, 1.2f, 0f, 30f,
            0f, 0f, 0f, 1f, 0f
        ),
        "Vintage" to floatArrayOf(
            0.9f, 0f, 0f, 0f, 0f,
            0f, 0.8f, 0f, 0f, 0f,
            0f, 0f, 0.5f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        )
    )

    var selectedFilterIndex by remember { mutableStateOf(0) }

    Column(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            // Draw image with Compose ColorFilter live preview
            ComposeCanvas(modifier = Modifier.fillMaxSize()) {
                if (previewRect.width > 0) {
                    val filterMatrix = filtersList[selectedFilterIndex].second
                    val imageBitmap = bitmap.asImageBitmap()
                    drawImage(
                        image = imageBitmap,
                        srcOffset = androidx.compose.ui.unit.IntOffset.Zero,
                        srcSize = androidx.compose.ui.unit.IntSize(imageBitmap.width, imageBitmap.height),
                        dstOffset = androidx.compose.ui.unit.IntOffset(previewRect.left.toInt(), previewRect.top.toInt()),
                        dstSize = androidx.compose.ui.unit.IntSize(previewRect.width.toInt(), previewRect.height.toInt()),
                        colorFilter = androidx.compose.ui.graphics.ColorFilter.colorMatrix(
                            androidx.compose.ui.graphics.ColorMatrix(filterMatrix)
                        )
                    )
                }
            }
        }

        // Filters Picker Drawer
        Surface(
            color = Color(0xFF1E1E1E),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp)
            ) {
                // Scrollable filters list
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Displays first 4 filters in a clean row, or scroll row
                    filtersList.forEachIndexed { index, (name, _) ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (selectedFilterIndex == index) colorScheme.primary.copy(alpha = 0.2f)
                                    else Color(0xFF2D2D2D)
                                )
                                .border(
                                    width = if (selectedFilterIndex == index) 1.5.dp else 0.dp,
                                    color = if (selectedFilterIndex == index) colorScheme.primary else Color.Transparent,
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .clickable { selectedFilterIndex = index }
                                .padding(horizontal = 12.dp, vertical = 10.dp)
                        ) {
                            Text(
                                text = name,
                                color = if (selectedFilterIndex == index) Color.White else Color.LightGray,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    IconButton(
                        onClick = {
                            val filterMatrix = filtersList[selectedFilterIndex].second
                            val filteredBitmap = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
                            val canvas = Canvas(filteredBitmap)
                            val paint = Paint(Paint.FILTER_BITMAP_FLAG).apply {
                                colorFilter = ColorMatrixColorFilter(ColorMatrix(filterMatrix))
                            }
                            canvas.drawBitmap(bitmap, 0f, 0f, paint)
                            onFilterApplied(filteredBitmap)
                        },
                        modifier = Modifier
                            .background(colorScheme.primary, CircleShape)
                            .size(44.dp)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = "Apply Filter", tint = Color.Black)
                    }
                }
            }
        }
    }
}

// 3. ADJUST SLIDERS TOOL
@Composable
fun AdjustToolView(
    bitmap: Bitmap,
    previewRect: Rect,
    onAdjustmentApplied: (Bitmap) -> Unit
) {
    // Sliders: Brightness [-1.0f, 1.0f], Contrast [0.5f, 2.0f], Saturation [0.0f, 2.0f]
    var brightness by remember { mutableFloatStateOf(0f) }
    var contrast by remember { mutableFloatStateOf(1.0f) }
    var saturation by remember { mutableFloatStateOf(1.0f) }

    fun buildFilterMatrix(b: Float, c: Float, s: Float): FloatArray {
        // Combined saturation + brightness + contrast matrix
        val matrix = ColorMatrix()
        matrix.setSaturation(s)

        val translate = b * 255f + 128f * (1f - c)
        val scaleMatrix = ColorMatrix(floatArrayOf(
            c, 0f, 0f, 0f, translate,
            0f, c, 0f, 0f, translate,
            0f, 0f, c, 0f, translate,
            0f, 0f, 0f, 1f, 0f
        ))
        matrix.postConcat(scaleMatrix)
        return matrix.array
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            ComposeCanvas(modifier = Modifier.fillMaxSize()) {
                if (previewRect.width > 0) {
                    val matrixArray = buildFilterMatrix(brightness, contrast, saturation)
                    val imageBitmap = bitmap.asImageBitmap()
                    drawImage(
                        image = imageBitmap,
                        srcOffset = androidx.compose.ui.unit.IntOffset.Zero,
                        srcSize = androidx.compose.ui.unit.IntSize(imageBitmap.width, imageBitmap.height),
                        dstOffset = androidx.compose.ui.unit.IntOffset(previewRect.left.toInt(), previewRect.top.toInt()),
                        dstSize = androidx.compose.ui.unit.IntSize(previewRect.width.toInt(), previewRect.height.toInt()),
                        colorFilter = androidx.compose.ui.graphics.ColorFilter.colorMatrix(
                            androidx.compose.ui.graphics.ColorMatrix(matrixArray)
                        )
                    )
                }
            }
        }

        // Adjust Slider Panels
        Surface(
            color = Color(0xFF1E1E1E),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                // Brightness
                AdjustmentRow(
                    label = "Brightness",
                    value = brightness,
                    valueRange = -1.0f..1.0f,
                    onValueChange = { brightness = it }
                )

                // Contrast
                AdjustmentRow(
                    label = "Contrast",
                    value = contrast,
                    valueRange = 0.5f..2.0f,
                    onValueChange = { contrast = it }
                )

                // Saturation
                AdjustmentRow(
                    label = "Saturation",
                    value = saturation,
                    valueRange = 0.0f..2.0f,
                    onValueChange = { saturation = it }
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    IconButton(
                        onClick = {
                            val matrixArray = buildFilterMatrix(brightness, contrast, saturation)
                            val adjusted = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
                            val canvas = Canvas(adjusted)
                            val paint = Paint(Paint.FILTER_BITMAP_FLAG).apply {
                                colorFilter = ColorMatrixColorFilter(ColorMatrix(matrixArray))
                            }
                            canvas.drawBitmap(bitmap, 0f, 0f, paint)
                            onAdjustmentApplied(adjusted)
                        },
                        modifier = Modifier
                            .background(colorScheme.primary, CircleShape)
                            .size(44.dp)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = "Apply Adjustments", tint = Color.Black)
                    }
                }
            }
        }
    }
}

@Composable
fun AdjustmentRow(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = Color.White,
            fontSize = 13.sp,
            modifier = Modifier.width(80.dp),
            fontWeight = FontWeight.Medium
        )
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            modifier = Modifier.weight(1f),
            colors = SliderDefaults.colors(
                thumbColor = colorScheme.primary,
                activeTrackColor = colorScheme.primary
            )
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = String.format("%.1f", value),
            color = Color.LightGray,
            fontSize = 12.sp,
            modifier = Modifier.width(36.dp),
            textAlign = TextAlign.End
        )
    }
}

// 4. ANNOTATE / DRAW TOOL
@Composable
fun DrawToolView(
    bitmap: Bitmap,
    previewRect: Rect,
    onDrawApplied: (Bitmap) -> Unit
) {
    var brushColor by remember { mutableStateOf(Color.Red) }
    var brushSize by remember { mutableFloatStateOf(10f) }
    var isEraser by remember { mutableStateOf(false) }

    // List of drawn path lines (relative coordinates)
    val paths = remember { mutableStateListOf<DrawPathFraction>() }
    // A path in progress
    var currentPathPoints = remember { mutableStateListOf<DrawPointFraction>() }

    val colors = listOf(Color.Red, Color.Green, Color.Blue, Color.Yellow, Color.Magenta, Color.White, Color.Black)

    Column(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            // Draw background image
            ComposeCanvas(modifier = Modifier.fillMaxSize()) {
                if (previewRect.width > 0) {
                    val imageBitmap = bitmap.asImageBitmap()
                    drawImage(
                        image = imageBitmap,
                        srcOffset = androidx.compose.ui.unit.IntOffset.Zero,
                        srcSize = androidx.compose.ui.unit.IntSize(imageBitmap.width, imageBitmap.height),
                        dstOffset = androidx.compose.ui.unit.IntOffset(previewRect.left.toInt(), previewRect.top.toInt()),
                        dstSize = androidx.compose.ui.unit.IntSize(previewRect.width.toInt(), previewRect.height.toInt())
                    )
                }
            }

            // Interactive Drawing Canvas Overlay
            if (previewRect.width > 0) {
                ComposeCanvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(previewRect) {
                            detectDragGestures(
                                onDragStart = { offset ->
                                    if (previewRect.contains(offset)) {
                                        val relX = (offset.x - previewRect.left) / previewRect.width
                                        val relY = (offset.y - previewRect.top) / previewRect.height
                                        currentPathPoints.add(DrawPointFraction(relX, relY))
                                    }
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    val offset = change.position
                                    if (previewRect.contains(offset)) {
                                        val relX = (offset.x - previewRect.left) / previewRect.width
                                        val relY = (offset.y - previewRect.top) / previewRect.height
                                        currentPathPoints.add(DrawPointFraction(relX, relY))
                                    }
                                },
                                onDragEnd = {
                                    if (currentPathPoints.isNotEmpty()) {
                                        paths.add(
                                            DrawPathFraction(
                                                points = currentPathPoints.toList(),
                                                color = brushColor,
                                                strokeWidth = brushSize,
                                                isEraser = isEraser
                                            )
                                        )
                                        currentPathPoints.clear()
                                    }
                                }
                            )
                        }
                ) {
                    // Draw existing paths
                    paths.forEach { drawPath ->
                        val points = drawPath.points.map { pt ->
                            Offset(
                                previewRect.left + pt.x * previewRect.width,
                                previewRect.top + pt.y * previewRect.height
                            )
                        }
                        if (points.size > 1) {
                            val path = androidx.compose.ui.graphics.Path().apply {
                                moveTo(points[0].x, points[0].y)
                                for (i in 1 until points.size) {
                                    lineTo(points[i].x, points[i].y)
                                }
                            }
                            drawPath(
                                path = path,
                                color = if (drawPath.isEraser) Color.Black else drawPath.color, // Eraser draws black (or we can implement blend mode, but black works well for background overlay edits)
                                style = Stroke(
                                    width = drawPath.strokeWidth,
                                    cap = androidx.compose.ui.graphics.StrokeCap.Round,
                                    join = androidx.compose.ui.graphics.StrokeJoin.Round
                                )
                            )
                        }
                    }

                    // Draw current path in progress
                    if (currentPathPoints.size > 1) {
                        val points = currentPathPoints.map { pt ->
                            Offset(
                                previewRect.left + pt.x * previewRect.width,
                                previewRect.top + pt.y * previewRect.height
                            )
                        }
                        val path = androidx.compose.ui.graphics.Path().apply {
                            moveTo(points[0].x, points[0].y)
                            for (i in 1 until points.size) {
                                lineTo(points[i].x, points[i].y)
                            }
                        }
                        drawPath(
                            path = path,
                            color = if (isEraser) Color.Black else brushColor,
                            style = Stroke(
                                width = brushSize,
                                cap = androidx.compose.ui.graphics.StrokeCap.Round,
                                join = androidx.compose.ui.graphics.StrokeJoin.Round
                            )
                        )
                    }
                }
            }
        }

        // Brush Tools Selection
        Surface(
            color = Color(0xFF1E1E1E),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
            ) {
                // Color Picker Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        colors.forEach { color ->
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(color)
                                    .border(
                                        width = if (brushColor == color && !isEraser) 2.dp else 0.dp,
                                        color = Color.White,
                                        shape = CircleShape
                                    )
                                    .clickable {
                                        brushColor = color
                                        isEraser = false
                                    }
                            )
                        }
                    }

                    // Eraser Toggle
                    IconButton(
                        onClick = { isEraser = !isEraser },
                        modifier = Modifier
                            .background(
                                if (isEraser) colorScheme.primary else Color(0xFF2D2D2D),
                                CircleShape
                            )
                            .size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Brush,
                            contentDescription = "Eraser",
                            tint = if (isEraser) Color.Black else Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Brush Size Slider
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Size", color = Color.White, fontSize = 12.sp, modifier = Modifier.width(36.dp))
                    Slider(
                        value = brushSize,
                        onValueChange = { brushSize = it },
                        valueRange = 2f..80f,
                        modifier = Modifier.weight(1f),
                        colors = SliderDefaults.colors(
                            thumbColor = colorScheme.primary,
                            activeTrackColor = colorScheme.primary
                        )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    // Clear all / undo buttons
                    IconButton(
                        onClick = { if (paths.isNotEmpty()) paths.removeAt(paths.lastIndex) },
                        enabled = paths.isNotEmpty()
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo line", tint = if (paths.isNotEmpty()) Color.White else Color.Gray)
                    }
                    IconButton(onClick = { paths.clear() }, enabled = paths.isNotEmpty()) {
                        Icon(Icons.Default.Close, contentDescription = "Clear all", tint = if (paths.isNotEmpty()) Color.White else Color.Gray)
                    }

                    // Apply drawing button
                    IconButton(
                        onClick = {
                            val drawn = bitmap.copy(bitmap.config ?: Bitmap.Config.ARGB_8888, true)
                            val canvas = Canvas(drawn)

                            // Render paths with absolute pixel coordinates on bitmap
                            paths.forEach { drawPath ->
                                val paint = Paint().apply {
                                    color = if (drawPath.isEraser) android.graphics.Color.BLACK else drawPath.color.toArgb()
                                    style = Paint.Style.STROKE
                                    strokeWidth = drawPath.strokeWidth * (bitmap.width.toFloat() / previewRect.width)
                                    strokeCap = Paint.Cap.ROUND
                                    strokeJoin = Paint.Join.ROUND
                                }
                                val path = Path()
                                val pts = drawPath.points
                                if (pts.size > 1) {
                                    path.moveTo(pts[0].x * bitmap.width, pts[0].y * bitmap.height)
                                    for (i in 1 until pts.size) {
                                        path.lineTo(pts[i].x * bitmap.width, pts[i].y * bitmap.height)
                                    }
                                    canvas.drawPath(path, paint)
                                }
                            }
                            onDrawApplied(drawn)
                        },
                        modifier = Modifier
                            .background(colorScheme.primary, CircleShape)
                            .size(40.dp)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = "Apply Draw", tint = Color.Black)
                    }
                }
            }
        }
    }
}

// SAVE TYPE CHOOSER DIALOG
@Composable
fun SaveChooserDialog(
    onDismiss: () -> Unit,
    onConfirm: (overwrite: Boolean) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Save Edits") },
        text = { Text("Would you like to overwrite the original image or save your changes as a copy?") },
        confirmButton = {
            Button(
                onClick = { onConfirm(true) },
                colors = ButtonDefaults.buttonColors(containerColor = colorScheme.primary)
            ) {
                Text("Overwrite", color = Color.Black)
            }
        },
        dismissButton = {
            OutlinedButton(onClick = { onConfirm(false) }) {
                Text("Save Copy")
            }
        }
    )
}
