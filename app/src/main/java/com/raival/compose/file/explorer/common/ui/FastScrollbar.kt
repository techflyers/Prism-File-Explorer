package com.raival.compose.file.explorer.common.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Draws a draggable fast-scroll thumb at the right edge of a LazyVerticalGrid.
 * Apply as `Modifier.fastScrollbar(state)` on the LazyVerticalGrid.
 *
 * The alpha fades only the drawn scrollbar shapes — NOT the grid content.
 */
@Composable
fun Modifier.fastScrollbar(state: LazyGridState): Modifier {
    val coroutineScope = rememberCoroutineScope()

    // Position fraction [0..1] of the thumb along the track
    val thumbFraction by remember(state) {
        derivedStateOf {
            val info = state.layoutInfo
            val total = info.totalItemsCount
            if (total == 0) return@derivedStateOf 0f
            val visible = info.visibleItemsInfo.size
            val maxIdx = (total - visible).coerceAtLeast(0)
            if (maxIdx == 0) return@derivedStateOf 0f
            state.firstVisibleItemIndex.toFloat() / maxIdx.toFloat()
        }
    }

    // Thumb height fraction [0.05..1]
    val thumbSizeFraction by remember(state) {
        derivedStateOf {
            val info = state.layoutInfo
            val total = info.totalItemsCount
            if (total == 0) return@derivedStateOf 1f
            (info.visibleItemsInfo.size.toFloat() / total.toFloat()).coerceIn(0.05f, 1f)
        }
    }

    // Auto-hide after 1.5 s of no scroll activity
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(state.firstVisibleItemIndex, state.firstVisibleItemScrollOffset) {
        visible = true
        delay(1500)
        visible = false
    }

    val scrollbarAlpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(300),
        label = "scrollbarAlpha"
    )

    var trackHeightPx by remember { mutableFloatStateOf(0f) }

    val trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)
    val thumbColor = MaterialTheme.colorScheme.primary

    val thumbVisualWidthDp = 4.dp    // visual width of the pill
    val touchZoneWidthDp = 28.dp     // wider touch target

    return this
        .onSizeChanged { trackHeightPx = it.height.toFloat() }
        // Draw scrollbar OVER content — alpha applied only to the shapes, not the content
        .drawWithContent {
            drawContent() // always draw grid content at full opacity

            if (trackHeightPx <= 0f || scrollbarAlpha == 0f) return@drawWithContent

            val thumbW = thumbVisualWidthDp.toPx()
            val thumbH = (trackHeightPx * thumbSizeFraction).coerceAtLeast(40.dp.toPx())
            val usable = (trackHeightPx - thumbH).coerceAtLeast(0f)
            val thumbTop = thumbFraction * usable
            val thumbX = size.width - thumbW

            // Track background
            drawRoundRect(
                color = trackColor.copy(alpha = trackColor.alpha * scrollbarAlpha),
                topLeft = Offset(thumbX, 0f),
                size = Size(thumbW, size.height),
                cornerRadius = CornerRadius(thumbW / 2)
            )
            // Thumb pill
            drawRoundRect(
                color = thumbColor.copy(alpha = 0.80f * scrollbarAlpha),
                topLeft = Offset(thumbX, thumbTop),
                size = Size(thumbW, thumbH),
                cornerRadius = CornerRadius(thumbW / 2)
            )
        }
        // Handle drag in the right-edge touch zone using low-level pointer API
        // so the scrollbar intercepts touches before the LazyVerticalGrid scroll
        .pointerInput(state) {
            val touchZoneWidthPx = touchZoneWidthDp.toPx()
            awaitPointerEventScope {
                while (true) {
                    // Wait for any down event
                    val down = awaitPointerEvent(PointerEventPass.Initial)
                    val downChange = down.changes.firstOrNull() ?: continue

                    // Only intercept if touch is in the right-edge zone
                    val touchX = downChange.position.x
                    if (touchX < size.width - touchZoneWidthPx) continue

                    // Make scrollbar immediately visible on touch
                    visible = true
                    downChange.consume()

                    // Track drag while finger is down
                    var prevY = downChange.position.y
                    var pointerUp = false
                    while (!pointerUp) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        event.changes.forEach { change: PointerInputChange ->
                            if (change.pressed) {
                                val dy = change.position.y - prevY
                                prevY = change.position.y
                                change.consume()

                                if (abs(dy) > 0f && trackHeightPx > 0f) {
                                    val thumbH = (trackHeightPx * thumbSizeFraction)
                                        .coerceAtLeast(40.dp.toPx())
                                    val usable = (trackHeightPx - thumbH).coerceAtLeast(1f)
                                    // Current thumb top → add delta → compute new fraction
                                    val currentTop = thumbFraction * usable
                                    val newTop = (currentTop + dy).coerceIn(0f, usable)
                                    val newFraction = newTop / usable

                                    val info = state.layoutInfo
                                    val total = info.totalItemsCount
                                    val visibleCount = max(info.visibleItemsInfo.size, 1)
                                    val maxIdx = (total - visibleCount).coerceAtLeast(0)
                                    val target = (newFraction * maxIdx).roundToInt()
                                    coroutineScope.launch { state.scrollToItem(target) }
                                }
                            } else {
                                pointerUp = true
                            }
                        }
                    }
                }
            }
        }
}

// Keep stub for backward compat
@Composable
fun LazyGridFastScrollbar(state: LazyGridState, modifier: Modifier = Modifier) = Unit
