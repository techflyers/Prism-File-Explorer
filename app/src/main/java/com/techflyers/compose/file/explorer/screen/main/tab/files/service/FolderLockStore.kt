package com.techflyers.compose.file.explorer.screen.main.tab.files.service

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.techflyers.compose.file.explorer.common.toFormattedSize
import java.io.File
import java.io.RandomAccessFile
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

enum class FolderLockType { PIN, PATTERN }

data class FolderLockEntry(
    val path: String,
    val salt: String,
    val hash: String,
    val type: FolderLockType,
    val isEncrypted: Boolean = false,
    val autoEncryptedBySize: Boolean = false,
    val folderSizeBytes: Long = 0L,
    val encryptedFileCount: Int = 0
)

object FolderLockStore {
    private const val PREFS = "folder_lock_prefs"
    private const val KEY = "locks"
    const val AUTO_ENCRYPT_THRESHOLD_BYTES = 100L * 1024L * 1024L // 100 MB
    private const val ENCRYPT_HEADER_MAGIC = "PRISM_LOCK_ENC_V1!" // 18 bytes
    private const val ENCRYPT_BLOCK_SIZE = 65536 // 64 KB fast in-place AES-256-CTR block
    private const val CREATED_NOMEDIA_MARKER = ".prism_lock_nomedia"

    private val gson = Gson()
    private val sessionUnlocked = ConcurrentHashMap.newKeySet<String>()
    private val sessionSecrets = ConcurrentHashMap<String, String>()

    fun isLocked(path: String): Boolean {
        val entry = matchingLock(path) ?: return false
        return !sessionUnlocked.contains(entry.path)
    }

    fun hasLock(path: String): Boolean = matchingLock(path) != null

    fun isFolderEncrypted(path: String): Boolean = matchingLock(path)?.isEncrypted == true

    fun matchingLock(path: String): FolderLockEntry? {
        val normalized = path.trimEnd('/')
        return load().firstOrNull { lock ->
            val lockNorm = lock.path.trimEnd('/')
            normalized == lockNorm || normalized.startsWith("$lockNorm/")
        }
    }

    fun calculateFolderSize(path: String): Long {
        val dir = File(path)
        if (!dir.exists() || !dir.isDirectory) return 0L
        var total = 0L
        runCatching {
            dir.walkTopDown().maxDepth(12).onFail { _, _ -> }.forEach { f ->
                if (f.isFile && f.name != ".nomedia" && f.name != CREATED_NOMEDIA_MARKER) {
                    total += f.length()
                }
            }
        }
        return total
    }

    fun shouldAutoEncrypt(folderSizeBytes: Long): Boolean {
        val prefs = com.techflyers.compose.file.explorer.App.globalClass.preferencesManager
        return prefs.autoEncryptLargeFolders && folderSizeBytes >= AUTO_ENCRYPT_THRESHOLD_BYTES
    }

    fun lock(
        path: String,
        secret: String,
        type: FolderLockType,
        encryptContents: Boolean? = null,
        precomputedSizeBytes: Long? = null
    ): FolderLockEntry {
        val normalized = path.trimEnd('/')
        val salt = System.currentTimeMillis().toString()
        val prefs = com.techflyers.compose.file.explorer.App.globalClass.preferencesManager
        val folderSize = precomputedSizeBytes ?: calculateFolderSize(normalized)
        val autoEncrypted = prefs.autoEncryptLargeFolders && folderSize >= AUTO_ENCRYPT_THRESHOLD_BYTES
        val shouldEncrypt = encryptContents ?: (prefs.encryptLockedFolders || autoEncrypted)

        var encryptedCount = 0
        if (shouldEncrypt) {
            encryptedCount = encryptDirectoryContents(File(normalized), secret, salt)
        }

        val entry = FolderLockEntry(
            path = normalized,
            salt = salt,
            hash = hash(secret, salt),
            type = type,
            isEncrypted = shouldEncrypt,
            autoEncryptedBySize = autoEncrypted && shouldEncrypt,
            folderSizeBytes = folderSize,
            encryptedFileCount = encryptedCount
        )
        val next = load().filter { it.path.trimEnd('/') != normalized } + entry
        save(next)
        sessionUnlocked.remove(normalized)
        sessionSecrets.remove(normalized)
        return entry
    }

    fun unlock(path: String, secret: String): Boolean {
        val entry = matchingLock(path) ?: return false
        if (entry.hash != hash(secret, entry.salt)) return false
        if (entry.isEncrypted) {
            decryptDirectoryContents(File(entry.path), secret, entry.salt)
        }
        sessionUnlocked.add(entry.path)
        sessionSecrets[entry.path] = secret
        return true
    }

    fun relockSession(path: String): Boolean {
        val entry = matchingLock(path) ?: return false
        val secret = sessionSecrets[entry.path]
        if (entry.isEncrypted && secret != null) {
            encryptDirectoryContents(File(entry.path), secret, entry.salt)
        }
        sessionUnlocked.remove(entry.path)
        return true
    }

    fun verifyAnyLock(secret: String): Boolean {
        val locks = load()
        if (locks.isEmpty()) return true
        return locks.any { it.hash == hash(secret, it.salt) }
    }

    fun remove(path: String, secret: String? = null) {
        val normalized = path.trimEnd('/')
        val existing = matchingLock(normalized)
        if (existing != null && existing.isEncrypted) {
            val effectiveSecret = secret ?: sessionSecrets[existing.path]
            if (effectiveSecret != null) {
                decryptDirectoryContents(File(existing.path), effectiveSecret, existing.salt)
            }
        }
        save(load().filter { it.path.trimEnd('/') != normalized })
        sessionUnlocked.remove(normalized)
        sessionSecrets.remove(normalized)
    }

    fun reset(secret: String? = null) {
        val locks = load()
        for (entry in locks) {
            if (entry.isEncrypted) {
                val effectiveSecret = secret?.takeIf { entry.hash == hash(it, entry.salt) }
                    ?: sessionSecrets[entry.path]
                if (effectiveSecret != null) {
                    decryptDirectoryContents(File(entry.path), effectiveSecret, entry.salt)
                }
            }
        }
        save(emptyList())
        sessionUnlocked.clear()
        sessionSecrets.clear()
    }

    fun hasAnyLocks(): Boolean = load().isNotEmpty()

    fun getStatusSummary(): String {
        val locks = load()
        if (locks.isEmpty()) return "No folders are currently locked"
        val encCount = locks.count { it.isEncrypted }
        val unlockedCount = locks.count { sessionUnlocked.contains(it.path) }
        return "${locks.size} locked folder(s) • $encCount AES-256 encrypted • $unlockedCount unlocked in session"
    }

    fun getFolderSecurityBadgeText(path: String): String? {
        val entry = matchingLock(path) ?: return null
        val isCurrentlyUnlocked = sessionUnlocked.contains(entry.path)
        return when {
            entry.isEncrypted && isCurrentlyUnlocked -> "🔓 Unlocked (AES-256 Encrypted)"
            entry.isEncrypted && !isCurrentlyUnlocked -> "🔒 Encrypted & Locked"
            !entry.isEncrypted && isCurrentlyUnlocked -> "🔓 Unlocked"
            else -> "🔒 Locked"
        }
    }

    fun getDetailedFolderSecurityStatus(path: String): String {
        val entry = matchingLock(path) ?: return "Unlocked (Standard folder access)"
        val isCurrentlyUnlocked = sessionUnlocked.contains(entry.path)
        val accessPart = if (isCurrentlyUnlocked) {
            "Unlocked for current session"
        } else {
            "Locked (${entry.type.name} required)"
        }
        val encPart = if (entry.isEncrypted) {
            val autoNote = if (entry.autoEncryptedBySize) " [Auto-encrypted ≥ 100 MB]" else ""
            "AES-256 Encrypted — Protected from external apps$autoNote"
        } else {
            "App-Lock Only (Not encrypted)"
        }
        val sizePart = if (entry.folderSizeBytes > 0L) " • ${entry.folderSizeBytes.toFormattedSize()}" else ""
        return "$accessPart • $encPart$sizePart"
    }

    private fun deriveAesKeyAndIv(secret: String, salt: String): Pair<SecretKeySpec, IvParameterSpec> {
        val keyBytes = MessageDigest.getInstance("SHA-256")
            .digest(("PRISM_AES_KEY:$secret:$salt").toByteArray(Charsets.UTF_8))
        val ivBytes = MessageDigest.getInstance("MD5")
            .digest(("PRISM_AES_IV:$salt:$secret").toByteArray(Charsets.UTF_8))
        return SecretKeySpec(keyBytes, "AES") to IvParameterSpec(ivBytes)
    }

    private fun encryptDirectoryContents(directory: File, secret: String, salt: String): Int {
        if (!directory.exists() || !directory.isDirectory) return 0
        var count = 0
        val (keySpec, ivSpec) = deriveAesKeyAndIv(secret, salt)
        val magicBytes = ENCRYPT_HEADER_MAGIC.toByteArray(Charsets.UTF_8)

        runCatching {
            val nomedia = File(directory, ".nomedia")
            val marker = File(directory, CREATED_NOMEDIA_MARKER)
            if (!nomedia.exists()) {
                if (nomedia.createNewFile()) {
                    marker.createNewFile()
                }
            }
        }

        runCatching {
            directory.walkTopDown().maxDepth(12).onFail { _, _ -> }.forEach { file ->
                if (file.isFile &&
                    file.canRead() &&
                    file.canWrite() &&
                    file.name != ".nomedia" &&
                    file.name != CREATED_NOMEDIA_MARKER &&
                    file.length() > 0L
                ) {
                    if (encryptSingleFile(file, keySpec, ivSpec, magicBytes)) {
                        count++
                    }
                }
            }
        }
        return count
    }

    private fun decryptDirectoryContents(directory: File, secret: String, salt: String): Int {
        if (!directory.exists() || !directory.isDirectory) return 0
        var count = 0
        val (keySpec, ivSpec) = deriveAesKeyAndIv(secret, salt)
        val magicBytes = ENCRYPT_HEADER_MAGIC.toByteArray(Charsets.UTF_8)

        runCatching {
            val nomedia = File(directory, ".nomedia")
            val marker = File(directory, CREATED_NOMEDIA_MARKER)
            if (marker.exists()) {
                nomedia.delete()
                marker.delete()
            }
        }

        runCatching {
            directory.walkTopDown().maxDepth(12).onFail { _, _ -> }.forEach { file ->
                if (file.isFile &&
                    file.canRead() &&
                    file.canWrite() &&
                    file.name != ".nomedia" &&
                    file.name != CREATED_NOMEDIA_MARKER &&
                    file.length() >= magicBytes.size
                ) {
                    if (decryptSingleFile(file, keySpec, ivSpec, magicBytes)) {
                        count++
                    }
                }
            }
        }
        return count
    }

    private fun encryptSingleFile(
        file: File,
        keySpec: SecretKeySpec,
        ivSpec: IvParameterSpec,
        magicBytes: ByteArray
    ): Boolean {
        return try {
            val originalMtime = file.lastModified()
            RandomAccessFile(file, "rw").use { raf ->
                val len = raf.length()
                if (len <= 0L) return false
                // Check if already encrypted
                if (len >= magicBytes.size) {
                    val checkBuf = ByteArray(magicBytes.size)
                    raf.seek(0)
                    raf.readFully(checkBuf)
                    if (checkBuf.contentEquals(magicBytes)) {
                        return true
                    }
                }
                val blockLen = minOf(len, ENCRYPT_BLOCK_SIZE.toLong()).toInt()
                val plainBlock = ByteArray(blockLen)
                raf.seek(0)
                raf.readFully(plainBlock)

                val cipher = Cipher.getInstance("AES/CTR/NoPadding")
                cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec)
                val encryptedBlock = cipher.doFinal(plainBlock)

                // Store displaced tail (first magicBytes.size bytes of encryptedBlock are replaced by magicBytes at offset 0,
                // and those displaced encrypted bytes are appended at the end of the file so no external app can parse the header)
                val displacedLen = minOf(magicBytes.size, encryptedBlock.size)
                val displacedBytes = encryptedBlock.copyOfRange(0, displacedLen)
                System.arraycopy(magicBytes, 0, encryptedBlock, 0, displacedLen)

                raf.seek(0)
                raf.write(encryptedBlock)
                raf.seek(len)
                raf.write(displacedBytes)
            }
            file.setLastModified(originalMtime)
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun decryptSingleFile(
        file: File,
        keySpec: SecretKeySpec,
        ivSpec: IvParameterSpec,
        magicBytes: ByteArray
    ): Boolean {
        return try {
            val originalMtime = file.lastModified()
            RandomAccessFile(file, "rw").use { raf ->
                val totalLen = raf.length()
                if (totalLen <= magicBytes.size) return false

                val checkLen = minOf(magicBytes.size.toLong(), totalLen / 2).toInt()
                val headerCheck = ByteArray(checkLen)
                raf.seek(0)
                raf.readFully(headerCheck)
                for (i in 0 until checkLen) {
                    if (headerCheck[i] != magicBytes[i]) return false
                }

                val origLen = totalLen - checkLen
                val blockLen = minOf(origLen, ENCRYPT_BLOCK_SIZE.toLong()).toInt()
                val encryptedBlock = ByteArray(blockLen)
                raf.seek(0)
                raf.readFully(encryptedBlock)

                val displacedBytes = ByteArray(checkLen)
                raf.seek(origLen)
                raf.readFully(displacedBytes)
                System.arraycopy(displacedBytes, 0, encryptedBlock, 0, checkLen)

                val cipher = Cipher.getInstance("AES/CTR/NoPadding")
                cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec)
                val plainBlock = cipher.doFinal(encryptedBlock)

                raf.seek(0)
                raf.write(plainBlock)
                raf.setLength(origLen)
            }
            file.setLastModified(originalMtime)
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun load(): List<FolderLockEntry> {
        val json = globalPrefs().getString(KEY, "[]") ?: "[]"
        return try {
            val type = object : TypeToken<List<FolderLockEntry>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun save(entries: List<FolderLockEntry>) {
        globalPrefs().edit().putString(KEY, gson.toJson(entries)).apply()
    }

    private fun globalPrefs() =
        com.techflyers.compose.file.explorer.App.globalClass
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun hash(secret: String, salt: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest((secret + salt).toByteArray()).joinToString("") { "%02x".format(it) }
    }
}
