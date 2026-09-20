package com.techflyers.compose.file.explorer.screen.viewer.document

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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.FitScreen
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material.icons.rounded.TableChart
import androidx.compose.material.icons.rounded.ZoomIn
import androidx.compose.material.icons.rounded.ZoomOut
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.techflyers.compose.file.explorer.common.copyToClipboard
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlin.math.roundToInt
import com.techflyers.compose.file.explorer.App.Companion.globalClass
import com.techflyers.compose.file.explorer.common.ConvertioApiKeyDialog
import com.techflyers.compose.file.explorer.common.ConvertioProgressDialog
import com.techflyers.compose.file.explorer.common.ConvertioService
import com.techflyers.compose.file.explorer.common.ui.SafeSurface
import com.techflyers.compose.file.explorer.screen.viewer.ViewerActivity
import com.techflyers.compose.file.explorer.screen.viewer.ViewerInstance
import com.techflyers.compose.file.explorer.theme.FileExplorerTheme
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
    override fun getAppName(): String = getString(com.techflyers.compose.file.explorer.R.string.app_name)
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
            val resolvedPath = com.techflyers.compose.file.explorer.common.resolveUriToPath(globalClass, uri)
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
            com.techflyers.compose.file.explorer.App.logger.logError(e)
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
                    // Open With
                    IconButton(onClick = {
                        val openIntent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                            data = instance.uri
                            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        activity.startActivity(
                            android.content.Intent.createChooser(
                                openIntent,
                                activity.getString(com.techflyers.compose.file.explorer.R.string.open_with)
                            )
                        )
                    }) {
                        Icon(Icons.Rounded.OpenInNew, contentDescription = "Open with")
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
                            onFilterClick = { showFilterDialog = true },
                            onColumnFilter = { col ->
                                filterColumnIndex = col
                                showFilterDialog = true
                            },
                            onColumnSort = { col, sort ->
                                sortColumnIndex = col
                                sortType = sort
                            }
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
private fun ExcelView(
    sheets: List<SheetData>,
    selectedSheet: Int,
    onSheetSelected: (Int) -> Unit,
    processedRows: List<List<String>>,
    filterActive: Boolean,
    onFilterClick: () -> Unit,
    onColumnFilter: ((Int) -> Unit)? = null,
    onColumnSort: ((Int, String) -> Unit)? = null
) {
    var zoomScale by remember(selectedSheet) { mutableFloatStateOf(1.0f) }
    var showZoomMenu by remember { mutableStateOf(false) }
    var showCornerMenu by remember { mutableStateOf(false) }

    // Dialog state
    var resizeColumnTarget by remember { mutableStateOf<Int?>(null) }
    var resizeRowTarget by remember { mutableStateOf<Int?>(null) }

    val baseRowHeaderWidth = 46.dp
    val baseHeaderHeight = 32.dp
    val defaultRowHeight = 32.dp

    val scaledRowHeaderWidth = (baseRowHeaderWidth * zoomScale).coerceIn(28.dp, 80.dp)
    val scaledHeaderHeight = (baseHeaderHeight * zoomScale).coerceIn(20.dp, 56.dp)

    val cellFontSize = (12f * zoomScale).coerceIn(7f, 20f).sp
    val headerFontSize = (11f * zoomScale).coerceIn(7f, 18f).sp
    val rowFontSize = (10f * zoomScale).coerceIn(6.5f, 16f).sp

    // Selected cell: Pair(rowIndex, colIndex)
    var selectedCell by remember(selectedSheet) { mutableStateOf<Pair<Int, Int>?>(null) }
    var expandedCell by remember(selectedSheet) { mutableStateOf<Pair<Int, Int>?>(null) }
    var showCellDetailDialog by remember(selectedSheet) { mutableStateOf(false) }
    var activeColumnMenu by remember(selectedSheet) { mutableStateOf<Int?>(null) }
    var activeRowMenu by remember(selectedSheet) { mutableStateOf<Int?>(null) }

    val maxCols = remember(processedRows) {
        if (processedRows.isNotEmpty()) processedRows.maxOf { it.size } else 0
    }

    // Base column widths (without zoom scale)
    val columnWidths = remember(selectedSheet, maxCols) {
        mutableStateMapOf<Int, Dp>().apply {
            for (c in 0 until maxCols) {
                val maxChars = processedRows.take(150).maxOfOrNull { it.getOrNull(c)?.length ?: 0 } ?: 0
                val colLetterLen = getColumnLetter(c).length
                val longest = maxOf(maxChars, colLetterLen)
                val autoWidth = (longest * 8.5f + 28f).dp.coerceIn(60.dp, 300.dp)
                put(c, autoWidth)
            }
        }
    }

    // Base row heights (without zoom scale)
    val rowHeights = remember(selectedSheet) {
        mutableStateMapOf<Int, Dp>()
    }

    fun baseColWidth(c: Int): Dp = columnWidths[c] ?: 80.dp
    fun scaledColWidth(c: Int): Dp = baseColWidth(c) * zoomScale

    fun baseRowHeight(r: Int): Dp = rowHeights[r] ?: defaultRowHeight
    fun scaledRowHeight(r: Int): Dp = baseRowHeight(r) * zoomScale

    val selectedValue = selectedCell?.let { (r, c) -> processedRows.getOrNull(r)?.getOrNull(c) ?: "" } ?: ""
    val selectedCellRef = selectedCell?.let { (r, c) -> "${getColumnLetter(c)}${r + 1}" } ?: ""

    val horizontalScrollState = rememberScrollState()
    val lazyListState = rememberLazyListState()
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp

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
                        onClick = {
                            selectedCell = null
                            expandedCell = null
                            showCellDetailDialog = false
                            onSheetSelected(index)
                        },
                        text = { Text(sheet.name) }
                    )
                }
            }
        }

        // ── Cell Formula / Inspector & Zoom Bar ───────────────────────────
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
            border = androidx.compose.foundation.BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Coordinate badge
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = if (selectedCell != null) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                    border = androidx.compose.foundation.BorderStroke(
                        0.5.dp,
                        if (selectedCell != null) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outlineVariant
                    ),
                    modifier = Modifier.clickable(enabled = selectedCell != null && selectedValue.isNotEmpty()) {
                        showCellDetailDialog = true
                    }
                ) {
                    Text(
                        text = if (selectedCell != null) selectedCellRef else "fx",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (selectedCell != null) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Cell content / formula preview
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clickable(enabled = selectedCell != null && selectedValue.isNotEmpty()) {
                            showCellDetailDialog = true
                        }
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (selectedCell != null) {
                            selectedValue.ifEmpty { "«Empty»" }
                        } else {
                            "Tap a cell • Pinch or +/- to zoom"
                        },
                        modifier = Modifier.weight(1f, fill = false),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = if (selectedCell != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                    if (selectedCell != null && selectedValue.isNotEmpty()) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Rounded.FitScreen,
                            contentDescription = "View full text",
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                        )
                    }
                }

                // Zoom Controls
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 4.dp)
                ) {
                    IconButton(
                        onClick = { zoomScale = (zoomScale - 0.15f).coerceAtLeast(0.35f) },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ZoomOut,
                            contentDescription = "Zoom Out",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Box {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = androidx.compose.foundation.BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier.clickable { showZoomMenu = true }
                        ) {
                            Text(
                                text = "${(zoomScale * 100).roundToInt()}%",
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        DropdownMenu(
                            expanded = showZoomMenu,
                            onDismissRequest = { showZoomMenu = false }
                        ) {
                            listOf(0.4f, 0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f).forEach { scale ->
                                val pct = (scale * 100).roundToInt()
                                val label = when (pct) {
                                    40 -> "40% (Overview)"
                                    50 -> "50% (Wide View)"
                                    75 -> "75% (Compact)"
                                    100 -> "100% (Default)"
                                    125 -> "125% (Comfortable)"
                                    150 -> "150% (Large)"
                                    200 -> "200% (Close-up)"
                                    else -> "$pct%"
                                }
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = label,
                                            fontWeight = if ((zoomScale * 100).roundToInt() == pct) FontWeight.Bold else FontWeight.Normal,
                                            color = if ((zoomScale * 100).roundToInt() == pct) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                        )
                                    },
                                    onClick = {
                                        zoomScale = scale
                                        showZoomMenu = false
                                    }
                                )
                            }
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("Fit Sheet Width") },
                                onClick = {
                                    showZoomMenu = false
                                    val totalColWidth = (0 until minOf(maxCols, 8)).sumOf { (columnWidths[it] ?: 80.dp).value.toDouble() }.toFloat()
                                    if (totalColWidth > 0f) {
                                        zoomScale = ((screenWidth - 50.dp).value / totalColWidth).coerceIn(0.35f, 1.5f)
                                    }
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Reset Zoom (100%)") },
                                onClick = {
                                    zoomScale = 1.0f
                                    showZoomMenu = false
                                }
                            )
                        }
                    }

                    IconButton(
                        onClick = { zoomScale = (zoomScale + 0.15f).coerceAtMost(2.5f) },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ZoomIn,
                            contentDescription = "Zoom In",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (selectedCell != null) {
                    if (selectedValue.isNotEmpty()) {
                        IconButton(
                            onClick = {
                                selectedValue.copyToClipboard()
                                globalClass.showMsg("Copied $selectedCellRef to clipboard")
                            },
                            modifier = Modifier.size(30.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.ContentCopy,
                                contentDescription = "Copy cell content",
                                modifier = Modifier.size(15.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    IconButton(
                        onClick = { selectedCell = null },
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Clear selection",
                            modifier = Modifier.size(15.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        if (processedRows.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            do {
                                val event = awaitPointerEvent()
                                if (event.changes.size >= 2) {
                                    val p0 = event.changes[0].position
                                    val p1 = event.changes[1].position
                                    val prev0 = event.changes[0].previousPosition
                                    val prev1 = event.changes[1].previousPosition
                                    val currentDist = (p0 - p1).getDistance()
                                    val prevDist = (prev0 - prev1).getDistance()
                                    if (prevDist > 1f && currentDist > 1f) {
                                        val ratio = currentDist / prevDist
                                        zoomScale = (zoomScale * ratio).coerceIn(0.35f, 2.5f)
                                    }
                                    event.changes.forEach { it.consume() }
                                }
                            } while (event.changes.any { it.pressed })
                        }
                    }
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // ── Sticky Column Header Row ──────────────────────────────
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(scaledHeaderHeight)
                    ) {
                        // Pinned Top-Left Corner Box
                        Box(
                            modifier = Modifier
                                .width(scaledRowHeaderWidth)
                                .height(scaledHeaderHeight)
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
                                .clickable { showCornerMenu = true },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.TableChart,
                                contentDescription = "Spreadsheet Options",
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )

                            DropdownMenu(
                                expanded = showCornerMenu,
                                onDismissRequest = { showCornerMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Auto-fit All Columns") },
                                    leadingIcon = { Icon(Icons.Rounded.FitScreen, null, modifier = Modifier.size(18.dp)) },
                                    onClick = {
                                        showCornerMenu = false
                                        for (col in 0 until maxCols) {
                                            val maxChars = processedRows.maxOfOrNull { it.getOrNull(col)?.length ?: 0 } ?: 0
                                            columnWidths[col] = (maxOf(maxChars, getColumnLetter(col).length) * 9f + 32f).dp.coerceIn(50.dp, 450.dp)
                                        }
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Reset Column Widths") },
                                    leadingIcon = { Icon(Icons.Rounded.RestartAlt, null, modifier = Modifier.size(18.dp)) },
                                    onClick = {
                                        showCornerMenu = false
                                        columnWidths.clear()
                                        for (c in 0 until maxCols) {
                                            val maxChars = processedRows.take(150).maxOfOrNull { it.getOrNull(c)?.length ?: 0 } ?: 0
                                            val colLetterLen = getColumnLetter(c).length
                                            val longest = maxOf(maxChars, colLetterLen)
                                            columnWidths[c] = (longest * 8.5f + 28f).dp.coerceIn(60.dp, 300.dp)
                                        }
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Reset Row Heights") },
                                    leadingIcon = { Icon(Icons.Rounded.RestartAlt, null, modifier = Modifier.size(18.dp)) },
                                    onClick = {
                                        showCornerMenu = false
                                        rowHeights.clear()
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Fit Width to Screen") },
                                    leadingIcon = { Icon(Icons.Rounded.SwapHoriz, null, modifier = Modifier.size(18.dp)) },
                                    onClick = {
                                        showCornerMenu = false
                                        val totalColWidth = (0 until minOf(maxCols, 8)).sumOf { (columnWidths[it] ?: 80.dp).value.toDouble() }.toFloat()
                                        if (totalColWidth > 0f) {
                                            zoomScale = ((screenWidth - 50.dp).value / totalColWidth).coerceIn(0.35f, 1.5f)
                                        }
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Reset Zoom (100%)") },
                                    leadingIcon = { Icon(Icons.Rounded.RestartAlt, null, modifier = Modifier.size(18.dp)) },
                                    onClick = {
                                        showCornerMenu = false
                                        zoomScale = 1.0f
                                    }
                                )
                            }
                        }

                        // Horizontally scrollable column header cells
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .horizontalScroll(horizontalScrollState)
                        ) {
                            for (c in 0 until maxCols) {
                                val isColActive = selectedCell?.second == c
                                Box(
                                    modifier = Modifier
                                        .width(scaledColWidth(c))
                                        .height(scaledHeaderHeight)
                                        .background(
                                            if (isColActive) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                            else MaterialTheme.colorScheme.surfaceVariant
                                        )
                                        .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
                                ) {
                                    // Column letter / click zone
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clickable { activeColumnMenu = c },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = getColumnLetter(c),
                                            fontSize = headerFontSize,
                                            fontWeight = FontWeight.SemiBold,
                                            color = if (isColActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    // Draggable resize handle on right border
                                    var isDraggingCol by remember { mutableStateOf(false) }
                                    Box(
                                        modifier = Modifier
                                            .width(18.dp)
                                            .fillMaxHeight()
                                            .align(Alignment.CenterEnd)
                                            .pointerInput(c, zoomScale) {
                                                detectHorizontalDragGestures(
                                                    onDragStart = { isDraggingCol = true },
                                                    onDragEnd = { isDraggingCol = false },
                                                    onDragCancel = { isDraggingCol = false }
                                                ) { change, dragAmount ->
                                                    change.consume()
                                                    val currentBase = columnWidths[c] ?: 80.dp
                                                    val deltaDp = (dragAmount / density.density / zoomScale).dp
                                                    columnWidths[c] = (currentBase + deltaDp).coerceIn(28.dp, 600.dp)
                                                }
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .width(2.5.dp)
                                                .fillMaxHeight(0.65f)
                                                .background(
                                                    if (isDraggingCol) MaterialTheme.colorScheme.primary
                                                    else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f),
                                                    RoundedCornerShape(1.dp)
                                                )
                                        )
                                    }

                                    DropdownMenu(
                                        expanded = activeColumnMenu == c,
                                        onDismissRequest = { activeColumnMenu = null }
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text("Resize Column ${getColumnLetter(c)}…") },
                                            leadingIcon = { Icon(Icons.Rounded.SwapHoriz, null, modifier = Modifier.size(18.dp)) },
                                            onClick = {
                                                activeColumnMenu = null
                                                resizeColumnTarget = c
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Auto-fit Column ${getColumnLetter(c)}") },
                                            leadingIcon = { Icon(Icons.Rounded.FitScreen, null, modifier = Modifier.size(18.dp)) },
                                            onClick = {
                                                activeColumnMenu = null
                                                val maxChars = processedRows.maxOfOrNull { it.getOrNull(c)?.length ?: 0 } ?: 0
                                                columnWidths[c] = (maxOf(maxChars, getColumnLetter(c).length) * 9f + 32f).dp.coerceIn(50.dp, 450.dp)
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Auto-fit All Columns") },
                                            leadingIcon = { Icon(Icons.Rounded.TableChart, null, modifier = Modifier.size(18.dp)) },
                                            onClick = {
                                                activeColumnMenu = null
                                                for (col in 0 until maxCols) {
                                                    val maxChars = processedRows.maxOfOrNull { it.getOrNull(col)?.length ?: 0 } ?: 0
                                                    columnWidths[col] = (maxOf(maxChars, getColumnLetter(col).length) * 9f + 32f).dp.coerceIn(50.dp, 450.dp)
                                                }
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Sort A → Z") },
                                            leadingIcon = { Icon(Icons.Rounded.ArrowUpward, null, modifier = Modifier.size(18.dp)) },
                                            onClick = {
                                                activeColumnMenu = null
                                                onColumnSort?.invoke(c, "asc")
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Sort Z → A") },
                                            leadingIcon = { Icon(Icons.Rounded.ArrowDownward, null, modifier = Modifier.size(18.dp)) },
                                            onClick = {
                                                activeColumnMenu = null
                                                onColumnSort?.invoke(c, "desc")
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Copy Column Data") },
                                            leadingIcon = { Icon(Icons.Rounded.ContentCopy, null, modifier = Modifier.size(18.dp)) },
                                            onClick = {
                                                activeColumnMenu = null
                                                val columnData = processedRows.mapNotNull { it.getOrNull(c) }.joinToString("\n")
                                                columnData.copyToClipboard()
                                                globalClass.showMsg("Copied column ${getColumnLetter(c)} to clipboard")
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Filter by Column ${getColumnLetter(c)}") },
                                            leadingIcon = { Icon(Icons.Rounded.FilterList, null, modifier = Modifier.size(18.dp)) },
                                            onClick = {
                                                activeColumnMenu = null
                                                onColumnFilter?.invoke(c)
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // ── Data Rows with Pinned Left Row Numbers ────────────────
                    LazyColumn(
                        state = lazyListState,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        itemsIndexed(processedRows) { rIdx, row ->
                            val isRowActive = selectedCell?.first == rIdx
                            val currentRHeight = scaledRowHeight(rIdx)

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(currentRHeight)
                            ) {
                                // Pinned Left Row Number Cell (OUTSIDE horizontalScroll)
                                Box(
                                    modifier = Modifier
                                        .width(scaledRowHeaderWidth)
                                        .height(currentRHeight)
                                        .background(
                                            if (isRowActive) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                            else MaterialTheme.colorScheme.surfaceVariant
                                        )
                                        .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
                                ) {
                                    // Row number tap/long-tap zone
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .pointerInput(rIdx) {
                                                detectTapGestures(
                                                    onTap = { selectedCell = Pair(rIdx, 0) },
                                                    onLongPress = { activeRowMenu = rIdx }
                                                )
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = (rIdx + 1).toString(),
                                            fontSize = rowFontSize,
                                            fontWeight = FontWeight.SemiBold,
                                            color = if (isRowActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    // Draggable resize handle on bottom border
                                    var isDraggingRow by remember { mutableStateOf(false) }
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(14.dp)
                                            .align(Alignment.BottomCenter)
                                            .pointerInput(rIdx, zoomScale) {
                                                detectVerticalDragGestures(
                                                    onDragStart = { isDraggingRow = true },
                                                    onDragEnd = { isDraggingRow = false },
                                                    onDragCancel = { isDraggingRow = false }
                                                ) { change, dragAmount ->
                                                    change.consume()
                                                    val currentBase = rowHeights[rIdx] ?: defaultRowHeight
                                                    val deltaDp = (dragAmount / density.density / zoomScale).dp
                                                    rowHeights[rIdx] = (currentBase + deltaDp).coerceIn(16.dp, 300.dp)
                                                }
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .width(14.dp)
                                                .height(2.dp)
                                                .background(
                                                    if (isDraggingRow) MaterialTheme.colorScheme.primary
                                                    else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f),
                                                    RoundedCornerShape(1.dp)
                                                )
                                        )
                                    }

                                    DropdownMenu(
                                        expanded = activeRowMenu == rIdx,
                                        onDismissRequest = { activeRowMenu = null }
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text("Resize Row ${rIdx + 1}…") },
                                            leadingIcon = { Icon(Icons.Rounded.SwapVert, null, modifier = Modifier.size(18.dp)) },
                                            onClick = {
                                                activeRowMenu = null
                                                resizeRowTarget = rIdx
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Reset Row Height") },
                                            leadingIcon = { Icon(Icons.Rounded.RestartAlt, null, modifier = Modifier.size(18.dp)) },
                                            onClick = {
                                                activeRowMenu = null
                                                rowHeights.remove(rIdx)
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Copy Row Data") },
                                            leadingIcon = { Icon(Icons.Rounded.ContentCopy, null, modifier = Modifier.size(18.dp)) },
                                            onClick = {
                                                activeRowMenu = null
                                                val rowText = row.joinToString("\t")
                                                rowText.copyToClipboard()
                                                globalClass.showMsg("Copied row ${rIdx + 1} to clipboard")
                                            }
                                        )
                                    }
                                }

                                // Horizontally scrollable data cells
                                Row(
                                    modifier = Modifier
                                        .weight(1f)
                                        .horizontalScroll(horizontalScrollState)
                                ) {
                                    for (c in 0 until maxCols) {
                                        val cellVal = row.getOrNull(c) ?: ""
                                        val isSelected = selectedCell?.first == rIdx && selectedCell?.second == c
                                        val isExpanded = expandedCell?.first == rIdx && expandedCell?.second == c
                                        val colLetter = getColumnLetter(c)
                                        val cellRef = "$colLetter${rIdx + 1}"
                                        ExcelDataCell(
                                            text = cellVal,
                                            cellRef = cellRef,
                                            width = scaledColWidth(c),
                                            height = currentRHeight,
                                            fontSize = cellFontSize,
                                            isSelected = isSelected,
                                            isExpanded = isExpanded,
                                            onClick = {
                                                selectedCell = Pair(rIdx, c)
                                                expandedCell = Pair(rIdx, c)
                                            },
                                            onDismissExpand = {
                                                if (expandedCell == Pair(rIdx, c)) {
                                                    expandedCell = null
                                                }
                                            },
                                            onDetailClick = {
                                                selectedCell = Pair(rIdx, c)
                                                showCellDetailDialog = true
                                            },
                                            onCopy = {
                                                cellVal.copyToClipboard()
                                                globalClass.showMsg("Copied cell $cellRef to clipboard")
                                            }
                                        )
                                    }
                                }
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

    // ── Resize Column Dialog ──────────────────────────────────────────────
    if (resizeColumnTarget != null) {
        val col = resizeColumnTarget!!
        ResizeColumnDialog(
            colIndex = col,
            currentWidth = baseColWidth(col),
            onDismiss = { resizeColumnTarget = null },
            onApply = { newWidth, applyToAll ->
                if (applyToAll) {
                    for (c in 0 until maxCols) {
                        columnWidths[c] = newWidth
                    }
                } else {
                    columnWidths[col] = newWidth
                }
            },
            onAutoFit = {
                val maxChars = processedRows.maxOfOrNull { it.getOrNull(col)?.length ?: 0 } ?: 0
                columnWidths[col] = (maxOf(maxChars, getColumnLetter(col).length) * 9f + 32f).dp.coerceIn(50.dp, 450.dp)
            }
        )
    }

    // ── Resize Row Dialog ─────────────────────────────────────────────────
    if (resizeRowTarget != null) {
        val row = resizeRowTarget!!
        ResizeRowDialog(
            rowIndex = row,
            currentHeight = baseRowHeight(row),
            onDismiss = { resizeRowTarget = null },
            onApply = { newHeight, applyToAll ->
                if (applyToAll) {
                    for (r in processedRows.indices) {
                        rowHeights[r] = newHeight
                    }
                } else {
                    rowHeights[row] = newHeight
                }
            },
            onReset = {
                rowHeights.remove(row)
            }
        )
    }

    // ── Cell Detail Dialog ────────────────────────────────────────────────
    if (showCellDetailDialog && selectedCell != null && selectedValue.isNotEmpty()) {
        val (selRow, selCol) = selectedCell!!
        CellDetailDialog(
            cellRef = selectedCellRef,
            text = selectedValue,
            onDismiss = { showCellDetailDialog = false },
            onAutoFitColumn = {
                val maxChars = processedRows.maxOfOrNull { it.getOrNull(selCol)?.length ?: 0 } ?: 0
                columnWidths[selCol] = (maxOf(maxChars, getColumnLetter(selCol).length) * 9f + 32f).dp.coerceIn(50.dp, 450.dp)
            },
            onCopy = {
                selectedValue.copyToClipboard()
                globalClass.showMsg("Copied cell $selectedCellRef to clipboard")
            }
        )
    }
}

@Composable
private fun ExcelDataCell(
    text: String,
    cellRef: String,
    width: Dp,
    height: Dp,
    fontSize: TextUnit,
    isSelected: Boolean,
    isExpanded: Boolean,
    onClick: () -> Unit,
    onDismissExpand: () -> Unit,
    onDetailClick: () -> Unit,
    onCopy: () -> Unit
) {
    val padH = (6f * (fontSize.value / 12f)).dp.coerceIn(2.dp, 10.dp)
    var isTruncated by remember(text, width, fontSize) { mutableStateOf(false) }
    val mightOverflow = remember(text, width, fontSize) {
        text.contains('\n') || (text.length * fontSize.value * 0.62f > (width.value - padH.value * 2f))
    }

    Box(
        modifier = Modifier
            .width(width)
            .height(height)
            .background(
                when {
                    isSelected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                    else -> MaterialTheme.colorScheme.surface
                }
            )
            .border(
                if (isSelected) 1.5.dp else 0.5.dp,
                if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = padH, vertical = 2.dp),
            fontSize = fontSize,
            maxLines = 1,
            overflow = if (isSelected) TextOverflow.Clip else TextOverflow.Ellipsis,
            onTextLayout = { res ->
                isTruncated = res.hasVisualOverflow
            },
            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
        )

        // Floating full-text overlay card when clicked / expanded
        if (isExpanded && text.isNotBlank() && (mightOverflow || isTruncated)) {
            Popup(
                alignment = Alignment.TopStart,
                onDismissRequest = onDismissExpand,
                properties = PopupProperties(
                    focusable = false,
                    dismissOnBackPress = true,
                    dismissOnClickOutside = true,
                    clippingEnabled = true
                )
            ) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shadowElevation = 8.dp,
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary),
                    modifier = Modifier
                        .widthIn(min = width, max = 340.dp)
                        .heightIn(min = height, max = 240.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = padH + 2.dp, vertical = 6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = cellRef,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(
                                    onClick = onCopy,
                                    modifier = Modifier.size(20.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.ContentCopy,
                                        contentDescription = "Copy text",
                                        modifier = Modifier.size(13.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                                IconButton(
                                    onClick = onDetailClick,
                                    modifier = Modifier.size(20.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.FitScreen,
                                        contentDescription = "Full view",
                                        modifier = Modifier.size(13.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(2.dp))

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState())
                                .clickable(onClick = onDetailClick)
                        ) {
                            Text(
                                text = text,
                                fontSize = fontSize,
                                fontWeight = FontWeight.Normal,
                                color = MaterialTheme.colorScheme.onSurface,
                                lineHeight = (fontSize.value * 1.35f).sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CellDetailDialog(
    cellRef: String,
    text: String,
    onDismiss: () -> Unit,
    onAutoFitColumn: () -> Unit,
    onCopy: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Cell $cellRef", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        "${text.length} chars",
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    border = androidx.compose.foundation.BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    SelectionContainer {
                        Text(
                            text = text,
                            modifier = Modifier.padding(12.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                onCopy()
                onDismiss()
            }) {
                Icon(Icons.Rounded.ContentCopy, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Copy")
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    onAutoFitColumn()
                    onDismiss()
                }) {
                    Text("Auto-Fit Column")
                }
                TextButton(onClick = onDismiss) {
                    Text("Close")
                }
            }
        }
    )
}

@Composable
private fun ResizeColumnDialog(
    colIndex: Int,
    currentWidth: Dp,
    onDismiss: () -> Unit,
    onApply: (width: Dp, applyToAll: Boolean) -> Unit,
    onAutoFit: () -> Unit
) {
    var widthSlider by remember { mutableFloatStateOf(currentWidth.value.coerceIn(30f, 400f)) }
    var applyToAll by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Resize Column ${getColumnLetter(colIndex)}") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Width", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "${widthSlider.roundToInt()} dp",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Slider(
                    value = widthSlider,
                    onValueChange = { widthSlider = it },
                    valueRange = 30f..400f
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(60, 90, 130, 180, 240).forEach { preset ->
                        OutlinedButton(
                            onClick = { widthSlider = preset.toFloat() },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 2.dp, vertical = 4.dp)
                        ) {
                            Text("$preset", fontSize = 10.sp)
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { applyToAll = !applyToAll }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(checked = applyToAll, onCheckedChange = { applyToAll = it })
                    Spacer(Modifier.width(8.dp))
                    Text("Apply to all columns", style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = {
                    onAutoFit()
                    onDismiss()
                }) {
                    Text("Auto-Fit")
                }
                Button(onClick = {
                    onApply(widthSlider.dp, applyToAll)
                    onDismiss()
                }) {
                    Text("Apply")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun ResizeRowDialog(
    rowIndex: Int,
    currentHeight: Dp,
    onDismiss: () -> Unit,
    onApply: (height: Dp, applyToAll: Boolean) -> Unit,
    onReset: () -> Unit
) {
    var heightSlider by remember { mutableFloatStateOf(currentHeight.value.coerceIn(18f, 150f)) }
    var applyToAll by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Resize Row ${rowIndex + 1}") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Height", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "${heightSlider.roundToInt()} dp",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Slider(
                    value = heightSlider,
                    onValueChange = { heightSlider = it },
                    valueRange = 18f..150f
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(
                        22 to "22",
                        32 to "32",
                        44 to "44",
                        60 to "60",
                        80 to "80"
                    ).forEach { (preset, label) ->
                        OutlinedButton(
                            onClick = { heightSlider = preset.toFloat() },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 2.dp, vertical = 4.dp)
                        ) {
                            Text(label, fontSize = 10.sp)
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { applyToAll = !applyToAll }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(checked = applyToAll, onCheckedChange = { applyToAll = it })
                    Spacer(Modifier.width(8.dp))
                    Text("Apply to all rows", style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = {
                    onReset()
                    onDismiss()
                }) {
                    Text("Reset")
                }
                Button(onClick = {
                    onApply(heightSlider.dp, applyToAll)
                    onDismiss()
                }) {
                    Text("Apply")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
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
