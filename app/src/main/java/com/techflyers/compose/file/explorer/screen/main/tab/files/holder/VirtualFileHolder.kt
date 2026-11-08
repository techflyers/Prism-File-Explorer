package com.techflyers.compose.file.explorer.screen.main.tab.files.holder

import com.techflyers.compose.file.explorer.App.Companion.globalClass
import com.techflyers.compose.file.explorer.R
import com.techflyers.compose.file.explorer.common.emptyString
import com.techflyers.compose.file.explorer.screen.main.tab.files.misc.ContentCount
import com.techflyers.compose.file.explorer.screen.main.tab.files.misc.FileListCategory
import com.techflyers.compose.file.explorer.screen.main.tab.files.provider.StorageProvider.getAllFiles
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
    private val seenFilePaths = HashSet<String>()
    private val seenCategoryPaths = HashSet<String>()
    private val categoryObjects = arrayListOf<FileListCategory>()
    private val seenFormats = HashSet<String>()
    @Volatile
    private var cachedCategoriesSnapshot: List<FileListCategory> = emptyList()
    @Volatile
    private var cachedFormatsSnapshot: List<String> = emptyList()

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
        ALL_FILES -> "All Files"
        else -> globalClass.getString(R.string.unknown)
    }

    override val isFolder = true

    override val lastModified = 0L

    override val size = 0L

    override val uniquePath = displayName

    override val extension = emptyString

    override suspend fun isValid() = true

    override suspend fun getParent() = null

    fun isUnlimitedMode(): Boolean {
        return type == ALL_FILES || (type == RECENT && globalClass.preferencesManager.recentFilesLimit == -1)
    }

    override suspend fun listSortedContent(): ArrayList<out ContentHolder> {
        if (type == SEARCH || type == BOOKMARKS || type == DUPLICATES) {
            return super.listSortedContent()
        }

        val sortingPrefs = globalClass.preferencesManager.getSortingPrefsFor(this)
        val content = listContent()
        if (!globalClass.preferencesManager.showHiddenFiles) {
            content.removeIf { it.isHidden() }
        }

        // StorageProvider already applies sorting for name, date, and size.
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

        val configuredRecentLimit = globalClass.preferencesManager.recentFilesLimit
        val initialLimit = if (type == RECENT && configuredRecentLimit > 0) {
            minOf(INITIAL_PAGE_SIZE, configuredRecentLimit)
        } else {
            INITIAL_PAGE_SIZE
        }

        val rawItems = when (type) {
            BOOKMARKS -> getBookmarks()
            AUDIO -> getAudioFiles(sortingPrefs, limit = INITIAL_PAGE_SIZE, offset = 0)
            VIDEO -> getVideoFiles(sortingPrefs, limit = INITIAL_PAGE_SIZE, offset = 0)
            IMAGE -> getImageFiles(sortingPrefs, limit = INITIAL_PAGE_SIZE, offset = 0)
            ARCHIVE -> getArchiveFiles(sortingPrefs, limit = INITIAL_PAGE_SIZE, offset = 0)
            DOCUMENT -> getDocumentFiles(sortingPrefs, limit = INITIAL_PAGE_SIZE, offset = 0)
            RECENT -> getRecentFiles(sortingPrefs = sortingPrefs, limit = initialLimit, offset = 0)
            ALL_FILES -> getAllFiles(sortingPrefs = sortingPrefs, limit = INITIAL_PAGE_SIZE, offset = 0)
            SEARCH -> getSearchResult()
            DUPLICATES -> ArrayList((customItems ?: emptyList()).filter { runBlocking { it.isValid() } })
            APK -> getApkFiles(sortingPrefs, limit = INITIAL_PAGE_SIZE, offset = 0)
            else -> arrayListOf()
        }

        if (type != BOOKMARKS && type != SEARCH && type != DUPLICATES) {
            if (rawItems.size < initialLimit || (type == RECENT && configuredRecentLimit in 1..rawItems.size)) {
                hasMoreContent = false
            }
            currentOffset = rawItems.size
        } else {
            hasMoreContent = false
        }

        contentList.clear()
        seenFilePaths.clear()
        seenCategoryPaths.clear()
        categoryObjects.clear()
        seenFormats.clear()

        val uniqueRaw = ArrayList<ContentHolder>(rawItems.size)
        for (item in rawItems) {
            if (seenFilePaths.add(item.uniquePath)) {
                uniqueRaw.add(item)
            }
        }
        contentList.addAll(uniqueRaw)

        if (type != BOOKMARKS && type != DUPLICATES) {
            appendCategoriesFrom(uniqueRaw)
        } else {
            cachedCategoriesSnapshot = emptyList()
            cachedFormatsSnapshot = emptyList()
        }

        return uniqueRaw.filter {
            val categoryOk = selectedCategory == null ||
                it.uniquePath == (selectedCategory!!.data as File).path + File.separator + it.displayName
            val formatOk = selectedFormats.isEmpty() || it.extension.lowercase() in selectedFormats
            categoryOk && formatOk
        }.toCollection(arrayListOf()).also { fileCount = it.size }
    }

    suspend fun loadNextPage(): List<ContentHolder> {
        val maxCap = when {
            type == ALL_FILES -> Int.MAX_VALUE
            type == RECENT -> {
                val limitPref = globalClass.preferencesManager.recentFilesLimit
                if (limitPref == -1) Int.MAX_VALUE else limitPref
            }
            else -> MAX_PAGED_CAP
        }
        if (!hasMoreContent || currentOffset >= maxCap) {
            hasMoreContent = false
            return emptyList()
        }
        val pageSize = minOf(PAGE_SIZE, (maxCap - currentOffset).coerceAtLeast(1))
        val sortingPrefs = globalClass.preferencesManager.getSortingPrefsFor(this)
        val nextPage = when (type) {
            AUDIO -> getAudioFiles(sortingPrefs, limit = pageSize, offset = currentOffset)
            VIDEO -> getVideoFiles(sortingPrefs, limit = pageSize, offset = currentOffset)
            IMAGE -> getImageFiles(sortingPrefs, limit = pageSize, offset = currentOffset)
            ARCHIVE -> getArchiveFiles(sortingPrefs, limit = pageSize, offset = currentOffset)
            DOCUMENT -> getDocumentFiles(sortingPrefs, limit = pageSize, offset = currentOffset)
            RECENT -> getRecentFiles(sortingPrefs = sortingPrefs, limit = pageSize, offset = currentOffset)
            ALL_FILES -> getAllFiles(sortingPrefs = sortingPrefs, limit = pageSize, offset = currentOffset)
            APK -> getApkFiles(sortingPrefs, limit = pageSize, offset = currentOffset)
            else -> arrayListOf()
        }

        if (nextPage.size < pageSize || currentOffset + nextPage.size >= maxCap) {
            hasMoreContent = false
        }
        currentOffset += nextPage.size

        val uniqueNextPage = ArrayList<ContentHolder>(nextPage.size)
        for (item in nextPage) {
            if (seenFilePaths.add(item.uniquePath)) {
                uniqueNextPage.add(item)
            }
        }
        if (uniqueNextPage.isEmpty()) {
            hasMoreContent = false
            return emptyList()
        }

        contentList.addAll(uniqueNextPage)
        appendCategoriesFrom(uniqueNextPage)

        val showHidden = globalClass.preferencesManager.showHiddenFiles
        return uniqueNextPage.filter {
            val hiddenOk = showHidden || !it.isHidden()
            val categoryOk = selectedCategory == null ||
                it.uniquePath == (selectedCategory!!.data as File).path + File.separator + it.displayName
            val formatOk = selectedFormats.isEmpty() || it.extension.lowercase() in selectedFormats
            hiddenOk && categoryOk && formatOk
        }.also { fileCount = contentList.size }
    }

    private fun appendCategoriesFrom(items: List<ContentHolder>) {
        var categoriesChanged = false
        var formatsChanged = false
        items.forEach { content ->
            if (content is LocalFileHolder) {
                val parentPath = content.file.parent
                if (parentPath != null && seenCategoryPaths.add(parentPath)) {
                    val dirFile = File(parentPath)
                    categoryObjects.add(FileListCategory(dirFile.name, dirFile))
                    categoriesChanged = true
                }
            }
            val ext = content.extension.lowercase()
            if (ext.isNotEmpty() && seenFormats.add(ext)) {
                formatsChanged = true
            }
        }
        if (categoriesChanged || cachedCategoriesSnapshot.isEmpty()) {
            cachedCategoriesSnapshot = ArrayList(categoryObjects)
        }
        if (formatsChanged) {
            cachedFormatsSnapshot = seenFormats.sorted()
        }
    }

    fun getCategories(): List<FileListCategory> = cachedCategoriesSnapshot

    fun availableFormats(): List<String> = cachedFormatsSnapshot

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
        const val INITIAL_PAGE_SIZE = 100
        const val PAGE_SIZE = 250
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
        const val ALL_FILES = 10
    }
}
