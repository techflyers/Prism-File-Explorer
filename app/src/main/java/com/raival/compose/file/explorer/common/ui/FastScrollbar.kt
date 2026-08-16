package com.raival.compose.file.explorer.common.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitTouchSlopOrCancellation
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.ScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Draws a Material-style fast-scroll thumb that:
 *  - is hidden when content fits in the viewport
 *  - only intercepts pointer events on the thumb itself, after a vertical drag
 *    (so edge back-gestures and overlay windows keep working)
 *  - fades out shortly after scrolling stops
 */
@Composable
fun Modifier.fastScrollbar(state: LazyGridState): Modifier {
    val metrics by remember(state) {
        derivedStateOf {
            val info = state.layoutInfo
            val total = info.totalItemsCount
            val visible = max(info.visibleItemsInfo.size, 1)
            val maxIdx = (total - visible).coerceAtLeast(0)
            val canScroll = total > 0 && maxIdx > 0 &&
                (info.totalItemsCount > info.visibleItemsInfo.size ||
                    (info.visibleItemsInfo.lastOrNull()?.let {
                        it.offset.y + it.size.height > info.viewportEndOffset
                    } ?: false) ||
                    state.canScrollForward || state.canScrollBackward)
            val fraction = if (maxIdx == 0) 0f else state.firstVisibleItemIndex.toFloat() / maxIdx
            val sizeFraction = if (total == 0) 1f
            else (visible.toFloat() / total.toFloat()).coerceIn(0.08f, 1f)
            ScrollMetrics(canScroll, fraction.coerceIn(0f, 1f), sizeFraction, maxIdx)
        }
    }
    return fastScrollbarImpl(
        metrics = metrics,
        onSeekFraction = { fraction, maxIdx ->
            state.scrollToItem((fraction * maxIdx).roundToInt().coerceIn(0, maxIdx))
        },
        scrollTick = state.firstVisibleItemIndex to state.firstVisibleItemScrollOffset
    )
}

@Composable
fun Modifier.fastScrollbar(state: LazyListState): Modifier {
    val metrics by remember(state) {
        derivedStateOf {
            val info = state.layoutInfo
            val total = info.totalItemsCount
            val visible = max(info.visibleItemsInfo.size, 1)
            val maxIdx = (total - visible).coerceAtLeast(0)
            val canScroll = total > 0 && (state.canScrollForward || state.canScrollBackward)
            val fraction = if (maxIdx == 0) 0f else state.firstVisibleItemIndex.toFloat() / maxIdx
            val sizeFraction = if (total == 0) 1f
            else (visible.toFloat() / total.toFloat()).coerceIn(0.08f, 1f)
            ScrollMetrics(canScroll, fraction.coerceIn(0f, 1f), sizeFraction, maxIdx)
        }
    }
    return fastScrollbarImpl(
        metrics = metrics,
        onSeekFraction = { fraction, maxIdx ->
            state.scrollToItem((fraction * maxIdx).roundToInt().coerceIn(0, maxIdx))
        },
        scrollTick = state.firstVisibleItemIndex to state.firstVisibleItemScrollOffset
    )
}

@Composable
fun Modifier.fastScrollbar(state: ScrollState): Modifier {
    val metrics by remember(state) {
        derivedStateOf {
            val canScroll = state.maxValue > 0
            val fraction = if (state.maxValue <= 0) 0f
            else state.value.toFloat() / state.maxValue.toFloat()
            val viewport = 1f
            val content = viewport + (state.maxValue / 1000f).coerceAtLeast(0f)
            val sizeFraction = if (!canScroll) 1f
            else (viewport / (viewport + state.maxValue.toFloat().coerceAtLeast(1f) / 400f))
                .coerceIn(0.08f, 1f)
            ScrollMetrics(canScroll, fraction.coerceIn(0f, 1f), sizeFraction, state.maxValue)
        }
    }
    return fastScrollbarImpl(
        metrics = metrics,
        onSeekFraction = { fraction, maxValue ->
            state.scrollTo((fraction * maxValue).roundToInt().coerceIn(0, maxValue))
        },
        scrollTick = state.value
    )
}

private data class ScrollMetrics(
    val canScroll: Boolean,
    val thumbFraction: Float,
    val thumbSizeFraction: Float,
    val maxIndex: Int
)

@Composable
private fun Modifier.fastScrollbarImpl(
    metrics: ScrollMetrics,
    onSeekFraction: suspend (Float, Int) -> Unit,
    scrollTick: Any
): Modifier {
    val coroutineScope = rememberCoroutineScope()
    var dragging by remember { mutableStateOf(false) }
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(scrollTick, dragging, metrics.canScroll) {
        if (!metrics.canScroll) {
            visible = false
            return@LaunchedEffect
        }
        visible = true
        if (!dragging) {
            kotlinx.coroutines.delay(1400)
            visible = false
        }
    }

    val scrollbarAlpha by animateFloatAsState(
        targetValue = if (visible && metrics.canScroll) 1f else 0f,
        animationSpec = tween(180),
        label = "scrollbarAlpha"
    )
    val thumbWidthDp by animateDpAsState(
        targetValue = if (dragging) 8.dp else 5.dp,
        animationSpec = tween(120),
        label = "thumbWidth"
    )

    var trackHeightPx by remember { mutableFloatStateOf(0f) }
    val trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
    val thumbColor = MaterialTheme.colorScheme.primary

    val minThumbDp = 36.dp
    val hitWidthDp = 20.dp
    val edgeInsetDp = 3.dp

    return this
        .onSizeChanged { trackHeightPx = it.height.toFloat() }
        .drawWithContent {
            drawContent()
            if (!metrics.canScroll || trackHeightPx <= 0f || scrollbarAlpha == 0f) return@drawWithContent

            val thumbW = thumbWidthDp.toPx()
            val inset = edgeInsetDp.toPx()
            val thumbH = (trackHeightPx * metrics.thumbSizeFraction).coerceAtLeast(minThumbDp.toPx())
            val usable = (trackHeightPx - thumbH).coerceAtLeast(0f)
            val thumbTop = metrics.thumbFraction * usable
            val thumbX = size.width - thumbW - inset

            drawRoundRect(
                color = trackColor.copy(alpha = trackColor.alpha * scrollbarAlpha),
                topLeft = Offset(thumbX + thumbW / 2f - 1.dp.toPx(), 0f),
                size = Size(2.dp.toPx(), size.height),
                cornerRadius = CornerRadius(1.dp.toPx())
            )
            drawRoundRect(
                color = thumbColor.copy(alpha = 0.92f * scrollbarAlpha),
                topLeft = Offset(thumbX, thumbTop),
                size = Size(thumbW, thumbH),
                cornerRadius = CornerRadius(thumbW)
            )
        }
        .pointerInput(metrics.canScroll, metrics.maxIndex) {
            if (!metrics.canScroll) return@pointerInput
            val hitWidthPx = hitWidthDp.toPx()
            val minThumbPx = minThumbDp.toPx()
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Main)
                val inHitZone = down.position.x >= size.width - hitWidthPx
                if (!inHitZone) return@awaitEachGesture

                val slop = awaitTouchSlopOrCancellation(down.id) { change, over ->
                    if (abs(over.y) > abs(over.x)) {
                        change.consume()
                    }
                } ?: return@awaitEachGesture

                dragging = true
                visible = true

                fun seekToY(y: Float) {
                    val thumbH = (trackHeightPx * metrics.thumbSizeFraction).coerceAtLeast(minThumbPx)
                    val usable = (trackHeightPx - thumbH).coerceAtLeast(1f)
                    val newFraction = (y - thumbH / 2f).coerceIn(0f, usable) / usable
                    coroutineScope.launch { onSeekFraction(newFraction, metrics.maxIndex) }
                }

                seekToY(slop.position.y)
                drag(slop.id) { change ->
                    change.consume()
                    seekToY(change.position.y)
                }
                dragging = false
            }
        }
}

@Composable
fun LazyGridFastScrollbar(state: LazyGridState, modifier: Modifier = Modifier) = Unit
