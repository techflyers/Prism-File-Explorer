package com.raival.compose.file.explorer.screen.main.tab.files.zip

import com.raival.compose.file.explorer.App.Companion.globalClass
import com.raival.compose.file.explorer.common.NativeBinaryExecutor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Represents an entry parsed from the 7z listing output.
 */
data class ArchiveEntry(
    val path: String,
    val size: Long,
    val isDirectory: Boolean,
    val lastModified: Long = 0L,
    val encrypted: Boolean = false
)

/**
 * Manages listing and extraction of native archive formats (.7z, .rar, .iso, .cab, etc.)
 * using the bundled lib7za.so binary.
 */
object ArchiveManager {

    private val NATIVE_ARCHIVE_EXTENSIONS = setOf(
        "7z", "rar", "iso", "cab", "xz", "jar", "war", "ear"
    )

    /**
     * Check if a file extension is a native archive that requires the 7z binary.
     */
    fun isNativeArchive(extension: String): Boolean {
        return extension.lowercase() in NATIVE_ARCHIVE_EXTENSIONS
    }

    /**
     * Check if a file path ends with a native archive extension.
     */
    fun isNativeArchivePath(path: String): Boolean {
        val ext = path.substringAfterLast('.', "").lowercase()
        return ext in NATIVE_ARCHIVE_EXTENSIONS
    }

    /**
     * List the contents of a native archive using 7za.
     * Parses the -slt (show technical listing) output format.
     *
     * @param password Optional password for encrypted archives.
     * @return List of [ArchiveEntry] representing all files and directories
     */
    suspend fun listArchive(archivePath: String, password: String? = null): List<ArchiveEntry> = withContext(Dispatchers.IO) {
        val args = mutableListOf("l", "-slt", archivePath)
        if (!password.isNullOrEmpty()) {
            args.add("-p$password")
        } else {
            args.add("-p-") // Pass dummy password to disable interactive prompt & fail immediately
        }
        android.util.Log.d("PrismArchive", "ArchiveManager: listArchive command=7z ${args.joinToString(" ")}")
        val result = NativeBinaryExecutor.run(
            context = globalClass,
            binaryName = "lib7za.so",
            arguments = args
        )

        android.util.Log.d("PrismArchive", "ArchiveManager: listArchive exitCode=${result.exitCode}, success=${result.success}, outputLength=${result.output.length}")

        if (!result.success) {
            android.util.Log.e("PrismArchive", "ArchiveManager: listArchive failed. Output: ${result.output}")
            throw Exception("7za listing failed (exit ${result.exitCode}):\n${result.output}")
        }

        val entries = parseListOutput(result.output, archivePath)
        android.util.Log.d("PrismArchive", "ArchiveManager: listArchive parsed ${entries.size} entries.")
        entries
    }

    /**
     * Extract all contents of a native archive to the destination directory.
     *
     * @param password Optional password for encrypted archives. Passed as -p<password> to 7za.
     */
    suspend fun extractAll(archivePath: String, destinationDir: String, password: String? = null) {
        val args = mutableListOf("x", archivePath, "-o$destinationDir", "-y")
        if (!password.isNullOrEmpty()) {
            args.add("-p$password")
        } else {
            args.add("-p-")
        }
        android.util.Log.d("PrismArchive", "ArchiveManager: extractAll command=7z ${args.joinToString(" ")}")
        val result = NativeBinaryExecutor.run(
            context = globalClass,
            binaryName = "lib7za.so",
            arguments = args
        )

        android.util.Log.d("PrismArchive", "ArchiveManager: extractAll exitCode=${result.exitCode}, success=${result.success}")

        if (!result.success) {
            android.util.Log.e("PrismArchive", "ArchiveManager: extractAll failed. Output: ${result.output}")
            throw Exception("7za extraction failed (exit ${result.exitCode}):\n${result.output}")
        }
        android.util.Log.d("PrismArchive", "ArchiveManager: Extraction successful for $archivePath")
    }

    /**
     * Extract a single file from a native archive.
     *
     * @param archivePath Path to the archive file
     * @param internalPath The path of the file inside the archive to extract
     * @param destinationDir Directory to extract to
     * @param password Optional password for encrypted archives.
     */
    suspend fun extractSingleFile(
        archivePath: String,
        internalPath: String,
        destinationDir: String,
        password: String? = null
    ) {
        val args = mutableListOf("x", archivePath, "-o$destinationDir", "-y", internalPath)
        if (!password.isNullOrEmpty()) {
            args.add("-p$password")
        } else {
            args.add("-p-")
        }
        android.util.Log.d("PrismArchive", "ArchiveManager: extractSingleFile command=7z ${args.joinToString(" ")}")
        val result = NativeBinaryExecutor.run(
            context = globalClass,
            binaryName = "lib7za.so",
            arguments = args
        )

        android.util.Log.d("PrismArchive", "ArchiveManager: extractSingleFile exitCode=${result.exitCode}, success=${result.success}")

        if (!result.success) {
            android.util.Log.e("PrismArchive", "ArchiveManager: extractSingleFile failed. Output: ${result.output}")
            throw Exception("7za extract failed (exit ${result.exitCode}):\n${result.output}")
        }
    }

    /**
     * Parse the 7za -slt verbose listing output into [ArchiveEntry] objects.
     *
     * The output format looks like:
     * ```
     * ----------
     * Path = some/file.txt
     * Size = 1234
     * Modified = 2023-05-12 14:30:00
     * Folder = -
     * ...
     * ```
     *
     * @param archivePath The path of the archive itself — entries matching this path are filtered out.
     */
    private fun parseListOutput(output: String, archivePath: String): List<ArchiveEntry> {
        val entries = mutableListOf<ArchiveEntry>()
        val blocks = output.split("\n\n")
        // Derive just the filename of the archive to compare against entries
        val archiveFileName = archivePath.substringAfterLast('/')
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

        for (block in blocks) {
            var path: String? = null
            var size: Long = 0
            var isDir = false
            var lastModified = 0L
            var encrypted = false

            for (line in block.lines()) {
                val trimmed = line.trim()
                when {
                    trimmed.startsWith("Path = ") -> {
                        path = trimmed.removePrefix("Path = ")
                    }
                    trimmed.startsWith("Size = ") -> {
                        size = trimmed.removePrefix("Size = ").toLongOrNull() ?: 0
                    }
                    trimmed.startsWith("Modified = ") -> {
                        val dateStr = trimmed.removePrefix("Modified = ").trim()
                        try {
                            // 7za format: "2023-05-12 14:30:00" or "2023-05-12 14:30:00.1234567"
                            val cleanDate = dateStr.substringBefore('.') // trim nanoseconds
                            lastModified = dateFormat.parse(cleanDate)?.time ?: 0L
                        } catch (_: Exception) {
                            lastModified = 0L
                        }
                    }
                    trimmed.startsWith("Folder = ") -> {
                        isDir = isDir || (trimmed.removePrefix("Folder = ") == "+")
                    }
                    trimmed.startsWith("Attributes = ") -> {
                        val attrs = trimmed.removePrefix("Attributes = ")
                        if (attrs.contains("D", ignoreCase = true)) {
                            isDir = true
                        }
                    }
                    trimmed.startsWith("Encrypted = ") -> {
                        encrypted = trimmed.removePrefix("Encrypted = ").trim() == "+"
                    }
                }
            }

            if (path != null && path.isNotEmpty()) {
                // Filter out entries that look like absolute filesystem paths (the archive itself)
                // 7za sometimes lists the archive container as "/storage/emulated/.../archive.7z"
                val isAbsolutePath = path.startsWith("/") || (path.length >= 2 && path[1] == ':')
                val isArchiveSelf = path == archivePath || path.endsWith("/$archiveFileName") ||
                        path == archiveFileName
                // Also filter out Windows-style drive paths like "C:\"
                val hasWindowsDrive = path.length >= 2 && path[1] == ':' && (path.length < 3 || path[2] == '\\' || path[2] == '/')

                if (!isAbsolutePath && !isArchiveSelf && !hasWindowsDrive) {
                    val finalIsDir = isDir || path.endsWith("/") || path.endsWith("\\")
                    val cleanPath = path.trimEnd('/', '\\')
                    if (cleanPath.isNotEmpty()) {
                        entries.add(
                            ArchiveEntry(
                                path = cleanPath,
                                size = size,
                                isDirectory = finalIsDir,
                                lastModified = lastModified,
                                encrypted = encrypted
                            )
                        )
                    }
                } else {
                    android.util.Log.d("ArchiveManager", "Filtered out entry: $path")
                }
            }
        }

        return entries
    }
}
