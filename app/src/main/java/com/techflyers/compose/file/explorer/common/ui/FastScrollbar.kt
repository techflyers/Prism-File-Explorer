package com.techflyers.compose.file.explorer.common.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Sora Editor styled scrollbar:
 *  - 8.dp width, min 56.dp thumb length
 *  - Slide & fade animations on hide/show (translating horizontally off-screen)
 *  - Non-obstructive touch handling: only intercepts when dragging the visible thumb on the Main pass
 *    (after child buttons have had a chance to handle taps/long-presses), preventing interference
 *    with right-aligned action buttons.
 */
@Composable
fun Modifier.scrollbar(state: LazyGridState): Modifier {
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
            else (visible.toFloat() / total.toFloat()).coerceIn(0.06f, 1f)
            ScrollMetrics(canScroll, fraction.coerceIn(0f, 1f), sizeFraction, maxIdx)
        }
    }
    return soraScrollbarImpl(
        metrics = metrics,
        onSeekFraction = { fraction, maxIdx ->
            state.scrollToItem((fraction * maxIdx).roundToInt().coerceIn(0, maxIdx))
        },
        scrollTick = state.firstVisibleItemIndex to state.firstVisibleItemScrollOffset
    )
}

@Composable
fun Modifier.fastScrollbar(state: LazyGridState): Modifier = scrollbar(state)

@Composable
fun Modifier.scrollbar(state: LazyListState): Modifier {
    val metrics by remember(state) {
        derivedStateOf {
            val info = state.layoutInfo
            val total = info.totalItemsCount
            val visible = max(info.visibleItemsInfo.size, 1)
            val maxIdx = (total - visible).coerceAtLeast(0)
            val canScroll = total > 0 && (state.canScrollForward || state.canScrollBackward)
            val fraction = if (maxIdx == 0) 0f else state.firstVisibleItemIndex.toFloat() / maxIdx
            val sizeFraction = if (total == 0) 1f
            else (visible.toFloat() / total.toFloat()).coerceIn(0.06f, 1f)
            ScrollMetrics(canScroll, fraction.coerceIn(0f, 1f), sizeFraction, maxIdx)
        }
    }
    return soraScrollbarImpl(
        metrics = metrics,
        onSeekFraction = { fraction, maxIdx ->
            state.scrollToItem((fraction * maxIdx).roundToInt().coerceIn(0, maxIdx))
        },
        scrollTick = state.firstVisibleItemIndex to state.firstVisibleItemScrollOffset
    )
}

@Composable
fun Modifier.fastScrollbar(state: LazyListState): Modifier = scrollbar(state)

@Composable
fun Modifier.scrollbar(state: ScrollState): Modifier {
    val metrics by remember(state) {
        derivedStateOf {
            val canScroll = state.maxValue > 0
            val fraction = if (state.maxValue <= 0) 0f
            else state.value.toFloat() / state.maxValue.toFloat()
            val viewport = 1f
            val sizeFraction = if (!canScroll) 1f
            else (viewport / (viewport + state.maxValue.toFloat().coerceAtLeast(1f) / 400f))
                .coerceIn(0.06f, 1f)
            ScrollMetrics(canScroll, fraction.coerceIn(0f, 1f), sizeFraction, state.maxValue)
        }
    }
    return soraScrollbarImpl(
        metrics = metrics,
        onSeekFraction = { fraction, maxValue ->
            state.scrollTo((fraction * maxValue).roundToInt().coerceIn(0, maxValue))
        },
        scrollTick = state.value
    )
}

@Composable
fun Modifier.fastScrollbar(state: ScrollState): Modifier = scrollbar(state)

private data class ScrollMetrics(
    val canScroll: Boolean,
    val thumbFraction: Float,
    val thumbSizeFraction: Float,
    val maxIndex: Int
)

@Composable
private fun Modifier.soraScrollbarImpl(
    metrics: ScrollMetrics,
    onSeekFraction: suspend (Float, Int) -> Unit,
    scrollTick: Any
): Modifier {
    val coroutineScope = rememberCoroutineScope()
    var isHolding by remember { mutableStateOf(false) }
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(scrollTick, isHolding, metrics.canScroll) {
        if (!metrics.canScroll) {
            visible = false
            return@LaunchedEffect
        }
        visible = true
        if (!isHolding) {
            delay(1200)
            visible = false
        }
    }

    val visibilityProgress by animateFloatAsState(
        targetValue = if ((visible || isHolding) && metrics.canScroll) 1f else 0f,
        animationSpec = tween(200),
        label = "soraScrollbarVisibility"
    )

    var trackHeightPx by remember { mutableFloatStateOf(0f) }

    val trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
    val thumbColorNormal = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    val thumbColorPressed = MaterialTheme.colorScheme.primary

    val scrollbarWidthDp = 8.dp
    val minThumbLengthDp = 56.dp
    val cornerRadiusDp = 2.dp
    val hitZoneWidthDp = 14.dp

    return this
        .onSizeChanged { trackHeightPx = it.height.toFloat() }
        .drawWithContent {
            drawContent()
            if (!metrics.canScroll || trackHeightPx <= 0f || visibilityProgress <= 0.001f) return@drawWithContent

            val scrollbarWidthPx = scrollbarWidthDp.toPx()
            val slideXPx = scrollbarWidthPx * (1f - visibilityProgress)
            val alpha = visibilityProgress

            val minThumbPx = minThumbLengthDp.toPx()
            val thumbH = (trackHeightPx * metrics.thumbSizeFraction).coerceAtLeast(minThumbPx)
            val usable = (trackHeightPx - thumbH).coerceAtLeast(0f)
            val thumbTop = metrics.thumbFraction * usable
            val thumbLeft = size.width - scrollbarWidthPx + slideXPx
            val cornerRadius = CornerRadius(cornerRadiusDp.toPx(), cornerRadiusDp.toPx())

            if (isHolding) {
                drawRoundRect(
                    color = trackColor.copy(alpha = trackColor.alpha * alpha),
                    topLeft = Offset(thumbLeft, 0f),
                    size = Size(scrollbarWidthPx, size.height),
                    cornerRadius = cornerRadius
                )
            }

            val currentThumbColor = if (isHolding) thumbColorPressed else thumbColorNormal
            drawRoundRect(
                color = currentThumbColor.copy(alpha = currentThumbColor.alpha * alpha),
                topLeft = Offset(thumbLeft, thumbTop),
                size = Size(scrollbarWidthPx, thumbH),
                cornerRadius = cornerRadius
            )
        }
        .pointerInput(metrics.canScroll, metrics.maxIndex) {
            if (!metrics.canScroll) return@pointerInput
            val hitWidthPx = hitZoneWidthDp.toPx()
            val minThumbPx = minThumbLengthDp.toPx()
            val dragThresholdPx = 6.dp.toPx()

            awaitPointerEventScope {
                while (true) {
                    // Use Main pass with requireUnconsumed = true so child IconButtons (e.g. Copy SHA-256, Open in new tab)
                    // are never blocked or hijacked by the scrollbar.
                    val down = awaitFirstDown(requireUnconsumed = true, pass = PointerEventPass.Main)
                    if (!visible && !isHolding) continue
                    val inHitZone = down.position.x >= size.width - hitWidthPx
                    if (!inHitZone) continue

                    val thumbH = (trackHeightPx * metrics.thumbSizeFraction).coerceAtLeast(minThumbPx)
                    val usable = (trackHeightPx - thumbH).coerceAtLeast(1f)
                    val thumbTop = metrics.thumbFraction * usable
                    val onThumb = down.position.y in (thumbTop - 16f)..(thumbTop + thumbH + 16f)
                    if (!onThumb) continue

                    var draggingThumb = false

                    fun seekToY(y: Float) {
                        val currentThumbH = (trackHeightPx * metrics.thumbSizeFraction).coerceAtLeast(minThumbPx)
                        val currentUsable = (trackHeightPx - currentThumbH).coerceAtLeast(1f)
                        val newFraction = ((y - currentThumbH / 2f) / currentUsable).coerceIn(0f, 1f)
                        coroutineScope.launch { onSeekFraction(newFraction, metrics.maxIndex) }
                    }

                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Main)
                        val anyPressed = event.changes.any { it.pressed }
                        if (!anyPressed) break

                        val change = event.changes.firstOrNull { it.pressed } ?: break
                        if (!draggingThumb && abs(change.position.y - down.position.y) >= dragThresholdPx) {
                            draggingThumb = true
                            isHolding = true
                            visible = true
                        }
                        if (draggingThumb) {
                            change.consume()
                            seekToY(change.position.y)
                        }
                    }

                    isHolding = false
                }
            }
        }
}

@Composable
fun LazyGridFastScrollbar(state: LazyGridState, modifier: Modifier = Modifier) = Unit
