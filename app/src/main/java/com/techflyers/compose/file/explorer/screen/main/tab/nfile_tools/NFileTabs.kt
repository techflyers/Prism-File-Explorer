package com.techflyers.compose.file.explorer.screen.main.tab.nfile_tools

import android.os.Environment
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.techflyers.compose.file.explorer.App.Companion.globalClass
import com.techflyers.compose.file.explorer.R
import com.techflyers.compose.file.explorer.screen.main.tab.Tab
import com.techflyers.compose.file.explorer.screen.main.tab.files.FilesTab
import com.techflyers.compose.file.explorer.screen.main.tab.files.holder.LocalFileHolder
import com.techflyers.compose.file.explorer.screen.main.tab.files.service.remote.NetworkConnectionModel
import com.techflyers.compose.file.explorer.screen.main.tab.files.service.remote.NetworkConnectionsService
import kotlinx.coroutines.*
import java.io.File

class VaultTab : Tab() {
    override val id = globalClass.generateUid()
    override val header = "Vault"
    var isPinVerified by mutableStateOf(false)
    var activePassword by mutableStateOf("")

    override fun onTabStarted() {
        super.onTabStarted()
        requestHomeToolbarUpdate()
    }

    override fun onTabResumed() {
        super.onTabResumed()
        requestHomeToolbarUpdate()
    }

    override fun onTabStopped() {
        super.onTabStopped()
    }

    override fun onTabRemoved() {
        isPinVerified = false
        activePassword = ""
        super.onTabRemoved()
    }

    override suspend fun getTitle() = "Private Wallet"
    override suspend fun getSubtitle() = if (isPinVerified) "Secure Sandbox" else "Locked"
}

class FtpServerTab : Tab() {
    override val id = globalClass.generateUid()
    override val header = "FTP Server"

    override fun onTabStarted() {
        super.onTabStarted()
        requestHomeToolbarUpdate()
    }

    override fun onTabResumed() {
        super.onTabResumed()
        requestHomeToolbarUpdate()
    }

    override suspend fun getTitle() = "FTP Server"
    override suspend fun getSubtitle() = "Local File Server"
}

class WebSharingTab(
    val initialDirectoryPath: String? = null
) : Tab() {
    override val id = globalClass.generateUid()
    override val header = "Web Share"

    var targetDirectoryPath: String by mutableStateOf(
        resolveInitialDirectoryPath(initialDirectoryPath)
    )

    companion object {
        fun resolveInitialDirectoryPath(explicitPath: String? = null): String {
            if (!explicitPath.isNullOrBlank()) {
                val explicitFile = File(explicitPath)
                if (explicitFile.exists()) {
                    return if (explicitFile.isDirectory) explicitFile.absolutePath
                    else explicitFile.parentFile?.absolutePath ?: explicitFile.absolutePath
                }
            }
            val activeTab = runCatching { globalClass.mainActivityManager.getActiveTab() }.getOrNull()
            val filesTab = (activeTab as? FilesTab)
                ?: (activeTab?.parentTab as? FilesTab)
                ?: runCatching {
                    globalClass.mainActivityManager.state.value.tabs
                        .filterIsInstance<FilesTab>()
                        .lastOrNull { it.activeFolder is LocalFileHolder }
                }.getOrNull()

            val localHolder = filesTab?.activeFolder as? LocalFileHolder
            if (localHolder != null && localHolder.file.exists()) {
                return if (localHolder.file.isDirectory) {
                    localHolder.file.absolutePath
                } else {
                    localHolder.file.parentFile?.absolutePath
                        ?: Environment.getExternalStorageDirectory().absolutePath
                }
            }
            return Environment.getExternalStorageDirectory().absolutePath
        }
    }

    override fun onTabStarted() {
        super.onTabStarted()
        if (initialDirectoryPath == null) {
            val parentFilesTab = parentTab as? FilesTab
            val local = parentFilesTab?.activeFolder as? LocalFileHolder
            if (local != null && local.file.exists() && local.file.isDirectory) {
                targetDirectoryPath = local.file.absolutePath
            }
        }
        requestHomeToolbarUpdate()
    }

    override fun onTabResumed() {
        super.onTabResumed()
        requestHomeToolbarUpdate()
    }

    override suspend fun getTitle() = "Web Sharing"
    override suspend fun getSubtitle() = File(targetDirectoryPath).name.ifEmpty { "Share over Wi-Fi/Internet" }
}

class NetworkConnectionWizardTab(
    val editingConnection: NetworkConnectionModel? = null
) : Tab() {
    override val id = globalClass.generateUid()
    override val header = if (editingConnection != null) "Edit Connection" else "Add Connection"

    override fun onTabStarted() {
        super.onTabStarted()
        requestHomeToolbarUpdate()
    }

    override fun onTabResumed() {
        super.onTabResumed()
        requestHomeToolbarUpdate()
    }

    override suspend fun getTitle() = if (editingConnection != null) {
        "Edit: ${editingConnection.name}"
    } else {
        "Add Connection"
    }
    override suspend fun getSubtitle() = if (editingConnection != null) {
        "${editingConnection.type} • ${editingConnection.host}:${editingConnection.port}"
    } else {
        "FTP, SFTP, SMB, WebDAV"
    }
}

class RemoteExplorerTab(var connection: NetworkConnectionModel) : Tab() {
    override val id = globalClass.generateUid()
    override val header: String
        get() = connection.name

    override fun onTabStarted() {
        super.onTabStarted()
        requestHomeToolbarUpdate()
    }

    override fun onTabResumed() {
        super.onTabResumed()
        NetworkConnectionsService.getConnectionById(globalClass, connection.id)?.let { updated ->
            connection = updated
        }
        requestHomeToolbarUpdate()
    }

    override suspend fun getTitle() = connection.name
    override suspend fun getSubtitle() = connection.host
}

class StorageAnalysisTab : Tab() {
    override val id = globalClass.generateUid()
    override val header = "Storage"

    override fun onTabStarted() {
        super.onTabStarted()
        requestHomeToolbarUpdate()
    }

    override fun onTabResumed() {
        super.onTabResumed()
        requestHomeToolbarUpdate()
    }

    override suspend fun getTitle() = "Storage Analysis"
    override suspend fun getSubtitle() = "Space & Folder Breakdown"
}

data class DuplicateFileItem(
    val file: File,
    val size: Long,
    val lastModified: Long,
    var isSelectedForDeletion: Boolean = false
)

data class DuplicateGroup(
    val id: String,
    val name: String,
    val size: Long,
    val files: MutableList<DuplicateFileItem>
)

enum class MinFileSizeFilter(val bytes: Long, val labelRes: Int) {
    SIZE_ALL(0L, R.string.all_sizes),
    SIZE_100KB(100L * 1024L, R.string.min_100kb),
    SIZE_1MB(1024L * 1024L, R.string.min_1mb),
    SIZE_10MB(10L * 1024L * 1024L, R.string.min_10mb)
}

enum class MatchMode {
    ACCURATE_HASH,
    FAST_NAME_SIZE
}

class DuplicateFinderTab : Tab() {
    override val id = globalClass.generateUid()
    override val header = "Duplicates"

    private val tabScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    var scanTargetFolder: File by mutableStateOf(Environment.getExternalStorageDirectory())
    var minSizeBytes: Long by mutableLongStateOf(MinFileSizeFilter.SIZE_100KB.bytes)
    var isCustomSize: Boolean by mutableStateOf(false)
    var customSizeLabel: String by mutableStateOf("")
    var matchMode: MatchMode by mutableStateOf(MatchMode.ACCURATE_HASH)

    var isScanning: Boolean by mutableStateOf(false)
    var scannedFilesCount: Int by mutableIntStateOf(0)
    var currentScanningFile: String by mutableStateOf("")
    private var scanJob: Job? = null

    var duplicateGroups: List<DuplicateGroup> by mutableStateOf(emptyList())
    var hasScanned: Boolean by mutableStateOf(false)

    fun startScan(scanner: suspend (File, Long, MatchMode, (Int, String) -> Unit) -> List<DuplicateGroup>) {
        scanJob?.cancel()
        isScanning = true
        hasScanned = true
        scannedFilesCount = 0
        currentScanningFile = ""
        duplicateGroups = emptyList()

        scanJob = tabScope.launch {
            val results = withContext(Dispatchers.IO) {
                scanner(scanTargetFolder, minSizeBytes, matchMode) { count, path ->
                    scannedFilesCount = count
                    currentScanningFile = path
                }
            }
            duplicateGroups = results
            isScanning = false
        }
    }

    fun stopScan() {
        scanJob?.cancel()
        isScanning = false
    }

    override fun onTabRemoved() {
        super.onTabRemoved()
        scanJob?.cancel()
        tabScope.cancel()
    }

    override fun onTabStarted() {
        super.onTabStarted()
        requestHomeToolbarUpdate()
    }

    override fun onTabResumed() {
        super.onTabResumed()
        requestHomeToolbarUpdate()
        if (duplicateGroups.isNotEmpty() && !isScanning) {
            val pruned = duplicateGroups.mapNotNull { group ->
                val remaining = group.files.filter { it.file.exists() }.toMutableList()
                if (remaining.size >= 2) group.copy(files = remaining) else null
            }
            if (pruned.size != duplicateGroups.size || pruned.sumOf { it.files.size } != duplicateGroups.sumOf { it.files.size }) {
                duplicateGroups = pruned
            }
        }
    }

    override suspend fun getTitle() = "Duplicate Finder"
    override suspend fun getSubtitle() = "Find & Clean Duplicate Files"
}
