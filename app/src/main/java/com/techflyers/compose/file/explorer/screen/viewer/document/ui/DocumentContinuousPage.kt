package com.techflyers.compose.file.explorer.screen.viewer.document.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.techflyers.compose.file.explorer.screen.viewer.document.core.PdfEditTool
import com.techflyers.compose.file.explorer.screen.viewer.document.core.PdfMarkup
import com.techflyers.compose.file.explorer.screen.viewer.document.core.PdfSearchMatch
import com.techflyers.compose.file.explorer.screen.viewer.document.core.PdfTextBlock
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.layout.onGloballyPositioned
import com.techflyers.compose.file.explorer.screen.viewer.document.selection.PdfTextSelectionState

private val DarkInvertMatrix = ColorMatrix(
    floatArrayOf(
        -0.85f, 0f, 0f, 0f, 240f,
        0f, -0.85f, 0f, 0f, 240f,
        0f, 0f, -0.85f, 0f, 240f,
        0f, 0f, 0f, 1f, 0f
    )
)

@Composable
fun DocumentContinuousPage(
    pageIndex: Int,
    bitmap: Bitmap?,
    aspectRatio: Float,
    darkPageAppearance: Boolean,
    markups: List<PdfMarkup>,
    searchMatches: List<PdfSearchMatch>,
    currentMatchIndex: Int,
    allMatches: List<PdfSearchMatch>,
    textBlocks: List<PdfTextBlock>,
    textSelection: PdfTextSelectionState? = null,
    activeTool: PdfEditTool,
    currentColor: Color,
    currentStrokeWidth: Float,
    onTap: () -> Unit,
    onAddMarkup: (PdfMarkup) -> Unit,
    onPlaceText: (Offset) -> Unit,
    onPlaceNote: (Offset) -> Unit,
    onEditNote: (PdfMarkup.StickyNoteMarkup) -> Unit,
    modifier: Modifier = Modifier
) {
    var pageSize by remember { mutableStateOf(IntSize.Zero) }

    // Draft drawing state
    var draftPoints by remember(pageIndex, activeTool) { mutableStateOf<List<Offset>>(emptyList()) }
    var draftStart by remember(pageIndex, activeTool) { mutableStateOf<Offset?>(null) }
    var draftEnd by remember(pageIndex, activeTool) { mutableStateOf<Offset?>(null) }

    val pageColorFilter = remember(darkPageAppearance) {
        if (darkPageAppearance) ColorFilter.colorMatrix(DarkInvertMatrix) else null
    }

    DisposableEffect(pageIndex, textSelection) {
        onDispose {
            textSelection?.unregisterPage(pageIndex, null)
        }
    }

    val baseModifier = modifier
        .fillMaxWidth()
        .padding(horizontal = 12.dp, vertical = 6.dp)
        .aspectRatio(aspectRatio.coerceIn(0.2f, 5f))
        .clip(RoundedCornerShape(12.dp))
        .background(if (darkPageAppearance) Color(0xFF1E1E1E) else Color.White)
        .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
        .onSizeChanged { pageSize = it }
        .onGloballyPositioned { coords ->
            textSelection?.registerPage(pageIndex, coords)
        }

    val pageModifier = if (activeTool == PdfEditTool.None) {
        baseModifier
    } else {
        baseModifier.pointerInput(pageIndex, activeTool, currentColor, currentStrokeWidth) {
            if (activeTool == PdfEditTool.Text || activeTool == PdfEditTool.Note) {
                detectTapGestures(
                    onTap = { offset ->
                        if (activeTool == PdfEditTool.Text) onPlaceText(offset)
                        else onPlaceNote(offset)
                    }
                )
            } else {
                detectDragGestures(
                    onDragStart = { offset ->
                        when (activeTool) {
                            PdfEditTool.Draw, PdfEditTool.Highlight -> {
                                draftPoints = listOf(offset)
                            }
                            PdfEditTool.Rect, PdfEditTool.Ellipse, PdfEditTool.Line, PdfEditTool.Arrow -> {
                                draftStart = offset
                                draftEnd = offset
                            }
                            else -> {}
                        }
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        when (activeTool) {
                            PdfEditTool.Draw, PdfEditTool.Highlight -> {
                                draftPoints = draftPoints + change.position
                            }
                            PdfEditTool.Rect, PdfEditTool.Ellipse, PdfEditTool.Line, PdfEditTool.Arrow -> {
                                draftEnd = change.position
                            }
                            else -> {}
                        }
                    },
                    onDragEnd = {
                        when (activeTool) {
                            PdfEditTool.Draw -> {
                                if (draftPoints.size > 1) {
                                    onAddMarkup(
                                        PdfMarkup.StrokeMarkup(
                                            points = draftPoints,
                                            color = currentColor,
                                            width = currentStrokeWidth
                                        )
                                    )
                                }
                            }
                            PdfEditTool.Highlight -> {
                                if (draftPoints.size > 1) {
                                    onAddMarkup(
                                        PdfMarkup.StrokeMarkup(
                                            points = draftPoints,
                                            color = currentColor.copy(alpha = 0.38f),
                                            width = currentStrokeWidth * 3.5f,
                                            alpha = 0.38f
                                        )
                                    )
                                }
                            }
                            PdfEditTool.Rect -> {
                                if (draftStart != null && draftEnd != null) {
                                    onAddMarkup(
                                        PdfMarkup.RectMarkup(
                                            start = draftStart!!,
                                            end = draftEnd!!,
                                            color = currentColor
                                        )
                                    )
                                }
                            }
                            PdfEditTool.Ellipse -> {
                                if (draftStart != null && draftEnd != null) {
                                    onAddMarkup(
                                        PdfMarkup.OvalMarkup(
                                            start = draftStart!!,
                                            end = draftEnd!!,
                                            color = currentColor
                                        )
                                    )
                                }
                            }
                            PdfEditTool.Line -> {
                                if (draftStart != null && draftEnd != null) {
                                    onAddMarkup(
                                        PdfMarkup.LineMarkup(
                                            start = draftStart!!,
                                            end = draftEnd!!,
                                            color = currentColor,
                                            width = currentStrokeWidth,
                                            arrowHead = false
                                        )
                                    )
                                }
                            }
                            PdfEditTool.Arrow -> {
                                if (draftStart != null && draftEnd != null) {
                                    onAddMarkup(
                                        PdfMarkup.LineMarkup(
                                            start = draftStart!!,
                                            end = draftEnd!!,
                                            color = currentColor,
                                            width = currentStrokeWidth,
                                            arrowHead = true
                                        )
                                    )
                                }
                            }
                            else -> {}
                        }
                        draftPoints = emptyList()
                        draftStart = null
                        draftEnd = null
                    },
                    onDragCancel = {
                        draftPoints = emptyList()
                        draftStart = null
                        draftEnd = null
                    }
                )
            }
        }
    }

    Box(modifier = pageModifier) {
        // Page Bitmap
        if (bitmap != null && !bitmap.isRecycled) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Page ${pageIndex + 1}",
                contentScale = ContentScale.FillWidth,
                colorFilter = pageColorFilter,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(modifier = Modifier.size(32.dp), strokeWidth = 2.dp)
            }
        }

        // Overlay Markups & Highlights Canvas
        Canvas(modifier = Modifier.fillMaxSize()) {
            val widthF = size.width
            val heightF = size.height

            // 0. Text Selection Highlight
            textSelection?.pageRange(pageIndex)?.let { (from, to) ->
                val selColor = Color(0xFF2196F3).copy(alpha = 0.35f)
                textSelection.layout(pageIndex)?.selectionRects(from, to, size)?.forEach { r ->
                    drawRoundRect(
                        color = selColor,
                        topLeft = r.topLeft,
                        size = r.size,
                        cornerRadius = CornerRadius(4f, 4f)
                    )
                }
            }

            // 1. Search Highlights
            searchMatches.forEach { match ->
                val isCurrent = allMatches.getOrNull(currentMatchIndex) == match
                val highlightColor = if (isCurrent) Color(0xFFFF9500).copy(alpha = 0.6f) else Color(0xFFFFEB3B).copy(alpha = 0.45f)
                val left = match.left * widthF
                val top = match.top * heightF
                val right = match.right * widthF
                val bottom = match.bottom * heightF
                drawRoundRect(
                    color = highlightColor,
                    topLeft = Offset(left, top),
                    size = Size(right - left, bottom - top),
                    cornerRadius = CornerRadius(4f, 4f)
                )
            }

            // 2. Existing Markups
            markups.forEach { markup ->
                renderMarkup(markup)
            }

            // 3. Draft shapes during gesture
            if (draftPoints.size > 1) {
                val path = Path().apply {
                    moveTo(draftPoints[0].x, draftPoints[0].y)
                    for (i in 1 until draftPoints.size) {
                        lineTo(draftPoints[i].x, draftPoints[i].y)
                    }
                }
                val isHighlighter = activeTool == PdfEditTool.Highlight
                drawPath(
                    path = path,
                    color = if (isHighlighter) currentColor.copy(alpha = 0.38f) else currentColor,
                    style = Stroke(
                        width = if (isHighlighter) currentStrokeWidth * 3.5f else currentStrokeWidth,
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )
            }

            if (draftStart != null && draftEnd != null) {
                val s = draftStart!!
                val e = draftEnd!!
                when (activeTool) {
                    PdfEditTool.Rect -> {
                        val minX = minOf(s.x, e.x)
                        val minY = minOf(s.y, e.y)
                        val maxX = maxOf(s.x, e.x)
                        val maxY = maxOf(s.y, e.y)
                        drawRect(
                            color = currentColor,
                            topLeft = Offset(minX, minY),
                            size = Size(maxX - minX, maxY - minY),
                            style = Stroke(width = currentStrokeWidth)
                        )
                    }
                    PdfEditTool.Ellipse -> {
                        val minX = minOf(s.x, e.x)
                        val minY = minOf(s.y, e.y)
                        val maxX = maxOf(s.x, e.x)
                        val maxY = maxOf(s.y, e.y)
                        drawOval(
                            color = currentColor,
                            topLeft = Offset(minX, minY),
                            size = Size(maxX - minX, maxY - minY),
                            style = Stroke(width = currentStrokeWidth)
                        )
                    }
                    PdfEditTool.Line -> {
                        drawLine(
                            color = currentColor,
                            start = s,
                            end = e,
                            strokeWidth = currentStrokeWidth,
                            cap = StrokeCap.Round
                        )
                    }
                    PdfEditTool.Arrow -> {
                        drawArrow(
                            color = currentColor,
                            start = s,
                            end = e,
                            strokeWidth = currentStrokeWidth
                        )
                    }
                    else -> {}
                }
            }
        }

        // 4. Interactive Overlay Elements (Sticky notes, stamps, text boxes)
        markups.forEach { markup ->
            when (markup) {
                is PdfMarkup.StickyNoteMarkup -> {
                    Box(
                        modifier = Modifier
                            .offset { IntOffset(markup.position.x.toInt(), markup.position.y.toInt()) }
                            .size(32.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(markup.color)
                            .clickable { onEditNote(markup) }
                            .padding(4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.EditNote,
                            contentDescription = "Sticky Note",
                            tint = Color.Black,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                is PdfMarkup.TextBoxMarkup -> {
                    Surface(
                        modifier = Modifier
                            .offset { IntOffset(markup.position.x.toInt(), markup.position.y.toInt()) }
                            .padding(4.dp),
                        color = Color.Transparent
                    ) {
                        Text(
                            text = markup.text,
                            color = markup.color,
                            fontSize = markup.fontSize.sp
                        )
                    }
                }
                is PdfMarkup.ImageMarkup -> {
                    val w = (markup.end.x - markup.start.x).coerceAtLeast(10f)
                    val h = (markup.end.y - markup.start.y).coerceAtLeast(10f)
                    if (!markup.bitmap.isRecycled) {
                        Image(
                            bitmap = markup.bitmap.asImageBitmap(),
                            contentDescription = "Image stamp",
                            modifier = Modifier
                                .offset { IntOffset(markup.start.x.toInt(), markup.start.y.toInt()) }
                                .size(w.dp, h.dp)
                        )
                    }
                }
                else -> {}
            }
        }
    }
}

private fun DrawScope.renderMarkup(markup: PdfMarkup) {
    when (markup) {
        is PdfMarkup.StrokeMarkup -> {
            if (markup.points.size > 1) {
                val path = Path().apply {
                    moveTo(markup.points[0].x, markup.points[0].y)
                    for (i in 1 until markup.points.size) {
                        lineTo(markup.points[i].x, markup.points[i].y)
                    }
                }
                drawPath(
                    path = path,
                    color = markup.color.copy(alpha = markup.alpha),
                    style = Stroke(
                        width = markup.width,
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )
            }
        }
        is PdfMarkup.RectMarkup -> {
            val minX = minOf(markup.start.x, markup.end.x)
            val minY = minOf(markup.start.y, markup.end.y)
            val maxX = maxOf(markup.start.x, markup.end.x)
            val maxY = maxOf(markup.start.y, markup.end.y)
            drawRect(
                color = markup.color.copy(alpha = markup.alpha),
                topLeft = Offset(minX, minY),
                size = Size(maxX - minX, maxY - minY),
                style = if (markup.filled) Fill else Stroke(width = 4f)
            )
        }
        is PdfMarkup.OvalMarkup -> {
            val minX = minOf(markup.start.x, markup.end.x)
            val minY = minOf(markup.start.y, markup.end.y)
            val maxX = maxOf(markup.start.x, markup.end.x)
            val maxY = maxOf(markup.start.y, markup.end.y)
            drawOval(
                color = markup.color.copy(alpha = markup.alpha),
                topLeft = Offset(minX, minY),
                size = Size(maxX - minX, maxY - minY),
                style = if (markup.filled) Fill else Stroke(width = 4f)
            )
        }
        is PdfMarkup.LineMarkup -> {
            if (markup.arrowHead) {
                drawArrow(
                    color = markup.color.copy(alpha = markup.alpha),
                    start = markup.start,
                    end = markup.end,
                    strokeWidth = markup.width
                )
            } else {
                drawLine(
                    color = markup.color.copy(alpha = markup.alpha),
                    start = markup.start,
                    end = markup.end,
                    strokeWidth = markup.width,
                    cap = StrokeCap.Round
                )
            }
        }
        else -> {}
    }
}

private fun DrawScope.drawArrow(
    color: Color,
    start: Offset,
    end: Offset,
    strokeWidth: Float
) {
    drawLine(
        color = color,
        start = start,
        end = end,
        strokeWidth = strokeWidth,
        cap = StrokeCap.Round
    )

    val angle = atan2((end.y - start.y).toDouble(), (end.x - start.x).toDouble())
    val arrowLen = (strokeWidth * 3.5f).coerceAtLeast(16f)
    val arrowAngle = Math.PI / 6.0

    val p1 = Offset(
        (end.x - arrowLen * cos(angle - arrowAngle)).toFloat(),
        (end.y - arrowLen * sin(angle - arrowAngle)).toFloat()
    )
    val p2 = Offset(
        (end.x - arrowLen * cos(angle + arrowAngle)).toFloat(),
        (end.y - arrowLen * sin(angle + arrowAngle)).toFloat()
    )

    val arrowPath = Path().apply {
        moveTo(end.x, end.y)
        lineTo(p1.x, p1.y)
        lineTo(p2.x, p2.y)
        close()
    }
    drawPath(path = arrowPath, color = color, style = Fill)
}
