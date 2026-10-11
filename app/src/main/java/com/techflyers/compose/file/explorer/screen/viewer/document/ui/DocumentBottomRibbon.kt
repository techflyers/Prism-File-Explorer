package com.techflyers.compose.file.explorer.screen.viewer.document.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Redo
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Brush
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.CropSquare
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Draw
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.FormatColorReset
import androidx.compose.material.icons.rounded.FormatPaint
import androidx.compose.material.icons.rounded.FormatUnderlined
import androidx.compose.material.icons.rounded.Gesture
import androidx.compose.material.icons.rounded.Highlight
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.NorthEast
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.SlowMotionVideo
import androidx.compose.material.icons.rounded.Straighten
import androidx.compose.material.icons.rounded.StrikethroughS
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.ViewAgenda
import androidx.compose.material.icons.rounded.ZoomIn
import androidx.compose.material.icons.rounded.ZoomOut
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.techflyers.compose.file.explorer.screen.viewer.document.core.PdfEditTool

val RibbonPresetColors = listOf(
    Color(0xFFFFCC00), // Yellow
    Color(0xFF34C759), // Green
    Color(0xFF007AFF), // Blue
    Color(0xFF5856D6), // Purple
    Color(0xFFFF3B30), // Red
    Color(0xFFFF9500), // Orange
    Color(0xFF000000), // Black
    Color(0xFFFFFFFF)  // White
)

@Composable
fun DocumentBottomRibbon(
    activeTool: PdfEditTool,
    onSelectTool: (PdfEditTool) -> Unit,
    currentColor: Color,
    onColorSelected: (Color) -> Unit,
    currentStrokeWidth: Float,
    onStrokeWidthChange: (Float) -> Unit,
    canUndo: Boolean,
    onUndo: () -> Unit,
    onClearPage: () -> Unit,
    // View state
    isNightMode: Boolean,
    onToggleNightMode: () -> Unit,
    isAutoScrolling: Boolean,
    autoScrollSpeed: Float,
    onToggleAutoScroll: () -> Unit,
    onAutoScrollSpeedChange: (Float) -> Unit,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    onResetZoom: () -> Unit,
    // Actions
    onAskAiClick: () -> Unit,
    onSearchClick: () -> Unit,
    onSignaturePadClick: () -> Unit,
    onPickImageClick: () -> Unit,
    onShareClick: () -> Unit,
    onInfoClick: () -> Unit,
    // LibreOffice on-demand hint
    showLibreOfficeHint: Boolean,
    onDownloadLibreOffice: () -> Unit,
    visible: Boolean,
    modifier: Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableIntStateOf(0) } // 0: Annotate, 1: View, 2: Tools

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + slideInVertically { it },
        exit = fadeOut() + slideOutVertically { it },
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Optional LibreOffice Engine Prompt Card
            if (showLibreOfficeHint) {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.95f),
                    tonalElevation = 4.dp,
                    shadowElevation = 6.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.CloudDownload,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(24.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Enhance Document Fidelity",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = "Download LibreOffice Engine (~45 MB) for pixel-perfect layout.",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                            )
                        }
                        Button(
                            onClick = onDownloadLibreOffice,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Download", fontSize = 12.sp)
                        }
                    }
                }
            }

            // Expanded Ribbon Panel
            if (isExpanded || activeTool != PdfEditTool.None) {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.96f),
                    tonalElevation = 6.dp,
                    shadowElevation = 10.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        // Category Tabs
                        TabRow(
                            selectedTabIndex = selectedTab,
                            containerColor = Color.Transparent,
                            divider = {}
                        ) {
                            Tab(
                                selected = selectedTab == 0,
                                onClick = { selectedTab = 0 },
                                text = { Text("Annotate", fontWeight = FontWeight.SemiBold) }
                            )
                            Tab(
                                selected = selectedTab == 1,
                                onClick = { selectedTab = 1 },
                                text = { Text("View", fontWeight = FontWeight.SemiBold) }
                            )
                            Tab(
                                selected = selectedTab == 2,
                                onClick = { selectedTab = 2 },
                                text = { Text("Tools", fontWeight = FontWeight.SemiBold) }
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Tab 0: Annotate Tools
                        if (selectedTab == 0) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                ToolChip(
                                    icon = Icons.Rounded.Brush,
                                    label = "Pen",
                                    selected = activeTool == PdfEditTool.Draw,
                                    onClick = {
                                        onSelectTool(if (activeTool == PdfEditTool.Draw) PdfEditTool.None else PdfEditTool.Draw)
                                    }
                                )
                                ToolChip(
                                    icon = Icons.Rounded.Highlight,
                                    label = "Highlight",
                                    selected = activeTool == PdfEditTool.Highlight,
                                    onClick = {
                                        onSelectTool(if (activeTool == PdfEditTool.Highlight) PdfEditTool.None else PdfEditTool.Highlight)
                                    }
                                )
                                ToolChip(
                                    icon = Icons.Rounded.CropSquare,
                                    label = "Rectangle",
                                    selected = activeTool == PdfEditTool.Rect,
                                    onClick = {
                                        onSelectTool(if (activeTool == PdfEditTool.Rect) PdfEditTool.None else PdfEditTool.Rect)
                                    }
                                )
                                ToolChip(
                                    icon = Icons.Rounded.RadioButtonUnchecked,
                                    label = "Oval",
                                    selected = activeTool == PdfEditTool.Ellipse,
                                    onClick = {
                                        onSelectTool(if (activeTool == PdfEditTool.Ellipse) PdfEditTool.None else PdfEditTool.Ellipse)
                                    }
                                )
                                ToolChip(
                                    icon = Icons.Rounded.Straighten,
                                    label = "Line",
                                    selected = activeTool == PdfEditTool.Line,
                                    onClick = {
                                        onSelectTool(if (activeTool == PdfEditTool.Line) PdfEditTool.None else PdfEditTool.Line)
                                    }
                                )
                                ToolChip(
                                    icon = Icons.Rounded.NorthEast,
                                    label = "Arrow",
                                    selected = activeTool == PdfEditTool.Arrow,
                                    onClick = {
                                        onSelectTool(if (activeTool == PdfEditTool.Arrow) PdfEditTool.None else PdfEditTool.Arrow)
                                    }
                                )
                                ToolChip(
                                    icon = Icons.Rounded.TextFields,
                                    label = "Text Box",
                                    selected = activeTool == PdfEditTool.Text,
                                    onClick = {
                                        onSelectTool(if (activeTool == PdfEditTool.Text) PdfEditTool.None else PdfEditTool.Text)
                                    }
                                )
                                ToolChip(
                                    icon = Icons.Rounded.EditNote,
                                    label = "Sticky Note",
                                    selected = activeTool == PdfEditTool.Note,
                                    onClick = {
                                        onSelectTool(if (activeTool == PdfEditTool.Note) PdfEditTool.None else PdfEditTool.Note)
                                    }
                                )
                                ToolChip(
                                    icon = Icons.Rounded.Gesture,
                                    label = "Signature",
                                    selected = false,
                                    onClick = onSignaturePadClick
                                )
                                ToolChip(
                                    icon = Icons.Rounded.AddPhotoAlternate,
                                    label = "Add Image",
                                    selected = false,
                                    onClick = onPickImageClick
                                )
                            }

                            // Active tool sub-toolbar (Color palette, stroke width, undo, clear)
                            if (activeTool in listOf(PdfEditTool.Draw, PdfEditTool.Highlight, PdfEditTool.Rect, PdfEditTool.Ellipse, PdfEditTool.Line, PdfEditTool.Arrow)) {
                                Spacer(modifier = Modifier.height(10.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    // Palette
                                    Row(
                                        modifier = Modifier
                                            .weight(1f)
                                            .horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        RibbonPresetColors.forEach { color ->
                                            Box(
                                                modifier = Modifier
                                                    .size(28.dp)
                                                    .clip(CircleShape)
                                                    .background(color)
                                                    .border(
                                                        width = if (currentColor == color) 2.5.dp else 1.dp,
                                                        color = if (currentColor == color) MaterialTheme.colorScheme.primary else Color.Gray.copy(alpha = 0.4f),
                                                        shape = CircleShape
                                                    )
                                                    .clickable { onColorSelected(color) }
                                            )
                                        }
                                    }

                                    // Undo button
                                    IconButton(
                                        onClick = onUndo,
                                        enabled = canUndo,
                                        modifier = Modifier.size(34.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Rounded.Undo,
                                            contentDescription = "Undo",
                                            tint = if (canUndo) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                        )
                                    }

                                    // Clear Page marks
                                    IconButton(
                                        onClick = onClearPage,
                                        modifier = Modifier.size(34.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.DeleteOutline,
                                            contentDescription = "Clear",
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    }

                                    // Close sub-tool
                                    IconButton(
                                        onClick = { onSelectTool(PdfEditTool.None) },
                                        modifier = Modifier.size(34.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Close,
                                            contentDescription = "Close Tool",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                // Stroke width slider
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = "Stroke",
                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Slider(
                                        value = currentStrokeWidth,
                                        onValueChange = onStrokeWidthChange,
                                        valueRange = 2f..24f,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }

                        // Tab 1: View Controls
                        if (selectedTab == 1) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                ToolChip(
                                    icon = if (isNightMode) Icons.Rounded.LightMode else Icons.Rounded.DarkMode,
                                    label = if (isNightMode) "Light Mode" else "Dark Page",
                                    selected = isNightMode,
                                    onClick = onToggleNightMode
                                )
                                ToolChip(
                                    icon = if (isAutoScrolling) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                    label = if (isAutoScrolling) "Pause Scroll" else "Auto-Scroll",
                                    selected = isAutoScrolling,
                                    onClick = onToggleAutoScroll
                                )
                                ToolChip(
                                    icon = Icons.Rounded.ZoomIn,
                                    label = "Zoom In",
                                    selected = false,
                                    onClick = onZoomIn
                                )
                                ToolChip(
                                    icon = Icons.Rounded.ZoomOut,
                                    label = "Zoom Out",
                                    selected = false,
                                    onClick = onZoomOut
                                )
                                ToolChip(
                                    icon = Icons.Rounded.ViewAgenda,
                                    label = "Fit Width",
                                    selected = false,
                                    onClick = onResetZoom
                                )
                            }

                            if (isAutoScrolling) {
                                Spacer(modifier = Modifier.height(10.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = "Speed: ${"%.1f".format(autoScrollSpeed)}x",
                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Slider(
                                        value = autoScrollSpeed,
                                        onValueChange = onAutoScrollSpeedChange,
                                        valueRange = 0.5f..4.0f,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }

                        // Tab 2: Tools (Search, Info, Share, Print)
                        if (selectedTab == 2) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                ToolChip(
                                    icon = Icons.Rounded.Search,
                                    label = "Find in Document",
                                    selected = false,
                                    onClick = onSearchClick
                                )
                                ToolChip(
                                    icon = Icons.Rounded.AutoAwesome,
                                    label = "Ask AI Assistant",
                                    selected = false,
                                    onClick = onAskAiClick
                                )
                                ToolChip(
                                    icon = Icons.Rounded.Info,
                                    label = "Document Info",
                                    selected = false,
                                    onClick = onInfoClick
                                )
                                ToolChip(
                                    icon = Icons.Rounded.Share,
                                    label = "Share",
                                    selected = false,
                                    onClick = onShareClick
                                )
                            }
                        }
                    }
                }
            }

            // Collapsed Dock / Floating Ribbon Trigger Pill
            Surface(
                shape = RoundedCornerShape(26.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.95f),
                tonalElevation = 6.dp,
                shadowElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Expand/Collapse Ribbon Main Pill
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = if (isExpanded || activeTool != PdfEditTool.None) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.clickable { isExpanded = !isExpanded }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = if (activeTool != PdfEditTool.None) Icons.Rounded.Draw else Icons.Rounded.FormatPaint,
                                contentDescription = null,
                                tint = if (isExpanded || activeTool != PdfEditTool.None) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = if (activeTool != PdfEditTool.None) activeTool.name else "Tools & Ribbon",
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                                color = if (isExpanded || activeTool != PdfEditTool.None) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Icon(
                                imageVector = if (isExpanded) Icons.Rounded.KeyboardArrowDown else Icons.Rounded.KeyboardArrowUp,
                                contentDescription = null,
                                tint = if (isExpanded || activeTool != PdfEditTool.None) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    // Direct Quick Action: Ask AI
                    IconButton(
                        onClick = onAskAiClick,
                        modifier = Modifier
                            .size(38.dp)
                            .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.AutoAwesome,
                            contentDescription = "Ask AI",
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // Direct Quick Action: Night Mode
                    IconButton(
                        onClick = onToggleNightMode,
                        modifier = Modifier.size(38.dp)
                    ) {
                        Icon(
                            imageVector = if (isNightMode) Icons.Rounded.LightMode else Icons.Rounded.DarkMode,
                            contentDescription = "Night Mode",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // Direct Quick Action: Auto Scroll
                    IconButton(
                        onClick = onToggleAutoScroll,
                        modifier = Modifier.size(38.dp)
                    ) {
                        Icon(
                            imageVector = if (isAutoScrolling) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = "Auto Scroll",
                            tint = if (isAutoScrolling) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ToolChip(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val bg = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh
    val fg = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = bg,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = fg,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                color = fg
            )
        }
    }
}
