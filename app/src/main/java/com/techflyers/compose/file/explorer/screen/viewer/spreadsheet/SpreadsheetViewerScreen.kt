package com.techflyers.compose.file.explorer.screen.viewer.spreadsheet

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.OpenWith
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Print
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.SaveAs
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.techflyers.compose.file.explorer.R
import com.techflyers.compose.file.explorer.screen.main.tab.files.holder.LocalFileHolder
import com.techflyers.compose.file.explorer.utils.xlsx.Axis
import com.techflyers.compose.file.explorer.utils.xlsx.CellRange
import com.techflyers.compose.file.explorer.utils.xlsx.ColorSpec
import com.techflyers.compose.file.explorer.utils.xlsx.PaintTheme
import com.techflyers.compose.file.explorer.utils.xlsx.SheetLayout
import com.techflyers.compose.file.explorer.utils.xlsx.StylePatch
import com.techflyers.compose.file.explorer.utils.xlsx.ValidationKind
import com.techflyers.compose.file.explorer.utils.xlsx.XlsxPainter
import com.techflyers.compose.file.explorer.utils.xlsx.XlsxRefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpreadsheetViewerScreen(
    instance: SpreadsheetViewerInstance,
    onBackPress: () -> Unit,
    viewModel: SpreadsheetViewModel
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    val uiState by viewModel.state.collectAsState()

    LaunchedEffect(instance.uri, instance.extraFilePath) {
        viewModel.load(context, instance.uri, instance.extraFilePath)
    }

    var sheetIndex by remember { mutableIntStateOf(0) }
    var editMode by remember { mutableStateOf(false) }
    var selection by remember { mutableStateOf<GridSelection?>(null) }
    var zoom by remember { mutableFloatStateOf(1f) }
    val scrollX = remember { mutableFloatStateOf(0f) }
    val listState = rememberLazyListState()

    // Formula bar & cell editing
    var formulaText by remember { mutableStateOf("") }
    var editingFormula by remember { mutableStateOf(false) }
    val formulaFocusRequester = remember { FocusRequester() }

    // Panels & Modals
    var activePanel by remember { mutableStateOf(SpreadsheetPanel.NONE) }
    var dropdownState by remember { mutableStateOf<DropdownState?>(null) }
    var showUnsavedDialog by remember { mutableStateOf(false) }
    var showOverflowMenu by remember { mutableStateOf(false) }
    var exitAfterSave by remember { mutableStateOf(false) }

    // Filter & Sort
    var showFilterDialog by remember { mutableStateOf(false) }
    var filterColumn by remember { mutableIntStateOf(0) }
    var filterQuery by remember { mutableStateOf<String?>(null) }
    var filterCondition by remember { mutableStateOf("contains") }
    var filterSort by remember { mutableStateOf("none") }
    var filterFirstRowIsHeader by remember { mutableStateOf(true) }

    val isFilterActive = !filterQuery.isNullOrBlank() || filterSort != "none"

    // Search
    var searchVisible by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var currentMatchIndex by remember { mutableIntStateOf(0) }

    val isDark = isSystemInDarkTheme()
    val surfaceColor = MaterialTheme.colorScheme.surface
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface
    val primaryColor = MaterialTheme.colorScheme.primary
    val gridLineColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)

    val gridColors = remember(surfaceColor, onSurfaceColor, primaryColor, isDark) {
        GridColors(
            surface = surfaceColor,
            headerBand = if (isDark) Color(0xFF24262B) else Color(0xFFF0F3F8),
            headerText = if (isDark) Color(0xFFADB0BA) else Color(0xFF5A5C64),
            headerDivider = if (isDark) Color(0xFF383A40) else Color(0xFFD6DAE2),
            accent = primaryColor
        )
    }

    val paintTheme = remember(isDark, surfaceColor, onSurfaceColor, gridLineColor) {
        PaintTheme(
            isDark = isDark,
            surface = surfaceColor.toArgb(),
            ink = onSurfaceColor.toArgb(),
            gridLine = gridLineColor.toArgb()
        )
    }

    val wb = uiState.workbook
    val visibleSheetIndices = remember(wb) {
        wb?.sheets?.indices?.filter { !wb.sheets[it].hidden } ?: emptyList()
    }

    val currentSheet = remember(wb, sheetIndex, visibleSheetIndices) {
        if (wb == null || visibleSheetIndices.isEmpty()) null
        else wb.sheets.getOrNull(sheetIndex) ?: wb.sheets[visibleSheetIndices.first()]
    }

    val layout = remember(currentSheet, uiState.version) {
        currentSheet?.let { SheetLayout(it) }
    }

    val painter = remember(wb, paintTheme) {
        wb?.let { XlsxPainter(it, paintTheme) }
    }

    LaunchedEffect(uiState.version) {
        painter?.invalidate()
    }

    val columnLabels = remember(currentSheet, layout, painter, filterFirstRowIsHeader) {
        if (currentSheet == null || layout == null) emptyList()
        else {
            (0 until minOf(layout.nCols, 50)).map { c ->
                val letter = XlsxRefs.colLetter(c)
                if (filterFirstRowIsHeader) {
                    val headerCell = currentSheet.cell(0, c)
                    val headerText = if (headerCell != null && painter != null) painter.display(headerCell).trim() else ""
                    if (headerText.isNotEmpty()) "$letter ($headerText)" else letter
                } else {
                    letter
                }
            }
        }
    }

    val filteredScrollRows = remember(
        currentSheet,
        layout,
        painter,
        uiState.version,
        filterColumn,
        filterQuery,
        filterCondition,
        filterSort,
        filterFirstRowIsHeader
    ) {
        if (currentSheet == null || layout == null || painter == null) return@remember null
        val q = filterQuery?.trim()
        val hasFilter = !q.isNullOrEmpty()
        val hasSort = filterSort == "asc" || filterSort == "desc"
        if (!hasFilter && !hasSort) return@remember null

        val baseRows = layout.scrollRows.toList()
        if (baseRows.isEmpty()) return@remember null

        // 1. Separate actual content rows from virtual empty padding rows
        // currentSheet.maxRow is the last row with actual data in the sheet
        val maxDataRow = maxOf(currentSheet.maxRow, 0)
        val (contentRows, paddingRows) = baseRows.partition { it <= maxDataRow }

        // 2. Identify header rows (if first row is designated as header)
        // If sheet.frozenRows > 0, the header is already in the frozen section.
        // If sheet.frozenRows == 0 and firstRowIsHeader is true, row 0 is the header in contentRows.
        val (headerRows, dataRows) = if (filterFirstRowIsHeader && contentRows.contains(0)) {
            listOf(0) to contentRows.filter { it != 0 }
        } else {
            emptyList<Int>() to contentRows
        }

        // Helper to extract numeric value and display text for cell (r, filterColumn)
        fun getCellSortData(r: Int): Pair<Double?, String> {
            val cell = currentSheet.cell(r, filterColumn) ?: return null to ""
            val displayText = painter.display(cell).trim()
            if (displayText.isEmpty()) return null to ""

            val clean = displayText
                .replace(",", "")
                .replace("$", "")
                .replace("€", "")
                .replace("£", "")
                .replace("¥", "")
                .replace("₹", "")
                .replace("%", "")
                .replace("(", "-")
                .replace(")", "")
                .trim()

            val num = if (cell.type == "" || cell.type == "n") {
                cell.raw.toDoubleOrNull() ?: clean.toDoubleOrNull()
            } else {
                clean.toDoubleOrNull()
            }
            return num to displayText
        }

        // 3. Filter dataRows if a search query is active
        val matchedRows = if (hasFilter && q != null) {
            dataRows.filter { r ->
                val (_, text) = getCellSortData(r)
                when (filterCondition) {
                    "equals" -> text.equals(q, ignoreCase = true)
                    "starts_with" -> text.startsWith(q, ignoreCase = true)
                    "not_contains" -> !text.contains(q, ignoreCase = true)
                    else -> text.contains(q, ignoreCase = true)
                }
            }
        } else {
            dataRows
        }

        // 4. Sort matchedRows if sorting is active
        val sortedRows = if (hasSort) {
            val isDesc = filterSort == "desc"
            matchedRows.sortedWith { r1, r2 ->
                val (num1, text1) = getCellSortData(r1)
                val (num2, text2) = getCellSortData(r2)

                val isBlank1 = text1.isEmpty()
                val isBlank2 = text2.isEmpty()

                // Blank cells ALWAYS go to the very bottom, in both ASC and DESC
                if (isBlank1 && isBlank2) return@sortedWith r1.compareTo(r2)
                if (isBlank1) return@sortedWith 1
                if (isBlank2) return@sortedWith -1

                val cmp = if (num1 != null && num2 != null) {
                    num1.compareTo(num2)
                } else {
                    text1.compareTo(text2, ignoreCase = true)
                }

                if (isDesc) -cmp else cmp
            }
        } else {
            matchedRows
        }

        val finalRows = if (hasFilter) {
            headerRows + sortedRows
        } else {
            headerRows + sortedRows + paddingRows
        }
        finalRows.toIntArray()
    }

    val anchorCell = selection?.let { currentSheet?.cell(it.anchorR, it.anchorC) }
    val anchorStyle = remember(anchorCell, uiState.version) {
        wb?.styles?.resolve(anchorCell?.style ?: 0)
    }

    fun anchorEditText(): String =
        selection?.let { s -> currentSheet?.cell(s.anchorR, s.anchorC) }?.let { wb?.editText(it) } ?: ""

    fun commitDraft() {
        val sel = selection ?: return
        if (!editingFormula) return
        editingFormula = false

        val validation = currentSheet?.validationAt(sel.anchorR, sel.anchorC)
        if (validation != null && validation.kind == ValidationKind.LIST && validation.strict && formulaText.isNotEmpty()) {
            val items = wb?.listItems(validation, currentSheet) ?: emptyList()
            if (items.isNotEmpty() && items.none { it.equals(formulaText, ignoreCase = true) }) {
                Toast.makeText(context, R.string.sheet_invalid_value, Toast.LENGTH_SHORT).show()
                return
            }
        }
        viewModel.setCellText(sheetIndex, sel.anchorR, sel.anchorC, formulaText)
    }

    fun selectCell(sel: GridSelection) {
        if (editingFormula) {
            commitDraft()
            focusManager.clearFocus()
        }
        dropdownState = null
        selection = sel
        formulaText = sel.let { s -> currentSheet?.cell(s.anchorR, s.anchorC)?.let { wb?.editText(it) } } ?: ""
    }

    fun styleSel(patch: StylePatch) {
        val sel = selection ?: return
        if (editingFormula) commitDraft()
        viewModel.applyStyle(sheetIndex, sel.range, patch)
    }

    // Save As document launcher
    val saveAsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    ) { uri ->
        if (uri != null) {
            viewModel.save(context, uri) { res ->
                when (res) {
                    is SpreadsheetViewModel.SaveResult.Saved -> {
                        Toast.makeText(context, R.string.sheet_saved, Toast.LENGTH_SHORT).show()
                        if (exitAfterSave) onBackPress()
                    }
                    else -> Toast.makeText(context, R.string.sheet_save_failed, Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            exitAfterSave = false
        }
    }

    fun performSave(asCopy: Boolean) {
        if (editingFormula) commitDraft()
        if (asCopy) {
            saveAsLauncher.launch(viewModel.suggestedSaveAsName())
            return
        }
        viewModel.save(context, null) { res ->
            when (res) {
                is SpreadsheetViewModel.SaveResult.Saved -> {
                    haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                    Toast.makeText(context, R.string.sheet_saved, Toast.LENGTH_SHORT).show()
                    if (exitAfterSave) onBackPress()
                }
                is SpreadsheetViewModel.SaveResult.NeedsSaveAs -> {
                    saveAsLauncher.launch(viewModel.suggestedSaveAsName())
                }
                is SpreadsheetViewModel.SaveResult.Failed -> {
                    exitAfterSave = false
                    Toast.makeText(context, res.message.ifBlank { context.getString(R.string.sheet_save_failed) }, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun handleBack() {
        when {
            showFilterDialog -> showFilterDialog = false
            dropdownState != null -> dropdownState = null
            activePanel != SpreadsheetPanel.NONE -> activePanel = SpreadsheetPanel.NONE
            searchVisible -> {
                searchVisible = false
                searchQuery = ""
            }
            editingFormula -> {
                editingFormula = false
                formulaText = anchorEditText()
                focusManager.clearFocus()
            }
            uiState.dirty -> {
                showUnsavedDialog = true
            }
            else -> onBackPress()
        }
    }

    val hasBackIntercept = showFilterDialog ||
        dropdownState != null ||
        activePanel != SpreadsheetPanel.NONE ||
        searchVisible ||
        editingFormula ||
        uiState.dirty

    BackHandler(enabled = hasBackIntercept) {
        when {
            showFilterDialog -> showFilterDialog = false
            dropdownState != null -> dropdownState = null
            activePanel != SpreadsheetPanel.NONE -> activePanel = SpreadsheetPanel.NONE
            searchVisible -> {
                searchVisible = false
                searchQuery = ""
            }
            editingFormula -> {
                editingFormula = false
                formulaText = anchorEditText()
                focusManager.clearFocus()
            }
            uiState.dirty -> {
                showUnsavedDialog = true
            }
        }
    }

    // Debounced search across the active worksheet
    val matches by produceState(emptyList<Pair<Int, Int>>(), currentSheet, searchQuery, uiState.version) {
        val q = searchQuery.trim()
        if (q.isBlank() || currentSheet == null || painter == null) {
            value = emptyList()
            return@produceState
        }
        kotlinx.coroutines.delay(180L)
        value = withContext(Dispatchers.Default) {
            val out = ArrayList<Pair<Int, Int>>()
            scan@ for ((r, row) in currentSheet.rows) {
                for ((c, cell) in row.cells) {
                    if (!isActive) return@withContext out
                    if (painter.display(cell).contains(q, ignoreCase = true)) {
                        out.add(r to c)
                        if (out.size >= 5000) break@scan
                    }
                }
            }
            out
        }
    }

    fun scrollToCell(r: Int, c: Int) {
        val lay = layout ?: return
        if (r >= (currentSheet?.frozenRows ?: 0)) {
            scope.launch {
                val rows = filteredScrollRows ?: lay.scrollRows
                val idx = rows.indexOf(r)
                if (idx >= 0) {
                    listState.animateScrollToItem((idx - 2).coerceAtLeast(0))
                } else {
                    listState.animateScrollToItem((lay.itemIndexOf(r) - 2).coerceAtLeast(0))
                }
            }
        }
        val target = ((lay.colX[c] - lay.frozenWidth) * density.density * zoom - 24 * density.density).coerceAtLeast(0f)
        if (c >= (currentSheet?.frozenCols ?: 0)) {
            scrollX.floatValue = target
        }
    }

    LaunchedEffect(matches) {
        currentMatchIndex = 0
        matches.firstOrNull()?.let { (r, c) -> scrollToCell(r, c) }
    }

    fun goToMatch(delta: Int) {
        if (matches.isEmpty()) return
        currentMatchIndex = ((currentMatchIndex + delta) % matches.size + matches.size) % matches.size
        matches[currentMatchIndex].let { (r, c) -> scrollToCell(r, c) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "${uiState.fileName}${if (uiState.dirty) " •" else ""}",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { handleBack() }) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    // Filter & Sort toggle
                    IconButton(onClick = {
                        if (selection != null) {
                            filterColumn = selection!!.anchorC
                        }
                        showFilterDialog = true
                    }) {
                        BadgedBox(
                            badge = {
                                if (isFilterActive) {
                                    Badge(containerColor = MaterialTheme.colorScheme.primary)
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.FilterList,
                                contentDescription = "Filter & Sort",
                                tint = if (isFilterActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    // Search toggle
                    IconButton(onClick = {
                        searchVisible = !searchVisible
                        if (!searchVisible) {
                            searchQuery = ""
                        }
                    }) {
                        Icon(
                            imageVector = if (searchVisible) Icons.Rounded.Close else Icons.Rounded.Search,
                            contentDescription = "Search",
                            tint = if (searchVisible) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    }

                    // Save button (when dirty or saving)
                    if (uiState.dirty || uiState.saving) {
                        IconButton(
                            onClick = { performSave(false) },
                            enabled = !uiState.saving
                        ) {
                            if (uiState.saving) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(
                                    imageVector = Icons.Rounded.Save,
                                    contentDescription = stringResource(R.string.sheet_save),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }

                    // Edit / Done toggle
                    if (uiState.editable) {
                        FilledTonalButton(
                            onClick = {
                                if (editingFormula) commitDraft()
                                editMode = !editMode
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.padding(horizontal = 4.dp)
                        ) {
                            Icon(
                                imageVector = if (editMode) Icons.Rounded.Check else Icons.Rounded.Edit,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = if (editMode) stringResource(R.string.sheet_done) else stringResource(R.string.sheet_edit),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    // Overflow menu
                    Box {
                        IconButton(onClick = { showOverflowMenu = true }) {
                            Icon(Icons.Rounded.MoreVert, contentDescription = "More options")
                        }
                        DropdownMenu(
                            expanded = showOverflowMenu,
                            onDismissRequest = { showOverflowMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.share)) },
                                leadingIcon = { Icon(Icons.Rounded.Share, contentDescription = null) },
                                onClick = {
                                    showOverflowMenu = false
                                    viewModel.shareableUri(context) { uri ->
                                        shareFile(context, uri ?: instance.uri)
                                    }
                                }
                            )

                            if (uiState.editable) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.sheet_save_as)) },
                                    leadingIcon = { Icon(Icons.Rounded.SaveAs, contentDescription = null) },
                                    onClick = {
                                        showOverflowMenu = false
                                        performSave(true)
                                    }
                                )
                            }

                            DropdownMenuItem(
                                text = { Text("Export to PDF") },
                                leadingIcon = { Icon(Icons.Rounded.PictureAsPdf, contentDescription = null) },
                                onClick = {
                                    showOverflowMenu = false
                                    Toast.makeText(context, "Exporting to PDF…", Toast.LENGTH_SHORT).show()
                                    viewModel.exportToPdf(context) { pdfUri ->
                                        if (pdfUri != null) {
                                            shareFile(context, pdfUri)
                                        } else {
                                            Toast.makeText(context, "Export failed", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            )

                            DropdownMenuItem(
                                text = { Text("Print") },
                                leadingIcon = { Icon(Icons.Rounded.Print, contentDescription = null) },
                                onClick = {
                                    showOverflowMenu = false
                                    viewModel.exportToPdf(context) { pdfUri ->
                                        if (pdfUri != null) {
                                            runCatching {
                                                val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager
                                                val pfd = context.contentResolver.openFileDescriptor(pdfUri, "r")
                                                if (printManager != null && pfd != null) {
                                                    printManager.print(
                                                        uiState.fileName,
                                                        object : PrintDocumentAdapter() {
                                                            override fun onLayout(
                                                                oldAttributes: PrintAttributes?,
                                                                newAttributes: PrintAttributes?,
                                                                cancellationSignal: android.os.CancellationSignal?,
                                                                callback: LayoutResultCallback?,
                                                                extras: android.os.Bundle?
                                                            ) {
                                                                val info = PrintDocumentInfo.Builder(uiState.fileName)
                                                                    .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                                                                    .build()
                                                                callback?.onLayoutFinished(info, true)
                                                            }

                                                            override fun onWrite(
                                                                pages: Array<out android.print.PageRange>?,
                                                                destination: android.os.ParcelFileDescriptor?,
                                                                cancellationSignal: android.os.CancellationSignal?,
                                                                callback: WriteResultCallback?
                                                            ) {
                                                                destination?.let { dest ->
                                                                    java.io.FileInputStream(pfd.fileDescriptor).use { input ->
                                                                        java.io.FileOutputStream(dest.fileDescriptor).use { output ->
                                                                            input.copyTo(output)
                                                                        }
                                                                    }
                                                                    callback?.onWriteFinished(arrayOf(android.print.PageRange.ALL_PAGES))
                                                                }
                                                            }
                                                        },
                                                        null
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            )

                            DropdownMenuItem(
                                text = { Text("Open with…") },
                                leadingIcon = { Icon(Icons.Rounded.OpenWith, contentDescription = null) },
                                onClick = {
                                    showOverflowMenu = false
                                    val sendIntent = Intent(Intent.ACTION_VIEW).apply {
                                        setDataAndType(instance.uri, context.contentResolver.getType(instance.uri) ?: "application/vnd.ms-excel")
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    runCatching {
                                        context.startActivity(Intent.createChooser(sendIntent, "Open with"))
                                    }
                                }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when {
                uiState.isLoading -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = "Loading spreadsheet…",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                uiState.error != null -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Rounded.ErrorOutline,
                            contentDescription = null,
                            modifier = Modifier.size(56.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = uiState.error ?: "Failed to load spreadsheet",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = onBackPress) {
                            Text("Go Back")
                        }
                    }
                }

                currentSheet != null && layout != null && painter != null -> {
                    Box(Modifier.fillMaxSize()) {
                        SpreadsheetGrid(
                            sheet = currentSheet,
                            layout = layout,
                            painter = painter,
                            version = uiState.version,
                            zoom = zoom,
                            listState = listState,
                            scrollX = scrollX,
                            selection = selection,
                            matches = matches,
                            currentMatch = matches.getOrNull(currentMatchIndex),
                            colors = gridColors,
                            editMode = editMode,
                            flingBehavior = rememberStackingFlingBehavior(),
                            onSelect = { sel -> selectCell(sel) },
                            onTapSelected = { r, c ->
                                if (uiState.editable) {
                                    if (!editMode) editMode = true
                                    editingFormula = true
                                }
                            },
                            onDropdown = { r, c, anchorInRoot ->
                                val v = currentSheet.validationAt(r, c)
                                if (v != null && wb != null) {
                                    val items = wb.listItems(v, currentSheet)
                                    if (items.isNotEmpty()) {
                                        haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                                        dropdownState = DropdownState(r, c, anchorInRoot, items, v.strict)
                                    }
                                }
                            },
                            onZoom = { newZoom ->
                                zoom = newZoom.coerceIn(0.5f, 2.5f)
                            },
                            onColumnResize = { col, widthChars ->
                                viewModel.setColWidth(sheetIndex, col, widthChars)
                            },
                            customScrollRows = filteredScrollRows,
                            modifier = Modifier.fillMaxSize()
                        )

                        // Active filter banner (below top of sheet)
                        if (isFilterActive) {
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.92f),
                                tonalElevation = 3.dp,
                                shape = RoundedCornerShape(20.dp),
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .padding(top = 8.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .clickable { showFilterDialog = true }
                                        .padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Rounded.FilterList,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    val colLetter = XlsxRefs.colLetter(filterColumn)
                                    val desc = buildString {
                                        append("Col $colLetter")
                                        if (!filterQuery.isNullOrBlank()) {
                                            append(": $filterCondition '$filterQuery'")
                                        }
                                        if (filterSort != "none") {
                                            append(" • ${if (filterSort == "asc") "A→Z" else "Z→A"}")
                                        }
                                    }
                                    Text(
                                        text = desc,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    IconButton(
                                        onClick = {
                                            filterQuery = null
                                            filterSort = "none"
                                            scope.launch { listState.scrollToItem(0) }
                                        },
                                        modifier = Modifier.size(20.dp)
                                    ) {
                                        Icon(
                                            Icons.Rounded.Close,
                                            contentDescription = "Clear filter",
                                            modifier = Modifier.size(14.dp),
                                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                }
                            }
                        }

                        // Vertical row scrubber
                        SheetRowScrubber(
                            listState = listState,
                            rowCount = layout.nRows,
                            accent = primaryColor,
                            modifier = Modifier.align(Alignment.CenterEnd)
                        )

                        // Search bar banner (anchored top)
                        AnimatedVisibility(
                            visible = searchVisible,
                            enter = slideInVertically { -it } + fadeIn(),
                            exit = slideOutVertically { -it } + fadeOut(),
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            SpreadsheetSearchBar(
                                query = searchQuery,
                                matchCount = matches.size,
                                currentMatchIndex = currentMatchIndex,
                                onQueryChange = { searchQuery = it },
                                onPrevMatch = { goToMatch(-1) },
                                onNextMatch = { goToMatch(1) },
                                onClose = {
                                    searchVisible = false
                                    searchQuery = ""
                                }
                            )
                        }

                        // Floating panels: Formula Bar, Edit Toolbar, and Sheet Tabs
                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .navigationBarsPadding()
                                .imePadding()
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            // Formula bar
                            FormulaBar(
                                ref = selection?.let { XlsxRefs.cellRef(it.anchorR, it.anchorC) } ?: "",
                                value = formulaText,
                                editable = uiState.editable,
                                editing = editingFormula,
                                focusRequester = formulaFocusRequester,
                                onValueChange = { formulaText = it },
                                onStartEdit = {
                                    if (uiState.editable && selection != null) {
                                        if (!editMode) editMode = true
                                        editingFormula = true
                                    }
                                },
                                onCopy = { text ->
                                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                    cm?.setPrimaryClip(ClipData.newPlainText("cell", text))
                                    Toast.makeText(context, R.string.copied_to_clipboard, Toast.LENGTH_SHORT).show()
                                },
                                onCommit = { commitDraft() },
                                onCancel = {
                                    editingFormula = false
                                    formulaText = anchorEditText()
                                    focusManager.clearFocus()
                                }
                            )

                            // Edit toolbar when edit mode is active
                            AnimatedVisibility(
                                visible = editMode && uiState.editable,
                                enter = slideInVertically { it } + fadeIn(),
                                exit = slideOutVertically { it } + fadeOut()
                            ) {
                                EditToolbar(
                                    accent = primaryColor,
                                    canUndo = uiState.canUndo,
                                    canRedo = uiState.canRedo,
                                    hasSelection = selection != null,
                                    bold = anchorStyle?.bold == true,
                                    italic = anchorStyle?.italic == true,
                                    underline = anchorStyle?.underline == true,
                                    strike = anchorStyle?.strike == true,
                                    panel = activePanel,
                                    onUndo = { viewModel.undo() },
                                    onRedo = { viewModel.redo() },
                                    onBold = { styleSel(StylePatch(bold = !(anchorStyle?.bold == true))) },
                                    onItalic = { styleSel(StylePatch(italic = !(anchorStyle?.italic == true))) },
                                    onUnderline = { styleSel(StylePatch(underline = !(anchorStyle?.underline == true))) },
                                    onStrike = { styleSel(StylePatch(strike = !(anchorStyle?.strike == true))) },
                                    onPanel = { p ->
                                        activePanel = if (activePanel == p) SpreadsheetPanel.NONE else p
                                    },
                                    onClear = {
                                        selection?.let { viewModel.clearRange(sheetIndex, it.range) }
                                    },
                                    onSaveAs = { performSave(true) }
                                )
                            }

                            // Sheet tabs (multi-sheet workbooks)
                            if (visibleSheetIndices.size > 1 && wb != null) {
                                SheetTabs(
                                    wb = wb,
                                    indices = visibleSheetIndices,
                                    selected = sheetIndex,
                                    onSelect = { newIdx ->
                                        if (editingFormula) commitDraft()
                                        sheetIndex = newIdx
                                        selection = null
                                        editingFormula = false
                                        filterQuery = null
                                        filterSort = "none"
                                        scrollX.floatValue = 0f
                                        scope.launch { listState.scrollToItem(0) }
                                    }
                                )
                            }
                        }

                        // Data validation dropdown overlay
                        val dd = dropdownState
                        if (dd != null) {
                            ValidationDropdown(
                                state = dd,
                                current = anchorEditText(),
                                onPick = { item ->
                                    viewModel.setCellText(sheetIndex, dd.r, dd.c, item)
                                    formulaText = item
                                    dropdownState = null
                                }
                            )
                        }
                    }
                }
            }

            // Bottom sheet modal for formatting panels
            if (activePanel != SpreadsheetPanel.NONE) {
                ModalBottomSheet(
                    onDismissRequest = { activePanel = SpreadsheetPanel.NONE },
                    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    tonalElevation = 6.dp
                ) {
                    when (activePanel) {
                        SpreadsheetPanel.FILL_COLOR -> {
                            val currentBg = anchorStyle?.fillArgb?.let { Color(it) } ?: Color.White
                            ExcelColorPicker(
                                color = currentBg,
                                onColorChange = { color ->
                                    styleSel(StylePatch(fill = ColorSpec.ofArgb(color.toArgb())))
                                },
                                onClear = {
                                    styleSel(StylePatch(clearFill = true))
                                },
                                clearLabel = stringResource(R.string.sheet_no_fill),
                                themeColors = OfficeThemeColors,
                                standardColors = OfficeStandardColors
                            )
                        }

                        SpreadsheetPanel.FONT_COLOR -> {
                            val currentFg = anchorStyle?.fontArgb?.let { Color(it) } ?: MaterialTheme.colorScheme.onSurface
                            ExcelColorPicker(
                                color = currentFg,
                                onColorChange = { color ->
                                    styleSel(StylePatch(fontColor = ColorSpec.ofArgb(color.toArgb())))
                                },
                                onClear = {
                                    styleSel(StylePatch(fontColor = ColorSpec(auto = true)))
                                },
                                clearLabel = stringResource(R.string.sheet_automatic),
                                themeColors = OfficeThemeColors,
                                standardColors = OfficeStandardColors
                            )
                        }

                        SpreadsheetPanel.ALIGN -> {
                            AlignPanel(
                                h = anchorStyle?.hAlign ?: "general",
                                v = anchorStyle?.vAlign ?: "bottom",
                                wrap = anchorStyle?.wrap == true,
                                onH = { h -> styleSel(StylePatch(hAlign = h)) },
                                onV = { v -> styleSel(StylePatch(vAlign = v)) },
                                onWrap = { w -> styleSel(StylePatch(wrap = w)) }
                            )
                        }

                        SpreadsheetPanel.BORDERS -> {
                            BordersPanel { preset ->
                                val sel = selection ?: return@BordersPanel
                                viewModel.applyBorders(sheetIndex, sel.range, preset)
                            }
                        }

                        SpreadsheetPanel.NUMBER -> {
                            NumberPanel(
                                current = anchorStyle?.numFmtCode ?: "General",
                                onPick = { code ->
                                    styleSel(StylePatch(numFmtCode = code))
                                }
                            )
                        }

                        SpreadsheetPanel.ROWS_COLS -> {
                            RowsColsPanel(
                                enabled = selection != null,
                                onAction = { actionIdx ->
                                    val sel = selection ?: return@RowsColsPanel
                                    when (actionIdx) {
                                        0 -> viewModel.insertLines(sheetIndex, Axis.ROW, sel.range.r1, sel.range.r2 - sel.range.r1 + 1)
                                        1 -> viewModel.insertLines(sheetIndex, Axis.ROW, sel.range.r2 + 1, sel.range.r2 - sel.range.r1 + 1)
                                        2 -> viewModel.deleteLines(sheetIndex, Axis.ROW, sel.range.r1, sel.range.r2 - sel.range.r1 + 1)
                                        3 -> viewModel.insertLines(sheetIndex, Axis.COL, sel.range.c1, sel.range.c2 - sel.range.c1 + 1)
                                        4 -> viewModel.insertLines(sheetIndex, Axis.COL, sel.range.c2 + 1, sel.range.c2 - sel.range.c1 + 1)
                                        5 -> viewModel.deleteLines(sheetIndex, Axis.COL, sel.range.c1, sel.range.c2 - sel.range.c1 + 1)
                                    }
                                    activePanel = SpreadsheetPanel.NONE
                                }
                            )
                        }

                        SpreadsheetPanel.NONE -> {}
                    }
                }
            }

            // Filter and Sort dialog
            if (showFilterDialog) {
                SpreadsheetFilterDialog(
                    columns = columnLabels,
                    currentColumn = filterColumn,
                    currentQuery = filterQuery,
                    currentCondition = filterCondition,
                    currentSort = filterSort,
                    firstRowIsHeader = filterFirstRowIsHeader,
                    onDismiss = { showFilterDialog = false },
                    onClear = {
                        filterQuery = null
                        filterSort = "none"
                        showFilterDialog = false
                        scope.launch { listState.scrollToItem(0) }
                    },
                    onApply = { col, q, cond, sort, firstRowHdr ->
                        filterColumn = col
                        filterQuery = q
                        filterCondition = cond
                        filterSort = sort
                        filterFirstRowIsHeader = firstRowHdr
                        showFilterDialog = false
                        scope.launch { listState.scrollToItem(0) }
                    }
                )
            }

            // Unsaved changes confirmation dialog
            SpreadsheetUnsavedChangesDialog(
                visible = showUnsavedDialog,
                onDiscard = {
                    showUnsavedDialog = false
                    onBackPress()
                },
                onCancel = {
                    showUnsavedDialog = false
                },
                onSave = {
                    showUnsavedDialog = false
                    exitAfterSave = true
                    performSave(false)
                }
            )
        }
    }
}
