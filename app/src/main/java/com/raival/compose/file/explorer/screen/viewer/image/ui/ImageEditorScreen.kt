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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CropFree
import androidx.compose.material.icons.rounded.RestartAlt
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
import androidx.compose.runtime.rememberUpdatedState
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
import androidx.compose.ui.graphics.StrokeCap
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
import kotlin.math.abs
import kotlin.math.hypot
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
                    if (ratio == "Free") {
                        // Keep current crop bounds so user can freely adjust from current framing
                        return@CropToolOptions
                    }
                    val r = when (ratio) { "1:1"->1f; "4:3"->4f/3f; "16:9"->16f/9f; else->1f }
                    val iW = previewImageRect.width; val iH = previewImageRect.height
                    if (iW <= 0 || iH <= 0) return@CropToolOptions
                    if (iW/iH > r) { val f = (iH*r)/iW; cropLeft=(1f-f)/2f; cropRight=1f-cropLeft; cropTop=0f; cropBottom=1f }
                    else { val f = (iW/r)/iH; cropTop=(1f-f)/2f; cropBottom=1f-cropTop; cropLeft=0f; cropRight=1f }
                },
                onResetCrop = {
                    cropLeft = 0f
                    cropTop = 0f
                    cropRight = 1f
                    cropBottom = 1f
                    selectedRatio = "Free"
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
private enum class CropHandle {
    NONE,
    TOP_LEFT,
    TOP_RIGHT,
    BOTTOM_LEFT,
    BOTTOM_RIGHT,
    EDGE_TOP,
    EDGE_BOTTOM,
    EDGE_LEFT,
    EDGE_RIGHT,
    BODY
}

@Composable
fun CropImageOverlay(
    bitmap: Bitmap,
    previewRect: Rect,
    cropLeft: Float, cropTop: Float, cropRight: Float, cropBottom: Float,
    selectedRatio: String,
    onCropChange: (l: Float, t: Float, r: Float, b: Float) -> Unit,
    locked: Boolean
) {
    val currentCropLeft by rememberUpdatedState(cropLeft)
    val currentCropTop by rememberUpdatedState(cropTop)
    val currentCropRight by rememberUpdatedState(cropRight)
    val currentCropBottom by rememberUpdatedState(cropBottom)
    val currentSelectedRatio by rememberUpdatedState(selectedRatio)
    val currentOnCropChange by rememberUpdatedState(onCropChange)

    // Base image
    ComposeCanvas(modifier = Modifier.fillMaxSize()) {
        if (previewRect.width > 0) {
            val img = bitmap.asImageBitmap()
            drawImage(
                img,
                srcOffset = androidx.compose.ui.unit.IntOffset.Zero,
                srcSize = androidx.compose.ui.unit.IntSize(img.width, img.height),
                dstOffset = androidx.compose.ui.unit.IntOffset(previewRect.left.toInt(), previewRect.top.toInt()),
                dstSize = androidx.compose.ui.unit.IntSize(previewRect.width.toInt(), previewRect.height.toInt())
            )
        }
    }

    // Crop handles overlay (only interactive when not locked)
    if (previewRect.width > 0) {
        val bW = previewRect.width
        val bH = previewRect.height
        ComposeCanvas(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (!locked) {
                        Modifier.pointerInput(previewRect) {
                            var activeHandle = CropHandle.NONE
                            var startCropL = 0f
                            var startCropT = 0f
                            var startCropR = 1f
                            var startCropB = 1f
                            var startTouchX = 0f
                            var startTouchY = 0f

                            detectDragGestures(
                                onDragStart = { downPos ->
                                    if (bW <= 0f || bH <= 0f) {
                                        activeHandle = CropHandle.NONE
                                        return@detectDragGestures
                                    }

                                    val lPx = previewRect.left + currentCropLeft * bW
                                    val tPx = previewRect.top + currentCropTop * bH
                                    val rPx = previewRect.left + currentCropRight * bW
                                    val bPx = previewRect.top + currentCropBottom * bH

                                    val cornerTouchRadius = 40.dp.toPx()
                                    val edgeTouchThreshold = 24.dp.toPx()

                                    val distTL = hypot(downPos.x - lPx, downPos.y - tPx)
                                    val distTR = hypot(downPos.x - rPx, downPos.y - tPx)
                                    val distBL = hypot(downPos.x - lPx, downPos.y - bPx)
                                    val distBR = hypot(downPos.x - rPx, downPos.y - bPx)

                                    val minCornerDist = minOf(distTL, distTR, distBL, distBR)
                                    if (minCornerDist <= cornerTouchRadius) {
                                        activeHandle = when (minCornerDist) {
                                            distTL -> CropHandle.TOP_LEFT
                                            distTR -> CropHandle.TOP_RIGHT
                                            distBL -> CropHandle.BOTTOM_LEFT
                                            else -> CropHandle.BOTTOM_RIGHT
                                        }
                                    } else {
                                        val inHorizontalSpan = downPos.x in (lPx - edgeTouchThreshold)..(rPx + edgeTouchThreshold)
                                        val inVerticalSpan = downPos.y in (tPx - edgeTouchThreshold)..(bPx + edgeTouchThreshold)

                                        val distTop = abs(downPos.y - tPx)
                                        val distBottom = abs(downPos.y - bPx)
                                        val distLeft = abs(downPos.x - lPx)
                                        val distRight = abs(downPos.x - rPx)

                                        if (inHorizontalSpan && distTop <= edgeTouchThreshold && distTop < distBottom) {
                                            activeHandle = CropHandle.EDGE_TOP
                                        } else if (inHorizontalSpan && distBottom <= edgeTouchThreshold) {
                                            activeHandle = CropHandle.EDGE_BOTTOM
                                        } else if (inVerticalSpan && distLeft <= edgeTouchThreshold && distLeft < distRight) {
                                            activeHandle = CropHandle.EDGE_LEFT
                                        } else if (inVerticalSpan && distRight <= edgeTouchThreshold) {
                                            activeHandle = CropHandle.EDGE_RIGHT
                                        } else if (downPos.x in lPx..rPx && downPos.y in tPx..bPx) {
                                            activeHandle = CropHandle.BODY
                                        } else {
                                            activeHandle = CropHandle.NONE
                                        }
                                    }

                                    if (activeHandle != CropHandle.NONE) {
                                        startCropL = currentCropLeft
                                        startCropT = currentCropTop
                                        startCropR = currentCropRight
                                        startCropB = currentCropBottom
                                        startTouchX = downPos.x
                                        startTouchY = downPos.y
                                    }
                                },
                                onDragEnd = { activeHandle = CropHandle.NONE },
                                onDragCancel = { activeHandle = CropHandle.NONE },
                                onDrag = { change, _ ->
                                    if (activeHandle == CropHandle.NONE) return@detectDragGestures
                                    change.consume()

                                    if (bW <= 0f || bH <= 0f) return@detectDragGestures

                                    val totalDx = (change.position.x - startTouchX) / bW
                                    val totalDy = (change.position.y - startTouchY) / bH
                                    val minSize = 0.05f

                                    if (activeHandle == CropHandle.BODY) {
                                        val boxW = startCropR - startCropL
                                        val boxH = startCropB - startCropT
                                        val newL = (startCropL + totalDx).coerceIn(0f, 1f - boxW)
                                        val newT = (startCropT + totalDy).coerceIn(0f, 1f - boxH)
                                        currentOnCropChange(newL, newT, newL + boxW, newT + boxH)
                                        return@detectDragGestures
                                    }

                                    val ratio = currentSelectedRatio
                                    if (ratio == "Free") {
                                        var newL = startCropL
                                        var newT = startCropT
                                        var newR = startCropR
                                        var newB = startCropB

                                        when (activeHandle) {
                                            CropHandle.TOP_LEFT -> {
                                                newL = (startCropL + totalDx).coerceIn(0f, startCropR - minSize)
                                                newT = (startCropT + totalDy).coerceIn(0f, startCropB - minSize)
                                            }
                                            CropHandle.TOP_RIGHT -> {
                                                newR = (startCropR + totalDx).coerceIn(startCropL + minSize, 1f)
                                                newT = (startCropT + totalDy).coerceIn(0f, startCropB - minSize)
                                            }
                                            CropHandle.BOTTOM_LEFT -> {
                                                newL = (startCropL + totalDx).coerceIn(0f, startCropR - minSize)
                                                newB = (startCropB + totalDy).coerceIn(startCropT + minSize, 1f)
                                            }
                                            CropHandle.BOTTOM_RIGHT -> {
                                                newR = (startCropR + totalDx).coerceIn(startCropL + minSize, 1f)
                                                newB = (startCropB + totalDy).coerceIn(startCropT + minSize, 1f)
                                            }
                                            CropHandle.EDGE_TOP -> {
                                                newT = (startCropT + totalDy).coerceIn(0f, startCropB - minSize)
                                            }
                                            CropHandle.EDGE_BOTTOM -> {
                                                newB = (startCropB + totalDy).coerceIn(startCropT + minSize, 1f)
                                            }
                                            CropHandle.EDGE_LEFT -> {
                                                newL = (startCropL + totalDx).coerceIn(0f, startCropR - minSize)
                                            }
                                            CropHandle.EDGE_RIGHT -> {
                                                newR = (startCropR + totalDx).coerceIn(startCropL + minSize, 1f)
                                            }
                                            else -> {}
                                        }
                                        currentOnCropChange(newL, newT, newR, newB)
                                    } else {
                                        val r = when (ratio) {
                                            "1:1" -> 1f
                                            "4:3" -> 4f / 3f
                                            "16:9" -> 16f / 9f
                                            else -> 1f
                                        }
                                        val normRatio = (r * bH) / bW
                                        if (normRatio <= 0f) return@detectDragGestures

                                        val minNormW = minSize
                                        val minNormH = minNormW / normRatio

                                        when (activeHandle) {
                                            CropHandle.BOTTOM_RIGHT -> {
                                                val candW = startCropR - startCropL + totalDx
                                                val candH = startCropB - startCropT + totalDy
                                                val chosenW = if (abs(totalDx) >= abs(totalDy * normRatio)) candW else candH * normRatio
                                                val maxW = 1f - startCropL
                                                val maxH = 1f - startCropT
                                                val allowedW = min(maxW, maxH * normRatio)
                                                val finalW = chosenW.coerceIn(minNormW, allowedW)
                                                val finalH = finalW / normRatio
                                                currentOnCropChange(startCropL, startCropT, startCropL + finalW, startCropT + finalH)
                                            }
                                            CropHandle.TOP_LEFT -> {
                                                val candW = startCropR - startCropL - totalDx
                                                val candH = startCropB - startCropT - totalDy
                                                val chosenW = if (abs(totalDx) >= abs(totalDy * normRatio)) candW else candH * normRatio
                                                val maxW = startCropR
                                                val maxH = startCropB
                                                val allowedW = min(maxW, maxH * normRatio)
                                                val finalW = chosenW.coerceIn(minNormW, allowedW)
                                                val finalH = finalW / normRatio
                                                currentOnCropChange(startCropR - finalW, startCropB - finalH, startCropR, startCropB)
                                            }
                                            CropHandle.TOP_RIGHT -> {
                                                val candW = startCropR - startCropL + totalDx
                                                val candH = startCropB - startCropT - totalDy
                                                val chosenW = if (abs(totalDx) >= abs(totalDy * normRatio)) candW else candH * normRatio
                                                val maxW = 1f - startCropL
                                                val maxH = startCropB
                                                val allowedW = min(maxW, maxH * normRatio)
                                                val finalW = chosenW.coerceIn(minNormW, allowedW)
                                                val finalH = finalW / normRatio
                                                currentOnCropChange(startCropL, startCropB - finalH, startCropL + finalW, startCropB)
                                            }
                                            CropHandle.BOTTOM_LEFT -> {
                                                val candW = startCropR - startCropL - totalDx
                                                val candH = startCropB - startCropT + totalDy
                                                val chosenW = if (abs(totalDx) >= abs(totalDy * normRatio)) candW else candH * normRatio
                                                val maxW = startCropR
                                                val maxH = 1f - startCropT
                                                val allowedW = min(maxW, maxH * normRatio)
                                                val finalW = chosenW.coerceIn(minNormW, allowedW)
                                                val finalH = finalW / normRatio
                                                currentOnCropChange(startCropR - finalW, startCropT, startCropR, startCropT + finalH)
                                            }
                                            CropHandle.EDGE_RIGHT -> {
                                                val candW = startCropR - startCropL + totalDx
                                                val centerY = (startCropT + startCropB) / 2f
                                                val maxW = min(1f - startCropL, min(centerY, 1f - centerY) * 2f * normRatio)
                                                val finalW = candW.coerceIn(minNormW, maxW)
                                                val finalH = finalW / normRatio
                                                currentOnCropChange(startCropL, centerY - finalH / 2f, startCropL + finalW, centerY + finalH / 2f)
                                            }
                                            CropHandle.EDGE_LEFT -> {
                                                val candW = startCropR - startCropL - totalDx
                                                val centerY = (startCropT + startCropB) / 2f
                                                val maxW = min(startCropR, min(centerY, 1f - centerY) * 2f * normRatio)
                                                val finalW = candW.coerceIn(minNormW, maxW)
                                                val finalH = finalW / normRatio
                                                currentOnCropChange(startCropR - finalW, centerY - finalH / 2f, startCropR, centerY + finalH / 2f)
                                            }
                                            CropHandle.EDGE_BOTTOM -> {
                                                val candH = startCropB - startCropT + totalDy
                                                val centerX = (startCropL + startCropR) / 2f
                                                val maxH = min(1f - startCropT, min(centerX, 1f - centerX) * 2f / normRatio)
                                                val finalH = candH.coerceIn(minNormH, maxH)
                                                val finalW = finalH * normRatio
                                                currentOnCropChange(centerX - finalW / 2f, startCropT, centerX + finalW / 2f, startCropT + finalH)
                                            }
                                            CropHandle.EDGE_TOP -> {
                                                val candH = startCropB - startCropT - totalDy
                                                val centerX = (startCropL + startCropR) / 2f
                                                val maxH = min(startCropT, min(centerX, 1f - centerX) * 2f / normRatio)
                                                val finalH = candH.coerceIn(minNormH, maxH)
                                                val finalW = finalH * normRatio
                                                currentOnCropChange(centerX - finalW / 2f, startCropB - finalH, centerX + finalW / 2f, startCropB)
                                            }
                                            else -> {}
                                        }
                                    }
                                }
                            )
                        }
                    } else Modifier
                )
        ) {
            val lPx = previewRect.left + cropLeft * bW
            val tPx = previewRect.top + cropTop * bH
            val rPx = previewRect.left + cropRight * bW
            val bPx = previewRect.top + cropBottom * bH
            val cW = rPx - lPx
            val cH = bPx - tPx

            // Dimming outside crop area
            drawRect(Color.Black.copy(alpha = 0.55f), Offset(previewRect.left, previewRect.top), Size(bW, (tPx - previewRect.top).coerceAtLeast(0f)))
            drawRect(Color.Black.copy(alpha = 0.55f), Offset(previewRect.left, bPx), Size(bW, (previewRect.bottom - bPx).coerceAtLeast(0f)))
            drawRect(Color.Black.copy(alpha = 0.55f), Offset(previewRect.left, tPx), Size((lPx - previewRect.left).coerceAtLeast(0f), cH))
            drawRect(Color.Black.copy(alpha = 0.55f), Offset(rPx, tPx), Size((previewRect.right - rPx).coerceAtLeast(0f), cH))

            // White border with subtle dark outline for contrast
            drawRect(Color.Black.copy(alpha = 0.35f), Offset(lPx - 1.dp.toPx(), tPx - 1.dp.toPx()), Size(cW + 2.dp.toPx(), cH + 2.dp.toPx()), style = Stroke(3.dp.toPx()))
            drawRect(Color.White, Offset(lPx, tPx), Size(cW, cH), style = Stroke(2.dp.toPx()))

            // Grid lines (rule of thirds)
            drawLine(Color.White.copy(alpha = 0.4f), Offset(lPx + cW / 3f, tPx), Offset(lPx + cW / 3f, bPx), 1.dp.toPx())
            drawLine(Color.White.copy(alpha = 0.4f), Offset(lPx + cW * 2 / 3f, tPx), Offset(lPx + cW * 2 / 3f, bPx), 1.dp.toPx())
            drawLine(Color.White.copy(alpha = 0.4f), Offset(lPx, tPx + cH / 3f), Offset(rPx, tPx + cH / 3f), 1.dp.toPx())
            drawLine(Color.White.copy(alpha = 0.4f), Offset(lPx, tPx + cH * 2 / 3f), Offset(rPx, tPx + cH * 2 / 3f), 1.dp.toPx())

            // Corner handles (L-shaped thick brackets with dark outline)
            val cornerLength = 22.dp.toPx()
            val cornerStroke = 4.dp.toPx()
            val cornerColor = Color.White
            val cornerOutline = Color.Black.copy(alpha = 0.5f)

            fun drawCorner(x: Float, y: Float, dirX: Float, dirY: Float) {
                // Outline
                drawLine(cornerOutline, Offset(x - dirX * 1.5.dp.toPx(), y), Offset(x + dirX * cornerLength, y), cornerStroke + 2.5.dp.toPx(), StrokeCap.Round)
                drawLine(cornerOutline, Offset(x, y - dirY * 1.5.dp.toPx()), Offset(x, y + dirY * cornerLength), cornerStroke + 2.5.dp.toPx(), StrokeCap.Round)
                // Foreground stroke
                drawLine(cornerColor, Offset(x, y), Offset(x + dirX * cornerLength, y), cornerStroke, StrokeCap.Round)
                drawLine(cornerColor, Offset(x, y), Offset(x, y + dirY * cornerLength), cornerStroke, StrokeCap.Round)
            }

            drawCorner(lPx, tPx, 1f, 1f)   // TL
            drawCorner(rPx, tPx, -1f, 1f)  // TR
            drawCorner(lPx, bPx, 1f, -1f)  // BL
            drawCorner(rPx, bPx, -1f, -1f) // BR

            // Midpoint edge handles
            val edgeBarLength = 20.dp.toPx()
            val edgeBarStroke = 3.5.dp.toPx()
            val midX = lPx + cW / 2f
            val midY = tPx + cH / 2f

            // Top edge bar
            drawLine(cornerOutline, Offset(midX - edgeBarLength / 2f, tPx), Offset(midX + edgeBarLength / 2f, tPx), edgeBarStroke + 2.dp.toPx(), StrokeCap.Round)
            drawLine(cornerColor, Offset(midX - edgeBarLength / 2f, tPx), Offset(midX + edgeBarLength / 2f, tPx), edgeBarStroke, StrokeCap.Round)
            // Bottom edge bar
            drawLine(cornerOutline, Offset(midX - edgeBarLength / 2f, bPx), Offset(midX + edgeBarLength / 2f, bPx), edgeBarStroke + 2.dp.toPx(), StrokeCap.Round)
            drawLine(cornerColor, Offset(midX - edgeBarLength / 2f, bPx), Offset(midX + edgeBarLength / 2f, bPx), edgeBarStroke, StrokeCap.Round)
            // Left edge bar
            drawLine(cornerOutline, Offset(lPx, midY - edgeBarLength / 2f), Offset(lPx, midY + edgeBarLength / 2f), edgeBarStroke + 2.dp.toPx(), StrokeCap.Round)
            drawLine(cornerColor, Offset(lPx, midY - edgeBarLength / 2f), Offset(lPx, midY + edgeBarLength / 2f), edgeBarStroke, StrokeCap.Round)
            // Right edge bar
            drawLine(cornerOutline, Offset(rPx, midY - edgeBarLength / 2f), Offset(rPx, midY + edgeBarLength / 2f), edgeBarStroke + 2.dp.toPx(), StrokeCap.Round)
            drawLine(cornerColor, Offset(rPx, midY - edgeBarLength / 2f), Offset(rPx, midY + edgeBarLength / 2f), edgeBarStroke, StrokeCap.Round)
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
    onResetCrop: () -> Unit,
    onApplyCrop: () -> Unit
) {
    Surface(color = Color(0xFF1E1E1E), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
            // Preset ratio buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("Free", "1:1", "4:3", "16:9").forEach { ratio ->
                    val isSelected = selectedRatio == ratio
                    Button(
                        onClick = { onRatioChange(ratio) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isSelected) colorScheme.primary else Color(0xFF2C2C2C),
                            contentColor = if (isSelected) Color.Black else Color.White
                        ),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
                    ) {
                        if (ratio == "Free") {
                            Icon(
                                imageVector = Icons.Rounded.CropFree,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                        }
                        Text(
                            text = ratio,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            // Transform + Reset + Apply row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { onRotate(-90f) }) {
                    Icon(Icons.AutoMirrored.Filled.RotateLeft, "Rotate Left", tint = Color.White)
                }
                IconButton(onClick = { onRotate(90f) }) {
                    Icon(Icons.AutoMirrored.Filled.RotateRight, "Rotate Right", tint = Color.White)
                }
                IconButton(onClick = { onFlip(-1f, 1f) }) {
                    Icon(Icons.Default.Flip, "Flip H", tint = Color.White)
                }
                IconButton(onClick = { onFlip(1f, -1f) }) {
                    Icon(Icons.Default.FlipCameraAndroid, "Flip V", tint = Color.White)
                }
                IconButton(onClick = onResetCrop) {
                    Icon(Icons.Rounded.RestartAlt, "Reset Crop", tint = Color.White)
                }
                IconButton(
                    onClick = onApplyCrop,
                    modifier = Modifier
                        .background(colorScheme.primary, CircleShape)
                        .size(40.dp)
                ) {
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
                    enabled = !overwrite,
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
                enabled = overwrite || filename.isNotBlank(),
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
