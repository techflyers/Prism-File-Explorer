package com.techflyers.compose.file.explorer.screen.main.tab.files.provider

import com.techflyers.compose.file.explorer.App.Companion.globalClass
import com.techflyers.compose.file.explorer.R
import com.techflyers.compose.file.explorer.common.emptyString
import com.techflyers.compose.file.explorer.common.isNot
import com.techflyers.compose.file.explorer.common.toFormattedDate
import com.techflyers.compose.file.explorer.common.toFormattedSize
import com.techflyers.compose.file.explorer.screen.main.tab.files.holder.ContentHolder
import com.techflyers.compose.file.explorer.screen.main.tab.files.holder.LocalFileHolder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.math.BigInteger
import java.nio.file.Files
import java.security.MessageDigest

data class CalculationProgress(
    val isCalculating: Boolean = false,
    val current: Long = 0,
    val total: Long = 0,
    val message: String = emptyString,
    val etaSeconds: Long = -1L,
    val filesProcessed: Long = 0L,
    val totalFiles: Long = 0L
) {
    val percentage: Int
        get() = if (total > 0) ((current * 100) / total).toInt().coerceIn(0, 100) else 0

    val formattedEta: String?
        get() {
            if (etaSeconds < 0) return null
            return when {
                etaSeconds < 60 -> "${etaSeconds}s"
                etaSeconds < 3600 -> "${etaSeconds / 60}m ${etaSeconds % 60}s"
                else -> "${etaSeconds / 3600}h ${(etaSeconds % 3600) / 60}m"
            }
        }
}

sealed interface PropertiesState {
    object Loading : PropertiesState

    data class SingleContentProperties(
        val name: String,
        val path: String,
        val type: String,
        val lastModified: String,
        val size: String,
        val permissions: String,
        val owner: String,
        val group: String = "",
        val seLinuxContext: String = "",
        val symbolicLinkTarget: String? = null,
        val fileHolder: ContentHolder? = null,
        val contentCount: StateFlow<String>,
        val checksum: StateFlow<String>,
        val sha1: StateFlow<String>,
        val sha256: StateFlow<String>,
        val contentProgress: StateFlow<CalculationProgress>,
        val checksumProgress: StateFlow<CalculationProgress>,
        val sha1Progress: StateFlow<CalculationProgress>,
        val sha256Progress: StateFlow<CalculationProgress>,
    ) : PropertiesState

    data class TwoFileChecksumComparison(
        val file1Name: String,
        val file2Name: String,
        val file1Md5: String,
        val file2Md5: String,
        val file1Sha256: String,
        val file2Sha256: String,
        val isMatch: Boolean,
        val primaryAlgorithm: String = "MD5"
    )

    enum class DirectoryEntryStatus {
        IDENTICAL,
        MODIFIED,
        UNIQUE
    }

    data class DirectoryEntryFileInfo(
        val folderIndex: Int = 0,
        val folderName: String,
        val folderPath: String,
        val holder: ContentHolder,
        val md5: String,
        val size: Long,
        val lastModified: Long = holder.lastModified,
        val hashAlgorithm: String = "xxHash3",
        val sha256: String = ""
    ) {
        val hash: String get() = md5
    }

    data class DirectoryComparisonEntry(
        val relativePath: String,
        val isFolder: Boolean,
        val status: DirectoryEntryStatus,
        val entriesByFolder: List<DirectoryEntryFileInfo>
    )

    data class DirectoryComparisonResult(
        val folderNames: List<String>,
        val folderPaths: List<String>,
        val isIdentical: Boolean,
        val identicalCount: Int,
        val modifiedCount: Int,
        val uniqueCountsByFolder: Map<String, Int>,
        val entries: List<DirectoryComparisonEntry>,
        val hashAlgorithm: String = "xxHash3",
        val timestamp: Long = System.currentTimeMillis(),
        val savedDuplicateGroups: List<Pair<String, List<ContentHolder>>> = emptyList(),
        val savedTwoFileComparison: TwoFileChecksumComparison? = null
    )

    data class MultipleContentProperties(
        val selectedFileCount: Int,
        val totalFileCount: StateFlow<String>,
        val totalSize: StateFlow<String>,
        val countProgress: StateFlow<CalculationProgress>,
        val sizeProgress: StateFlow<CalculationProgress>,
        val checksumStatus: StateFlow<String>,
        val checksumProgress: StateFlow<CalculationProgress>,
        val duplicateGroups: StateFlow<List<Pair<String, List<ContentHolder>>>> = MutableStateFlow(emptyList()),
        val twoFileComparison: StateFlow<TwoFileChecksumComparison?> = MutableStateFlow(null),
        val hasDirectoryComparison: Boolean = false,
        val directoryComparisonStatus: StateFlow<String> = MutableStateFlow(emptyString),
        val directoryComparisonProgress: StateFlow<CalculationProgress> = MutableStateFlow(CalculationProgress()),
        val directoryComparison: StateFlow<DirectoryComparisonResult?> = MutableStateFlow(null)
    ) : PropertiesState
}

object SavedDirectoryComparisonStore {
    @Volatile
    private var lastDuplicateGroupsCache: List<Pair<String, List<ContentHolder>>> = emptyList()

    @Volatile
    private var lastTwoFileCompCache: PropertiesState.TwoFileChecksumComparison? = null

    fun updateDuplicatesSnapshot(
        groups: List<Pair<String, List<ContentHolder>>>,
        twoFile: PropertiesState.TwoFileChecksumComparison?
    ) {
        lastDuplicateGroupsCache = groups
        lastTwoFileCompCache = twoFile
        val existing = load()
        if (existing != null) {
            save(
                existing.copy(
                    savedDuplicateGroups = groups,
                    savedTwoFileComparison = twoFile
                )
            )
        }
    }

    fun save(result: PropertiesState.DirectoryComparisonResult) {
        try {
            val root = JSONObject()
            root.put("folderNames", JSONArray(result.folderNames))
            root.put("folderPaths", JSONArray(result.folderPaths))
            root.put("isIdentical", result.isIdentical)
            root.put("identicalCount", result.identicalCount)
            root.put("modifiedCount", result.modifiedCount)
            root.put("hashAlgorithm", result.hashAlgorithm)
            root.put("timestamp", result.timestamp)

            val uniqueObj = JSONObject()
            result.uniqueCountsByFolder.forEach { (k, v) -> uniqueObj.put(k, v) }
            root.put("uniqueCountsByFolder", uniqueObj)

            val entriesArr = JSONArray()
            result.entries.take(2500).forEach { entry ->
                val eObj = JSONObject()
                eObj.put("relativePath", entry.relativePath)
                eObj.put("isFolder", entry.isFolder)
                eObj.put("status", entry.status.name)
                val filesArr = JSONArray()
                entry.entriesByFolder.forEach { info ->
                    val fObj = JSONObject()
                    fObj.put("folderIndex", info.folderIndex)
                    fObj.put("folderName", info.folderName)
                    fObj.put("folderPath", info.folderPath)
                    fObj.put("filePath", info.holder.uniquePath)
                    fObj.put("md5", info.md5)
                    fObj.put("size", info.size)
                    fObj.put("lastModified", info.lastModified)
                    fObj.put("hashAlgorithm", info.hashAlgorithm)
                    fObj.put("sha256", info.sha256)
                    filesArr.put(fObj)
                }
                eObj.put("entriesByFolder", filesArr)
                entriesArr.put(eObj)
            }
            root.put("entries", entriesArr)

            val dupGroups = result.savedDuplicateGroups.ifEmpty { lastDuplicateGroupsCache }
            val dupArr = JSONArray()
            dupGroups.take(1000).forEach { (hash, holders) ->
                val gObj = JSONObject()
                gObj.put("hash", hash)
                val pArr = JSONArray()
                holders.forEach { h -> pArr.put(h.uniquePath) }
                gObj.put("paths", pArr)
                dupArr.put(gObj)
            }
            root.put("duplicateGroups", dupArr)

            val twoFile = result.savedTwoFileComparison ?: lastTwoFileCompCache
            if (twoFile != null) {
                val tfObj = JSONObject()
                tfObj.put("file1Name", twoFile.file1Name)
                tfObj.put("file2Name", twoFile.file2Name)
                tfObj.put("file1Md5", twoFile.file1Md5)
                tfObj.put("file2Md5", twoFile.file2Md5)
                tfObj.put("file1Sha256", twoFile.file1Sha256)
                tfObj.put("file2Sha256", twoFile.file2Sha256)
                tfObj.put("isMatch", twoFile.isMatch)
                tfObj.put("primaryAlgorithm", twoFile.primaryAlgorithm)
                root.put("twoFileComparison", tfObj)
            }

            globalClass.preferencesManager.lastDirectoryComparisonJson = root.toString()
        } catch (_: Exception) {}
    }

    fun load(): PropertiesState.DirectoryComparisonResult? {
        val raw = globalClass.preferencesManager.lastDirectoryComparisonJson
        if (raw.isBlank()) return null
        return try {
            val root = JSONObject(raw)
            val folderNamesArr = root.optJSONArray("folderNames") ?: JSONArray()
            val folderNames = (0 until folderNamesArr.length()).map { folderNamesArr.getString(it) }
            val folderPathsArr = root.optJSONArray("folderPaths") ?: JSONArray()
            val folderPaths = (0 until folderPathsArr.length()).map { folderPathsArr.getString(it) }
            val isIdentical = root.optBoolean("isIdentical", false)
            val identicalCount = root.optInt("identicalCount", 0)
            val modifiedCount = root.optInt("modifiedCount", 0)
            val hashAlgorithm = root.optString("hashAlgorithm", "xxHash3")
            val timestamp = root.optLong("timestamp", 0L)

            val uniqueObj = root.optJSONObject("uniqueCountsByFolder") ?: JSONObject()
            val uniqueCounts = linkedMapOf<String, Int>()
            folderNames.forEach { name ->
                uniqueCounts[name] = uniqueObj.optInt(name, 0)
            }
            val keys = uniqueObj.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                uniqueCounts[key] = uniqueObj.optInt(key, 0)
            }

            val entriesArr = root.optJSONArray("entries") ?: JSONArray()
            val entries = mutableListOf<PropertiesState.DirectoryComparisonEntry>()
            for (i in 0 until entriesArr.length()) {
                val eObj = entriesArr.getJSONObject(i)
                val relPath = eObj.optString("relativePath", "")
                val isFolder = eObj.optBoolean("isFolder", false)
                val statusName = eObj.optString("status", "MODIFIED")
                val status = runCatching {
                    PropertiesState.DirectoryEntryStatus.valueOf(statusName)
                }.getOrDefault(PropertiesState.DirectoryEntryStatus.MODIFIED)

                val filesArr = eObj.optJSONArray("entriesByFolder") ?: JSONArray()
                val infos = mutableListOf<PropertiesState.DirectoryEntryFileInfo>()
                for (j in 0 until filesArr.length()) {
                    val fObj = filesArr.getJSONObject(j)
                    val filePath = fObj.optString("filePath", "")
                    val holder = LocalFileHolder(File(filePath))
                    val folderIdx = fObj.optInt("folderIndex", j.coerceAtMost((folderNames.size - 1).coerceAtLeast(0)))
                    infos.add(
                        PropertiesState.DirectoryEntryFileInfo(
                            folderIndex = folderIdx,
                            folderName = fObj.optString("folderName", folderNames.getOrNull(folderIdx) ?: ""),
                            folderPath = fObj.optString("folderPath", folderPaths.getOrNull(folderIdx) ?: ""),
                            holder = holder,
                            md5 = fObj.optString("md5", ""),
                            size = fObj.optLong("size", holder.size),
                            lastModified = fObj.optLong("lastModified", holder.lastModified),
                            hashAlgorithm = fObj.optString("hashAlgorithm", hashAlgorithm),
                            sha256 = fObj.optString("sha256", "")
                        )
                    )
                }
                entries.add(
                    PropertiesState.DirectoryComparisonEntry(
                        relativePath = relPath,
                        isFolder = isFolder,
                        status = status,
                        entriesByFolder = infos
                    )
                )
            }

            val dupGroups = mutableListOf<Pair<String, List<ContentHolder>>>()
            val dupArr = root.optJSONArray("duplicateGroups")
            if (dupArr != null) {
                for (i in 0 until dupArr.length()) {
                    val gObj = dupArr.getJSONObject(i)
                    val hash = gObj.optString("hash", "")
                    val pArr = gObj.optJSONArray("paths") ?: JSONArray()
                    val holders = (0 until pArr.length()).map { idx ->
                        LocalFileHolder(File(pArr.getString(idx)))
                    }
                    if (hash.isNotBlank() && holders.isNotEmpty()) {
                        dupGroups.add(hash to holders)
                    }
                }
            }

            val tfObj = root.optJSONObject("twoFileComparison")
            val twoFile = if (tfObj != null) {
                PropertiesState.TwoFileChecksumComparison(
                    file1Name = tfObj.optString("file1Name", ""),
                    file2Name = tfObj.optString("file2Name", ""),
                    file1Md5 = tfObj.optString("file1Md5", ""),
                    file2Md5 = tfObj.optString("file2Md5", ""),
                    file1Sha256 = tfObj.optString("file1Sha256", ""),
                    file2Sha256 = tfObj.optString("file2Sha256", ""),
                    isMatch = tfObj.optBoolean("isMatch", false),
                    primaryAlgorithm = tfObj.optString("primaryAlgorithm", "MD5")
                )
            } else null

            PropertiesState.DirectoryComparisonResult(
                folderNames = folderNames,
                folderPaths = folderPaths,
                isIdentical = isIdentical,
                identicalCount = identicalCount,
                modifiedCount = modifiedCount,
                uniqueCountsByFolder = uniqueCounts,
                entries = entries,
                hashAlgorithm = hashAlgorithm,
                timestamp = timestamp,
                savedDuplicateGroups = dupGroups,
                savedTwoFileComparison = twoFile
            )
        } catch (_: Exception) {
            null
        }
    }
}

data class ContentPropertiesUiState(
    val details: PropertiesState = PropertiesState.Loading
)

class ContentPropertiesProvider(private val contentHolders: List<ContentHolder>) {
    private val _uiState = MutableStateFlow(ContentPropertiesUiState())
    val uiState: StateFlow<ContentPropertiesUiState> = _uiState.asStateFlow()

    private val calculationScope = CoroutineScope(IO)
    private val activeJobs = mutableSetOf<Job>()

    init {
        when {
            contentHolders.isEmpty() -> {}

            contentHolders.size == 1 -> {
                loadSingleContentDetails(contentHolders.first())
            }

            else -> {
                loadMultipleContentDetails(contentHolders)
            }
        }
    }

    fun cleanup() {
        activeJobs.forEach { it.cancel() }
        calculationScope.cancel()
    }

    fun reload() {
        if (contentHolders.size == 1) {
            loadSingleContentDetails(contentHolders.first())
        } else if (contentHolders.size > 1) {
            activeJobs.forEach { it.cancel() }
            activeJobs.clear()
            loadMultipleContentDetails(contentHolders)
        }
    }

    private fun loadSingleContentDetails(file: ContentHolder) {
        val contentCountFlow = MutableStateFlow(emptyString)
        val checksumFlow = MutableStateFlow(
            if (file.isFolder) emptyString else globalClass.getString(
                R.string.calculating
            )
        )
        val sha1Flow = MutableStateFlow(
            if (file.isFolder) emptyString else globalClass.getString(
                R.string.calculating
            )
        )
        val sha256Flow = MutableStateFlow(
            if (file.isFolder) emptyString else globalClass.getString(
                R.string.calculating
            )
        )
        val contentProgressFlow = MutableStateFlow(CalculationProgress())
        val checksumProgressFlow = MutableStateFlow(CalculationProgress())
        val sha1ProgressFlow = MutableStateFlow(CalculationProgress())
        val sha256ProgressFlow = MutableStateFlow(CalculationProgress())

        _uiState.update {
            it.copy(
                details = PropertiesState.SingleContentProperties(
                    name = file.displayName.ifEmpty { globalClass.getString(R.string.root) },
                    path = file.uniquePath,
                    type = determineFileType(file),
                    lastModified = file.lastModified.toFormattedDate(),
                    size = if (file.isFolder) globalClass.getString(R.string.calculating) else file.size.toFormattedSize(),
                    permissions = getPermissions(file),
                    owner = getOwner(file),
                    group = getGroup(file),
                    seLinuxContext = getSELinuxContext(file),
                    symbolicLinkTarget = file.symbolicLinkTarget,
                    fileHolder = file,
                    contentCount = contentCountFlow,
                    checksum = checksumFlow,
                    sha1 = sha1Flow,
                    sha256 = sha256Flow,
                    contentProgress = contentProgressFlow,
                    checksumProgress = checksumProgressFlow,
                    sha1Progress = sha1ProgressFlow,
                    sha256Progress = sha256ProgressFlow
                )
            )
        }

        if (file.isFolder) {
            val job = calculationScope.launch {
                contentProgressFlow.value = CalculationProgress(isCalculating = true)
                try {
                    val (fileCount, folderCount, totalSize) = calculateDirectoryStats(file) { current ->
                        contentProgressFlow.value = CalculationProgress(
                            isCalculating = true,
                            current = current
                        )
                    }
                    contentCountFlow.value =
                        globalClass.getString(R.string.files_folders_count, fileCount, folderCount)

                    _uiState.update { state ->
                        val currentDetails =
                            state.details as? PropertiesState.SingleContentProperties
                        currentDetails?.let {
                            state.copy(
                                details = it.copy(size = totalSize.toFormattedSize())
                            )
                        } ?: state
                    }
                } catch (_: Exception) {
                    contentCountFlow.value = globalClass.getString(R.string.error_calculating)
                } finally {
                    contentProgressFlow.value = CalculationProgress(isCalculating = false)
                }
            }
            activeJobs.add(job)
        } else if (file is LocalFileHolder) {
            val job = calculationScope.launch {
                checksumProgressFlow.value = CalculationProgress(isCalculating = true)
                try {
                    val checksum = calculateMD5WithProgress(file) { bytesProcessed, totalBytes, eta ->
                        checksumProgressFlow.value = CalculationProgress(
                            isCalculating = true,
                            current = bytesProcessed,
                            total = totalBytes,
                            etaSeconds = eta
                        )
                    }
                    checksumFlow.value = checksum
                } catch (_: Exception) {
                    checksumFlow.value = globalClass.getString(R.string.error_calculating)
                } finally {
                    checksumProgressFlow.value = CalculationProgress(isCalculating = false)
                }
            }
            activeJobs.add(job)

            val sha1Job = calculationScope.launch {
                sha1ProgressFlow.value = CalculationProgress(isCalculating = true)
                try {
                    val sha1 = calculateSha1(file) { bytesProcessed, totalBytes, eta ->
                        sha1ProgressFlow.value = CalculationProgress(
                            isCalculating = true,
                            current = bytesProcessed,
                            total = totalBytes,
                            etaSeconds = eta
                        )
                    }
                    sha1Flow.value = sha1
                } catch (_: Exception) {
                    sha1Flow.value = globalClass.getString(R.string.error_calculating)
                } finally {
                    sha1ProgressFlow.value = CalculationProgress(isCalculating = false)
                }
            }
            activeJobs.add(sha1Job)

            val sha256Job = calculationScope.launch {
                sha256ProgressFlow.value = CalculationProgress(isCalculating = true)
                try {
                    val sha256 = calculateSha256(file) { bytesProcessed, totalBytes, eta ->
                        sha256ProgressFlow.value = CalculationProgress(
                            isCalculating = true,
                            current = bytesProcessed,
                            total = totalBytes,
                            etaSeconds = eta
                        )
                    }
                    sha256Flow.value = sha256
                } catch (_: Exception) {
                    sha256Flow.value = globalClass.getString(R.string.error_calculating)
                } finally {
                    sha256ProgressFlow.value = CalculationProgress(isCalculating = false)
                }
            }
            activeJobs.add(sha256Job)
        } else {
            checksumFlow.value = emptyString
            sha1Flow.value = emptyString
            sha256Flow.value = emptyString
        }
    }

    private fun loadMultipleContentDetails(files: List<ContentHolder>) {
        val totalFileCountFlow = MutableStateFlow(globalClass.getString(R.string.calculating))
        val totalSizeFlow = MutableStateFlow(globalClass.getString(R.string.calculating))
        val countProgressFlow = MutableStateFlow(CalculationProgress())
        val sizeProgressFlow = MutableStateFlow(CalculationProgress())
        val checksumStatusFlow = MutableStateFlow(globalClass.getString(R.string.calculating))
        val checksumProgressFlow = MutableStateFlow(CalculationProgress())
        val duplicateGroupsFlow = MutableStateFlow<List<Pair<String, List<ContentHolder>>>>(emptyList())
        val twoFileComparisonFlow = MutableStateFlow<PropertiesState.TwoFileChecksumComparison?>(null)

        val selectedFolders = files.filter { it.isFolder }
        val hasDirComp = selectedFolders.size >= 2
        val dirCompStatusFlow = MutableStateFlow(
            if (hasDirComp) globalClass.getString(R.string.calculating) else emptyString
        )
        val dirCompProgressFlow = MutableStateFlow(CalculationProgress())
        val dirCompResultFlow = MutableStateFlow<PropertiesState.DirectoryComparisonResult?>(null)

        _uiState.update {
            it.copy(
                details = PropertiesState.MultipleContentProperties(
                    selectedFileCount = files.size,
                    totalFileCount = totalFileCountFlow,
                    totalSize = totalSizeFlow,
                    countProgress = countProgressFlow,
                    sizeProgress = sizeProgressFlow,
                    checksumStatus = checksumStatusFlow,
                    checksumProgress = checksumProgressFlow,
                    duplicateGroups = duplicateGroupsFlow,
                    twoFileComparison = twoFileComparisonFlow,
                    hasDirectoryComparison = hasDirComp,
                    directoryComparisonStatus = dirCompStatusFlow,
                    directoryComparisonProgress = dirCompProgressFlow,
                    directoryComparison = dirCompResultFlow
                )
            )
        }

        val job = calculationScope.launch {
            countProgressFlow.value =
                CalculationProgress(isCalculating = true, total = files.size.toLong())
            sizeProgressFlow.value =
                CalculationProgress(isCalculating = true, total = files.size.toLong())

            try {
                var totalFiles = 0L
                var totalFolders = 0L
                var totalSize = 0L
                var processedItems = 0L

                files.forEach { file ->
                    if (!isActive) return@forEach

                    processedItems++
                    countProgressFlow.value = CalculationProgress(
                        isCalculating = true,
                        current = processedItems,
                        total = files.size.toLong()
                    )
                    sizeProgressFlow.value = CalculationProgress(
                        isCalculating = true,
                        current = processedItems,
                        total = files.size.toLong()
                    )

                    if (file.isFolder) {
                        val (filesCount, foldersCount, dirSize) = calculateDirectoryStats(file)
                        totalFiles += filesCount
                        totalFolders += foldersCount + 1
                        totalSize += dirSize
                    } else {
                        totalFiles++
                        totalSize += file.size
                    }

                    totalFileCountFlow.value = globalClass.getString(
                        R.string.files_folders_count,
                        totalFiles,
                        totalFolders
                    )
                    totalSizeFlow.value = totalSize.toFormattedSize()

                    yield()
                }
            } catch (_: Exception) {
                totalFileCountFlow.value = globalClass.getString(R.string.error_calculating)
                totalSizeFlow.value = globalClass.getString(R.string.error_calculating)
            } finally {
                countProgressFlow.value = CalculationProgress(isCalculating = false)
                sizeProgressFlow.value = CalculationProgress(isCalculating = false)
            }
        }
        activeJobs.add(job)

        calculateDuplicates(
            files,
            checksumStatusFlow,
            checksumProgressFlow,
            duplicateGroupsFlow,
            twoFileComparisonFlow
        )

        if (hasDirComp) {
            calculateDirectoryComparison(
                selectedFolders,
                dirCompStatusFlow,
                dirCompProgressFlow,
                dirCompResultFlow
            )
        }
    }

    private suspend fun collectNestedLocalFiles(folder: ContentHolder, out: MutableList<LocalFileHolder>) {
        if (!currentCoroutineContext().isActive) return
        try {
            for (item in folder.listContent()) {
                if (!currentCoroutineContext().isActive) return
                if (item.isFolder) {
                    collectNestedLocalFiles(item, out)
                } else if (item is LocalFileHolder) {
                    out.add(item)
                }
            }
        } catch (_: Exception) {}
    }

    private suspend fun computeConfiguredHash(
        file: LocalFileHolder,
        algorithm: String,
        onProgress: (Long, Long, Long) -> Unit = { _, _, _ -> }
    ): String {
        return when (algorithm) {
            "SHA-256" -> calculateSha256(file, onProgress)
            "MD5" -> calculateMD5WithProgress(file, onProgress)
            else -> calculateXxHash3WithProgress(file, onProgress)
        }
    }

    private fun calculateDuplicates(
        contentHolders: List<ContentHolder>,
        checksumStatusFlow: MutableStateFlow<String>,
        checksumProgressFlow: MutableStateFlow<CalculationProgress>,
        duplicateGroupsFlow: MutableStateFlow<List<Pair<String, List<ContentHolder>>>>,
        twoFileComparisonFlow: MutableStateFlow<PropertiesState.TwoFileChecksumComparison?>
    ) {
        val checksumJob = calculationScope.launch {
            val configuredAlgo = globalClass.preferencesManager.comparisonHashAlgorithm.ifBlank { "xxHash3" }
            val directLocalFiles = contentHolders.filterIsInstance<LocalFileHolder>().filter { !it.isFolder }
            val localFiles = if (directLocalFiles.isNotEmpty()) {
                directLocalFiles
            } else {
                val nested = mutableListOf<LocalFileHolder>()
                for (folder in contentHolders.filter { it.isFolder }) {
                    if (!isActive) return@launch
                    collectNestedLocalFiles(folder, nested)
                }
                nested.distinctBy { it.uniquePath }
            }

            if (localFiles.isEmpty()) {
                checksumStatusFlow.value = globalClass.getString(R.string.no_applicable_files)
                return@launch
            }
            checksumProgressFlow.value =
                CalculationProgress(isCalculating = true, total = localFiles.size.toLong(), totalFiles = localFiles.size.toLong())
            val checksumMap = linkedMapOf<String, MutableList<ContentHolder>>()
            val hashByPath = mutableMapOf<String, String>()
            var processed = 0L
            val startTime = System.currentTimeMillis()
            try {
                for (file in localFiles) {
                    if (!isActive) return@launch
                    processed++
                    val now = System.currentTimeMillis()
                    val elapsed = now - startTime
                    val eta = if (elapsed > 500 && processed > 0) {
                        val avgTime = elapsed / processed
                        ((localFiles.size - processed) * avgTime) / 1000L
                    } else -1L
                    checksumProgressFlow.value = CalculationProgress(
                        isCalculating = true,
                        current = processed,
                        total = localFiles.size.toLong(),
                        filesProcessed = processed,
                        totalFiles = localFiles.size.toLong(),
                        etaSeconds = eta
                    )
                    // For 2-file comparison, compute MD5 so the classic 2-File MD5 + SHA-256 comparison card is always populated
                    val hash = if (localFiles.size == 2 && configuredAlgo == "xxHash3") {
                        calculateMD5WithProgress(file) { _, _, _ -> }
                    } else {
                        computeConfiguredHash(file, configuredAlgo)
                    }
                    if (hash.isNotBlank() && hash != globalClass.getString(R.string.error_calculating)) {
                        checksumMap.getOrPut(hash) { mutableListOf() }.add(file)
                        hashByPath[file.uniquePath] = hash
                    }
                    yield()
                }
                var twoFileCompResult: PropertiesState.TwoFileChecksumComparison? = null
                if (localFiles.size == 2) {
                    val f1 = localFiles[0]
                    val f2 = localFiles[1]
                    val sha1Hash = calculateSha256(f1) { _, _, _ -> }
                    val sha2Hash = calculateSha256(f2) { _, _, _ -> }
                    val m1 = hashByPath[f1.uniquePath] ?: ""
                    val m2 = hashByPath[f2.uniquePath] ?: ""
                    twoFileCompResult = PropertiesState.TwoFileChecksumComparison(
                        file1Name = f1.displayName,
                        file2Name = f2.displayName,
                        file1Md5 = m1,
                        file2Md5 = m2,
                        file1Sha256 = sha1Hash,
                        file2Sha256 = sha2Hash,
                        isMatch = sha1Hash.isNotBlank() && sha1Hash == sha2Hash && m1 == m2,
                        primaryAlgorithm = if (configuredAlgo == "xxHash3") "MD5" else configuredAlgo
                    )
                    twoFileComparisonFlow.value = twoFileCompResult
                }
                val duplicateOnlyGroups = checksumMap.filter { it.value.size > 1 }.map { it.key to it.value.toList() }
                val allGroups = checksumMap.entries
                    .sortedWith(
                        compareByDescending<Map.Entry<String, MutableList<ContentHolder>>> { it.value.size }
                            .thenBy { it.value.firstOrNull()?.displayName?.lowercase() ?: "" }
                    )
                    .map { it.key to it.value.toList() }
                duplicateGroupsFlow.value = allGroups
                SavedDirectoryComparisonStore.updateDuplicatesSnapshot(allGroups, twoFileCompResult)

                val hashedCount = checksumMap.values.sumOf { it.size }
                if (duplicateOnlyGroups.isEmpty()) {
                    if (hashedCount == 0 && localFiles.isNotEmpty()) {
                        checksumStatusFlow.value = globalClass.getString(R.string.error_calculating)
                    } else {
                        checksumStatusFlow.value = "${globalClass.getString(R.string.all_files_distinct)} ($configuredAlgo)"
                    }
                } else {
                    checksumStatusFlow.value = "${globalClass.getString(R.string.duplicates_found, duplicateOnlyGroups.size)} ($configuredAlgo)"
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Exception) {
                checksumStatusFlow.value = globalClass.getString(R.string.error_calculating)
            } finally {
                checksumProgressFlow.value = CalculationProgress(isCalculating = false)
            }
        }
        activeJobs.add(checksumJob)
    }

    private fun calculateDirectoryComparison(
        folders: List<ContentHolder>,
        statusFlow: MutableStateFlow<String>,
        progressFlow: MutableStateFlow<CalculationProgress>,
        resultFlow: MutableStateFlow<PropertiesState.DirectoryComparisonResult?>
    ) {
        val dirJob = calculationScope.launch {
            progressFlow.value = CalculationProgress(isCalculating = true)
            try {
                val configuredAlgo = globalClass.preferencesManager.comparisonHashAlgorithm.ifBlank { "xxHash3" }

                val nameCounts = folders.groupingBy { it.displayName }.eachCount()
                val folderLabels = folders.map { folder ->
                    if ((nameCounts[folder.displayName] ?: 0) > 1) {
                        val parentName = (folder as? LocalFileHolder)?.file?.parentFile?.name
                        if (!parentName.isNullOrBlank()) "${folder.displayName} ($parentName)" else "${folder.displayName} (${folder.uniquePath})"
                    } else {
                        folder.displayName
                    }
                }

                // Only compare actual files (!child.isFolder) so intermediate directory nodes
                // never inflate Identical or Unique file counts.
                val relativeFileMap = linkedMapOf<String, MutableList<Pair<Int, ContentHolder>>>()
                var totalFilesToProcess = 0L

                suspend fun scanTree(rootFolderIndex: Int, currentFolder: ContentHolder, prefix: String) {
                    if (!currentCoroutineContext().isActive) return
                    try {
                        val children = currentFolder.listContent()
                        for (child in children) {
                            if (!currentCoroutineContext().isActive) return
                            val relPath = if (prefix.isEmpty()) child.displayName else "$prefix/${child.displayName}"
                            if (child.isFolder) {
                                scanTree(rootFolderIndex, child, relPath)
                            } else {
                                val list = relativeFileMap.getOrPut(relPath) { mutableListOf() }
                                if (list.none { it.first == rootFolderIndex }) {
                                    list.add(rootFolderIndex to child)
                                    totalFilesToProcess++
                                }
                            }
                        }
                    } catch (_: Exception) {}
                }

                folders.forEachIndexed { idx, folder ->
                    if (!isActive) return@launch
                    scanTree(idx, folder, "")
                }

                var processedFiles = 0L
                val startTime = System.currentTimeMillis()
                val entries = mutableListOf<PropertiesState.DirectoryComparisonEntry>()
                var identicalCount = 0
                var modifiedCount = 0
                val uniqueCounts = linkedMapOf<String, Int>().apply {
                    folderLabels.forEach { put(it, 0) }
                }

                for ((relPath, items) in relativeFileMap) {
                    if (!isActive) return@launch
                    val presentFolderIndices = items.map { it.first }.distinct()
                    val isPresentInAllFolders = presentFolderIndices.size == folders.size
                    val fileInfos = mutableListOf<PropertiesState.DirectoryEntryFileInfo>()

                    for ((folderIdx, holder) in items) {
                        processedFiles++
                        val elapsed = System.currentTimeMillis() - startTime
                        val eta = if (elapsed > 400 && processedFiles > 0 && totalFilesToProcess > 0) {
                            val avg = elapsed / processedFiles
                            ((totalFilesToProcess - processedFiles).coerceAtLeast(0L) * avg) / 1000L
                        } else -1L
                        progressFlow.value = CalculationProgress(
                            isCalculating = true,
                            current = processedFiles,
                            total = totalFilesToProcess.coerceAtLeast(1L),
                            filesProcessed = processedFiles,
                            totalFiles = totalFilesToProcess,
                            etaSeconds = eta
                        )

                        val algoForEntry = if (!isPresentInAllFolders && configuredAlgo != "xxHash3") {
                            "xxHash3"
                        } else {
                            configuredAlgo
                        }
                        val hashValue = if (holder is LocalFileHolder) {
                            computeConfiguredHash(holder, algoForEntry)
                        } else {
                            ""
                        }
                        fileInfos.add(
                            PropertiesState.DirectoryEntryFileInfo(
                                folderIndex = folderIdx,
                                folderName = folderLabels[folderIdx],
                                folderPath = folders[folderIdx].uniquePath,
                                holder = holder,
                                md5 = hashValue,
                                size = holder.size,
                                lastModified = holder.lastModified,
                                hashAlgorithm = algoForEntry,
                                sha256 = if (algoForEntry == "SHA-256") hashValue else ""
                            )
                        )
                    }

                    val status = when {
                        !isPresentInAllFolders -> {
                            fileInfos.forEach { info ->
                                uniqueCounts[info.folderName] = (uniqueCounts[info.folderName] ?: 0) + 1
                            }
                            PropertiesState.DirectoryEntryStatus.UNIQUE
                        }
                        else -> {
                            val distinctSizes = fileInfos.map { it.size }.distinct()
                            val distinctHashes = fileInfos.map { it.md5 }.distinct()
                            val allValidHashes = fileInfos.all {
                                it.md5.isNotBlank() && it.md5 != globalClass.getString(R.string.error_calculating)
                            }
                            if (distinctSizes.size == 1 && allValidHashes && distinctHashes.size == 1) {
                                identicalCount++
                                PropertiesState.DirectoryEntryStatus.IDENTICAL
                            } else {
                                modifiedCount++
                                PropertiesState.DirectoryEntryStatus.MODIFIED
                            }
                        }
                    }

                    entries.add(
                        PropertiesState.DirectoryComparisonEntry(
                            relativePath = relPath,
                            isFolder = false,
                            status = status,
                            entriesByFolder = fileInfos
                        )
                    )
                    yield()
                }

                val sortedEntries = entries.sortedWith(
                    compareBy<PropertiesState.DirectoryComparisonEntry> {
                        when (it.status) {
                            PropertiesState.DirectoryEntryStatus.MODIFIED -> 0
                            PropertiesState.DirectoryEntryStatus.UNIQUE -> 1
                            PropertiesState.DirectoryEntryStatus.IDENTICAL -> 2
                        }
                    }.thenBy { it.relativePath.lowercase() }
                )

                val totalUnique = uniqueCounts.values.sum()
                val isIdentical = modifiedCount == 0 && totalUnique == 0

                val statusSummary = if (isIdentical) {
                    "Directories match ($identicalCount matching files • $configuredAlgo)"
                } else {
                    "$identicalCount matching, $modifiedCount mismatched, $totalUnique only in one directory ($configuredAlgo)"
                }

                val comparisonResult = PropertiesState.DirectoryComparisonResult(
                    folderNames = folderLabels,
                    folderPaths = folders.map { it.uniquePath },
                    isIdentical = isIdentical,
                    identicalCount = identicalCount,
                    modifiedCount = modifiedCount,
                    uniqueCountsByFolder = uniqueCounts,
                    entries = sortedEntries,
                    hashAlgorithm = configuredAlgo,
                    timestamp = System.currentTimeMillis()
                )
                resultFlow.value = comparisonResult
                SavedDirectoryComparisonStore.save(comparisonResult)

                // Record in Closed Tabs History so user can reopen from Closed Tabs History at any time
                globalClass.mainActivityManager.recordClosedComparison(
                    title = "Compare: ${folderLabels.joinToString(" vs ")}",
                    subtitle = statusSummary,
                    selectionPaths = folders.map { it.uniquePath }
                )

                statusFlow.value = statusSummary
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Exception) {
                statusFlow.value = globalClass.getString(R.string.error_calculating)
            } finally {
                progressFlow.value = CalculationProgress(isCalculating = false)
            }
        }
        activeJobs.add(dirJob)
    }

    private fun determineFileType(file: ContentHolder): String {
        val baseType = when {
            file.isFolder -> globalClass.getString(R.string.folder)
            else -> file.extension.lowercase().ifEmpty { globalClass.getString(R.string.unknown) }
        }
        return if (file.isSymbolicLink) {
            if (file.isSymbolicLinkBroken) {
                globalClass.getString(R.string.symbolic_link_broken)
            } else {
                globalClass.getString(R.string.file_properties_basic_type_symbolic_link_format, baseType)
            }
        } else {
            baseType
        }
    }

    private fun getPermissions(file: ContentHolder): String {
        if (file is LocalFileHolder) {
            val mode = file.posixMode
            if (mode != null) {
                return com.techflyers.compose.file.explorer.screen.main.tab.files.posix.formatPosixMode(mode)
            }
            return buildString {
                append(if (file.canRead) "r" else "-")
                append(if (file.canWrite) "w" else "-")
                append(if (file.file.canExecute()) "x" else "-")
            }
        }
        return emptyString
    }

    private fun getOwner(file: ContentHolder): String {
        if (file is LocalFileHolder) {
            val uid = file.posixUid
            if (uid != null) {
                return com.techflyers.compose.file.explorer.screen.main.tab.files.posix.PosixPrincipalLookup.formatUser(uid)
            }
        }
        return try {
            if (file is LocalFileHolder) {
                Files.getOwner(file.file.toPath()).name
            } else {
                globalClass.getString(R.string.unknown)
            }
        } catch (_: Exception) {
            globalClass.getString(R.string.unknown)
        }
    }

    private fun getGroup(file: ContentHolder): String {
        if (file is LocalFileHolder) {
            val gid = file.posixGid
            if (gid != null) {
                return com.techflyers.compose.file.explorer.screen.main.tab.files.posix.PosixPrincipalLookup.formatGroup(gid)
            }
        }
        return globalClass.getString(R.string.unknown)
    }

    private fun getSELinuxContext(file: ContentHolder): String {
        if (file is LocalFileHolder) {
            return file.seLinuxContext ?: com.techflyers.compose.file.explorer.screen.main.tab.files.posix.SELinuxManager.getFileContext(file.file.absolutePath) ?: "—"
        }
        return "—"
    }

    private suspend fun calculateDirectoryStats(
        directory: ContentHolder,
        onProgress: ((Long) -> Unit)? = null
    ): Triple<Long, Long, Long> {
        var fileCount = 0L
        var folderCount = 0L
        var totalSize = 0L
        var processed = 0L

        suspend fun processDirectory(dir: ContentHolder) {
            if (!currentCoroutineContext().isActive) return

            try {
                dir.listContent().forEach { file ->
                    if (!currentCoroutineContext().isActive) return@forEach

                    processed++
                    onProgress?.invoke(processed)

                    if (file.isFolder) {
                        folderCount++
                        processDirectory(file)
                    } else {
                        fileCount++
                        totalSize += file.size
                    }

                    if (processed % 50 == 0L) {
                        yield()
                    }
                }
            } catch (_: SecurityException) {
            }
        }

        processDirectory(directory)
        return Triple(fileCount, folderCount, totalSize)
    }

    private suspend fun calculateXxHash3WithProgress(
        file: LocalFileHolder,
        onProgress: (Long, Long, Long) -> Unit
    ): String {
        if (file.isFolder) return emptyString
        return withContext(IO) {
            try {
                val fileSize = file.size
                val startTime = System.currentTimeMillis()
                var lastUpdateTime = startTime
                XxHash3.hashFile(
                    file = file.file,
                    onProgress = { bytesProcessed, _ ->
                        val now = System.currentTimeMillis()
                        if (now - lastUpdateTime >= 150L || bytesProcessed == fileSize) {
                            val elapsed = now - startTime
                            val eta = if (elapsed > 300 && bytesProcessed > 0) {
                                val rate = (bytesProcessed * 1000L) / elapsed
                                if (rate > 0) (fileSize - bytesProcessed).coerceAtLeast(0L) / rate else -1L
                            } else -1L
                            onProgress(bytesProcessed, fileSize, eta)
                            lastUpdateTime = now
                        }
                    }
                )
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Exception) {
                globalClass.getString(R.string.error_calculating)
            }
        }
    }

    private suspend fun calculateSha1(
        file: LocalFileHolder,
        onProgress: (Long, Long, Long) -> Unit
    ): String {
        if (file.isFolder) return emptyString

        return withContext(IO) {
            val md = MessageDigest.getInstance("SHA-1")
            val fileSize = file.size
            var bytesProcessed = 0L
            val startTime = System.currentTimeMillis()
            var lastUpdateTime = startTime

            try {
                FileInputStream(file.file).use { fis ->
                    val buffer = ByteArray(64 * 1024)
                    var read: Int
                    while (fis.read(buffer).also { read = it } isNot -1) {
                        if (!currentCoroutineContext().isActive) break
                        md.update(buffer, 0, read)
                        bytesProcessed += read

                        val now = System.currentTimeMillis()
                        if (now - lastUpdateTime >= 150L || bytesProcessed == fileSize) {
                            val elapsed = now - startTime
                            val eta = if (elapsed > 300 && bytesProcessed > 0) {
                                val rate = (bytesProcessed * 1000L) / elapsed
                                if (rate > 0) (fileSize - bytesProcessed).coerceAtLeast(0L) / rate else -1L
                            } else -1L
                            onProgress(bytesProcessed, fileSize, eta)
                            lastUpdateTime = now
                        }

                        if (bytesProcessed % (256 * 1024) == 0L) {
                            yield()
                        }
                    }
                }

                val digest = md.digest()
                val bigInt = BigInteger(1, digest)
                bigInt.toString(16).padStart(40, '0')
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Exception) {
                globalClass.getString(R.string.error_calculating)
            }
        }
    }

    private suspend fun calculateSha256(
        file: LocalFileHolder,
        onProgress: (Long, Long, Long) -> Unit
    ): String {
        if (file.isFolder) return emptyString
        return computeFileSha256(file.file, onProgress)
    }

    private suspend fun calculateMD5WithProgress(
        file: LocalFileHolder,
        onProgress: (Long, Long, Long) -> Unit
    ): String {
        if (file.isFolder) return emptyString

        return withContext(IO) {
            val md = MessageDigest.getInstance("MD5")
            val fileSize = file.size
            var bytesProcessed = 0L
            val startTime = System.currentTimeMillis()
            var lastUpdateTime = startTime

            try {
                FileInputStream(file.file).use { fis ->
                    val buffer = ByteArray(64 * 1024)
                    var read: Int
                    while (fis.read(buffer).also { read = it } isNot -1) {
                        if (!currentCoroutineContext().isActive) break
                        md.update(buffer, 0, read)
                        bytesProcessed += read

                        val now = System.currentTimeMillis()
                        if (now - lastUpdateTime >= 150L || bytesProcessed == fileSize) {
                            val elapsed = now - startTime
                            val eta = if (elapsed > 300 && bytesProcessed > 0) {
                                val rate = (bytesProcessed * 1000L) / elapsed
                                if (rate > 0) (fileSize - bytesProcessed).coerceAtLeast(0L) / rate else -1L
                            } else -1L
                            onProgress(bytesProcessed, fileSize, eta)
                            lastUpdateTime = now
                        }

                        if (bytesProcessed % (256 * 1024) == 0L) {
                            yield()
                        }
                    }
                }

                val digest = md.digest()
                val bigInt = BigInteger(1, digest)
                bigInt.toString(16).padStart(32, '0')
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Exception) {
                globalClass.getString(R.string.error_calculating)
            }
        }
    }

    companion object {
        suspend fun computeFileSha256(
            file: File,
            onProgress: (Long, Long, Long) -> Unit = { _, _, _ -> }
        ): String = withContext(IO) {
            if (!file.exists() || file.isDirectory) return@withContext emptyString
            val md = MessageDigest.getInstance("SHA-256")
            val fileSize = file.length()
            var bytesProcessed = 0L
            val startTime = System.currentTimeMillis()
            var lastUpdateTime = startTime

            try {
                FileInputStream(file).use { fis ->
                    val buffer = ByteArray(64 * 1024)
                    var read: Int
                    while (fis.read(buffer).also { read = it } isNot -1) {
                        if (!currentCoroutineContext().isActive) break
                        md.update(buffer, 0, read)
                        bytesProcessed += read

                        val now = System.currentTimeMillis()
                        if (now - lastUpdateTime >= 150L || bytesProcessed == fileSize) {
                            val elapsed = now - startTime
                            val eta = if (elapsed > 300 && bytesProcessed > 0) {
                                val rate = (bytesProcessed * 1000L) / elapsed
                                if (rate > 0) (fileSize - bytesProcessed).coerceAtLeast(0L) / rate else -1L
                            } else -1L
                            onProgress(bytesProcessed, fileSize, eta)
                            lastUpdateTime = now
                        }

                        if (bytesProcessed % (256 * 1024) == 0L) {
                            yield()
                        }
                    }
                }
                val digest = md.digest()
                BigInteger(1, digest).toString(16).padStart(64, '0')
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Exception) {
                globalClass.getString(R.string.error_calculating)
            }
        }
    }
}
