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
import com.techflyers.compose.file.explorer.screen.main.tab.files.service.remote.NetworkConnectionModel
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

class WebSharingTab : Tab() {
    override val id = globalClass.generateUid()
    override val header = "Web Share"

    override fun onTabStarted() {
        super.onTabStarted()
        requestHomeToolbarUpdate()
    }

    override fun onTabResumed() {
        super.onTabResumed()
        requestHomeToolbarUpdate()
    }

    override suspend fun getTitle() = "Web Sharing"
    override suspend fun getSubtitle() = "Share over Wi-Fi/Internet"
}

class NetworkConnectionWizardTab : Tab() {
    override val id = globalClass.generateUid()
    override val header = "Add Connection"

    override fun onTabStarted() {
        super.onTabStarted()
        requestHomeToolbarUpdate()
    }

    override fun onTabResumed() {
        super.onTabResumed()
        requestHomeToolbarUpdate()
    }

    override suspend fun getTitle() = "Add Connection"
    override suspend fun getSubtitle() = "Remote server connection wizard"
}

class RemoteExplorerTab(val connection: NetworkConnectionModel) : Tab() {
    override val id = globalClass.generateUid()
    override val header = connection.name

    override fun onTabStarted() {
        super.onTabStarted()
        requestHomeToolbarUpdate()
    }

    override fun onTabResumed() {
        super.onTabResumed()
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

