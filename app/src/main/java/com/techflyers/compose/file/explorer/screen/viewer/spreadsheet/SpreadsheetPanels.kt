package com.techflyers.compose.file.explorer.screen.viewer.spreadsheet

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Redo
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.BorderAll
import androidx.compose.material.icons.rounded.BorderBottom
import androidx.compose.material.icons.rounded.BorderClear
import androidx.compose.material.icons.rounded.BorderInner
import androidx.compose.material.icons.rounded.BorderLeft
import androidx.compose.material.icons.rounded.BorderOuter
import androidx.compose.material.icons.rounded.BorderRight
import androidx.compose.material.icons.rounded.BorderStyle
import androidx.compose.material.icons.rounded.BorderTop
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Checkbox
import com.techflyers.compose.file.explorer.utils.xlsx.XlsxSheet
import com.techflyers.compose.file.explorer.utils.xlsx.XlsxRefs
import androidx.compose.material.icons.rounded.FormatAlignCenter
import androidx.compose.material.icons.rounded.FormatAlignLeft
import androidx.compose.material.icons.rounded.FormatAlignRight
import androidx.compose.material.icons.rounded.FormatBold
import androidx.compose.material.icons.rounded.FormatClear
import androidx.compose.material.icons.rounded.FormatColorFill
import androidx.compose.material.icons.rounded.FormatColorText
import androidx.compose.material.icons.rounded.FormatItalic
import androidx.compose.material.icons.rounded.FormatStrikethrough
import androidx.compose.material.icons.rounded.FormatUnderlined
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Numbers
import androidx.compose.material.icons.rounded.SaveAs
import androidx.compose.material.icons.rounded.TableRows
import androidx.compose.material.icons.rounded.VerticalAlignBottom
import androidx.compose.material.icons.rounded.VerticalAlignCenter
import androidx.compose.material.icons.rounded.VerticalAlignTop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.techflyers.compose.file.explorer.R
import com.techflyers.compose.file.explorer.utils.ExcelCellFormat
import com.techflyers.compose.file.explorer.utils.xlsx.XlsxWorkbook

enum class SpreadsheetPanel { NONE, FONT_COLOR, FILL_COLOR, ALIGN, BORDERS, NUMBER, ROWS_COLS }

class DropdownState(val r: Int, val c: Int, val anchor: Rect, val items: List<String>, val strict: Boolean)

val NumberFormats = listOf(
    "General" to "General",
    "Number" to "#,##0.00",
    "Integer" to "0",
    "Currency" to "\"$\"#,##0.00",
    "Accounting" to "_(\"$\"* #,##0.00_);_(\"$\"* (#,##0.00);_(\"$\"* \"-\"??_);_(@_)",
    "Percent" to "0%",
    "Percent (2dp)" to "0.00%",
    "Scientific" to "0.00E+00",
    "Short date" to "dd-mm-yyyy",
    "Long date" to "d mmmm yyyy",
    "Time" to "h:mm:ss",
    "Text" to "@"
)

val OfficeThemeColors: List<Color> = listOf(
    Color(0xFFFFFFFF), Color(0xFF000000), Color(0xFFE7E6E6), Color(0xFF44546A), Color(0xFF4472C4),
    Color(0xFFED7D31), Color(0xFFA5A5A5), Color(0xFFFFC000), Color(0xFF5B9BD5), Color(0xFF70AD47)
)

val OfficeStandardColors: List<Color> = listOf(
    Color(0xFFC00000), Color(0xFFFF0000), Color(0xFFFFC000), Color(0xFFFFFF00), Color(0xFF92D050),
    Color(0xFF00B050), Color(0xFF00B0F0), Color(0xFF0070C0), Color(0xFF002060), Color(0xFF7030A0)
)

fun officeVariants(base: Color): List<Color> {
    val lum = base.luminance()
    fun lighter(f: Float) = Color(
        base.red + (1f - base.red) * f, base.green + (1f - base.green) * f, base.blue + (1f - base.blue) * f
    )
    fun darker(f: Float) = Color(base.red * (1f - f), base.green * (1f - f), base.blue * (1f - f))
    return when {
        lum > 0.9f -> listOf(darker(0.05f), darker(0.15f), darker(0.25f), darker(0.35f), darker(0.5f))
        lum < 0.02f -> listOf(lighter(0.5f), lighter(0.35f), lighter(0.25f), lighter(0.15f), lighter(0.05f))
        else -> listOf(lighter(0.8f), lighter(0.6f), lighter(0.4f), darker(0.25f), darker(0.5f))
    }
}

// ── Formula Bar ─────────────────────────────────────────────────────────────

@Composable
fun FormulaBar(
    ref: String,
    value: String,
    editable: Boolean,
    editing: Boolean,
    focusRequester: FocusRequester,
    onValueChange: (String) -> Unit,
    onStartEdit: () -> Unit,
    onCopy: (String) -> Unit,
    onCommit: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    LaunchedEffect(editing) { if (editing) runCatching { focusRequester.requestFocus() } }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 3.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.widthIn(min = 36.dp, max = 96.dp)
            ) {
                Text(
                    text = ref,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                )
            }

            Box(
                Modifier
                    .width(1.dp)
                    .height(20.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant)
            )

            Text(
                text = "fx",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontStyle = FontStyle.Italic
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Box(Modifier.weight(1f)) {
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    readOnly = !editing,
                    singleLine = false,
                    maxLines = 4,
                    textStyle = TextStyle(
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 14.sp
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { onCommit() }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                        .onFocusChanged { if (it.isFocused && !editing && editable) onStartEdit() },
                    decorationBox = { inner ->
                        if (value.isEmpty() && editing) {
                            Text(
                                text = stringResource(R.string.sheet_formula_hint),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                        }
                        inner()
                    }
                )
            }

            IconButton(
                onClick = { onCopy(value) },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    Icons.Rounded.ContentCopy,
                    contentDescription = stringResource(R.string.copy),
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (editing) {
                IconButton(
                    onClick = onCommit,
                    modifier = Modifier.size(32.dp),
                    colors = IconButtonDefaults.iconButtonColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Icon(
                        Icons.Rounded.Check,
                        contentDescription = stringResource(R.string.sheet_save),
                        modifier = Modifier.size(17.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }
    }
}

// ── Sheet Tabs ──────────────────────────────────────────────────────────────

@Composable
fun SheetTabs(
    wb: XlsxWorkbook,
    indices: List<Int>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    if (indices.size <= 1) return
    val scrollState = rememberScrollState()

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 2.dp,
        border = androidx.compose.foundation.BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .horizontalScroll(scrollState)
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            indices.forEach { sheetIndex ->
                val sheet = wb.sheets[sheetIndex]
                val isSelected = sheetIndex == selected
                val tabColor = sheet.tabColor?.resolve(wb.styles.themeColors, wb.styles.indexedColors)?.let(::Color)

                Surface(
                    modifier = Modifier
                        .height(34.dp)
                        .clip(RoundedCornerShape(17.dp))
                        .clickable { onSelect(sheetIndex) },
                    shape = RoundedCornerShape(17.dp),
                    color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (tabColor != null) {
                            Box(
                                Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(tabColor)
                            )
                        }
                        Text(
                            text = sheet.name,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            ),
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

// ── Edit Toolbar ────────────────────────────────────────────────────────────

@Composable
fun EditToolbar(
    accent: Color,
    canUndo: Boolean,
    canRedo: Boolean,
    hasSelection: Boolean,
    bold: Boolean,
    italic: Boolean,
    underline: Boolean,
    strike: Boolean,
    panel: SpreadsheetPanel,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onBold: () -> Unit,
    onItalic: () -> Unit,
    onUnderline: () -> Unit,
    onStrike: () -> Unit,
    onPanel: (SpreadsheetPanel) -> Unit,
    onClear: () -> Unit,
    onSaveAs: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 4.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        val scrollState = rememberScrollState()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(scrollState)
                .padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            ToolButton(Icons.AutoMirrored.Rounded.Undo, stringResource(R.string.sheet_undo), false, onUndo, enabled = canUndo)
            ToolButton(Icons.AutoMirrored.Rounded.Redo, stringResource(R.string.sheet_redo), false, onRedo, enabled = canRedo)

            Box(Modifier.width(1.dp).height(20.dp).background(MaterialTheme.colorScheme.outlineVariant))

            ToolButton(Icons.Rounded.FormatBold, stringResource(R.string.sheet_bold), bold, onBold, enabled = hasSelection)
            ToolButton(Icons.Rounded.FormatItalic, stringResource(R.string.sheet_italic), italic, onItalic, enabled = hasSelection)
            ToolButton(Icons.Rounded.FormatUnderlined, stringResource(R.string.sheet_underline), underline, onUnderline, enabled = hasSelection)
            ToolButton(Icons.Rounded.FormatStrikethrough, stringResource(R.string.sheet_strike), strike, onStrike, enabled = hasSelection)

            Box(Modifier.width(1.dp).height(20.dp).background(MaterialTheme.colorScheme.outlineVariant))

            ToolButton(Icons.Rounded.FormatColorText, stringResource(R.string.sheet_text_color), panel == SpreadsheetPanel.FONT_COLOR, { onPanel(SpreadsheetPanel.FONT_COLOR) }, enabled = hasSelection)
            ToolButton(Icons.Rounded.FormatColorFill, stringResource(R.string.sheet_fill_color), panel == SpreadsheetPanel.FILL_COLOR, { onPanel(SpreadsheetPanel.FILL_COLOR) }, enabled = hasSelection)
            ToolButton(Icons.Rounded.FormatAlignLeft, stringResource(R.string.sheet_align), panel == SpreadsheetPanel.ALIGN, { onPanel(SpreadsheetPanel.ALIGN) }, enabled = hasSelection)
            ToolButton(Icons.Rounded.BorderAll, stringResource(R.string.sheet_borders), panel == SpreadsheetPanel.BORDERS, { onPanel(SpreadsheetPanel.BORDERS) }, enabled = hasSelection)
            ToolButton(Icons.Rounded.Numbers, stringResource(R.string.sheet_number_format), panel == SpreadsheetPanel.NUMBER, { onPanel(SpreadsheetPanel.NUMBER) }, enabled = hasSelection)
            ToolButton(Icons.Rounded.TableRows, stringResource(R.string.sheet_rows_cols), panel == SpreadsheetPanel.ROWS_COLS, { onPanel(SpreadsheetPanel.ROWS_COLS) }, enabled = hasSelection)

            Box(Modifier.width(1.dp).height(20.dp).background(MaterialTheme.colorScheme.outlineVariant))

            ToolButton(Icons.Rounded.FormatClear, stringResource(R.string.sheet_clear), false, onClear, enabled = hasSelection)
            ToolButton(Icons.Rounded.SaveAs, stringResource(R.string.sheet_save_as), false, onSaveAs, enabled = true)
        }
    }
}

@Composable
private fun ToolButton(
    icon: ImageVector,
    desc: String,
    active: Boolean,
    onClick: () -> Unit,
    enabled: Boolean = true
) {
    val haptics = LocalHapticFeedback.current
    Surface(
        modifier = Modifier
            .size(38.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(enabled = enabled) {
                haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                onClick()
            },
        shape = RoundedCornerShape(10.dp),
        color = when {
            active -> MaterialTheme.colorScheme.primaryContainer
            else -> Color.Transparent
        }
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                icon,
                contentDescription = desc,
                modifier = Modifier.size(20.dp),
                tint = when {
                    !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                    active -> MaterialTheme.colorScheme.onPrimaryContainer
                    else -> MaterialTheme.colorScheme.onSurface
                }
            )
        }
    }
}

// ── Panels ──────────────────────────────────────────────────────────────────

@Composable
fun AlignPanel(
    h: String,
    v: String,
    wrap: Boolean,
    onH: (String) -> Unit,
    onV: (String) -> Unit,
    onWrap: (Boolean) -> Unit
) {
    val hOptions = listOf("left", "center", "right")
    val hLabels = listOf("Left", "Center", "Right")
    val hIcons = listOf(Icons.Rounded.FormatAlignLeft, Icons.Rounded.FormatAlignCenter, Icons.Rounded.FormatAlignRight)

    val vOptions = listOf("top", "center", "bottom")
    val vLabels = listOf("Top", "Middle", "Bottom")
    val vIcons = listOf(Icons.Rounded.VerticalAlignTop, Icons.Rounded.VerticalAlignCenter, Icons.Rounded.VerticalAlignBottom)

    Column(
        Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = stringResource(R.string.sheet_horizontal).uppercase(),
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            hOptions.forEachIndexed { i, opt ->
                val selected = h == opt || (h == "general" && opt == "left")
                Button(
                    onClick = { onH(opt) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
                        contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                    )
                ) {
                    Icon(hIcons[i], contentDescription = hLabels[i], modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(hLabels[i], fontSize = 12.sp)
                }
            }
        }

        Text(
            text = stringResource(R.string.sheet_vertical).uppercase(),
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            vOptions.forEachIndexed { i, opt ->
                val selected = v == opt
                Button(
                    onClick = { onV(opt) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
                        contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                    )
                ) {
                    Icon(vIcons[i], contentDescription = vLabels[i], modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(vLabels[i], fontSize = 12.sp)
                }
            }
        }

        Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                stringResource(R.string.sheet_wrap),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurface
            )
            Switch(
                checked = wrap,
                onCheckedChange = onWrap,
                colors = SwitchDefaults.colors(checkedThumbColor = MaterialTheme.colorScheme.primary)
            )
        }
    }
}

@Composable
fun BordersPanel(
    onPick: (com.techflyers.compose.file.explorer.screen.viewer.spreadsheet.BorderPreset) -> Unit
) {
    val items = listOf(
        Triple(BorderPreset.ALL, Icons.Rounded.BorderAll, "All borders"),
        Triple(BorderPreset.OUTER, Icons.Rounded.BorderOuter, "Outside borders"),
        Triple(BorderPreset.THICK_OUTER, Icons.Rounded.BorderStyle, "Thick outside borders"),
        Triple(BorderPreset.INNER, Icons.Rounded.BorderInner, "Inside borders"),
        Triple(BorderPreset.TOP, Icons.Rounded.BorderTop, "Top border"),
        Triple(BorderPreset.BOTTOM, Icons.Rounded.BorderBottom, "Bottom border"),
        Triple(BorderPreset.LEFT, Icons.Rounded.BorderLeft, "Left border"),
        Triple(BorderPreset.RIGHT, Icons.Rounded.BorderRight, "Right border"),
        Triple(BorderPreset.NONE, Icons.Rounded.BorderClear, "No borders")
    )
    Column(
        Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = stringResource(R.string.sheet_borders).uppercase(),
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        for ((p, icon, label) in items) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onPick(p) },
                shape = RoundedCornerShape(12.dp),
                color = Color.Transparent
            ) {
                Row(
                    Modifier.fillMaxSize().padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary)
                    Text(label, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium), color = MaterialTheme.colorScheme.onSurface)
                }
            }
        }
    }
}

@Composable
fun NumberPanel(
    current: String,
    onPick: (String) -> Unit
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = stringResource(R.string.sheet_number_format).uppercase(),
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        for ((label, code) in NumberFormats) {
            val sample = ExcelCellFormat.apply(if (code.contains("d") && code.contains("y")) "46267" else "1234.5", code)
            val isCurrent = current == code
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onPick(code) },
                shape = RoundedCornerShape(12.dp),
                color = if (isCurrent) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
            ) {
                Row(
                    Modifier.fillMaxSize().padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        label,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium
                        ),
                        color = if (isCurrent) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        sample,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isCurrent) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
fun RowsColsPanel(
    enabled: Boolean,
    onAction: (Int) -> Unit
) {
    val labels = listOf(
        R.string.sheet_insert_row_above, R.string.sheet_insert_row_below, R.string.sheet_delete_rows,
        R.string.sheet_insert_col_left, R.string.sheet_insert_col_right, R.string.sheet_delete_cols
    )
    Column(
        Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = stringResource(R.string.sheet_rows_cols).uppercase(),
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        labels.forEachIndexed { i, id ->
            val destructive = i == 2 || i == 5
            val color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(enabled = enabled) { onAction(i) },
                shape = RoundedCornerShape(12.dp),
                color = if (destructive) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f) else Color.Transparent
            ) {
                Row(
                    Modifier.fillMaxSize().padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        stringResource(id),
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                        color = if (!enabled) color.copy(alpha = 0.38f) else color
                    )
                }
            }
        }
    }
}

// ── Color Picker ────────────────────────────────────────────────────────────

@Composable
fun ExcelColorPicker(
    color: Color,
    onColorChange: (Color) -> Unit,
    onClear: (() -> Unit)?,
    clearLabel: String,
    themeColors: List<Color> = OfficeThemeColors,
    standardColors: List<Color> = OfficeStandardColors,
    themeVariants: (Color) -> List<Color> = ::officeVariants,
    onThemePick: ((column: Int, row: Int, color: Color) -> Unit)? = null
) {
    val haptics = LocalHapticFeedback.current
    var customOpen by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (onClear != null) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable {
                        haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                        onClear()
                    },
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest
            ) {
                Row(
                    Modifier.fillMaxSize().padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Rounded.Block, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                    Text(clearLabel, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium), color = MaterialTheme.colorScheme.onSurface)
                }
            }
        }

        Text(
            text = "Theme colours",
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        val columns = themeColors.map { listOf(it) + themeVariants(it) }
        SwatchRow(themeColors, color) { c ->
            val col = themeColors.indexOf(c).coerceAtLeast(0)
            if (onThemePick != null) onThemePick(col, 0, c) else onColorChange(c)
        }
        Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
            for (row in 1..5) {
                val rowColors = columns.map { it[row] }
                SwatchRow(rowColors, color, gap = 4.dp, tight = true) { c ->
                    val col = rowColors.indexOf(c).coerceAtLeast(0)
                    if (onThemePick != null) onThemePick(col, row, c) else onColorChange(c)
                }
            }
        }

        Text(
            text = "Standard colours",
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        SwatchRow(standardColors, color) { onColorChange(it) }

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .clickable { customOpen = !customOpen },
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHighest
        ) {
            Row(
                Modifier.fillMaxSize().padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    Modifier
                        .size(20.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(
                            Brush.sweepGradient(
                                listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red)
                            )
                        )
                )
                Text(
                    stringResource(R.string.sheet_more_colors),
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Icon(if (customOpen) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        AnimatedVisibility(
            visible = customOpen,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            CustomColorSection(color = color, onChange = onColorChange)
        }
    }
}

@Composable
private fun SwatchRow(
    colors: List<Color>,
    selected: Color,
    gap: androidx.compose.ui.unit.Dp = 4.dp,
    tight: Boolean = false,
    onPick: (Color) -> Unit
) {
    val selArgb = selected.copy(alpha = 1f).toArgb()
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap)) {
        colors.forEach { c ->
            val isSel = c.copy(alpha = 1f).toArgb() == selArgb
            val swatchShape = RoundedCornerShape(if (tight) 3.dp else 6.dp)
            Box(
                Modifier
                    .weight(1f)
                    .aspectRatio(if (tight) 1.25f else 1f)
                    .clip(swatchShape)
                    .background(c)
                    .border(
                        width = if (isSel) 2.dp else 0.5.dp,
                        color = if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                        shape = swatchShape
                    )
                    .clickable { onPick(c) },
                contentAlignment = Alignment.Center
            ) {
                if (isSel && !tight) {
                    Icon(
                        Icons.Rounded.Check,
                        contentDescription = null,
                        tint = if (c.luminance() > 0.5f) Color.Black else Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun CustomColorSection(
    color: Color,
    onChange: (Color) -> Unit
) {
    val hsv = remember(color) {
        val out = FloatArray(3)
        android.graphics.Color.colorToHSV(color.toArgb(), out)
        out
    }
    var hue by remember { mutableFloatStateOf(hsv[0]) }
    var sat by remember { mutableFloatStateOf(hsv[1]) }
    var value by remember { mutableFloatStateOf(hsv[2]) }

    fun emit() {
        val c = Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, sat, value)))
        onChange(c)
    }

    val pure = Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, 1f, 1f)))

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(140.dp)
                .clip(RoundedCornerShape(12.dp))
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        sat = (down.position.x / size.width).coerceIn(0f, 1f)
                        value = 1f - (down.position.y / size.height).coerceIn(0f, 1f)
                        emit()
                        down.consume()
                        while (true) {
                            val ev = awaitPointerEvent()
                            val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                            if (!ch.pressed) break
                            ch.consume()
                            sat = (ch.position.x / size.width).coerceIn(0f, 1f)
                            value = 1f - (ch.position.y / size.height).coerceIn(0f, 1f)
                            emit()
                        }
                    }
                }
        ) {
            drawRect(Brush.horizontalGradient(listOf(Color.White, pure)))
            drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
            val c = Offset(sat * size.width, (1f - value) * size.height)
            drawCircle(Color.Black.copy(0.25f), radius = 10.dp.toPx(), center = c, style = Stroke(3.dp.toPx()))
            drawCircle(Color.White, radius = 9.dp.toPx(), center = c, style = Stroke(2.5.dp.toPx()))
        }

        Canvas(
            Modifier
                .fillMaxWidth()
                .height(26.dp)
                .clip(RoundedCornerShape(13.dp))
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        hue = ((down.position.x / size.width) * 360f).coerceIn(0f, 359.9f)
                        emit()
                        down.consume()
                        while (true) {
                            val ev = awaitPointerEvent()
                            val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                            if (!ch.pressed) break
                            ch.consume()
                            hue = ((ch.position.x / size.width) * 360f).coerceIn(0f, 359.9f)
                            emit()
                        }
                    }
                }
        ) {
            drawRoundRect(
                Brush.horizontalGradient(
                    listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red)
                ),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height / 2f)
            )
            val cx = (hue / 360f) * size.width
            val c = Offset(cx.coerceIn(size.height / 2f, size.width - size.height / 2f), size.height / 2f)
            drawCircle(Color.Black.copy(0.25f), radius = size.height / 2f - 1.dp.toPx(), center = c, style = Stroke(3.dp.toPx()))
            drawCircle(Color.White, radius = size.height / 2f - 2.dp.toPx(), center = c, style = Stroke(2.dp.toPx()))
        }
    }
}

// ── Validation Dropdown ─────────────────────────────────────────────────────

@Composable
fun ValidationDropdown(
    state: DropdownState,
    current: String,
    onPick: (String) -> Unit
) {
    val density = LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val screenH = with(density) { maxHeight.toPx() }
        val screenW = with(density) { maxWidth.toPx() }
        val anchor = if (state.anchor == Rect.Zero) Rect(screenW * 0.2f, screenH * 0.35f, screenW * 0.8f, screenH * 0.35f) else state.anchor
        val itemH = with(density) { 46.dp.toPx() }
        val listH = (state.items.size * itemH + with(density) { 16.dp.toPx() }).coerceAtMost(screenH * 0.45f)
        val width = maxOf(anchor.width, with(density) { 220.dp.toPx() }).coerceAtMost(screenW - with(density) { 24.dp.toPx() })
        val below = anchor.bottom + listH + 24f < screenH
        val y = if (below) anchor.bottom + 6f else (anchor.top - listH - 6f).coerceAtLeast(24f)
        val x = anchor.left.coerceIn(with(density) { 12.dp.toPx() }, screenW - width - with(density) { 12.dp.toPx() })

        Surface(
            modifier = Modifier
                .offset { IntOffset(x.toInt(), y.toInt()) }
                .width(with(density) { width.toDp() })
                .heightIn(max = with(density) { listH.toDp() }),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            tonalElevation = 8.dp,
            shadowElevation = 8.dp,
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(6.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                for (item in state.items) {
                    val sel = item.equals(current, ignoreCase = true)
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onPick(item) },
                        shape = RoundedCornerShape(10.dp),
                        color = if (sel) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                    ) {
                        Row(
                            Modifier.fillMaxSize().padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                item,
                                Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal
                                ),
                                color = if (sel) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (sel) {
                                Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── Search Bar ──────────────────────────────────────────────────────────────

@Composable
fun SpreadsheetSearchBar(
    query: String,
    matchCount: Int,
    currentMatchIndex: Int,
    onQueryChange: (String) -> Unit,
    onPrevMatch: () -> Unit,
    onNextMatch: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 6.dp,
        shadowElevation = 8.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = { Text("Search spreadsheet…", fontSize = 14.sp) },
                singleLine = true,
                modifier = Modifier.weight(1f),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = Color.Transparent
                )
            )

            if (matchCount > 0) {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = "${currentMatchIndex + 1} of $matchCount",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            IconButton(onClick = onPrevMatch, enabled = matchCount > 0, modifier = Modifier.size(34.dp)) {
                Icon(Icons.Rounded.KeyboardArrowUp, contentDescription = "Previous match")
            }

            IconButton(onClick = onNextMatch, enabled = matchCount > 0, modifier = Modifier.size(34.dp)) {
                Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Next match")
            }

            IconButton(onClick = onClose, modifier = Modifier.size(34.dp)) {
                Icon(Icons.Rounded.Close, contentDescription = "Close search")
            }
        }
    }
}

// ── Unsaved Changes Dialog ──────────────────────────────────────────────────

@Composable
fun SpreadsheetUnsavedChangesDialog(
    visible: Boolean,
    onDiscard: () -> Unit,
    onCancel: () -> Unit,
    onSave: () -> Unit
) {
    if (!visible) return

    AlertDialog(
        onDismissRequest = onCancel,
        title = {
            Text(stringResource(R.string.sheet_unsaved_title), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
        },
        text = {
            Text(stringResource(R.string.sheet_unsaved_body), style = MaterialTheme.typography.bodyMedium)
        },
        confirmButton = {
            Button(onClick = onSave) {
                Text(stringResource(R.string.sheet_save))
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onDiscard) {
                    Text(stringResource(R.string.sheet_discard), color = MaterialTheme.colorScheme.error)
                }
                TextButton(onClick = onCancel) {
                    Text("Cancel")
                }
            }
        }
    )
}

// ── Spreadsheet Advanced Filter & Sorting Dialog ────────────────────────────

@Composable
fun SpreadsheetFilterDialog(
    columns: List<String>,
    currentColumn: Int,
    currentQuery: String?,
    currentCondition: String,
    currentSort: String,
    firstRowIsHeader: Boolean,
    onDismiss: () -> Unit,
    onClear: () -> Unit,
    onApply: (column: Int, query: String?, condition: String, sort: String, firstRowHeader: Boolean) -> Unit
) {
    var selectedCol by remember(currentColumn) { mutableIntStateOf(currentColumn) }
    var queryText by remember(currentQuery) { mutableStateOf(currentQuery ?: "") }
    var condition by remember(currentCondition) { mutableStateOf(currentCondition) }
    var sortType by remember(currentSort) { mutableStateOf(currentSort) }
    var firstRowHeader by remember(firstRowIsHeader) { mutableStateOf(firstRowIsHeader) }
    var colDropdownExpanded by remember { mutableStateOf(false) }

    val columnOptions = remember(columns) {
        if (columns.isNotEmpty()) columns else listOf("Column A")
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Rounded.FilterList,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Filter & Sort Columns",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Column Selector
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Target Column", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = { colDropdownExpanded = true },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(columnOptions.getOrElse(selectedCol) { "Column ${XlsxRefs.colLetter(selectedCol)}" })
                            Spacer(Modifier.weight(1f))
                            Icon(Icons.Rounded.ExpandMore, contentDescription = null)
                        }
                        DropdownMenu(
                            expanded = colDropdownExpanded,
                            onDismissRequest = { colDropdownExpanded = false }
                        ) {
                            columnOptions.forEachIndexed { idx, label ->
                                DropdownMenuItem(
                                    text = { Text(label) },
                                    onClick = {
                                        selectedCol = idx
                                        colDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                // Filter Text Input
                OutlinedTextField(
                    value = queryText,
                    onValueChange = { queryText = it },
                    label = { Text("Filter query") },
                    placeholder = { Text("Search in column…") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    trailingIcon = {
                        if (queryText.isNotEmpty()) {
                            IconButton(onClick = { queryText = "" }) {
                                Icon(Icons.Rounded.Clear, contentDescription = "Clear")
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                // Filter Condition
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Filter Condition", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(
                            "contains" to "Contains",
                            "equals" to "Equals",
                            "starts_with" to "Starts with",
                            "not_contains" to "Doesn't contain"
                        ).forEach { (key, label) ->
                            FilterChip(
                                selected = condition == key,
                                onClick = { condition = key },
                                label = { Text(label, fontSize = 12.sp) }
                            )
                        }
                    }
                }

                HorizontalDivider()

                // Sort Order
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Sort Order", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FilterChip(
                            selected = sortType == "none",
                            onClick = { sortType = "none" },
                            label = { Text("None", fontSize = 12.sp) },
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = sortType == "asc",
                            onClick = { sortType = "asc" },
                            leadingIcon = { Icon(Icons.Rounded.ArrowUpward, contentDescription = null, modifier = Modifier.size(14.dp)) },
                            label = { Text("A → Z", fontSize = 12.sp) },
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = sortType == "desc",
                            onClick = { sortType = "desc" },
                            leadingIcon = { Icon(Icons.Rounded.ArrowDownward, contentDescription = null, modifier = Modifier.size(14.dp)) },
                            label = { Text("Z → A", fontSize = 12.sp) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // First row is header checkbox
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { firstRowHeader = !firstRowHeader }
                        .padding(vertical = 4.dp)
                ) {
                    Checkbox(
                        checked = firstRowHeader,
                        onCheckedChange = { firstRowHeader = it }
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "First row is header (keep at top)",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onApply(
                        selectedCol,
                        queryText.ifBlank { null },
                        condition,
                        sortType,
                        firstRowHeader
                    )
                }
            ) {
                Text("Apply")
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TextButton(onClick = onClear) {
                    Text("Clear All", color = MaterialTheme.colorScheme.error)
                }
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        }
    )
}
