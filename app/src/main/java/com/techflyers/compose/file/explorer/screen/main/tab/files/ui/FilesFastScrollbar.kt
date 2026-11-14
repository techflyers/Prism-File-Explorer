package com.techflyers.compose.file.explorer.screen.main.tab.files.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.techflyers.compose.file.explorer.common.toFormattedSize
import com.techflyers.compose.file.explorer.screen.main.tab.files.holder.ContentHolder
import com.techflyers.compose.file.explorer.screen.main.tab.files.misc.SortingMethod
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun FilesFastScrollbar(
    items: List<ContentHolder>,
    sortMethod: Int,
    listState: LazyListState? = null,
    gridState: LazyGridState? = null,
    modifier: Modifier = Modifier
) {
    val totalCount = items.size
    if (totalCount <= 8) return

    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val thumbHeightDp = 48.dp
    val thumbHeightPx = with(density) { thumbHeightDp.toPx() }

    var trackHeightPx by remember { mutableIntStateOf(0) }
    var isDragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }
    var isVisible by remember { mutableStateOf(false) }

    val isScrollInProgress by remember(listState, gridState) {
        derivedStateOf {
            listState?.isScrollInProgress == true || gridState?.isScrollInProgress == true
        }
    }

    val firstVisibleIndex by remember(listState, gridState) {
        derivedStateOf {
            listState?.firstVisibleItemIndex ?: gridState?.firstVisibleItemIndex ?: 0
        }
    }

    val visibleCount by remember(listState, gridState) {
        derivedStateOf {
            listState?.layoutInfo?.visibleItemsInfo?.size
                ?: gridState?.layoutInfo?.visibleItemsInfo?.size
                ?: 1
        }
    }

    LaunchedEffect(isScrollInProgress, isDragging) {
        if (isScrollInProgress || isDragging) {
            isVisible = true
        } else {
            delay(1500L)
            if (!isDragging && !isScrollInProgress) {
                isVisible = false
            }
        }
    }

    val maxScrollIndex = (totalCount - visibleCount).coerceAtLeast(1)
    val currentFraction = if (isDragging) {
        dragFraction
    } else {
        (firstVisibleIndex.toFloat() / maxScrollIndex.toFloat()).coerceIn(0f, 1f)
    }

    val availableTrackPx = (trackHeightPx - thumbHeightPx).coerceAtLeast(1f)
    val thumbOffsetY = (currentFraction * availableTrackPx).roundToInt()

    val activeIndex = (currentFraction * (totalCount - 1)).roundToInt().coerceIn(0, totalCount - 1)
    val activeItem = items.getOrNull(activeIndex)
    val pillLabel = remember(activeItem, sortMethod) {
        activeItem?.let { formatSortPillLabel(it, sortMethod) }.orEmpty()
    }

    AnimatedVisibility(
        visible = isVisible || isDragging,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier.fillMaxHeight()
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .onSizeChanged { trackHeightPx = it.height }
        ) {
            // Sort-order aware indicator pill while holding & dragging
            AnimatedVisibility(
                visible = isDragging && pillLabel.isNotEmpty(),
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = 44.dp)
                    .offset { IntOffset(0, thumbOffsetY) }
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    tonalElevation = 6.dp,
                    shadowElevation = 6.dp
                ) {
                    Text(
                        text = pillLabel,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        modifier = Modifier
                            .widthIn(min = 48.dp)
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }

            // Interactive touch target & thumb
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .fillMaxHeight()
                    .width(36.dp)
                    .pointerInput(totalCount, trackHeightPx, listState, gridState) {
                        if (trackHeightPx <= 0) return@pointerInput
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val currentThumbTop = currentFraction * availableTrackPx
                            val touchY = down.position.y
                            val slop = 24.dp.toPx()
                            val hitThumbOrTrack = touchY in (currentThumbTop - slop)..(currentThumbTop + thumbHeightPx + slop) || isVisible
                            if (!hitThumbOrTrack) return@awaitEachGesture

                            down.consume()
                            isDragging = true

                            fun updateToY(y: Float) {
                                val fraction = ((y - thumbHeightPx / 2f) / availableTrackPx).coerceIn(0f, 1f)
                                dragFraction = fraction
                                val targetIdx = (fraction * (totalCount - 1)).roundToInt().coerceIn(0, totalCount - 1)
                                scope.launch {
                                    listState?.scrollToItem(targetIdx)
                                    gridState?.scrollToItem(targetIdx)
                                }
                            }

                            updateToY(touchY)

                            var pointerId = down.id
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == pointerId }
                                    ?: event.changes.firstOrNull()
                                if (change == null || !change.pressed) {
                                    break
                                }
                                pointerId = change.id
                                change.consume()
                                updateToY(change.position.y)
                            }
                            isDragging = false
                        }
                    }
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(end = 4.dp)
                        .offset { IntOffset(0, thumbOffsetY) }
                        .width(if (isDragging) 10.dp else 6.dp)
                        .height(thumbHeightDp)
                        .clip(RoundedCornerShape(50))
                        .background(
                            if (isDragging) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                        )
                )
            }
        }
    }
}

private fun formatSortPillLabel(
    item: ContentHolder,
    sortMethod: Int
): String {
    return when (sortMethod) {
        SortingMethod.SORT_BY_NAME -> {
            val firstChar = item.displayName.trim().firstOrNull()?.uppercaseChar() ?: '#'
            if (firstChar.isLetter()) firstChar.toString() else "#"
        }
        SortingMethod.SORT_BY_DATE -> {
            val timestamp = item.lastModified
            if (timestamp <= 0L) return "—"
            val nowYear = Calendar.getInstance().get(Calendar.YEAR)
            val itemCal = Calendar.getInstance().apply { timeInMillis = timestamp }
            val pattern = if (itemCal.get(Calendar.YEAR) == nowYear) "MMM dd" else "MMM dd, yyyy"
            SimpleDateFormat(pattern, Locale.getDefault()).format(Date(timestamp))
        }
        SortingMethod.SORT_BY_SIZE -> {
            if (item.isFolder) "Folder" else item.size.toFormattedSize().ifEmpty { "0 B" }
        }
        SortingMethod.SORT_BY_TYPE -> {
            if (item.isFolder) "FOLDER"
            else item.extension.uppercase(Locale.getDefault()).ifEmpty { "FILE" }
        }
        else -> {
            val firstChar = item.displayName.trim().firstOrNull()?.uppercaseChar() ?: '#'
            if (firstChar.isLetter()) firstChar.toString() else "#"
        }
    }
}
