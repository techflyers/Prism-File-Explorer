package com.techflyers.compose.file.explorer.saf

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Looper
import android.os.storage.StorageManager
import android.os.storage.StorageVolume
import android.provider.DocumentsContract
import android.webkit.MimeTypeMap
import com.techflyers.compose.file.explorer.App.Companion.globalClass
import com.techflyers.compose.file.explorer.App.Companion.logger
import com.techflyers.compose.file.explorer.base.BaseActivity
import com.techflyers.compose.file.explorer.screen.main.tab.files.provider.StorageProvider
import com.techflyers.compose.file.explorer.screen.main.tab.files.shizuku.ShizukuManager
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.lang.ref.WeakReference
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages Storage Access Framework (SAF) tree grants and [DocumentsContract] file operations
 * for removable SD card and USB OTG volumes, particularly on Android 10 (API 29) and below
 * (API < 30) where `WRITE_EXTERNAL_STORAGE` only permits direct POSIX writes to primary
 * external storage and the app's own `Android/data/<package>` directory.
 */
object SafStorageManager {

    private const val EXTERNAL_STORAGE_AUTHORITY = "com.android.externalstorage.documents"
    private const val PREFS_NAME = "prism_saf_volume_grants"

    data class RemovableVolumeInfo(
        val volumeId: String,
        val rootFile: File,
        val description: String,
        val relativePath: String
    )

    private data class GrantedTree(
        val treeUri: Uri,
        val treeVolumeId: String,
        val treeRelativePath: String
    )

    @Volatile
    private var activeActivityRef: WeakReference<BaseActivity>? = null

    private val promptMutex = Mutex()

    @Volatile
    private var pendingGrantDeferred: CompletableDeferred<Boolean>? = null

    @Volatile
    private var pendingGrantVolumeInfo: RemovableVolumeInfo? = null

    private val promptedVolumesOnOpen: MutableSet<String> =
        Collections.newSetFromMap(ConcurrentHashMap<String, Boolean>())

    fun registerActivity(activity: BaseActivity) {
        activeActivityRef = WeakReference(activity)
    }

    fun unregisterActivity(activity: BaseActivity) {
        if (activeActivityRef?.get() === activity) {
            activeActivityRef = null
        }
    }

    private fun normalizePath(rawPath: String): String {
        var p = rawPath
        if (p.contains("storage_root")) {
            p = if (p.startsWith("/storage_root")) {
                p.removePrefix("/storage_root")
            } else {
                val suffix = p.substringAfterLast("storage_root")
                if (suffix.startsWith("/")) suffix else "/$suffix"
            }
        }
        return if (p.length > 1 && p.endsWith("/")) p.trimEnd('/') else p
    }

    /**
     * Returns true if [path] is inside this app's own private external directories
     * (`/Android/data/<package>` or `/Android/obb/<package>`), which are always directly writable
     * via `java.io.File` without SAF.
     */
    fun isAppPrivateExternalPath(path: String): Boolean {
        val pkg = runCatching { globalClass.packageName }.getOrDefault("com.techflyers.compose.file.explorer")
        val normalized = normalizePath(path)
        return normalized.contains("/Android/data/$pkg") ||
                normalized.contains("/Android/obb/$pkg") ||
                normalized.contains("/Android/media/$pkg")
    }

    /**
     * Resolves removable/secondary volume metadata for [file], or returns `null` if [file]
     * resides on primary internal storage, internal app data, or the app's own external dirs.
     */
    fun getRemovableVolumeInfo(file: File): RemovableVolumeInfo? =
        getRemovableVolumeInfo(file.absolutePath)

    fun getRemovableVolumeInfo(rawPath: String): RemovableVolumeInfo? {
        val cleanPath = normalizePath(rawPath)
        if (cleanPath.isEmpty() || cleanPath == "/" || cleanPath == "/storage" || cleanPath == "/mnt") {
            return null
        }
        if (isAppPrivateExternalPath(cleanPath)) {
            return null
        }

        val primaryRoot = runCatching {
            Environment.getExternalStorageDirectory().absolutePath.trimEnd('/')
        }.getOrDefault("/storage/emulated/0")

        if (cleanPath == primaryRoot ||
            cleanPath.startsWith("$primaryRoot/") ||
            cleanPath.startsWith("/storage/emulated/") ||
            cleanPath.startsWith("/storage/self") ||
            cleanPath.startsWith("/data/")
        ) {
            return null
        }

        val context = runCatching { globalClass }.getOrNull() ?: return null
        val storageManager = runCatching {
            context.getSystemService(Context.STORAGE_SERVICE) as? StorageManager
        }.getOrNull()

        var matchedRoot: File? = null
        var matchedVolumeId: String? = null
        var matchedDesc: String? = null

        // 1. Check /storage/<VOLUME-ID> or /mnt/media_rw/<VOLUME-ID>
        val storagePrefix = when {
            cleanPath.startsWith("/storage/") -> "/storage/"
            cleanPath.startsWith("/mnt/media_rw/") -> "/mnt/media_rw/"
            else -> null
        }
        if (storagePrefix != null) {
            val remainder = cleanPath.removePrefix(storagePrefix)
            val volSegment = remainder.substringBefore('/').trim()
            if (volSegment.isNotEmpty() &&
                !volSegment.equals("emulated", ignoreCase = true) &&
                !volSegment.equals("self", ignoreCase = true)
            ) {
                val candidateStorage = File("/storage/$volSegment")
                val candidateRoot = if (candidateStorage.exists()) {
                    candidateStorage
                } else {
                    File("$storagePrefix$volSegment")
                }
                matchedRoot = candidateRoot
                matchedVolumeId = volSegment
            }
        }

        // 2. Check StorageProvider's discovered external storage directories
        if (matchedRoot == null) {
            val externalDirs = runCatching {
                StorageProvider.getExternalStorageDirectories(context)
            }.getOrDefault(emptyList())

            for ((label, extRoot) in externalDirs) {
                val rootPath = extRoot.absolutePath.trimEnd('/')
                if (rootPath != primaryRoot &&
                    (cleanPath == rootPath || cleanPath.startsWith("$rootPath/"))
                ) {
                    matchedRoot = extRoot
                    matchedVolumeId = extRoot.name
                    matchedDesc = label.takeIf { it.isNotBlank() }
                    break
                }
            }
        }

        // 3. Enrich with StorageManager / StorageVolume if available
        val probeFile = matchedRoot ?: File(cleanPath)
        val storageVolume: StorageVolume? = runCatching {
            storageManager?.getStorageVolume(probeFile)
                ?: storageManager?.storageVolumes?.firstOrNull { sv ->
                    !sv.isPrimary && sv.uuid != null &&
                            (sv.uuid.equals(matchedVolumeId, ignoreCase = true) ||
                                    cleanPath.contains("/${sv.uuid}", ignoreCase = true))
                }
        }.getOrNull()

        if (storageVolume != null) {
            if (storageVolume.isPrimary && matchedRoot == null) {
                return null
            }
            val uuid = storageVolume.uuid
            if (!uuid.isNullOrBlank()) {
                matchedVolumeId = uuid
                if (matchedRoot == null) {
                    val dir = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        storageVolume.directory
                    } else {
                        File("/storage/$uuid")
                    }
                    if (dir != null) matchedRoot = dir
                }
            }
            val desc = runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    storageVolume.getDescription(context)
                } else {
                    @Suppress("DEPRECATION")
                    storageVolume.getDescription(context)
                }
            }.getOrNull()
            if (!desc.isNullOrBlank()) {
                matchedDesc = desc
            }
        }

        val finalRoot = matchedRoot ?: return null
        val finalVolumeId = matchedVolumeId?.takeIf { it.isNotBlank() } ?: finalRoot.name
        val rootAbs = finalRoot.absolutePath.trimEnd('/')

        val relPath = when {
            cleanPath == rootAbs -> ""
            cleanPath.startsWith("$rootAbs/") -> cleanPath.removePrefix("$rootAbs/").trim('/')
            cleanPath.startsWith("/mnt/media_rw/$finalVolumeId") ->
                cleanPath.removePrefix("/mnt/media_rw/$finalVolumeId").trim('/')
            else -> ""
        }

        return RemovableVolumeInfo(
            volumeId = finalVolumeId,
            rootFile = finalRoot,
            description = matchedDesc?.takeIf { it.isNotBlank() } ?: finalVolumeId,
            relativePath = relPath
        )
    }

    fun isOnRemovableVolume(file: File): Boolean = getRemovableVolumeInfo(file) != null

    fun isOnRemovableVolume(path: String): Boolean = getRemovableVolumeInfo(path) != null

    fun isOnRemovableStorage(file: File): Boolean = getRemovableVolumeInfo(file) != null

    fun isOnRemovableStorage(path: String): Boolean = getRemovableVolumeInfo(path) != null

    fun isSameVolume(first: File, second: File): Boolean {
        val v1 = getRemovableVolumeInfo(first) ?: return false
        val v2 = getRemovableVolumeInfo(second) ?: return false
        return v1.volumeId.equals(v2.volumeId, ignoreCase = true)
    }

    /**
     * Returns true if direct POSIX (`java.io.File`) writes are restricted on [file]'s volume
     * and SAF should be used.
     *
     * On Android 10 and below (`API < 30`), removable SD cards NEVER allow direct POSIX writes
     * outside `Android/data/<package>`. On API 30+, SAF is used if the volume root is not directly writable.
     */
    fun requiresSafForWrite(file: File): Boolean {
        val vol = getRemovableVolumeInfo(file) ?: return false
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return true
        }
        val nearestExisting = findNearestExistingFile(file) ?: vol.rootFile
        return !nearestExisting.canWrite()
    }

    /**
     * Returns true if [file] is on a removable volume that requires SAF for write access,
     * and neither a SAF tree grant nor Shizuku/Root is currently active.
     */
    fun needsSafGrant(file: File): Boolean {
        if (ShizukuManager.isPrivileged) return false
        if (!requiresSafForWrite(file)) return false
        return !hasSafGrant(file)
    }

    /**
     * Returns true if [file] can be written to (either because it already has a SAF grant
     * or is on a removable volume where SAF can be used).
     */
    fun canWrite(file: File): Boolean {
        if (file.canWrite()) return true
        val vol = getRemovableVolumeInfo(file) ?: return false
        if (hasSafGrant(vol)) return true
        // Allow UI write actions on removable storage so tapping Create/Rename/Delete/Paste/Save
        // can trigger the SAF permission grant flow instead of disabling buttons.
        return true
    }

    fun hasSafGrant(file: File): Boolean {
        val vol = getRemovableVolumeInfo(file) ?: return false
        return findGrantedTree(vol) != null
    }

    fun hasSafGrant(vol: RemovableVolumeInfo): Boolean = findGrantedTree(vol) != null

    private fun findGrantedTree(vol: RemovableVolumeInfo): GrantedTree? {
        val context = runCatching { globalClass }.getOrNull() ?: return null
        val persisted = runCatching {
            context.contentResolver.persistedUriPermissions
        }.getOrDefault(emptyList())

        var bestMatch: GrantedTree? = null

        for (perm in persisted) {
            if (!perm.isWritePermission) continue
            val uri = perm.uri ?: continue
            val treeDocId = runCatching { DocumentsContract.getTreeDocumentId(uri) }.getOrNull() ?: continue
            val treeVolId = treeDocId.substringBefore(':')
            val treeRel = treeDocId.substringAfter(':', "").trim('/')

            if (treeVolId.equals(vol.volumeId, ignoreCase = true)) {
                val coversTarget = treeRel.isEmpty() ||
                        vol.relativePath.equals(treeRel, ignoreCase = true) ||
                        vol.relativePath.startsWith("$treeRel/", ignoreCase = true)
                if (coversTarget) {
                    if (treeRel.isEmpty()) {
                        return GrantedTree(uri, treeVolId, "")
                    }
                    if (bestMatch == null || treeRel.length < bestMatch.treeRelativePath.length) {
                        bestMatch = GrantedTree(uri, treeVolId, treeRel)
                    }
                }
            }
        }

        if (bestMatch != null) return bestMatch

        // Fallback: check saved volume preferences if persisted permission still holds
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedUriStr = prefs.getString(vol.volumeId.uppercase(), null)
            ?: prefs.getString(vol.rootFile.absolutePath, null)
        if (!savedUriStr.isNullOrEmpty()) {
            val savedUri = runCatching { Uri.parse(savedUriStr) }.getOrNull()
            if (savedUri != null && persisted.any { it.uri == savedUri && it.isWritePermission }) {
                val treeDocId = runCatching { DocumentsContract.getTreeDocumentId(savedUri) }.getOrNull()
                if (treeDocId != null) {
                    val treeVolId = treeDocId.substringBefore(':')
                    val treeRel = treeDocId.substringAfter(':', "").trim('/')
                    if (treeRel.isEmpty() ||
                        vol.relativePath.equals(treeRel, ignoreCase = true) ||
                        vol.relativePath.startsWith("$treeRel/", ignoreCase = true)
                    ) {
                        return GrantedTree(savedUri, treeVolId, treeRel)
                    }
                }
            }
        }

        return null
    }

    private fun saveGrantedTreeUri(vol: RemovableVolumeInfo?, treeUri: Uri) {
        val context = runCatching { globalClass }.getOrNull() ?: return
        val treeDocId = runCatching { DocumentsContract.getTreeDocumentId(treeUri) }.getOrNull()
        val treeVolId = treeDocId?.substringBefore(':')?.takeIf { it.isNotBlank() } ?: vol?.volumeId
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().apply {
            if (!treeVolId.isNullOrBlank()) {
                putString(treeVolId.uppercase(), treeUri.toString())
            }
            if (vol != null) {
                putString(vol.volumeId.uppercase(), treeUri.toString())
                putString(vol.rootFile.absolutePath, treeUri.toString())
            }
            apply()
        }
    }

    /**
     * Builds the system SAF intent (`ACTION_OPEN_DOCUMENT_TREE` or scoped volume access intent)
     * pre-targeted to the removable volume root and resolved to the Android system DocumentsUI
     * so Prism's own `FilePickerActivity` never intercepts it.
     */
    fun buildOpenDocumentTreeIntent(context: Context, vol: RemovableVolumeInfo): Intent {
        val storageManager = runCatching {
            context.getSystemService(Context.STORAGE_SERVICE) as? StorageManager
        }.getOrNull()

        val storageVolume: StorageVolume? = runCatching {
            storageManager?.getStorageVolume(vol.rootFile)
                ?: storageManager?.storageVolumes?.firstOrNull { sv ->
                    sv.uuid != null && sv.uuid.equals(vol.volumeId, ignoreCase = true)
                }
        }.getOrNull()

        var intent: Intent? = null

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && storageVolume != null) {
            intent = runCatching { storageVolume.createOpenDocumentTreeIntent() }.getOrNull()
        } else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q && storageVolume != null) {
            @Suppress("DEPRECATION")
            val legacyIntent = runCatching { storageVolume.createAccessIntent(null) }.getOrNull()
            if (legacyIntent != null && legacyIntent.resolveActivity(context.packageManager) != null) {
                intent = legacyIntent
            }
        }

        val rootTreeUri = runCatching {
            DocumentsContract.buildRootUri(EXTERNAL_STORAGE_AUTHORITY, vol.volumeId)
        }.getOrNull()

        val initialDocUri = runCatching {
            DocumentsContract.buildDocumentUri(EXTERNAL_STORAGE_AUTHORITY, "${vol.volumeId}:")
        }.getOrNull()

        if (intent == null) {
            intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
                val initUri = initialDocUri ?: rootTreeUri
                if (initUri != null) {
                    putExtra(DocumentsContract.EXTRA_INITIAL_URI, initUri)
                }
            }
        } else if (intent.action == Intent.ACTION_OPEN_DOCUMENT_TREE &&
            !intent.hasExtra(DocumentsContract.EXTRA_INITIAL_URI)
        ) {
            val initUri = initialDocUri ?: rootTreeUri
            if (initUri != null) {
                intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, initUri)
            }
        }

        intent.addFlags(
            Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                    Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or
                    Intent.FLAG_GRANT_PREFIX_URI_PERMISSION
        )

        // Ensure the intent resolves to Android's system DocumentsUI rather than Prism's own FilePickerActivity
        if (intent.component == null && intent.`package` == null) {
            runCatching {
                val pm = context.packageManager
                val candidates = pm.queryIntentActivities(intent, 0)
                    .mapNotNull { it.activityInfo }
                    .filter { it.packageName != context.packageName }

                val preferred = candidates.firstOrNull {
                    it.packageName == "com.android.documentsui" ||
                            it.packageName == "com.google.android.documentsui"
                } ?: candidates.firstOrNull {
                    (it.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                } ?: candidates.firstOrNull()

                if (preferred != null) {
                    intent.setClassName(preferred.packageName, preferred.name)
                }
            }
        }

        return intent
    }

    /**
     * Called when the user opens a folder on a removable volume (e.g., when first opening the SD card).
     * Prompts once per volume per session if write access hasn't been granted yet.
     */
    fun promptSafGrantOnOpenIfNeeded(folder: File, onGranted: (() -> Unit)? = null) {
        if (!needsSafGrant(folder)) return
        val vol = getRemovableVolumeInfo(folder) ?: return
        val key = vol.volumeId.uppercase()
        if (!promptedVolumesOnOpen.add(key)) return

        val activity = activeActivityRef?.get() ?: return
        activity.runOnUiThread {
            globalClass.showMsg("Grant access to '${vol.description}' to allow editing files on SD card")
            activity.launchSafTreeRequest(vol) { granted ->
                if (granted) {
                    onGranted?.invoke()
                }
            }
        }
    }

    /**
     * Ensures a persistable SAF grant exists for [file].
     * If not yet granted, launches the system SAF picker via the active [BaseActivity]
     * and suspends until the user completes the prompt.
     */
    suspend fun ensureSafGrant(file: File): Boolean {
        val vol = getRemovableVolumeInfo(file) ?: return false
        if (hasSafGrant(vol)) return true

        return promptMutex.withLock {
            if (hasSafGrant(vol)) return@withLock true
            val activity = activeActivityRef?.get() ?: return@withLock false
            val deferred = CompletableDeferred<Boolean>()
            pendingGrantDeferred = deferred
            pendingGrantVolumeInfo = vol

            withContext(Dispatchers.Main) {
                globalClass.showMsg("Select '${vol.description}' root folder to grant SD card write access")
                val launched = activity.launchSafTreeRequest(vol) { granted ->
                    if (!deferred.isCompleted) {
                        deferred.complete(granted && hasSafGrant(vol))
                    }
                }
                if (!launched && !deferred.isCompleted) {
                    deferred.complete(false)
                }
            }

            try {
                deferred.await()
            } finally {
                if (pendingGrantDeferred === deferred) {
                    pendingGrantDeferred = null
                    pendingGrantVolumeInfo = null
                }
            }
        }
    }

    /**
     * Blocking variant of [ensureSafGrant] safe to call from background IO threads.
     */
    fun ensureSafGrantBlocking(file: File): Boolean {
        val vol = getRemovableVolumeInfo(file) ?: return false
        if (hasSafGrant(vol)) return true
        if (Looper.myLooper() == Looper.getMainLooper()) {
            // Never block the main thread; trigger async launch and return false for this sync call
            val activity = activeActivityRef?.get()
            activity?.launchSafTreeRequest(vol, null)
            return false
        }
        return runBlocking(Dispatchers.IO) {
            ensureSafGrant(file)
        }
    }

    /**
     * Invoked by [BaseActivity] when the system SAF document tree picker returns a result.
     */
    fun onSafTreeResult(context: Context, treeUri: Uri?): Boolean {
        val targetVol = pendingGrantVolumeInfo
        if (treeUri == null) {
            pendingGrantDeferred?.complete(false)
            return false
        }

        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        try {
            context.contentResolver.takePersistableUriPermission(treeUri, flags)
        } catch (e: Exception) {
            logger.logError(e)
        }

        saveGrantedTreeUri(targetVol, treeUri)
        val isGranted = if (targetVol != null) hasSafGrant(targetVol) else true
        if (!isGranted && targetVol != null) {
            globalClass.showMsg("Please grant access to the root of '${targetVol.description}'")
        } else {
            globalClass.mainActivityManager.refreshAllTabs()
        }
        pendingGrantDeferred?.complete(isGranted)
        return isGranted
    }

    // ─── DocumentsContract URI Builders ───────────────────────────────────────

    private fun buildDocumentId(granted: GrantedTree, relativePath: String): String {
        val cleanRel = relativePath.trim('/')
        return "${granted.treeVolumeId}:$cleanRel"
    }

    private fun buildDocumentUri(granted: GrantedTree, relativePath: String): Uri {
        val docId = buildDocumentId(granted, relativePath)
        return DocumentsContract.buildDocumentUriUsingTree(granted.treeUri, docId)
    }

    private fun resolveMimeTypeForFile(fileName: String, explicitMime: String? = null): String {
        if (!explicitMime.isNullOrBlank()) return explicitMime
        val ext = fileName.substringAfterLast('.', "").lowercase()
        if (ext.isNotEmpty() && ext != fileName.lowercase()) {
            val fromMap = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
            if (!fromMap.isNullOrBlank()) return fromMap
        }
        // Passing "*/*" prevents Android's FileUtils.buildUniqueFileWithExtension from appending
        // ".bin" or ".txt" to extensionless or custom-extension filenames.
        return "*/*"
    }

    // ─── SAF File & Directory Operations ──────────────────────────────────────

    /**
     * Creates [directory] and any missing ancestor directories on a removable volume via SAF.
     */
    fun mkdirs(directory: File, promptIfNeeded: Boolean = true): Boolean {
        if (directory.exists() && directory.isDirectory) return true
        val vol = getRemovableVolumeInfo(directory) ?: return false
        if (promptIfNeeded && !hasSafGrant(vol)) {
            ensureSafGrantBlocking(directory)
        }
        val granted = findGrantedTree(vol) ?: return false
        val resolver = globalClass.contentResolver

        return try {
            val rootPath = vol.rootFile.absolutePath.trimEnd('/')
            val targetPath = normalizePath(directory.absolutePath)
            if (targetPath == rootPath) return true

            val missingAncestors = mutableListOf<File>()
            var cur: File? = File(targetPath)
            while (cur != null && cur.absolutePath.trimEnd('/') != rootPath && !cur.exists()) {
                missingAncestors.add(cur)
                cur = cur.parentFile
            }
            missingAncestors.reverse()

            for (folderToCreate in missingAncestors) {
                if (folderToCreate.exists() && folderToCreate.isDirectory) continue
                val parent = folderToCreate.parentFile ?: continue
                val parentInfo = getRemovableVolumeInfo(parent) ?: continue
                val parentUri = buildDocumentUri(granted, parentInfo.relativePath)

                val createdUri = DocumentsContract.createDocument(
                    resolver,
                    parentUri,
                    DocumentsContract.Document.MIME_TYPE_DIR,
                    folderToCreate.name
                )
                if (createdUri == null && !folderToCreate.exists()) {
                    return false
                }
            }
            directory.exists() && directory.isDirectory
        } catch (e: Exception) {
            logger.logError(e)
            directory.exists() && directory.isDirectory
        }
    }

    /**
     * Creates a new empty file at [file] via SAF and returns its Document [Uri],
     * or returns the existing Document [Uri] if [file] already exists.
     */
    fun createFileUri(file: File, mimeType: String? = null, promptIfNeeded: Boolean = true): Uri? {
        val vol = getRemovableVolumeInfo(file) ?: return null
        if (promptIfNeeded && !hasSafGrant(vol)) {
            ensureSafGrantBlocking(file)
        }
        val granted = findGrantedTree(vol) ?: return null
        val resolver = globalClass.contentResolver

        return try {
            val parent = file.parentFile ?: return null
            if (!parent.exists() && !mkdirs(parent, promptIfNeeded = false)) {
                return null
            }
            if (file.exists() && file.isFile) {
                return buildDocumentUri(granted, vol.relativePath)
            }

            val parentInfo = getRemovableVolumeInfo(parent) ?: return null
            val parentUri = buildDocumentUri(granted, parentInfo.relativePath)
            val resolvedMime = resolveMimeTypeForFile(file.name, mimeType)

            var createdUri = DocumentsContract.createDocument(
                resolver,
                parentUri,
                resolvedMime,
                file.name
            ) ?: return null

            // Guard against OEM ROMs that append an extension when creating files with unknown MIME types
            if (!file.exists()) {
                val createdDocId = runCatching { DocumentsContract.getDocumentId(createdUri) }.getOrNull()
                val createdName = createdDocId?.substringAfter(':')?.substringAfterLast('/')
                if (!createdName.isNullOrEmpty() && createdName != file.name) {
                    val renamedUri = runCatching {
                        DocumentsContract.renameDocument(resolver, createdUri, file.name)
                    }.getOrNull()
                    if (renamedUri != null) {
                        createdUri = renamedUri
                    }
                }
            }

            createdUri
        } catch (e: Exception) {
            logger.logError(e)
            null
        }
    }

    /**
     * Creates a new empty file at [file] via SAF and returns `true` if successful.
     */
    fun createFile(file: File, mimeType: String? = null, promptIfNeeded: Boolean = true): Boolean {
        val uri = createFileUri(file, mimeType = mimeType, promptIfNeeded = promptIfNeeded)
        return uri != null || (file.exists() && file.isFile)
    }

    /**
     * Deletes a file or directory (recursively) on a removable volume via SAF.
     */
    fun delete(file: File, promptIfNeeded: Boolean = true): Boolean {
        if (!file.exists()) return true
        val vol = getRemovableVolumeInfo(file) ?: return false
        if (vol.relativePath.isEmpty()) return false // Never delete the root of the volume itself
        if (promptIfNeeded && !hasSafGrant(vol)) {
            ensureSafGrantBlocking(file)
        }
        val granted = findGrantedTree(vol) ?: return false
        val resolver = globalClass.contentResolver

        return try {
            val docUri = buildDocumentUri(granted, vol.relativePath)
            val deleted = DocumentsContract.deleteDocument(resolver, docUri)
            deleted || !file.exists()
        } catch (e: Exception) {
            logger.logError(e)
            !file.exists()
        }
    }

    /**
     * Renames [source] to [destination] on a removable volume via SAF.
     */
    fun rename(source: File, destination: File, promptIfNeeded: Boolean = true): Boolean {
        val srcVol = getRemovableVolumeInfo(source) ?: return false
        if (promptIfNeeded && !hasSafGrant(srcVol)) {
            ensureSafGrantBlocking(source)
        }
        val granted = findGrantedTree(srcVol) ?: return false
        val resolver = globalClass.contentResolver

        // If moving across different parent directories, delegate to move()
        if (source.parentFile?.absolutePath != destination.parentFile?.absolutePath) {
            return move(source, destination, overwrite = false, promptIfNeeded = false)
        }

        return try {
            val srcUri = buildDocumentUri(granted, srcVol.relativePath)

            // Handle case-only rename on case-insensitive filesystems (exFAT / vfat)
            if (source.name.equals(destination.name, ignoreCase = true) && source.name != destination.name) {
                val tempName = "${source.name}_tmp_${System.currentTimeMillis()}"
                val tempUri = DocumentsContract.renameDocument(resolver, srcUri, tempName)
                if (tempUri != null) {
                    val finalUri = DocumentsContract.renameDocument(resolver, tempUri, destination.name)
                    if (finalUri != null || destination.exists()) return true
                    // Rollback on failure
                    runCatching { DocumentsContract.renameDocument(resolver, tempUri, source.name) }
                }
            }

            val renamedUri = DocumentsContract.renameDocument(resolver, srcUri, destination.name)
            renamedUri != null || destination.exists()
        } catch (e: Exception) {
            logger.logError(e)
            false
        }
    }

    /**
     * Moves [source] to [destination] on the same removable volume via SAF, using
     * [DocumentsContract.moveDocument] when possible or falling back to copy + delete.
     */
    fun move(
        source: File,
        destination: File,
        overwrite: Boolean = true,
        promptIfNeeded: Boolean = true
    ): Boolean {
        val srcVol = getRemovableVolumeInfo(source) ?: return false
        val dstVol = getRemovableVolumeInfo(destination) ?: return false
        if (!srcVol.volumeId.equals(dstVol.volumeId, ignoreCase = true)) return false

        if (promptIfNeeded && !hasSafGrant(srcVol)) {
            ensureSafGrantBlocking(source)
        }
        val granted = findGrantedTree(srcVol) ?: return false
        val resolver = globalClass.contentResolver

        try {
            if (destination.exists()) {
                if (!overwrite) return false
                if (!delete(destination, promptIfNeeded = false)) return false
            }

            val dstParent = destination.parentFile ?: return false
            if (!dstParent.exists() && !mkdirs(dstParent, promptIfNeeded = false)) {
                return false
            }

            val srcParent = source.parentFile ?: return false
            if (srcParent.absolutePath == dstParent.absolutePath) {
                return rename(source, destination, promptIfNeeded = false)
            }

            val srcUri = buildDocumentUri(granted, srcVol.relativePath)
            val srcParentVol = getRemovableVolumeInfo(srcParent) ?: return false
            val dstParentVol = getRemovableVolumeInfo(dstParent) ?: return false
            val srcParentUri = buildDocumentUri(granted, srcParentVol.relativePath)
            val dstParentUri = buildDocumentUri(granted, dstParentVol.relativePath)

            if (source.name == destination.name) {
                val movedUri = runCatching {
                    DocumentsContract.moveDocument(resolver, srcUri, srcParentUri, dstParentUri)
                }.getOrNull()
                if (movedUri != null || (destination.exists() && !source.exists())) {
                    return true
                }
            } else {
                // Move to destination parent first, then rename to target name
                val tempInDest = File(dstParent, source.name)
                if (!tempInDest.exists()) {
                    val movedUri = runCatching {
                        DocumentsContract.moveDocument(resolver, srcUri, srcParentUri, dstParentUri)
                    }.getOrNull()
                    if (movedUri != null) {
                        val renamedUri = runCatching {
                            DocumentsContract.renameDocument(resolver, movedUri, destination.name)
                        }.getOrNull()
                        if (renamedUri != null || destination.exists()) {
                            return true
                        }
                    }
                }
            }
        } catch (e: Exception) {
            logger.logError(e)
        }

        // Fallback: copy then delete source
        if (source.isDirectory) {
            if (copyDirectoryFromLocal(source, destination, overwrite = overwrite, promptIfNeeded = false)) {
                return delete(source, promptIfNeeded = false)
            }
        } else {
            if (copyFromLocal(source, destination, overwrite = overwrite, promptIfNeeded = false)) {
                return delete(source, promptIfNeeded = false)
            }
        }
        return false
    }

    /**
     * Opens an [OutputStream] to write to [file] on a removable volume via SAF.
     */
    fun openOutputStream(
        file: File,
        append: Boolean = false,
        promptIfNeeded: Boolean = true
    ): OutputStream? {
        val vol = getRemovableVolumeInfo(file) ?: return null
        if (promptIfNeeded && !hasSafGrant(vol)) {
            ensureSafGrantBlocking(file)
        }
        val granted = findGrantedTree(vol) ?: return null
        val resolver = globalClass.contentResolver

        return try {
            val existedBefore = file.exists() && file.isFile
            val docUri = if (existedBefore) {
                buildDocumentUri(granted, vol.relativePath)
            } else {
                createFileUri(file, promptIfNeeded = false) ?: return null
            }

            if (append) {
                resolver.openOutputStream(docUri, "wa")
                    ?: resolver.openOutputStream(docUri)
            } else if (existedBefore) {
                // Truncate existing file when overwriting
                runCatching { resolver.openOutputStream(docUri, "wt") }.getOrNull()
                    ?: runCatching { resolver.openOutputStream(docUri, "rwt") }.getOrNull()
                    ?: run {
                        // Fallback if provider rejects "wt"/"rwt": recreate document cleanly
                        if (delete(file, promptIfNeeded = false)) {
                            val recreated = createFileUri(file, promptIfNeeded = false)
                            if (recreated != null) resolver.openOutputStream(recreated, "w") else null
                        } else {
                            resolver.openOutputStream(docUri, "w")
                        }
                    }
            } else {
                resolver.openOutputStream(docUri, "w")
                    ?: resolver.openOutputStream(docUri)
            }
        } catch (e: Exception) {
            logger.logError(e)
            null
        }
    }

    /**
     * Opens an [OutputStream] for [file], trying standard [FileOutputStream] first when directly
     * writable, and falling back to SAF [openOutputStream] on removable volumes.
     */
    fun openFileOutputStream(
        file: File,
        overwrite: Boolean = true,
        append: Boolean = false,
        promptIfNeeded: Boolean = true
    ): OutputStream? {
        if (!requiresSafForWrite(file)) {
            try {
                if (file.exists() && !overwrite && !append) {
                    return null
                }
                file.parentFile?.mkdirs()
                return FileOutputStream(file, append)
            } catch (e: Exception) {
                if (!isOnRemovableVolume(file)) {
                    logger.logError(e)
                    return null
                }
            }
        }

        if (file.exists() && !overwrite && !append) {
            return null
        }

        return openOutputStream(file, append = append, promptIfNeeded = promptIfNeeded)
    }

    fun writeBytes(file: File, bytes: ByteArray, promptIfNeeded: Boolean = true): Boolean {
        return try {
            openOutputStream(file, append = false, promptIfNeeded = promptIfNeeded)?.use { out ->
                out.write(bytes)
                out.flush()
            } != null
        } catch (e: Exception) {
            logger.logError(e)
            false
        }
    }

    fun writeText(file: File, text: String, promptIfNeeded: Boolean = true): Boolean {
        return writeBytes(file, text.toByteArray(Charsets.UTF_8), promptIfNeeded = promptIfNeeded)
    }

    /**
     * Copies a readable file [source] to [destination] on a removable volume via SAF.
     */
    fun copyFromLocal(
        source: File,
        destination: File,
        overwrite: Boolean = true,
        promptIfNeeded: Boolean = true,
        isAborted: (() -> Boolean)? = null,
        onBytesCopied: ((Long) -> Unit)? = null
    ): Boolean {
        if (!source.exists() || !source.isFile) return false
        val isSame = runCatching { source.canonicalPath == destination.canonicalPath }
            .getOrDefault(source.absolutePath == destination.absolutePath)
        if (isSame) {
            if (overwrite) {
                onBytesCopied?.invoke(source.length())
                return true
            }
            return false
        }

        if (destination.exists() && !overwrite) return false

        val outStream = openOutputStream(destination, append = false, promptIfNeeded = promptIfNeeded)
            ?: return false

        val buffer = ByteArray(65536)
        var streamBytes = 0L
        val fileSize = source.length()
        var lastReported = 0L

        return try {
            source.inputStream().buffered(65536).use { input ->
                outStream.buffered(65536).use { output ->
                    while (true) {
                        if (isAborted?.invoke() == true) {
                            runCatching { delete(destination, promptIfNeeded = false) }
                            return false
                        }
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        streamBytes += read

                        if (onBytesCopied != null &&
                            (streamBytes - lastReported >= 131072L || streamBytes == fileSize)
                        ) {
                            onBytesCopied(streamBytes - lastReported)
                            lastReported = streamBytes
                        }
                    }
                    output.flush()
                }
            }
            if (onBytesCopied != null && streamBytes > lastReported) {
                onBytesCopied(streamBytes - lastReported)
            }
            true
        } catch (e: Exception) {
            logger.logError(e)
            runCatching { delete(destination, promptIfNeeded = false) }
            false
        }
    }

    /**
     * Recursively copies a local staging directory [sourceDir] into [destDir] on a removable volume via SAF.
     */
    fun copyDirectoryFromLocal(
        sourceDir: File,
        destDir: File,
        overwrite: Boolean = true,
        promptIfNeeded: Boolean = true,
        isAborted: (() -> Boolean)? = null,
        onFileCopied: ((String) -> Unit)? = null
    ): Boolean {
        if (!sourceDir.exists() || !sourceDir.isDirectory) return false
        if (!mkdirs(destDir, promptIfNeeded = promptIfNeeded)) return false

        val children = sourceDir.listFiles() ?: return true
        for (child in children) {
            if (isAborted?.invoke() == true) return false
            val targetChild = File(destDir, child.name)
            if (child.isDirectory) {
                if (!copyDirectoryFromLocal(
                        sourceDir = child,
                        destDir = targetChild,
                        overwrite = overwrite,
                        promptIfNeeded = false,
                        isAborted = isAborted,
                        onFileCopied = onFileCopied
                    )
                ) {
                    return false
                }
            } else if (child.isFile) {
                onFileCopied?.invoke(child.name)
                if (!copyFromLocal(
                        source = child,
                        destination = targetChild,
                        overwrite = overwrite,
                        promptIfNeeded = false,
                        isAborted = isAborted
                    )
                ) {
                    return false
                }
            }
        }
        return true
    }

    private fun findNearestExistingFile(file: File): File? {
        var cur: File? = file
        while (cur != null && !cur.exists()) {
            cur = cur.parentFile
        }
        return cur
    }
}
