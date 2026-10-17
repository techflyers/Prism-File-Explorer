package com.techflyers.compose.file.explorer.screen.main.tab.files

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.net.Uri
import android.os.Environment
import android.os.Looper
import com.techflyers.compose.file.explorer.screen.main.tab.files.misc.FileSortingPrefs
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
import com.techflyers.compose.file.explorer.screen.main.tab.files.misc.ViewType
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
import java.util.concurrent.atomic.AtomicLong
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import com.techflyers.compose.file.explorer.screen.main.tab.files.misc.FolderHierarchyChecker
import com.techflyers.compose.file.explorer.screen.preferences.constant.DragDropAction

private data class FolderScrollPosition(
    val index: Int,
    val offset: Int,
    val viewType: ViewType
)

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

    var activeFolder: ContentHolder by mutableStateOf(homeDir)
    var sortingPrefs by mutableStateOf(
        globalClass.preferencesManager.getSortingPrefsFor(activeFolder)
    )

    fun updateSortingConfig() {
        val newPrefs = globalClass.preferencesManager.getSortingPrefsFor(activeFolder)
        if (Looper.myLooper() == Looper.getMainLooper()) {
            if (sortingPrefs != newPrefs) {
                sortingPrefs = newPrefs
            }
        } else {
            scope.launch(Dispatchers.Main) {
                if (sortingPrefs != newPrefs) {
                    sortingPrefs = newPrefs
                }
            }
        }
    }

    private data class CachedFolderListing(
        val mtime: Long,
        val childCount: Int,
        val foldersCount: Int,
        val filesCount: Int,
        val showHidden: Boolean,
        val sortingPrefs: FileSortingPrefs,
        val items: List<ContentHolder>
    )

    private val folderListingCache = android.util.LruCache<String, CachedFolderListing>(24)

    fun invalidateFolderListingCache(path: String? = null) {
        if (path != null) {
            folderListingCache.remove(path)
        } else {
            folderListingCache.evictAll()
        }
    }

    fun applySortingChange(previousPrefs: FileSortingPrefs = sortingPrefs) {
        val newPrefs = globalClass.preferencesManager.getSortingPrefsFor(activeFolder)
        if (Looper.myLooper() == Looper.getMainLooper()) {
            sortingPrefs = newPrefs
        } else {
            scope.launch(Dispatchers.Main) {
                sortingPrefs = newPrefs
            }
        }

        val sortOrderChanged = previousPrefs.sortMethod != newPrefs.sortMethod ||
                previousPrefs.reverseSorting != newPrefs.reverseSorting ||
                previousPrefs.showFoldersFirst != newPrefs.showFoldersFirst ||
                newPrefs.sortMethod == com.techflyers.compose.file.explorer.screen.main.tab.files.misc.SortingMethod.SORT_BY_SHUFFLED

        if (!sortOrderChanged) {
            // Only the "Apply to this folder only" flag changed or dialog closed without changes;
            // only refresh the sort icon without refreshing the folder.
            return
        }

        if (globalClass.preferencesManager.onlyRefreshSortIconOnNavigation &&
            activeFolder !is VirtualFileHolder &&
            activeFolderContent.isNotEmpty() &&
            !isLoading
        ) {
            val currentFolder = activeFolder
            val currentItems = activeFolderContent.toList()
            scope.launch(Dispatchers.IO) {
                val sorted = ContentHolder.sortItemsWithPrefs(currentItems, newPrefs)
                if (currentFolder is LocalFileHolder) {
                    folderListingCache.get(currentFolder.uniquePath)?.let { existing ->
                        folderListingCache.put(
                            currentFolder.uniquePath,
                            existing.copy(sortingPrefs = newPrefs, items = sorted)
                        )
                    }
                }
                withContext(Dispatchers.Main) {
                    if (activeFolder.uniquePath == currentFolder.uniquePath) {
                        activeFolderContent.clear()
                        activeFolderContent.addAll(sorted)
                        updateContentSnapshot()
                    }
                }
            }
        } else {
            invalidateFolderListingCache(activeFolder.uniquePath)
            openFolder(activeFolder, rememberListState = false)
        }
    }

    var activeFolderContent = mutableStateListOf<ContentHolder>()
    var contentSnapshot by mutableStateOf<List<ContentHolder>>(emptyList())
    var maxSiblingSize by mutableLongStateOf(1L)
    private var listingJob: Job? = null
    private val listingGeneration = AtomicLong(0L)
    private var lastKnownDirMtime: Long = -1L
    private var lastKnownChildCount: Int = -1
    private var lastKnownCategoryCount: Int = -1
    private var lastKnownCategoryMaxDate: Long = -1L

    fun updateContentSnapshot() {
        val activeFilters = selectedTypeFilters
        val filtered = if (activeFilters.isNotEmpty()) {
            activeFolderContent.filter { item ->
                item.isFolder || activeFilters.any { filter ->
                    matchesTypeFilterExtension(item.extension, filter)
                }
            }
        } else {
            activeFolderContent.toList()
        }
        contentSnapshot = filtered
        maxSiblingSize = filtered.maxOfOrNull { it.size }?.coerceAtLeast(1L) ?: 1L
    }

    private fun matchesTypeFilterExtension(ext: String, filter: String?): Boolean {
        if (filter == null) return false
        val e = ext.lowercase()
        return when (filter) {
            "image" -> e in com.techflyers.compose.file.explorer.screen.main.tab.files.misc.FileMimeType.imageFileType
            "video" -> e in com.techflyers.compose.file.explorer.screen.main.tab.files.misc.FileMimeType.videoFileType
            "audio" -> e in com.techflyers.compose.file.explorer.screen.main.tab.files.misc.FileMimeType.audioFileType
            "document" -> e in com.techflyers.compose.file.explorer.screen.main.tab.files.misc.FileMimeType.documentFileType
            "archive" -> e in com.techflyers.compose.file.explorer.screen.main.tab.files.misc.FileMimeType.archiveFileType
            else -> true
        }
    }

    val contentListStates = hashMapOf<String, LazyGridState>()
    var activeListState by mutableStateOf(LazyGridState())
    val contentColumnListStates = hashMapOf<String, LazyListState>()
    var activeColumnListState by mutableStateOf(LazyListState())
    private val savedFolderScrollPositions = hashMapOf<String, FolderScrollPosition>()
    var pendingScrollRestore by mutableStateOf<Pair<Int, Int>?>(null)
    var onRequestScrollAboveRibbon: ((index: Int) -> Unit)? = null

    fun scrollItemAboveRibbon(index: Int) {
        onRequestScrollAboveRibbon?.invoke(index)
    }

    private fun getCurrentScrollPosition(): FolderScrollPosition = if (viewConfig.viewType == ViewType.GRID) {
        FolderScrollPosition(
            index = activeListState.firstVisibleItemIndex,
            offset = activeListState.firstVisibleItemScrollOffset,
            viewType = ViewType.GRID
        )
    } else {
        FolderScrollPosition(
            index = activeColumnListState.firstVisibleItemIndex,
            offset = activeColumnListState.firstVisibleItemScrollOffset,
            viewType = ViewType.LIST
        )
    }

    private fun recordCurrentScrollPosition() {
        val path = activeFolder.uniquePath
        if (path.isNotEmpty()) savedFolderScrollPositions[path] = getCurrentScrollPosition()
    }

    private fun maxRestorableIndex(itemCount: Int, type: ViewType): Int {
        if (itemCount <= 0) return 0
        return when (type) {
            ViewType.GRID -> itemCount - 1
            ViewType.LIST -> {
                val columns = maxOf(1, viewConfig.columnCount)
                ((itemCount + columns - 1) / columns) - 1
            }
        }.coerceAtLeast(0)
    }

    private fun applyFolderScrollRestore(path: String, rememberListState: Boolean, itemCount: Int) {
        val saved = (if (rememberListState) savedFolderScrollPositions[path] else null)
            ?.takeIf { it.viewType == viewConfig.viewType }
        val visibleItemCount = minOf(itemCount, contentSnapshot.size)
        val maxIndex = maxRestorableIndex(visibleItemCount, viewConfig.viewType)
        val validIndex = saved?.index?.coerceIn(0, maxIndex) ?: 0
        val validOffset = saved?.offset?.coerceAtLeast(0) ?: 0
        val gridIndex = if (viewConfig.viewType == ViewType.GRID) validIndex else 0
        val columnIndex = if (viewConfig.viewType == ViewType.LIST) validIndex else 0
        val gridOffset = if (viewConfig.viewType == ViewType.GRID) validOffset else 0
        val columnOffset = if (viewConfig.viewType == ViewType.LIST) validOffset else 0
        val newGridState = LazyGridState(gridIndex, gridOffset)
        val newColumnState = LazyListState(columnIndex, columnOffset)
        contentListStates[path] = newGridState
        contentColumnListStates[path] = newColumnState
        activeListState = newGridState
        activeColumnListState = newColumnState
        pendingScrollRestore = if (saved != null && visibleItemCount > 0 && (saved.index > 0 || saved.offset > 0)) {
            saved.index to saved.offset.coerceAtLeast(0)
        } else {
            null
        }
    }

    private fun restoreCurrentScrollPositionIfNeeded() {
        if (isLoading) return
        val saved = savedFolderScrollPositions[activeFolder.uniquePath] ?: return
        if (saved.viewType != viewConfig.viewType) return
        val current = getCurrentScrollPosition()
        if (current.index == saved.index && current.offset == saved.offset) return
        val count = contentSnapshot.size
        if (count == 0) return
        val maxIndex = maxRestorableIndex(count, saved.viewType)
        val index = saved.index.coerceIn(0, maxIndex)
        val offset = saved.offset.coerceAtLeast(0)
        if (saved.viewType == ViewType.GRID) {
            val state = LazyGridState(index, offset)
            contentListStates[activeFolder.uniquePath] = state
            activeListState = state
        } else {
            val state = LazyListState(index, offset)
            contentColumnListStates[activeFolder.uniquePath] = state
            activeColumnListState = state
        }
        pendingScrollRestore = if (saved.index > 0 || offset > 0) saved.index to offset else null
    }

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
    /** Multi-select type filters for ordinary folders; an empty set means show all files. */
    var selectedTypeFilters by mutableStateOf<Set<String>>(emptySet())
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
    var copyHereTargetFolder by mutableStateOf<ContentHolder?>(null)
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

    // Unified Gesture Engine state
    var dragDropSession by mutableStateOf<DragDropSession?>(null)
    var transientPressHighlightPath by mutableStateOf<String?>(null)
    var contentViewCoordinates by mutableStateOf<LayoutCoordinates?>(null)
    var pendingDropTransfer by mutableStateOf<PendingDropTransfer?>(null)

    private val dropTargets = mutableMapOf<String, LayoutCoordinates>()
    private val dropTargetFolders = mutableMapOf<String, ContentHolder>()

    fun registerDropTarget(folder: ContentHolder, coordinates: LayoutCoordinates) {
        dropTargets[folder.uniquePath] = coordinates
        dropTargetFolders[folder.uniquePath] = folder
    }

    fun unregisterDropTarget(uniquePath: String) {
        dropTargets.remove(uniquePath)
        dropTargetFolders.remove(uniquePath)
    }

    fun findDropTargetAt(windowPosition: Offset): ContentHolder? {
        for ((path, coords) in dropTargets) {
            if (coords.isAttached) {
                val bounds = coords.boundsInWindow()
                if (bounds.contains(windowPosition)) {
                    return dropTargetFolders[path]
                }
            }
        }
        return null
    }

    fun startDragDrop(items: List<ContentHolder>, startOffset: Offset, initialWindowOffset: Offset) {
        dragDropSession = DragDropSession(items, startOffset, initialWindowOffset)
    }

    fun updateDragDrop(localOffset: Offset, windowOffset: Offset) {
        val target = findDropTargetAt(windowOffset)
        dragDropSession?.updatePosition(localOffset, windowOffset, target)
    }

    fun completeDragDrop() {
        val session = dragDropSession
        dragDropSession = null
        if (session == null) return

        val target = session.hoveredTargetHolder ?: return
        executeDropTransfer(session.items, target)
    }

    fun executeDropTransfer(items: List<ContentHolder>, targetFolder: ContentHolder) {
        if (targetFolder.uniquePath == activeFolder.uniquePath) {
            return
        }

        for (item in items) {
            if (item.isFolder && FolderHierarchyChecker.isChildOrSame(targetFolder.uniquePath, item.uniquePath)) {
                globalClass.showMsg(globalClass.getString(R.string.cannot_move_into_self))
                return
            }
        }

        if (!targetFolder.canAddNewContent) {
            globalClass.showMsg(globalClass.getString(R.string.permission_denied))
            return
        }

        when (globalClass.preferencesManager.dragDropAction) {
            DragDropAction.ALWAYS_MOVE.ordinal -> {
                globalClass.taskManager.addTaskAndRun(
                    CopyTask(items, deleteSourceFiles = true),
                    CopyTaskParameters(targetFolder)
                )
                unselectAllFiles()
            }
            DragDropAction.ALWAYS_COPY.ordinal -> {
                globalClass.taskManager.addTaskAndRun(
                    CopyTask(items, deleteSourceFiles = false),
                    CopyTaskParameters(targetFolder)
                )
                unselectAllFiles()
            }
            else -> {
                pendingDropTransfer = PendingDropTransfer(items, targetFolder)
            }
        }
    }

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

    override fun onTabStopped() {
        recordCurrentScrollPosition()
    }

    override fun onTabResumed() {
        scope.launch {
            withContext(Dispatchers.Main) {
                restoreCurrentScrollPositionIfNeeded()
            }
            validateActiveFolder()
            // Important to clear any information from previous tabs (when switching tabs)
            requestHomeToolbarUpdate()
            // Detect any content changes
            detectFileChanges()
            // Check for display mode change
            updateDisplayConfig()
            updateSortingConfig()
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

    suspend fun pruneInvalidHistory() {
        val validBack = historyBackStack.filter { it.isValid() }
        val validForward = historyForwardStack.filter { it.isValid() }
        withContext(Dispatchers.Main) {
            if (historyBackStack.size != validBack.size) {
                historyBackStack.clear()
                historyBackStack.addAll(validBack)
            }
            if (historyForwardStack.size != validForward.size) {
                historyForwardStack.clear()
                historyForwardStack.addAll(validForward)
            }
        }
    }

    fun canGoBack(): Boolean = historyBackStack.isNotEmpty() || (handleBackGesture && (activeFolder as? LocalFileHolder)?.file?.parentFile != null)

    fun canGoForward(): Boolean = historyForwardStack.isNotEmpty()

    fun goBack(): Boolean {
        if (unselectAnySelectedFiles()) {
            return true
        }
        if (historyBackStack.isNotEmpty()) {
            val current = activeFolder
            scope.launch {
                isNavigatingHistory = true
                try {
                    while (historyBackStack.isNotEmpty()) {
                        val target = withContext(Dispatchers.Main) {
                            historyBackStack.removeAt(historyBackStack.lastIndex)
                        }
                        if (target.isValid()) {
                            withContext(Dispatchers.Main) {
                                if (current.isValid()) {
                                    historyForwardStack.add(current)
                                    if (historyForwardStack.size > 50) {
                                        historyForwardStack.removeAt(0)
                                    }
                                }
                                highlightedFiles.apply {
                                    clear()
                                    add(current.uniquePath)
                                }
                            }
                            openFolderImpl(target, rememberSelectedFiles = false)
                            return@launch
                        }
                    }
                    // If all targets were invalid, validate active folder
                    validateActiveFolder()
                } finally {
                    isNavigatingHistory = false
                }
            }
            return true
        } else if (handleBackGesture && !shouldNavigateToParentTab()) {
            scope.launch {
                val parent = activeFolder.getParent()
                if (parent != null && parent.isValid()) {
                    val current = activeFolder
                    if (current.isValid()) {
                        withContext(Dispatchers.Main) {
                            historyForwardStack.add(current)
                            if (historyForwardStack.size > 50) {
                                historyForwardStack.removeAt(0)
                            }
                            highlightedFiles.apply {
                                clear()
                                add(current.uniquePath)
                            }
                        }
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
            val current = activeFolder
            scope.launch {
                isNavigatingHistory = true
                try {
                    while (historyForwardStack.isNotEmpty()) {
                        val target = withContext(Dispatchers.Main) {
                            historyForwardStack.removeAt(historyForwardStack.lastIndex)
                        }
                        if (target.isValid()) {
                            withContext(Dispatchers.Main) {
                                if (current.isValid()) {
                                    historyBackStack.add(current)
                                    if (historyBackStack.size > 50) {
                                        historyBackStack.removeAt(0)
                                    }
                                }
                            }
                            openFolderImpl(target, rememberSelectedFiles = false)
                            return@launch
                        }
                    }
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
        // Latest-request-wins: cancel in-flight listing so Home/navigation is not dropped (#78)
        val requestGeneration = listingGeneration.incrementAndGet()
        listingJob?.cancel()
        withContext(Dispatchers.Main) {
            if (requestGeneration == listingGeneration.get()) isLoading = false
        }

        // Prevent opening invalid files
        if (!item.isValid()) return
        if (requestGeneration != listingGeneration.get()) return

        if (item is LocalFileHolder && com.techflyers.compose.file.explorer.screen.main.tab.files.service.FolderLockStore.isLocked(item.uniquePath)) {
            pendingLockPath = item.uniquePath
            pendingLockMode = com.techflyers.compose.file.explorer.screen.main.tab.files.ui.dialog.FolderLockDialogMode.UNLOCK
            return
        }

        val previousFolder = withContext(Dispatchers.Main) {
            if (requestGeneration != listingGeneration.get()) return@withContext null
            recordCurrentScrollPosition()
            activeFolder
        } ?: return

        val shouldAddHistory = !isNavigatingHistory &&
                item.uniquePath != previousFolder.uniquePath && previousFolder.isValid()
        if (requestGeneration != listingGeneration.get()) return

        val activated = withContext(Dispatchers.Main) {
            if (requestGeneration != listingGeneration.get()) return@withContext false
            if (shouldAddHistory) {
                historyBackStack.add(previousFolder)
                if (historyBackStack.size > 50) historyBackStack.removeAt(0)
                historyForwardStack.clear()
            }
            if (item is VirtualFileHolder) item.selectedCategory = selectedCategory
            if (item != activeFolder) {
                selectedFormats = emptySet()
                selectedTypeFilters = emptySet()
            }
            activeFolder = item
            updateSortingConfig()
            dropTargets.clear()
            dropTargetFolders.clear()
            dragDropSession = null
            transientPressHighlightPath = null
            true
        }
        if (!activated) return

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

        withContext(Dispatchers.Main) {
            if (requestGeneration != listingGeneration.get()) return@withContext
            if (!rememberListState) {
                savedFolderScrollPositions.remove(item.uniquePath)
                contentListStates.remove(item.uniquePath)
                contentColumnListStates.remove(item.uniquePath)
            }
            activeListState = contentListStates[item.uniquePath] ?: LazyGridState(0, 0)
                .also { contentListStates[item.uniquePath] = it }
            activeColumnListState = contentColumnListStates[item.uniquePath] ?: LazyListState(0, 0)
                .also { contentColumnListStates[item.uniquePath] = it }
        }

        // Get display config for this folder
        withContext(Dispatchers.Main) {
            updateDisplayConfig()
            updateSortingConfig()
        }
        val targetSavedPos = withContext(Dispatchers.Main) {
            if (rememberListState) savedFolderScrollPositions[item.uniquePath] else null
        }

        if (requestGeneration != listingGeneration.get()) return

        // When "Only refresh sort icon on navigation" is enabled, reuse cached folder listings
        // when navigating back and forth between folders whose contents haven't changed on disk,
        // refreshing the sort icon without forcing a full folder reload.
        if (globalClass.preferencesManager.onlyRefreshSortIconOnNavigation && item is LocalFileHolder) {
            val cached = folderListingCache.get(item.uniquePath)
            if (cached != null) {
                val currentMtime = item.file.lastModified()
                val currentChildCount = item.file.list()?.size ?: -1
                val currentShowHidden = globalClass.preferencesManager.showHiddenFiles
                if (cached.mtime == currentMtime &&
                    cached.childCount == currentChildCount &&
                    cached.showHidden == currentShowHidden
                ) {
                    val effectiveSort = globalClass.preferencesManager.getSortingPrefsFor(item)
                    val sameSortOrder = cached.sortingPrefs.sortMethod == effectiveSort.sortMethod &&
                            cached.sortingPrefs.reverseSorting == effectiveSort.reverseSorting &&
                            cached.sortingPrefs.showFoldersFirst == effectiveSort.showFoldersFirst &&
                            effectiveSort.sortMethod != com.techflyers.compose.file.explorer.screen.main.tab.files.misc.SortingMethod.SORT_BY_SHUFFLED
                    val resolvedItems = if (sameSortOrder) {
                        cached.items
                    } else {
                        ContentHolder.sortItemsWithPrefs(cached.items, effectiveSort).also { reSorted ->
                            folderListingCache.put(
                                item.uniquePath,
                                cached.copy(sortingPrefs = effectiveSort, items = reSorted)
                            )
                        }
                    }
                    lastKnownDirMtime = currentMtime
                    lastKnownChildCount = currentChildCount
                    withContext(Dispatchers.Main) {
                        if (requestGeneration != listingGeneration.get()) return@withContext
                        isLoading = false
                        sortingPrefs = effectiveSort
                        foldersCount = cached.foldersCount
                        filesCount = cached.filesCount
                        showCategories = false
                        activeFolderContent.clear()
                        activeFolderContent.addAll(resolvedItems)
                        updateContentSnapshot()
                        applyFolderScrollRestore(item.uniquePath, rememberListState, resolvedItems.size)
                        requestHomeToolbarUpdate()
                        postEvent()
                    }
                    return
                }
            }
        }

        withContext(Dispatchers.Main) {
            if (requestGeneration == listingGeneration.get()) isLoading = true
        }

        listingJob = scope.launch(Dispatchers.IO) {
            if (requestGeneration != listingGeneration.get()) return@launch
            try {
                if (item is LocalFileHolder) {
                    val localFolder = item
                    val dirMtime = localFolder.file.lastModified()
                    val dirChildCount = localFolder.file.list()?.size ?: -1
                    lastKnownDirMtime = dirMtime
                    lastKnownChildCount = dirChildCount

                    val sorted = localFolder.listSortedContent()
                    if (!isActive || requestGeneration != listingGeneration.get()) return@launch

                    val count = localFolder.getContentCount()
                    withContext(Dispatchers.Main) {
                        if (requestGeneration != listingGeneration.get()) return@withContext
                        foldersCount = count.folders
                        filesCount = count.files
                        showCategories = false
                    }

                    val displayItems = if (sorted.size > 10_000) sorted.subList(0, 10_000) else sorted
                    folderListingCache.put(
                        localFolder.uniquePath,
                        CachedFolderListing(
                            mtime = dirMtime,
                            childCount = dirChildCount,
                            foldersCount = count.folders,
                            filesCount = count.files,
                            showHidden = globalClass.preferencesManager.showHiddenFiles,
                            sortingPrefs = globalClass.preferencesManager.getSortingPrefsFor(localFolder),
                            items = displayItems.toList()
                        )
                    )
                    val matchingSavedPos = targetSavedPos?.takeIf { it.viewType == viewConfig.viewType }
                    val initialTake = if (matchingSavedPos != null && matchingSavedPos.index > 0) {
                        val itemsPerSavedPosition = if (viewConfig.viewType == ViewType.LIST) {
                            maxOf(1, viewConfig.columnCount)
                        } else {
                            1
                        }
                        maxOf(
                            256,
                            minOf((matchingSavedPos.index + 64) * itemsPerSavedPosition, displayItems.size)
                        )
                    } else {
                        minOf(256, displayItems.size)
                    }
                    val firstChunk = displayItems.take(initialTake)

                    withContext(Dispatchers.Main) {
                        if (requestGeneration != listingGeneration.get()) return@withContext
                        activeFolderContent.clear()
                        activeFolderContent.addAll(firstChunk)
                        updateContentSnapshot()
                        applyFolderScrollRestore(item.uniquePath, rememberListState, firstChunk.size)
                        requestHomeToolbarUpdate()
                        postEvent()
                    }

                    var offset = initialTake
                    val appendChunkSize = 512
                    while (isActive && requestGeneration == listingGeneration.get() && offset < displayItems.size) {
                        val end = minOf(offset + appendChunkSize, displayItems.size)
                        val nextChunk = displayItems.subList(offset, end)
                        offset = end
                        withContext(Dispatchers.Main) {
                            if (requestGeneration != listingGeneration.get()) return@withContext
                            activeFolderContent.addAll(nextChunk)
                            updateContentSnapshot()
                        }
                        kotlinx.coroutines.yield()
                    }
                } else if (item is VirtualFileHolder) {
                    val vFolder = item
                    val check = StorageProvider.getCategoryQuickCheck(vFolder.type)
                    lastKnownCategoryCount = check.first
                    lastKnownCategoryMaxDate = check.second

                    val firstPage = vFolder.listSortedContent()
                    if (!isActive || requestGeneration != listingGeneration.get()) return@launch

                    withContext(Dispatchers.Main) {
                        if (requestGeneration != listingGeneration.get()) return@withContext
                        activeFolderContent.clear()
                        activeFolderContent.addAll(firstPage)
                        updateContentSnapshot()
                        foldersCount = 0
                        filesCount = activeFolderContent.size
                        categories.clear()
                        categories.addAll(vFolder.getCategories())
                        showCategories = categories.isNotEmpty()
                        applyFolderScrollRestore(item.uniquePath, rememberListState, firstPage.size)
                        requestHomeToolbarUpdate()
                        postEvent()
                    }

                    // Background append for remaining pages (up to 5,000 for media categories, or all items for Unlimited Recent / All Files)
                    val pageCap = if (vFolder.isUnlimitedMode()) Int.MAX_VALUE else 5000
                    var loadedCount = firstPage.size
                    while (isActive && requestGeneration == listingGeneration.get() && vFolder.hasMoreContent && loadedCount < pageCap) {
                        val nextPage = vFolder.loadNextPage()
                        if (!isActive || requestGeneration != listingGeneration.get() || nextPage.isEmpty()) break
                        loadedCount += nextPage.size
                        withContext(Dispatchers.Main) {
                            if (requestGeneration != listingGeneration.get()) return@withContext
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
                    if (isActive && requestGeneration == listingGeneration.get() && globalClass.preferencesManager.deepScanCategories) {
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
                            val existingPaths = withContext(Dispatchers.Main) {
                                if (requestGeneration == listingGeneration.get()) {
                                    activeFolderContent.map { it.uniquePath }.toSet()
                                } else {
                                    emptySet()
                                }
                            }
                            if (requestGeneration != listingGeneration.get()) return@launch
                            CategoryFileScanner.walkStorageRootsBackground(ext, existingPaths) { batch ->
                                if (isActive && requestGeneration == listingGeneration.get() && batch.isNotEmpty()) {
                                    withContext(Dispatchers.Main) {
                                        if (requestGeneration != listingGeneration.get()) return@withContext
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
                    val sorted = item.listSortedContent()
                    if (!isActive || requestGeneration != listingGeneration.get()) return@launch
                    val count = item.getContentCount()
                    withContext(Dispatchers.Main) {
                        if (requestGeneration != listingGeneration.get()) return@withContext
                        activeFolderContent.clear()
                        activeFolderContent.addAll(sorted)
                        updateContentSnapshot()
                        foldersCount = count.folders
                        filesCount = count.files
                        showCategories = false
                        applyFolderScrollRestore(item.uniquePath, rememberListState, sorted.size)
                        requestHomeToolbarUpdate()
                        postEvent()
                    }
                }
            } finally {
                withContext(Dispatchers.Main) {
                    if (requestGeneration == listingGeneration.get()) {
                        isLoading = false
                        requestHomeToolbarUpdate()
                    }
                }
            }
        }
    }

    private fun shouldNavigateToParentTab(folder: ContentHolder = activeFolder): Boolean {
        if (parentTab == null) return false
        if (historyBackStack.isEmpty() && folder.uniquePath == homeDir.uniquePath) {
            return true
        }
        return folder is ZipFileHolder
                && with(folder) {
            runBlocking { getParent()?.uniquePath == zipTree.source.getParent()?.uniquePath }
        }
    }

    suspend fun validateActiveFolder() {
        pruneInvalidHistory()
        pruneInvalidBreadcrumbSegments()
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
                openFolderImpl(it, rememberListState = false)
                return
            }

            if (homeDir.isValid()) {
                openFolderImpl(homeDir, rememberListState = false)
            } else {
                openFolderImpl(StorageProvider.getPrimaryInternalStorage(globalClass).contentHolder, rememberListState = false)
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
            val (busy, folder) = withContext(Dispatchers.Main) {
                isLoading to activeFolder
            }
            if (busy) return@launch

            val folderHasParent = folder.hasParent()
            val navigateToParentTab = shouldNavigateToParentTab(folder)

            withContext(Dispatchers.Main) {
                // All reads of Compose state and mutable selection collections stay on Main.
                if (isLoading || activeFolder.uniquePath != folder.uniquePath) return@withContext

                selectedFilesCount = selectedFiles.size
                updateSelectedFilesSize()
                handleBackGesture = selectedFiles.isNotEmpty()
                        || (folderHasParent && !navigateToParentTab)

                val inRecycleBin = activeFolder is LocalFileHolder &&
                        ((activeFolder as LocalFileHolder).hasParent(globalClass.recycleBinDir) ||
                                activeFolder.uniquePath == globalClass.recycleBinDir.uniquePath)
                _bottomOptionsBarState.update {
                    it.copy(
                        showQuickOptions = selectedFiles.isNotEmpty(),
                        showMoreOptionsButton = selectedFiles.isNotEmpty(),
                        showEmptyRecycleBinButton = inRecycleBin,
                        showRestoreButton = inRecycleBin && selectedFiles.isNotEmpty()
                    )
                }

                // Recompute the immutable UI snapshot without clearing/replacing the live list.
                // Replacing it from a background copy could race a folder listing and lose newer items.
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
        invalidateFolderListingCache(activeFolder.uniquePath)
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

    fun toggleCopyHereMode(target: ContentHolder = activeFolder) {
        copyHereTargetFolder = if (copyHereTargetFolder?.uniquePath == target.uniquePath) {
            null
        } else {
            target
        }
        tabViewLabel = createTabLabel()
    }

    fun exitCopyHereMode() {
        copyHereTargetFolder = null
        tabViewLabel = createTabLabel()
    }

    private fun createTabLabel(): String {
        val folderForLabel = copyHereTargetFolder ?: activeFolder
        val fullName =
            folderForLabel.displayName.orIf(globalClass.getString(R.string.internal_storage)) {
                folderForLabel.uniquePath == Environment.getExternalStorageDirectory().absolutePath
            }
        return if (fullName.length > 18) fullName.substring(0, 15) + "..." else fullName
    }

    fun breadcrumbSegmentKey(holder: ContentHolder): String {
        return when (holder) {
            is ZipFileHolder -> "zip:${holder.zipTree.source.uniquePath}:${holder.uniquePath}"
            else -> "${holder::class.simpleName}:${holder.uniquePath}"
        }
    }

    suspend fun isBreadcrumbSegmentValid(holder: ContentHolder): Boolean {
        val cleanOnExitPath = globalClass.cleanOnExitDir.uniquePath
        return runCatching {
            when (holder) {
                is ZipFileHolder -> {
                    val archiveFile = holder.zipTree.source.file
                    if (!archiveFile.exists() || !archiveFile.isFile) {
                        false
                    } else if (holder.uniquePath.isEmpty()) {
                        true
                    } else if (holder.zipTree.isReady) {
                        holder.zipTree.findNodeByPath(holder.uniquePath) != null
                    } else {
                        true
                    }
                }
                is LocalFileHolder -> {
                    !holder.uniquePath.startsWith(cleanOnExitPath) &&
                            holder.file.exists() &&
                            holder.file.isDirectory &&
                            holder.canRead
                }
                else -> holder.canRead && holder.isValid()
            }
        }.getOrDefault(false)
    }

    suspend fun pruneInvalidBreadcrumbSegments() {
        val existing = withContext(Dispatchers.Main) { currentPathSegments.toList() }
        if (existing.isEmpty()) return
        val validPrefix = ArrayList<ContentHolder>(existing.size)
        for (segment in existing) {
            if (isBreadcrumbSegmentValid(segment)) {
                validPrefix.add(segment)
            } else {
                break
            }
        }
        if (validPrefix.size != existing.size) {
            withContext(Dispatchers.Main) {
                currentPathSegments = validPrefix
            }
        }
    }

    private suspend fun updatePathList() {
        val cleanOnExitPath = globalClass.cleanOnExitDir.uniquePath

        // Walk through parent files
        val paths = mutableListOf<ContentHolder>()
        var curr: ContentHolder? = activeFolder
        while (curr != null) {
            if (curr.canRead && curr.isValid() && !(curr is LocalFileHolder && curr.uniquePath.startsWith(cleanOnExitPath))) {
                paths.add(curr)
            }
            curr = curr.getParent()
        }

        val newAncestorSegments = paths.reversed()
        val existingSegments = withContext(Dispatchers.Main) { currentPathSegments.toList() }

        // Keep forward breadcrumb history (greyed-out child path segments) when navigating up
        // a folder, while validating each trailing segment so deleted archives/folders are pruned
        // immediately without ever causing a crash.
        val mergedSegments = ArrayList<ContentHolder>(maxOf(newAncestorSegments.size, existingSegments.size))
        mergedSegments.addAll(newAncestorSegments)

        if (newAncestorSegments.isNotEmpty() && existingSegments.size > newAncestorSegments.size) {
            val matchesPrefix = newAncestorSegments.indices.all { idx ->
                breadcrumbSegmentKey(existingSegments[idx]) == breadcrumbSegmentKey(newAncestorSegments[idx])
            }
            if (matchesPrefix) {
                for (i in newAncestorSegments.size until existingSegments.size) {
                    val trailing = existingSegments[i]
                    if (isBreadcrumbSegmentValid(trailing)) {
                        mergedSegments.add(trailing)
                    } else {
                        break
                    }
                }
            }
        }

        withContext(Dispatchers.Main) {
            currentPathSegments = mergedSegments
            highlightedPathSegment = activeFolder
        }
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

data class PendingDropTransfer(
    val items: List<ContentHolder>,
    val targetFolder: ContentHolder
)

class DragDropSession(
    val items: List<ContentHolder>,
    val startOffset: Offset,
    initialWindowOffset: Offset
) {
    var currentOffset by mutableStateOf(startOffset)
    var currentWindowOffset by mutableStateOf(initialWindowOffset)
    var hoveredTargetDirectory by mutableStateOf<String?>(null)
    var hoveredTargetHolder by mutableStateOf<ContentHolder?>(null)

    fun updatePosition(localOffset: Offset, windowOffset: Offset, target: ContentHolder?) {
        currentOffset = localOffset
        currentWindowOffset = windowOffset
        hoveredTargetHolder = target
        hoveredTargetDirectory = target?.uniquePath
    }

    fun isValidDropTarget(target: ContentHolder, activeFolder: ContentHolder): Boolean {
        if (target.uniquePath == activeFolder.uniquePath) return false
        if (!target.canAddNewContent) return false
        for (item in items) {
            if (item.isFolder && FolderHierarchyChecker.isChildOrSame(target.uniquePath, item.uniquePath)) {
                return false
            }
        }
        return true
    }
}
