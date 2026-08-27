package com.raival.compose.file.explorer.screen.main.tab.home

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.VideoFile
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.raival.compose.file.explorer.App.Companion.globalClass
import com.raival.compose.file.explorer.R
import com.raival.compose.file.explorer.common.emptyString
import com.raival.compose.file.explorer.screen.main.tab.Tab
import com.raival.compose.file.explorer.screen.main.tab.apps.AppsTab
import com.raival.compose.file.explorer.screen.main.tab.files.FilesTab
import com.raival.compose.file.explorer.screen.main.tab.files.holder.LocalFileHolder
import com.raival.compose.file.explorer.screen.main.tab.files.holder.VirtualFileHolder
import com.raival.compose.file.explorer.screen.main.tab.files.holder.VirtualFileHolder.Companion.ARCHIVE
import com.raival.compose.file.explorer.screen.main.tab.files.holder.VirtualFileHolder.Companion.AUDIO
import com.raival.compose.file.explorer.screen.main.tab.files.holder.VirtualFileHolder.Companion.DOCUMENT
import com.raival.compose.file.explorer.screen.main.tab.files.holder.VirtualFileHolder.Companion.IMAGE
import com.raival.compose.file.explorer.screen.main.tab.files.holder.VirtualFileHolder.Companion.VIDEO
import com.raival.compose.file.explorer.screen.main.tab.files.provider.StorageProvider
import com.raival.compose.file.explorer.screen.main.tab.home.holder.HomeCategory
import com.raival.compose.file.explorer.screen.main.tab.home.holder.RecentFile
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
    val pinnedFiles = arrayListOf<LocalFileHolder>()

    var showCustomizeHomeTabDialog by mutableStateOf(false)

    override fun onTabStarted() {
        super.onTabStarted()
        requestHomeToolbarUpdate()
    }

    override fun onTabResumed() {
        super.onTabResumed()
        requestHomeToolbarUpdate()
    }

    override suspend fun getSubtitle() = emptyString

    override suspend fun getTitle() = globalClass.getString(R.string.home_tab_title)

    fun getPinnedFiles() {
        pinnedFiles.clear()
        pinnedFiles.addAll(
            globalClass.preferencesManager.pinnedFiles.map { LocalFileHolder(File(it)) }
        )
    }

    fun fetchRecentFiles() {
        if (recentFiles.isNotEmpty() || recentFilesJob?.isActive == true) return

        recentFilesJob = scope.launch {
            val files = getRecentFiles()
            if (isActive) {
                recentFiles.addAll(files)
            }
        }
    }

    fun refreshRecentFiles() {
        recentFilesJob?.cancel()
        recentFilesJob = null
        recentFiles.clear()
        fetchRecentFiles()
    }

    private val _mainCategories by lazy {
        val mainActivityManager = globalClass.mainActivityManager
        listOf(
            HomeCategory(
                name = globalClass.getString(R.string.images),
                icon = Icons.Rounded.Image,
                onClick = {
                    mainActivityManager.replaceCurrentTabWith(
                        FilesTab(VirtualFileHolder(IMAGE))
                    )
                }
            ),
            HomeCategory(
                name = globalClass.getString(R.string.videos),
                icon = Icons.Rounded.VideoFile,
                onClick = {
                    mainActivityManager.replaceCurrentTabWith(
                        FilesTab(VirtualFileHolder(VIDEO))
                    )
                }
            ),
            HomeCategory(
                name = globalClass.getString(R.string.audios),
                icon = Icons.Rounded.AudioFile,
                onClick = {
                    mainActivityManager.replaceCurrentTabWith(
                        FilesTab(VirtualFileHolder(AUDIO))
                    )
                }
            ),
            HomeCategory(
                name = globalClass.getString(R.string.documents),
                icon = Icons.AutoMirrored.Rounded.InsertDriveFile,
                onClick = {
                    mainActivityManager.replaceCurrentTabWith(
                        FilesTab(VirtualFileHolder(DOCUMENT))
                    )
                }
            ),
            HomeCategory(
                name = globalClass.getString(R.string.archives),
                icon = Icons.Rounded.Archive,
                onClick = {
                    mainActivityManager.replaceCurrentTabWith(
                        FilesTab(VirtualFileHolder(ARCHIVE))
                    )
                }
            ),
            HomeCategory(
                name = globalClass.getString(R.string.apps),
                icon = Icons.Rounded.Android,
                onClick = {
                    mainActivityManager.replaceCurrentTabWith(
                        AppsTab()
                    )
                }
            )
        )
    }

    fun getMainCategories(): List<HomeCategory> = _mainCategories

    private fun getRecentFiles(): ArrayList<RecentFile> {
        return arrayListOf<RecentFile>().apply {
            addAll(
                StorageProvider.getRawRecentFiles(
                    recentHours = 24,
                    limit = 25
                )
            )
        }
    }

    fun removePinnedFile(file: LocalFileHolder) {
        pinnedFiles.remove(file)
        globalClass.preferencesManager.pinnedFiles = pinnedFiles.map { it.uniquePath }
    }
}