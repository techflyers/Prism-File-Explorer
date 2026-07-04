package com.raival.compose.file.explorer.screen.main.tab.files.service

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.*
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.*
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

data class VaultFileRecord(
    val id: String,
    val originalName: String,
    val originalPath: String,
    val scrambledPath: String,
    val size: Long,
    val lockedAt: String,
    val isInPlace: Boolean,
    val isFolder: Boolean
)

object VaultService {
    private const val MAGIC_TAG = "NFILE_VAULT_V1"
    private const val SCRAMBLE_SIZE = 8192 // 8 KB

    fun isPasswordSet(context: Context): Boolean {
        val prefs = context.getSharedPreferences("vault_prefs", Context.MODE_PRIVATE)
        return prefs.contains("vault_password_hash")
    }

    fun setPassword(context: Context, pin: String) {
        val prefs = context.getSharedPreferences("vault_prefs", Context.MODE_PRIVATE)
        val salt = System.currentTimeMillis().toString()
        val saltedPassword = pin + salt
        val hash = sha256(saltedPassword)
        prefs.edit()
            .putString("vault_salt", salt)
            .putString("vault_password_hash", hash)
            .apply()
    }

    fun verifyPassword(context: Context, pin: String): Boolean {
        val prefs = context.getSharedPreferences("vault_prefs", Context.MODE_PRIVATE)
        val salt = prefs.getString("vault_salt", null) ?: return false
        val hash = prefs.getString("vault_password_hash", null) ?: return false
        val checkHash = sha256(pin + salt)
        return hash == checkHash
    }

    fun getVaultDir(context: Context): File {
        val vaultDir = File(context.filesDir, "vault")
        if (!vaultDir.exists()) {
            vaultDir.mkdirs()
        }
        return vaultDir
    }

    private fun getMetadataFile(context: Context): File {
        return File(getVaultDir(context), "metadata.json")
    }

    fun loadRecords(context: Context): List<VaultFileRecord> {
        val file = getMetadataFile(context)
        if (!file.exists()) return emptyList()
        return try {
            val json = file.readText(Charsets.UTF_8)
            val type = object : TypeToken<List<VaultFileRecord>>() {}.type
            Gson().fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveRecords(context: Context, records: List<VaultFileRecord>) {
        val file = getMetadataFile(context)
        try {
            val json = Gson().toJson(records)
            file.writeText(json, Charsets.UTF_8)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun sha256Bytes(input: String): ByteArray {
        val md = MessageDigest.getInstance("SHA-256")
        return md.digest(input.toByteArray(Charsets.UTF_8))
    }

    private fun sha256(input: String): String {
        val digest = sha256Bytes(input)
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun deriveKey(password: String, length: Int): ByteArray {
        val hash = sha256Bytes(password)
        val key = ByteArray(length)
        for (i in 0 until length) {
            val h = hash[i % hash.size].toInt() and 0xFF
            key[i] = (h xor (i and 0xFF)).toByte()
        }
        return key
    }

    private fun xorBytes(bytes: ByteArray, key: ByteArray): ByteArray {
        val result = ByteArray(bytes.size)
        for (i in bytes.indices) {
            result[i] = (bytes[i].toInt() xor key[i % key.size].toInt()).toByte()
        }
        return result
    }

    fun lockFile(
        context: Context,
        file: File,
        password: CharSequence,
        inPlace: Boolean,
        customName: String? = null,
        customPath: String? = null,
        isFolder: Boolean = false
    ): VaultFileRecord {
        if (!file.exists()) {
            throw FileNotFoundException("File does not exist: ${file.absolutePath}")
        }

        val originalPath = customPath ?: file.absolutePath
        val originalName = customName ?: file.name
        val size = file.length()
        val timestamp = System.currentTimeMillis().toString()

        val scrambledPath = if (inPlace) {
            val parent = if (isFolder) originalPath else file.parent ?: ""
            File(parent, ".vault_$timestamp.nfv").absolutePath
        } else {
            File(getVaultDir(context), "vault_$timestamp.nfv").absolutePath
        }

        val fileBytes = file.readBytes()
        val scrambleLen = minOf(SCRAMBLE_SIZE, fileBytes.size)
        val scrambleBytes = fileBytes.copyOfRange(0, scrambleLen)
        val restBytes = fileBytes.copyOfRange(scrambleLen, fileBytes.size)

        val key = deriveKey(password.toString(), scrambleLen)
        val obfuscatedBytes = xorBytes(scrambleBytes, key)

        // Build metadata payload
        val metadata = mapOf(
            "name" to originalName,
            "path" to originalPath,
            "size" to size,
            "timestamp" to timestamp,
            "isFolder" to isFolder
        )
        val metadataStr = Gson().toJson(metadata)
        val metadataBytes = metadataStr.toByteArray(Charsets.UTF_8)

        val metaKey = deriveKey(password.toString(), metadataBytes.size)
        val obfuscatedMetadata = xorBytes(metadataBytes, metaKey)

        val targetFile = File(scrambledPath)
        targetFile.outputStream().use { out ->
            out.write(MAGIC_TAG.toByteArray(Charsets.UTF_8)) // 14 bytes
            val metaLen = obfuscatedMetadata.size
            out.write(byteArrayOf(
                ((metaLen shr 24) and 0xFF).toByte(),
                ((metaLen shr 16) and 0xFF).toByte(),
                ((metaLen shr 8) and 0xFF).toByte(),
                (metaLen and 0xFF).toByte()
            ))
            out.write(obfuscatedMetadata)
            out.write(obfuscatedBytes)
            out.write(restBytes)
        }

        // Delete original file
        file.delete()

        val record = VaultFileRecord(
            id = timestamp,
            originalName = originalName,
            originalPath = originalPath,
            scrambledPath = scrambledPath,
            size = size,
            lockedAt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS", Locale.US).format(Date()),
            isInPlace = inPlace,
            isFolder = isFolder
        )

        val records = loadRecords(context).toMutableList()
        records.add(record)
        saveRecords(context, records)

        return record
    }

    fun unlockFile(
        context: Context,
        record: VaultFileRecord,
        password: CharSequence
    ): File {
        val scrambledFile = File(record.scrambledPath)
        if (!scrambledFile.exists()) {
            throw FileNotFoundException("Scrambled vault file not found: ${record.scrambledPath}")
        }

        val bytes = scrambledFile.readBytes()
        if (bytes.size < MAGIC_TAG.length + 4) {
            throw Exception("Invalid vault file format (Too short)")
        }

        val magicBytes = bytes.copyOfRange(0, MAGIC_TAG.length)
        val magic = String(magicBytes, Charsets.UTF_8)
        if (magic != MAGIC_TAG) {
            throw Exception("Invalid vault file format (Magic tag mismatch)")
        }

        val metaLen = ((bytes[14].toInt() and 0xFF) shl 24) or
                      ((bytes[15].toInt() and 0xFF) shl 16) or
                      ((bytes[16].toInt() and 0xFF) shl 8) or
                      (bytes[17].toInt() and 0xFF)

        val metaStart = MAGIC_TAG.length + 4
        val metaEnd = metaStart + metaLen
        if (bytes.size < metaEnd) {
            throw Exception("Invalid vault file format (Corrupted header)")
        }

        val obfuscatedMetadata = bytes.copyOfRange(metaStart, metaEnd)
        val metaKey = deriveKey(password.toString(), obfuscatedMetadata.size)
        val decryptedMetadataBytes = xorBytes(obfuscatedMetadata, metaKey)
        val decryptedMetadataStr = String(decryptedMetadataBytes, Charsets.UTF_8)
        val metadata = Gson().fromJson(decryptedMetadataStr, Map::class.java)

        val fileDataStart = metaEnd
        val originalSize = (metadata["size"] as Number).toLong()
        val scrambleLen = minOf(SCRAMBLE_SIZE, originalSize.toInt())

        if (bytes.size < fileDataStart + scrambleLen) {
            throw Exception("Invalid vault file format (Corrupted payload)")
        }

        val obfuscatedSignature = bytes.copyOfRange(fileDataStart, fileDataStart + scrambleLen)
        val restBytes = bytes.copyOfRange(fileDataStart + scrambleLen, bytes.size)

        val key = deriveKey(password.toString(), scrambleLen)
        val decryptedSignature = xorBytes(obfuscatedSignature, key)

        val originalFile = File(record.originalPath)
        if (record.isFolder) {
            val zipBytes = decryptedSignature + restBytes
            val destDir = originalFile.parentFile ?: File("/")
            unzip(zipBytes, destDir)
        } else {
            originalFile.parentFile?.mkdirs()
            originalFile.outputStream().use { out ->
                out.write(decryptedSignature)
                out.write(restBytes)
            }
        }

        scrambledFile.delete()

        val records = loadRecords(context).toMutableList()
        records.removeAll { it.id == record.id }
        saveRecords(context, records)

        return originalFile
    }

    fun lockDirectory(
        context: Context,
        directory: File,
        password: CharSequence,
        inPlace: Boolean
    ): VaultFileRecord {
        if (!directory.exists()) {
            throw FileNotFoundException("Directory does not exist: ${directory.absolutePath}")
        }

        val originalPath = directory.absolutePath
        val originalName = directory.name

        // Zip recursively to bytes
        val zipBytes = zipDirectoryToBytes(directory)

        // Write zip to a temporary file
        val tempZipFile = File(context.cacheDir, "temp_vault_zip_${System.currentTimeMillis()}.zip")
        tempZipFile.writeBytes(zipBytes)

        try {
            val record = lockFile(
                context = context,
                file = tempZipFile,
                password = password,
                inPlace = inPlace,
                customName = originalName,
                customPath = originalPath,
                isFolder = true
            )

            // Clean up original directory contents
            if (inPlace) {
                val children = directory.listFiles() ?: emptyArray()
                for (child in children) {
                    if (child.absolutePath != record.scrambledPath) {
                        child.deleteRecursively()
                    }
                }
            } else {
                directory.deleteRecursively()
            }

            return record
        } finally {
            if (tempZipFile.exists()) {
                tempZipFile.delete()
            }
        }
    }

    fun decryptTemporary(
        context: Context,
        record: VaultFileRecord,
        password: CharSequence
    ): File {
        val scrambledFile = File(record.scrambledPath)
        if (!scrambledFile.exists()) {
            throw FileNotFoundException("Scrambled vault file not found")
        }

        val bytes = scrambledFile.readBytes()
        val lenBytes = bytes.copyOfRange(MAGIC_TAG.length, MAGIC_TAG.length + 4)
        val metaLen = ((lenBytes[0].toInt() and 0xFF) shl 24) or
                      ((lenBytes[1].toInt() and 0xFF) shl 16) or
                      ((lenBytes[2].toInt() and 0xFF) shl 8) or
                      (lenBytes[3].toInt() and 0xFF)

        val metaStart = MAGIC_TAG.length + 4
        val metaEnd = metaStart + metaLen
        val obfuscatedMetadata = bytes.copyOfRange(metaStart, metaEnd)
        val metaKey = deriveKey(password.toString(), obfuscatedMetadata.size)
        val decryptedMetadataBytes = xorBytes(obfuscatedMetadata, metaKey)
        val decryptedMetadataStr = String(decryptedMetadataBytes, Charsets.UTF_8)
        val metadata = Gson().fromJson(decryptedMetadataStr, Map::class.java)

        val originalSize = (metadata["size"] as Number).toLong()
        val scrambleLen = minOf(SCRAMBLE_SIZE, originalSize.toInt())

        val fileDataStart = metaEnd
        val obfuscatedSignature = bytes.copyOfRange(fileDataStart, fileDataStart + scrambleLen)
        val restBytes = bytes.copyOfRange(fileDataStart + scrambleLen, bytes.size)

        val key = deriveKey(password.toString(), scrambleLen)
        val decryptedSignature = xorBytes(obfuscatedSignature, key)

        val extension = if (record.isFolder) ".zip" else ""
        val tempFilePath = File(context.cacheDir, "temp_vault_${record.id}_${record.originalName}$extension")
        tempFilePath.outputStream().use { out ->
            out.write(decryptedSignature)
            out.write(restBytes)
        }
        return tempFilePath
    }

    private fun zipDirectoryToBytes(directory: File): ByteArray {
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            zipDir(directory, directory, zos)
        }
        return baos.toByteArray()
    }

    private fun zipDir(rootDir: File, currentDir: File, zos: ZipOutputStream) {
        val files = currentDir.listFiles() ?: return
        for (file in files) {
            if (file.isDirectory) {
                zipDir(rootDir, file, zos)
            } else {
                val parentPath = rootDir.parentFile?.absolutePath ?: ""
                val relativePath = file.absolutePath.substring(parentPath.length + 1)
                val entry = ZipEntry(relativePath)
                zos.putNextEntry(entry)
                file.inputStream().use { input ->
                    input.copyTo(zos)
                }
                zos.closeEntry()
            }
        }
    }

    private fun unzip(zipBytes: ByteArray, destDir: File) {
        ZipInputStream(ByteArrayInputStream(zipBytes)).use { zis ->
            var entry: ZipEntry? = zis.nextEntry
            while (entry != null) {
                val file = File(destDir, entry.name)
                if (entry.isDirectory) {
                    file.mkdirs()
                } else {
                    file.parentFile?.mkdirs()
                    file.outputStream().use { out ->
                        zis.copyTo(out)
                    }
                }
                entry = zis.nextEntry
            }
        }
    }
}
