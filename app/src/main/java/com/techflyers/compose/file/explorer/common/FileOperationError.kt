package com.techflyers.compose.file.explorer.common

import android.os.StatFs
import android.system.ErrnoException
import android.system.OsConstants
import java.io.File
import java.nio.file.AccessDeniedException
import java.nio.file.FileAlreadyExistsException
import java.nio.file.FileSystemException
import java.nio.file.NoSuchFileException

/**
 * Diagnoses the underlying filesystem/OS reason why a file or folder operation failed
 * (such as no free space left on device, read-only storage, permission denied, invalid characters,
 * name too long, or item already existing) and formats actionable user-facing error messages.
 */
object FileOperationError {
    private const val MIN_FREE_SPACE_THRESHOLD_BYTES = 4096L

    fun formatMessage(baseMessage: String, reason: String?): String {
        val cleanReason = reason?.trim().orEmpty()
        if (cleanReason.isEmpty()) return baseMessage
        if (baseMessage.contains(cleanReason, ignoreCase = true)) return baseMessage
        return "$baseMessage: $cleanReason"
    }

    fun diagnosePathError(
        targetFile: File?,
        isFolder: Boolean = false,
        requiredBytes: Long = 0L,
        throwable: Throwable? = null
    ): String {
        // 1. Check any OS/filesystem exception cause chain first
        diagnoseThrowable(throwable, isFolder)?.let { return it }

        // 2. Check free storage space via StatFs on the nearest existing ancestor directory
        if (targetFile != null) {
            val existingDir = findNearestExistingDir(targetFile)
            if (existingDir != null) {
                try {
                    val statFs = StatFs(existingDir.absolutePath)
                    val availableBytes = statFs.availableBytes
                    val minNeeded = if (requiredBytes > 0L) requiredBytes else MIN_FREE_SPACE_THRESHOLD_BYTES
                    if (availableBytes <= 0L || availableBytes < minNeeded) {
                        return if (requiredBytes > MIN_FREE_SPACE_THRESHOLD_BYTES && availableBytes > 0L) {
                            "Not enough free storage space (${requiredBytes.toFormattedSize()} required, ${availableBytes.toFormattedSize()} available)"
                        } else {
                            "No free storage space left on device"
                        }
                    }
                } catch (_: Exception) {
                }
            }

            val name = targetFile.name
            if (name.toByteArray(Charsets.UTF_8).size > 255) {
                return "Name is too long (maximum 255 bytes)"
            }

            val invalidChars = charArrayOf('/', '\u0000', ':', '*', '?', '"', '<', '>', '|', '\\')
            val foundInvalid = name.filter { it in invalidChars }.toSet()
            if (foundInvalid.isNotEmpty()) {
                return "Name contains unsupported characters (${foundInvalid.joinToString(" ")})"
            }

            if (targetFile.exists()) {
                return if (targetFile.isDirectory) {
                    "A folder with this name already exists"
                } else {
                    "A file with this name already exists"
                }
            }

            val parent = targetFile.parentFile
            if (parent != null && !parent.exists()) {
                return "Parent folder does not exist"
            }

            if (existingDir != null && isMountPointReadOnly(existingDir.absolutePath)) {
                return "Storage is read-only"
            }

            if (parent != null && parent.exists() && !parent.canWrite()) {
                return "Permission denied (cannot write to this folder)"
            }
        }

        val rawMsg = throwable?.message?.trim().orEmpty()
        if (rawMsg.isNotEmpty()) {
            return rawMsg
        }

        return "Permission denied or storage is unavailable"
    }

    private fun diagnoseThrowable(throwable: Throwable?, isFolder: Boolean): String? {
        var current = throwable
        var depth = 0
        while (current != null && depth < 8) {
            if (current is ErrnoException) {
                when (current.errno) {
                    OsConstants.ENOSPC, OsConstants.EDQUOT ->
                        return "No free storage space left on device"
                    OsConstants.EROFS ->
                        return "Storage is read-only"
                    OsConstants.EACCES, OsConstants.EPERM ->
                        return "Permission denied (restricted or protected location)"
                    OsConstants.EEXIST ->
                        return if (isFolder) "A folder with this name already exists" else "A file with this name already exists"
                    OsConstants.ENAMETOOLONG ->
                        return "Name is too long (maximum 255 bytes)"
                    OsConstants.ENOENT ->
                        return "Parent folder does not exist"
                    OsConstants.ENOTDIR ->
                        return "Parent path is not a folder"
                    OsConstants.EINVAL ->
                        return "Invalid name or unsupported characters for this storage format"
                    OsConstants.EBUSY, OsConstants.ETXTBSY ->
                        return "Item is currently in use by another process"
                    OsConstants.EIO ->
                        return "Storage I/O error"
                }
            }
            when (current) {
                is FileAlreadyExistsException ->
                    return if (isFolder) "A folder with this name already exists" else "A file with this name already exists"
                is AccessDeniedException ->
                    return "Permission denied (restricted or protected location)"
                is NoSuchFileException ->
                    return "Target or parent folder does not exist"
                is FileSystemException -> {
                    val reason = current.reason?.lowercase().orEmpty()
                    when {
                        reason.contains("no space left") || reason.contains("quota") ->
                            return "No free storage space left on device"
                        reason.contains("read-only") ->
                            return "Storage is read-only"
                        reason.contains("permission denied") || reason.contains("operation not permitted") ->
                            return "Permission denied (restricted or protected location)"
                        reason.contains("file name too long") ->
                            return "Name is too long (maximum 255 bytes)"
                        reason.contains("invalid argument") ->
                            return "Invalid name or unsupported characters for this storage format"
                    }
                }
            }

            val msg = current.message?.lowercase().orEmpty()
            when {
                msg.contains("no space left") || msg.contains("enospc") || msg.contains("edquot") || msg.contains("not enough space") || msg.contains("disk full") ->
                    return "No free storage space left on device"
                msg.contains("read-only file system") || msg.contains("erofs") ->
                    return "Storage is read-only"
                msg.contains("permission denied") || msg.contains("eacces") || msg.contains("eperm") || msg.contains("operation not permitted") ->
                    return "Permission denied (restricted or protected location)"
                msg.contains("file exists") || msg.contains("eexist") ->
                    return if (isFolder) "A folder with this name already exists" else "A file with this name already exists"
                msg.contains("file name too long") || msg.contains("enametoolong") ->
                    return "Name is too long (maximum 255 bytes)"
                msg.contains("invalid argument") || msg.contains("einval") ->
                    return "Invalid name or unsupported characters for this storage format"
                msg.contains("device or resource busy") || msg.contains("ebusy") ->
                    return "Item is currently in use by another process"
                msg.contains("i/o error") || msg.contains("input/output error") ->
                    return "Storage I/O error"
            }

            current = current.cause
            depth++
        }
        return null
    }

    private fun findNearestExistingDir(file: File): File? {
        var cur: File? = if (file.exists() && file.isDirectory) file else file.parentFile
        while (cur != null && !cur.exists()) {
            cur = cur.parentFile
        }
        return cur
    }

    private fun isMountPointReadOnly(path: String): Boolean {
        return try {
            val mounts = File("/proc/mounts")
            if (!mounts.exists()) return false
            var bestMount = ""
            var isRo = false
            mounts.forEachLine { line ->
                val parts = line.split(' ')
                if (parts.size >= 4) {
                    val mountPoint = parts[1]
                    val options = parts[3].split(',')
                    if (path.startsWith(mountPoint) && mountPoint.length >= bestMount.length) {
                        bestMount = mountPoint
                        isRo = options.contains("ro")
                    }
                }
            }
            isRo
        } catch (_: Exception) {
            false
        }
    }
}
