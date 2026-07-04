package com.raival.compose.file.explorer.screen.main.tab.files.search

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.raival.compose.file.explorer.App.Companion.globalClass
import com.raival.compose.file.explorer.App.Companion.logger
import com.raival.compose.file.explorer.R
import com.raival.compose.file.explorer.common.emptyString
import com.raival.compose.file.explorer.screen.main.tab.files.FilesTab
import com.raival.compose.file.explorer.screen.main.tab.files.holder.ContentHolder
import com.raival.compose.file.explorer.screen.main.tab.files.holder.LocalFileHolder
import com.raival.compose.file.explorer.screen.main.tab.files.holder.VirtualFileHolder
import com.raival.compose.file.explorer.screen.main.tab.files.holder.VirtualFileHolder.Companion.SEARCH
import com.raival.compose.file.explorer.screen.main.tab.files.misc.FileMimeType
import com.raival.compose.file.explorer.screen.main.tab.files.search.ai.AiSearchResult
import com.raival.compose.file.explorer.screen.main.tab.files.search.ai.FileIndexer
import com.raival.compose.file.explorer.screen.main.tab.files.search.ai.FileSearchIndex
import com.raival.compose.file.explorer.screen.main.tab.files.search.ai.ModelDownloadManager
import com.raival.compose.file.explorer.screen.main.tab.files.search.ai.SemanticSearchEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import java.io.BufferedReader
import java.io.FileReader
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.regex.Pattern

class SearchManager {
    var searchQuery by mutableStateOf(emptyString)
    var searchOptions by mutableStateOf(SearchOptions())
    var isSearching by mutableStateOf(false)
    var searchProgress by mutableFloatStateOf(0f)
    var currentSearchingFile by mutableStateOf(emptyString)

    val searchResults = mutableStateListOf<SearchResult>()

    // --- AI Search State ---
    var isAiMode by mutableStateOf(false)
    var isAiSearching by mutableStateOf(false)
    var isIndexing by mutableStateOf(false)
    var indexingPhase by mutableStateOf("")
    var indexingCurrent by mutableStateOf(0)
    var indexingTotal by mutableStateOf(0)
    val aiSearchResults = mutableStateListOf<AiSearchResult>()

    private var searchEngine: SemanticSearchEngine? = null
    private var fileIndex: FileSearchIndex? = null
    private var indexedFolderPath: String? = null
    private var aiSearchJob: Job? = null

    private var searchJob: Job? = null
    private var uiUpdateJob: Job? = null
    private val editableExtensions = FileMimeType.editableFileType.toSet()
    private val codeExtensions = FileMimeType.codeFileType.toSet()

    // Internal state for tracking search progress (not observed by UI)
    private var internalProgress = 0f
    private var internalCurrentFile = emptyString
    private val uiUpdateInterval = 100L // Update UI every 100ms

    // Batched results management
    private val pendingResults = ConcurrentLinkedQueue<SearchResult>()
    private val resultsBatchSize = 20 // Add results in batches of 20

    /**
     * Toggle AI search mode.
     */
    fun toggleAiMode(tab: FilesTab) {
        isAiMode = !isAiMode
        clearResults()
        aiSearchResults.clear()

        if (isAiMode) {
            // Check if model is downloaded
            if (!ModelDownloadManager.isModelDownloaded(globalClass)) {
                ModelDownloadManager.requestModelDownload {
                    // Model is now ready, initialize engine
                    initializeAiEngine()
                    if (tab.activeFolder is LocalFileHolder) {
                        indexFolder(tab, (tab.activeFolder as LocalFileHolder).file.absolutePath)
                    }
                }
            } else {
                initializeAiEngine()
                if (tab.activeFolder is LocalFileHolder) {
                    indexFolder(tab, (tab.activeFolder as LocalFileHolder).file.absolutePath)
                }
            }
        }
    }

    private fun initializeAiEngine() {
        if (searchEngine != null) return
        try {
            val modelPath = ModelDownloadManager.getModelPath(globalClass)
            val tokenizerPath = ModelDownloadManager.getTokenizerPath(globalClass)
            searchEngine = SemanticSearchEngine.fromPath(modelPath, tokenizerPath)
        } catch (e: Exception) {
            logger.logError(e)
            globalClass.showMsg("Failed to load AI model: ${e.message}")
            isAiMode = false
        }
    }

    /**
     * Index the current folder for AI search.
     */
    fun indexFolder(tab: FilesTab, folderPath: String) {
        if (folderPath == indexedFolderPath && fileIndex != null) return
        val engine = searchEngine ?: return

        aiSearchJob?.cancel()
        aiSearchJob = tab.scope.launch {
            isIndexing = true
            try {
                fileIndex = FileIndexer.buildIndex(
                    rootPath = folderPath,
                    engine = engine,
                    onProgress = { phase, current, total ->
                        indexingPhase = phase
                        indexingCurrent = current
                        indexingTotal = total
                    }
                )
                indexedFolderPath = folderPath
            } catch (e: Exception) {
                if (e !is CancellationException) {
                    logger.logError(e)
                    globalClass.showMsg("Indexing failed: ${e.message}")
                }
            } finally {
                isIndexing = false
            }
        }
    }

    /**
     * Perform AI semantic search.
     */
    fun aiSearch(tab: FilesTab) {
        if (searchQuery.isBlank() || searchEngine == null || fileIndex == null) {
            aiSearchResults.clear()
            return
        }

        aiSearchJob?.cancel()
        aiSearchJob = tab.scope.launch {
            isAiSearching = true
            delay(300) // Debounce

            try {
                val results = withContext(Dispatchers.IO) {
                    searchEngine!!.search(searchQuery, fileIndex!!, topK = 20)
                }
                aiSearchResults.clear()
                aiSearchResults.addAll(results)

                // Populate UI search results
                searchResults.clear()
                results.forEach { aiRes ->
                    val file = java.io.File(aiRes.filePath)
                    if (file.exists()) {
                        searchResults.add(
                            SearchResult(
                                file = LocalFileHolder(file),
                                matchType = SearchResult.MatchType.CONTENT,
                                lineNumber = 1,
                                matchedLine = "Semantic match score: ${(aiRes.score * 100).toInt()}%"
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                if (e !is CancellationException) {
                    logger.logError(e)
                }
            } finally {
                isAiSearching = false
            }
        }
    }

    fun startSearch(tab: FilesTab) {
        if (isAiMode) {
            aiSearch(tab)
            return
        }

        if (searchQuery.isEmpty()) {
            clearResults()
            return
        }

        searchJob?.cancel()
        uiUpdateJob?.cancel()
        clearResults()
        pendingResults.clear()
        isSearching = true
        searchProgress = 0f
        internalProgress = 0f
        internalCurrentFile = emptyString

        // Start UI update job
        uiUpdateJob = tab.scope.launch {
            while (isSearching) {
                delay(uiUpdateInterval)
                updateUiState()
                flushPendingResults()
            }
        }

        searchJob = tab.scope.launch {
            try {
                val searchPattern = if (searchOptions.useRegex) {
                    try {
                        Pattern.compile(
                            searchQuery,
                            if (searchOptions.ignoreCase) Pattern.CASE_INSENSITIVE else 0
                        )
                    } catch (_: Exception) {
                        // Invalid regex, fall back to literal search
                        null
                    }
                } else null

                if (tab.activeFolder is VirtualFileHolder) {
                    searchInList(tab.activeFolderContent, searchPattern)
                } else {
                    internalProgress = -1f
                    searchInFolder(tab.activeFolder, searchPattern)
                }

                internalProgress = 1f
                updateUiState() // Final update
                flushPendingResults() // Flush any remaining results
            } catch (_: CancellationException) {
                // Search was cancelled
            } catch (e: Exception) {
                // Handle other errors
                logger.logError(e)
                globalClass.showMsg(globalClass.getString(R.string.something_went_wrong))
            } finally {
                isSearching = false
                uiUpdateJob?.cancel()
                updateUiState() // Final cleanup
                flushPendingResults() // Final flush
                internalCurrentFile = emptyString
            }
        }
    }

    private fun updateUiState() {
        searchProgress = internalProgress
        currentSearchingFile = internalCurrentFile
    }

    private fun updateInternalProgress(progress: Float, currentFile: String) {
        internalProgress = progress
        internalCurrentFile = currentFile
    }

    private fun addSearchResult(result: SearchResult) {
        pendingResults.add(result)

        // If we've accumulated enough results, flush them immediately
        if (pendingResults.size >= resultsBatchSize) {
            flushPendingResults()
        }
    }

    private fun flushPendingResults() {
        val resultsToAdd = mutableListOf<SearchResult>()

        // Drain all pending results
        while (pendingResults.isNotEmpty()) {
            pendingResults.poll()?.let { resultsToAdd.add(it) }
        }

        // Add them all at once to minimize recomposition
        if (resultsToAdd.isNotEmpty()) {
            searchResults.addAll(resultsToAdd)
        }
    }

    fun stopSearch() {
        searchJob?.cancel()
        uiUpdateJob?.cancel()
        aiSearchJob?.cancel()
        isSearching = false
        isAiSearching = false
        searchProgress = 0f
        currentSearchingFile = emptyString
        flushPendingResults() // Ensure any pending results are added
    }

    fun clearResults() {
        searchResults.clear()
        pendingResults.clear()
        searchProgress = 0f
        currentSearchingFile = emptyString
        internalProgress = 0f
        internalCurrentFile = emptyString
    }

    fun onExpand() {
        globalClass.mainActivityManager.addTabAndSelect(
            FilesTab(VirtualFileHolder(SEARCH))
        )
    }

    private suspend fun searchInList(
        content: List<ContentHolder>,
        searchPattern: Pattern?
    ) {
        val totalItems = content.size
        content.forEachIndexed { index, item ->
            if (!isSearching) return

            updateInternalProgress((index + 1).toFloat() / totalItems, item.displayName)
            searchInItem(item, searchPattern)

            if (getTotalResultsCount() >= searchOptions.maxResults) return
        }
    }

    private suspend fun searchInFolder(
        folder: ContentHolder,
        searchPattern: Pattern?
    ) {
        if (!isSearching || !folder.isFolder) return
        try {
            var processedItems = 0

            suspend fun processFolder(currentFolder: ContentHolder) {
                if (!isSearching) return

                if (currentFolder.isFolder) {
                    try {
                        val folderContent = currentFolder.listContent()
                        for (item in folderContent) {
                            if (!isSearching) return

                            updateInternalProgress(-1f, item.displayName)
                            processedItems++

                            if (item.isFolder) {
                                searchInItem(item, searchPattern)
                                processFolder(item)
                            } else {
                                searchInItem(item, searchPattern)
                            }

                            if (getTotalResultsCount() >= searchOptions.maxResults) return

                            // Only yield occasionally to prevent blocking
                            if (processedItems % 50 == 0) {
                                yield()
                            }
                        }
                    } catch (_: Exception) {
                        // Skip folders that can't be accessed
                    }
                }
            }

            processFolder(folder)
        } catch (_: Exception) {
            // folder access errors
        }
    }

    private fun getTotalResultsCount(): Int {
        return searchResults.size + pendingResults.size
    }

    private suspend fun searchInItem(
        item: ContentHolder,
        searchPattern: Pattern?
    ) {
        if (!isSearching) return

        // Search by extension
        if (searchOptions.searchByExtension) {
            val extension = item.extension
            if (extension.isNotEmpty() && matchesQuery(extension, searchPattern)) {
                addSearchResult(
                    SearchResult(
                        file = item,
                        matchType = SearchResult.MatchType.EXTENSION
                    )
                )
            }
        } else {
            if (matchesQuery(item.displayName, searchPattern)) {
                addSearchResult(
                    SearchResult(
                        file = item,
                        matchType = SearchResult.MatchType.FILENAME
                    )
                )
            }
        }

        // Search in file content
        if (searchOptions.searchInFileContent && item.isFile() && isEditableFile(item)) {
            searchInFileContent(item, searchPattern)
        }
    }

    private suspend fun searchInFileContent(
        file: ContentHolder,
        searchPattern: Pattern?
    ) {
        if (!isSearching || file !is LocalFileHolder) return

        try {
            if (!file.isValid() || file.size > searchOptions.maxFileSize) return

            withContext(Dispatchers.IO) {
                BufferedReader(FileReader(file.file)).use { reader ->
                    var lineNumber = 0
                    var line: String?

                    while (reader.readLine().also { line = it } != null && isSearching) {
                        lineNumber++
                        line?.let { currentLine ->
                            if (matchesQuery(currentLine, searchPattern)) {
                                addSearchResult(
                                    SearchResult(
                                        file = file,
                                        matchType = SearchResult.MatchType.CONTENT,
                                        lineNumber = lineNumber,
                                        matchedLine = currentLine.trim()
                                            .take(100) // Limit line preview
                                    )
                                )

                                return@withContext // Return immediately after finding first match
                            }
                        }

                        // Yield periodically to prevent blocking
                        if (lineNumber % 1000 == 0) {
                            yield()
                        }
                    }
                }
            }
        } catch (_: Exception) {
            // Skip files that can't be read
        }
    }

    private fun matchesQuery(text: String, searchPattern: Pattern?): Boolean {
        return if (searchPattern != null) {
            searchPattern.matcher(text).find()
        } else {
            text.contains(searchQuery, ignoreCase = searchOptions.ignoreCase)
        }
    }

    private fun isEditableFile(file: ContentHolder): Boolean {
        val extension = file.extension
        return extension in editableExtensions || extension in codeExtensions
    }
}