package com.techflyers.compose.file.explorer.screen.main.tab.files

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.net.Uri
import android.os.Environment
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.ShareCompat
import androidx.core.content.FileProvider.getUriForFile
import com.techflyers.compose.file.explorer.App.Companion.globalClass
import com.techflyers.compose.file.explorer.App.Companion.logger
import com.techflyers.compose.file.explorer.R
import com.techflyers.compose.file.explorer.common.emptyString
import com.techflyers.compose.file.explorer.common.getMimeType
import com.techflyers.compose.file.explorer.common.isNot
import com.techflyers.compose.file.explorer.common.orIf
import com.techflyers.compose.file.explorer.common.removeIf
import com.techflyers.compose.file.explorer.screen.main.MainActivity
import com.techflyers.compose.file.explorer.screen.openwith.OpenWithDispatchActivity
import com.techflyers.compose.file.explorer.screen.main.tab.Tab
import com.techflyers.compose.file.explorer.screen.main.tab.files.holder.ContentHolder
import com.techflyers.compose.file.explorer.screen.main.tab.files.holder.LocalFileHolder
import com.techflyers.compose.file.explorer.screen.main.tab.files.holder.VirtualFileHolder
import com.techflyers.compose.file.explorer.screen.main.tab.files.holder.ZipFileHolder
import com.techflyers.compose.file.explorer.screen.main.tab.files.misc.FileListCategory
import com.techflyers.compose.file.explorer.screen.main.tab.files.misc.FileMimeType.anyFileType
import com.techflyers.compose.file.explorer.screen.main.tab.files.misc.FileMimeType.apkFileType
import com.techflyers.compose.file.explorer.screen.main.tab.files.provider.CategoryFileScanner
import com.techflyers.compose.file.explorer.screen.main.tab.files.provider.StorageProvider
import com.techflyers.compose.file.explorer.screen.main.tab.files.state.BottomOptionsBarState
import com.techflyers.compose.file.explorer.screen.main.tab.files.state.DialogsState
import com.techflyers.compose.file.explorer.screen.main.tab.files.task.CompressTask
import com.techflyers.compose.file.explorer.screen.main.tab.files.task.CopyTask
import com.techflyers.compose.file.explorer.screen.main.tab.files.task.CopyTaskParameters
import com.techflyers.compose.file.explorer.screen.main.tab.files.shizuku.ShizukuFileHolder
import com.techflyers.compose.file.explorer.screen.main.tab.files.zip.ArchiveManager
import com.reandroid.archive.ZipAlign
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import net.lingala.zip4j.ZipFile
import net.lingala.zip4j.model.ZipParameters
import org.json.JSONObject
import java.io.File

class FilesTab(
    val source: ContentHolder,
    context: Context? = null
) : Tab() {
    companion object {
        fun isValidLocalPath(path: String) = File(path).exists()

        fun updateHomeScreenShortcutLabel(
            context: Context,
            file: LocalFileHolder,
            label: String
        ) {
            val shortcutManager = context.getSystemService(ShortcutManager::class.java) ?: return
            if (shortcutManager.pinnedShortcuts.none { it.id == file.uniquePath }) return

            val targetIntent = if (file.isFolder) {
                Intent(context, MainActivity::class.java).apply {
                    action = Intent.ACTION_VIEW
                    putExtra("filePath", file.uniquePath)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
            } else {
                val fileUri = file.createUri()
                Intent(context, OpenWithDispatchActivity::class.java).apply {
                    action = Intent.ACTION_VIEW
                    setDataAndType(fileUri, file.mimeType)
                    putExtra("extra_file_path", file.file.absolutePath)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_CLEAR_TOP or
                            Intent.FLAG_GRANT_READ_URI_PERMISSION or
                            Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                }
            }
            val updatedShortcut = ShortcutInfo.Builder(context, file.uniquePath)
                .setIntent(targetIntent)
                .setIcon(
                    android.graphics.drawable.Icon.createWithResource(
                        context,
                        if (file.isFile()) R.mipmap.file_shortcut else R.mipmap.folder_shortcut
                    )
                )
                .setShortLabel(label)
                .build()
            shortcutManager.updateShortcuts(listOf(updatedShortcut))
        }
    }

    override val id = globalClass.generateUid()
    val scope = CoroutineScope(Dispatchers.IO)

    val homeDir: ContentHolder =
        if (source is VirtualFileHolder || source.isFolder) source else runBlocking { source.getParent() }
            ?: StorageProvider.getPrimaryInternalStorage(globalClass).contentHolder

    var activeFolder: ContentHolder = homeDir
    var activeFolderContent = mutableStateListOf<ContentHolder>()
    var contentSnapshot by mutableStateOf<List<ContentHolder>>(emptyList())
    var maxSiblingSize by mutableLongStateOf(1L)
    private var listingJob: Job? = null
    private var lastKnownDirMtime: Long = -1L
    private var lastKnownChildCount: Int = -1
    private var lastKnownCategoryCount: Int = -1
    private var lastKnownCategoryMaxDate: Long = -1L

    fun updateContentSnapshot() {
        contentSnapshot = activeFolderContent.toList()
        maxSiblingSize = activeFolderContent.maxOfOrNull { it.size }?.coerceAtLeast(1L) ?: 1L
    }

    val contentListStates = hashMapOf<String, LazyGridState>()
    var activeListState by mutableStateOf(LazyGridState())

    var viewConfig by mutableStateOf(
        globalClass.preferencesManager.getViewConfigPrefsFor(activeFolder)
    )

    val highlightedFiles = mutableStateListOf<String>()
    var pendingLocateFilePath by mutableStateOf<String?>(null)
    val selectedFiles = linkedMapOf<String, ContentHolder>()
    var lastSelectedFileIndex = -1

    var currentPathSegments by mutableStateOf(listOf<ContentHolder>())
    var highlightedPathSegment by mutableStateOf(activeFolder)
    val currentPathSegmentsListState = LazyListState()

    var showCategories by mutableStateOf(false)
    var categories = mutableStateListOf<FileListCategory>()
    var selectedCategory by mutableStateOf<FileListCategory?>(null)
    var selectedFormats by mutableStateOf<Set<String>>(emptySet())
    var pendingLockPath by mutableStateOf<String?>(null)
    var pendingLockMode by mutableStateOf(com.techflyers.compose.file.explorer.screen.main.tab.files.ui.dialog.FolderLockDialogMode.UNLOCK)
    var showCreateTaskDialog by mutableStateOf(false)

    // Holds the file that has been long-clicked
    var targetFile: ContentHolder? = null
    var compressTaskHolder: CompressTask? = null

    // The archive waiting for a password to be entered before opening
    var pendingPasswordArchive: com.techflyers.compose.file.explorer.screen.main.tab.files.holder.LocalFileHolder? = null

    // Used to detect changes to an already updated ZipTree in ZipManager
    var zipSourceTimestamp = -1L

    private val _dialogsState = MutableStateFlow(DialogsState())
    val dialogsState = _dialogsState.asStateFlow()

    private val _bottomOptionsBarState = MutableStateFlow(BottomOptionsBarState())
    val bottomOptionsBarState = _bottomOptionsBarState.asStateFlow()

    var handleBackGesture by mutableStateOf(true)
    var tabViewLabel by mutableStateOf(createTabLabel())

    var isLoading by mutableStateOf(false)

    var foldersCount by mutableIntStateOf(0)
        private set
    var filesCount by mutableIntStateOf(0)
        private set
    var selectedFilesCount by mutableIntStateOf(0)
    var dragSelectionVersion by mutableIntStateOf(0)
    var selectedFilesTotalSize by mutableLongStateOf(0L)
    var isCalculatingSelectedSize by mutableStateOf(false)
    private var calculateSizeJob: Job? = null

    init {
        // If the tab point to a file, open it immediately without waiting for its parent content to be loaded
        if (source.isFile()) {
            context?.let { openFile(context, source) }
        }
    }

    override fun onTabStarted() {
        super.onTabStarted()
        // Load either the source file or the home tab
        scope.launch {
            if (source.isFile()) { // This is true when locating a file
                // Highlight the opened file
                highlightedFiles.apply {
                    clear()
                    add(source.uniquePath)
                }

                // Check if we can access the parent folder and open it
                source.getParent()?.let { parent ->
                    openFolderImpl(parent) {
                        pendingLocateFilePath = source.uniquePath
                    }
                } ?: also {
                    // If parent folder cannot be accessed, revert to home folder
                    openFolderImpl(homeDir)
                }
            } else {
                // If the source file is a folder, open it
                openFolderImpl(homeDir)
            }
        }
    }

    override fun onTabResumed() {
        scope.launch {
            validateActiveFolder()
            // Important to clear any information from previous tabs (when switching tabs)
            requestHomeToolbarUpdate()
            // Detect any content changes
            detectFileChanges()
            // Check for display mode change
            updateDisplayConfig()
            quickReloadFiles()
        }
    }

    override val header: String
        get() = tabViewLabel

    override suspend fun getTitle(): String {
        return createTitle()
    }

    override suspend fun getSubtitle(): String {
        return createSubtitle()
    }

    private suspend fun createSubtitle(): String {
        if (selectedFiles.isNotEmpty()) {
            val selectedFolders = selectedFiles.values.count { it.isFolder }
            val selectedFilesCount = selectedFiles.size - selectedFolders
            val parts = mutableListOf<String>()
            if (selectedFolders > 0) parts.add("📁 $selectedFolders")
            if (selectedFilesCount > 0) parts.add("📄 $selectedFilesCount")
            return parts.joinToString("  ")
        }
        if (foldersCount + filesCount == 0) {
            return "Ø"
        }
        if (globalClass.preferencesManager.deepEmptyFolderCheck && foldersCount > 0 && filesCount == 0 && activeFolder is LocalFileHolder) {
            if (com.techflyers.compose.file.explorer.screen.main.tab.files.misc.FolderHierarchyChecker.isFolderEmptyWithin((activeFolder as LocalFileHolder).file)) {
                return "○"
            }
        }
        val isPagedCapped = activeFolder is VirtualFileHolder && (activeFolder as VirtualFileHolder).hasMoreContent
        return if (isPagedCapped) {
            "📄 $filesCount of many"
        } else if (foldersCount > 0) {
            "📁 $foldersCount   📄 $filesCount"
        } else {
            "📄 $filesCount"
        }
    }

    private suspend fun createTitle() = when (activeFolder) {
        is VirtualFileHolder -> activeFolder.displayName
        else -> globalClass.getString(R.string.files_tab_title)
    }

    val historyBackStack = mutableStateListOf<ContentHolder>()
    val historyForwardStack = mutableStateListOf<ContentHolder>()
    private var isNavigatingHistory = false

    fun canGoBack(): Boolean = historyBackStack.isNotEmpty() || (handleBackGesture && (activeFolder as? LocalFileHolder)?.file?.parentFile != null)

    fun canGoForward(): Boolean = historyForwardStack.isNotEmpty()

    fun goBack(): Boolean {
        if (unselectAnySelectedFiles()) {
            return true
        }
        if (historyBackStack.isNotEmpty()) {
            val target = historyBackStack.removeAt(historyBackStack.lastIndex)
            val current = activeFolder
            historyForwardStack.add(current)
            if (historyForwardStack.size > 50) {
                historyForwardStack.removeAt(0)
            }
            scope.launch {
                isNavigatingHistory = true
                try {
                    highlightedFiles.apply {
                        clear()
                        add(current.uniquePath)
                    }
                    openFolderImpl(target, rememberSelectedFiles = false)
                } finally {
                    isNavigatingHistory = false
                }
            }
            return true
        } else if (handleBackGesture) {
            scope.launch {
                val parent = activeFolder.getParent()
                if (parent != null) {
                    val current = activeFolder
                    historyForwardStack.add(current)
                    if (historyForwardStack.size > 50) {
                        historyForwardStack.removeAt(0)
                    }
                    highlightedFiles.apply {
                        clear()
                        add(current.uniquePath)
                    }
                    isNavigatingHistory = true
                    try {
                        openFolderImpl(parent)
                    } finally {
                        isNavigatingHistory = false
                    }
                }
            }
            return (activeFolder as? LocalFileHolder)?.file?.parentFile != null
        }
        return false
    }

    fun goForward(): Boolean {
        if (historyForwardStack.isNotEmpty()) {
            val target = historyForwardStack.removeAt(historyForwardStack.lastIndex)
            val current = activeFolder
            historyBackStack.add(current)
            if (historyBackStack.size > 50) {
                historyBackStack.removeAt(0)
            }
            scope.launch {
                isNavigatingHistory = true
                try {
                    openFolderImpl(target, rememberSelectedFiles = false)
                } finally {
                    isNavigatingHistory = false
                }
            }
            return true
        }
        return false
    }

    override fun onBackPressed(): Boolean {
        return goBack()
    }

    /**
     * Unselects all the selected files.
     * returns true if any files were selected
     */
    fun unselectAnySelectedFiles(): Boolean {
        if (selectedFiles.isNotEmpty()) {
            unselectAllFiles()
            return true
        }
        return false
    }


    fun unselectAllFiles(quickReload: Boolean = true) {
        selectedFiles.clear()
        selectedFilesCount = 0
        dragSelectionVersion++
        lastSelectedFileIndex = -1
        updateSelectedFilesSize()
        if (quickReload) quickReloadFiles()
    }

    fun openFile(context: Context, item: ContentHolder) {
        if (item is LocalFileHolder && (item.isApk() || item.isApkBundle())) {
            toggleApkDialog(item)
        } else {
            item.open(
                context = context,
                anonymous = false,
                skipSupportedExtensions = !globalClass.preferencesManager.useBuiltInViewer,
                customMimeType = null
            )
        }
    }

    fun openFolder(
        item: ContentHolder,
        rememberListState: Boolean = true,
        rememberSelectedFiles: Boolean = false,
        postEvent: () -> Unit = {}
    ) {
        scope.launch {
            openFolderImpl(item, rememberListState, rememberSelectedFiles, postEvent)
        }
    }

    suspend fun openFolderImpl(
        item: ContentHolder,
        rememberListState: Boolean = true,
        rememberSelectedFiles: Boolean = false,
        postEvent: () -> Unit = {}
    ) {
        // Block UI
        if (isLoading) return

        // Prevent opening invalid files
        if (!item.isValid()) return

        if (item is LocalFileHolder && com.techflyers.compose.file.explorer.screen.main.tab.files.service.FolderLockStore.isLocked(item.uniquePath)) {
            pendingLockPath = item.uniquePath
            pendingLockMode = com.techflyers.compose.file.explorer.screen.main.tab.files.ui.dialog.FolderLockDialogMode.UNLOCK
            return
        }

        // For virtual folders, update the category
        if (item is VirtualFileHolder) {
            item.selectedCategory = selectedCategory
        }

        if (item != activeFolder) {
            selectedFormats = emptySet()
        }

        val previousFolder = activeFolder
        if (!isNavigatingHistory && item.uniquePath != previousFolder.uniquePath) {
            historyBackStack.add(previousFolder)
            if (historyBackStack.size > 50) {
                historyBackStack.removeAt(0)
            }
            historyForwardStack.clear()
        }

        // Switch to the new folder
        activeFolder = item

        // Update header label
        withContext(Dispatchers.Main) {
            tabViewLabel = createTabLabel()
        }

        // Clear selection if not needed
        if (!rememberSelectedFiles) {
            selectedFiles.clear()
            lastSelectedFileIndex = -1
        } else {
            // Otherwise, validate the selection
            selectedFiles.removeIf { key, value -> runBlocking { !value.isValid() } }
            if (selectedFiles.isEmpty()) lastSelectedFileIndex = -1
        }
        withContext(Dispatchers.Main) {
            selectedFilesCount = selectedFiles.size
            updateSelectedFilesSize()
        }

        // Update the bottom bar options to fit the new folder
        _bottomOptionsBarState.update {
            it.copy(
                showQuickOptions = selectedFiles.isNotEmpty(),
                showCreateNewContentButton = activeFolder.canAddNewContent,
                showMoreOptionsButton = selectedFiles.isNotEmpty(),
                showEmptyRecycleBinButton = activeFolder is LocalFileHolder &&
                        ((activeFolder as LocalFileHolder).hasParent(globalClass.recycleBinDir) ||
                                activeFolder.uniquePath == globalClass.recycleBinDir.uniquePath)
            )
        }

        // Check if the back gesture can be handled
        handleBackGesture = runBlocking {
            selectedFiles.isNotEmpty() || (activeFolder.hasParent() && !shouldNavigateToParentTab())
        }

        // Update the path list
        updatePathList()

        // Update the zipSourceTimestamp if the active folder is a zip file
        if (activeFolder is ZipFileHolder) {
            zipSourceTimestamp = (activeFolder as ZipFileHolder).zipTree.timeStamp
        }

        // Cancel any existing listing job
        listingJob?.cancel()

        // If a new folder is opened, the list must be at the starting position,
        // but when navigating back to parent folder, the saved location must be maintained
        if (!rememberListState) {
            contentListStates[item.uniquePath] = LazyGridState(0, 0)
        }

        // Update the active list state
        activeListState = contentListStates[item.uniquePath] ?: LazyGridState()
            .also { contentListStates[item.uniquePath] = it }

        // Get display config for this folder
        updateDisplayConfig()

        listingJob = scope.launch(Dispatchers.IO) {
            isLoading = true
            try {
                if (activeFolder is LocalFileHolder) {
                    val localFolder = activeFolder as LocalFileHolder
                    lastKnownDirMtime = localFolder.file.lastModified()
                    lastKnownChildCount = localFolder.file.list()?.size ?: -1

                    val sorted = localFolder.listSortedContent()
                    if (!isActive) return@launch

                    val count = localFolder.getContentCount()
                    withContext(Dispatchers.Main) {
                        foldersCount = count.folders
                        filesCount = count.files
                        showCategories = false
                    }

                    val displayItems = if (sorted.size > 10_000) sorted.subList(0, 10_000) else sorted
                    val firstChunk = displayItems.take(256)

                    withContext(Dispatchers.Main) {
                        activeFolderContent.clear()
                        activeFolderContent.addAll(firstChunk)
                        updateContentSnapshot()
                        requestHomeToolbarUpdate()
                        postEvent()
                    }

                    var offset = 256
                    val appendChunkSize = 512
                    while (isActive && offset < displayItems.size) {
                        val end = minOf(offset + appendChunkSize, displayItems.size)
                        val nextChunk = displayItems.subList(offset, end)
                        offset = end
                        withContext(Dispatchers.Main) {
                            activeFolderContent.addAll(nextChunk)
                            updateContentSnapshot()
                        }
                        kotlinx.coroutines.yield()
                    }
                } else if (activeFolder is VirtualFileHolder) {
                    val vFolder = activeFolder as VirtualFileHolder
                    val check = StorageProvider.getCategoryQuickCheck(vFolder.type)
                    lastKnownCategoryCount = check.first
                    lastKnownCategoryMaxDate = check.second

                    val firstPage = vFolder.listSortedContent()
                    if (!isActive) return@launch

                    withContext(Dispatchers.Main) {
                        activeFolderContent.clear()
                        activeFolderContent.addAll(firstPage)
                        updateContentSnapshot()
                        foldersCount = 0
                        filesCount = activeFolderContent.size
                        categories.clear()
                        categories.addAll(vFolder.getCategories())
                        showCategories = categories.isNotEmpty()
                        requestHomeToolbarUpdate()
                        postEvent()
                    }

                    // Background append for remaining pages up to 5,000
                    while (isActive && vFolder.hasMoreContent && activeFolderContent.size < 5000) {
                        val nextPage = vFolder.loadNextPage()
                        if (!isActive || nextPage.isEmpty()) break
                        withContext(Dispatchers.Main) {
                            activeFolderContent.addAll(nextPage)
                            updateContentSnapshot()
                            filesCount = activeFolderContent.size
                            categories.clear()
                            categories.addAll(vFolder.getCategories())
                            showCategories = categories.isNotEmpty()
                            requestHomeToolbarUpdate()
                        }
                        kotlinx.coroutines.yield()
                    }

                    // Optional background filesystem walk if enabled in preferences
                    if (isActive && globalClass.preferencesManager.deepScanCategories) {
                        val ext = when (vFolder.type) {
                            VirtualFileHolder.IMAGE -> com.techflyers.compose.file.explorer.screen.main.tab.files.misc.FileMimeType.imageFileType
                            VirtualFileHolder.VIDEO -> com.techflyers.compose.file.explorer.screen.main.tab.files.misc.FileMimeType.videoFileType
                            VirtualFileHolder.AUDIO -> com.techflyers.compose.file.explorer.screen.main.tab.files.misc.FileMimeType.audioFileType
                            VirtualFileHolder.DOCUMENT -> com.techflyers.compose.file.explorer.screen.main.tab.files.misc.FileMimeType.documentFileType
                            VirtualFileHolder.ARCHIVE -> com.techflyers.compose.file.explorer.screen.main.tab.files.misc.FileMimeType.archiveFileType
                            VirtualFileHolder.APK -> setOf("apk", "apks", "xapk", "apkm")
                            else -> emptySet()
                        }
                        if (ext.isNotEmpty()) {
                            val existingPaths = activeFolderContent.map { it.uniquePath }.toSet()
                            CategoryFileScanner.walkStorageRootsBackground(ext, existingPaths) { batch ->
                                if (isActive && batch.isNotEmpty()) {
                                    withContext(Dispatchers.Main) {
                                        activeFolderContent.addAll(batch)
                                        updateContentSnapshot()
                                        filesCount = activeFolderContent.size
                                        requestHomeToolbarUpdate()
                                    }
                                }
                            }
                        }
                    }
                } else {
                    val sorted = activeFolder.listSortedContent()
                    if (!isActive) return@launch
                    val count = activeFolder.getContentCount()
                    withContext(Dispatchers.Main) {
                        activeFolderContent.clear()
                        activeFolderContent.addAll(sorted)
                        updateContentSnapshot()
                        foldersCount = count.folders
                        filesCount = count.files
                        showCategories = false
                        requestHomeToolbarUpdate()
                        postEvent()
                    }
                }
            } finally {
                withContext(Dispatchers.Main) {
                    isLoading = false
                    requestHomeToolbarUpdate()
                }
            }
        }
    }

    private fun shouldNavigateToParentTab(): Boolean {
        return parentTab isNot null
                && activeFolder is ZipFileHolder
                && with(activeFolder as ZipFileHolder) {
            runBlocking { getParent()?.uniquePath == zipTree.source.getParent()?.uniquePath }
        }
    }

    suspend fun validateActiveFolder() {
        if (!activeFolder.isValid()) {
            // Try to find a valid parent folder
            var validParent: ContentHolder? = null
            var current = activeFolder

            while (current.hasParent()) {
                current = current.getParent() ?: break
                if (current.isValid()) {
                    validParent = current
                    break
                }
            }
            validParent?.let {
                openFolder(it)
                return
            }

            if (homeDir.isValid()) {
                openFolder(homeDir)
            } else {
                openFolder(StorageProvider.getPrimaryInternalStorage(globalClass).contentHolder)
            }
        }
    }

    suspend fun detectFileChanges(): Boolean {
        // terminate the check if the tab is busy
        if (isLoading) return false

        // check if any file has been changed
        if (activeFolder is VirtualFileHolder) {
            val vFolder = activeFolder as VirtualFileHolder
            if (vFolder.type == VirtualFileHolder.BOOKMARKS) {
                validateBookmarks()
                return false
            }
            if (vFolder.type == VirtualFileHolder.SEARCH) {
                validateSearchResult()
                return false
            }
            if (vFolder.type == VirtualFileHolder.DUPLICATES) {
                return false
            }
            val check = StorageProvider.getCategoryQuickCheck(vFolder.type)
            if (lastKnownCategoryCount != -1 && (check.first != lastKnownCategoryCount || check.second != lastKnownCategoryMaxDate)) {
                reloadFiles()
                return true
            }
            return false
        } else if (activeFolder is LocalFileHolder) {
            val dir = (activeFolder as LocalFileHolder).file
            val currentMtime = dir.lastModified()
            if (lastKnownDirMtime != -1L && currentMtime != lastKnownDirMtime) {
                reloadFiles()
                return true
            }
            val childCount = dir.list()?.size ?: -1
            if (lastKnownChildCount != -1 && childCount != -1 && childCount != lastKnownChildCount) {
                reloadFiles()
                return true
            }
            return false
        } else if (activeFolder is com.techflyers.compose.file.explorer.screen.main.tab.files.shizuku.ShizukuFileHolder) {
            val shizukuList = com.techflyers.compose.file.explorer.screen.main.tab.files.shizuku.ShizukuManager.listFiles(activeFolder.uniquePath)
                .filter {
                    val isHidden = it.name.startsWith(".")
                    it.name != "metadata.json" && (globalClass.preferencesManager.showHiddenFiles || !isHidden)
                }
            if (shizukuList.size != activeFolderContent.size ||
                activeFolderContent.map { it.displayName }.toSet() != shizukuList.map { it.name }.toSet()) {
                reloadFiles()
                return true
            }
        } else if (activeFolder is ZipFileHolder) {
            val invalidZipTrees = globalClass.zipManager.validateArchiveTrees()
            if (invalidZipTrees.contains((activeFolder as ZipFileHolder).zipTree.source.uniquePath)) {
                validateActiveFolder()
            } else if (globalClass.zipManager.checkForSourceChanges()) {
                // Check if the source any of the files that have been extracted has changed
                val zipTree = (activeFolder as ZipFileHolder).zipTree
                val changedFiles = zipTree.checkExtractedFiles()
                if (changedFiles.isNotEmpty()) {
                    isLoading = true
                    val sourceFile = zipTree.source.file
                    val isNative = ArchiveManager.isNativeArchivePath(sourceFile.name) ||
                            ArchiveManager.isNativeArchive(sourceFile.extension)

                    if (isNative) {
                        try {
                            changedFiles.forEach { changedFile ->
                                zipTree.getRelatedNode(changedFile)?.let { node ->
                                    runBlocking {
                                        ArchiveManager.addOrUpdateMember(
                                            archivePath = zipTree.archivePathForNative,
                                            localFile = changedFile.file,
                                            internalPath = node.path,
                                            password = zipTree.password
                                        )
                                    }
                                }
                                changedFile.resetCachedTimestamp()
                            }
                        } catch (e: Exception) {
                            logger.logError(e)
                        }
                    } else {
                        ZipFile(zipTree.source.file).use { zipFile ->
                            changedFiles.forEach { changedFile ->
                                zipTree.getRelatedNode(changedFile)?.let { node ->
                                    zipFile.addFile(
                                        changedFile.file,
                                        ZipParameters().apply {
                                            fileNameInZip = node.path
                                            isOverrideExistingFilesInZip = true
                                        }
                                    )
                                }
                                changedFile.resetCachedTimestamp()
                            }
                        }
                    }
                    if (zipTree.source.extension == apkFileType) {
                        try {
                            ZipAlign.alignApk(zipTree.source.file)
                        } catch (e: Exception) {
                            logger.logError(e)
                        }
                    }
                    isLoading = false
                }
                zipTree.reset()
                reloadFiles()
                return true
            } else if (zipSourceTimestamp isNot (activeFolder as ZipFileHolder).zipTree.timeStamp) {
                reloadFiles()
                return true
            }
        }
        return false
    }

    private fun validateBookmarks() {
        globalClass.preferencesManager.bookmarks = globalClass.preferencesManager.bookmarks.filter {
            File(it).exists()
        }.toSet()
    }

    private fun validateSearchResult() {
        globalClass.searchManager.searchResults.removeIf { !File(it.file.uniquePath).exists() }
    }

    fun quickReloadFiles() {
        scope.launch {
            // terminate if the tab is busy
            if (isLoading) return@launch

            // Put the content in a temporary list
            val temp = arrayListOf<ContentHolder>().apply { addAll(activeFolderContent) }

            // Recheck the back gesture
            handleBackGesture = selectedFiles.isNotEmpty()
                    || (activeFolder.hasParent() && !shouldNavigateToParentTab())

            // Update the bottom bar options
            _bottomOptionsBarState.update {
                it.copy(
                    showQuickOptions = selectedFiles.isNotEmpty(),
                    showMoreOptionsButton = selectedFiles.isNotEmpty(),
                    showEmptyRecycleBinButton = activeFolder is LocalFileHolder &&
                            ((activeFolder as LocalFileHolder).hasParent(globalClass.recycleBinDir) ||
                                    activeFolder.uniquePath == globalClass.recycleBinDir.uniquePath),
                    showRestoreButton = activeFolder is LocalFileHolder &&
                            selectedFiles.isNotEmpty() &&
                            ((activeFolder as LocalFileHolder).hasParent(globalClass.recycleBinDir) ||
                                    activeFolder.uniquePath == globalClass.recycleBinDir.uniquePath)
                )
            }

            withContext(Dispatchers.Main) {
                selectedFilesCount = selectedFiles.size
                updateSelectedFilesSize()

                // Reload the list
                activeFolderContent.clear()
                activeFolderContent.addAll(temp)
                updateContentSnapshot()

                // Update title and subtitle
                requestHomeToolbarUpdate()
            }
        }
    }

    fun onSelectionChange() {
        scope.launch {
            // Recheck the back gesture
            handleBackGesture =
                selectedFiles.isNotEmpty() || (activeFolder.hasParent() && !shouldNavigateToParentTab())

            // Update the bottom bar options
            _bottomOptionsBarState.update {
                it.copy(
                    showQuickOptions = selectedFiles.isNotEmpty(),
                    showMoreOptionsButton = selectedFiles.isNotEmpty(),
                    showEmptyRecycleBinButton = activeFolder is LocalFileHolder &&
                            ((activeFolder as LocalFileHolder).hasParent(globalClass.recycleBinDir) ||
                                    activeFolder.uniquePath == globalClass.recycleBinDir.uniquePath),
                    showRestoreButton = activeFolder is LocalFileHolder &&
                            selectedFiles.isNotEmpty() &&
                            ((activeFolder as LocalFileHolder).hasParent(globalClass.recycleBinDir) ||
                                    activeFolder.uniquePath == globalClass.recycleBinDir.uniquePath)
                )
            }

            withContext(Dispatchers.Main) {
                selectedFilesCount = selectedFiles.size
                updateSelectedFilesSize()
            }

            // Update title and subtitle
            requestHomeToolbarUpdate()
        }
    }

    fun updateSelectedFilesSize() {
        if (selectedFiles.isEmpty()) {
            calculateSizeJob?.cancel()
            calculateSizeJob = null
            selectedFilesTotalSize = 0L
            isCalculatingSelectedSize = false
            return
        }

        val directFilesSize = selectedFiles.values.filter { !it.isFolder }.sumOf { it.size }
        val selectedFolders = selectedFiles.values.filter { it.isFolder }.toList()

        if (selectedFolders.isEmpty()) {
            calculateSizeJob?.cancel()
            calculateSizeJob = null
            selectedFilesTotalSize = directFilesSize
            isCalculatingSelectedSize = false
            return
        }

        selectedFilesTotalSize = directFilesSize
        isCalculatingSelectedSize = true
        calculateSizeJob?.cancel()
        calculateSizeJob = scope.launch(Dispatchers.IO) {
            var totalFolderSize = 0L
            for (folder in selectedFolders) {
                if (!isActive) return@launch
                when (folder) {
                    is LocalFileHolder -> {
                        try {
                            folder.file.walkTopDown().maxDepth(50).onFail { _, _ -> }.forEach { f ->
                                if (!isActive) return@launch
                                if (f.isFile) {
                                    totalFolderSize += f.length()
                                }
                            }
                        } catch (_: Exception) {}
                    }
                    is ZipFileHolder -> {
                        try {
                            folder.node.listFilesAndEmptyDirs().forEach { child ->
                                if (!child.isDirectory) {
                                    totalFolderSize += child.size
                                }
                            }
                        } catch (_: Exception) {}
                    }
                    else -> {
                        try {
                            suspend fun walkGeneric(holder: ContentHolder) {
                                if (!isActive) return
                                holder.listContent().forEach { child ->
                                    if (!isActive) return@forEach
                                    if (child.isFolder) {
                                        walkGeneric(child)
                                    } else {
                                        totalFolderSize += child.size
                                    }
                                }
                            }
                            walkGeneric(folder)
                        } catch (_: Exception) {}
                    }
                }
            }
            if (isActive) {
                withContext(Dispatchers.Main) {
                    selectedFilesTotalSize = directFilesSize + totalFolderSize
                    isCalculatingSelectedSize = false
                }
            }
        }
    }

    fun reloadFiles(postEvent: () -> Unit = {}) {
        scope.launch {
            openFolderImpl(activeFolder) { postEvent() }
        }
    }

    // Called when a new file/folder is created
    fun onNewFileCreated(newFile: ContentHolder, openContent: Boolean = false, context: Context? = null) {
        scope.launch {
            if (openContent && newFile.isFolder) {
                openFolderImpl(newFile)
            } else {
                highlightedFiles.apply {
                    clear()
                    add(newFile.uniquePath)
                }

                reloadFiles {
                    CoroutineScope(Dispatchers.Main).launch {
                        pendingLocateFilePath = newFile.uniquePath
                        if (openContent && !newFile.isFolder) {
                            val ctx = context ?: globalClass
                            openFile(ctx, newFile)
                        }
                    }
                }
            }
        }
    }

    /**
     * Navigates to the parent folder of the given file, highlights it, and scrolls it into view.
     */
    fun locateFile(file: ContentHolder) {
        scope.launch {
            val parent = file.getParent() ?: return@launch
            highlightedFiles.apply {
                clear()
                add(file.uniquePath)
            }
            openFolderImpl(parent) {
                pendingLocateFilePath = file.uniquePath
            }
        }
    }

    fun getFileListState() = contentListStates[activeFolder.uniquePath] ?: LazyGridState().also {
        contentListStates[activeFolder.uniquePath] = it
    }

    private fun createTabLabel(): String {
        val fullName =
            activeFolder.displayName.orIf(globalClass.getString(R.string.internal_storage)) {
                activeFolder.uniquePath == Environment.getExternalStorageDirectory().absolutePath
            }
        return if (fullName.length > 18) fullName.substring(0, 15) + "..." else fullName
    }

    private suspend fun updatePathList() {
        val cleanOnExitPath = globalClass.cleanOnExitDir.uniquePath

        // Walk through parent files
        val paths = generateSequence(activeFolder) {
            runBlocking { it.getParent() }
        }

        // Filter those that are accessible and valid, then reverse the list.
        // Also strip any segments that resolve to the temp extraction cache dir
        // (cleanOnExitDir) — these appear when browsing nested archives and
        // would otherwise expose cache paths in the breadcrumb.
        val newPathSegments = paths
            .filter { holder ->
                holder.canRead
                    && runBlocking { holder.isValid() }
                    && !(holder is LocalFileHolder
                        && holder.uniquePath.startsWith(cleanOnExitPath))
            }
            .toList()
            .reversed()

        if (!currentPathSegments.joinToString(emptyString) { it.displayName }.startsWith(
                newPathSegments.joinToString(emptyString) { it.displayName })
        ) {
            // Update the state to reflect the new path
            withContext(Dispatchers.Main) {
                currentPathSegments = newPathSegments
            }
        } else {
            // validate path segments if not changed
            currentPathSegments = currentPathSegments.filter { it.isValid() }
        }

        highlightedPathSegment = activeFolder
    }

    fun updateDisplayConfig() {
        viewConfig = globalClass.preferencesManager.getViewConfigPrefsFor(activeFolder)
    }

    fun requestNewTab(tab: Tab) {
        globalClass.mainActivityManager.addTabAndSelect(tab)
    }

    /**
     * Shares the selected files.
     * Only local content can be shared, other types must create a local copy first.
     */
    fun shareSelectedFiles(context: Context) {
        val uris = arrayListOf<Uri>()

        selectedFiles.forEach { selectedFile ->
            val content = selectedFile.component2()
            if (content is LocalFileHolder) {
                uris.add(
                    getUriForFile(context, globalClass.packageName + ".provider", content.file)
                )
            }
        }

        if (uris.isEmpty()) return

        val builder = ShareCompat.IntentBuilder(context)
            .setType(if (uris.size == 1) uris[0].getMimeType(globalClass) else anyFileType)
        
        uris.forEach {
            builder.addStream(it)
        }

        val intent = builder.intent.apply {
            if (uris.size > 1) action = Intent.ACTION_SEND_MULTIPLE
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        val chooserIntent = Intent.createChooser(intent, null)
        
        // On Android 14+ (API 34+), the system automatically uses the new share sheet.
        // We can add custom actions or preview data here if needed.
        context.startActivity(chooserIntent)
    }

    fun addToHomeScreen(context: Context, file: LocalFileHolder, shortcutName: String = file.displayName) {
        val shortcutManager = context.getSystemService(ShortcutManager::class.java)
        val targetIntent = if (file.isFolder) {
            Intent(context, MainActivity::class.java).apply {
                action = Intent.ACTION_VIEW
                putExtra("filePath", file.uniquePath)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
        } else {
            val fileUri = file.createUri()
            Intent(context, OpenWithDispatchActivity::class.java).apply {
                action = Intent.ACTION_VIEW
                setDataAndType(fileUri, file.mimeType)
                putExtra("extra_file_path", file.file.absolutePath)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            }
        }
        val pinShortcutInfo = ShortcutInfo
            .Builder(context, file.uniquePath)
            .setIntent(targetIntent)
            .setIcon(
                android.graphics.drawable.Icon.createWithResource(
                    context,
                    if (file.isFile()) R.mipmap.file_shortcut else R.mipmap.folder_shortcut
                )
            )
            .setShortLabel(shortcutName.take(25).ifBlank { file.displayName })
            .build()
        val pinnedShortcutCallbackIntent =
            shortcutManager.createShortcutResultIntent(pinShortcutInfo)
        shortcutManager.requestPinShortcut(
            pinShortcutInfo,
            PendingIntent.getBroadcast(
                context,
                0,
                pinnedShortcutCallbackIntent,
                PendingIntent.FLAG_IMMUTABLE
            ).intentSender
        )
    }

    fun extractZipHolderForPreview(zipFileHolder: ZipFileHolder, onDone: (String) -> Unit) {
        isLoading = true
        scope.launch {
            val newFilePath = zipFileHolder.extractForPreview()
            isLoading = false
            withContext(Dispatchers.Main) {
                onDone(newFilePath)
            }
        }
    }

    fun toggleApkDialog(file: LocalFileHolder?) {
        if (file isNot null) {
            targetFile = file
            _dialogsState.update { it.copy(showApkDialog = true) }
        } else {
            targetFile = null
            _dialogsState.update { it.copy(showApkDialog = false) }
        }
    }

    fun toggleFileOptionsMenu(file: ContentHolder?, clear: Boolean = true) {
        if (file isNot null) {
            targetFile = file
            _dialogsState.update { it.copy(showFileOptionsDialog = true) }
        } else {
            if (clear) targetFile = null
            _dialogsState.update { it.copy(showFileOptionsDialog = false) }
        }
    }

    fun toggleCompressTaskDialog(task: CompressTask?) {
        if (task isNot null) {
            compressTaskHolder = task
            _dialogsState.update { it.copy(showNewZipFileDialog = true) }
        } else {
            _dialogsState.update { it.copy(showNewZipFileDialog = false) }
        }
    }

    fun toggleBookmarksDialog(show: Boolean) {
        _dialogsState.update { it.copy(showBookmarkDialog = show) }
    }

    fun toggleDeleteConfirmationDialog(show: Boolean) {
        _dialogsState.update { it.copy(showConfirmDeleteDialog = show) }
    }

    fun toggleCreateNewFileDialog(show: Boolean) {
        _dialogsState.update { it.copy(showCreateNewFileDialog = show) }
    }

    fun toggleTasksPanel(show: Boolean) {
        _dialogsState.update { it.copy(showTasksPanel = show) }
    }

    fun toggleSortingMenu(show: Boolean) {
        _dialogsState.update { it.copy(showSortingMenu = show) }
    }

    fun toggleViewConfigDialog(show: Boolean) {
        _dialogsState.update { it.copy(showViewConfigDialog = show) }
    }

    fun toggleSearchPenal(show: Boolean) {
        _dialogsState.update { it.copy(showSearchPenal = show) }
    }

    fun toggleRenameDialog(show: Boolean) {
        _dialogsState.update { it.copy(showRenameDialog = show) }
    }

    fun toggleOpenWithDialog(show: Boolean) {
        _dialogsState.update { it.copy(showOpenWithDialog = show) }
    }

    fun toggleFilePropertiesDialog(show: Boolean) {
        _dialogsState.update { it.copy(showFileProperties = show) }
    }

    fun toggleImportPrefsDialog(file: LocalFileHolder?) {
        targetFile = file
        _dialogsState.update { it.copy(showImportPrefsDialog = file != null) }
    }

    fun toggleArchivePasswordDialog(archive: LocalFileHolder?) {
        pendingPasswordArchive = archive
        _dialogsState.update { it.copy(showArchivePasswordDialog = archive != null) }
    }

    fun toggleShareFolderCompressDialog(show: Boolean) {
        _dialogsState.update { it.copy(showShareFolderCompressDialog = show) }
    }

    /**
     * Restores selected files from the recycle bin to their original locations
     * by reading metadata.json from each timestamped recycle bin subfolder.
     */
    fun restoreSelectedFiles() {
        val filesToRestore = selectedFiles.values.toList()
        if (filesToRestore.isEmpty()) return

        unselectAllFiles()
        scope.launch {
            filesToRestore.forEach { file ->
                if (file is LocalFileHolder) {
                    // The file is inside a timestamped folder under .prism/bin/
                    // Read metadata.json from the parent timestamped folder
                    val parentDir = file.file.parentFile ?: return@forEach
                    val metadataFile = File(parentDir, "metadata.json")
                    if (metadataFile.exists()) {
                        try {
                            val json = JSONObject(metadataFile.readText())
                            val items = json.getJSONArray("items")
                            for (i in 0 until items.length()) {
                                val item = items.getJSONObject(i)
                                if (item.getString("name") == file.displayName) {
                                    val originalPath = item.getString("originalPath")
                                    val originalParent = File(originalPath).parentFile
                                    if (originalParent != null) {
                                        originalParent.mkdirs()
                                        val destHolder = LocalFileHolder(originalParent)
                                        globalClass.taskManager.addTaskAndRun(
                                            CopyTask(listOf(file), deleteSourceFiles = true),
                                            CopyTaskParameters(destHolder)
                                        )
                                        break
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            globalClass.showMsg("Failed to restore: ${file.displayName}")
                        }
                    } else {
                        // No metadata - can't determine original path
                        globalClass.showMsg("No restore info for: ${file.displayName}")
                    }
                }
            }
        }
    }
}
