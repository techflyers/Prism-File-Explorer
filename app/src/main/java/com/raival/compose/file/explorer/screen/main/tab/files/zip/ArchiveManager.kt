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
 * Manages listing, extraction, and compression of archives using the bundled lib7za.so binary.
 *
 * Supported pack + unpack: 7z, XZ, BZIP2, GZIP, TAR, ZIP, WIM
 * Supported unpack only:   APFS, AR, ARJ, CAB, CHM, CPIO, CramFS, DMG, EXT, FAT, GPT, HFS,
 *                          IHEX, ISO, LZH, LZMA, MBR, MSI, NSIS, NTFS, QCOW2, RAR, RPM,
 *                          SquashFS, UDF, UEFI, VDI, VHD, VHDX, VMDK, XAR, Z
 */
object ArchiveManager {

    /**
     * All archive extensions that lib7za can open (list + extract).
     * Includes both pack+unpack and unpack-only formats.
     */
    private val NATIVE_ARCHIVE_EXTENSIONS = setOf(
        // Pack + Unpack
        "7z", "xz", "bz2", "bzip2", "gz", "gzip", "tar", "zip", "wim",
        // Unpack Only
        "apfs", "ar", "arj", "cab", "chm", "cpio", "cramfs", "dmg",
        "ext", "fat", "gpt", "hfs", "ihex", "iso", "lzh", "lzma",
        "mbr", "msi", "nsis", "ntfs", "qcow2", "rar", "rpm", "squashfs",
        "udf", "uefi", "vdi", "vhd", "vhdx", "vmdk", "xar", "z",
        // Common aliases / wrappers
        "jar", "war", "ear", "tgz", "tbz2", "txz", "lz", "apk", "apks", "obb"
    )

    /**
     * Extensions that lib7za can CREATE (pack/compress).
     * When the user picks a compression format, only these are offered.
     */
    val NATIVE_COMPRESS_EXTENSIONS = setOf("7z", "zip", "tar", "gz", "bz2", "xz", "wim")

    /**
     * Check if a file extension is handled by the lib7za native binary (list + extract).
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
     * Check if an extension can be used as a compression output format (i.e. lib7za can create it).
     */
    fun isNativeCompressFormat(extension: String): Boolean {
        return extension.lowercase() in NATIVE_COMPRESS_EXTENSIONS
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
     * Compress files/directories into a native archive format using lib7za.
     *
     * Supported output formats: 7z, zip, tar, gz, bz2, xz, wim (determined by [archivePath] extension).
     *
     * @param sourcePaths   List of absolute paths to files or folders to add.
     * @param archivePath   Destination archive path. The extension determines the format.
     * @param password      Optional password (supported for 7z and zip formats only).
     * @param compressionLevel Compression level 0–9 (0=store, 9=ultra). Mapped to 7za -mx= flag.
     */
    suspend fun compress(
        sourcePaths: List<String>,
        archivePath: String,
        password: String? = null,
        compressionLevel: Int = 5
    ) = withContext(Dispatchers.IO) {
        val ext = archivePath.substringAfterLast('.', "").lowercase()

        // Determine the 7za format type flag from the output extension
        val formatFlag = when (ext) {
            "7z"               -> "-t7z"
            "zip"              -> "-tzip"
            "tar"              -> "-ttar"
            "gz", "gzip"       -> "-tgzip"
            "bz2", "bzip2"     -> "-tbzip2"
            "xz"               -> "-txz"
            "wim"              -> "-twim"
            else               -> "-t7z" // default fallback
        }

        val args = mutableListOf("a", formatFlag, archivePath)
        args.add("-mx=$compressionLevel")

        // Password only for 7z / zip
        if (!password.isNullOrEmpty() && (ext == "7z" || ext == "zip")) {
            args.add("-p$password")
            if (ext == "7z") {
                // Encrypt headers too for 7z
                args.add("-mhe=on")
            }
        }

        // Append all source paths
        args.addAll(sourcePaths)

        android.util.Log.d("PrismArchive", "ArchiveManager: compress command=7z ${args.joinToString(" ")}")
        val result = NativeBinaryExecutor.run(
            context = globalClass,
            binaryName = "lib7za.so",
            arguments = args
        )

        android.util.Log.d("PrismArchive", "ArchiveManager: compress exitCode=${result.exitCode}, success=${result.success}")

        if (!result.success) {
            android.util.Log.e("PrismArchive", "ArchiveManager: compress failed. Output: ${result.output}")
            throw Exception("7za compression failed (exit ${result.exitCode}):\n${result.output}")
        }
        android.util.Log.d("PrismArchive", "ArchiveManager: Compression successful -> $archivePath")
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
