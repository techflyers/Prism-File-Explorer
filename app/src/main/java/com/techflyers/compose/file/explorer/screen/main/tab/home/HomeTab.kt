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
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

class HomeTab : Tab() {
    override val id = globalClass.generateUid()
    val scope = CoroutineScope(Dispatchers.IO)
    private var recentFilesJob: Job? = null
    override val header = globalClass.getString(R.string.home_tab_header)
    val recentFiles = mutableStateListOf<RecentFile>()
    var isLoadingRecentFiles by mutableStateOf(true)
    val pinnedFiles = arrayListOf<LocalFileHolder>()
    val scrollState = ScrollState(0)

    var showCustomizeHomeTabDialog by mutableStateOf(false)
    var showCustomizeCategoriesDialog by mutableStateOf(false)
    var showSearchDialog by mutableStateOf(false)
    val internalStorageSearchTab by lazy {
        FilesTab(StorageProvider.getPrimaryInternalStorage(globalClass).contentHolder)
    }

    override fun onTabStarted() {
        super.onTabStarted()
        requestHomeToolbarUpdate()
        globalClass.mainActivityManager.refreshStorageDevices()
    }

    override fun onTabResumed() {
        super.onTabResumed()
        requestHomeToolbarUpdate()
        globalClass.mainActivityManager.refreshStorageDevices()
        refreshRecentFiles(forceInvalidate = false)
    }

    override suspend fun getSubtitle() = emptyString

    override suspend fun getTitle() = globalClass.getString(R.string.home_tab_title)

    fun getPinnedFiles() {
        pinnedFiles.clear()
        pinnedFiles.addAll(
            globalClass.preferencesManager.pinnedFiles.map { LocalFileHolder(File(it)) }
        )
    }

    fun isRecentDisabledOnHome(): Boolean {
        val prefs = globalClass.preferencesManager
        return prefs.disableRecentOnHomeTab && (prefs.showRecentInSidebar || prefs.showRecentInCategories)
    }

    fun fetchRecentFiles() {
        if (isRecentDisabledOnHome()) {
            isLoadingRecentFiles = false
            return
        }
        if (recentFiles.isNotEmpty() || recentFilesJob?.isActive == true) return

        isLoadingRecentFiles = true
        recentFilesJob = scope.launch {
            try {
                val files = getRecentFiles()
                if (isActive) {
                    kotlinx.coroutines.withContext(Dispatchers.Main) {
                        recentFiles.clear()
                        recentFiles.addAll(files)
                        isLoadingRecentFiles = false
                    }
                }
            } finally {
                if (isActive) {
                    kotlinx.coroutines.withContext(Dispatchers.Main) {
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
            return
        }
        if (forceInvalidate) {
            StorageProvider.invalidateRecentFilesCache()
        }
        recentFilesJob?.cancel()
        isLoadingRecentFiles = true
        recentFilesJob = scope.launch {
            try {
                val files = getRecentFiles()
                if (isActive) {
                    kotlinx.coroutines.withContext(Dispatchers.Main) {
                        recentFiles.clear()
                        recentFiles.addAll(files)
                        isLoadingRecentFiles = false
                    }
                }
            } finally {
                if (isActive) {
                    kotlinx.coroutines.withContext(Dispatchers.Main) {
                        isLoadingRecentFiles = false
                    }
                }
            }
        }
    }

    suspend fun refreshRecentFilesSuspend(forceInvalidate: Boolean = true) {
        if (isRecentDisabledOnHome()) {
            recentFilesJob?.cancel()
            kotlinx.coroutines.withContext(Dispatchers.Main) {
                recentFiles.clear()
                isLoadingRecentFiles = false
            }
            return
        }
        if (forceInvalidate) {
            StorageProvider.invalidateRecentFilesCache()
        }
        recentFilesJob?.cancel()
        kotlinx.coroutines.withContext(Dispatchers.Main) {
            isLoadingRecentFiles = true
        }
        val job = scope.launch {
            try {
                val files = getRecentFiles()
                if (isActive) {
                    kotlinx.coroutines.withContext(Dispatchers.Main) {
                        recentFiles.clear()
                        recentFiles.addAll(files)
                        isLoadingRecentFiles = false
                    }
                }
            } finally {
                kotlinx.coroutines.withContext(Dispatchers.Main) {
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

    private fun getRecentFiles(): ArrayList<RecentFile> {
        val configuredLimit = globalClass.preferencesManager.recentFilesLimit
        val carouselLimit = if (configuredLimit in 1..30) configuredLimit else 30
        return arrayListOf<RecentFile>().apply {
            addAll(
                StorageProvider.getRawRecentFiles(
                    limit = carouselLimit
                )
            )
        }
    }

    fun removePinnedFile(file: LocalFileHolder) {
        pinnedFiles.remove(file)
        globalClass.preferencesManager.pinnedFiles = pinnedFiles.map { it.uniquePath }
        globalClass.preferencesManager.pinnedFileNames =
            globalClass.preferencesManager.pinnedFileNames - file.uniquePath
    }
}
