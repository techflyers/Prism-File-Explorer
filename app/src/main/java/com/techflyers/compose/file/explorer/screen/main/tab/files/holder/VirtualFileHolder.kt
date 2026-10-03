package com.techflyers.compose.file.explorer.screen.main.tab.files.holder

import com.techflyers.compose.file.explorer.App.Companion.globalClass
import com.techflyers.compose.file.explorer.R
import com.techflyers.compose.file.explorer.common.emptyString
import com.techflyers.compose.file.explorer.screen.main.tab.files.misc.ContentCount
import com.techflyers.compose.file.explorer.screen.main.tab.files.misc.FileListCategory
import com.techflyers.compose.file.explorer.screen.main.tab.files.provider.StorageProvider.getApkFiles
import com.techflyers.compose.file.explorer.screen.main.tab.files.provider.StorageProvider.getArchiveFiles
import com.techflyers.compose.file.explorer.screen.main.tab.files.provider.StorageProvider.getAudioFiles
import com.techflyers.compose.file.explorer.screen.main.tab.files.provider.StorageProvider.getBookmarks
import com.techflyers.compose.file.explorer.screen.main.tab.files.provider.StorageProvider.getDocumentFiles
import com.techflyers.compose.file.explorer.screen.main.tab.files.provider.StorageProvider.getImageFiles
import com.techflyers.compose.file.explorer.screen.main.tab.files.provider.StorageProvider.getRecentFiles
import com.techflyers.compose.file.explorer.screen.main.tab.files.provider.StorageProvider.getSearchResult
import com.techflyers.compose.file.explorer.screen.main.tab.files.provider.StorageProvider.getVideoFiles
import kotlinx.coroutines.runBlocking
import java.io.File

class VirtualFileHolder(
    val type: Int,
    val customItems: List<ContentHolder>? = null,
    val customTitle: String? = null
) : ContentHolder() {
    private var fileCount = 0
    private val contentList = arrayListOf<ContentHolder>()
    private val categories = arrayListOf<String>()

    var selectedCategory: FileListCategory? = null
    var selectedFormats: Set<String> = emptySet()

    var hasMoreContent = true
        private set
    private var currentOffset = 0

    override val displayName = when (type) {
        BOOKMARKS -> globalClass.getString(R.string.bookmarks)
        AUDIO -> globalClass.getString(R.string.audios)
        VIDEO -> globalClass.getString(R.string.videos)
        IMAGE -> globalClass.getString(R.string.images)
        ARCHIVE -> globalClass.getString(R.string.archives)
        DOCUMENT -> globalClass.getString(R.string.documents)
        RECENT -> globalClass.getString(R.string.recent_files)
        SEARCH -> globalClass.getString(R.string.search)
        DUPLICATES -> customTitle ?: "Duplicates"
        APK -> globalClass.getString(R.string.apk_and_bundles)
        else -> globalClass.getString(R.string.unknown)
    }

    override val isFolder = true

    override val lastModified = 0L

    override val size = 0L

    override val uniquePath = displayName

    override val extension = emptyString

    override suspend fun isValid() = true

    override suspend fun getParent() = null

    override suspend fun listSortedContent(): ArrayList<out ContentHolder> {
        if (type == SEARCH || type == BOOKMARKS || type == DUPLICATES) {
            return super.listSortedContent()
        }

        val sortingPrefs = globalClass.preferencesManager.getSortingPrefsFor(this)
        val content = listContent()
        if (!globalClass.preferencesManager.showHiddenFiles) {
            content.removeIf { it.isHidden() }
        }

        // MediaStore already applies SQL ORDER BY for name, date, and size.
        // Since category views only contain files, folders-first has no effect;
        // skipping second in-memory sort prevents expensive O(n log n) passes.
        if (sortingPrefs.sortMethod in listOf(
                com.techflyers.compose.file.explorer.screen.main.tab.files.misc.SortingMethod.SORT_BY_NAME,
                com.techflyers.compose.file.explorer.screen.main.tab.files.misc.SortingMethod.SORT_BY_DATE,
                com.techflyers.compose.file.explorer.screen.main.tab.files.misc.SortingMethod.SORT_BY_SIZE
            )
        ) {
            return content
        }
        return super.listSortedContent()
    }

    override suspend fun listContent(): ArrayList<out ContentHolder> {
        val sortingPrefs = globalClass.preferencesManager.getSortingPrefsFor(this)
        currentOffset = 0
        hasMoreContent = true

        val rawItems = when (type) {
            BOOKMARKS -> getBookmarks()
            AUDIO -> getAudioFiles(sortingPrefs, limit = PAGE_SIZE, offset = 0)
            VIDEO -> getVideoFiles(sortingPrefs, limit = PAGE_SIZE, offset = 0)
            IMAGE -> getImageFiles(sortingPrefs, limit = PAGE_SIZE, offset = 0)
            ARCHIVE -> getArchiveFiles(sortingPrefs, limit = PAGE_SIZE, offset = 0)
            DOCUMENT -> getDocumentFiles(sortingPrefs, limit = PAGE_SIZE, offset = 0)
            RECENT -> getRecentFiles(limit = PAGE_SIZE, offset = 0)
            SEARCH -> getSearchResult()
            DUPLICATES -> ArrayList(customItems ?: emptyList())
            APK -> getApkFiles(sortingPrefs, limit = PAGE_SIZE, offset = 0)
            else -> arrayListOf()
        }

        if (type != BOOKMARKS && type != SEARCH && type != DUPLICATES) {
            if (rawItems.size < PAGE_SIZE) {
                hasMoreContent = false
            }
            currentOffset = rawItems.size
        } else {
            hasMoreContent = false
        }

        contentList.clear()
        contentList.addAll(rawItems)
        if (type != BOOKMARKS && type != DUPLICATES) fetchCategories()

        return contentList.filter {
            val categoryOk = selectedCategory == null ||
                it.uniquePath == (selectedCategory!!.data as File).path + File.separator + it.displayName
            val formatOk = selectedFormats.isEmpty() || it.extension.lowercase() in selectedFormats
            categoryOk && formatOk
        }.toCollection(arrayListOf()).also { fileCount = it.size }
    }

    suspend fun loadNextPage(): List<ContentHolder> {
        if (!hasMoreContent || currentOffset >= MAX_PAGED_CAP) return emptyList()
        val sortingPrefs = globalClass.preferencesManager.getSortingPrefsFor(this)
        val nextPage = when (type) {
            AUDIO -> getAudioFiles(sortingPrefs, limit = PAGE_SIZE, offset = currentOffset)
            VIDEO -> getVideoFiles(sortingPrefs, limit = PAGE_SIZE, offset = currentOffset)
            IMAGE -> getImageFiles(sortingPrefs, limit = PAGE_SIZE, offset = currentOffset)
            ARCHIVE -> getArchiveFiles(sortingPrefs, limit = PAGE_SIZE, offset = currentOffset)
            DOCUMENT -> getDocumentFiles(sortingPrefs, limit = PAGE_SIZE, offset = currentOffset)
            RECENT -> getRecentFiles(limit = PAGE_SIZE, offset = currentOffset)
            APK -> getApkFiles(sortingPrefs, limit = PAGE_SIZE, offset = currentOffset)
            else -> arrayListOf()
        }

        if (nextPage.size < PAGE_SIZE || currentOffset + nextPage.size >= MAX_PAGED_CAP) {
            hasMoreContent = false
        }
        currentOffset += nextPage.size

        contentList.addAll(nextPage)
        fetchCategories()

        return nextPage.filter {
            val categoryOk = selectedCategory == null ||
                it.uniquePath == (selectedCategory!!.data as File).path + File.separator + it.displayName
            val formatOk = selectedFormats.isEmpty() || it.extension.lowercase() in selectedFormats
            categoryOk && formatOk
        }.also { fileCount = contentList.size }
    }

    private fun fetchCategories() {
        categories.clear()
        val seen = HashSet<String>()
        contentList.forEach { content ->
            if (content is LocalFileHolder) {
                val parentPath = content.file.parent
                if (parentPath != null && seen.add(parentPath)) {
                    categories.add(parentPath)
                }
            }
        }
    }

    fun getCategories() = categories.map { File(it).let { FileListCategory(it.name, it) } }

    fun availableFormats(): List<String> {
        return contentList.map { it.extension.lowercase() }
            .filter { it.isNotEmpty() }
            .distinct()
            .sorted()
    }

    override suspend fun findFile(name: String): ContentHolder? {
        if (contentList.isEmpty()) {
            runBlocking {
                listContent()
            }
        }

        return contentList.find { it.displayName == name }
    }

    override val canAddNewContent = false

    override val canRead = true

    override val canWrite = false

    override suspend fun getContentCount() = ContentCount(files = fileCount)

    companion object {
        const val PAGE_SIZE = 200
        const val MAX_PAGED_CAP = 5000
        const val BOOKMARKS = 0
        const val AUDIO = 1
        const val VIDEO = 2
        const val IMAGE = 3
        const val ARCHIVE = 4
        const val DOCUMENT = 5
        const val RECENT = 6
        const val SEARCH = 7
        const val DUPLICATES = 8
        const val APK = 9
    }
}