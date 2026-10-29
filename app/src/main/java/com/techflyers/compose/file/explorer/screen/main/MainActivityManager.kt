package com.techflyers.compose.file.explorer.screen.main

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.techflyers.compose.file.explorer.App.Companion.globalClass
import com.techflyers.compose.file.explorer.App.Companion.logger
import com.techflyers.compose.file.explorer.R
import com.techflyers.compose.file.explorer.common.emptyString
import com.techflyers.compose.file.explorer.common.fromJson
import com.techflyers.compose.file.explorer.common.isNot
import com.techflyers.compose.file.explorer.common.padEnd
import com.techflyers.compose.file.explorer.common.printFullStackTrace
import com.techflyers.compose.file.explorer.common.showMsg
import com.techflyers.compose.file.explorer.common.toJson
import com.techflyers.compose.file.explorer.screen.main.model.GithubRelease
import com.techflyers.compose.file.explorer.screen.main.model.ClosedTabEntry
import com.techflyers.compose.file.explorer.screen.main.startup.PlusButtonOverride
import com.techflyers.compose.file.explorer.screen.main.startup.StartupTab
import com.techflyers.compose.file.explorer.screen.main.startup.StartupTabType
import com.techflyers.compose.file.explorer.screen.main.startup.StartupTabs
import com.techflyers.compose.file.explorer.screen.main.tab.Tab
import com.techflyers.compose.file.explorer.screen.main.tab.apps.AppsTab
import com.techflyers.compose.file.explorer.screen.main.tab.files.FilesTab
import com.techflyers.compose.file.explorer.screen.main.tab.files.holder.LocalFileHolder
import com.techflyers.compose.file.explorer.screen.main.tab.files.provider.StorageProvider
import com.techflyers.compose.file.explorer.screen.main.tab.home.HomeTab
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.io.InputStreamReader
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.URL
import java.net.UnknownHostException
import kotlin.math.max
import kotlin.math.min

class MainActivityManager {
    val managerScope = CoroutineScope(Dispatchers.IO)
    private val _state = MutableStateFlow(MainActivityState())
    val state = _state.asStateFlow()

    var newUpdate: GithubRelease? = null

    /**\
     * Loads available storage devices (Internal Storage, SD cards, etc)
     */
    suspend fun updateStorageDevices() {
        val devices = StorageProvider.getStorageDevices(globalClass)
        _state.update {
            it.copy(storageDevices = devices)
        }
    }

    fun refreshStorageDevices() {
        managerScope.launch {
            updateStorageDevices()
        }
    }

    fun setup() {
        refreshStorageDevices()
        StorageProvider.triggerRecentFilesIndexingIfNeeded(forceRebuild = false)
    }

    private val closedTabsHistory = mutableListOf<ClosedTabEntry>()
    var lastHomeTab: HomeTab? = null

    // Cross-tab drag-and-drop coordination
    private val tabDropTargetCoords = java.util.concurrent.ConcurrentHashMap<Int, androidx.compose.ui.layout.LayoutCoordinates>()
    var hoveredTabDropIndex by androidx.compose.runtime.mutableIntStateOf(-1)
    var activeDragSourceTab by androidx.compose.runtime.mutableStateOf<FilesTab?>(null)

    fun registerTabDropTarget(index: Int, coordinates: androidx.compose.ui.layout.LayoutCoordinates) {
        tabDropTargetCoords[index] = coordinates
    }

    fun unregisterTabDropTarget(index: Int) {
        tabDropTargetCoords.remove(index)
        if (hoveredTabDropIndex == index) hoveredTabDropIndex = -1
    }

    fun findTabDropTargetIndexAt(windowPosition: androidx.compose.ui.geometry.Offset): Int {
        val currentTabs = _state.value.tabs
        for ((idx, coords) in tabDropTargetCoords) {
            if (idx in currentTabs.indices && coords.isAttached) {
                val bounds = coords.boundsInWindow()
                if (bounds.contains(windowPosition)) {
                    val targetTab = currentTabs[idx]
                    if (targetTab is FilesTab) {
                        return idx
                    }
                }
            }
        }
        return -1
    }

    fun findTabDropTargetFolderAt(windowPosition: androidx.compose.ui.geometry.Offset): com.techflyers.compose.file.explorer.screen.main.tab.files.holder.ContentHolder? {
        val idx = findTabDropTargetIndexAt(windowPosition)
        hoveredTabDropIndex = idx
        if (idx < 0) return null
        val targetTab = _state.value.tabs.getOrNull(idx) as? FilesTab ?: return null
        return targetTab.activeFolder
    }

    private fun createClosedTabEntry(tab: Tab, originalIndex: Int = -1): ClosedTabEntry {
        return when (tab) {
            is FilesTab -> {
                val folder = tab.activeFolder
                val subtitleText = if (folder is com.techflyers.compose.file.explorer.screen.main.tab.files.holder.VirtualFileHolder &&
                    folder.type == com.techflyers.compose.file.explorer.screen.main.tab.files.holder.VirtualFileHolder.DUPLICATES
                ) {
                    "${folder.customItems?.size ?: 0} files • Comparison / Duplicates"
                } else {
                    folder.displayName
                }
                ClosedTabEntry(
                    title = tab.header.ifEmpty { folder.displayName },
                    subtitle = subtitleText,
                    originalIndex = originalIndex,
                    createTab = { FilesTab(source = folder) }
                )
            }
            is HomeTab -> ClosedTabEntry(
                title = tab.header.ifEmpty { "Home" },
                subtitle = "Home",
                originalIndex = originalIndex,
                createTab = { HomeTab() }
            )
            is AppsTab -> ClosedTabEntry(
                title = tab.header.ifEmpty { "Apps" },
                subtitle = "Apps",
                originalIndex = originalIndex,
                createTab = { AppsTab() }
            )
            is com.techflyers.compose.file.explorer.screen.main.tab.nfile_tools.VaultTab -> ClosedTabEntry(
                title = tab.header.ifEmpty { "Vault" },
                subtitle = "Vault",
                originalIndex = originalIndex,
                createTab = { com.techflyers.compose.file.explorer.screen.main.tab.nfile_tools.VaultTab() }
            )
            is com.techflyers.compose.file.explorer.screen.main.tab.nfile_tools.FtpServerTab -> ClosedTabEntry(
                title = tab.header.ifEmpty { "FTP Server" },
                subtitle = "FTP Server",
                originalIndex = originalIndex,
                createTab = { com.techflyers.compose.file.explorer.screen.main.tab.nfile_tools.FtpServerTab() }
            )
            is com.techflyers.compose.file.explorer.screen.main.tab.nfile_tools.WebSharingTab -> ClosedTabEntry(
                title = tab.header.ifEmpty { "Web Sharing" },
                subtitle = "Web Sharing",
                originalIndex = originalIndex,
                createTab = { com.techflyers.compose.file.explorer.screen.main.tab.nfile_tools.WebSharingTab() }
            )
            is com.techflyers.compose.file.explorer.screen.main.tab.nfile_tools.NetworkConnectionWizardTab -> ClosedTabEntry(
                title = tab.header.ifEmpty { "Network Wizard" },
                subtitle = "Network Connection",
                originalIndex = originalIndex,
                createTab = { com.techflyers.compose.file.explorer.screen.main.tab.nfile_tools.NetworkConnectionWizardTab() }
            )
            is com.techflyers.compose.file.explorer.screen.main.tab.nfile_tools.RemoteExplorerTab -> ClosedTabEntry(
                title = tab.header.ifEmpty { tab.connection.name },
                subtitle = tab.connection.name,
                originalIndex = originalIndex,
                createTab = { com.techflyers.compose.file.explorer.screen.main.tab.nfile_tools.RemoteExplorerTab(tab.connection) }
            )
            is com.techflyers.compose.file.explorer.screen.main.tab.nfile_tools.StorageAnalysisTab -> ClosedTabEntry(
                title = tab.header.ifEmpty { "Storage" },
                subtitle = "Storage Analysis",
                originalIndex = originalIndex,
                createTab = { com.techflyers.compose.file.explorer.screen.main.tab.nfile_tools.StorageAnalysisTab() }
            )
            is com.techflyers.compose.file.explorer.screen.main.tab.nfile_tools.DuplicateFinderTab -> ClosedTabEntry(
                title = tab.header.ifEmpty { "Duplicates" },
                subtitle = "Duplicate Finder",
                originalIndex = originalIndex,
                createTab = { com.techflyers.compose.file.explorer.screen.main.tab.nfile_tools.DuplicateFinderTab() }
            )
            else -> ClosedTabEntry(
                title = tab.header.ifEmpty { "Tab" },
                subtitle = "",
                originalIndex = originalIndex,
                createTab = { HomeTab() }
            )
        }
    }

    fun recordClosedTab(tab: Tab, originalIndex: Int = -1) {
        val entry = createClosedTabEntry(tab, originalIndex)
        synchronized(closedTabsHistory) {
            closedTabsHistory.add(0, entry)
            if (closedTabsHistory.size > 30) {
                closedTabsHistory.removeAt(closedTabsHistory.lastIndex)
            }
        }
    }

    fun recordClosedComparison(
        title: String,
        subtitle: String,
        selectionPaths: List<String>
    ) {
        val entry = ClosedTabEntry(
            title = title,
            subtitle = subtitle,
            createTab = {
                val firstFolder = selectionPaths.firstOrNull()?.let { File(it) }?.let {
                    if (it.isDirectory) it else it.parentFile
                } ?: File("/storage/emulated/0")
                FilesTab(LocalFileHolder(firstFolder)).apply {
                    showSavedComparisonDialog = true
                }
            },
            onReopen = {
                val currentActive = getActiveTab()
                if (currentActive is FilesTab) {
                    currentActive.showSavedComparisonDialog = true
                } else {
                    val existingFilesTabIndex = _state.value.tabs.indexOfFirst { it is FilesTab }
                    if (existingFilesTabIndex >= 0) {
                        selectTabAt(existingFilesTabIndex, true)
                        (_state.value.tabs[existingFilesTabIndex] as? FilesTab)?.showSavedComparisonDialog = true
                    } else {
                        val firstFolder = selectionPaths.firstOrNull()?.let { File(it) }?.let {
                            if (it.isDirectory) it else it.parentFile
                        } ?: File("/storage/emulated/0")
                        addTabAndSelect(
                            FilesTab(LocalFileHolder(firstFolder)).apply {
                                showSavedComparisonDialog = true
                            }
                        )
                    }
                }
            }
        )
        synchronized(closedTabsHistory) {
            closedTabsHistory.removeAll { it.onReopen != null && it.title == title }
            closedTabsHistory.add(0, entry)
            if (closedTabsHistory.size > 30) {
                closedTabsHistory.removeAt(closedTabsHistory.lastIndex)
            }
        }
    }

    fun reopenLastClosedTab(): Boolean {
        val entry = synchronized(closedTabsHistory) {
            if (closedTabsHistory.isEmpty()) return false
            closedTabsHistory.removeAt(0)
        }
        if (entry.onReopen != null) {
            entry.onReopen.invoke()
            return true
        }
        val restoredTab = entry.createTab()
        addTabAndSelect(restoredTab, entry.originalIndex)
        return true
    }

    fun reopenClosedTab(entry: ClosedTabEntry) {
        synchronized(closedTabsHistory) {
            closedTabsHistory.remove(entry)
        }
        if (entry.onReopen != null) {
            entry.onReopen.invoke()
            return
        }
        val restoredTab = entry.createTab()
        addTabAndSelect(restoredTab, entry.originalIndex)
    }

    fun duplicateTabAt(index: Int) {
        val currentTabs = _state.value.tabs
        if (index !in currentTabs.indices) return
        val sourceTab = currentTabs[index]
        val clonedTab = createClosedTabEntry(sourceTab, index + 1).createTab()
        addTabAndSelect(clonedTab, index + 1)
    }

    fun togglePinTabAt(index: Int) {
        val currentTabs = _state.value.tabs
        if (index !in currentTabs.indices) return
        val targetTab = currentTabs[index]
        val activeTab = getActiveTab()
        val newPinnedState = !targetTab.isPinned
        targetTab.isPinned = newPinnedState

        _state.update { state ->
            val mutable = state.tabs.toMutableList()
            mutable.removeAt(index)
            val insertIndex = if (newPinnedState) {
                val lastPinned = mutable.indexOfLast { it.isPinned }
                if (lastPinned == -1) 0 else lastPinned + 1
            } else {
                val lastPinned = mutable.indexOfLast { it.isPinned }
                if (lastPinned == -1) 0 else lastPinned + 1
            }
            mutable.add(insertIndex.coerceIn(0, mutable.size), targetTab)
            val newSelected = activeTab?.let { act -> mutable.indexOfFirst { it.id == act.id }.takeIf { it >= 0 } }
                ?: insertIndex
            state.copy(tabs = mutable, selectedTabIndex = newSelected)
        }
    }

    fun removeClosedTab(entry: ClosedTabEntry) {
        synchronized(closedTabsHistory) {
            closedTabsHistory.remove(entry)
        }
    }

    fun clearClosedTabsHistory() {
        synchronized(closedTabsHistory) {
            closedTabsHistory.clear()
        }
    }

    fun getClosedTabsHistorySnapshot(): List<ClosedTabEntry> {
        return synchronized(closedTabsHistory) {
            closedTabsHistory.toList()
        }
    }

    /**
     * Removes all tabs except the one at the given index (and any pinned tabs).
     *
     * @param tabIndex The index of the tab to keep.
     */
    fun removeOtherTabs(tabIndex: Int) {
        if (tabIndex !in _state.value.tabs.indices) return
        if (tabIndex isNot _state.value.selectedTabIndex) {
            selectTabAt(tabIndex, true)
        }

        val currentTabs = _state.value.tabs
        val tabToKeep = currentTabs[tabIndex]
        val tabsToKeep = currentTabs.filter { it == tabToKeep || it.isPinned }

        currentTabs.forEachIndexed { idx, tab ->
            if (tab !in tabsToKeep) {
                recordClosedTab(tab, idx)
                tab.onTabRemoved()
            }
        }

        val newSelectedIndex = tabsToKeep.indexOfFirst { it.id == tabToKeep.id }.coerceAtLeast(0)
        _state.update {
            it.copy(
                tabs = tabsToKeep,
                selectedTabIndex = newSelectedIndex
            )
        }
    }

    fun removeTabAt(index: Int) {
        if (index !in _state.value.tabs.indices) return

        val tabToRemove = _state.value.tabs[index]
        if (tabToRemove.isPinned) {
            showMsg("Unpin tab before closing")
            return
        }

        // When only a single tab is open, record it and replace with HomeTab
        if (_state.value.tabs.size <= 1) {
            recordClosedTab(tabToRemove, index)
            if (tabToRemove !is HomeTab) {
                replaceCurrentTabWith(HomeTab())
            }
            return
        }

        val currentSelectedIndex = _state.value.selectedTabIndex

        recordClosedTab(tabToRemove, index)

        // Call callbacks on the tab to be removed BEFORE state update
        if (currentSelectedIndex == index) {
            tabToRemove.onTabStopped()
        }
        tabToRemove.onTabRemoved()

        // Calculate new selected tab index
        val newSelectedTabIndex = if (index < currentSelectedIndex) {
            currentSelectedIndex - 1
        } else if (index > currentSelectedIndex) {
            currentSelectedIndex
        } else {
            // Removing the selected tab itself - choose the previous tab if available, otherwise the next one
            max(0, index - 1)
        }

        // Update the state
        _state.update {
            it.copy(
                tabs = _state.value.tabs.filterIndexed { i, _ ->
                    i isNot index
                },
                selectedTabIndex = newSelectedTabIndex
            )
        }

        // Call callbacks on the new selected tab AFTER state update (if it's different from before)
        if (index == currentSelectedIndex) {
            val newSelectedTab = _state.value.tabs[newSelectedTabIndex]
            if (newSelectedTab.isCreated) {
                newSelectedTab.onTabResumed()
            } else {
                newSelectedTab.onTabStarted()
            }
        }
    }

    fun addTabAndSelect(tab: Tab, index: Int = -1) {
        val currentActiveTab = getActiveTab()

        // Stop the active tab BEFORE state update
        currentActiveTab?.onTabStopped()

        val currentTabs = _state.value.tabs
        val firstUnpinned = currentTabs.indexOfFirst { !it.isPinned }.let { if (it == -1) currentTabs.size else it }
        val validatedIndex = if (index isNot -1) {
            max(firstUnpinned, min(index, currentTabs.size))
        } else {
            currentTabs.size
        }

        // Update the state by inserting at validatedIndex
        _state.update {
            val updated = it.tabs.toMutableList().apply {
                add(validatedIndex.coerceIn(0, size), tab)
            }
            it.copy(
                tabs = updated,
                selectedTabIndex = validatedIndex.coerceIn(0, updated.lastIndex)
            )
        }

        // Start the new tab AFTER state update
        if (tab.isCreated) {
            tab.onTabResumed()
        } else {
            tab.onTabStarted()
        }
    }


    /** Opens a tool tab in a new tab with parentTab set for Back (#70). */
    fun openToolInNewTab(tab: Tab) {
        val origin = getActiveTab()
        if (origin != null && origin !== tab) {
            tab.parentTab = origin
        }
        addTabAndSelect(tab)
    }

    fun selectTabAt(index: Int, skipTabRefresh: Boolean = false) {
        // Validate the index
        val validatedIndex = if (index isNot -1) {
            max(0, min(index, _state.value.tabs.lastIndex))
        } else {
            _state.value.tabs.lastIndex
        }

        val currentSelectedIndex = _state.value.selectedTabIndex
        val currentActiveTab = getActiveTab()

        // If the tab is already selected, resume that tab (kind of refreshing the tab)
        if (validatedIndex == currentSelectedIndex) {
            if (!skipTabRefresh) {
                currentActiveTab?.onTabResumed()
            }
        } else {
            // Stop the active tab BEFORE state update
            currentActiveTab?.onTabStopped()

            // Update the state
            _state.update {
                it.copy(selectedTabIndex = validatedIndex)
            }

            // Start the new tab AFTER state update
            val newSelectedTab = _state.value.tabs[validatedIndex]
            if (newSelectedTab.isCreated) {
                newSelectedTab.onTabResumed()
            } else {
                newSelectedTab.onTabStarted()
            }
        }
    }

    fun replaceCurrentTabWith(tab: Tab, keepCurrentTabAsParent: Boolean = false) {
        val currentActiveTab = getActiveTab()
        val currentSelectedIndex = _state.value.selectedTabIndex
        if (currentActiveTab is HomeTab) lastHomeTab = currentActiveTab
        if (tab is HomeTab) lastHomeTab = tab

        // Prevent duplicate tab IDs if `tab` is already open at another index
        val existingIdx = _state.value.tabs.indexOfFirst { it === tab || it.id == tab.id }
        if (existingIdx != -1 && existingIdx != currentSelectedIndex) {
            val target = _state.value.tabs[existingIdx]
            currentActiveTab?.apply {
                onTabStopped()
                if (!keepCurrentTabAsParent) onTabRemoved()
            }
            val newSelected = if (currentSelectedIndex < existingIdx) existingIdx - 1 else existingIdx
            _state.update { state ->
                state.copy(
                    tabs = state.tabs.filterIndexed { i, _ -> i != currentSelectedIndex },
                    selectedTabIndex = newSelected
                )
            }
            if (target.isCreated) target.onTabResumed() else target.onTabStarted()
            return
        }

        // Stop and remove the active tab BEFORE state update
        currentActiveTab?.apply {
            onTabStopped()
            if (!keepCurrentTabAsParent) onTabRemoved()
        }

        if (keepCurrentTabAsParent && currentActiveTab !== tab) {
            tab.parentTab = currentActiveTab
        }

        // Preserve slotId and pin status when replacing in-place so the tab bar and pager
        // keep the exact same slot key without shifting or pushing edge tabs off-screen.
        if (currentActiveTab != null) {
            tab.slotId = currentActiveTab.slotId
            if (currentActiveTab.isPinned) {
                tab.isPinned = true
            }
        }

        // Update the state
        _state.update {
            it.copy(
                tabs = _state.value.tabs.mapIndexed { index, oldTab ->
                    if (index == _state.value.selectedTabIndex) tab else oldTab
                }
            )
        }

        // Start the new tab AFTER state update
        if (tab.isCreated) {
            tab.onTabResumed()
        } else {
            tab.onTabStarted()
        }
    }

    fun jumpToFile(file: LocalFileHolder, context: Context) {
        openFile(file, context)
    }

    private fun openFile(file: LocalFileHolder, context: Context) {
        if (file.exists()) {
            addTabAndSelect(FilesTab(file, context))
        }
    }

    fun resumeActiveTab() {
        getActiveTab()?.onTabResumed()
    }

    fun refreshAllTabs() {
        _state.value.tabs.forEach { tab ->
            if (tab is com.techflyers.compose.file.explorer.screen.main.tab.files.FilesTab) {
                tab.reloadFiles()
            } else {
                tab.onTabResumed()
            }
        }
    }

    fun onResume() {
        resumeActiveTab()
        refreshStorageDevices()
    }

    fun onStop() {
        getActiveTab()?.onTabStopped()
    }

    fun getActiveTab(): Tab? {
        return if (_state.value.tabs.isNotEmpty()) {
            _state.value.tabs[_state.value.selectedTabIndex]
        } else {
            null
        }
    }

    /**
     * Checks if the app can exit.
     *
     * This function checks the following conditions in order:
     * 1. If the active tab handles the back press, the app cannot exit.
     * 2. If the active tab is not the home tab and the "skip home when tab closed" setting is disabled,
     *    the active tab is replaced with the home tab and the app cannot exit.
     * 3. If there is more than one tab and the selected tab is not the first tab,
     *    the active tab is removed and the app cannot exit.
     * 4. If there is only one tab and there are unsaved files in the text editor,
     *    a dialog is shown to save the files and the app cannot exit.
     *
     * If none of the above conditions are met, the app can exit.
     */
    private var isExitingViaBackPress = false

    fun addDefaultOrOverriddenNewTab() {
        val startupTabsObj = try {
            fromJson<StartupTabs>(globalClass.preferencesManager.startupTabs) ?: StartupTabs.default()
        } catch (e: Exception) {
            StartupTabs.default()
        }

        val tabToCreate: Tab = when (startupTabsObj.plusButtonOverride ?: PlusButtonOverride.DEFAULT) {
            PlusButtonOverride.HOME -> HomeTab()
            PlusButtonOverride.APPS -> AppsTab()
            PlusButtonOverride.FILES -> FilesTab(LocalFileHolder(File("/storage/emulated/0")))
            PlusButtonOverride.CUSTOM_FOLDER -> {
                val path = if (!startupTabsObj.plusButtonCustomPath.isNullOrEmpty()) {
                    startupTabsObj.plusButtonCustomPath
                } else {
                    "/storage/emulated/0"
                }
                FilesTab(LocalFileHolder(File(path)))
            }
            PlusButtonOverride.DEFAULT -> {
                val firstTab = startupTabsObj.tabs.firstOrNull()
                when (firstTab?.type) {
                    StartupTabType.FILES -> FilesTab(LocalFileHolder(File(firstTab.extra.ifEmpty { "/storage/emulated/0" })))
                    StartupTabType.APPS -> AppsTab()
                    else -> HomeTab()
                }
            }
        }

        addTabAndSelect(tabToCreate)
    }

    /**
     * Checks if the app can exit.
     *
     * 1. If the active tab handles the back press, the app cannot exit.
     * 2. If the active tab is not the home tab and the "skip home when tab closed" setting is disabled,
     *    the active tab is replaced with the home tab and the app cannot exit.
     * 3. If there is more than one tab and the selected tab is not the first tab,
     *    the active tab is removed and the app cannot exit.
     * 4. If there is only one tab and there are unsaved files in the text editor,
     *    a dialog is shown to save the files and the app cannot exit.
     *
     * If none of the above conditions are met, the app can exit.
     *
     * @return True if the app can exit, false otherwise.
     */
    fun canExit(): Boolean {
        val tabs = _state.value.tabs
        val selectedTabIndex = _state.value.selectedTabIndex

        if (tabs.isEmpty()) {
            return true
        }

        // Handle tabs's onBackPress
        if (getActiveTab()!!.onBackPressed()) {
            return false
        }

        // Replace with parent tab; resolve by stable id if object was closed/reordered (#70)
        val activeForParent = getActiveTab()!!
        val parent = activeForParent.parentTab
        if (parent != null) {
            val existingIndex = _state.value.tabs.indexOfFirst { it === parent || it.id == parent.id }
            if (existingIndex != -1 && existingIndex != selectedTabIndex) {
                recordClosedTab(activeForParent, selectedTabIndex)
                val target = _state.value.tabs[existingIndex]
                activeForParent.onTabStopped()
                activeForParent.onTabRemoved()
                val newSelectedIndex = if (selectedTabIndex < existingIndex) existingIndex - 1 else existingIndex
                _state.update { state ->
                    state.copy(
                        tabs = state.tabs.filterIndexed { i, _ -> i != selectedTabIndex },
                        selectedTabIndex = newSelectedIndex
                    )
                }
                if (target.isCreated) {
                    target.onTabResumed()
                } else {
                    target.onTabStarted()
                }
                return false
            } else if (existingIndex == -1) {
                recordClosedTab(activeForParent, selectedTabIndex)
                replaceCurrentTabWith(parent, false)
                return false
            }
            activeForParent.parentTab = null
        }

        // Replace the active tab with the home tab in-place (if turned on in settings)
        if (getActiveTab() !is HomeTab && !globalClass.preferencesManager.skipHomeWhenTabClosed) {
            isExitingViaBackPress = true
            recordClosedTab(activeForParent, selectedTabIndex)
            val reusableHome = lastHomeTab?.takeIf { home ->
                _state.value.tabs.none { it === home || it.id == home.id }
            } ?: HomeTab()
            replaceCurrentTabWith(reusableHome)
            return false
        }

        // Remove the active tab
        if (tabs.size > 1 && selectedTabIndex isNot 0 && globalClass.preferencesManager.closeTabOnBackPress) {
            removeTabAt(selectedTabIndex)
            return false
        }

        // Check TextEditor files
        if (tabs.size == 1 && !allTextEditorFileInstancesSaved()) {
            _state.update {
                it.copy(
                    showSaveEditorFilesDialog = true
                )
            }
            return false
        }

        return true
    }

    private fun allTextEditorFileInstancesSaved(): Boolean {
        globalClass.textEditorManager.fileInstanceList.forEach {
            if (it.requireSave) return false
        }
        return true
    }

    fun ignoreTextEditorFiles() {
        globalClass.textEditorManager.fileInstanceList.clear()
    }

    fun saveTextEditorFiles(onFinish: () -> Unit) {
        _state.update {
            it.copy(
                isSavingFiles = true
            )
        }

        managerScope.launch {
            globalClass.textEditorManager.fileInstanceList.apply {
                forEach {
                    if (it.requireSave) {
                        it.file.writeText(it.content.toString())
                    }
                }
                clear()
            }

            _state.update {
                it.copy(
                    isSavingFiles = false
                )
            }

            onFinish()
        }
    }

    fun clearTabsForExit() {
        _state.update {
            it.copy(
                tabs = emptyList(),
                selectedTabIndex = 0
            )
        }
    }

    fun saveSession() {
        if (isExitingViaBackPress) return

        val startupTabs = arrayListOf<StartupTab>()
        val currentState = _state.value
        currentState.tabs.forEach { tab ->
            when (tab) {
                is FilesTab -> {
                    val folder = tab.activeFolder
                    val fromHome = tab.parentTab is HomeTab
                    val encodedExtra = when (folder) {
                        is com.techflyers.compose.file.explorer.screen.main.tab.files.holder.VirtualFileHolder -> {
                            if (fromHome) {
                                "virtual_from_home:${folder.type}"
                            } else {
                                "virtual:${folder.type}"
                            }
                        }
                        else -> {
                            val path = folder.uniquePath.ifEmpty { "/storage/emulated/0" }
                            if (fromHome) "local_from_home:$path" else path
                        }
                    }
                    startupTabs.add(
                        StartupTab(
                            type = StartupTabType.FILES,
                            extra = encodedExtra,
                            isPinned = tab.isPinned
                        )
                    )
                }

                is AppsTab -> {
                    startupTabs.add(
                        StartupTab(
                            type = StartupTabType.APPS,
                            isPinned = tab.isPinned
                        )
                    )
                }

                is HomeTab -> {
                    startupTabs.add(
                        StartupTab(
                            type = StartupTabType.HOME,
                            isPinned = tab.isPinned
                        )
                    )
                }

                is com.techflyers.compose.file.explorer.screen.main.tab.nfile_tools.VaultTab -> {
                    startupTabs.add(
                        StartupTab(
                            type = StartupTabType.FILES,
                            extra = "tool:vault",
                            isPinned = tab.isPinned
                        )
                    )
                }

                is com.techflyers.compose.file.explorer.screen.main.tab.nfile_tools.FtpServerTab -> {
                    startupTabs.add(
                        StartupTab(
                            type = StartupTabType.FILES,
                            extra = "tool:ftp",
                            isPinned = tab.isPinned
                        )
                    )
                }

                is com.techflyers.compose.file.explorer.screen.main.tab.nfile_tools.WebSharingTab -> {
                    startupTabs.add(
                        StartupTab(
                            type = StartupTabType.FILES,
                            extra = "tool:web_share",
                            isPinned = tab.isPinned
                        )
                    )
                }

                is com.techflyers.compose.file.explorer.screen.main.tab.nfile_tools.StorageAnalysisTab -> {
                    startupTabs.add(
                        StartupTab(
                            type = StartupTabType.FILES,
                            extra = "tool:storage_analysis",
                            isPinned = tab.isPinned
                        )
                    )
                }

                is com.techflyers.compose.file.explorer.screen.main.tab.nfile_tools.DuplicateFinderTab -> {
                    startupTabs.add(
                        StartupTab(
                            type = StartupTabType.FILES,
                            extra = "tool:duplicate_finder",
                            isPinned = tab.isPinned
                        )
                    )
                }
            }
        }
        if (startupTabs.isNotEmpty()) {
            val savedIndex = currentState.selectedTabIndex.coerceIn(0, startupTabs.lastIndex)
            globalClass.preferencesManager.lastSessionTabs = StartupTabs(
                tabs = startupTabs,
                selectedTabIndex = savedIndex
            ).toJson()
        }
    }

    private fun instantiateStartupTab(tab: StartupTab?): Tab {
        if (tab == null) return HomeTab()
        val restored: Tab = when (tab.type) {
            StartupTabType.FILES -> {
                val extra = tab.extra
                when {
                    extra.startsWith("virtual_from_home:") -> {
                        val vType = extra.removePrefix("virtual_from_home:").toIntOrNull()
                            ?: com.techflyers.compose.file.explorer.screen.main.tab.files.holder.VirtualFileHolder.RECENT
                        FilesTab(com.techflyers.compose.file.explorer.screen.main.tab.files.holder.VirtualFileHolder(vType)).apply {
                            parentTab = HomeTab()
                        }
                    }
                    extra.startsWith("virtual:") -> {
                        val vType = extra.removePrefix("virtual:").toIntOrNull()
                            ?: com.techflyers.compose.file.explorer.screen.main.tab.files.holder.VirtualFileHolder.RECENT
                        FilesTab(com.techflyers.compose.file.explorer.screen.main.tab.files.holder.VirtualFileHolder(vType))
                    }
                    extra.startsWith("local_from_home:") -> {
                        val path = extra.removePrefix("local_from_home:").ifEmpty { "/storage/emulated/0" }
                        FilesTab(LocalFileHolder(File(path))).apply {
                            parentTab = HomeTab()
                        }
                    }
                    extra == "tool:vault" -> com.techflyers.compose.file.explorer.screen.main.tab.nfile_tools.VaultTab()
                    extra == "tool:ftp" -> com.techflyers.compose.file.explorer.screen.main.tab.nfile_tools.FtpServerTab()
                    extra == "tool:web_share" -> com.techflyers.compose.file.explorer.screen.main.tab.nfile_tools.WebSharingTab()
                    extra == "tool:storage_analysis" -> com.techflyers.compose.file.explorer.screen.main.tab.nfile_tools.StorageAnalysisTab()
                    extra == "tool:duplicate_finder" -> com.techflyers.compose.file.explorer.screen.main.tab.nfile_tools.DuplicateFinderTab()
                    else -> {
                        val safePath = if (extra.isEmpty() || !extra.startsWith("/")) {
                            "/storage/emulated/0"
                        } else {
                            extra
                        }
                        FilesTab(LocalFileHolder(File(safePath)))
                    }
                }
            }
            StartupTabType.APPS -> AppsTab()
            else -> HomeTab()
        }
        restored.isPinned = tab.isPinned
        return restored
    }

    fun loadStartupTabs(restoreFromBackgroundKill: Boolean = false) {
        isExitingViaBackPress = false
        managerScope.launch {
            val configuredStartupTabsObj: StartupTabs =
                fromJson(globalClass.preferencesManager.startupTabs) ?: StartupTabs.default()

            val shouldRestoreSession = globalClass.preferencesManager.rememberLastSession || restoreFromBackgroundKill
            val startupTabsObj: StartupTabs =
                if (shouldRestoreSession) {
                    val lastSessionObj: StartupTabs? = fromJson(globalClass.preferencesManager.lastSessionTabs)
                    if (lastSessionObj != null && lastSessionObj.tabs.isNotEmpty()) {
                        lastSessionObj
                    } else {
                        configuredStartupTabsObj
                    }
                } else {
                    configuredStartupTabsObj
                }

            val tabs = arrayListOf<Tab>()

            startupTabsObj.tabs.forEach { tab ->
                tabs.add(instantiateStartupTab(tab))
            }

            if (tabs.isEmpty()) {
                val firstConfigured = configuredStartupTabsObj.tabs.firstOrNull()
                tabs.add(instantiateStartupTab(firstConfigured))
            }

            val index = if (shouldRestoreSession) {
                startupTabsObj.selectedTabIndex.coerceIn(0, tabs.lastIndex)
            } else {
                0
            }

            // Update the state first
            _state.update {
                it.copy(
                    tabs = tabs,
                    selectedTabIndex = index
                )
            }

            // Call callbacks on the selected tab AFTER state update
            val activeTab = tabs.getOrNull(index)
            if (activeTab != null) {
                if (activeTab.isCreated) {
                    activeTab.onTabResumed()
                } else {
                    activeTab.onTabStarted()
                }
            }
        }
    }

    fun checkForUpdate() {
        fetchGithubReleases { releases ->
            val latestRelease = releases.firstOrNull() ?: return@fetchGithubReleases
            val latestVersionName = latestRelease.tagName

            try {
                val currentVersionName = globalClass.packageManager.getPackageInfo(
                    globalClass.packageName,
                    0
                ).versionName ?: emptyString

                val latestVersion = parseVersion(latestVersionName)
                val currentVersion = parseVersion(currentVersionName)

                if (isNewerVersion(latestVersion, currentVersion)) {
                    newUpdate = latestRelease
                    _state.update { it.copy(hasNewUpdate = true) }
                    showMsg(R.string.new_update_available)
                }
            } catch (_: Exception) {
            }
        }
    }

    private fun isNewerVersion(latestVersion: List<Int>, currentVersion: List<Int>): Boolean {
        // Determine the length of the longest version array to pad the shorter one.
        val componentCount = max(latestVersion.size, currentVersion.size)

        // Pad both lists to the same size with zeros. This handles cases like "2.0" vs "2.0.1".
        val latestPadded = latestVersion.padEnd(componentCount, 0)
        val currentPadded = currentVersion.padEnd(componentCount, 0)

        for (i in 0 until componentCount) {
            if (latestPadded[i] > currentPadded[i]) {
                return true // latest is newer (e.g., 1.10.0 vs 1.9.0)
            }
            if (latestPadded[i] < currentPadded[i]) {
                return false // current is newer or same, so latest is not an update
            }
        }
        return false // Versions are identical
    }

    private fun parseVersion(versionName: String): List<Int> {
        return versionName.removePrefix("v")
            .split(".")
            .map { it.toIntOrNull() ?: 0 } // Use toIntOrNull for safety
    }

    fun fetchGithubReleases(
        onResult: (List<GithubRelease>) -> Unit
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            val url = "https://api.github.com/repos/techflyers/Prism-File-Explorer/releases"
            var releases = emptyList<GithubRelease>()

            try {
                val connection = URL(url).openConnection() as HttpURLConnection
                connection.apply {
                    requestMethod = "GET"
                    connectTimeout = 10000
                    readTimeout = 5000
                    setRequestProperty("Accept", "application/vnd.github.v3+json")
                    setRequestProperty("User-Agent", "Prism-File-Explorer")
                }

                if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                    connection.inputStream.use { inputStream ->
                        releases = Gson().fromJson(
                            InputStreamReader(
                                inputStream
                            ),
                            object : TypeToken<List<GithubRelease>>() {}.type
                        )
                    }
                }
            } catch (_: UnknownHostException) {
                logger.logInfo(globalClass.getString(R.string.check_for_updates_failed_no_internet_connection))
            } catch (_: ConnectException) {
                logger.logInfo(globalClass.getString(R.string.check_for_updates_failed_failed_to_connect_to_server))
            } catch (e: Exception) {
                logger.logError(e.printFullStackTrace())
            }

            onResult(releases)
        }
    }

    fun toggleJumpToPathDialog(show: Boolean) {
        _state.update {
            it.copy(
                showJumpToPathDialog = show
            )
        }
    }

    fun toggleAppInfoDialog(show: Boolean) {
        _state.update {
            it.copy(
                showAppInfoDialog = show
            )
        }
    }

    fun toggleSaveEditorFilesDialog(show: Boolean) {
        _state.update {
            it.copy(
                showSaveEditorFilesDialog = show
            )
        }
    }

    fun toggleStartupTabsDialog(show: Boolean) {
        _state.update {
            it.copy(
                showStartupTabsDialog = show
            )
        }
    }

    fun reorderTabs(from: Int, to: Int) {
        val active = getActiveTab()
        _state.update { state ->
            val newTabs = state.tabs.toMutableList().apply {
                add(to, removeAt(from))
            }
            val newSelected = active?.let { t -> newTabs.indexOf(t).takeIf { it >= 0 } }
                ?: state.selectedTabIndex.coerceIn(0, newTabs.lastIndex.coerceAtLeast(0))
            state.copy(tabs = newTabs, selectedTabIndex = newSelected)
        }
    }

    fun updateHomeToolbar(title: String, subtitle: String) {
        _state.update {
            it.copy(title = title, subtitle = subtitle)
        }
    }
}