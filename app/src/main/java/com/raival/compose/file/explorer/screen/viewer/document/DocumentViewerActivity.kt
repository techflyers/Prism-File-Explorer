package com.raival.compose.file.explorer.screen.viewer.document

import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.View
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.raival.compose.file.explorer.App.Companion.globalClass
import com.raival.compose.file.explorer.common.ConvertioApiKeyDialog
import com.raival.compose.file.explorer.common.ConvertioProgressDialog
import com.raival.compose.file.explorer.common.ConvertioService
import com.raival.compose.file.explorer.common.ui.SafeSurface
import com.raival.compose.file.explorer.screen.viewer.ViewerActivity
import com.raival.compose.file.explorer.screen.viewer.ViewerInstance
import com.raival.compose.file.explorer.theme.FileExplorerTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class DocumentViewerActivity : ViewerActivity(), com.ahmadullahpk.alldocumentreader.xs.system.IMainFrame {
    var control: com.ahmadullahpk.alldocumentreader.xs.system.MainControl? = null
    var onFileLoadedListener: ((View) -> Unit)? = null
    var onErrorListener: ((String) -> Unit)? = null

    // Implementation of IMainFrame
    override fun getActivity(): android.app.Activity = this
    
    override fun setFindBackForwardState(state: Boolean) {
        android.util.Log.d("DocViewer", "setFindBackForwardState: state = $state")
    }
    
    override fun getTopBarHeight(): Int = 0
    
    override fun onEventMethod(v: View?, e1: android.view.MotionEvent?, e2: android.view.MotionEvent?, xValue: Float, yValue: Float, eventMethodType: Byte): Boolean {
        android.util.Log.d("DocViewer", "onEventMethod: eventMethodType = $eventMethodType")
        return false
    }
    
    override fun isDrawPageNumber(): Boolean = true
    override fun isShowZoomingMsg(): Boolean = true
    override fun isPopUpErrorDlg(): Boolean = false
    override fun isShowPasswordDlg(): Boolean = true
    override fun isShowProgressBar(): Boolean = false
    override fun isShowFindDlg(): Boolean = true
    override fun isShowTXTEncodeDlg(): Boolean = true
    override fun getTXTDefaultEncode(): String = "GBK"
    override fun isTouchZoom(): Boolean = true
    override fun isZoomAfterLayoutForWord(): Boolean = true
    override fun getWordDefaultView(): Byte = 0
    
    override fun changeZoom() {
        android.util.Log.d("DocViewer", "changeZoom")
    }
    
    override fun changePage() {
        android.util.Log.d("DocViewer", "changePage")
    }
    
    override fun completeLayout() {
        android.util.Log.d("DocViewer", "completeLayout called")
    }
    
    override fun error(errorCode: Int) {
        android.util.Log.e("DocViewer", "error callback triggered: errorCode = $errorCode")
        val errorMsg = when(errorCode) {
            0 -> "Insufficient memory"
            1 -> "System crash"
            2 -> "Bad file format"
            3 -> "Old office document version not supported"
            4 -> "File parsing error"
            5 -> "RTF format is not supported"
            6 -> "Password protected document"
            7 -> "Incorrect password"
            8 -> "SD card read/write error"
            9 -> "SD card permission denied"
            10 -> "No space left on device"
            else -> "Unknown error (code: $errorCode)"
        }
        runOnUiThread {
            onErrorListener?.invoke(errorMsg)
        }
    }
    
    override fun fullScreen(fullscreen: Boolean) {
        android.util.Log.d("DocViewer", "fullScreen: $fullscreen")
    }
    
    override fun showProgressBar(visible: Boolean) {
        android.util.Log.d("DocViewer", "showProgressBar: $visible")
    }
    
    override fun updateViewImages(viewList: MutableList<Int>?) {
        android.util.Log.d("DocViewer", "updateViewImages: viewList = $viewList")
    }
    
    override fun isChangePage(): Boolean = true
    override fun setIgnoreOriginalSize(ignoreOriginalSize: Boolean) {}
    override fun isIgnoreOriginalSize(): Boolean = false
    override fun getPageListViewMovingPosition(): Byte = 0
    
    override fun doActionEvent(actionID: Int, obj: Any?): Boolean {
        android.util.Log.d("DocViewer", "doActionEvent: actionID = $actionID, obj = $obj")
        if (actionID == 0) {
            onBackPressedDispatcher.onBackPressed()
            return true
        }
        return false
    }
    
    override fun updateToolsbarStatus() {
        android.util.Log.d("DocViewer", "updateToolsbarStatus")
    }
    
    override fun openFileFinish() {
        val view = control?.view
        android.util.Log.d("DocViewer", "openFileFinish: view = $view")
        if (view != null) {
            runOnUiThread {
                onFileLoadedListener?.invoke(view)
            }
        } else {
            android.util.Log.e("DocViewer", "openFileFinish: control view is null!")
        }
    }
    
    override fun getBottomBarHeight(): Int = 0
    override fun getAppName(): String = getString(com.raival.compose.file.explorer.R.string.app_name)
    override fun getLocalString(str: String?): String = com.ahmadullahpk.alldocumentreader.xs.res.ResKit.instance().getLocalString(str) ?: str ?: ""
    override fun setWriteLog(z: Boolean) {}
    override fun isWriteLog(): Boolean = true
    override fun setThumbnail(z: Boolean) {}
    override fun getViewBackground(): Any = -7829368
    override fun isThumbnail(): Boolean = false
    override fun getTemporaryDirectory(): File = cacheDir ?: filesDir
    
    override fun dispose() {
        control?.dispose()
        control = null
        onFileLoadedListener = null
        onErrorListener = null
    }

    override fun onDestroy() {
        dispose()
        super.onDestroy()
    }

    fun getFilePathFromUri(uri: Uri): String? {
        if (uri.scheme == "file") {
            return uri.path
        }
        try {
            val resolvedPath = com.raival.compose.file.explorer.common.resolveUriToPath(globalClass, uri)
            if (resolvedPath.startsWith("/") && File(resolvedPath).exists()) {
                return resolvedPath
            }
        } catch (_: Exception) {}

        return try {
            val fileName = getFileNameFromUri(uri) ?: "temp_doc"
            val tempDir = File(cacheDir, "temp_docs").apply { mkdirs() }
            val tempFile = File(tempDir, fileName)
            contentResolver.openInputStream(uri)?.use { input ->
                tempFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            tempFile.absolutePath
        } catch (e: Exception) {
            com.raival.compose.file.explorer.App.logger.logError(e)
            null
        }
    }

    private fun getFileNameFromUri(uri: Uri): String? {
        if (uri.scheme == "file") {
            return File(uri.path ?: "").name
        }
        var name: String? = null
        try {
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1) {
                        name = cursor.getString(nameIndex)
                    }
                }
            }
        } catch (_: Exception) {}
        return name ?: uri.lastPathSegment
    }

    override fun onCreateNewInstance(uri: Uri, uid: String): ViewerInstance {
        return DocumentViewerInstance(uri, uid)
    }

    override fun onReady(instance: ViewerInstance) {
        if (instance !is DocumentViewerInstance) {
            globalClass.showMsg("Invalid document file")
            finish()
            return
        }

        setContent {
            FileExplorerTheme {
                SafeSurface(false) {
                    DocumentViewerScreen(
                        instance = instance,
                        onBackPress = { onBackPressedDispatcher.onBackPressed() }
                    )
                }
            }
        }
    }
}

class DocumentViewerInstance(
    override val uri: Uri,
    override val id: String
) : ViewerInstance {
    override fun onClose() {}
}

private data class SheetData(val name: String, val rows: List<List<String>>)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DocumentViewerScreen(
    instance: DocumentViewerInstance,
    onBackPress: () -> Unit
) {
    var renderedView by remember { mutableStateOf<View?>(null) }
    var excelSheets by remember { mutableStateOf<List<SheetData>?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }

    val context = androidx.compose.ui.platform.LocalContext.current
    val activity = context as DocumentViewerActivity

    val filePath = remember {
        activity.getFilePathFromUri(instance.uri)
    }
    val fileName = remember {
        if (filePath != null) File(filePath).name else instance.uri.lastPathSegment ?: "document"
    }
    val extension = remember {
        filePath?.substringAfterLast('.', "")?.lowercase() ?: ""
    }
    val isExcelFile = remember(extension) {
        extension == "xls" || extension == "xlsx"
    }

    // Excel filter and sorting state
    var selectedSheet by remember { mutableStateOf(0) }
    var filterColumnIndex by remember { mutableStateOf<Int?>(null) }
    var filterQuery by remember { mutableStateOf<String?>(null) }
    var filterCondition by remember { mutableStateOf("contains") }
    var sortType by remember { mutableStateOf("none") }
    var sortColumnIndex by remember { mutableStateOf<Int?>(null) }
    var showFilterDialog by remember { mutableStateOf(false) }

    LaunchedEffect(filePath) {
        if (filePath == null) {
            loadError = "Failed to resolve file path"
            return@LaunchedEffect
        }

        if (isExcelFile) {
            withContext(Dispatchers.IO) {
                try {
                    val sheetsList = parseExcelFile(filePath)
                    excelSheets = sheetsList
                } catch (e: Exception) {
                    loadError = e.message ?: "Failed to parse Excel file"
                }
            }
        } else {
            activity.onFileLoadedListener = { view ->
                android.util.Log.d("DocViewer", "onFileLoadedListener invoked: view = $view")
                renderedView = view
            }
            activity.onErrorListener = { error ->
                android.util.Log.e("DocViewer", "onErrorListener invoked: error = $error")
                loadError = error
            }

            try {
                android.util.Log.d("DocViewer", "Initializing MainControl and opening file: $filePath")
                val controlInstance = com.ahmadullahpk.alldocumentreader.xs.system.MainControl(activity)
                activity.control = controlInstance
                controlInstance.openFile(filePath)
            } catch (e: Exception) {
                android.util.Log.e("DocViewer", "Exception while opening document", e)
                loadError = e.message ?: "Failed to open document"
            }
        }
    }

    val activeSheet = excelSheets?.getOrNull(selectedSheet)
    val originalRows = activeSheet?.rows ?: emptyList()

    val processedRows = remember(originalRows, filterColumnIndex, filterQuery, filterCondition, sortType, sortColumnIndex) {
        var result = originalRows.mapIndexed { idx, row -> idx to row }

        // 1. Filter rows
        if (filterColumnIndex != null && !filterQuery.isNullOrBlank()) {
            val colIdx = filterColumnIndex!!
            val query = filterQuery!!.lowercase()
            result = result.filter { (_, row) ->
                val cellValue = row.getOrNull(colIdx)?.lowercase() ?: ""
                when (filterCondition) {
                    "equals" -> cellValue == query
                    else -> cellValue.contains(query)
                }
            }
        }

        // 2. Sort rows
        if (sortColumnIndex != null && sortType != "none") {
            val colIdx = sortColumnIndex!!
            result = if (sortType == "asc") {
                result.sortedBy { (_, row) -> row.getOrNull(colIdx) ?: "" }
            } else {
                result.sortedByDescending { (_, row) -> row.getOrNull(colIdx) ?: "" }
            }
        }

        result.map { it.second }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = fileName,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackPress) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (isExcelFile && excelSheets != null) {
                        IconButton(onClick = { showFilterDialog = true }) {
                            Icon(
                                imageVector = Icons.Rounded.FilterList,
                                contentDescription = "Filter Columns",
                                tint = if (filterColumnIndex != null || sortType != "none") MaterialTheme.colorScheme.primary else LocalContentColor.current
                            )
                        }
                    }
                    if (filePath != null) {
                        IconButton(onClick = {
                            ConvertioService.convertToPdf(globalClass, filePath)
                        }) {
                            Icon(Icons.Rounded.PictureAsPdf, contentDescription = "Convert to PDF")
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
        ) {
            when {
                loadError != null -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            "Error",
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(loadError!!, textAlign = TextAlign.Center)
                    }
                }
                isExcelFile -> {
                    if (excelSheets == null) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(48.dp))
                            Spacer(Modifier.height(16.dp))
                            Text("Loading spreadsheet…", style = MaterialTheme.typography.bodyLarge)
                        }
                    } else {
                        ExcelView(
                            sheets = excelSheets!!,
                            selectedSheet = selectedSheet,
                            onSheetSelected = {
                                selectedSheet = it
                                // Reset filter/sort on sheet switch
                                filterColumnIndex = null
                                filterQuery = null
                                sortType = "none"
                                sortColumnIndex = null
                            },
                            processedRows = processedRows,
                            filterActive = filterColumnIndex != null || sortType != "none",
                            onFilterClick = { showFilterDialog = true }
                        )
                    }
                }
                renderedView == null -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(48.dp))
                        Spacer(Modifier.height(16.dp))
                        Text("Loading layout…", style = MaterialTheme.typography.bodyLarge)
                    }
                }
                else -> {
                    AndroidView(
                        factory = { ctx ->
                            android.widget.LinearLayout(ctx).apply {
                                layoutParams = android.view.ViewGroup.LayoutParams(
                                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                                    android.view.ViewGroup.LayoutParams.MATCH_PARENT
                                )
                                orientation = android.widget.LinearLayout.VERTICAL
                                (renderedView!!.parent as? android.view.ViewGroup)?.removeView(renderedView)
                                addView(
                                    renderedView,
                                    android.widget.LinearLayout.LayoutParams(
                                        android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                                        android.widget.LinearLayout.LayoutParams.MATCH_PARENT
                                    )
                                )
                            }
                        },
                        modifier = Modifier.fillMaxSize(),
                        update = { view ->
                            view.requestLayout()
                            view.invalidate()
                        }
                    )
                }
            }
        }
    }

    // Filter Dialog
    if (showFilterDialog && activeSheet != null) {
        val maxCols = originalRows.maxOfOrNull { it.size } ?: 0
        ExcelFilterDialog(
            maxCols = maxCols,
            initialColumn = filterColumnIndex,
            initialQuery = filterQuery,
            initialCondition = filterCondition,
            initialSort = sortType,
            onDismiss = { showFilterDialog = false },
            onApply = { col, q, cond, sort ->
                filterColumnIndex = col
                filterQuery = q
                filterCondition = cond
                sortType = sort
                sortColumnIndex = if (sort == "none") null else col
                showFilterDialog = false
            }
        )
    }

    // Convertio dialogs
    if (ConvertioService.showApiKeyDialog) {
        ConvertioApiKeyDialog(
            onDismiss = { ConvertioService.showApiKeyDialog = false },
            onConfirm = { ConvertioService.onApiKeyConfirmed(it) }
        )
    }
    if (ConvertioService.showProgressDialog) {
        ConvertioProgressDialog(onCancel = { ConvertioService.cancelConversion() })
        LaunchedEffect(Unit) {
            val ctx = ConvertioService.getPendingContext()
            val path = ConvertioService.getPendingFilePath()
            if (ctx != null && path != null) {
                 ConvertioService.executeConversion(ctx, path, globalClass.preferencesManager.convertioApiKey)
            }
        }
    }
}

private fun parseExcelFile(filePath: String): List<SheetData> {
    android.util.Log.d("DocViewer", "parseExcelFile: Start parsing $filePath")
    val lower = filePath.lowercase()
    val workbook = try {
        if (lower.endsWith(".xlsx")) {
            android.util.Log.d("DocViewer", "parseExcelFile: Initializing XLSXReader")
            val reader = com.ahmadullahpk.alldocumentreader.xs.fc.xls.XLSXReader(null, filePath)
            // getModel() internally triggers WorkbookReader.read() which spawns async SheetThread(s).
            // Do NOT call WorkbookReader.instance().readSheet() manually — the singleton's mutable
            // state (sheetIndexList, book, iReader) gets disposed by SheetThread on any parse error
            // (chart sheets, graphical tables, etc.), causing NPE on subsequent calls.
            val book = reader.getModel() as com.ahmadullahpk.alldocumentreader.xs.ss.model.baseModel.Workbook
            val sheetCount = book.sheetCount
            android.util.Log.d("DocViewer", "parseExcelFile: Waiting for $sheetCount XLSX sheets to be loaded by background SheetThread")
            // Wait for the async SheetThread to mark each sheet accomplished (max 15s total).
            val deadlineMs = System.currentTimeMillis() + 15_000L
            for (i in 0 until sheetCount) {
                val sheet = book.getSheet(i) ?: continue
                // Chart sheets / graphical-table sheets may never reach State_Accomplished;
                // break the wait early for those so they don't block the whole load.
                while (!sheet.isAccomplished && System.currentTimeMillis() < deadlineMs) {
                    Thread.sleep(50)
                }
                android.util.Log.d("DocViewer", "parseExcelFile: Sheet $i accomplished=${sheet.isAccomplished}, type=${sheet.sheetType}")
            }
            book
        } else {
            android.util.Log.d("DocViewer", "parseExcelFile: Initializing XLSReader")
            val reader = com.ahmadullahpk.alldocumentreader.xs.fc.xls.XLSReader(null, filePath)
            val book = reader.getModel() as com.ahmadullahpk.alldocumentreader.xs.ss.model.baseModel.Workbook
            val sheetCount = book.sheetCount
            android.util.Log.d("DocViewer", "parseExcelFile: Triggering processSheet for $sheetCount XLS sheets")
            for (i in 0 until sheetCount) {
                val sheetObj = book.getSheet(i)
                if (sheetObj is com.ahmadullahpk.alldocumentreader.xs.ss.model.XLSModel.ASheet) {
                    try {
                        sheetObj.processSheet(reader)
                    } catch (e: Exception) {
                        android.util.Log.e("DocViewer", "parseExcelFile: Error loading XLS sheet index $i", e)
                    }
                }
            }
            book
        }
    } catch (e: Exception) {
        android.util.Log.e("DocViewer", "parseExcelFile: Failed to load workbook model", e)
        throw e
    }

    android.util.Log.d("DocViewer", "parseExcelFile: Workbook loaded. Sheet count: ${workbook.sheetCount}")
    val sheets = mutableListOf<SheetData>()
    val sheetCount = workbook.sheetCount
    for (i in 0 until sheetCount) {
        val sheet = workbook.getSheet(i) ?: continue
        val sheetName = sheet.sheetName ?: "Sheet ${i + 1}"

        // Skip chart/graphical sheets — they have no cell data to display
        if (sheet.sheetType == com.ahmadullahpk.alldocumentreader.xs.ss.model.baseModel.Sheet.TYPE_CHARTSHEET) {
            android.util.Log.d("DocViewer", "parseExcelFile: Skipping chart sheet index $i ($sheetName)")
            sheets.add(SheetData(name = "\uD83D\uDCCA $sheetName (Chart)", rows = emptyList()))
            continue
        }

        val firstRow = sheet.firstRowNum
        val lastRow = sheet.lastRowNum
        android.util.Log.d("DocViewer", "parseExcelFile: Processing sheet $i ($sheetName). Rows: $firstRow..$lastRow")
        val rows = mutableListOf<List<String>>()

        for (r in firstRow..lastRow) {
            val rowObj = sheet.getRow(r)
            val cells = mutableListOf<String>()
            if (rowObj != null) {
                var maxCol = 0
                val cellCol = rowObj.cellCollection()
                if (cellCol != null) {
                    for (c in cellCol) {
                        maxCol = maxOf(maxCol, c.colNumber)
                    }
                }
                maxCol = minOf(maxCol, 256)
                for (c in 0..maxCol) {
                    val cellObj = rowObj.getCell(c)
                    val cellValue = if (cellObj != null) {
                        try {
                            getCellValueAsString(cellObj, workbook)
                        } catch (e: Exception) {
                            android.util.Log.w("DocViewer", "getCellValueAsString failed at row=$r col=$c", e)
                            ""
                        }
                    } else {
                        ""
                    }
                    cells.add(cellValue)
                }
            }
            rows.add(cells)
        }
        android.util.Log.d("DocViewer", "parseExcelFile: Sheet $sheetName done. Rows: ${rows.size}")
        sheets.add(SheetData(name = sheetName, rows = rows))
    }
    android.util.Log.d("DocViewer", "parseExcelFile: All sheets parsed successfully.")
    return sheets
}

private fun getCellValueAsString(
    cell: com.ahmadullahpk.alldocumentreader.xs.ss.model.baseModel.Cell,
    workbook: com.ahmadullahpk.alldocumentreader.xs.ss.model.baseModel.Workbook
): String {
    val cellType = cell.cellType.toInt()
    android.util.Log.d("DocViewer", "getCellValueAsString: col=${cell.colNumber}, row=${cell.rowNumber}, type=$cellType, stringCellValueIndex=${cell.stringCellValueIndex}")
    return when {
        cellType == 0 || cellType in 6..11 -> { // CELL_TYPE_NUMERIC and all numeric subtypes
            val value = cell.numberValue
            if (value.isNaN()) {
                ""
            } else if (value % 1.0 == 0.0) {
                value.toLong().toString()
            } else {
                value.toString()
            }
        }
        cellType == 1 -> { // CELL_TYPE_STRING
            val index = cell.stringCellValueIndex
            val item = workbook.getSharedItem(index)
            if (item is String) {
                item
            } else if (item is com.ahmadullahpk.alldocumentreader.xs.simpletext.model.SectionElement) {
                item.getText(null)
            } else if (item != null) {
                item.toString()
            } else {
                ""
            }
        }
        cellType == 2 -> cell.cellFormulaValue ?: "" // CELL_TYPE_FORMULA
        cellType == 3 -> "" // CELL_TYPE_BLANK
        cellType == 4 -> cell.booleanValue.toString() // CELL_TYPE_BOOLEAN
        cellType == 5 -> cell.errorCodeValue.toString() // CELL_TYPE_ERROR
        else -> ""
    }
}

private fun getColumnLetter(colIndex: Int): String {
    var letter = ""
    var temp = colIndex
    while (temp >= 0) {
        letter = ((temp % 26) + 65).toChar() + letter
        temp = (temp / 26) - 1
    }
    return letter
}

@Composable
private fun ExcelCell(
    text: String,
    isHeader: Boolean = false,
    width: Dp = 120.dp,
    height: Dp = 36.dp
) {
    Box(
        modifier = Modifier
            .width(width)
            .height(height)
            .background(
                if (isHeader) MaterialTheme.colorScheme.surfaceVariant
                else MaterialTheme.colorScheme.surface
            )
            .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            fontSize = 12.sp,
            maxLines = if (height > 36.dp) Int.MAX_VALUE else 1,
            overflow = TextOverflow.Ellipsis,
            fontWeight = if (isHeader) FontWeight.Bold else FontWeight.Normal,
            color = if (isHeader) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun ExcelView(
    sheets: List<SheetData>,
    selectedSheet: Int,
    onSheetSelected: (Int) -> Unit,
    processedRows: List<List<String>>,
    filterActive: Boolean,
    onFilterClick: () -> Unit
) {
    val defaultColWidth = 120.dp
    val defaultRowHeight = 36.dp
    val minColWidth = 40.dp
    val minRowHeight = 24.dp
    val headerHeight = 36.dp
    val density = LocalDensity.current

    // Per-sheet resize state — resets automatically when selectedSheet changes
    val columnWidths = remember(selectedSheet) { mutableStateMapOf<Int, Dp>() }
    val rowHeights  = remember(selectedSheet) { mutableStateMapOf<Int, Dp>() }

    fun colWidth(c: Int): Dp  = columnWidths[c] ?: defaultColWidth
    fun rowHeight(r: Int): Dp = rowHeights[r]  ?: defaultRowHeight

    val handleColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)

    Column(modifier = Modifier.fillMaxSize()) {
        // Tab row for multiple sheets
        if (sheets.size > 1) {
            ScrollableTabRow(
                selectedTabIndex = selectedSheet,
                edgePadding = 16.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                sheets.forEachIndexed { index, sheet ->
                    Tab(
                        selected = selectedSheet == index,
                        onClick = { onSheetSelected(index) },
                        text = { Text(sheet.name) }
                    )
                }
            }
        }

        if (processedRows.isNotEmpty()) {
            val maxCols = processedRows.maxOf { it.size }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .horizontalScroll(rememberScrollState())
            ) {
                // ── Header row (column letters + resize handles) ──────────────
                Row {
                    // Top-left corner spacer
                    Box(
                        modifier = Modifier
                            .width(40.dp)
                            .height(headerHeight)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
                    )

                    for (c in 0 until maxCols) {
                        // Wrap each column header in a Box so we can overlay the drag handle
                        Box(modifier = Modifier.width(colWidth(c)).height(headerHeight)) {
                            ExcelCell(
                                text = getColumnLetter(c),
                                isHeader = true,
                                width = colWidth(c),
                                height = headerHeight
                            )
                            // Right-edge drag handle for column resize
                            Box(
                                modifier = Modifier
                                    .align(Alignment.CenterEnd)
                                    .width(10.dp)
                                    .fillMaxHeight()
                                    .pointerInput(c) {
                                        detectDragGestures { change, dragAmount ->
                                            change.consume()
                                            val newW = with(density) {
                                                (colWidth(c).toPx() + dragAmount.x)
                                                    .coerceAtLeast(minColWidth.toPx())
                                                    .toDp()
                                            }
                                            columnWidths[c] = newW
                                        }
                                    }
                            ) {
                                // Thin visual indicator
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.CenterEnd)
                                        .width(2.dp)
                                        .fillMaxHeight(0.55f)
                                        .background(handleColor)
                                )
                            }
                        }
                    }
                }

                // ── Data rows ────────────────────────────────────────────────
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    itemsIndexed(processedRows) { rIdx, row ->
                        Row {
                            // Row-number cell with bottom-edge drag handle
                            Box(
                                modifier = Modifier
                                    .width(40.dp)
                                    .height(rowHeight(rIdx))
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                        .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = (rIdx + 1).toString(),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                // Bottom-edge drag handle for row resize
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .fillMaxWidth()
                                        .height(10.dp)
                                        .pointerInput(rIdx) {
                                            detectDragGestures { change, dragAmount ->
                                                change.consume()
                                                val newH = with(density) {
                                                    (rowHeight(rIdx).toPx() + dragAmount.y)
                                                        .coerceAtLeast(minRowHeight.toPx())
                                                        .toDp()
                                                }
                                                rowHeights[rIdx] = newH
                                            }
                                        }
                                ) {
                                    // Thin visual indicator
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.BottomCenter)
                                            .fillMaxWidth(0.55f)
                                            .height(2.dp)
                                            .background(handleColor)
                                    )
                                }
                            }

                            // Data cells using dynamic width + height
                            for (c in 0 until maxCols) {
                                val cellVal = row.getOrNull(c) ?: ""
                                ExcelCell(
                                    text = cellVal,
                                    width = colWidth(c),
                                    height = rowHeight(rIdx)
                                )
                            }
                        }
                    }
                }
            }
        } else {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("No data or all rows filtered out", style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExcelFilterDialog(
    maxCols: Int,
    initialColumn: Int?,
    initialQuery: String?,
    initialCondition: String,
    initialSort: String,
    onDismiss: () -> Unit,
    onApply: (column: Int?, query: String?, condition: String, sort: String) -> Unit
) {
    var selectedCol by remember { mutableStateOf(initialColumn ?: 0) }
    var queryText by remember { mutableStateOf(initialQuery ?: "") }
    var condition by remember { mutableStateOf(initialCondition) }
    var sortType by remember { mutableStateOf(initialSort) }
    var expanded by remember { mutableStateOf(false) }

    val columns = remember(maxCols) {
        List(maxCols) { col -> "${getColumnLetter(col)} (Col ${col + 1})" }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Filter & Sort Columns") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Column Dropdown
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { expanded = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(columns.getOrNull(selectedCol) ?: "Select Column")
                    }
                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false },
                        modifier = Modifier.fillMaxWidth(0.8f)
                    ) {
                        columns.forEachIndexed { idx, name ->
                            DropdownMenuItem(
                                text = { Text(name) },
                                onClick = {
                                    selectedCol = idx
                                    expanded = false
                                }
                            )
                        }
                    }
                }

                // Filter Query
                OutlinedTextField(
                    value = queryText,
                    onValueChange = { queryText = it },
                    label = { Text("Filter text") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Condition
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Filter Type", style = MaterialTheme.typography.titleSmall)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = condition == "contains", onClick = { condition = "contains" })
                            Text("Contains")
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = condition == "equals", onClick = { condition = "equals" })
                            Text("Equals")
                        }
                    }
                }

                // Sorting
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Sort Order", style = MaterialTheme.typography.titleSmall)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = sortType == "none", onClick = { sortType = "none" })
                            Text("None")
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = sortType == "asc", onClick = { sortType = "asc" })
                            Text("Asc")
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = sortType == "desc", onClick = { sortType = "desc" })
                            Text("Desc")
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onApply(
                        selectedCol,
                        if (queryText.isBlank()) null else queryText,
                        condition,
                        sortType
                    )
                }
            ) {
                Text("Apply")
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    onApply(null, null, "contains", "none")
                }
            ) {
                Text("Clear Filter")
            }
        }
    )
}
