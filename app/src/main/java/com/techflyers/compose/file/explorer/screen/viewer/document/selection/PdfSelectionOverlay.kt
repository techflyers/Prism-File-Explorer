package com.techflyers.compose.file.explorer.screen.viewer.document.selection

import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.roundToInt

class SelectionAutoScroller internal constructor(
    private val scope: CoroutineScope,
    private val listState: LazyListState,
    private val state: PdfTextSelectionState
) {
    var viewport: Size = Size.Zero
    var edgePx: Float = 0f
    var maxSpeedPxPerSec: Float = 0f
    var panBy: (Float) -> Float = { 0f }

    private var job: Job? = null
    private var finger = Offset.Unspecified
    private var onTick: (Offset) -> Unit = {}

    private fun velocity(p: Offset): Offset {
        if (!p.isSpecified || viewport == Size.Zero || edgePx <= 0f) return Offset.Zero
        fun axis(pos: Float, extent: Float): Float {
            val d = when {
                pos < edgePx -> -(edgePx - pos) / edgePx
                pos > extent - edgePx -> (pos - (extent - edgePx)) / edgePx
                else -> 0f
            }.coerceIn(-1f, 1f)
            return d * kotlin.math.abs(d) * maxSpeedPxPerSec
        }
        val vy = axis(p.y, viewport.height)
        val vx = if (state.transform.scale > 1.01f) axis(p.x, viewport.width) else 0f
        return Offset(vx, vy)
    }

    fun update(p: Offset, tick: (Offset) -> Unit) {
        finger = p
        onTick = tick
        if (velocity(p) == Offset.Zero) { stop(); return }
        if (job?.isActive == true) return
        job = scope.launch {
            var last = 0L
            while (isActive) {
                val now = withFrameNanos { it }
                val dt = if (last == 0L) 0.016f else ((now - last) / 1e9f).coerceIn(0f, 0.05f)
                last = now
                val v = velocity(finger)
                if (v == Offset.Zero) break
                val s = state.transform.scale.coerceAtLeast(0.01f)
                if (v.y != 0f) listState.scrollBy(v.y * dt / s)
                if (v.x != 0f) panBy(-v.x * dt)
                onTick(finger)
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        finger = Offset.Unspecified
    }
}

@Composable
fun rememberSelectionAutoScroller(listState: LazyListState, state: PdfTextSelectionState): SelectionAutoScroller {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    return remember(listState, state) { SelectionAutoScroller(scope, listState, state) }.also {
        it.edgePx = with(density) { 56.dp.toPx() }
        it.maxSpeedPxPerSec = with(density) { 1400.dp.toPx() }
    }
}

fun Modifier.pdfTextSelectionGestures(
    state: PdfTextSelectionState,
    autoScroller: SelectionAutoScroller,
    haptics: HapticFeedback,
    enabled: () -> Boolean,
    onSelectionStarted: () -> Unit
): Modifier = pointerInput(state) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        if (!enabled()) return@awaitEachGesture
        val slop = viewConfiguration.touchSlop
        val cancelled = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
            while (true) {
                val ev = awaitPointerEvent()
                if (ev.changes.count { it.pressed } != 1) return@withTimeoutOrNull false
                val ch = ev.changes.firstOrNull { it.id == down.id } ?: return@withTimeoutOrNull false
                if (!ch.pressed || ch.isConsumed) return@withTimeoutOrNull false
                if ((ch.position - down.position).getDistance() > slop) return@withTimeoutOrNull false
            }
            @Suppress("UNREACHABLE_CODE")
            false
        }
        if (cancelled != null) return@awaitEachGesture
        if (!state.selectWordAt(down.position, slop * 1.5f)) return@awaitEachGesture

        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        state.gestureActive = true
        onSelectionStarted()
        try {
            while (true) {
                val ev = awaitPointerEvent(PointerEventPass.Initial)
                ev.changes.forEach { it.consume() }
                val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                if (!ch.pressed) break
                if (ev.changes.count { it.pressed } > 1) break
                val p = ch.position
                fun apply(at: Offset) {
                    if (state.extendWordSelection(at)) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    val focus = state.focus ?: return
                    val caret = state.caretOnScreen(focus, focus < (state.anchor ?: focus))
                    state.magnifierCenter = Offset(at.x, caret?.lineCenterY ?: at.y)
                }
                apply(p)
                autoScroller.update(p) { apply(it) }
            }
        } finally {
            autoScroller.stop()
            state.endGesture()
        }
    }
}

@Composable
fun PdfSelectionHandles(
    state: PdfTextSelectionState,
    listState: LazyListState,
    autoScroller: SelectionAutoScroller,
    modifier: Modifier = Modifier
) {
    val colors = LocalTextSelectionColors.current
    val context = LocalContext.current
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val handleColor = colors.handleColor

    val drawables = remember(context, handleColor) {
        val ta = context.obtainStyledAttributes(
            intArrayOf(android.R.attr.textSelectHandleLeft, android.R.attr.textSelectHandleRight)
        )
        val l = runCatching { ta.getDrawable(0) }.getOrNull()?.mutate()?.apply { setTint(handleColor.toArgb()) }
        val r = runCatching { ta.getDrawable(1) }.getOrNull()?.mutate()?.apply { setTint(handleColor.toArgb()) }
        ta.recycle()
        l to r
    }
    val fallbackPx = with(density) { 22.dp.toPx() }
    fun sizeOf(d: Drawable?): Size =
        if (d != null && d.intrinsicWidth > 0 && d.intrinsicHeight > 0) Size(d.intrinsicWidth.toFloat(), d.intrinsicHeight.toFloat())
        else Size(fallbackPx, fallbackPx)
    val leftSize = sizeOf(drawables.first)
    val rightSize = sizeOf(drawables.second)

    fun hotspotX(h: SelectionHandle, sz: Size): Float {
        val real = (if (h == SelectionHandle.Start) drawables.first else drawables.second) != null
        return when {
            h == SelectionHandle.Start -> if (real) sz.width * 3f / 4f else sz.width
            else -> if (real) sz.width / 4f else 0f
        }
    }

    val minTouchPx = with(density) { 44.dp.toPx() }
    val hasSelection by remember(state) { derivedStateOf { state.hasSelection } }
    val overlayCoords = remember { arrayOfNulls<LayoutCoordinates>(1) }

    Box(
        modifier
            .fillMaxSize()
            .onGloballyPositioned {
                overlayCoords[0] = it
                autoScroller.viewport = Size(it.size.width.toFloat(), it.size.height.toFloat())
            }
    ) {
        if (!hasSelection) return@Box

        Canvas(Modifier.fillMaxSize()) {
            listState.firstVisibleItemIndex
            listState.firstVisibleItemScrollOffset
            listState.layoutInfo
            state.transform
            for (h in SelectionHandle.entries) {
                val caret = state.handleCaret(h) ?: continue
                val d = if (h == SelectionHandle.Start) drawables.first else drawables.second
                val sz = if (h == SelectionHandle.Start) leftSize else rightSize
                val left = caret.x - hotspotX(h, sz)
                val top = caret.lineBottom
                translate(left, top) {
                    if (d != null) drawIntoCanvas { c ->
                        d.setBounds(0, 0, sz.width.roundToInt(), sz.height.roundToInt())
                        d.draw(c.nativeCanvas)
                    } else drawFallbackHandle(h == SelectionHandle.Start, sz, handleColor)
                }
            }
        }

        for (h in SelectionHandle.entries) {
            val sz = if (h == SelectionHandle.Start) leftSize else rightSize
            val touchW = max(sz.width, minTouchPx)
            val touchH = max(sz.height, minTouchPx)
            val handleCoords = remember { arrayOfNulls<LayoutCoordinates>(1) }
            val latestHotspot by rememberUpdatedState(hotspotX(h, sz))
            Box(
                Modifier
                    .offset {
                        listState.firstVisibleItemScrollOffset
                        state.transform
                        val caret = state.handleCaret(h)
                        if (caret == null) IntOffset(-100_000, -100_000)
                        else IntOffset(
                            (caret.x - latestHotspot - (touchW - sz.width) / 2f).roundToInt(),
                            (caret.lineBottom - minTouchPx * 0.15f).roundToInt()
                        )
                    }
                    .size(with(density) { touchW.toDp() }, with(density) { touchH.toDp() })
                    .onGloballyPositioned { handleCoords[0] = it }
                    .pointerInput(h) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            down.consume()
                            val overlay = overlayCoords[0] ?: return@awaitEachGesture
                            val me = handleCoords[0] ?: return@awaitEachGesture
                            fun abs(local: Offset): Offset =
                                if (overlay.isAttached && me.isAttached) overlay.localPositionOf(me, local) else local
                            val caret = state.handleCaret(h) ?: return@awaitEachGesture
                            val grab = Offset(caret.x, caret.lineBottom) - abs(down.position)
                            var lineH = caret.lineHeight
                            state.beginHandleDrag(h)
                            fun apply(fingerAbs: Offset) {
                                val hs = fingerAbs + grab
                                if (state.dragFocusTo(Offset(hs.x, hs.y - lineH / 2f))) {
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                                val f = state.focus ?: return
                                val fc = state.caretOnScreen(f, f < (state.anchor ?: f))
                                if (fc != null) lineH = fc.lineHeight
                                state.magnifierCenter = Offset(hs.x, fc?.lineCenterY ?: (hs.y - lineH / 2f))
                            }
                            try {
                                while (true) {
                                    val ev = awaitPointerEvent()
                                    val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                                    if (!ch.pressed) { ch.consume(); break }
                                    if (ev.changes.count { it.pressed } > 1) break
                                    ch.consume()
                                    val p = abs(ch.position)
                                    apply(p)
                                    autoScroller.update(p) { apply(it) }
                                }
                            } finally {
                                autoScroller.stop()
                                state.endGesture()
                            }
                        }
                    }
            )
        }
    }
}

private fun DrawScope.drawFallbackHandle(isStart: Boolean, sz: Size, color: Color) {
    val r = sz.width / 2f
    val path = Path().apply {
        addOval(Rect(0f, 0f, sz.width, sz.width))
        if (isStart) addRect(Rect(r, 0f, sz.width, r))
        else addRect(Rect(0f, 0f, r, r))
    }
    drawPath(path, color)
}
