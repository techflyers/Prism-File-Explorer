package com.techflyers.compose.file.explorer.screen.viewer.document.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.FormatUnderlined
import androidx.compose.material.icons.rounded.Highlight
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.StrikethroughS
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt

val SelectionHighlightColors: List<Color> = listOf(
    Color(0xFFFFD60A), // Yellow
    Color(0xFF34C759), // Green
    Color(0xFF30B0C7), // Teal
    Color(0xFF007AFF), // Blue
    Color(0xFFAF52DE), // Purple
    Color(0xFFFF375F), // Pink/Red
    Color(0xFFFF9500)  // Orange
)

@Composable
fun DocumentSelectionToolbar(
    visible: Boolean,
    anchorPosition: Offset? = null,
    onCopy: () -> Unit,
    onHighlight: (colorArgb: Long) -> Unit,
    onUnderline: () -> Unit,
    onStrike: () -> Unit,
    onAskAi: () -> Unit,
    onShare: () -> Unit,
    onSearch: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showColorPicker by remember { mutableStateOf(false) }
    val density = LocalDensity.current
    var toolbarWidthPx by remember { mutableFloatStateOf(0f) }
    var toolbarHeightPx by remember { mutableFloatStateOf(0f) }

    val inPlaceModifier = if (anchorPosition != null && anchorPosition != Offset.Zero) {
        val marginPx = with(density) { 16.dp.toPx() }
        val topLimitPx = with(density) { 90.dp.toPx() }
        val bottomGapPx = with(density) { 60.dp.toPx() }

        val targetY = if (anchorPosition.y - bottomGapPx > topLimitPx) {
            anchorPosition.y - bottomGapPx
        } else {
            anchorPosition.y + with(density) { 36.dp.toPx() }
        }

        Modifier.offset {
            val posX = (anchorPosition.x - toolbarWidthPx / 2f).coerceAtLeast(marginPx)
            IntOffset(posX.roundToInt(), targetY.roundToInt())
        }
    } else {
        Modifier
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + scaleIn(initialScale = 0.9f),
        exit = fadeOut() + scaleOut(targetScale = 0.9f),
        modifier = modifier.then(inPlaceModifier)
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.96f),
            tonalElevation = 8.dp,
            shadowElevation = 8.dp,
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .onGloballyPositioned { coords ->
                    toolbarWidthPx = coords.size.width.toFloat()
                    toolbarHeightPx = coords.size.height.toFloat()
                }
        ) {
            Row(
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (showColorPicker) {
                    // Color selection chips
                    SelectionHighlightColors.forEach { color ->
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(color)
                                .clickable {
                                    onHighlight(color.toArgb().toLong())
                                    showColorPicker = false
                                }
                        )
                    }
                    IconButton(
                        onClick = { showColorPicker = false },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Close colors",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    // Standard toolbar items
                    SelectionActionItem(
                        icon = Icons.Rounded.ContentCopy,
                        label = "Copy",
                        onClick = onCopy
                    )

                    SelectionActionItem(
                        icon = Icons.Rounded.Highlight,
                        label = "Highlight",
                        onClick = { showColorPicker = true }
                    )

                    SelectionActionItem(
                        icon = Icons.Rounded.FormatUnderlined,
                        label = "Underline",
                        onClick = onUnderline
                    )

                    SelectionActionItem(
                        icon = Icons.Rounded.StrikethroughS,
                        label = "Strike",
                        onClick = onStrike
                    )

                    VerticalDivider(
                        modifier = Modifier
                            .height(20.dp)
                            .padding(horizontal = 2.dp),
                        color = MaterialTheme.colorScheme.outlineVariant
                    )

                    SelectionActionItem(
                        icon = Icons.Rounded.AutoAwesome,
                        label = "Ask AI",
                        tint = MaterialTheme.colorScheme.primary,
                        onClick = onAskAi
                    )

                    SelectionActionItem(
                        icon = Icons.Rounded.Share,
                        label = "Share",
                        onClick = onShare
                    )

                    SelectionActionItem(
                        icon = Icons.Rounded.Search,
                        label = "Web",
                        onClick = onSearch
                    )

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Dismiss",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SelectionActionItem(
    icon: ImageVector,
    label: String,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = tint,
            modifier = Modifier.size(16.dp)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp),
            color = tint
        )
    }
}
