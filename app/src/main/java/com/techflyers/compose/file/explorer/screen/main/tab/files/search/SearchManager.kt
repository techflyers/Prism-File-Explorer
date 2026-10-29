package com.techflyers.compose.file.explorer.screen.main.tab.files.search

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.techflyers.compose.file.explorer.App.Companion.globalClass
import com.techflyers.compose.file.explorer.App.Companion.logger
import com.techflyers.compose.file.explorer.R
import com.techflyers.compose.file.explorer.common.emptyString
import com.techflyers.compose.file.explorer.screen.main.tab.files.FilesTab
import com.techflyers.compose.file.explorer.screen.main.tab.files.holder.ContentHolder
import com.techflyers.compose.file.explorer.screen.main.tab.files.holder.LocalFileHolder
import com.techflyers.compose.file.explorer.screen.main.tab.files.holder.VirtualFileHolder
import com.techflyers.compose.file.explorer.screen.main.tab.files.holder.VirtualFileHolder.Companion.SEARCH
import com.techflyers.compose.file.explorer.screen.main.tab.files.misc.FileMimeType
import com.techflyers.compose.file.explorer.screen.main.tab.files.search.ai.AiSearchResult
import com.techflyers.compose.file.explorer.screen.main.tab.files.search.ai.FileIndexer
import com.techflyers.compose.file.explorer.screen.main.tab.files.search.ai.FileSearchIndex
import com.techflyers.compose.file.explorer.screen.main.tab.files.search.ai.MlKitOcrEngine
import com.techflyers.compose.file.explorer.screen.main.tab.files.search.ai.ModelDownloadManager
import com.techflyers.compose.file.explorer.screen.main.tab.files.search.ai.SemanticSearchEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.util.concurrent.ConcurrentHashMap
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

    // Fast in-memory directory index cache for reliable, high-performance repeat searches
    private data class CachedDirEntries(
        val dirMtime: Long,
        val showHidden: Boolean,
        val entries: List<ContentHolder>
    )
    private val dirEntryIndexCache = ConcurrentHashMap<String, CachedDirEntries>()
    private val seenResultKeys = ConcurrentHashMap.newKeySet<String>()

    private var internalProgress = 0f
    private var internalCurrentFile = emptyString
    private val uiUpdateInterval = 80L

    private val pendingResults = ConcurrentLinkedQueue<SearchResult>()
    private val resultsBatchSize = 25

    fun invalidateIndexCache(path: String? = null) {
        if (path == null) {
            dirEntryIndexCache.clear()
        } else {
            dirEntryIndexCache.remove(path)
        }
    }

    /**
     * Toggle AI search mode.
     */
    fun toggleAiMode(tab: FilesTab) {
        isAiMode = !isAiMode
        clearResults()
        aiSearchResults.clear()

        if (isAiMode) {
            if (!ModelDownloadManager.isModelDownloaded(globalClass)) {
                ModelDownloadManager.requestModelDownload {
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
                    ocrEngine = MlKitOcrEngine(),
                    onProgress = { phase, current, total ->
                        indexingPhase = phase
                        indexingCurrent = current
                        indexingTotal = total
                    }
                )
                indexedFolderPath = folderPath
                if (searchQuery.isNotBlank()) {
                    aiSearch(tab)
                }
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
            delay(250)

            try {
                val results = withContext(Dispatchers.IO) {
                    searchEngine!!.search(searchQuery, fileIndex!!, topK = 30)
                }
                aiSearchResults.clear()
                aiSearchResults.addAll(results)

                withContext(Dispatchers.Main) {
                    searchResults.clear()
                    results.forEach { aiRes ->
                        val file = File(aiRes.filePath)
                        if (file.exists()) {
                            val holder = LocalFileHolder(file)
                            if (searchOptions.matchesFilter(holder)) {
                                searchResults.add(
                                    SearchResult(
                                        file = holder,
                                        matchType = SearchResult.MatchType.CONTENT,
                                        lineNumber = 1,
                                        matchedLine = "Semantic match score: ${(aiRes.score * 100).toInt()}%"
                                    )
                                )
                            }
                        }
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

        if (searchQuery.isEmpty() && !searchOptions.hasActiveFilters()) {
            clearResults()
            return
        }

        searchJob?.cancel()
        uiUpdateJob?.cancel()
        clearResults()
        pendingResults.clear()
        seenResultKeys.clear()
        isSearching = true
        searchProgress = 0f
        internalProgress = 0f
        internalCurrentFile = emptyString

        uiUpdateJob = tab.scope.launch(Dispatchers.Main) {
            while (isSearching) {
                delay(uiUpdateInterval)
                updateUiState()
                flushPendingResults()
            }
        }

        searchJob = tab.scope.launch(Dispatchers.IO) {
            try {
                val searchPattern = if (searchOptions.useRegex && searchQuery.isNotEmpty()) {
                    try {
                        Pattern.compile(
                            searchQuery,
                            if (searchOptions.ignoreCase) Pattern.CASE_INSENSITIVE else 0
                        )
                    } catch (_: Exception) {
                        null
                    }
                } else null

                if (tab.activeFolder is VirtualFileHolder) {
                    val snapshot = withContext(Dispatchers.Main) { tab.activeFolderContent.toList() }
                    searchInList(snapshot, searchPattern)
                } else {
                    internalProgress = -1f
                    searchInFolder(tab.activeFolder, searchPattern)
                    if (tab.isHomeSearchTab && globalClass.preferencesManager.searchExternalStorageFromHome) {
                        val primaryPath = tab.activeFolder.uniquePath
                        for ((_, extDir) in com.techflyers.compose.file.explorer.screen.main.tab.files.provider.StorageProvider.getExternalStorageDirectories(globalClass)) {
                            if (!isSearching || getTotalResultsCount() >= searchOptions.maxResults) break
                            if (extDir.exists() && extDir.absolutePath != primaryPath) {
                                searchInFolder(LocalFileHolder(extDir), searchPattern)
                            }
                        }
                    }
                }

                internalProgress = 1f
                withContext(Dispatchers.Main) {
                    updateUiState()
                    flushPendingResults()
                }
            } catch (_: CancellationException) {
                // Search was cancelled
            } catch (e: Exception) {
                logger.logError(e)
                globalClass.showMsg(globalClass.getString(R.string.something_went_wrong))
            } finally {
                isSearching = false
                uiUpdateJob?.cancel()
                withContext(Dispatchers.Main) {
                    updateUiState()
                    flushPendingResults()
                }
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
        val key = "${result.file.uniquePath}|${result.matchType}|${result.lineNumber ?: 0}"
        if (!seenResultKeys.add(key)) return
        pendingResults.add(result)
    }

    private fun flushPendingResults() {
        val resultsToAdd = mutableListOf<SearchResult>()
        while (pendingResults.isNotEmpty()) {
            pendingResults.poll()?.let { resultsToAdd.add(it) }
        }
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
        flushPendingResults()
    }

    fun clearResults() {
        searchResults.clear()
        pendingResults.clear()
        seenResultKeys.clear()
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
        val totalItems = content.size.coerceAtLeast(1)
        content.forEachIndexed { index, item ->
            if (!isSearching) return

            updateInternalProgress((index + 1).toFloat() / totalItems, item.displayName)
            searchInItem(item, searchPattern)

            if (getTotalResultsCount() >= searchOptions.maxResults) return
        }
    }

    private suspend fun getIndexedFolderContent(folder: ContentHolder): List<ContentHolder> {
        val showHidden = globalClass.preferencesManager.showHiddenFiles
        if (folder is LocalFileHolder) {
            val path = folder.uniquePath
            val currentMtime = folder.file.lastModified()
            val cached = dirEntryIndexCache[path]
            if (cached != null && cached.dirMtime == currentMtime && cached.showHidden == showHidden) {
                return cached.entries
            }
            val rawFiles = folder.file.listFiles() ?: emptyArray()
            val entries = ArrayList<ContentHolder>(rawFiles.size)
            for (f in rawFiles) {
                if (showHidden || !f.name.startsWith(".")) {
                    entries.add(LocalFileHolder(f))
                }
            }
            dirEntryIndexCache[path] = CachedDirEntries(
                dirMtime = currentMtime,
                showHidden = showHidden,
                entries = entries
            )
            return entries
        }
        return folder.listContent()
    }

    private suspend fun searchInFolder(
        folder: ContentHolder,
        searchPattern: Pattern?
    ) {
        if (!isSearching || !folder.isFolder) return
        try {
            var processedItems = 0
            val visitedCanonicalDirs = HashSet<String>()
            val skipSystemPaths = setOf("/proc", "/sys", "/dev")

            suspend fun processFolder(currentFolder: ContentHolder) {
                if (!isSearching) return
                if (currentFolder.uniquePath in skipSystemPaths) return

                if (currentFolder is LocalFileHolder) {
                    val canonical = runCatching { currentFolder.file.canonicalPath }.getOrNull()
                        ?: currentFolder.uniquePath
                    if (!visitedCanonicalDirs.add(canonical)) return
                }

                if (currentFolder.isFolder) {
                    try {
                        val folderContent = getIndexedFolderContent(currentFolder)
                        val subfolders = mutableListOf<ContentHolder>()
                        for (item in folderContent) {
                            if (!isSearching) return

                            updateInternalProgress(-1f, item.displayName)
                            processedItems++

                            searchInItem(item, searchPattern)
                            if (item.isFolder) {
                                subfolders.add(item)
                            }

                            if (getTotalResultsCount() >= searchOptions.maxResults) return

                            if (processedItems % 80 == 0) {
                                yield()
                            }
                        }
                        for (sub in subfolders) {
                            if (!isSearching || getTotalResultsCount() >= searchOptions.maxResults) return
                            processFolder(sub)
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

        val showHidden = globalClass.preferencesManager.showHiddenFiles
        if (!showHidden && item.isHidden()) return

        if (!searchOptions.matchesFilter(item)) return

        if (searchOptions.searchByExtension && searchQuery.isNotEmpty()) {
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
            } else if (item is LocalFileHolder && (item.isApk() || item.isApkBundle())) {
                val apkInfo = item.getApkPackageInfo()
                if (apkInfo != null && (matchesQuery(apkInfo.first, searchPattern) || matchesQuery(apkInfo.second, searchPattern))) {
                    addSearchResult(
                        SearchResult(
                            file = item,
                            matchType = SearchResult.MatchType.FILENAME,
                            matchedLine = "${apkInfo.first} (${apkInfo.second})"
                        )
                    )
                }
            }
        }

        if (searchOptions.searchInFileContent && searchQuery.isNotEmpty() && item.isFile()) {
            if (isEditableFile(item)) {
                searchInFileContent(item, searchPattern)
            } else if (item is LocalFileHolder && item.extension.lowercase() in FileMimeType.documentFileType) {
                searchInDocumentContent(item, searchPattern)
            }
        }
    }

    private suspend fun searchInDocumentContent(
        file: LocalFileHolder,
        searchPattern: Pattern?
    ) {
        if (!isSearching || !file.isValid() || file.size > searchOptions.maxFileSize) return

        try {
            val text = withContext(Dispatchers.IO) {
                DocumentTextExtractor.extract(file.file)
            }
            if (text.isNotBlank() && isSearching) {
                var lineNumber = 0
                for (line in text.lineSequence()) {
                    lineNumber++
                    if (matchesQuery(line, searchPattern)) {
                        addSearchResult(
                            SearchResult(
                                file = file,
                                matchType = SearchResult.MatchType.CONTENT,
                                lineNumber = lineNumber,
                                matchedLine = line.trim().take(100)
                            )
                        )
                        return
                    }
                    if (lineNumber % 500 == 0) yield()
                }
            }
        } catch (_: Exception) {}
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
                                        matchedLine = currentLine.trim().take(100)
                                    )
                                )
                                return@withContext
                            }
                        }

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
        if (searchQuery.isEmpty()) return true
        return if (searchPattern != null) {
            searchPattern.matcher(text).find()
        } else {
            text.contains(searchQuery, ignoreCase = searchOptions.ignoreCase)
        }
    }

    private fun isEditableFile(file: ContentHolder): Boolean {
        val extension = file.extension.lowercase()
        return extension in editableExtensions || extension in codeExtensions
    }
}
