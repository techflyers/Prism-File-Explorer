package com.techflyers.compose.file.explorer.screen.main.tab.home

import androidx.compose.foundation.ScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.FolderCopy
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.InstallMobile
import androidx.compose.material.icons.rounded.VideoFile
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.techflyers.compose.file.explorer.App.Companion.globalClass
import com.techflyers.compose.file.explorer.R
import com.techflyers.compose.file.explorer.common.emptyString
import com.techflyers.compose.file.explorer.screen.main.tab.Tab
import com.techflyers.compose.file.explorer.screen.main.tab.apps.AppsTab
import com.techflyers.compose.file.explorer.screen.main.tab.files.FilesTab
import com.techflyers.compose.file.explorer.screen.main.tab.files.holder.LocalFileHolder
import com.techflyers.compose.file.explorer.screen.main.tab.files.holder.VirtualFileHolder
import com.techflyers.compose.file.explorer.screen.main.tab.files.holder.VirtualFileHolder.Companion.ALL_FILES
import com.techflyers.compose.file.explorer.screen.main.tab.files.holder.VirtualFileHolder.Companion.ARCHIVE
import com.techflyers.compose.file.explorer.screen.main.tab.files.holder.VirtualFileHolder.Companion.AUDIO
import com.techflyers.compose.file.explorer.screen.main.tab.files.holder.VirtualFileHolder.Companion.DOCUMENT
import com.techflyers.compose.file.explorer.screen.main.tab.files.holder.VirtualFileHolder.Companion.IMAGE
import com.techflyers.compose.file.explorer.screen.main.tab.files.holder.VirtualFileHolder.Companion.RECENT
import com.techflyers.compose.file.explorer.screen.main.tab.files.holder.VirtualFileHolder.Companion.VIDEO
import com.techflyers.compose.file.explorer.screen.main.tab.files.provider.StorageProvider
import com.techflyers.compose.file.explorer.screen.main.tab.home.holder.HomeCategory
import com.techflyers.compose.file.explorer.screen.main.tab.home.holder.RecentFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class HomeTab : Tab() {
    override val id = globalClass.generateUid()
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var recentFilesJob: Job? = null
    override val header = globalClass.getString(R.string.home_tab_header)
    val recentFiles = mutableStateListOf<RecentFile>()
    var isLoadingRecentFiles by mutableStateOf(true)
    var recentFilesLoadingProgress by androidx.compose.runtime.mutableFloatStateOf(0f)
    val pinnedFiles = mutableStateListOf<LocalFileHolder>()
    val scrollState = ScrollState(0)

    var showCustomizeHomeTabDialog by mutableStateOf(false)
    var showCustomizeCategoriesDialog by mutableStateOf(false)
    var showSearchDialog by mutableStateOf(false)
    private var cachedSearchTab: FilesTab? = null

    fun getOrCreateSearchTab(): FilesTab {
        return cachedSearchTab ?: FilesTab(
            StorageProvider.getPrimaryInternalStorage(globalClass).contentHolder
        ).apply {
            isHomeSearchTab = true
            cachedSearchTab = this
        }
    }

    override fun onTabStarted() {
        super.onTabStarted()
        requestHomeToolbarUpdate()
        globalClass.mainActivityManager.refreshStorageDevices()
        fetchRecentFiles()
    }

    override fun onTabResumed() {
        super.onTabResumed()
        requestHomeToolbarUpdate()
        globalClass.mainActivityManager.refreshStorageDevices()
        refreshRecentFiles(forceInvalidate = false)
    }

    override suspend fun getSubtitle() = emptyString

    override suspend fun getTitle() = globalClass.getString(R.string.home_tab_title)

    suspend fun loadPinnedFiles() {
        val holders = withContext(Dispatchers.IO) {
            globalClass.preferencesManager.pinnedFiles.map { LocalFileHolder(File(it)) }
        }
        withContext(Dispatchers.Main) {
            if (pinnedFiles.size != holders.size ||
                pinnedFiles.indices.any { idx -> pinnedFiles[idx].uniquePath != holders[idx].uniquePath }
            ) {
                pinnedFiles.clear()
                pinnedFiles.addAll(holders)
            }
        }
    }

    fun getPinnedFiles() {
        scope.launch {
            loadPinnedFiles()
        }
    }

    fun isRecentDisabledOnHome(): Boolean {
        val prefs = globalClass.preferencesManager
        return prefs.disableRecentOnHomeTab && (prefs.showRecentInSidebar || prefs.showRecentInCategories)
    }

    private fun applyRecentFilesOnMain(files: List<RecentFile>) {
        val isSame = recentFiles.size == files.size &&
                recentFiles.indices.all { i ->
                    recentFiles[i].path == files[i].path &&
                            recentFiles[i].lastModified == files[i].lastModified
                }
        if (!isSame) {
            recentFiles.clear()
            recentFiles.addAll(files)
        }
        recentFilesLoadingProgress = 1f
        isLoadingRecentFiles = false
    }

    fun fetchRecentFiles() {
        if (isRecentDisabledOnHome()) {
            isLoadingRecentFiles = false
            recentFilesLoadingProgress = 1f
            return
        }
        if (globalClass.preferencesManager.recentFilesLimit == 0) {
            recentFiles.clear()
            isLoadingRecentFiles = false
            recentFilesLoadingProgress = 1f
            return
        }
        if (recentFiles.isNotEmpty() || recentFilesJob?.isActive == true) return

        isLoadingRecentFiles = true
        recentFilesLoadingProgress = 0.18f
        recentFilesJob = scope.launch {
            try {
                val files = getRecentFiles { progress ->
                    recentFilesLoadingProgress = progress
                }
                if (isActive) {
                    withContext(Dispatchers.Main) {
                        applyRecentFilesOnMain(files)
                    }
                }
            } finally {
                if (isActive) {
                    withContext(Dispatchers.Main) {
                        recentFilesLoadingProgress = 1f
                        isLoadingRecentFiles = false
                    }
                }
            }
        }
    }

    fun refreshRecentFiles(forceInvalidate: Boolean = true) {
        if (isRecentDisabledOnHome()) {
            recentFilesJob?.cancel()
            recentFiles.clear()
            isLoadingRecentFiles = false
            recentFilesLoadingProgress = 1f
            return
        }
        if (globalClass.preferencesManager.recentFilesLimit == 0) {
            recentFilesJob?.cancel()
            recentFiles.clear()
            isLoadingRecentFiles = false
            recentFilesLoadingProgress = 1f
            return
        }
        if (forceInvalidate) {
            StorageProvider.invalidateRecentFilesCache()
        }
        recentFilesJob?.cancel()
        isLoadingRecentFiles = true
        recentFilesLoadingProgress = 0.18f
        recentFilesJob = scope.launch {
            try {
                val files = getRecentFiles { progress ->
                    recentFilesLoadingProgress = progress
                }
                if (isActive) {
                    withContext(Dispatchers.Main) {
                        applyRecentFilesOnMain(files)
                    }
                }
            } finally {
                if (isActive) {
                    withContext(Dispatchers.Main) {
                        recentFilesLoadingProgress = 1f
                        isLoadingRecentFiles = false
                    }
                }
            }
        }
    }

    suspend fun refreshRecentFilesSuspend(forceInvalidate: Boolean = true) {
        if (isRecentDisabledOnHome()) {
            recentFilesJob?.cancel()
            withContext(Dispatchers.Main) {
                recentFiles.clear()
                recentFilesLoadingProgress = 1f
                isLoadingRecentFiles = false
            }
            return
        }
        if (globalClass.preferencesManager.recentFilesLimit == 0) {
            recentFilesJob?.cancel()
            withContext(Dispatchers.Main) {
                recentFiles.clear()
                recentFilesLoadingProgress = 1f
                isLoadingRecentFiles = false
            }
            return
        }
        if (forceInvalidate) {
            StorageProvider.invalidateRecentFilesCache()
        }
        recentFilesJob?.cancel()
        withContext(Dispatchers.Main) {
            isLoadingRecentFiles = true
            recentFilesLoadingProgress = 0.18f
        }
        val job = scope.launch {
            try {
                val files = getRecentFiles { progress ->
                    recentFilesLoadingProgress = progress
                }
                if (isActive) {
                    withContext(Dispatchers.Main) {
                        applyRecentFilesOnMain(files)
                    }
                }
            } finally {
                withContext(Dispatchers.Main) {
                    recentFilesLoadingProgress = 1f
                    isLoadingRecentFiles = false
                }
            }
        }
        recentFilesJob = job
        job.join()
    }

    fun removeRecentFile(path: String) {
        val canonicalPath = runCatching { File(path).canonicalPath }.getOrDefault(path)
        recentFiles.removeAll { recent ->
            runCatching { File(recent.path).canonicalPath }.getOrDefault(recent.path) == canonicalPath
        }
    }

    fun getAllAvailableCategories(): List<Pair<String, HomeCategory>> {
        val mainActivityManager = globalClass.mainActivityManager
        return listOf(
            "recent" to HomeCategory(
                name = globalClass.getString(R.string.recent_files),
                icon = Icons.Rounded.History,
                onClick = {
                    mainActivityManager.replaceCurrentTabWith(
                        FilesTab(VirtualFileHolder(RECENT)),
                        keepCurrentTabAsParent = true
                    )
                }
            ),
            "all_files" to HomeCategory(
                name = "All Files",
                icon = Icons.Rounded.FolderCopy,
                onClick = {
                    mainActivityManager.replaceCurrentTabWith(
                        FilesTab(VirtualFileHolder(ALL_FILES)),
                        keepCurrentTabAsParent = true
                    )
                }
            ),
            "images" to HomeCategory(
                name = globalClass.getString(R.string.images),
                icon = Icons.Rounded.Image,
                onClick = {
                    mainActivityManager.replaceCurrentTabWith(
                        FilesTab(VirtualFileHolder(IMAGE)),
                        keepCurrentTabAsParent = true
                    )
                }
            ),
            "videos" to HomeCategory(
                name = globalClass.getString(R.string.videos),
                icon = Icons.Rounded.VideoFile,
                onClick = {
                    mainActivityManager.replaceCurrentTabWith(
                        FilesTab(VirtualFileHolder(VIDEO)),
                        keepCurrentTabAsParent = true
                    )
                }
            ),
            "audios" to HomeCategory(
                name = globalClass.getString(R.string.audios),
                icon = Icons.Rounded.AudioFile,
                onClick = {
                    mainActivityManager.replaceCurrentTabWith(
                        FilesTab(VirtualFileHolder(AUDIO)),
                        keepCurrentTabAsParent = true
                    )
                }
            ),
            "documents" to HomeCategory(
                name = globalClass.getString(R.string.documents),
                icon = Icons.AutoMirrored.Rounded.InsertDriveFile,
                onClick = {
                    mainActivityManager.replaceCurrentTabWith(
                        FilesTab(VirtualFileHolder(DOCUMENT)),
                        keepCurrentTabAsParent = true
                    )
                }
            ),
            "archives" to HomeCategory(
                name = globalClass.getString(R.string.archives),
                icon = Icons.Rounded.Archive,
                onClick = {
                    mainActivityManager.replaceCurrentTabWith(
                        FilesTab(VirtualFileHolder(ARCHIVE)),
                        keepCurrentTabAsParent = true
                    )
                }
            ),
            "apk" to HomeCategory(
                name = globalClass.getString(R.string.apk_and_bundles),
                icon = Icons.Rounded.InstallMobile,
                onClick = {
                    mainActivityManager.replaceCurrentTabWith(
                        FilesTab(VirtualFileHolder(VirtualFileHolder.APK)),
                        keepCurrentTabAsParent = true
                    )
                }
            ),
            "apps" to HomeCategory(
                name = globalClass.getString(R.string.apps),
                icon = Icons.Rounded.Android,
                onClick = {
                    mainActivityManager.replaceCurrentTabWith(
                        AppsTab(),
                        keepCurrentTabAsParent = true
                    )
                }
            )
        )
    }

    fun getMainCategories(): List<HomeCategory> {
        val prefs = globalClass.preferencesManager
        val allMap = getAllAvailableCategories().toMap()
        val disabled = prefs.disabledHomeCategories
        val order = prefs.homeCategoriesOrder
        return order.mapNotNull { id ->
            if (id == "recent" && !prefs.showRecentInCategories) return@mapNotNull null
            if (id in disabled) return@mapNotNull null
            allMap[id]
        }
    }

    private fun getRecentFiles(onProgress: ((Float) -> Unit)? = null): ArrayList<RecentFile> {
        val configuredLimit = globalClass.preferencesManager.recentFilesLimit
        if (configuredLimit == 0) return arrayListOf()
        val carouselLimit = if (configuredLimit in 1..30) configuredLimit else 30
        onProgress?.invoke(0.45f)
        return arrayListOf<RecentFile>().apply {
            addAll(
                StorageProvider.getRawRecentFiles(
                    limit = carouselLimit
                )
            )
            onProgress?.invoke(0.92f)
        }
    }

    fun removePinnedFile(file: LocalFileHolder) {
        pinnedFiles.remove(file)
        globalClass.preferencesManager.pinnedFiles = pinnedFiles.map { it.uniquePath }
        globalClass.preferencesManager.pinnedFileNames =
            globalClass.preferencesManager.pinnedFileNames - file.uniquePath
    }
}
