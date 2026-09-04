package com.raival.compose.file.explorer.screen.main.tab.files.zip

import com.raival.compose.file.explorer.App.Companion.globalClass
import com.raival.compose.file.explorer.common.NativeBinaryExecutor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.lz4.FramedLZ4CompressorInputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.UUID

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
 * Manages listing, extraction, and compression of archives using the bundled lib7za.so binary
 * and Apache Commons Compress for LZ4.
 *
 * Supported pack + unpack: 7z, XZ, BZIP2, GZIP, TAR, ZIP, WIM
 * Supported unpack only:   APFS, AR, ARJ, CAB, CHM, CPIO, CramFS, DMG, EXT, FAT, GPT, HFS,
 *                          IHEX, ISO, LZH, LZMA, MBR, MSI, NSIS, NTFS, QCOW2, RAR, RPM,
 *                          SquashFS, UDF, UEFI, VDI, VHD, VHDX, VMDK, XAR, Z, ZSTD, LZ4
 *
 * Compound archives (tar.gz / tar.bz2 / tar.xz / tar.zst / tar.lz4) are listed/extracted
 * with full internal folder structure.
 *
 * Native path access: 7za runs in the app process and inherits its credentials, so real
 * filesystem paths that the app can open() are usable without a temporary copy. A cache
 * copy is only used as a fallback when the original path is not readable by the binary
 * (e.g. certain SAF / FUSE edge cases).
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
        // ZSTD (unpack since 7-Zip 24.00; this binary is 24.08)
        "zst", "zstd", "tzst",
        // LZ4 (via Apache Commons Compress fallback)
        "lz4",
        // Common aliases / wrappers / compound outer extensions
        "jar", "war", "ear", "tgz", "tbz2", "tbz", "txz", "lz", "apk", "apks", "obb",
        // Comic book archives & e-books
        "cbz", "cbr", "cb7", "cbt", "epub"
    )

    /**
     * Compound name suffixes that identify tar+compressor archives.
     * Used so callers can treat file.tar.xz etc. as archives even when
     * [File.extension] only returns the last segment.
     */
    val COMPOUND_ARCHIVE_SUFFIXES = listOf(
        ".tar.gz", ".tar.bz2", ".tar.bzip2", ".tar.xz", ".tar.zst", ".tar.zstd",
        ".tar.lz4", ".tar.lz", ".tgz", ".tbz2", ".tbz", ".txz", ".tzst", ".tpz"
    )

    val SINGLE_COMPRESSED_EXTENSIONS = setOf(
        "bz2", "bzip2", "zst", "zstd", "lz4", "gz", "gzip", "xz", "lzma", "z"
    )

    fun isCompoundArchive(pathOrName: String): Boolean {
        val lower = pathOrName.lowercase()
        return COMPOUND_ARCHIVE_SUFFIXES.any { lower.endsWith(it) }
    }

    fun isSingleCompressedFile(pathOrName: String): Boolean {
        if (isCompoundArchive(pathOrName)) return false
        val ext = pathOrName.substringAfterLast('.', "").lowercase()
        return ext in SINGLE_COMPRESSED_EXTENSIONS
    }

    fun isLz4Archive(pathOrName: String): Boolean {
        val lower = pathOrName.lowercase()
        return lower.endsWith(".lz4") || lower.endsWith(".tar.lz4")
    }

    /**
     * Extensions that lib7za can CREATE (pack/compress).
     * When the user picks a compression format, only these are offered.
     * Note: pure zst/lz4 creation is not supported by stock 7-Zip 24.08.
     */
    val NATIVE_COMPRESS_EXTENSIONS = setOf(
        "7z", "zip", "tar", "gz", "bz2", "xz", "wim",
        // compound outputs we synthesise via multi-step or type flags
        "tgz", "tbz2", "txz"
    )

    /**
     * Extensions of archives that support in-place modification (delete, rename, add, update).
     */
    val MODIFIABLE_ARCHIVE_EXTENSIONS = setOf(
        "7z", "zip", "tar", "wim", "tgz", "tbz2", "txz", "apk"
    )

    /**
     * Check if an archive format can be modified (files added, deleted, renamed).
     */
    fun isModifiableArchive(pathOrExtension: String): Boolean {
        val lower = pathOrExtension.lowercase()
        if (COMPOUND_ARCHIVE_SUFFIXES.any { lower.endsWith(it) }) return true
        val ext = pathOrExtension.substringAfterLast('.', pathOrExtension).lowercase()
        return ext in MODIFIABLE_ARCHIVE_EXTENSIONS
    }

    /**
     * Check if a file extension is handled by the lib7za native binary (list + extract).
     */
    fun isNativeArchive(extension: String): Boolean {
        return extension.lowercase() in NATIVE_ARCHIVE_EXTENSIONS
    }

    /**
     * Check if a file path ends with a native archive extension (including compound forms).
     */
    fun isNativeArchivePath(path: String): Boolean {
        val lower = path.lowercase()
        if (COMPOUND_ARCHIVE_SUFFIXES.any { lower.endsWith(it) }) return true
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
     * Resolve the real filesystem path that 7za should use for [archivePath].
     * Prefers the original path; only falls back to a temporary cache copy when the
     * binary cannot open the original (permission / FUSE / non-regular-file cases).
     *
     * Callers that previously forced a full copy into cache should use this instead.
     */
    suspend fun resolveAccessibleArchivePath(originalPath: String): String = withContext(Dispatchers.IO) {
        val original = File(originalPath)
        if (original.isFile && original.canRead() && tryNativeOpen(originalPath)) {
            android.util.Log.d("PrismArchive", "ArchiveManager: using native path $originalPath")
            return@withContext originalPath
        }
        // Fallback: copy into app cache so 7za always has a plain readable path
        val cacheParent = globalClass.externalCacheDir ?: globalClass.cacheDir
        val dest = File(cacheParent, "archive_access_${original.name}_${original.length()}")
        if (!dest.exists() || dest.length() != original.length()) {
            android.util.Log.w(
                "PrismArchive",
                "ArchiveManager: native open failed for $originalPath — copying to ${dest.absolutePath}"
            )
            original.inputStream().use { input ->
                dest.outputStream().use { output -> input.copyTo(output) }
            }
        }
        dest.absolutePath
    }

    /**
     * Quick probe: run `7za l -slt <path> -p-` and treat non-access errors as "openable".
     * We only care that the file itself is reachable, not that the format is valid.
     */
    private suspend fun tryNativeOpen(path: String): Boolean {
        return try {
            val result = NativeBinaryExecutor.run(
                context = globalClass,
                binaryName = "lib7za.so",
                arguments = listOf("l", "-slt", "-p-", path)
            )
            // Access failures typically contain these phrases; format errors still mean the file was opened.
            val out = result.output.lowercase()
            val accessDenied = out.contains("cannot open") &&
                    (out.contains("permission") || out.contains("no such file") ||
                            out.contains("access is denied") || out.contains("errno"))
            !accessDenied
        } catch (_: Exception) {
            false
        }
    }

    /**
     * List the contents of a native archive using 7za (or commons-compress for LZ4).
     * Parses the -slt (show technical listing) output format.
     *
     * @param password Optional password for encrypted archives.
     * @return List of [ArchiveEntry] representing all files and directories
     */
    suspend fun listArchive(archivePath: String, password: String? = null): List<ArchiveEntry> = withContext(Dispatchers.IO) {
        val accessiblePath = resolveAccessibleArchivePath(archivePath)

        // 1. Handle LZ4 formats (pure Java / stream fallback)
        if (isLz4Archive(archivePath)) {
            return@withContext listLz4Archive(accessiblePath, archivePath)
        }

        // 2. Handle Compound TAR formats (.tar.bz2, .tar.zst, .tar.gz, .tar.xz, .tbz2, etc.)
        if (isCompoundArchive(archivePath)) {
            return@withContext listCompoundArchive(accessiblePath, password)
        }

        // 3. Standard and single-stream formats handled by lib7za
        val args = mutableListOf("l", "-slt", accessiblePath)
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

        android.util.Log.d(
            "PrismArchive",
            "ArchiveManager: listArchive exitCode=${result.exitCode}, success=${result.success}, outputLength=${result.output.length}"
        )

        if (!result.success) {
            android.util.Log.e("PrismArchive", "ArchiveManager: listArchive failed. Output: ${result.output}")
            throw Exception("7za listing failed (exit ${result.exitCode}):\n${result.output}")
        }

        val entries = parseListOutput(result.output, accessiblePath)
        android.util.Log.d("PrismArchive", "ArchiveManager: listArchive parsed ${entries.size} entries.")
        entries
    }

    private fun listLz4Archive(accessiblePath: String, archivePath: String): List<ArchiveEntry> {
        val file = File(accessiblePath)
        val lower = archivePath.lowercase()
        if (lower.endsWith(".tar.lz4")) {
            val entries = mutableListOf<ArchiveEntry>()
            try {
                FramedLZ4CompressorInputStream(file.inputStream().buffered(), true).use { lz4In ->
                    TarArchiveInputStream(lz4In).use { tarIn ->
                        while (true) {
                            val entry = try {
                                tarIn.nextEntry
                            } catch (e: Exception) {
                                android.util.Log.w("ArchiveManager", "LZ4 tar read warning: ${e.message}")
                                null
                            } ?: break

                            val cleanPath = entry.name.trimStart('.', '/', '\\').trimEnd('/', '\\').replace('\\', '/')
                            if (cleanPath.isNotEmpty() && !cleanPath.startsWith("__MACOSX") && !cleanPath.startsWith("._")) {
                                entries.add(
                                    ArchiveEntry(
                                        path = cleanPath,
                                        size = entry.size,
                                        isDirectory = entry.isDirectory,
                                        lastModified = entry.lastModifiedDate?.time ?: file.lastModified(),
                                        encrypted = false
                                    )
                                )
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.w("ArchiveManager", "LZ4 archive listing caught exception: ${e.message}")
                if (entries.isEmpty()) {
                    throw e
                }
            }
            return entries
        } else {
            val archiveFileName = archivePath.substringAfterLast('/')
            val innerName = archiveFileName.removeSuffix(".lz4").removeSuffix(".LZ4")
            return listOf(
                ArchiveEntry(
                    path = innerName,
                    size = file.length(),
                    isDirectory = false,
                    lastModified = file.lastModified(),
                    encrypted = false
                )
            )
        }
    }

    private suspend fun listCompoundArchive(accessiblePath: String, password: String?): List<ArchiveEntry> {
        val cacheParent = globalClass.externalCacheDir ?: globalClass.cacheDir
        val tempDir = File(cacheParent, "compound_list_${UUID.randomUUID()}").apply { mkdirs() }
        try {
            val args = mutableListOf("x", accessiblePath, "-o${tempDir.absolutePath}", "-y", "-aoa")
            if (!password.isNullOrEmpty()) {
                args.add("-p$password")
            } else {
                args.add("-p-")
            }
            val extractResult = NativeBinaryExecutor.run(
                context = globalClass,
                binaryName = "lib7za.so",
                arguments = args
            )
            if (!extractResult.success) {
                throw Exception("7za compound outer unpack failed (exit ${extractResult.exitCode}):\n${extractResult.output}")
            }
            val tarFile = tempDir.listFiles()?.firstOrNull { it.extension.lowercase() == "tar" }
                ?: throw Exception("No intermediate tar file found in compound archive")

            val listArgs = mutableListOf("l", "-slt", "-p-", tarFile.absolutePath)
            val listResult = NativeBinaryExecutor.run(
                context = globalClass,
                binaryName = "lib7za.so",
                arguments = listArgs
            )
            if (!listResult.success) {
                throw Exception("7za intermediate tar listing failed (exit ${listResult.exitCode}):\n${listResult.output}")
            }
            return parseListOutput(listResult.output, tarFile.absolutePath)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    /**
     * Extract all contents of a native archive to the destination directory.
     *
     * @param password Optional password for encrypted archives. Passed as -p<password> to 7za.
     */
    suspend fun extractAll(
        archivePath: String,
        destinationDir: String,
        password: String? = null,
        isAborted: (() -> Boolean)? = null,
        onProgress: ((progressPercent: Float, currentFile: String) -> Unit)? = null
    ) = withContext(Dispatchers.IO) {
        val accessiblePath = resolveAccessibleArchivePath(archivePath)

        if (isLz4Archive(archivePath)) {
            extractAllLz4(accessiblePath, archivePath, destinationDir, isAborted, onProgress)
            return@withContext
        }

        if (isCompoundArchive(archivePath)) {
            extractAllCompound(accessiblePath, destinationDir, password, isAborted, onProgress)
            return@withContext
        }

        val args = mutableListOf("x", accessiblePath, "-o$destinationDir", "-y", "-aoa", "-bsp1")
        if (!password.isNullOrEmpty()) {
            args.add("-p$password")
        } else {
            args.add("-p-")
        }
        android.util.Log.d("PrismArchive", "ArchiveManager: extractAll command=7z ${args.joinToString(" ")}")
        val result = NativeBinaryExecutor.run(
            context = globalClass,
            binaryName = "lib7za.so",
            arguments = args,
            isAborted = isAborted,
            onProgressUpdate = onProgress
        )

        android.util.Log.d("PrismArchive", "ArchiveManager: extractAll exitCode=${result.exitCode}, success=${result.success}")

        if (isAborted?.invoke() == true) {
            return@withContext
        }

        if (!result.success) {
            android.util.Log.e("PrismArchive", "ArchiveManager: extractAll failed. Output: ${result.output}")
            throw Exception("7za extraction failed (exit ${result.exitCode}):\n${result.output}")
        }
        android.util.Log.d("PrismArchive", "ArchiveManager: Extraction successful for $archivePath")
    }

    private fun extractAllLz4(
        accessiblePath: String,
        archivePath: String,
        destinationDir: String,
        isAborted: (() -> Boolean)? = null,
        onProgress: ((progressPercent: Float, currentFile: String) -> Unit)? = null
    ) {
        val file = File(accessiblePath)
        val lower = archivePath.lowercase()
        val destDir = File(destinationDir).apply { mkdirs() }
        if (lower.endsWith(".tar.lz4")) {
            try {
                FramedLZ4CompressorInputStream(file.inputStream().buffered(), true).use { lz4In ->
                    TarArchiveInputStream(lz4In).use { tarIn ->
                        while (true) {
                            if (isAborted?.invoke() == true) return
                            val entry = try {
                                tarIn.nextEntry
                            } catch (e: Exception) {
                                android.util.Log.w("ArchiveManager", "LZ4 tar read warning: ${e.message}")
                                null
                            } ?: break

                            val cleanPath = entry.name.trimStart('.', '/', '\\').trimEnd('/', '\\').replace('\\', '/')
                            if (cleanPath.isNotEmpty() && !cleanPath.startsWith("__MACOSX") && !cleanPath.startsWith("._")) {
                                val outFile = File(destDir, cleanPath)
                                if (entry.isDirectory) {
                                    outFile.mkdirs()
                                } else {
                                    outFile.parentFile?.mkdirs()
                                    onProgress?.invoke(-1f, outFile.name)
                                    try {
                                        outFile.outputStream().use { out -> tarIn.copyTo(out) }
                                    } catch (e: Exception) {
                                        android.util.Log.w("ArchiveManager", "LZ4 file extract error for $cleanPath: ${e.message}")
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.w("ArchiveManager", "LZ4 extractAll warning: ${e.message}")
            }
        } else {
            val archiveFileName = archivePath.substringAfterLast('/')
            val innerName = archiveFileName.removeSuffix(".lz4").removeSuffix(".LZ4")
            val outFile = File(destDir, innerName)
            outFile.parentFile?.mkdirs()
            onProgress?.invoke(-1f, innerName)
            FramedLZ4CompressorInputStream(file.inputStream().buffered(), true).use { input ->
                outFile.outputStream().use { output -> input.copyTo(output) }
            }
        }
    }

    private suspend fun extractAllCompound(
        accessiblePath: String,
        destinationDir: String,
        password: String?,
        isAborted: (() -> Boolean)? = null,
        onProgress: ((progressPercent: Float, currentFile: String) -> Unit)? = null
    ) {
        val cacheParent = globalClass.externalCacheDir ?: globalClass.cacheDir
        val tempDir = File(cacheParent, "compound_ext_all_${UUID.randomUUID()}").apply { mkdirs() }
        try {
            val args = mutableListOf("x", accessiblePath, "-o${tempDir.absolutePath}", "-y", "-aoa", "-bsp1")
            if (!password.isNullOrEmpty()) {
                args.add("-p$password")
            } else {
                args.add("-p-")
            }
            val outerResult = NativeBinaryExecutor.run(
                context = globalClass,
                binaryName = "lib7za.so",
                arguments = args,
                isAborted = isAborted,
                onProgressUpdate = { pct, file ->
                    if (pct >= 0f) {
                        onProgress?.invoke(pct * 0.5f, file)
                    } else {
                        onProgress?.invoke(-1f, file)
                    }
                }
            )
            if (isAborted?.invoke() == true) return
            if (!outerResult.success) {
                throw Exception("7za compound outer unpack failed (exit ${outerResult.exitCode}):\n${outerResult.output}")
            }
            val tarFile = tempDir.listFiles()?.firstOrNull { it.extension.lowercase() == "tar" }
                ?: throw Exception("No intermediate tar file found in compound archive")

            val tarArgs = mutableListOf("x", tarFile.absolutePath, "-o$destinationDir", "-y", "-aoa", "-bsp1")
            val tarResult = NativeBinaryExecutor.run(
                context = globalClass,
                binaryName = "lib7za.so",
                arguments = tarArgs,
                isAborted = isAborted,
                onProgressUpdate = { pct, file ->
                    if (pct >= 0f) {
                        onProgress?.invoke(0.5f + (pct * 0.5f), file)
                    } else {
                        onProgress?.invoke(-1f, file)
                    }
                }
            )
            if (isAborted?.invoke() == true) return
            if (!tarResult.success) {
                throw Exception("7za intermediate tar extract failed (exit ${tarResult.exitCode}):\n${tarResult.output}")
            }
        } finally {
            tempDir.deleteRecursively()
        }
    }

    /**
     * Extract one or more members from a native archive into [destinationDir].
     * Paths are relative to the archive root (as returned by listArchive).
     *
     * Uses a single 7za invocation for the whole batch so nested folders and their
     * file contents are preserved correctly (fixes "only folders, no files" for non-zip).
     */
    suspend fun extractMembers(
        archivePath: String,
        internalPaths: List<String>,
        destinationDir: String,
        password: String? = null
    ) = withContext(Dispatchers.IO) {
        if (internalPaths.isEmpty()) return@withContext
        val accessiblePath = resolveAccessibleArchivePath(archivePath)

        if (isLz4Archive(archivePath)) {
            extractMembersLz4(accessiblePath, archivePath, internalPaths, destinationDir)
            return@withContext
        }

        if (isCompoundArchive(archivePath)) {
            extractMembersCompound(accessiblePath, internalPaths, destinationDir, password)
            return@withContext
        }

        val args = mutableListOf("x", accessiblePath, "-o$destinationDir", "-y", "-aoa")
        if (!password.isNullOrEmpty()) {
            args.add("-p$password")
        } else {
            args.add("-p-")
        }

        // For single compressed files (.bz2, .zst, .gz, etc.), 7za automatically extracts the single file.
        // We only add internalPaths include filters for container archives (and include ./ variants).
        if (!isSingleCompressedFile(archivePath)) {
            val filterArgs = mutableListOf<String>()
            for (p in internalPaths) {
                val clean = p.trimStart('.', '/', '\\')
                filterArgs.add(clean)
                filterArgs.add("./$clean")
            }
            args.addAll(filterArgs.distinct())
        }

        android.util.Log.d("PrismArchive", "ArchiveManager: extractMembers command=7z ${args.joinToString(" ")}")
        val result = NativeBinaryExecutor.run(
            context = globalClass,
            binaryName = "lib7za.so",
            arguments = args
        )
        android.util.Log.d(
            "PrismArchive",
            "ArchiveManager: extractMembers exitCode=${result.exitCode}, success=${result.success}"
        )
        if (!result.success) {
            android.util.Log.e("PrismArchive", "ArchiveManager: extractMembers failed. Output: ${result.output}")
            throw Exception("7za extract failed (exit ${result.exitCode}):\n${result.output}")
        }
    }

    private fun extractMembersLz4(
        accessiblePath: String,
        archivePath: String,
        internalPaths: List<String>,
        destinationDir: String
    ) {
        val file = File(accessiblePath)
        val lower = archivePath.lowercase()
        val destDir = File(destinationDir).apply { mkdirs() }
        val targetSet = internalPaths.map { it.trimStart('.', '/', '\\').trimEnd('/', '\\') }.toSet()

        if (lower.endsWith(".tar.lz4")) {
            try {
                FramedLZ4CompressorInputStream(file.inputStream().buffered(), true).use { lz4In ->
                    TarArchiveInputStream(lz4In).use { tarIn ->
                        while (true) {
                            val entry = try {
                                tarIn.nextEntry
                            } catch (e: Exception) {
                                android.util.Log.w("ArchiveManager", "LZ4 tar read warning: ${e.message}")
                                null
                            } ?: break

                            val cleanPath = entry.name.trimStart('.', '/', '\\').trimEnd('/', '\\').replace('\\', '/')
                            val isMatch = targetSet.contains(cleanPath) || targetSet.any { cleanPath.startsWith("$it/") }
                            if (isMatch) {
                                val outFile = File(destDir, cleanPath)
                                if (entry.isDirectory) {
                                    outFile.mkdirs()
                                } else {
                                    outFile.parentFile?.mkdirs()
                                    try {
                                        outFile.outputStream().use { out -> tarIn.copyTo(out) }
                                    } catch (e: Exception) {
                                        android.util.Log.w("ArchiveManager", "LZ4 file extract error for $cleanPath: ${e.message}")
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.w("ArchiveManager", "LZ4 extractMembers warning: ${e.message}")
            }
        } else {
            val archiveFileName = archivePath.substringAfterLast('/')
            val innerName = archiveFileName.removeSuffix(".lz4").removeSuffix(".LZ4")
            val targetName = internalPaths.firstOrNull()?.trimStart('.', '/', '\\') ?: innerName
            val outFile = File(destDir, targetName)
            outFile.parentFile?.mkdirs()
            FramedLZ4CompressorInputStream(file.inputStream().buffered(), true).use { input ->
                outFile.outputStream().use { output -> input.copyTo(output) }
            }
        }
    }

    private suspend fun extractMembersCompound(
        accessiblePath: String,
        internalPaths: List<String>,
        destinationDir: String,
        password: String?
    ) {
        val cacheParent = globalClass.externalCacheDir ?: globalClass.cacheDir
        val tempDir = File(cacheParent, "compound_ext_m_${UUID.randomUUID()}").apply { mkdirs() }
        try {
            val outerArgs = mutableListOf("x", accessiblePath, "-o${tempDir.absolutePath}", "-y", "-aoa")
            if (!password.isNullOrEmpty()) {
                outerArgs.add("-p$password")
            } else {
                outerArgs.add("-p-")
            }
            val outerResult = NativeBinaryExecutor.run(
                context = globalClass,
                binaryName = "lib7za.so",
                arguments = outerArgs
            )
            if (!outerResult.success) {
                throw Exception("7za compound outer unpack failed (exit ${outerResult.exitCode}):\n${outerResult.output}")
            }
            val tarFile = tempDir.listFiles()?.firstOrNull { it.extension.lowercase() == "tar" }
                ?: throw Exception("No intermediate tar file found in compound archive")

            val tarArgs = mutableListOf("x", tarFile.absolutePath, "-o$destinationDir", "-y", "-aoa")
            val filterArgs = mutableListOf<String>()
            for (p in internalPaths) {
                val clean = p.trimStart('.', '/', '\\')
                filterArgs.add(clean)
                filterArgs.add("./$clean")
            }
            tarArgs.addAll(filterArgs.distinct())

            val tarResult = NativeBinaryExecutor.run(
                context = globalClass,
                binaryName = "lib7za.so",
                arguments = tarArgs
            )
            if (!tarResult.success) {
                throw Exception("7za intermediate tar extract failed (exit ${tarResult.exitCode}):\n${tarResult.output}")
            }
        } finally {
            tempDir.deleteRecursively()
        }
    }

    /**
     * Extract a single file (or directory tree) from a native archive.
     */
    suspend fun extractSingleFile(
        archivePath: String,
        internalPath: String,
        destinationDir: String,
        password: String? = null
    ) {
        extractMembers(archivePath, listOf(internalPath), destinationDir, password)
    }

    private fun syncBackIfCached(originalPath: String, accessiblePath: String) {
        if (originalPath != accessiblePath) {
            val src = File(accessiblePath)
            val dst = File(originalPath)
            if (src.exists()) {
                src.copyTo(dst, overwrite = true)
            }
        }
    }

    /**
     * Delete one or more members from a native archive.
     */
    suspend fun deleteMembers(
        archivePath: String,
        internalPaths: List<String>,
        password: String? = null
    ) = withContext(Dispatchers.IO) {
        if (internalPaths.isEmpty()) return@withContext
        val lowerName = archivePath.lowercase()
        val isCompound = COMPOUND_ARCHIVE_SUFFIXES.any { lowerName.endsWith(it) }

        if (isCompound) {
            deleteMembersFromCompound(archivePath, internalPaths)
        } else {
            val accessiblePath = resolveAccessibleArchivePath(archivePath)
            val args = mutableListOf("d", accessiblePath, "-y")
            if (!password.isNullOrEmpty()) {
                args.add("-p$password")
            } else {
                args.add("-p-")
            }
            args.addAll(internalPaths)
            android.util.Log.d("PrismArchive", "ArchiveManager: deleteMembers command=7z ${args.joinToString(" ")}")
            val result = NativeBinaryExecutor.run(
                context = globalClass,
                binaryName = "lib7za.so",
                arguments = args
            )
            if (!result.success) {
                android.util.Log.e("PrismArchive", "ArchiveManager: deleteMembers failed. Output: ${result.output}")
                throw Exception("7za delete failed (exit ${result.exitCode}):\n${result.output}")
            }
            syncBackIfCached(archivePath, accessiblePath)
        }
    }

    /**
     * Delete members from a compound archive (.tar.gz, .tar.bz2, .tar.xz).
     */
    private suspend fun deleteMembersFromCompound(
        archivePath: String,
        internalPaths: List<String>
    ) {
        val cacheParent = globalClass.externalCacheDir ?: globalClass.cacheDir
        val tempDir = File(cacheParent, "compound_del_${UUID.randomUUID()}").apply { mkdirs() }
        val tempTar = File(tempDir, "archive.tar")
        try {
            extractAll(archivePath, tempDir.absolutePath)
            val extractedTar = tempDir.listFiles()?.firstOrNull { it.extension.lowercase() == "tar" }
                ?: throw Exception("Could not extract intermediate tar from $archivePath")
            extractedTar.renameTo(tempTar)

            val delArgs = mutableListOf("d", tempTar.absolutePath, "-y")
            delArgs.addAll(internalPaths)
            val delResult = NativeBinaryExecutor.run(
                context = globalClass,
                binaryName = "lib7za.so",
                arguments = delArgs
            )
            if (!delResult.success) {
                throw Exception("7za delete from tar failed (exit ${delResult.exitCode}):\n${delResult.output}")
            }

            val lowerName = archivePath.lowercase()
            val outerExt = when {
                lowerName.endsWith(".tar.gz") || lowerName.endsWith(".tgz") -> "tgz"
                lowerName.endsWith(".tar.bz2") || lowerName.endsWith(".tbz2") || lowerName.endsWith(".tbz") -> "tbz2"
                lowerName.endsWith(".tar.xz") || lowerName.endsWith(".txz") -> "txz"
                else -> "tgz"
            }
            val outerFlag = when (outerExt) {
                "tgz" -> "-tgzip"
                "tbz2" -> "-tbzip2"
                "txz" -> "-txz"
                else -> "-tgzip"
            }
            val tempOuter = File(tempDir, "output.archive")
            val compResult = NativeBinaryExecutor.run(
                context = globalClass,
                binaryName = "lib7za.so",
                arguments = listOf("a", outerFlag, tempOuter.absolutePath, "-mx=5", tempTar.absolutePath)
            )
            if (!compResult.success) {
                throw Exception("7za compound recompress failed (exit ${compResult.exitCode}):\n${compResult.output}")
            }
            tempOuter.copyTo(File(archivePath), overwrite = true)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    /**
     * Rename one or more members inside an archive.
     */
    suspend fun renameMembers(
        archivePath: String,
        renameMap: Map<String, String>,
        password: String? = null
    ) = withContext(Dispatchers.IO) {
        if (renameMap.isEmpty()) return@withContext
        val accessiblePath = resolveAccessibleArchivePath(archivePath)
        val ext = archivePath.substringAfterLast('.', "").lowercase()

        // 7z format has native 'rn' command: 7z rn a.7z old1 new1 old2 new2 ...
        if (ext == "7z") {
            val args = mutableListOf("rn", accessiblePath, "-y")
            if (!password.isNullOrEmpty()) {
                args.add("-p$password")
            } else {
                args.add("-p-")
            }
            renameMap.forEach { (oldPath, newPath) ->
                args.add(oldPath)
                args.add(newPath)
            }
            val result = NativeBinaryExecutor.run(
                context = globalClass,
                binaryName = "lib7za.so",
                arguments = args
            )
            if (result.success) {
                syncBackIfCached(archivePath, accessiblePath)
                return@withContext
            }
            android.util.Log.w("PrismArchive", "7za rn failed, falling back to extract-delete-add: ${result.output}")
        }

        // General fallback: extract old -> rename in staging -> delete old -> add new
        val cacheParent = globalClass.externalCacheDir ?: globalClass.cacheDir
        val stagingDir = File(cacheParent, "rename_staging_${UUID.randomUUID()}").apply { mkdirs() }
        try {
            val oldPaths = renameMap.keys.toList()
            extractMembers(archivePath, oldPaths, stagingDir.absolutePath, password)

            renameMap.forEach { (oldPath, newPath) ->
                val extractedOld = File(stagingDir, oldPath)
                if (extractedOld.exists()) {
                    val targetNew = File(stagingDir, newPath)
                    targetNew.parentFile?.mkdirs()
                    extractedOld.renameTo(targetNew)
                }
            }

            deleteMembers(archivePath, oldPaths, password)

            val newPaths = renameMap.values.toList()
            val addArgs = mutableListOf("a", accessiblePath, "-y", "-r")
            if (!password.isNullOrEmpty()) {
                addArgs.add("-p$password")
            } else {
                addArgs.add("-p-")
            }
            addArgs.addAll(newPaths)
            val addResult = NativeBinaryExecutor.run(
                context = globalClass,
                binaryName = "lib7za.so",
                arguments = addArgs,
                workingDir = stagingDir.absolutePath
            )
            if (!addResult.success) {
                throw Exception("7za rename (re-add) failed (exit ${addResult.exitCode}):\n${addResult.output}")
            }
            syncBackIfCached(archivePath, accessiblePath)
        } finally {
            stagingDir.deleteRecursively()
        }
    }

    /**
     * Add or update a file or folder inside an archive.
     */
    suspend fun addOrUpdateMember(
        archivePath: String,
        localFile: File,
        internalPath: String,
        password: String? = null
    ) = withContext(Dispatchers.IO) {
        val accessiblePath = resolveAccessibleArchivePath(archivePath)
        val cacheParent = globalClass.externalCacheDir ?: globalClass.cacheDir
        val stagingDir = File(cacheParent, "add_staging_${UUID.randomUUID()}").apply { mkdirs() }
        try {
            val stagedTarget = File(stagingDir, internalPath)
            if (localFile.isDirectory) {
                stagedTarget.mkdirs()
            } else {
                stagedTarget.parentFile?.mkdirs()
                localFile.copyTo(stagedTarget, overwrite = true)
            }

            val args = mutableListOf("a", accessiblePath, "-y", "-r")
            if (!password.isNullOrEmpty()) {
                args.add("-p$password")
            } else {
                args.add("-p-")
            }
            args.add(internalPath)
            val result = NativeBinaryExecutor.run(
                context = globalClass,
                binaryName = "lib7za.so",
                arguments = args,
                workingDir = stagingDir.absolutePath
            )
            if (!result.success) {
                throw Exception("7za add/update failed (exit ${result.exitCode}):\n${result.output}")
            }
            syncBackIfCached(archivePath, accessiblePath)
        } finally {
            stagingDir.deleteRecursively()
        }
    }

    /**
     * Create an empty file or folder entry inside an archive.
     */
    suspend fun createEmptyEntry(
        archivePath: String,
        internalPath: String,
        isDirectory: Boolean,
        password: String? = null
    ) = withContext(Dispatchers.IO) {
        val accessiblePath = resolveAccessibleArchivePath(archivePath)
        val cacheParent = globalClass.externalCacheDir ?: globalClass.cacheDir
        val stagingDir = File(cacheParent, "new_entry_staging_${UUID.randomUUID()}").apply { mkdirs() }
        try {
            val stagedTarget = File(stagingDir, internalPath)
            if (isDirectory) {
                stagedTarget.mkdirs()
            } else {
                stagedTarget.parentFile?.mkdirs()
                stagedTarget.createNewFile()
            }

            val args = mutableListOf("a", accessiblePath, "-y", "-r")
            if (!password.isNullOrEmpty()) {
                args.add("-p$password")
            } else {
                args.add("-p-")
            }
            args.add(internalPath)
            val result = NativeBinaryExecutor.run(
                context = globalClass,
                binaryName = "lib7za.so",
                arguments = args,
                workingDir = stagingDir.absolutePath
            )
            if (!result.success) {
                throw Exception("7za create empty entry failed (exit ${result.exitCode}):\n${result.output}")
            }
            syncBackIfCached(archivePath, accessiblePath)
        } finally {
            stagingDir.deleteRecursively()
        }
    }

    /**
     * Compress files/directories into a native archive format using lib7za.
     *
     * Supported output formats: 7z, zip, tar, gz, bz2, xz, wim, tgz, tbz2, txz
     * (determined by [archivePath] extension).
     *
     * For compound formats (tgz / tbz2 / txz) we create a temporary .tar then
     * compress it with the outer format, matching 7-Zip CLI behaviour.
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
        compressionLevel: Int = 5,
        isAborted: (() -> Boolean)? = null,
        onProgress: ((progressPercent: Float, currentFile: String) -> Unit)? = null
    ) = withContext(Dispatchers.IO) {
        val lowerName = archivePath.lowercase()
        val ext = when {
            lowerName.endsWith(".tar.gz") || lowerName.endsWith(".tgz") -> "tgz"
            lowerName.endsWith(".tar.bz2") || lowerName.endsWith(".tbz2") || lowerName.endsWith(".tbz") -> "tbz2"
            lowerName.endsWith(".tar.xz") || lowerName.endsWith(".txz") -> "txz"
            else -> archivePath.substringAfterLast('.', "").lowercase()
        }

        when (ext) {
            "tgz", "tbz2", "txz" -> compressCompound(sourcePaths, archivePath, ext, compressionLevel, isAborted, onProgress)
            else -> compressSimple(sourcePaths, archivePath, ext, password, compressionLevel, isAborted, onProgress)
        }
    }

    private suspend fun compressSimple(
        sourcePaths: List<String>,
        archivePath: String,
        ext: String,
        password: String?,
        compressionLevel: Int,
        isAborted: (() -> Boolean)? = null,
        onProgress: ((progressPercent: Float, currentFile: String) -> Unit)? = null
    ) {
        val formatFlag = when (ext) {
            "7z" -> "-t7z"
            "zip" -> "-tzip"
            "tar" -> "-ttar"
            "gz", "gzip" -> "-tgzip"
            "bz2", "bzip2" -> "-tbzip2"
            "xz" -> "-txz"
            "wim" -> "-twim"
            else -> "-t7z"
        }

        val args = mutableListOf("a", formatFlag, archivePath, "-bsp1")
        args.add("-mx=$compressionLevel")

        if (!password.isNullOrEmpty() && (ext == "7z" || ext == "zip")) {
            args.add("-p$password")
            if (ext == "7z") {
                args.add("-mhe=on")
            }
        }

        args.addAll(sourcePaths)

        android.util.Log.d("PrismArchive", "ArchiveManager: compress command=7z ${args.joinToString(" ")}")
        val result = NativeBinaryExecutor.run(
            context = globalClass,
            binaryName = "lib7za.so",
            arguments = args,
            isAborted = isAborted,
            onProgressUpdate = onProgress
        )

        android.util.Log.d("PrismArchive", "ArchiveManager: compress exitCode=${result.exitCode}, success=${result.success}")

        if (isAborted?.invoke() == true) return

        if (!result.success) {
            android.util.Log.e("PrismArchive", "ArchiveManager: compress failed. Output: ${result.output}")
            throw Exception("7za compression failed (exit ${result.exitCode}):\n${result.output}")
        }
        android.util.Log.d("PrismArchive", "ArchiveManager: Compression successful -> $archivePath")
    }

    /**
     * Create tar + outer compressor (gzip / bzip2 / xz) as two 7za steps.
     * Single-stream compressors cannot hold multiple members, so we always go via tar.
     */
    private suspend fun compressCompound(
        sourcePaths: List<String>,
        archivePath: String,
        compoundExt: String,
        compressionLevel: Int,
        isAborted: (() -> Boolean)? = null,
        onProgress: ((progressPercent: Float, currentFile: String) -> Unit)? = null
    ) {
        val cacheParent = globalClass.externalCacheDir ?: globalClass.cacheDir
        val tarTemp = File(cacheParent, "compress_tar_${System.currentTimeMillis()}.tar")
        try {
            // Step 1: create intermediate tar
            val tarArgs = mutableListOf("a", "-ttar", tarTemp.absolutePath, "-bsp1")
            tarArgs.addAll(sourcePaths)
            android.util.Log.d("PrismArchive", "ArchiveManager: compressCompound tar step: 7z ${tarArgs.joinToString(" ")}")
            val tarResult = NativeBinaryExecutor.run(
                context = globalClass,
                binaryName = "lib7za.so",
                arguments = tarArgs,
                isAborted = isAborted,
                onProgressUpdate = { pct, file ->
                    if (pct >= 0f) {
                        onProgress?.invoke(pct * 0.5f, file)
                    } else {
                        onProgress?.invoke(-1f, file)
                    }
                }
            )
            if (isAborted?.invoke() == true) return
            if (!tarResult.success) {
                throw Exception("7za tar step failed (exit ${tarResult.exitCode}):\n${tarResult.output}")
            }

            // Step 2: compress the tar with the outer format
            val outerFlag = when (compoundExt) {
                "tgz" -> "-tgzip"
                "tbz2" -> "-tbzip2"
                "txz" -> "-txz"
                else -> "-tgzip"
            }
            val outerArgs = mutableListOf(
                "a", outerFlag, archivePath, "-mx=$compressionLevel", tarTemp.absolutePath, "-bsp1"
            )
            android.util.Log.d("PrismArchive", "ArchiveManager: compressCompound outer step: 7z ${outerArgs.joinToString(" ")}")
            val outerResult = NativeBinaryExecutor.run(
                context = globalClass,
                binaryName = "lib7za.so",
                arguments = outerArgs,
                isAborted = isAborted,
                onProgressUpdate = { pct, file ->
                    if (pct >= 0f) {
                        onProgress?.invoke(0.5f + (pct * 0.5f), file)
                    } else {
                        onProgress?.invoke(-1f, file)
                    }
                }
            )
            if (isAborted?.invoke() == true) return
            if (!outerResult.success) {
                throw Exception("7za outer compress failed (exit ${outerResult.exitCode}):\n${outerResult.output}")
            }
            android.util.Log.d("PrismArchive", "ArchiveManager: Compound compression successful -> $archivePath")
        } finally {
            tarTemp.delete()
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
        // 7za -slt separates property blocks with blank lines; also tolerate \r\n
        val blocks = output.replace("\r\n", "\n").split("\n\n")
        val archiveFileName = archivePath.substringAfterLast('/')
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        var singleEntryParsedSize = 0L

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
                            val cleanDate = dateStr.substringBefore('.')
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

            if (size > 0) {
                singleEntryParsedSize = size
            }

            if (path != null && path.isNotEmpty()) {
                val isAbsolutePath = path.startsWith("/") || (path.length >= 2 && path[1] == ':')
                val isArchiveSelf = path == archivePath || path.endsWith("/$archiveFileName") ||
                        path == archiveFileName
                val hasWindowsDrive = path.length >= 2 && path[1] == ':' &&
                        (path.length < 3 || path[2] == '\\' || path[2] == '/')

                if (!isAbsolutePath && !isArchiveSelf && !hasWindowsDrive) {
                    val finalIsDir = isDir || path.endsWith("/") || path.endsWith("\\")
                    val cleanPath = path.trimStart('.', '/', '\\').trimEnd('/', '\\').replace('\\', '/')
                    if (cleanPath.isNotEmpty() && !cleanPath.startsWith("__MACOSX") && !cleanPath.startsWith("._")) {
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

        // Single-stream compressor fallback: .bz2, .zst, .xz, .gz, etc.
        // If 7za didn't emit a Path = line for the inner file, synthesize the single inner entry.
        if (entries.isEmpty() && isSingleCompressedFile(archivePath)) {
            val innerName = archiveFileName.substringBeforeLast('.')
            val srcFile = File(archivePath)
            val entrySize = if (singleEntryParsedSize > 0) singleEntryParsedSize else srcFile.length()
            val entryTime = if (srcFile.exists()) srcFile.lastModified() else System.currentTimeMillis()
            entries.add(
                ArchiveEntry(
                    path = innerName,
                    size = entrySize,
                    isDirectory = false,
                    lastModified = entryTime,
                    encrypted = false
                )
            )
            android.util.Log.d(
                "ArchiveManager",
                "Synthesized single-file entry '$innerName' (size=$entrySize) for single compressed file $archiveFileName"
            )
        }

        return entries
    }
}
