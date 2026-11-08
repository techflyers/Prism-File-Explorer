package com.techflyers.compose.file.explorer.office

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.mutableStateOf
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

enum class OfficeEngineMode {
    ASK,
    LIBREOFFICE,
    BUILTIN
}

/**
 * Entry point for the optional Office engine (powered by LibreOffice): preferences,
 * installer, and conversion hook used by UniversalDocumentConverter.
 */
object OfficeEngine {

    val focusSettingsSection = mutableStateOf(false)

    private const val PREFS = "office_engine"
    private const val KEY_USE_FOR_OFFICE = "use_for_office_files"
    private const val KEY_HINT_DISMISSED = "hint_dismissed"
    private const val KEY_ENGINE_MODE = "engine_mode"

    private val OFFICE_EXTENSIONS = setOf(
        "doc", "docx", "docm", "dot", "dotx", "odt", "ott", "rtf",
        "xls", "xlsx", "xlsm", "ods", "ots",
        "ppt", "pptx", "pptm", "pps", "ppsx", "odp", "otp", "odg"
    )

    @Volatile
    private var installerInstance: OfficeEngineInstaller? = null

    fun installer(context: Context): OfficeEngineInstaller =
        installerInstance ?: synchronized(this) {
            installerInstance ?: DownloadOfficeEngineInstaller(context.applicationContext).also { installerInstance = it }
        }

    fun isSupported(): Boolean = OfficeEngineManifest.bundleForDevice() != null

    fun isOfficeFile(fileName: String): Boolean =
        fileName.substringAfterLast('.', "").lowercase() in OFFICE_EXTENSIONS

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun getEngineMode(context: Context): OfficeEngineMode {
        val p = prefs(context)
        if (!p.contains(KEY_ENGINE_MODE)) {
            val legacyUse = p.getBoolean(KEY_USE_FOR_OFFICE, true)
            return if (!legacyUse) OfficeEngineMode.BUILTIN else OfficeEngineMode.ASK
        }
        val ord = p.getInt(KEY_ENGINE_MODE, OfficeEngineMode.ASK.ordinal)
        return OfficeEngineMode.entries.getOrElse(ord) { OfficeEngineMode.ASK }
    }

    fun setEngineMode(context: Context, mode: OfficeEngineMode) {
        prefs(context).edit()
            .putInt(KEY_ENGINE_MODE, mode.ordinal)
            .putBoolean(KEY_USE_FOR_OFFICE, mode != OfficeEngineMode.BUILTIN)
            .apply()
    }

    fun useForOfficeFiles(context: Context): Boolean =
        getEngineMode(context) != OfficeEngineMode.BUILTIN && prefs(context).getBoolean(KEY_USE_FOR_OFFICE, true)

    fun setUseForOfficeFiles(context: Context, enabled: Boolean) {
        prefs(context).edit()
            .putBoolean(KEY_USE_FOR_OFFICE, enabled)
            .putInt(KEY_ENGINE_MODE, if (enabled) OfficeEngineMode.LIBREOFFICE.ordinal else OfficeEngineMode.BUILTIN.ordinal)
            .apply()
    }

    fun isActive(context: Context): Boolean = useForOfficeFiles(context) && OfficeEngineStore.isInstalled(context)

    fun isInstalled(context: Context): Boolean = OfficeEngineStore.isInstalled(context)

    /**
     * Converts through LibreOffice when installed and enabled; returns null if absent, disabled or failed.
     */
    fun tryConvert(context: Context, source: Uri, fileName: String): File? {
        if (!isOfficeFile(fileName) || !isActive(context)) return null
        return OfficeEngineClient.convertBlocking(context, source, fileName).pdf
    }

    fun shouldOfferHint(context: Context, fileName: String): Boolean =
        isOfficeFile(fileName) && isSupported() && !OfficeEngineStore.isInstalled(context) &&
            getEngineMode(context) != OfficeEngineMode.BUILTIN &&
            !prefs(context).getBoolean(KEY_HINT_DISMISSED, false)

    fun dismissHint(context: Context) {
        prefs(context).edit().putBoolean(KEY_HINT_DISMISSED, true).apply()
    }

    fun resetHint(context: Context) {
        prefs(context).edit().putBoolean(KEY_HINT_DISMISSED, false).apply()
    }

    /**
     * Cancels any stuck or active download, wipes partial/staging/installed engine files and caches,
     * and resets hint state so the user can freshly choose Built-in Viewer or re-download.
     */
    fun resetEngine(context: Context) {
        val app = context.applicationContext
        resetHint(app)
        installer(app).uninstall()
    }

    internal fun removeInstalledEngine(context: Context) {
        OfficeEngineClient.stopEngineProcess(context)
        OfficeEngineStore.delete(context)
        OfficeEngineClient.clearCache(context)
    }

    internal fun installedState(context: Context): OfficeEngineState {
        if (!isSupported()) return OfficeEngineState.Unsupported
        OfficeEngineStore.installedDir(context) ?: return OfficeEngineState.NotInstalled
        val installed = OfficeEngineStore.installedVersion(context)
        val current = installed == OfficeEngineManifest.VERSION
        return OfficeEngineState.Installed(
            version = if (current) OfficeEngineManifest.DISPLAY_VERSION else installed.orEmpty().substringAfterLast('-'),
            sizeBytes = OfficeEngineStore.sizeOnDisk(context),
            updateAvailable = !current
        )
    }
}

private class DownloadOfficeEngineInstaller(private val context: Context) : OfficeEngineInstaller {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val workManager = WorkManager.getInstance(context)
    private val _state = MutableStateFlow<OfficeEngineState>(
        if (!OfficeEngine.isSupported()) OfficeEngineState.Unsupported else OfficeEngineState.NotInstalled
    )
    override val state: StateFlow<OfficeEngineState> = _state.asStateFlow()
    override val usesNotification: Boolean = true

    @Volatile private var startedHere = false

    init {
        scope.launch {
            _state.value = OfficeEngine.installedState(context)
            // Cancel any leftover stuck ENQUEUED/BLOCKED work from a prior app session on launch
            // so relaunching the app never gets permanently stuck in a zombie Downloading state.
            runCatching {
                val existing = workManager.getWorkInfosForUniqueWork(OfficeEngineDownloadWorker.UNIQUE_NAME).get()
                val first = existing.firstOrNull()
                if (first != null && !OfficeEngine.isInstalled(context) &&
                    (first.state == WorkInfo.State.ENQUEUED || first.state == WorkInfo.State.BLOCKED || first.runAttemptCount > 0)
                ) {
                    workManager.cancelUniqueWork(OfficeEngineDownloadWorker.UNIQUE_NAME)
                    workManager.pruneWork()
                }
            }
            workManager.getWorkInfosForUniqueWorkFlow(OfficeEngineDownloadWorker.UNIQUE_NAME).collect { infos ->
                _state.value = map(infos.firstOrNull())
            }
        }
    }

    private fun map(info: WorkInfo?): OfficeEngineState {
        if (!OfficeEngine.isSupported()) return OfficeEngineState.Unsupported
        if (OfficeEngine.isInstalled(context)) return OfficeEngine.installedState(context)
        return when (info?.state) {
            WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> {
                if (startedHere) {
                    OfficeEngineState.Downloading(0, OfficeEngineManifest.APPROX_DOWNLOAD_BYTES, waitingForNetwork = true)
                } else {
                    OfficeEngine.installedState(context)
                }
            }
            WorkInfo.State.RUNNING -> {
                val p = info.progress
                if (p.getString(OfficeEngineDownloadWorker.KEY_PHASE) == OfficeEngineDownloadWorker.PHASE_INSTALL) {
                    OfficeEngineState.Installing
                } else {
                    OfficeEngineState.Downloading(
                        p.getLong(OfficeEngineDownloadWorker.KEY_DONE, 0),
                        p.getLong(OfficeEngineDownloadWorker.KEY_TOTAL, OfficeEngineManifest.APPROX_DOWNLOAD_BYTES)
                    )
                }
            }
            WorkInfo.State.FAILED -> if (startedHere) {
                OfficeEngineState.Failed(
                    info.outputData.getString(OfficeEngineDownloadWorker.KEY_ERROR) ?: "Download failed"
                )
            } else {
                OfficeEngine.installedState(context)
            }
            else -> OfficeEngine.installedState(context)
        }
    }

    override fun install() {
        if (!OfficeEngine.isSupported()) return
        startedHere = true
        val request = OneTimeWorkRequestBuilder<OfficeEngineDownloadWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .setRequiresStorageNotLow(true)
                    .build()
            )
            .build()
        workManager.enqueueUniqueWork(OfficeEngineDownloadWorker.UNIQUE_NAME, ExistingWorkPolicy.REPLACE, request)
    }

    override fun cancel() {
        startedHere = false
        workManager.cancelUniqueWork(OfficeEngineDownloadWorker.UNIQUE_NAME)
        scope.launch {
            workManager.pruneWork()
            _state.value = OfficeEngine.installedState(context)
        }
    }

    override fun uninstall() {
        startedHere = false
        workManager.cancelUniqueWork(OfficeEngineDownloadWorker.UNIQUE_NAME)
        scope.launch {
            OfficeEngine.removeInstalledEngine(context)
            workManager.pruneWork()
            _state.value = OfficeEngine.installedState(context)
        }
    }

    override fun refresh() {
        scope.launch {
            val info = workManager.getWorkInfosForUniqueWork(OfficeEngineDownloadWorker.UNIQUE_NAME).get().firstOrNull()
            _state.value = map(info)
        }
    }
}
