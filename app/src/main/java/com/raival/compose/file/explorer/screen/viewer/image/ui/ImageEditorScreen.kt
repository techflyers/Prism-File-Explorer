package com.raival.compose.file.explorer.screen.viewer.image.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.os.Environment
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas as ComposeCanvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
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
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
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
    onSave: (Bitmap, overwrite: Boolean, filename: String) -> Unit,
    onCancel: () -> Unit
) {
    var currentBitmap by remember { mutableStateOf(originalBitmap) }
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

    var activeTab by remember { mutableStateOf(EditorTab.CROP_ROTATE) }
    var showSaveDialog by remember { mutableStateOf(false) }

    // Canvas size (pixel size of the canvas Box)
    var canvasWidth by remember { mutableStateOf(0f) }
    var canvasHeight by remember { mutableStateOf(0f) }

    // Rect of the scaled bitmap on screen (for hit-testing in tools)
    var previewImageRect by remember { mutableStateOf(Rect.Zero) }

    // Pan / zoom state — ONLY applied to the image canvas
    var canvasOffsetX by remember { mutableFloatStateOf(0f) }
    var canvasOffsetY by remember { mutableFloatStateOf(0f) }
    var canvasScale  by remember { mutableFloatStateOf(1f) }

    // Lock = PAN MODE: image is panned/zoomed, tool overlays and options are temporarily blocked
    var isCanvasLocked by remember { mutableStateOf(false) }

    // ── Shared tool state (hoisted so options panels below can mutate) ─────────

    // Crop
    var cropLeft   by remember { mutableFloatStateOf(0f) }
    var cropTop    by remember { mutableFloatStateOf(0f) }
    var cropRight  by remember { mutableFloatStateOf(1f) }
    var cropBottom by remember { mutableFloatStateOf(1f) }
    var selectedRatio by remember { mutableStateOf("Free") }
    LaunchedEffect(currentBitmap) {
        cropLeft = 0f; cropTop = 0f; cropRight = 1f; cropBottom = 1f
        selectedRatio = "Free"
    }

    // Draw
    var brushColor by remember { mutableStateOf(Color.Red) }
    var brushSize  by remember { mutableFloatStateOf(10f) }
    var isEraser   by remember { mutableStateOf(false) }
    val drawPaths  = remember { mutableStateListOf<DrawPathFraction>() }
    val currentPathPoints = remember { mutableStateListOf<DrawPointFraction>() }

    // Filters
    var selectedFilterIndex by remember { mutableStateOf(0) }

    // Adjust
    var brightness  by remember { mutableFloatStateOf(0f) }
    var contrast    by remember { mutableFloatStateOf(1f) }
    var saturation  by remember { mutableFloatStateOf(1f) }

    // ── Layout ────────────────────────────────────────────────────────────────
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
            onSave = { showSaveDialog = true },
            isCanvasLocked = isCanvasLocked,
            onToggleLock = { isCanvasLocked = !isCanvasLocked }
        )

        // ── IMAGE CANVAS (pan/zoom applies ONLY here) ─────────────────────
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(Color(0xFF0A0A0A))
                .onGloballyPositioned {
                    canvasWidth  = it.size.width.toFloat()
                    canvasHeight = it.size.height.toFloat()
                }
                .then(
                    if (isCanvasLocked) {
                        Modifier.pointerInput(isCanvasLocked) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                canvasScale = (canvasScale * zoom).coerceIn(0.5f, 5f)
                                canvasOffsetX += pan.x
                                canvasOffsetY += pan.y
                                val maxOff = 2000f * canvasScale
                                canvasOffsetX = canvasOffsetX.coerceIn(-maxOff, maxOff)
                                canvasOffsetY = canvasOffsetY.coerceIn(-maxOff, maxOff)
                            }
                        }
                    } else Modifier
                ),
            contentAlignment = Alignment.Center
        ) {
            // The graphicsLayer transform scales/pans ONLY the inner image+overlay
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(
                        scaleX = canvasScale,
                        scaleY = canvasScale,
                        translationX = canvasOffsetX,
                        translationY = canvasOffsetY
                    ),
                contentAlignment = Alignment.Center
            ) {
                // Compute preview rect
                val bW = currentBitmap.width.toFloat()
                val bH = currentBitmap.height.toFloat()
                if (canvasWidth > 0 && canvasHeight > 0) {
                    val s = min(canvasWidth / bW, canvasHeight / bH)
                    val vW = bW * s; val vH = bH * s
                    val vX = (canvasWidth - vW) / 2f; val vY = (canvasHeight - vH) / 2f
                    previewImageRect = Rect(vX, vY, vX + vW, vY + vH)
                }

                // Image-only canvas overlay for each tab
                when (activeTab) {
                    EditorTab.CROP_ROTATE -> CropImageOverlay(
                        bitmap = currentBitmap,
                        previewRect = previewImageRect,
                        cropLeft = cropLeft, cropTop = cropTop,
                        cropRight = cropRight, cropBottom = cropBottom,
                        selectedRatio = selectedRatio,
                        onCropChange = { l, t, r, b -> cropLeft=l; cropTop=t; cropRight=r; cropBottom=b },
                        locked = isCanvasLocked
                    )
                    EditorTab.FILTERS -> FiltersImageOverlay(
                        bitmap = currentBitmap,
                        previewRect = previewImageRect,
                        selectedFilterIndex = selectedFilterIndex
                    )
                    EditorTab.ADJUST -> AdjustImageOverlay(
                        bitmap = currentBitmap,
                        previewRect = previewImageRect,
                        brightness = brightness, contrast = contrast, saturation = saturation
                    )
                    EditorTab.DRAW -> DrawImageOverlay(
                        bitmap = currentBitmap,
                        previewRect = previewImageRect,
                        paths = drawPaths,
                        currentPathPoints = currentPathPoints,
                        brushColor = brushColor,
                        brushSize = brushSize,
                        isEraser = isEraser,
                        locked = isCanvasLocked,
                        onPathComplete = { drawPaths.add(it) }
                    )
                }

                // Lock scrim — blocks tool pointer events during pan mode
                if (isCanvasLocked) {
                    Box(modifier = Modifier.fillMaxSize().pointerInput(Unit) {})
                }
            }
        }

        // ── BOTTOM TABS ────────────────────────────────────────────────────
        BottomTabs(
            activeTab = activeTab,
            onTabSelected = {
                if (isCanvasLocked) isCanvasLocked = false
                activeTab = it
            }
        )

        // ── TOOL OPTIONS PANEL (fixed, never transforms) ───────────────────
        when (activeTab) {
            EditorTab.CROP_ROTATE -> CropToolOptions(
                bitmap = currentBitmap,
                previewRect = previewImageRect,
                cropLeft = cropLeft, cropTop = cropTop,
                cropRight = cropRight, cropBottom = cropBottom,
                selectedRatio = selectedRatio,
                onRatioChange = { ratio ->
                    selectedRatio = ratio
                    if (ratio == "Free") { cropLeft=0f; cropTop=0f; cropRight=1f; cropBottom=1f; return@CropToolOptions }
                    val r = when (ratio) { "1:1"->1f; "4:3"->4f/3f; "16:9"->16f/9f; else->1f }
                    val iW = previewImageRect.width; val iH = previewImageRect.height
                    if (iW <= 0 || iH <= 0) return@CropToolOptions
                    if (iW/iH > r) { val f = (iH*r)/iW; cropLeft=(1f-f)/2f; cropRight=1f-cropLeft; cropTop=0f; cropBottom=1f }
                    else { val f = (iW/r)/iH; cropTop=(1f-f)/2f; cropBottom=1f-cropTop; cropLeft=0f; cropRight=1f }
                },
                onRotate = { degrees ->
                    val m = Matrix().apply { postRotate(degrees) }
                    commitState(Bitmap.createBitmap(currentBitmap, 0,0, currentBitmap.width, currentBitmap.height, m, true))
                },
                onFlip = { sx, sy ->
                    val m = Matrix().apply { postScale(sx, sy) }
                    commitState(Bitmap.createBitmap(currentBitmap, 0,0, currentBitmap.width, currentBitmap.height, m, true))
                },
                onApplyCrop = {
                    val x = (cropLeft * currentBitmap.width).toInt().coerceIn(0, currentBitmap.width-1)
                    val y = (cropTop * currentBitmap.height).toInt().coerceIn(0, currentBitmap.height-1)
                    val w = ((cropRight-cropLeft)*currentBitmap.width).toInt().coerceIn(1, currentBitmap.width-x)
                    val h = ((cropBottom-cropTop)*currentBitmap.height).toInt().coerceIn(1, currentBitmap.height-y)
                    commitState(Bitmap.createBitmap(currentBitmap, x, y, w, h))
                }
            )
            EditorTab.FILTERS -> FiltersToolOptions(
                bitmap = currentBitmap,
                selectedFilterIndex = selectedFilterIndex,
                onFilterSelected = { selectedFilterIndex = it },
                onApply = { filterIdx ->
                    val fl = filtersList[filterIdx].second
                    val out = Bitmap.createBitmap(currentBitmap.width, currentBitmap.height, Bitmap.Config.ARGB_8888)
                    val cv = Canvas(out)
                    val p = Paint(Paint.FILTER_BITMAP_FLAG).apply { colorFilter = ColorMatrixColorFilter(ColorMatrix(fl)) }
                    cv.drawBitmap(currentBitmap, 0f, 0f, p)
                    commitState(out)
                }
            )
            EditorTab.ADJUST -> AdjustToolOptions(
                brightness = brightness, contrast = contrast, saturation = saturation,
                onBrightnessChange = { brightness = it },
                onContrastChange  = { contrast = it },
                onSaturationChange = { saturation = it },
                onApply = {
                    val ma = buildAdjustMatrix(brightness, contrast, saturation)
                    val out = Bitmap.createBitmap(currentBitmap.width, currentBitmap.height, Bitmap.Config.ARGB_8888)
                    val cv = Canvas(out)
                    val p = Paint(Paint.FILTER_BITMAP_FLAG).apply { colorFilter = ColorMatrixColorFilter(ColorMatrix(ma)) }
                    cv.drawBitmap(currentBitmap, 0f, 0f, p)
                    commitState(out)
                }
            )
            EditorTab.DRAW -> DrawToolOptions(
                brushColor = brushColor,
                brushSize = brushSize,
                isEraser = isEraser,
                hasPaths = drawPaths.isNotEmpty(),
                onColorChange = { brushColor = it; isEraser = false },
                onSizeChange = { brushSize = it },
                onToggleEraser = { isEraser = !isEraser },
                onUndoPath = { if (drawPaths.isNotEmpty()) drawPaths.removeAt(drawPaths.lastIndex) },
                onClearPaths = { drawPaths.clear() },
                onApply = {
                    val drawn = currentBitmap.copy(currentBitmap.config ?: Bitmap.Config.ARGB_8888, true)
                    val cv = Canvas(drawn)
                    drawPaths.forEach { dp ->
                        val pts = dp.points
                        if (pts.size > 1) {
                            val p = Paint().apply {
                                color = if (dp.isEraser) android.graphics.Color.BLACK else dp.color.toArgb()
                                style = Paint.Style.STROKE
                                strokeWidth = dp.strokeWidth * (currentBitmap.width / previewImageRect.width.coerceAtLeast(1f))
                                strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
                            }
                            val path = Path().apply {
                                moveTo(pts[0].x * currentBitmap.width, pts[0].y * currentBitmap.height)
                                for (i in 1 until pts.size) lineTo(pts[i].x * currentBitmap.width, pts[i].y * currentBitmap.height)
                            }
                            cv.drawPath(path, p)
                        }
                    }
                    drawPaths.clear()
                    commitState(drawn)
                }
            )
        }
    }

    if (showSaveDialog) {
        SaveChooserDialog(
            onDismiss = { showSaveDialog = false },
            onConfirm = { filename, overwrite ->
                showSaveDialog = false
                onSave(currentBitmap, overwrite, filename)
            }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Shared helper functions
// ─────────────────────────────────────────────────────────────────────────────
internal val filtersList = listOf(
    "Original"  to floatArrayOf(1f,0f,0f,0f,0f, 0f,1f,0f,0f,0f, 0f,0f,1f,0f,0f, 0f,0f,0f,1f,0f),
    "Sepia"     to floatArrayOf(0.393f,0.769f,0.189f,0f,0f, 0.349f,0.686f,0.168f,0f,0f, 0.272f,0.534f,0.131f,0f,0f, 0f,0f,0f,1f,0f),
    "Grayscale" to floatArrayOf(0.213f,0.715f,0.072f,0f,0f, 0.213f,0.715f,0.072f,0f,0f, 0.213f,0.715f,0.072f,0f,0f, 0f,0f,0f,1f,0f),
    "Invert"    to floatArrayOf(-1f,0f,0f,0f,255f, 0f,-1f,0f,0f,255f, 0f,0f,-1f,0f,255f, 0f,0f,0f,1f,0f),
    "Warm"      to floatArrayOf(1.2f,0f,0f,0f,30f, 0f,1f,0f,0f,0f, 0f,0f,0.8f,0f,-20f, 0f,0f,0f,1f,0f),
    "Cool"      to floatArrayOf(0.8f,0f,0f,0f,-20f, 0f,1f,0f,0f,0f, 0f,0f,1.2f,0f,30f, 0f,0f,0f,1f,0f),
    "Vintage"   to floatArrayOf(0.9f,0f,0f,0f,0f, 0f,0.8f,0f,0f,0f, 0f,0f,0.5f,0f,0f, 0f,0f,0f,1f,0f)
)

internal fun buildAdjustMatrix(b: Float, c: Float, s: Float): FloatArray {
    val m = ColorMatrix(); m.setSaturation(s)
    val t = b*255f + 128f*(1f-c)
    m.postConcat(ColorMatrix(floatArrayOf(c,0f,0f,0f,t, 0f,c,0f,0f,t, 0f,0f,c,0f,t, 0f,0f,0f,1f,0f)))
    return m.array
}

// TOP EDITOR TOOLBAR
@Composable
fun TopEditorBar(
    onBack: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    canUndo: Boolean,
    canRedo: Boolean,
    onSave: () -> Unit,
    isCanvasLocked: Boolean = false,
    onToggleLock: () -> Unit = {}
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

            // Lock/Unlock canvas toggle
            IconButton(onClick = onToggleLock) {
                Icon(
                    imageVector = if (isCanvasLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                    contentDescription = if (isCanvasLocked) "Unlock canvas" else "Lock canvas",
                    tint = if (isCanvasLocked) colorScheme.primary else Color.White
                )
            }

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

// ═════════════════════════════════════════════════════════════════════════════
// 1. CROP & ROTATE  — IMAGE OVERLAY (inside pan/zoom canvas)
// ═════════════════════════════════════════════════════════════════════════════
@Composable
fun CropImageOverlay(
    bitmap: Bitmap,
    previewRect: Rect,
    cropLeft: Float, cropTop: Float, cropRight: Float, cropBottom: Float,
    selectedRatio: String,
    onCropChange: (l: Float, t: Float, r: Float, b: Float) -> Unit,
    locked: Boolean
) {
    // Base image
    ComposeCanvas(modifier = Modifier.fillMaxSize()) {
        if (previewRect.width > 0) {
            val img = bitmap.asImageBitmap()
            drawImage(img,
                srcOffset = androidx.compose.ui.unit.IntOffset.Zero,
                srcSize = androidx.compose.ui.unit.IntSize(img.width, img.height),
                dstOffset = androidx.compose.ui.unit.IntOffset(previewRect.left.toInt(), previewRect.top.toInt()),
                dstSize = androidx.compose.ui.unit.IntSize(previewRect.width.toInt(), previewRect.height.toInt())
            )
        }
    }

    // Crop handles overlay (only interactive when not locked)
    if (previewRect.width > 0) {
        val bW = previewRect.width; val bH = previewRect.height
        ComposeCanvas(
            modifier = Modifier
                .fillMaxSize()
                .then(if (!locked) Modifier.pointerInput(previewRect, selectedRatio) {
                    detectDragGestures { change, _ ->
                        change.consume()
                        val x = change.position.x; val y = change.position.y
                        val relX = ((x - previewRect.left) / bW).coerceIn(0f, 1f)
                        val relY = ((y - previewRect.top) / bH).coerceIn(0f, 1f)
                        val corners = listOf(
                            Offset(cropLeft*bW + previewRect.left, cropTop*bH + previewRect.top) to "TL",
                            Offset(cropRight*bW + previewRect.left, cropTop*bH + previewRect.top) to "TR",
                            Offset(cropLeft*bW + previewRect.left, cropBottom*bH + previewRect.top) to "BL",
                            Offset(cropRight*bW + previewRect.left, cropBottom*bH + previewRect.top) to "BR"
                        )
                        val nearest = corners.minByOrNull { (pt, _) -> (change.position - pt).getDistance() }?.second
                        if (selectedRatio == "Free") {
                            when (nearest) {
                                "TL" -> onCropChange(relX.coerceAtMost(cropRight - 0.1f), relY.coerceAtMost(cropBottom - 0.1f), cropRight, cropBottom)
                                "TR" -> onCropChange(cropLeft, relY.coerceAtMost(cropBottom - 0.1f), relX.coerceAtLeast(cropLeft + 0.1f), cropBottom)
                                "BL" -> onCropChange(relX.coerceAtMost(cropRight - 0.1f), cropTop, cropRight, relY.coerceAtLeast(cropTop + 0.1f))
                                "BR" -> onCropChange(cropLeft, cropTop, relX.coerceAtLeast(cropLeft + 0.1f), relY.coerceAtLeast(cropTop + 0.1f))
                            }
                        } else {
                            val r = when (selectedRatio) { "1:1"->1f; "4:3"->4f/3f; "16:9"->16f/9f; else->1f }
                            when (nearest) {
                                "TL","BL" -> { val nL = relX.coerceAtMost(cropRight - 0.1f); val w = cropRight-nL; val h = w*(bW/bH)/r; onCropChange(nL, cropTop, cropRight, (cropTop+h).coerceIn(0f,1f)) }
                                else -> { val nR = relX.coerceAtLeast(cropLeft + 0.1f); val w = nR-cropLeft; val h = w*(bW/bH)/r; onCropChange(cropLeft, cropTop, nR, (cropTop+h).coerceIn(0f,1f)) }
                            }
                        }
                    }
                } else Modifier)
        ) {
            val lPx = previewRect.left + cropLeft*bW; val tPx = previewRect.top + cropTop*bH
            val rPx = previewRect.left + cropRight*bW; val bPx = previewRect.top + cropBottom*bH
            val cW = rPx-lPx; val cH = bPx-tPx
            // Dimming
            drawRect(Color.Black.copy(alpha=0.5f), Offset(previewRect.left, previewRect.top), Size(bW, tPx-previewRect.top))
            drawRect(Color.Black.copy(alpha=0.5f), Offset(previewRect.left, bPx), Size(bW, previewRect.bottom-bPx))
            drawRect(Color.Black.copy(alpha=0.5f), Offset(previewRect.left, tPx), Size(lPx-previewRect.left, cH))
            drawRect(Color.Black.copy(alpha=0.5f), Offset(rPx, tPx), Size(previewRect.right-rPx, cH))
            // Border
            drawRect(Color.White, Offset(lPx,tPx), Size(cW,cH), style = Stroke(2.dp.toPx()))
            // Grid lines
            drawLine(Color.White.copy(alpha=0.4f), Offset(lPx+cW/3f,tPx), Offset(lPx+cW/3f,bPx), 1.dp.toPx())
            drawLine(Color.White.copy(alpha=0.4f), Offset(lPx+cW*2/3f,tPx), Offset(lPx+cW*2/3f,bPx), 1.dp.toPx())
            drawLine(Color.White.copy(alpha=0.4f), Offset(lPx,tPx+cH/3f), Offset(rPx,tPx+cH/3f), 1.dp.toPx())
            drawLine(Color.White.copy(alpha=0.4f), Offset(lPx,tPx+cH*2/3f), Offset(rPx,tPx+cH*2/3f), 1.dp.toPx())
            // Handles
            val hs = 10.dp.toPx()
            drawCircle(Color.White, hs, Offset(lPx,tPx)); drawCircle(Color.White, hs, Offset(rPx,tPx))
            drawCircle(Color.White, hs, Offset(lPx,bPx)); drawCircle(Color.White, hs, Offset(rPx,bPx))
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 1b. CROP — TOOL OPTIONS PANEL (static, below BottomTabs)
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun CropToolOptions(
    bitmap: Bitmap,
    previewRect: Rect,
    cropLeft: Float, cropTop: Float, cropRight: Float, cropBottom: Float,
    selectedRatio: String,
    onRatioChange: (String) -> Unit,
    onRotate: (Float) -> Unit,
    onFlip: (Float, Float) -> Unit,
    onApplyCrop: () -> Unit
) {
    Surface(color = Color(0xFF1E1E1E), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
            // Preset ratio buttons
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf("Free","1:1","4:3","16:9").forEach { ratio ->
                    OutlinedButton(
                        onClick = { onRatioChange(ratio) },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = if (selectedRatio == ratio) colorScheme.primary else Color.LightGray),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        border = if (selectedRatio == ratio) androidx.compose.foundation.BorderStroke(1.5.dp, colorScheme.primary)
                                 else androidx.compose.foundation.BorderStroke(1.dp, Color.Gray.copy(alpha=0.5f))
                    ) { Text(ratio, fontSize = 12.sp) }
                }
            }
            Spacer(Modifier.height(10.dp))
            // Transform + Apply row
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.SpaceAround) {
                IconButton(onClick = { onRotate(-90f) }) { Icon(Icons.AutoMirrored.Filled.RotateLeft, "Rotate Left", tint = Color.White) }
                IconButton(onClick = { onRotate(90f) })  { Icon(Icons.AutoMirrored.Filled.RotateRight, "Rotate Right", tint = Color.White) }
                IconButton(onClick = { onFlip(-1f, 1f) }) { Icon(Icons.Default.Flip, "Flip H", tint = Color.White) }
                IconButton(onClick = { onFlip(1f, -1f) }) { Icon(Icons.Default.FlipCameraAndroid, "Flip V", tint = Color.White) }
                IconButton(onClick = onApplyCrop, modifier = Modifier.background(colorScheme.primary, CircleShape).size(40.dp)) {
                    Icon(Icons.Default.Check, "Apply Crop", tint = Color.Black)
                }
            }
        }
    }
}

// ═════════════════════════════════════════════════════════════════════════════
// 2. FILTERS — IMAGE OVERLAY
// ═════════════════════════════════════════════════════════════════════════════
@Composable
fun FiltersImageOverlay(bitmap: Bitmap, previewRect: Rect, selectedFilterIndex: Int) {
    ComposeCanvas(modifier = Modifier.fillMaxSize()) {
        if (previewRect.width > 0) {
            val fl = filtersList[selectedFilterIndex].second
            val img = bitmap.asImageBitmap()
            drawImage(img,
                srcOffset = androidx.compose.ui.unit.IntOffset.Zero,
                srcSize = androidx.compose.ui.unit.IntSize(img.width, img.height),
                dstOffset = androidx.compose.ui.unit.IntOffset(previewRect.left.toInt(), previewRect.top.toInt()),
                dstSize = androidx.compose.ui.unit.IntSize(previewRect.width.toInt(), previewRect.height.toInt()),
                colorFilter = androidx.compose.ui.graphics.ColorFilter.colorMatrix(androidx.compose.ui.graphics.ColorMatrix(fl))
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 2b. FILTERS — TOOL OPTIONS PANEL
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun FiltersToolOptions(
    bitmap: Bitmap,
    selectedFilterIndex: Int,
    onFilterSelected: (Int) -> Unit,
    onApply: (Int) -> Unit
) {
    Surface(color = Color(0xFF1E1E1E), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp)) {
            androidx.compose.foundation.lazy.LazyRow(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filtersList.size) { index ->
                    val (name, _) = filtersList[index]
                    Box(modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (selectedFilterIndex == index) colorScheme.primary.copy(alpha=0.2f) else Color(0xFF2D2D2D))
                        .border(if (selectedFilterIndex == index) 1.5.dp else 0.dp,
                                if (selectedFilterIndex == index) colorScheme.primary else Color.Transparent,
                                RoundedCornerShape(8.dp))
                        .clickable { onFilterSelected(index) }
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        Text(name, color = if (selectedFilterIndex == index) Color.White else Color.LightGray, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.End) {
                IconButton(onClick = { onApply(selectedFilterIndex) },
                           modifier = Modifier.background(colorScheme.primary, CircleShape).size(44.dp)) {
                    Icon(Icons.Default.Check, "Apply Filter", tint = Color.Black)
                }
            }
        }
    }
}

// ═════════════════════════════════════════════════════════════════════════════
// 3. ADJUST — IMAGE OVERLAY
// ═════════════════════════════════════════════════════════════════════════════
@Composable
fun AdjustImageOverlay(bitmap: Bitmap, previewRect: Rect, brightness: Float, contrast: Float, saturation: Float) {
    ComposeCanvas(modifier = Modifier.fillMaxSize()) {
        if (previewRect.width > 0) {
            val ma = buildAdjustMatrix(brightness, contrast, saturation)
            val img = bitmap.asImageBitmap()
            drawImage(img,
                srcOffset = androidx.compose.ui.unit.IntOffset.Zero,
                srcSize = androidx.compose.ui.unit.IntSize(img.width, img.height),
                dstOffset = androidx.compose.ui.unit.IntOffset(previewRect.left.toInt(), previewRect.top.toInt()),
                dstSize = androidx.compose.ui.unit.IntSize(previewRect.width.toInt(), previewRect.height.toInt()),
                colorFilter = androidx.compose.ui.graphics.ColorFilter.colorMatrix(androidx.compose.ui.graphics.ColorMatrix(ma))
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 3b. ADJUST — TOOL OPTIONS PANEL
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun AdjustToolOptions(
    brightness: Float, contrast: Float, saturation: Float,
    onBrightnessChange: (Float) -> Unit,
    onContrastChange: (Float) -> Unit,
    onSaturationChange: (Float) -> Unit,
    onApply: () -> Unit
) {
    Surface(color = Color(0xFF1E1E1E), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            AdjustmentRow("Brightness", brightness, -1f..1f, onBrightnessChange)
            AdjustmentRow("Contrast",   contrast,   0.5f..2f, onContrastChange)
            AdjustmentRow("Saturation", saturation, 0f..2f, onSaturationChange)
            Spacer(Modifier.height(6.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                IconButton(onClick = onApply, modifier = Modifier.background(colorScheme.primary, CircleShape).size(44.dp)) {
                    Icon(Icons.Default.Check, "Apply", tint = Color.Black)
                }
            }
        }
    }
}

@Composable
fun AdjustmentRow(label: String, value: Float, valueRange: ClosedFloatingPointRange<Float>, onValueChange: (Float) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Color.White, fontSize = 13.sp, modifier = Modifier.width(80.dp), fontWeight = FontWeight.Medium)
        Slider(value=value, onValueChange=onValueChange, valueRange=valueRange, modifier=Modifier.weight(1f),
               colors = SliderDefaults.colors(thumbColor=colorScheme.primary, activeTrackColor=colorScheme.primary))
        Spacer(Modifier.width(8.dp))
        Text(String.format("%.1f", value), color=Color.LightGray, fontSize=12.sp, modifier=Modifier.width(36.dp), textAlign=TextAlign.End)
    }
}

// ═════════════════════════════════════════════════════════════════════════════
// 4. DRAW — IMAGE OVERLAY
// ═════════════════════════════════════════════════════════════════════════════
@Composable
fun DrawImageOverlay(
    bitmap: Bitmap,
    previewRect: Rect,
    paths: androidx.compose.runtime.snapshots.SnapshotStateList<DrawPathFraction>,
    currentPathPoints: androidx.compose.runtime.snapshots.SnapshotStateList<DrawPointFraction>,
    brushColor: Color,
    brushSize: Float,
    isEraser: Boolean,
    locked: Boolean,
    onPathComplete: (DrawPathFraction) -> Unit
) {
    // Base image
    ComposeCanvas(modifier = Modifier.fillMaxSize()) {
        if (previewRect.width > 0) {
            val img = bitmap.asImageBitmap()
            drawImage(img,
                srcOffset = androidx.compose.ui.unit.IntOffset.Zero,
                srcSize = androidx.compose.ui.unit.IntSize(img.width, img.height),
                dstOffset = androidx.compose.ui.unit.IntOffset(previewRect.left.toInt(), previewRect.top.toInt()),
                dstSize = androidx.compose.ui.unit.IntSize(previewRect.width.toInt(), previewRect.height.toInt())
            )
        }
    }

    if (previewRect.width > 0) {
        ComposeCanvas(
            modifier = Modifier
                .fillMaxSize()
                .then(if (!locked) Modifier.pointerInput(previewRect) {
                    detectDragGestures(
                        onDragStart = { off -> if (previewRect.contains(off)) currentPathPoints.add(DrawPointFraction((off.x-previewRect.left)/previewRect.width, (off.y-previewRect.top)/previewRect.height)) },
                        onDrag = { ch, _ -> ch.consume(); val off = ch.position; if (previewRect.contains(off)) currentPathPoints.add(DrawPointFraction((off.x-previewRect.left)/previewRect.width, (off.y-previewRect.top)/previewRect.height)) },
                        onDragEnd = {
                            if (currentPathPoints.isNotEmpty()) {
                                onPathComplete(DrawPathFraction(
                                    points = currentPathPoints.toList(),
                                    color = brushColor,
                                    strokeWidth = brushSize,
                                    isEraser = isEraser
                                ))
                                currentPathPoints.clear()
                            }
                        }
                    )
                } else Modifier)
        ) {
            // Committed paths
            paths.forEach { dp ->
                val pts = dp.points.map { Offset(previewRect.left + it.x*previewRect.width, previewRect.top + it.y*previewRect.height) }
                if (pts.size > 1) {
                    val path = androidx.compose.ui.graphics.Path().apply { moveTo(pts[0].x,pts[0].y); for (i in 1 until pts.size) lineTo(pts[i].x,pts[i].y) }
                    drawPath(path, if (dp.isEraser) Color.Black else dp.color, style = Stroke(dp.strokeWidth, cap=androidx.compose.ui.graphics.StrokeCap.Round, join=androidx.compose.ui.graphics.StrokeJoin.Round))
                }
            }
            // In-progress path
            if (currentPathPoints.size > 1) {
                val pts = currentPathPoints.map { Offset(previewRect.left + it.x*previewRect.width, previewRect.top + it.y*previewRect.height) }
                val path = androidx.compose.ui.graphics.Path().apply { moveTo(pts[0].x,pts[0].y); for (i in 1 until pts.size) lineTo(pts[i].x,pts[i].y) }
                drawPath(path, if (isEraser) Color.Black else brushColor, style = Stroke(brushSize, cap=androidx.compose.ui.graphics.StrokeCap.Round, join=androidx.compose.ui.graphics.StrokeJoin.Round))
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 4b. DRAW — TOOL OPTIONS PANEL
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun DrawToolOptions(
    brushColor: Color,
    brushSize: Float,
    isEraser: Boolean,
    hasPaths: Boolean,
    onColorChange: (Color) -> Unit,
    onSizeChange: (Float) -> Unit,
    onToggleEraser: () -> Unit,
    onUndoPath: () -> Unit,
    onClearPaths: () -> Unit,
    onApply: () -> Unit
) {
    val colors = listOf(Color.Red, Color.Green, Color.Blue, Color.Yellow, Color.Magenta, Color.White, Color.Black)
    Surface(color = Color(0xFF1E1E1E), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            // Color + eraser row
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    colors.forEach { c ->
                        Box(modifier = Modifier.size(32.dp).clip(CircleShape).background(c)
                            .border(if (brushColor==c && !isEraser) 2.dp else 0.dp, Color.White, CircleShape)
                            .clickable { onColorChange(c) })
                    }
                }
                IconButton(onClick = onToggleEraser,
                           modifier = Modifier.background(if (isEraser) colorScheme.primary else Color(0xFF2D2D2D), CircleShape).size(36.dp)) {
                    Icon(Icons.Default.Brush, "Eraser", tint = if (isEraser) Color.Black else Color.White, modifier = Modifier.size(18.dp))
                }
            }
            Spacer(Modifier.height(10.dp))
            // Size + apply row
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Size", color=Color.White, fontSize=12.sp, modifier=Modifier.width(36.dp))
                Slider(value=brushSize, onValueChange=onSizeChange, valueRange=2f..80f, modifier=Modifier.weight(1f),
                       colors=SliderDefaults.colors(thumbColor=colorScheme.primary, activeTrackColor=colorScheme.primary))
                Spacer(Modifier.width(4.dp))
                IconButton(onClick=onUndoPath, enabled=hasPaths) { Icon(Icons.AutoMirrored.Filled.Undo, "Undo", tint=if (hasPaths) Color.White else Color.Gray) }
                IconButton(onClick=onClearPaths, enabled=hasPaths) { Icon(Icons.Default.Close, "Clear", tint=if (hasPaths) Color.White else Color.Gray) }
                IconButton(onClick=onApply, modifier=Modifier.background(colorScheme.primary, CircleShape).size(40.dp)) {
                    Icon(Icons.Default.Check, "Apply", tint=Color.Black)
                }
            }
        }
    }
}

// SAVE TYPE CHOOSER DIALOG — with rename support
@Composable
fun SaveChooserDialog(
    onDismiss: () -> Unit,
    onConfirm: (filename: String, overwrite: Boolean) -> Unit
) {
    // List existing images in Pictures/DCIM for context
    val existingFiles = remember {
        val picDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
        val dcimDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM)
        val imageExts = setOf("jpg", "jpeg", "png", "webp", "bmp")
        (picDir.listFiles()?.toList().orEmpty() + dcimDir.listFiles()?.toList().orEmpty())
            .filter { it.isFile && it.extension.lowercase() in imageExts }
            .map { it.name }
            .sorted()
    }

    var filename by remember { mutableStateOf("edited_image") }
    var overwrite by remember { mutableStateOf(false) }
    val conflict = existingFiles.any { it.startsWith(filename) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Save Image") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Filename input
                OutlinedTextField(
                    value = filename,
                    onValueChange = { filename = it.replace("/", "").replace("\\", "") },
                    label = { Text("Filename (without extension)") },
                    isError = conflict && !overwrite,
                    supportingText = if (conflict && !overwrite) {
                        { Text("A file with this name already exists. Enable overwrite or change the name.", color = colorScheme.error) }
                    } else null,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Overwrite checkbox row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { overwrite = !overwrite }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    androidx.compose.material3.Checkbox(
                        checked = overwrite,
                        onCheckedChange = { overwrite = it }
                    )
                    Text(text = "Overwrite original file", fontSize = 14.sp)
                }

                // Existing files (greyed-out context)
                if (existingFiles.isNotEmpty()) {
                    Text(
                        text = "Existing files in Pictures:",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp)
                    ) {
                        items(existingFiles) { name ->
                            Text(
                                text = name,
                                fontSize = 12.sp,
                                color = Color.Gray.copy(alpha = 0.5f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(filename, overwrite) },
                enabled = filename.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = colorScheme.primary)
            ) {
                Text("Save", color = Color.Black)
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
