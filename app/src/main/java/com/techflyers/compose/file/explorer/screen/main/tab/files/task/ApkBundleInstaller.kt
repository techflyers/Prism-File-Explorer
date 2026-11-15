package com.techflyers.compose.file.explorer.screen.main.tab.files.task

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import com.android.apksig.ApkSigner
import com.android.apksig.KeyConfig
import com.reandroid.apkeditor.merge.Merger
import com.reandroid.apkeditor.merge.MergerOptions
import com.techflyers.compose.file.explorer.App.Companion.globalClass
import com.techflyers.compose.file.explorer.App.Companion.logger
import com.techflyers.compose.file.explorer.common.toFormattedSize
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

/**
 * Handles installation of split APK bundles (.xapk, .apkm, .apks) with live, granular
 * progress and status tracking across extraction, OBB copying, split staging, user confirmation,
 * fallback merging/signing, and final installation outcome.
 */
object ApkBundleInstaller {

    enum class InstallState {
        IDLE,
        EXTRACTING,
        COPYING_OBB,
        STAGING_SPLITS,
        MERGING_SIGNING,
        WAITING_CONFIRMATION,
        INSTALLING_SYSTEM,
        SUCCESS,
        CANCELLED,
        FAILED
    }

    private val installerScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val attemptedFallbackPaths = ConcurrentHashMap.newKeySet<String>()
    private val activeSessionBundles = ConcurrentHashMap<Int, String>()

    @Volatile
    private var sessionCallbackRegistered = false

    var installState by mutableStateOf(InstallState.IDLE)
        private set

    val isInstalling: Boolean
        get() = installState in setOf(
            InstallState.EXTRACTING,
            InstallState.COPYING_OBB,
            InstallState.STAGING_SPLITS,
            InstallState.MERGING_SIGNING,
            InstallState.WAITING_CONFIRMATION,
            InstallState.INSTALLING_SYSTEM
        )

    var activeBundlePath by mutableStateOf<String?>(null)
        private set
    var activePackageName by mutableStateOf<String?>(null)
        private set
    var activeSessionId by mutableIntStateOf(-1)
        private set

    var installProgress by mutableFloatStateOf(0f)
        private set
    var isIndeterminate by mutableStateOf(false)
        private set
    var installPhase by mutableStateOf("")
        private set
    var installStatus by mutableStateOf("")
        private set
    var installDetail by mutableStateOf("")
        private set
    var totalSplitsCount by mutableIntStateOf(0)
        private set
    var currentSplitIndex by mutableIntStateOf(0)
        private set
    var installVersion by mutableIntStateOf(0)
        private set

    private val sessionCallback = object : PackageInstaller.SessionCallback() {
        override fun onCreated(sessionId: Int) {}
        override fun onBadgingChanged(sessionId: Int) {}
        override fun onActiveChanged(sessionId: Int, active: Boolean) {
            if (sessionId == activeSessionId && active && installState == InstallState.WAITING_CONFIRMATION) {
                installState = InstallState.INSTALLING_SYSTEM
                installPhase = "Installing app"
                installStatus = "System package installer is installing split APKs…"
                isIndeterminate = true
            }
        }

        override fun onProgressChanged(sessionId: Int, progress: Float) {
            if (sessionId == activeSessionId && isInstalling) {
                val mapped = (0.50f + 0.42f * progress.coerceIn(0f, 1f)).coerceIn(installProgress, 0.95f)
                installProgress = mapped
            }
        }

        override fun onFinished(sessionId: Int, success: Boolean) {
            val bundlePath = activeSessionBundles.remove(sessionId)
            if (sessionId != activeSessionId && bundlePath == null) return

            if (success) {
                onSessionSuccess(sessionId)
            } else {
                // Delay briefly so ApkInstallReceiver (which carries the specific EXTRA_STATUS and
                // EXTRA_STATUS_MESSAGE) can run first if delivered simultaneously.
                mainHandler.postDelayed({
                    if (activeSessionId == sessionId &&
                        (installState == InstallState.WAITING_CONFIRMATION ||
                            installState == InstallState.INSTALLING_SYSTEM ||
                            installState == InstallState.STAGING_SPLITS)
                    ) {
                        installState = InstallState.CANCELLED
                        isIndeterminate = false
                        installProgress = 0f
                        installPhase = "Installation cancelled"
                        installStatus = "Installation was cancelled or did not complete."
                        installDetail = ""
                    }
                }, 350L)
            }
        }
    }

    private fun ensureSessionCallbackRegistered(context: Context) {
        if (sessionCallbackRegistered) return
        synchronized(this) {
            if (!sessionCallbackRegistered) {
                runCatching {
                    context.applicationContext.packageManager.packageInstaller
                        .registerSessionCallback(sessionCallback, mainHandler)
                    sessionCallbackRegistered = true
                }
            }
        }
    }

    fun resetStatus() {
        installState = InstallState.IDLE
        installProgress = 0f
        isIndeterminate = false
        installPhase = ""
        installStatus = ""
        installDetail = ""
        totalSplitsCount = 0
        currentSplitIndex = 0
        activeSessionId = -1
    }

    internal fun onUserConfirmationRequired(sessionId: Int) {
        mainHandler.post {
            if (activeSessionId == sessionId || activeSessionId == -1) {
                activeSessionId = sessionId
                installState = InstallState.WAITING_CONFIRMATION
                installProgress = 0.90f
                isIndeterminate = true
                installPhase = "Waiting for confirmation"
                installStatus = "Confirm installation in the Android system prompt…"
                installDetail = if (totalSplitsCount > 1) {
                    "$totalSplitsCount split APKs staged in session #$sessionId"
                } else {
                    "APK staged in session #$sessionId"
                }
            }
        }
    }

    internal fun onSessionSuccess(sessionId: Int, message: String? = null) {
        mainHandler.post {
            activeSessionBundles.remove(sessionId)
            installState = InstallState.SUCCESS
            installProgress = 1.0f
            isIndeterminate = false
            installPhase = "Installation complete"
            installStatus = message ?: "App installed successfully!"
            installDetail = if (totalSplitsCount > 1) {
                "Installed $totalSplitsCount split APKs"
            } else {
                "Package installation finished"
            }
            installVersion++
            globalClass.showMsg("App installed successfully")
        }
    }

    internal fun onSessionCancelled(sessionId: Int) {
        mainHandler.post {
            activeSessionBundles.remove(sessionId)
            installState = InstallState.CANCELLED
            installProgress = 0f
            isIndeterminate = false
            installPhase = "Installation cancelled"
            installStatus = "Installation was cancelled by user."
            installDetail = ""
        }
    }

    internal fun onSessionFailed(sessionId: Int, reason: String?) {
        mainHandler.post {
            activeSessionBundles.remove(sessionId)
            installState = InstallState.FAILED
            installProgress = 0f
            isIndeterminate = false
            installPhase = "Installation failed"
            installStatus = if (!reason.isNullOrBlank()) reason else "Package installer reported an error."
            installDetail = ""
            globalClass.showMsg(
                if (!reason.isNullOrBlank()) "Installation failed: $reason"
                else "Installation failed"
            )
        }
    }

    /**
     * Install an APK bundle file (.xapk, .apkm, .apks) with live status updates.
     *
     * @param context Activity or application context
     * @param bundlePath Path to the .xapk/.apkm/.apks file
     * @param packageNameHint Optional package name of the bundle
     * @param onStarted Optional callback invoked when installation completes
     */
    suspend fun install(
        context: Context,
        bundlePath: String,
        packageNameHint: String? = null,
        onStarted: (() -> Unit)? = null
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !context.packageManager.canRequestPackageInstalls()
        ) {
            try {
                val permIntent = Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${context.packageName}")
                ).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(permIntent)
                installState = InstallState.FAILED
                installPhase = "Permission required"
                installStatus = "Please allow 'Install unknown apps' for Prism File Explorer and tap Install again."
                installDetail = ""
                globalClass.showMsg("Please allow installing unknown apps for Prism File Explorer")
            } catch (e: Exception) {
                logger.logError(e)
            }
            return
        }

        ensureSessionCallbackRegistered(context)
        attemptedFallbackPaths.remove(bundlePath)

        activeBundlePath = bundlePath
        activePackageName = packageNameHint
        activeSessionId = -1
        installState = InstallState.EXTRACTING
        installProgress = 0.02f
        isIndeterminate = false
        installPhase = "Step 1/3 • Extracting bundle"
        installStatus = "Inspecting bundle archive…"
        installDetail = File(bundlePath).name
        totalSplitsCount = 0
        currentSplitIndex = 0

        try {
            cleanupOldStagingDirs(context)

            val tempDir = File(context.cacheDir, "apk_bundle_${System.currentTimeMillis()}")
            tempDir.mkdirs()

            val apkFiles = mutableListOf<File>()
            val obbFiles = mutableListOf<File>()

            withContext(Dispatchers.IO) {
                ZipFile(File(bundlePath)).use { zip ->
                    val allEntries = zip.entries().asSequence().filter { entry ->
                        val name = entry.name
                        !entry.isDirectory &&
                            !name.startsWith("__MACOSX") &&
                            !name.substringAfterLast('/').startsWith("._") &&
                            (name.endsWith(".apk", ignoreCase = true) || name.endsWith(".obb", ignoreCase = true))
                    }.toList()

                    val totalExtractBytes = allEntries.sumOf { it.size.coerceAtLeast(0L) }.coerceAtLeast(1L)
                    var extractedBytes = 0L

                    allEntries.forEachIndexed { idx, entry ->
                        val safeRelPath = entry.name.trimStart('/', '\\').replace("..", "_")
                        val outFile = File(tempDir, safeRelPath)
                        outFile.parentFile?.mkdirs()
                        val shortName = outFile.name

                        withContext(Dispatchers.Main) {
                            installStatus = "Extracting $shortName (${idx + 1}/${allEntries.size})…"
                            installDetail = "${extractedBytes.toFormattedSize()} / ${totalExtractBytes.toFormattedSize()}"
                        }

                        zip.getInputStream(entry).use { input ->
                            FileOutputStream(outFile).use { fos ->
                                val buffer = ByteArray(32 * 1024)
                                var len: Int
                                var lastUiUpdateBytes = extractedBytes
                                while (input.read(buffer).also { len = it } > 0) {
                                    fos.write(buffer, 0, len)
                                    extractedBytes += len
                                    if (extractedBytes - lastUiUpdateBytes >= 256 * 1024L) {
                                        lastUiUpdateBytes = extractedBytes
                                        val pct = (0.40f * (extractedBytes.toFloat() / totalExtractBytes)).coerceIn(0.02f, 0.40f)
                                        installProgress = pct
                                        installDetail = "${extractedBytes.toFormattedSize()} / ${totalExtractBytes.toFormattedSize()}"
                                    }
                                }
                            }
                        }

                        val pct = (0.40f * (extractedBytes.toFloat() / totalExtractBytes)).coerceIn(0.02f, 0.40f)
                        installProgress = pct
                        installDetail = "${extractedBytes.toFormattedSize()} / ${totalExtractBytes.toFormattedSize()}"

                        when {
                            outFile.extension.equals("apk", true) -> apkFiles.add(outFile)
                            outFile.extension.equals("obb", true) -> obbFiles.add(outFile)
                        }
                    }
                }
            }

            if (apkFiles.isEmpty()) {
                tempDir.deleteRecursively()
                throw Exception("No APK files found in the bundle")
            }

            // Copy OBB files if any
            if (obbFiles.isNotEmpty()) {
                installState = InstallState.COPYING_OBB
                installPhase = "Copying OBB expansion files"
                val totalObbBytes = obbFiles.sumOf { it.length() }.coerceAtLeast(1L)
                var copiedObbBytes = 0L

                for ((idx, obb) in obbFiles.withIndex()) {
                    val obbName = obb.name
                    installStatus = "Copying OBB ${idx + 1}/${obbFiles.size}: $obbName"
                    val parts = obbName.split(".")
                    if (parts.size >= 4) {
                        val packageName = parts.drop(2).dropLast(1).joinToString(".")
                        val obbDir = File(
                            Environment.getExternalStorageDirectory(),
                            "Android/obb/$packageName"
                        )
                        obbDir.mkdirs()
                        val destObb = File(obbDir, obb.name)
                        withContext(Dispatchers.IO) {
                            FileInputStream(obb).use { input ->
                                FileOutputStream(destObb).use { output ->
                                    val buf = ByteArray(32 * 1024)
                                    var read: Int
                                    while (input.read(buf).also { read = it } > 0) {
                                        output.write(buf, 0, read)
                                        copiedObbBytes += read
                                        installProgress = 0.40f + 0.08f * (copiedObbBytes.toFloat() / totalObbBytes).coerceIn(0f, 1f)
                                        installDetail = "${copiedObbBytes.toFormattedSize()} / ${totalObbBytes.toFormattedSize()}"
                                    }
                                }
                            }
                        }
                    }
                }
            }

            val filteredApks = filterCompatibleSplitApks(apkFiles)
            totalSplitsCount = filteredApks.size

            // Install all bundles (1 or more APKs) via PackageInstaller.Session so we get live
            // session callbacks (STATUS_PENDING_USER_ACTION -> STATUS_SUCCESS / STATUS_FAILURE),
            // falling back to merge + sign if splits are unsigned.
            val allSigned = withContext(Dispatchers.IO) {
                filteredApks.all { isApkSigned(context, it) }
            }

            if (allSigned) {
                installState = InstallState.STAGING_SPLITS
                installPhase = "Step 2/3 • Staging split APKs"
                try {
                    installSplitApks(context, filteredApks, bundlePath)
                    withContext(Dispatchers.IO) {
                        tempDir.deleteRecursively()
                    }
                } catch (sessionError: Exception) {
                    logger.logError(sessionError)
                    // Fallback: merge & sign into a single universal APK and stage in session
                    mergeSignAndCommitFallback(context, File(bundlePath), tempDir)
                }
            } else {
                // Unsigned splits inside bundle: merge & sign first, then stage in session
                mergeSignAndCommitFallback(context, File(bundlePath), tempDir)
            }
        } catch (e: Exception) {
            logger.logError(e)
            onSessionFailed(-1, e.message ?: "Unexpected installation error")
        }
    }

    private suspend fun mergeSignAndCommitFallback(
        context: Context,
        bundleFile: File,
        tempDir: File
    ) {
        installState = InstallState.MERGING_SIGNING
        isIndeterminate = true
        installProgress = 0.55f
        installPhase = "Step 2/3 • Merging & signing split APKs"
        installStatus = "Merging split APKs into universal APK…"
        installDetail = bundleFile.name

        val mergedSigned = withContext(Dispatchers.IO) {
            mergeAndSignBundle(
                bundleFile = bundleFile,
                workDir = tempDir,
                onSigningPhase = {
                    mainHandler.post {
                        installProgress = 0.75f
                        installStatus = "Signing merged APK with local key…"
                    }
                }
            )
        }
        if (mergedSigned != null && mergedSigned.exists()) {
            isIndeterminate = false
            totalSplitsCount = 1
            installState = InstallState.STAGING_SPLITS
            installPhase = "Step 2/3 • Staging merged APK"
            installSplitApks(context, listOf(mergedSigned), bundleFile.absolutePath)
            withContext(Dispatchers.IO) {
                tempDir.deleteRecursively()
            }
        } else {
            withContext(Dispatchers.IO) {
                tempDir.deleteRecursively()
            }
            throw Exception("Failed to merge and sign split APK bundle")
        }
    }

    /**
     * Triggered asynchronously by [ApkInstallReceiver] if [PackageInstaller.Session] reports
     * [PackageInstaller.STATUS_FAILURE_INVALID] or [PackageInstaller.STATUS_FAILURE_INCOMPATIBLE].
     */
    fun fallbackMergeAndInstallAsync(
        context: Context,
        bundlePath: String,
        failureReason: String? = null
    ) {
        if (!attemptedFallbackPaths.add(bundlePath)) {
            onSessionFailed(activeSessionId, failureReason)
            return
        }
        val bundleFile = File(bundlePath)
        if (!bundleFile.exists()) {
            onSessionFailed(activeSessionId, failureReason)
            return
        }
        installerScope.launch {
            val tempDir = File(context.cacheDir, "apk_bundle_fallback_${System.currentTimeMillis()}").apply {
                mkdirs()
            }
            try {
                mergeSignAndCommitFallback(context, bundleFile, tempDir)
            } catch (e: Exception) {
                logger.logError(e)
                onSessionFailed(activeSessionId, failureReason ?: e.message)
            }
        }
    }

    /**
     * Launches the Android system package installer for a standalone `.apk` file.
     */
    fun installSingleApk(context: Context, apkFile: File) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !context.packageManager.canRequestPackageInstalls()
        ) {
            try {
                val permIntent = Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${context.packageName}")
                ).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(permIntent)
                globalClass.showMsg("Please allow installing unknown apps for Prism File Explorer")
            } catch (e: Exception) {
                logger.logError(e)
            }
            return
        }

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.provider",
            apkFile
        )

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    private suspend fun installSplitApks(
        context: Context,
        apkFiles: List<File>,
        bundlePath: String
    ) {
        withContext(Dispatchers.IO) {
            val packageInstaller = context.packageManager.packageInstaller
            val params = PackageInstaller.SessionParams(
                PackageInstaller.SessionParams.MODE_FULL_INSTALL
            )

            val totalSize = apkFiles.sumOf { it.length() }.coerceAtLeast(1L)
            params.setSize(totalSize)

            val sessionId = packageInstaller.createSession(params)
            activeSessionId = sessionId
            activeSessionBundles[sessionId] = bundlePath

            val session = packageInstaller.openSession(sessionId)

            var committed = false
            var writtenTotalBytes = 0L
            try {
                for ((index, apkFile) in apkFiles.withIndex()) {
                    currentSplitIndex = index + 1
                    withContext(Dispatchers.Main) {
                        isIndeterminate = false
                        installStatus = "Staging APK ${index + 1}/${apkFiles.size}: ${apkFile.name}"
                        installDetail = "${writtenTotalBytes.toFormattedSize()} / ${totalSize.toFormattedSize()}"
                    }

                    val sanitizedName = "split_${index}_${apkFile.name.replace(Regex("[^A-Za-z0-9._-]"), "_")}"
                    FileInputStream(apkFile).use { input ->
                        session.openWrite(sanitizedName, 0, apkFile.length()).use { output ->
                            val buffer = ByteArray(32 * 1024)
                            var len: Int
                            var lastUiUpdate = writtenTotalBytes
                            while (input.read(buffer).also { len = it } > 0) {
                                output.write(buffer, 0, len)
                                writtenTotalBytes += len
                                if (writtenTotalBytes - lastUiUpdate >= 256 * 1024L) {
                                    lastUiUpdate = writtenTotalBytes
                                    val pct = 0.45f + 0.43f * (writtenTotalBytes.toFloat() / totalSize).coerceIn(0f, 1f)
                                    installProgress = pct
                                    installDetail = "${writtenTotalBytes.toFormattedSize()} / ${totalSize.toFormattedSize()}"
                                }
                            }
                            session.fsync(output)
                        }
                    }
                    val pct = 0.45f + 0.43f * (writtenTotalBytes.toFloat() / totalSize).coerceIn(0f, 1f)
                    installProgress = pct
                    installDetail = "${writtenTotalBytes.toFormattedSize()} / ${totalSize.toFormattedSize()}"
                }

                withContext(Dispatchers.Main) {
                    installState = InstallState.WAITING_CONFIRMATION
                    installProgress = 0.90f
                    isIndeterminate = true
                    installPhase = "Step 3/3 • Waiting for confirmation"
                    installStatus = "Confirm installation in the Android system prompt…"
                    installDetail = "${apkFiles.size} APK(s) staged (${totalSize.toFormattedSize()})"
                }

                // Commit the session with an explicit BroadcastReceiver PendingIntent so
                // STATUS_PENDING_USER_ACTION immediately launches the system confirmation dialog
                // and STATUS_SUCCESS / STATUS_FAILURE updates the live status card.
                val broadcastIntent = Intent(context, ApkInstallReceiver::class.java).apply {
                    action = ApkInstallReceiver.ACTION_INSTALL_COMMIT
                    setPackage(context.packageName)
                    putExtra(ApkInstallReceiver.EXTRA_BUNDLE_PATH, bundlePath)
                }
                val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                } else {
                    PendingIntent.FLAG_UPDATE_CURRENT
                }
                val pendingIntent = PendingIntent.getBroadcast(
                    context,
                    sessionId,
                    broadcastIntent,
                    flags
                )
                session.commit(pendingIntent.intentSender)
                committed = true
            } catch (e: Exception) {
                activeSessionBundles.remove(sessionId)
                if (!committed) {
                    try {
                        session.abandon()
                    } catch (_: Exception) {
                    }
                }
                throw e
            } finally {
                try {
                    session.close()
                } catch (_: Exception) {
                }
            }
        }
    }

    /**
     * Filters out incompatible ABI split APKs when a bundle contains splits for multiple ABIs
     * (e.g., universal `.apks` archives containing arm64_v8a, armeabi_v7a, x86, and x86_64 splits).
     */
    private fun filterCompatibleSplitApks(apkFiles: List<File>): List<File> {
        if (apkFiles.size <= 1) return apkFiles

        val splitsDirFiles = apkFiles.filter {
            it.invariantSeparatorsPath.contains("/splits/")
        }
        val candidates = if (splitsDirFiles.isNotEmpty()) splitsDirFiles else apkFiles

        val knownAbis = listOf(
            "arm64-v8a" to listOf("arm64_v8a", "arm64-v8a"),
            "armeabi-v7a" to listOf("armeabi_v7a", "armeabi-v7a"),
            "armeabi" to listOf("armeabi"),
            "x86_64" to listOf("x86_64", "x86-64"),
            "x86" to listOf("x86")
        )

        fun detectAbi(fileName: String): String? {
            val lower = fileName.lowercase()
            for ((canonicalAbi, tokens) in knownAbis) {
                for (token in tokens) {
                    if (Regex("""(^|[._-])${Regex.escape(token)}([._-]|$)""").containsMatchIn(lower)) {
                        return canonicalAbi
                    }
                }
            }
            return null
        }

        val abiTaggedFiles = candidates.mapNotNull { file ->
            detectAbi(file.name)?.let { abi -> file to abi }
        }
        if (abiTaggedFiles.isEmpty()) return candidates

        val presentAbis = abiTaggedFiles.map { it.second }.toSet()
        if (presentAbis.size <= 1) return candidates

        val preferredAbi = Build.SUPPORTED_ABIS.firstOrNull { it in presentAbis }
            ?: return candidates

        return candidates.filter { file ->
            val fileAbi = detectAbi(file.name)
            fileAbi == null || fileAbi == preferredAbi
        }
    }

    /**
     * Checks whether [apkFile] carries a signature (v1 META-INF or v2/v3 signing block).
     */
    private fun isApkSigned(context: Context, apkFile: File): Boolean {
        try {
            val pm = context.packageManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val info = pm.getPackageArchiveInfo(
                    apkFile.absolutePath,
                    PackageManager.GET_SIGNING_CERTIFICATES
                )
                if (info?.signingInfo != null) return true
            } else {
                @Suppress("DEPRECATION")
                val info = pm.getPackageArchiveInfo(
                    apkFile.absolutePath,
                    PackageManager.GET_SIGNATURES
                )
                @Suppress("DEPRECATION")
                if (!info?.signatures.isNullOrEmpty()) return true
            }
        } catch (_: Exception) {
        }

        return try {
            ZipFile(apkFile).use { zip ->
                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    val entry: ZipEntry = entries.nextElement()
                    val upper = entry.name.uppercase()
                    if (upper.startsWith("META-INF/") &&
                        (upper.endsWith(".RSA") || upper.endsWith(".DSA") || upper.endsWith(".EC"))
                    ) {
                        return true
                    }
                }
                false
            }
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Merges a split APK bundle into a single `.apk` using [Merger] and signs it with [LocalApkSigningKey].
     */
    fun mergeAndSignBundle(
        bundleFile: File,
        workDir: File,
        onSigningPhase: (() -> Unit)? = null
    ): File? {
        val rawMergedFile = File(workDir, "${bundleFile.nameWithoutExtension}_merged_unsigned.apk")
        val signedOutputFile = File(workDir, "${bundleFile.nameWithoutExtension}_merged_signed.apk")
        return try {
            val options = MergerOptions().apply {
                inputFile = bundleFile
                outputFile = rawMergedFile
            }
            Merger(options).runCommand()
            if (!rawMergedFile.exists() || rawMergedFile.length() == 0L) {
                rawMergedFile.delete()
                return null
            }
            onSigningPhase?.invoke()
            val signed = signStandaloneApk(rawMergedFile, signedOutputFile)
            rawMergedFile.delete()
            signed
        } catch (e: Exception) {
            logger.logError(e)
            rawMergedFile.delete()
            signedOutputFile.delete()
            null
        }
    }

    /**
     * Signs [inputApk] into [outputApk] using [LocalApkSigningKey].
     */
    fun signStandaloneApk(inputApk: File, outputApk: File): File? {
        return try {
            val (signingKey, certificate) = LocalApkSigningKey.load(globalClass)
            val keyConfig = KeyConfig.Jca(signingKey)
            val signerConfig = ApkSigner.SignerConfig.Builder(
                "Prism",
                keyConfig,
                listOf(certificate)
            ).build()

            ApkSigner.Builder(listOf(signerConfig))
                .setInputApk(inputApk)
                .setOutputApk(outputApk)
                .setV1SigningEnabled(true)
                .setV2SigningEnabled(true)
                .build()
                .sign()

            if (outputApk.exists() && outputApk.length() > 0L) outputApk else null
        } catch (e: Exception) {
            logger.logError(e)
            outputApk.delete()
            null
        }
    }

    private fun cleanupOldStagingDirs(context: Context) {
        try {
            val cutoff = System.currentTimeMillis() - 15 * 60 * 1000L
            context.cacheDir.listFiles()?.forEach { file ->
                if (file.isDirectory &&
                    (file.name.startsWith("apk_bundle_") || file.name.startsWith("apk_bundle_fallback_")) &&
                    file.lastModified() < cutoff
                ) {
                    file.deleteRecursively()
                }
            }
        } catch (_: Exception) {
        }
    }

    /**
     * Check if a file is an APK bundle format.
     */
    fun isApkBundle(extension: String): Boolean {
        return extension.lowercase() in setOf("xapk", "apkm", "apks")
    }
}
