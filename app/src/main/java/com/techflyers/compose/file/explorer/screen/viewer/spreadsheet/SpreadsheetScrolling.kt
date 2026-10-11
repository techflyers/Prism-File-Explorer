package com.techflyers.compose.file.explorer.screen.viewer.spreadsheet

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.techflyers.compose.file.explorer.R
import kotlinx.coroutines.flow.distinctUntilChanged
import java.io.File
import kotlin.math.abs
import kotlin.math.roundToInt

private val SheetScrubberTrackHeight = 208.dp

/**
 * A vertical jump rail for the row axis — drag to scroll to any row in one motion,
 * with a floating "Row X of Y" badge that tracks the finger.
 * Only shown for sheets with 60+ rows.
 */
@Composable
internal fun SheetRowScrubber(
    listState: LazyListState,
    rowCount: Int,
    accent: Color,
    modifier: Modifier = Modifier,
    labelFor: (Int) -> Int = { it + 1 },
    totalLabel: Int = rowCount
) {
    if (rowCount < 60) return

    val view = LocalView.current
    var isDragging by remember { mutableStateOf(false) }
    var dragRow by remember { mutableIntStateOf(0) }
    val lastSpan = (rowCount - 1).coerceAtLeast(1)

    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .distinctUntilChanged()
            .collect { idx -> if (!isDragging) dragRow = idx.coerceIn(0, lastSpan) }
    }

    LaunchedEffect(dragRow, isDragging) {
        if (isDragging) {
            runCatching { view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK) }
            runCatching { listState.scrollToItem(dragRow) }
        }
    }

    val fraction by animateFloatAsState(
        targetValue = (dragRow.toFloat() / lastSpan).coerceIn(0f, 1f),
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = Spring.DampingRatioNoBouncy),
        label = "sheetScrubFraction"
    )
    val trackWidth by animateDpAsState(if (isDragging) 8.dp else 4.dp, spring(stiffness = Spring.StiffnessMedium), label = "sheetTrackWidth")
    val thumbHeight by animateDpAsState(if (isDragging) 40.dp else 30.dp, spring(stiffness = Spring.StiffnessMedium), label = "sheetThumbHeight")

    Box(modifier) {
        Box(
            Modifier
                .align(Alignment.CenterEnd)
                .height(SheetScrubberTrackHeight)
                .width(28.dp)
                .pointerInput(rowCount) {
                    detectTapGestures { offset ->
                        val target = ((offset.y / size.height) * lastSpan).roundToInt().coerceIn(0, lastSpan)
                        dragRow = target
                        runCatching { view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK) }
                    }
                }
                .pointerInput(rowCount) {
                    detectDragGestures(
                        onDragStart = { start ->
                            isDragging = true
                            dragRow = ((start.y / size.height) * lastSpan).roundToInt().coerceIn(0, lastSpan)
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            dragRow = ((change.position.y / size.height) * lastSpan).roundToInt().coerceIn(0, lastSpan)
                        },
                        onDragEnd = { isDragging = false },
                        onDragCancel = { isDragging = false }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Box(
                Modifier
                    .width(trackWidth)
                    .height(SheetScrubberTrackHeight)
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)),
                contentAlignment = Alignment.TopCenter
            ) {
                Box(
                    Modifier
                        .padding(top = ((SheetScrubberTrackHeight - thumbHeight) * fraction).coerceAtLeast(0.dp))
                        .width(if (isDragging) 8.dp else 4.dp)
                        .height(thumbHeight)
                        .clip(RoundedCornerShape(50))
                        .background(if (isDragging) accent else accent.copy(0.65f))
                )
            }
        }

        AnimatedVisibility(
            visible = isDragging,
            enter = fadeIn(tween(140)) + scaleIn(initialScale = 0.9f, animationSpec = tween(160)),
            exit = fadeOut(tween(180)) + scaleOut(targetScale = 0.92f),
            modifier = Modifier.align(Alignment.CenterEnd)
        ) {
            val yOffset = (SheetScrubberTrackHeight * fraction - SheetScrubberTrackHeight / 2f).coerceIn(-90.dp, 90.dp)
            Surface(
                modifier = Modifier
                    .offset(x = (-34).dp, y = yOffset)
                    .defaultMinSize(minWidth = 32.dp, minHeight = 28.dp),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                tonalElevation = 6.dp,
                shadowElevation = 4.dp
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                    Text(
                        text = stringResource(R.string.sheet_row_of, labelFor(dragRow), totalLabel),
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold, fontSize = 11.sp),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

/**
 * A fling behaviour that stacks consecutive flicks in the same direction to boost velocity.
 */
@Composable
internal fun rememberStackingFlingBehavior(): FlingBehavior {
    val base = ScrollableDefaults.flingBehavior()
    val minVelocity = with(LocalDensity.current) { MinStreakVelocityDp.dp.toPx() }
    return remember(base, minVelocity) { StackingFlingBehavior(base, minVelocity) }
}

private const val MinStreakVelocityDp = 1200f
private const val StreakWindowMillis = 320L
private const val FlingBoostPerSwipe = 0.9f
private const val MaxFlingMultiplier = 4f

private class StackingFlingBehavior(
    private val base: FlingBehavior,
    private val minVelocity: Float
) : FlingBehavior {

    private var lastDirection = 0
    private var lastFlingAtMillis = 0L
    private var streak = 0

    override suspend fun ScrollScope.performFling(initialVelocity: Float): Float {
        val now = SystemClock.uptimeMillis()
        val direction = when {
            initialVelocity > 0f -> 1
            initialVelocity < 0f -> -1
            else -> 0
        }
        val isFastFlick = abs(initialVelocity) >= minVelocity
        val continuesBurst = direction != 0 &&
            direction == lastDirection &&
            now - lastFlingAtMillis <= StreakWindowMillis

        streak = if (isFastFlick && continuesBurst) streak + 1 else 0
        lastDirection = direction
        lastFlingAtMillis = now

        val multiplier = (1f + streak * FlingBoostPerSwipe).coerceAtMost(MaxFlingMultiplier)
        return with(base) { performFling(initialVelocity * multiplier) }
    }
}

internal fun shareFile(context: Context, uri: Uri) {
    val shareUri = if (uri.scheme == "file") {
        runCatching {
            FileProvider.getUriForFile(context, "${context.packageName}.provider", File(uri.path!!))
        }.getOrNull() ?: uri
    } else uri
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = context.contentResolver.getType(shareUri) ?: "application/octet-stream"
        putExtra(Intent.EXTRA_STREAM, shareUri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    runCatching { context.startActivity(Intent.createChooser(intent, null)) }
}
