package com.techflyers.compose.file.explorer.screen.viewer.document

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.text.format.Formatter
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.techflyers.compose.file.explorer.office.OfficeEngine
import com.techflyers.compose.file.explorer.screen.viewer.document.ai.DocumentAiService
import com.techflyers.compose.file.explorer.screen.viewer.document.core.PdfDocument
import com.techflyers.compose.file.explorer.screen.viewer.document.core.PdfEditTool
import com.techflyers.compose.file.explorer.screen.viewer.document.core.PdfMarkup
import com.techflyers.compose.file.explorer.screen.viewer.document.core.PdfSearchMatch
import com.techflyers.compose.file.explorer.screen.viewer.document.core.PdfSecurityService
import com.techflyers.compose.file.explorer.screen.viewer.document.core.PdfTextBlock
import com.techflyers.compose.file.explorer.screen.viewer.document.core.PdfTextServiceImpl
import com.techflyers.compose.file.explorer.screen.viewer.document.core.PdfViewerImpl
import com.techflyers.compose.file.explorer.utils.UniversalDocumentConverter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.ArrayDeque

data class DocumentViewerUiState(
    val document: PdfDocument? = null,
    val originalUri: Uri? = null,
    val activePdfUri: Uri? = null,
    val fileName: String = "",
    val fileSizeString: String = "",
    val pageCount: Int = 0,
    val currentPage: Int = 0,
    val isLoading: Boolean = true,
    val progress: Float? = null,
    val loadingMessage: String = "Opening document…",
    val error: String? = null,
    val isEncrypted: Boolean = false,
    val passwordRequired: Boolean = false,
    val renderedByLibreOffice: Boolean = false,
    val isOfficeDocument: Boolean = false,
    val showOfficeDownloadPrompt: Boolean = false,
    val showSearch: Boolean = false,
    val searchQuery: String = "",
    val searchMatches: List<PdfSearchMatch> = emptyList(),
    val currentMatchIndex: Int = 0,
    val bookmarkedPages: Set<Int> = emptySet(),
    val darkPageAppearance: Boolean = false,
    val autoScroll: Boolean = false,
    val autoScrollSpeed: Float = 1.0f,
    val activeTool: PdfEditTool = PdfEditTool.None,
    val currentColor: Color = Color(0xFF007AFF),
    val currentStrokeWidth: Float = 4.0f,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val fullText: String = "",
    val pagesText: List<Pair<Int, String>> = emptyList(),
    val selectedText: String? = null,
    val showAiSheet: Boolean = false,
    val showSignatureDialog: Boolean = false,
    val showInfoDialog: Boolean = false,
    val showJumpDialog: Boolean = false,
    val showPasswordDialog: Boolean = false
)

class DocumentViewerViewModel(application: Application) : AndroidViewModel(application) {

    private val context: Context get() = getApplication<Application>().applicationContext
    private val pdfViewer = PdfViewerImpl()
    private val textService = PdfTextServiceImpl()

    private val _uiState = MutableStateFlow(DocumentViewerUiState())
    val uiState: StateFlow<DocumentViewerUiState> = _uiState.asStateFlow()

    // Page bitmaps cache
    val pageBitmaps = mutableStateMapOf<Int, Bitmap>()
    val pageAspectRatios = mutableStateMapOf<Int, Float>()
    val pageTextBlocks = mutableStateMapOf<Int, List<PdfTextBlock>>()

    // Markups per page
    val pageMarkups = mutableStateMapOf<Int, MutableList<PdfMarkup>>()

    // Undo / Redo stacks
    private sealed class MarkupAction {
        data class Add(val page: Int, val markup: PdfMarkup) : MarkupAction()
        data class Remove(val page: Int, val markup: PdfMarkup, val index: Int) : MarkupAction()
    }
    private val undoStack = ArrayDeque<MarkupAction>()
    private val redoStack = ArrayDeque<MarkupAction>()

    private var searchJob: Job? = null
    private var textExtractionJob: Job? = null
    private var installerObserverJob: Job? = null
    private var conversionProgressJob: Job? = null
    private var renderWidth = 1080

    fun initDocument(uri: Uri, deviceScreenWidth: Int = 1080) {
        renderWidth = deviceScreenWidth.coerceAtLeast(480)
        val name = UniversalDocumentConverter.getFileName(context, uri)
        val isOffice = OfficeEngine.isOfficeFile(name)

        _uiState.update {
            it.copy(
                originalUri = uri,
                fileName = name,
                isOfficeDocument = isOffice,
                isLoading = true,
                loadingMessage = "Preparing document…"
            )
        }

        // Check if office document and LibreOffice is not installed
        if (isOffice && !OfficeEngine.isInstalled(context)) {
            val currentState = OfficeEngine.installer(context).state.value
            if (currentState is com.techflyers.compose.file.explorer.office.OfficeEngineState.Downloading ||
                currentState is com.techflyers.compose.file.explorer.office.OfficeEngineState.Installing) {
                observeInstallerDownload()
            } else {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        showOfficeDownloadPrompt = true
                    )
                }
            }
        } else {
            loadDocument(uri, preferLibreOffice = true)
        }
    }

    fun onDownloadEngineConfirmed() {
        val installer = OfficeEngine.installer(context)
        installer.install()
        observeInstallerDownload()
    }

    fun observeInstallerDownload() {
        installerObserverJob?.cancel()
        _uiState.update {
            it.copy(
                showOfficeDownloadPrompt = false,
                isLoading = true,
                progress = 0f,
                loadingMessage = "Connecting to download LibreOffice engine…"
            )
        }
        installerObserverJob = viewModelScope.launch {
            OfficeEngine.installer(context).state.collect { state ->
                when (state) {
                    is com.techflyers.compose.file.explorer.office.OfficeEngineState.Downloading -> {
                        val done = state.downloaded
                        val total = state.total
                        val pct = if (total > 0) (done.toFloat() / total.toFloat()).coerceIn(0f, 1f) else 0f
                        val mbDone = Formatter.formatFileSize(context, done)
                        val mbTotal = Formatter.formatFileSize(context, total)
                        _uiState.update {
                            it.copy(
                                showOfficeDownloadPrompt = false,
                                isLoading = true,
                                progress = pct,
                                loadingMessage = if (state.waitingForNetwork) {
                                    "Waiting for network connection…"
                                } else {
                                    "Downloading LibreOffice: $mbDone / $mbTotal (${(pct * 100).toInt()}%)"
                                }
                            )
                        }
                    }
                    is com.techflyers.compose.file.explorer.office.OfficeEngineState.Installing -> {
                        _uiState.update {
                            it.copy(
                                showOfficeDownloadPrompt = false,
                                isLoading = true,
                                progress = null,
                                loadingMessage = "Installing LibreOffice engine files…"
                            )
                        }
                    }
                    is com.techflyers.compose.file.explorer.office.OfficeEngineState.Installed -> {
                        // Automatically detect completion without manual exit!
                        val uri = _uiState.value.originalUri
                        if (uri != null && _uiState.value.activePdfUri == null) {
                            _uiState.update {
                                it.copy(
                                    showOfficeDownloadPrompt = false,
                                    isLoading = true,
                                    progress = 0.05f,
                                    loadingMessage = "LibreOffice ready. Converting document…"
                                )
                            }
                            loadDocument(uri, preferLibreOffice = true)
                        }
                    }
                    is com.techflyers.compose.file.explorer.office.OfficeEngineState.Failed -> {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                showOfficeDownloadPrompt = false,
                                error = "Download failed: ${state.message}"
                            )
                        }
                    }
                    else -> {}
                }
            }
        }
    }

    fun onDismissDownloadPromptAndUseFallback() {
        _uiState.update { it.copy(showOfficeDownloadPrompt = false) }
        val uri = _uiState.value.originalUri ?: return
        loadDocument(uri, preferLibreOffice = false)
    }

    fun loadDocument(sourceUri: Uri, preferLibreOffice: Boolean) {
        conversionProgressJob?.cancel()
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isLoading = true, progress = 0.10f, loadingMessage = "Preparing document…", error = null) }

            // Dynamic fluid progress steps during conversion
            conversionProgressJob = viewModelScope.launch {
                val stages = listOf(
                    0.25f to "Analyzing document formatting…",
                    0.45f to "Extracting text and tables…",
                    0.65f to "Processing layout and fonts…",
                    0.82f to "Rendering vector PDF pages…",
                    0.92f to "Finalizing high-fidelity PDF…"
                )
                for ((prog, msg) in stages) {
                    delay(380)
                    _uiState.update { current ->
                        if (current.isLoading && current.activePdfUri == null) {
                            current.copy(progress = prog, loadingMessage = msg)
                        } else current
                    }
                }
            }

            try {
                var activePdfUri: Uri = sourceUri
                var renderedByLibre = false

                if (!UniversalDocumentConverter.isPdf(context, sourceUri)) {
                    val name = UniversalDocumentConverter.getFileName(context, sourceUri)
                    if (preferLibreOffice && OfficeEngine.isInstalled(context)) {
                        val libreConverted = OfficeEngine.tryConvert(context, sourceUri, name)
                        if (libreConverted != null) {
                            activePdfUri = Uri.fromFile(libreConverted)
                            renderedByLibre = true
                        } else {
                            activePdfUri = UniversalDocumentConverter.convertToPdf(context, sourceUri)
                        }
                    } else {
                        activePdfUri = UniversalDocumentConverter.convertToPdf(context, sourceUri)
                    }
                }

                conversionProgressJob?.cancel()
                _uiState.update { it.copy(progress = 0.96f, loadingMessage = "Opening PDF…") }

                // Check encryption
                val isEncrypted = PdfSecurityService.isPasswordProtected(context, activePdfUri)
                if (isEncrypted) {
                    _uiState.update {
                        it.copy(
                            activePdfUri = activePdfUri,
                            isEncrypted = true,
                            passwordRequired = true,
                            showPasswordDialog = true,
                            isLoading = false
                        )
                    }
                    return@launch
                }

                openPdfInternal(activePdfUri, renderedByLibre)

            } catch (e: Exception) {
                conversionProgressJob?.cancel()
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = e.message ?: "Failed to open document"
                    )
                }
            }
        }
    }

    fun unlockPasswordProtected(password: String) {
        val uri = _uiState.value.activePdfUri ?: return
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isLoading = true, loadingMessage = "Unlocking document…") }
            try {
                val decryptedFile = PdfSecurityService.decryptToCache(context, uri, password)
                val unlockedUri = Uri.fromFile(decryptedFile)
                openPdfInternal(unlockedUri, _uiState.value.renderedByLibreOffice)
                _uiState.update { it.copy(showPasswordDialog = false, passwordRequired = false) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        showPasswordDialog = true,
                        error = "Incorrect password. Please try again."
                    )
                }
            }
        }
    }

    private suspend fun openPdfInternal(pdfUri: Uri, renderedByLibreOffice: Boolean) {
        try {
            val doc = pdfViewer.open(context, pdfUri)
            val sizeStr = Formatter.formatFileSize(context, doc.sizeBytes)

            _uiState.update {
                it.copy(
                    document = doc,
                    activePdfUri = pdfUri,
                    pageCount = doc.pageCount,
                    fileSizeString = sizeStr,
                    renderedByLibreOffice = renderedByLibreOffice,
                    isLoading = false
                )
            }

            // Pre-calculate aspect ratios and render initial pages
            for (p in 0 until minOf(doc.pageCount, 15)) {
                val size = pdfViewer.pageSize(doc, p)
                if (size != null && size.width > 0 && size.height > 0) {
                    pageAspectRatios[p] = size.width.toFloat() / size.height.toFloat()
                } else {
                    pageAspectRatios[p] = 1f / 1.414f
                }
            }

            renderPagesAround(0)

        } catch (e: Exception) {
            _uiState.update {
                it.copy(
                    isLoading = false,
                    error = e.message ?: "Could not render PDF document"
                )
            }
        }
    }

    fun renderPagesAround(centerPage: Int, customWidth: Int? = null) {
        val doc = _uiState.value.document ?: return
        val targetWidth = customWidth ?: renderWidth
        viewModelScope.launch(Dispatchers.IO) {
            val start = (centerPage - 1).coerceAtLeast(0)
            val end = (centerPage + 2).coerceAtMost(doc.pageCount - 1)

            // Evict bitmaps far away from current viewport to keep memory small and eliminate GC stutter
            withContext(Dispatchers.Main) {
                val keysToRemove = pageBitmaps.keys.filter { kotlin.math.abs(it - centerPage) > 7 }
                keysToRemove.forEach { pageBitmaps.remove(it) }
            }

            for (p in start..end) {
                val currentBmp = pageBitmaps[p]
                if (currentBmp == null || (customWidth != null && currentBmp.width < targetWidth * 0.85f)) {
                    val bitmap = pdfViewer.renderPage(doc, p, targetWidth)
                    if (bitmap != null) {
                        withContext(Dispatchers.Main) {
                            pageBitmaps[p] = bitmap
                            pageAspectRatios[p] = bitmap.width.toFloat() / bitmap.height.toFloat()
                        }
                    }
                }
            }
        }
    }

    fun getPageTextBlocks(page: Int): List<PdfTextBlock> {
        val cached = pageTextBlocks[page]
        if (cached != null) return cached
        extractPageText(page)
        return emptyList()
    }

    fun extractPageText(page: Int) {
        if (pageTextBlocks.containsKey(page)) return
        val uri = _uiState.value.activePdfUri ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val blocks = textService.extractPage(context, uri, page)
            if (blocks.isNotEmpty()) {
                withContext(Dispatchers.Main) {
                    pageTextBlocks[page] = blocks
                }
            }
        }
    }

    private fun extractDocumentTextAsync(uri: Uri, pageCount: Int) {
        textExtractionJob?.cancel()
        textExtractionJob = viewModelScope.launch(Dispatchers.IO) {
            val pageTexts = mutableListOf<Pair<Int, String>>()
            val fullTextBuilder = StringBuilder()

            for (p in 0 until minOf(pageCount, 100)) {
                val blocks = textService.extractPage(context, uri, p)
                if (blocks.isNotEmpty()) {
                    withContext(Dispatchers.Main) {
                        pageTextBlocks[p] = blocks
                    }
                }
                val pText = blocks.joinToString(" ") { it.text }
                if (pText.isNotBlank()) {
                    pageTexts.add(p to pText)
                    fullTextBuilder.append(pText).append("\n\n")
                }
            }

            _uiState.update {
                it.copy(
                    fullText = fullTextBuilder.toString(),
                    pagesText = pageTexts
                )
            }
        }
    }

    // ── Search ─────────────────────────────────────────────────────────────────

    fun setSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        searchJob?.cancel()
        if (query.isBlank()) {
            _uiState.update { it.copy(searchMatches = emptyList(), currentMatchIndex = 0) }
            return
        }

        searchJob = viewModelScope.launch(Dispatchers.IO) {
            delay(250) // debounce
            val uri = _uiState.value.activePdfUri ?: return@launch
            val count = _uiState.value.pageCount
            val matches = textService.searchAll(context, uri, query, count)
            _uiState.update {
                it.copy(
                    searchMatches = matches,
                    currentMatchIndex = 0
                )
            }
        }
    }

    fun nextMatch() {
        val total = _uiState.value.searchMatches.size
        if (total > 0) {
            val nextIdx = (_uiState.value.currentMatchIndex + 1) % total
            _uiState.update { it.copy(currentMatchIndex = nextIdx) }
        }
    }

    fun prevMatch() {
        val total = _uiState.value.searchMatches.size
        if (total > 0) {
            val prevIdx = (_uiState.value.currentMatchIndex - 1 + total) % total
            _uiState.update { it.copy(currentMatchIndex = prevIdx) }
        }
    }

    fun toggleSearch() {
        _uiState.update {
            val show = !it.showSearch
            it.copy(
                showSearch = show,
                searchQuery = if (!show) "" else it.searchQuery,
                searchMatches = if (!show) emptyList() else it.searchMatches
            )
        }
    }

    // ── Markups, Drawing & Undo/Redo ──────────────────────────────────────────

    fun addMarkup(page: Int, markup: PdfMarkup) {
        val list = pageMarkups.getOrPut(page) { mutableListOf() }
        list.add(markup)
        undoStack.push(MarkupAction.Add(page, markup))
        redoStack.clear()
        updateUndoRedoState()
    }

    fun undo() {
        if (undoStack.isEmpty()) return
        val action = undoStack.pop()
        when (action) {
            is MarkupAction.Add -> {
                val list = pageMarkups[action.page]
                if (list != null && list.remove(action.markup)) {
                    redoStack.push(action)
                }
            }
            is MarkupAction.Remove -> {
                val list = pageMarkups.getOrPut(action.page) { mutableListOf() }
                list.add(action.index.coerceAtMost(list.size), action.markup)
                redoStack.push(action)
            }
        }
        updateUndoRedoState()
    }

    fun redo() {
        if (redoStack.isEmpty()) return
        val action = redoStack.pop()
        when (action) {
            is MarkupAction.Add -> {
                val list = pageMarkups.getOrPut(action.page) { mutableListOf() }
                list.add(action.markup)
                undoStack.push(action)
            }
            is MarkupAction.Remove -> {
                val list = pageMarkups[action.page]
                list?.remove(action.markup)
                undoStack.push(action)
            }
        }
        updateUndoRedoState()
    }

    private fun updateUndoRedoState() {
        _uiState.update {
            it.copy(
                canUndo = undoStack.isNotEmpty(),
                canRedo = redoStack.isNotEmpty()
            )
        }
    }

    // ── Tool & State Toggles ──────────────────────────────────────────────────

    fun setActiveTool(tool: PdfEditTool) {
        _uiState.update { it.copy(activeTool = tool) }
    }

    fun setColor(color: Color) {
        _uiState.update { it.copy(currentColor = color) }
    }

    fun setStrokeWidth(width: Float) {
        _uiState.update { it.copy(currentStrokeWidth = width) }
    }

    fun toggleDarkPage() {
        _uiState.update { it.copy(darkPageAppearance = !it.darkPageAppearance) }
    }

    fun toggleAutoScroll() {
        _uiState.update { it.copy(autoScroll = !it.autoScroll) }
    }

    fun setAutoScrollSpeed(speed: Float) {
        _uiState.update { it.copy(autoScrollSpeed = speed) }
    }

    fun toggleBookmark(page: Int) {
        _uiState.update {
            val bookmarks = it.bookmarkedPages.toMutableSet()
            if (page in bookmarks) bookmarks.remove(page) else bookmarks.add(page)
            it.copy(bookmarkedPages = bookmarks)
        }
    }

    fun setCurrentPage(page: Int) {
        if (_uiState.value.currentPage == page) return
        _uiState.update { it.copy(currentPage = page) }
        renderPagesAround(page)
    }

    // ── Dialog Visibility ─────────────────────────────────────────────────────

    fun setShowAiSheet(show: Boolean, selectedText: String? = null) {
        _uiState.update { it.copy(showAiSheet = show, selectedText = selectedText) }
        if (show && _uiState.value.fullText.isNullOrBlank()) {
            val uri = _uiState.value.activePdfUri ?: return
            val count = _uiState.value.pageCount
            extractDocumentTextAsync(uri, count)
        }
    }

    fun setShowSignatureDialog(show: Boolean) {
        _uiState.update { it.copy(showSignatureDialog = show) }
    }

    fun setShowInfoDialog(show: Boolean) {
        _uiState.update { it.copy(showInfoDialog = show) }
    }

    fun setShowJumpDialog(show: Boolean) {
        _uiState.update { it.copy(showJumpDialog = show) }
    }

    fun setShowPasswordDialog(show: Boolean) {
        _uiState.update { it.copy(showPasswordDialog = show) }
    }

    fun setShowOfficeDownloadPrompt(show: Boolean) {
        _uiState.update { it.copy(showOfficeDownloadPrompt = show) }
    }

    override fun onCleared() {
        super.onCleared()
        installerObserverJob?.cancel()
        conversionProgressJob?.cancel()
        textExtractionJob?.cancel()
        textService.close()
        val doc = _uiState.value.document
        if (doc != null) {
            pdfViewer.close(doc)
        }
        pageBitmaps.values.forEach { it.recycle() }
        pageBitmaps.clear()
    }
}
