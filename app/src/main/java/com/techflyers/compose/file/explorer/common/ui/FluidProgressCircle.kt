package com.techflyers.compose.file.explorer.common.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

/**
 * High-performance, isolated percentage-based fluid circular progress indicator.
 * All animation state reads are scoped inside [Isolate] and drawn directly on [Canvas]
 * so parent lists, tabs, and navigation gestures never recompose or drop frames.
 */
@Composable
fun FluidPercentProgressCircle(
    progress: Float,
    modifier: Modifier = Modifier,
    size: Dp = 56.dp,
    strokeWidth: Dp = 4.5.dp,
    fontSize: TextUnit = 12.sp,
    showPercentText: Boolean = true,
    label: String? = null,
    primaryColor: Color = MaterialTheme.colorScheme.primary,
    secondaryColor: Color = MaterialTheme.colorScheme.tertiary,
    trackColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest
) {
    Isolate {
        val target = progress.coerceIn(0.02f, 1f)
        val animatedProgress by animateFloatAsState(
            targetValue = target,
            animationSpec = tween(durationMillis = 240, easing = FastOutSlowInEasing),
            label = "fluidCircleProgress"
        )
        val infiniteTransition = rememberInfiniteTransition(label = "fluidCircleSpin")
        val sweepRotation by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1600, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "fluidSweepRotation"
        )

        val percentInt = (animatedProgress * 100f).roundToInt().coerceIn(1, 100)

        Column(
            modifier = modifier,
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier.size(size),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.size(size)) {
                    val strokePx = strokeWidth.toPx()
                    val diameter = this.size.minDimension - strokePx
                    val topLeft = Offset(strokePx / 2f, strokePx / 2f)
                    val arcSize = Size(diameter, diameter)

                    // Background track circle
                    drawArc(
                        color = trackColor,
                        startAngle = 0f,
                        sweepAngle = 360f,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = strokePx, cap = StrokeCap.Round)
                    )

                    // Subtle fluid rotating glow halo for continuous visual responsiveness
                    rotate(degrees = sweepRotation) {
                        drawArc(
                            color = primaryColor.copy(alpha = 0.22f),
                            startAngle = -90f,
                            sweepAngle = 70f,
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcSize,
                            style = Stroke(width = strokePx, cap = StrokeCap.Round)
                        )
                    }

                    // Primary percentage progress arc
                    val sweepAngle = (animatedProgress * 360f).coerceIn(8f, 360f)
                    drawArc(
                        brush = Brush.sweepGradient(
                            colors = listOf(primaryColor, secondaryColor, primaryColor)
                        ),
                        startAngle = -90f,
                        sweepAngle = sweepAngle,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = strokePx, cap = StrokeCap.Round)
                    )
                }

                if (showPercentText) {
                    Text(
                        text = "$percentInt%",
                        fontSize = fontSize,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            if (!label.isNullOrEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Compact, non-blocking percentage progress pill for headers and floating overlays
 * while categories or recent files stream/index in the background.
 */
@Composable
fun FluidPercentProgressPill(
    progress: Float,
    modifier: Modifier = Modifier,
    label: String? = null
) {
    Isolate {
        val target = progress.coerceIn(0.02f, 1f)
        val animatedProgress by animateFloatAsState(
            targetValue = target,
            animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
            label = "fluidPillProgress"
        )
        val percentInt = (animatedProgress * 100f).roundToInt().coerceIn(1, 100)

        Row(
            modifier = modifier
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.92f))
                .border(
                    width = 0.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(50)
                )
                .padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FluidPercentProgressCircle(
                progress = animatedProgress,
                size = 20.dp,
                strokeWidth = 2.5.dp,
                showPercentText = false
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = if (!label.isNullOrEmpty()) "$label $percentInt%" else "$percentInt%",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}
