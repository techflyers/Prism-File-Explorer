package com.techflyers.compose.file.explorer.screen.main.tab.files.zip

import com.techflyers.compose.file.explorer.App.Companion.globalClass
import com.techflyers.compose.file.explorer.common.NativeBinaryExecutor
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
 * Information about a multi-part (split) archive resolved in its parent directory.
 */
data class MultiPartArchiveInfo(
    val primaryFile: File,
    val allParts: List<File>,
    val missingParts: List<String>,
    val cleanBaseName: String,
    val isMultiPart: Boolean
)

/**
 * Manages listing, extraction, and compression of archives using the bundled lib7za.so binary
 * and Apache Commons Compress for LZ4.
 *
 * Supported pack + unpack: 7z, XZ, BZIP2, GZIP, TAR, ZIP, WIM (including split volumes .001, .002...)
 * Supported unpack only:   APFS, AR, ARJ, CAB, CHM, CPIO, CramFS, DMG, EXT, FAT, GPT, HFS,
 *                          IHEX, ISO, LZH, LZMA, MBR, MSI, NSIS, NTFS, QCOW2, RAR, RPM,
 *                          SquashFS, UDF, UEFI, VDI, VHD, VHDX, VMDK, XAR, Z, ZSTD, LZ4
 *
 * Compound archives (tar.gz / tar.bz2 / tar.xz / tar.zst / tar.lz4) and multi-part archives
 * (.7z.001, .zip.001, .part1.rar, .r00, .z01) are listed/extracted with full internal folder structure.
 */
object ArchiveManager {

    /**
     * Structured exception carrying both a concise user-facing summary and the full 7za diagnostic log.
     */
    class ArchiveOperationException(
        val summary: String,
        val diagnosticLog: String,
        val exitCode: Int = -1,
        val isPasswordError: Boolean = false,
        cause: Throwable? = null
    ) : Exception(summary, cause)

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
        "cbz", "cbr", "cb7", "cbt", "epub",
        // Common primary split part extension
        "001"
    )

    // Regex patterns for multi-part / split archive filenames
    private val NUMERIC_PART_REGEX = Regex("""^(.*)\.(\d{3,4})$""")
    private val RAR_PART_REGEX = Regex("""^(.*)\.part(0*\d+)\.rar$""", RegexOption.IGNORE_CASE)
    private val OLD_RAR_PART_REGEX = Regex("""^(.*)\.([rs])(\d{2,3})$""", RegexOption.IGNORE_CASE)
    private val ZIP_SPLIT_PART_REGEX = Regex("""^(.*)\.z(x?)(\d{2,3})$""", RegexOption.IGNORE_CASE)

    private val SPLIT_PART_EXT_REGEX = Regex(
        """^(\d{3,4}|[rs]\d{2,3}|z\d{2,3}|zx\d{2,3})$""",
        RegexOption.IGNORE_CASE
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

    /**
     * Strip any trailing split volume suffix (e.g. `.001`, `.part1`) to inspect the underlying archive name.
     */
    private fun stripSplitSuffix(pathOrName: String): String {
        val fileName = pathOrName.substringAfterLast('/').substringAfterLast('\\')
        NUMERIC_PART_REGEX.matchEntire(fileName)?.let { match ->
            return match.groupValues[1]
        }
        return fileName
    }

    fun isCompoundArchive(pathOrName: String): Boolean {
        val lower = stripSplitSuffix(pathOrName).lowercase()
        return COMPOUND_ARCHIVE_SUFFIXES.any { lower.endsWith(it) }
    }

    fun isSingleCompressedFile(pathOrName: String): Boolean {
        if (isCompoundArchive(pathOrName)) return false
        val stripped = stripSplitSuffix(pathOrName)
        val ext = stripped.substringAfterLast('.', "").lowercase()
        return ext in SINGLE_COMPRESSED_EXTENSIONS
    }

    fun isLz4Archive(pathOrName: String): Boolean {
        val lower = stripSplitSuffix(pathOrName).lowercase()
        return lower.endsWith(".lz4") || lower.endsWith(".tar.lz4")
    }

    /**
     * Check whether [pathOrName] matches a multi-part / split archive naming convention
     * (e.g., `.7z.001`, `.zip.002`, `.001`, `.part1.rar`, `.r00`, `.z01`).
     */
    fun isSplitArchivePart(pathOrName: String): Boolean {
        val fileName = pathOrName.substringAfterLast('/').substringAfterLast('\\')
        val lower = fileName.lowercase()

        if (RAR_PART_REGEX.matches(lower) || OLD_RAR_PART_REGEX.matches(lower) || ZIP_SPLIT_PART_REGEX.matches(lower)) {
            return true
        }

        val numMatch = NUMERIC_PART_REGEX.matchEntire(lower)
        if (numMatch != null) {
            val base = numMatch.groupValues[1]
            val partNum = numMatch.groupValues[2].toIntOrNull() ?: return false
            if (partNum <= 0) return false
            // Either it has an archive extension before .001 (e.g., archive.7z.001, archive.tar.gz.001)
            // or it is a .001/.002 volume part.
            val innerExt = base.substringAfterLast('.', "").lowercase()
            if (innerExt in NATIVE_ARCHIVE_EXTENSIONS || COMPOUND_ARCHIVE_SUFFIXES.any { base.endsWith(it) }) {
                return true
            }
            // Also accept generic .001, .002 if partNum in 1..999
            if (partNum in 1..999 && base.isNotEmpty()) {
                return true
            }
        }
        return false
    }

    /**
     * Check if a [File] is a supported archive or a part of a multi-part archive in its parent folder.
     */
    fun isSupportedArchiveFile(file: File): Boolean {
        val name = file.name
        val ext = file.extension.lowercase()
        if (isNativeArchivePath(name) || isNativeArchive(ext)) return true
        if (RAR_PART_REGEX.matches(name) || OLD_RAR_PART_REGEX.matches(name) || ZIP_SPLIT_PART_REGEX.matches(name)) {
            return true
        }
        val numMatch = NUMERIC_PART_REGEX.matchEntire(name)
        if (numMatch != null) {
            val base = numMatch.groupValues[1]
            val innerExt = base.substringAfterLast('.', "").lowercase()
            if (innerExt in NATIVE_ARCHIVE_EXTENSIONS || COMPOUND_ARCHIVE_SUFFIXES.any { base.lowercase().endsWith(it) }) {
                return true
            }
            // Generic .001/.002: check if .001 exists in parent folder
            val digits = numMatch.groupValues[2]
            val firstPartName = "$base.${"1".padStart(digits.length, '0')}"
            val parent = file.parentFile
            if (file.name.equals(firstPartName, ignoreCase = true) ||
                (parent != null && File(parent, firstPartName).exists())
            ) {
                return true
            }
        }
        return false
    }

    /**
     * Compute the clean folder/base name for an archive (stripping compound and split part suffixes).
     * Examples:
     * - `backup.7z.001` -> `backup`
     * - `project.tar.gz.002` -> `project`
     * - `movie.part01.rar` -> `movie`
     * - `photos.z01` -> `photos`
     * - `archive.tar.xz` -> `archive`
     */
    fun getCleanArchiveBaseName(pathOrName: String): String {
        val fileName = pathOrName.substringAfterLast('/').substringAfterLast('\\')

        RAR_PART_REGEX.matchEntire(fileName)?.let { match ->
            return match.groupValues[1].ifEmpty { "extracted" }
        }
        OLD_RAR_PART_REGEX.matchEntire(fileName)?.let { match ->
            return match.groupValues[1].ifEmpty { "extracted" }
        }
        ZIP_SPLIT_PART_REGEX.matchEntire(fileName)?.let { match ->
            return match.groupValues[1].ifEmpty { "extracted" }
        }

        var working = fileName
        NUMERIC_PART_REGEX.matchEntire(working)?.let { match ->
            working = match.groupValues[1]
        }

        val lowerWorking = working.lowercase()
        for (compound in COMPOUND_ARCHIVE_SUFFIXES) {
            if (lowerWorking.endsWith(compound)) {
                return working.dropLast(compound.length).ifEmpty { "extracted" }
            }
        }

        val ext = working.substringAfterLast('.', "").lowercase()
        if (ext in NATIVE_ARCHIVE_EXTENSIONS) {
            return working.substringBeforeLast('.').ifEmpty { working }
        }

        return working.substringBeforeLast('.', working).ifEmpty { "extracted" }
    }

    /**
     * Smartly inspect [file] and its parent folder to detect multi-part (split) archives,
     * locate the primary volume (`.001`, `.part1.rar`, `.rar`, `.z01`/`.zip`), collect all parts,
     * and identify any missing volume parts in the parent folder.
     */
    fun resolveMultiPartArchive(file: File): MultiPartArchiveInfo {
        val parentDir = file.parentFile
        val fileName = file.name
        val cleanBase = getCleanArchiveBaseName(fileName)

        if (parentDir == null || !parentDir.exists() || !parentDir.isDirectory) {
            return MultiPartArchiveInfo(
                primaryFile = file,
                allParts = listOf(file),
                missingParts = emptyList(),
                cleanBaseName = cleanBase,
                isMultiPart = false
            )
        }

        val siblings = parentDir.listFiles()?.filter { it.isFile } ?: listOf(file)

        // 1. Numeric split volumes: <prefix>.001, <prefix>.002, ...
        NUMERIC_PART_REGEX.matchEntire(fileName)?.let { match ->
            val prefix = match.groupValues[1]
            val digits = match.groupValues[2]
            val padWidth = digits.length

            val partRegex = Regex("""^${Regex.escape(prefix)}\.(\d{3,4})$""", RegexOption.IGNORE_CASE)
            val foundByIndex = mutableMapOf<Int, File>()
            for (sibling in siblings) {
                val m = partRegex.matchEntire(sibling.name) ?: continue
                val idx = m.groupValues[1].toIntOrNull() ?: continue
                if (idx > 0) {
                    foundByIndex[idx] = sibling
                }
            }

            val currentIdx = digits.toIntOrNull() ?: 1
            val maxIdx = maxOf(foundByIndex.keys.maxOrNull() ?: currentIdx, currentIdx)
            val firstPartName = "$prefix.${"1".padStart(padWidth, '0')}"
            val primaryFile = foundByIndex[1] ?: File(parentDir, firstPartName)

            val missing = mutableListOf<String>()
            for (i in 1..maxIdx) {
                if (!foundByIndex.containsKey(i)) {
                    missing.add("$prefix.${i.toString().padStart(padWidth, '0')}")
                }
            }
            // If user opened .001 and there are no other parts, check if it's a single .001 or multi-part
            val sortedParts = foundByIndex.entries.sortedBy { it.key }.map { it.value }
            return MultiPartArchiveInfo(
                primaryFile = primaryFile,
                allParts = sortedParts.ifEmpty { listOf(file) },
                missingParts = missing,
                cleanBaseName = getCleanArchiveBaseName(fileName),
                isMultiPart = true
            )
        }

        // 2. Modern RAR split volumes: <prefix>.part1.rar, <prefix>.part01.rar, <prefix>.part2.rar, ...
        RAR_PART_REGEX.matchEntire(fileName)?.let { match ->
            val prefix = match.groupValues[1]
            val digits = match.groupValues[2]
            val padWidth = digits.length

            val partRegex = Regex("""^${Regex.escape(prefix)}\.part(0*\d+)\.rar$""", RegexOption.IGNORE_CASE)
            val foundByIndex = mutableMapOf<Int, File>()
            var detectedPadWidth = padWidth
            for (sibling in siblings) {
                val m = partRegex.matchEntire(sibling.name) ?: continue
                val numStr = m.groupValues[1]
                val idx = numStr.toIntOrNull() ?: continue
                if (idx > 0) {
                    foundByIndex[idx] = sibling
                    detectedPadWidth = numStr.length
                }
            }

            val currentIdx = digits.toIntOrNull() ?: 1
            val maxIdx = maxOf(foundByIndex.keys.maxOrNull() ?: currentIdx, currentIdx)
            val firstPartName = "$prefix.part${"1".padStart(detectedPadWidth, '0')}.rar"
            val primaryFile = foundByIndex[1] ?: File(parentDir, firstPartName)

            val missing = mutableListOf<String>()
            for (i in 1..maxIdx) {
                if (!foundByIndex.containsKey(i)) {
                    missing.add("$prefix.part${i.toString().padStart(detectedPadWidth, '0')}.rar")
                }
            }
            val sortedParts = foundByIndex.entries.sortedBy { it.key }.map { it.value }
            return MultiPartArchiveInfo(
                primaryFile = primaryFile,
                allParts = sortedParts.ifEmpty { listOf(file) },
                missingParts = missing,
                cleanBaseName = prefix.ifEmpty { cleanBase },
                isMultiPart = true
            )
        }

        // 3. Classic RAR split volumes: <prefix>.rar, <prefix>.r00, <prefix>.r01, ...
        val isRar = fileName.lowercase().endsWith(".rar") && !RAR_PART_REGEX.matches(fileName)
        val oldRarMatch = OLD_RAR_PART_REGEX.matchEntire(fileName)
        if (isRar || oldRarMatch != null) {
            val prefix = if (oldRarMatch != null) oldRarMatch.groupValues[1] else fileName.substringBeforeLast('.')
            val rPartRegex = Regex("""^${Regex.escape(prefix)}\.r(\d{2,3})$""", RegexOption.IGNORE_CASE)
            val rarMainFile = siblings.firstOrNull { it.name.equals("$prefix.rar", ignoreCase = true) }
            val rParts = mutableMapOf<Int, File>()
            var padWidth = 2
            for (sibling in siblings) {
                val m = rPartRegex.matchEntire(sibling.name) ?: continue
                val numStr = m.groupValues[1]
                val idx = numStr.toIntOrNull() ?: continue
                rParts[idx] = sibling
                padWidth = numStr.length
            }

            if (rParts.isNotEmpty() || oldRarMatch != null) {
                val missing = mutableListOf<String>()
                if (rarMainFile == null || !rarMainFile.exists()) {
                    missing.add("$prefix.rar")
                }
                val maxIdx = rParts.keys.maxOrNull() ?: (oldRarMatch?.groupValues?.get(3)?.toIntOrNull() ?: 0)
                for (i in 0..maxIdx) {
                    if (!rParts.containsKey(i)) {
                        missing.add("$prefix.r${i.toString().padStart(padWidth, '0')}")
                    }
                }
                val primary = rarMainFile ?: rParts[0] ?: File(parentDir, "$prefix.rar")
                val all = buildList {
                    if (rarMainFile != null) add(rarMainFile)
                    addAll(rParts.entries.sortedBy { it.key }.map { it.value })
                }
                return MultiPartArchiveInfo(
                    primaryFile = primary,
                    allParts = all.ifEmpty { listOf(file) },
                    missingParts = missing,
                    cleanBaseName = prefix.ifEmpty { cleanBase },
                    isMultiPart = true
                )
            }
        }

        // 4. Split ZIP volumes: <prefix>.z01, <prefix>.z02, ..., <prefix>.zip
        val isZip = fileName.lowercase().endsWith(".zip")
        val zipSplitMatch = ZIP_SPLIT_PART_REGEX.matchEntire(fileName)
        if (isZip || zipSplitMatch != null) {
            val prefix = if (zipSplitMatch != null) zipSplitMatch.groupValues[1] else fileName.substringBeforeLast('.')
            val zPartRegex = Regex("""^${Regex.escape(prefix)}\.z(x?)(\d{2,3})$""", RegexOption.IGNORE_CASE)
            val zipMainFile = siblings.firstOrNull { it.name.equals("$prefix.zip", ignoreCase = true) }
            val zParts = mutableMapOf<Int, File>()
            var padWidth = 2
            var hasX = ""
            for (sibling in siblings) {
                val m = zPartRegex.matchEntire(sibling.name) ?: continue
                hasX = m.groupValues[1]
                val numStr = m.groupValues[2]
                val idx = numStr.toIntOrNull() ?: continue
                if (idx > 0) {
                    zParts[idx] = sibling
                    padWidth = numStr.length
                }
            }

            if (zParts.isNotEmpty() || zipSplitMatch != null) {
                val missing = mutableListOf<String>()
                val maxIdx = zParts.keys.maxOrNull() ?: (zipSplitMatch?.groupValues?.get(3)?.toIntOrNull() ?: 1)
                for (i in 1..maxIdx) {
                    if (!zParts.containsKey(i)) {
                        missing.add("$prefix.z$hasX${i.toString().padStart(padWidth, '0')}")
                    }
                }
                if (zipMainFile == null || !zipMainFile.exists()) {
                    missing.add("$prefix.zip")
                }
                // 7za opens split zip archives starting from .z01 (or .zip via zip4j fallback)
                val primary = zParts[1] ?: zipMainFile ?: File(parentDir, "$prefix.z${hasX}01")
                val all = buildList {
                    addAll(zParts.entries.sortedBy { it.key }.map { it.value })
                    if (zipMainFile != null) add(zipMainFile)
                }
                return MultiPartArchiveInfo(
                    primaryFile = primary,
                    allParts = all.ifEmpty { listOf(file) },
                    missingParts = missing,
                    cleanBaseName = prefix.ifEmpty { cleanBase },
                    isMultiPart = true
                )
            }
        }

        // 5. Check if the user tapped `archive.7z` or `archive.zip` when `archive.7z.001` exists in the parent folder
        val firstNumericSibling = siblings.firstOrNull {
            it.name.equals("$fileName.001", ignoreCase = true)
        }
        if (firstNumericSibling != null) {
            return resolveMultiPartArchive(firstNumericSibling)
        }

        return MultiPartArchiveInfo(
            primaryFile = file,
            allParts = listOf(file),
            missingParts = emptyList(),
            cleanBaseName = cleanBase,
            isMultiPart = false
        )
    }

    /**
     * Deduplicate a list of candidate archive files so that selecting multiple parts of the same
     * split archive (e.g., `.001`, `.002`, `.003`) resolves to a single extraction of its primary volume.
     */
    fun deduplicateArchiveParts(files: List<File>): List<File> {
        val seenPrimaryPaths = linkedSetOf<String>()
        val result = mutableListOf<File>()
        for (file in files) {
            val info = resolveMultiPartArchive(file)
            val target = if (info.primaryFile.exists()) info.primaryFile else file
            val canonical = runCatching { target.canonicalPath }.getOrDefault(target.absolutePath)
            if (seenPrimaryPaths.add(canonical)) {
                result.add(target)
            }
        }
        return result
    }

    /**
     * Inspects 7za output to determine whether a failure is genuinely caused by a missing or wrong password.
     *
     * IMPORTANT: Never match bare `"encrypted"` across raw `-slt` output because `7za l -slt` prints
     * `Encrypted = -` for every unencrypted entry block!
     */
    fun isPasswordRequiredError(output: String): Boolean {
        val lines = output.replace("\r\n", "\n").lines()
        for (rawLine in lines) {
            val line = rawLine.trim().lowercase()
            if (line.isEmpty()) continue
            // Ignore technical property lines for unencrypted entries
            if (line == "encrypted = -") continue
            // Ignore our own command echo or arguments
            if (line.startsWith("command=") || line.startsWith("executing:")) continue

            if (line == "encrypted = +") {
                return true
            }
            if (line.contains("can not open encrypted archive") ||
                line.contains("cannot open encrypted archive") ||
                line.contains("wrong password") ||
                line.contains("incorrect password") ||
                line.contains("data error in encrypted file") ||
                line.contains("headers error: encrypted") ||
                line.contains("enter password") ||
                line.contains("password is required")
            ) {
                return true
            }
        }
        return false
    }

    /**
     * Parses 7za stdout/stderr output into a concise, human-readable error summary instead of
     * showing the 7-Zip copyright banner or raw `-slt` property blocks.
     */
    fun parse7zaErrorSummary(output: String, exitCode: Int): String {
        val lines = output.replace("\r\n", "\n").lines().map { it.trim() }.filter { it.isNotEmpty() }

        val errorLines = lines.filter {
            it.startsWith("ERROR:", ignoreCase = true) ||
                it.startsWith("errno=", ignoreCase = true) ||
                it.contains("Missing volume", ignoreCase = true) ||
                it.contains("Unexpected end of archive", ignoreCase = true) ||
                it.contains("Unavailable data", ignoreCase = true) ||
                it.contains("Headers Error", ignoreCase = true) ||
                it.contains("Data Error", ignoreCase = true) ||
                it.contains("Unsupported Method", ignoreCase = true) ||
                it.contains("Cannot open the file as archive", ignoreCase = true) ||
                it.contains("Can not open the file as archive", ignoreCase = true) ||
                it.contains("Can not open encrypted archive", ignoreCase = true) ||
                it.contains("Wrong password", ignoreCase = true)
        }.distinct()

        if (errorLines.isNotEmpty()) {
            val cleaned = errorLines.map { line ->
                line.removePrefix("ERROR:").removePrefix("error:").trim()
            }.filter { it.isNotEmpty() }
            if (cleaned.isNotEmpty()) {
                return cleaned.joinToString("\n")
            }
        }

        val warningLines = lines.filter {
            it.startsWith("WARNING:", ignoreCase = true)
        }.map { it.removePrefix("WARNING:").removePrefix("warning:").trim() }

        if (warningLines.isNotEmpty()) {
            return warningLines.joinToString("\n")
        }

        return when (exitCode) {
            1 -> "Archive operation finished with warnings (exit code 1)."
            2 -> "Fatal archive error (exit code 2): archive may be corrupted, incomplete, or unsupported."
            7 -> "Invalid command line parameters passed to 7za (exit code 7)."
            8 -> "Not enough memory for archive operation (exit code 8)."
            255 -> "Archive operation was aborted by user."
            else -> "Archive operation failed (exit code $exitCode)."
        }
    }

    /**
     * Extracts a clean, structured diagnostic log from 7za output suitable for displaying in an
     * error log dialog (filtering out thousands of intermediate `-slt` `Path = ...` lines).
     */
    fun extract7zaErrorLog(output: String, exitCode: Int, commandDescription: String = ""): String {
        val sltPrefixes = listOf(
            "Path = ", "Size = ", "Packed Size = ", "Modified = ", "Created = ", "Accessed = ",
            "Attributes = ", "Encrypted = ", "Comment = ", "CRC = ", "Method = ", "Characteristics = ",
            "Host OS = ", "Version = ", "Volume Index = ", "Offset = ", "Folder = ", "Block = ",
            "Symbolic Link = ", "Hard Link = ", "User = ", "Group = "
        )
        val filteredLines = output.replace("\r\n", "\n")
            .lines()
            .filter { line ->
                val trimmed = line.trim()
                trimmed.isNotEmpty() &&
                    trimmed != "----------" &&
                    sltPrefixes.none { prefix -> trimmed.startsWith(prefix) }
            }

        return buildString {
            if (commandDescription.isNotBlank()) {
                appendLine("Command: $commandDescription")
            }
            appendLine("Exit Code: $exitCode")
            appendLine("Summary: ${parse7zaErrorSummary(output, exitCode)}")
            appendLine()
            appendLine("--- 7za Output Log ---")
            if (filteredLines.isEmpty()) {
                appendLine(output.trim().ifEmpty { "(No output emitted by 7za)" })
            } else {
                filteredLines.takeLast(60).forEach { appendLine(it) }
            }
        }.trim()
    }

    private fun build7zaFailureException(
        operationName: String,
        exitCode: Int,
        output: String,
        commandArgs: List<String> = emptyList()
    ): ArchiveOperationException {
        val isPassword = isPasswordRequiredError(output)
        val summary = parse7zaErrorSummary(output, exitCode)
        val cmdDesc = if (commandArgs.isNotEmpty()) "7za ${commandArgs.joinToString(" ")}" else operationName
        val log = extract7zaErrorLog(output, exitCode, cmdDesc)
        return ArchiveOperationException(
            summary = "$operationName: $summary",
            diagnosticLog = log,
            exitCode = exitCode,
            isPasswordError = isPassword
        )
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
     * Multi-part archives are read-only for in-place modification.
     */
    val MODIFIABLE_ARCHIVE_EXTENSIONS = setOf(
        "7z", "zip", "tar", "wim", "tgz", "tbz2", "txz", "apk"
    )

    /**
     * Check if an archive format can be modified (files added, deleted, renamed).
     */
    fun isModifiableArchive(pathOrExtension: String): Boolean {
        if (isSplitArchivePart(pathOrExtension)) return false
        val lower = pathOrExtension.lowercase()
        if (COMPOUND_ARCHIVE_SUFFIXES.any { lower.endsWith(it) }) return true
        val ext = pathOrExtension.substringAfterLast('.', pathOrExtension).lowercase()
        return ext in MODIFIABLE_ARCHIVE_EXTENSIONS
    }

    /**
     * Check if a file extension is handled by the lib7za native binary (list + extract).
     */
    fun isNativeArchive(extension: String): Boolean {
        val lower = extension.lowercase()
        return lower in NATIVE_ARCHIVE_EXTENSIONS || SPLIT_PART_EXT_REGEX.matches(lower)
    }

    /**
     * Check if a file path ends with a native archive extension (including compound and split forms).
     */
    fun isNativeArchivePath(path: String): Boolean {
        val lower = path.lowercase()
        if (COMPOUND_ARCHIVE_SUFFIXES.any { lower.endsWith(it) }) return true
        if (isSplitArchivePart(path)) return true
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
     *
     * Smart multi-part handling:
     * - Automatically resolves any part file (`.002`, `.part2.rar`, `.r01`, `.z01`, etc.)
     *   to the primary volume (`.001`, `.part1.rar`, `.rar`, `.z01`) in the parent folder.
     * - Verifies that all sibling parts exist in the parent folder; throws an
     *   [ArchiveOperationException] with a clear diagnostic log if any volume is missing.
     * - Keeps multi-part archives in their parent folder so 7za can open all sibling parts.
     */
    suspend fun resolveAccessibleArchivePath(originalPath: String): String = withContext(Dispatchers.IO) {
        val inputFile = File(originalPath)
        val multiPart = resolveMultiPartArchive(inputFile)

        if (multiPart.isMultiPart && multiPart.missingParts.isNotEmpty()) {
            val missingList = multiPart.missingParts.joinToString(", ")
            val parentPath = inputFile.parentFile?.absolutePath ?: ""
            val foundList = multiPart.allParts.joinToString(", ") { it.name }.ifEmpty { "none" }
            val summary = "Missing archive part(s) in parent folder: $missingList"
            val log = buildString {
                appendLine("Selected File: ${inputFile.name}")
                appendLine("Parent Folder: $parentPath")
                appendLine("Primary Volume: ${multiPart.primaryFile.name}")
                appendLine("Found Parts (${multiPart.allParts.size}): $foundList")
                appendLine("Missing Parts (${multiPart.missingParts.size}): $missingList")
                appendLine()
                appendLine("All parts of a split archive must be placed in the same parent folder before opening or extracting.")
            }
            throw ArchiveOperationException(
                summary = summary,
                diagnosticLog = log,
                exitCode = 2,
                isPasswordError = false
            )
        }

        val targetFile = if (multiPart.isMultiPart && multiPart.primaryFile.exists()) {
            multiPart.primaryFile
        } else {
            inputFile
        }
        val targetPath = targetFile.absolutePath

        if (targetFile.isFile && targetFile.canRead() && (multiPart.isMultiPart || tryNativeOpen(targetPath))) {
            android.util.Log.d("PrismArchive", "ArchiveManager: using native path $targetPath (original=$originalPath)")
            return@withContext targetPath
        }

        // Fallback: copy into app cache so 7za always has a plain readable path
        val cacheParent = globalClass.externalCacheDir ?: globalClass.cacheDir
        if (multiPart.isMultiPart) {
            val multiCacheDir = File(cacheParent, "archive_parts_${targetFile.nameWithoutExtension}").apply { mkdirs() }
            for (part in multiPart.allParts) {
                val destPart = File(multiCacheDir, part.name)
                if (!destPart.exists() || destPart.length() != part.length()) {
                    part.inputStream().use { input ->
                        destPart.outputStream().use { output -> input.copyTo(output) }
                    }
                }
            }
            return@withContext File(multiCacheDir, targetFile.name).absolutePath
        }

        val dest = File(cacheParent, "archive_access_${targetFile.name}_${targetFile.length()}")
        if (!dest.exists() || dest.length() != targetFile.length()) {
            android.util.Log.w(
                "PrismArchive",
                "ArchiveManager: native open failed for $targetPath — copying to ${dest.absolutePath}"
            )
            targetFile.inputStream().use { input ->
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
        if (isLz4Archive(accessiblePath)) {
            return@withContext listLz4Archive(accessiblePath, archivePath)
        }

        // 2. Handle Compound TAR formats (.tar.bz2, .tar.zst, .tar.gz, .tar.xz, .tbz2, etc., including .tar.gz.001)
        if (isCompoundArchive(accessiblePath)) {
            return@withContext listCompoundArchive(accessiblePath, password)
        }

        // 3. Standard, single-stream, and multi-part formats handled by lib7za
        val args = mutableListOf("l", "-slt", accessiblePath)
        if (!password.isNullOrEmpty()) {
            args.add("-p$password")
        } else {
            args.add("-p-") // Fail fast on encrypted headers without waiting on stdin
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

        val entries = parseListOutput(result.output, accessiblePath)

        if (!result.success) {
            // If exitCode == 1 (non-fatal warning) and entries were parsed without password error, allow browsing
            if (result.exitCode == 1 && entries.isNotEmpty() && !isPasswordRequiredError(result.output)) {
                android.util.Log.w("PrismArchive", "ArchiveManager: listArchive had warnings (exit 1) but parsed ${entries.size} entries.")
                return@withContext entries
            }
            android.util.Log.e("PrismArchive", "ArchiveManager: listArchive failed. Output: ${result.output}")
            throw build7zaFailureException("Archive listing failed", result.exitCode, result.output, args)
        }

        android.util.Log.d("PrismArchive", "ArchiveManager: listArchive parsed ${entries.size} entries.")
        entries
    }

    private fun listLz4Archive(accessiblePath: String, archivePath: String): List<ArchiveEntry> {
        val file = File(accessiblePath)
        val lower = stripSplitSuffix(archivePath).lowercase()
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
                    throw ArchiveOperationException(
                        summary = "LZ4 archive listing failed: ${e.message ?: "Corrupted LZ4 stream"}",
                        diagnosticLog = e.stackTraceToString()
                    )
                }
            }
            return entries
        } else {
            val archiveFileName = stripSplitSuffix(archivePath.substringAfterLast('/'))
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
                throw build7zaFailureException("Compound archive outer unpack failed", extractResult.exitCode, extractResult.output, args)
            }
            val tarFile = tempDir.listFiles()?.firstOrNull { it.extension.lowercase() == "tar" || it.isFile }
                ?: throw ArchiveOperationException(
                    summary = "No intermediate tar file found in compound archive",
                    diagnosticLog = extract7zaErrorLog(extractResult.output, extractResult.exitCode)
                )

            val listArgs = mutableListOf("l", "-slt", "-p-", tarFile.absolutePath)
            val listResult = NativeBinaryExecutor.run(
                context = globalClass,
                binaryName = "lib7za.so",
                arguments = listArgs
            )
            if (!listResult.success) {
                throw build7zaFailureException("Intermediate tar listing failed", listResult.exitCode, listResult.output, listArgs)
            }
            return parseListOutput(listResult.output, tarFile.absolutePath)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    /**
     * Extract all contents of a native archive (including multi-part archives) to the destination directory.
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

        if (isLz4Archive(accessiblePath)) {
            extractAllLz4(accessiblePath, archivePath, destinationDir, isAborted, onProgress)
            return@withContext
        }

        if (isCompoundArchive(accessiblePath)) {
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
            // Fallback for split .z01/.zip archives if 7za prefers Zip4j on the .zip file
            val multiInfo = resolveMultiPartArchive(File(archivePath))
            val zipMain = multiInfo.allParts.firstOrNull { it.extension.equals("zip", ignoreCase = true) }
            if (multiInfo.isMultiPart && zipMain != null && zipMain.exists()) {
                try {
                    val zf = if (!password.isNullOrEmpty()) {
                        net.lingala.zip4j.ZipFile(zipMain, password.toCharArray())
                    } else {
                        net.lingala.zip4j.ZipFile(zipMain)
                    }
                    zf.use { it.extractAll(destinationDir) }
                    return@withContext
                } catch (_: Exception) {
                    // Fall through to 7za exception
                }
            }

            android.util.Log.e("PrismArchive", "ArchiveManager: extractAll failed. Output: ${result.output}")
            throw build7zaFailureException("Extraction failed", result.exitCode, result.output, args)
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
        val lower = stripSplitSuffix(archivePath).lowercase()
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
            val archiveFileName = stripSplitSuffix(archivePath.substringAfterLast('/'))
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
                throw build7zaFailureException("Compound archive outer unpack failed", outerResult.exitCode, outerResult.output, args)
            }
            val tarFile = tempDir.listFiles()?.firstOrNull { it.extension.lowercase() == "tar" || it.isFile }
                ?: throw ArchiveOperationException(
                    summary = "No intermediate tar file found in compound archive",
                    diagnosticLog = extract7zaErrorLog(outerResult.output, outerResult.exitCode)
                )

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
                throw build7zaFailureException("Intermediate tar extraction failed", tarResult.exitCode, tarResult.output, tarArgs)
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
     * file contents are preserved correctly.
     */
    suspend fun extractMembers(
        archivePath: String,
        internalPaths: List<String>,
        destinationDir: String,
        password: String? = null
    ) = withContext(Dispatchers.IO) {
        if (internalPaths.isEmpty()) return@withContext
        val accessiblePath = resolveAccessibleArchivePath(archivePath)

        if (isLz4Archive(accessiblePath)) {
            extractMembersLz4(accessiblePath, archivePath, internalPaths, destinationDir)
            return@withContext
        }

        if (isCompoundArchive(accessiblePath)) {
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
        if (!isSingleCompressedFile(accessiblePath)) {
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
            throw build7zaFailureException("Member extraction failed", result.exitCode, result.output, args)
        }
    }

    private fun extractMembersLz4(
        accessiblePath: String,
        archivePath: String,
        internalPaths: List<String>,
        destinationDir: String
    ) {
        val file = File(accessiblePath)
        val lower = stripSplitSuffix(archivePath).lowercase()
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
            val archiveFileName = stripSplitSuffix(archivePath.substringAfterLast('/'))
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
                throw build7zaFailureException("Compound outer unpack failed", outerResult.exitCode, outerResult.output, outerArgs)
            }
            val tarFile = tempDir.listFiles()?.firstOrNull { it.extension.lowercase() == "tar" || it.isFile }
                ?: throw ArchiveOperationException(
                    summary = "No intermediate tar file found in compound archive",
                    diagnosticLog = extract7zaErrorLog(outerResult.output, outerResult.exitCode)
                )

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
                throw build7zaFailureException("Intermediate tar extraction failed", tarResult.exitCode, tarResult.output, tarArgs)
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
            if (src.exists() && !isSplitArchivePart(originalPath)) {
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
                throw build7zaFailureException("Delete from archive failed", result.exitCode, result.output, args)
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
                throw build7zaFailureException("Delete from intermediate tar failed", delResult.exitCode, delResult.output, delArgs)
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
            val compArgs = listOf("a", outerFlag, tempOuter.absolutePath, "-mx=5", tempTar.absolutePath)
            val compResult = NativeBinaryExecutor.run(
                context = globalClass,
                binaryName = "lib7za.so",
                arguments = compArgs
            )
            if (!compResult.success) {
                throw build7zaFailureException("Compound recompression failed", compResult.exitCode, compResult.output, compArgs)
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

        // 7z and zip formats support native 'rn' command: 7z rn a.zip old1 new1 old2 new2 ...
        if (ext == "7z" || ext == "zip") {
            val args = mutableListOf("rn", accessiblePath, "-y")
            if (!password.isNullOrEmpty()) {
                args.add("-p$password")
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
            }
            addArgs.addAll(newPaths)
            val addResult = NativeBinaryExecutor.run(
                context = globalClass,
                binaryName = "lib7za.so",
                arguments = addArgs,
                workingDir = stagingDir.absolutePath
            )
            if (!addResult.success) {
                throw build7zaFailureException("Rename (re-add) failed", addResult.exitCode, addResult.output, addArgs)
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
        password: String? = null,
        isAborted: (() -> Boolean)? = null,
        onProgress: ((progressPercent: Float, currentFile: String) -> Unit)? = null
    ) = addOrUpdateMembers(
        archivePath = archivePath,
        items = listOf(Pair(localFile, internalPath)),
        password = password,
        isAborted = isAborted,
        onProgress = onProgress
    )

    /**
     * Add or update multiple files or folders inside an archive in a single batch operation.
     */
    suspend fun addOrUpdateMembers(
        archivePath: String,
        items: List<Pair<File, String>>,
        password: String? = null,
        isAborted: (() -> Boolean)? = null,
        onProgress: ((progressPercent: Float, currentFile: String) -> Unit)? = null
    ) = withContext(Dispatchers.IO) {
        if (items.isEmpty()) return@withContext
        val lowerName = archivePath.lowercase()
        val isCompound = COMPOUND_ARCHIVE_SUFFIXES.any { lowerName.endsWith(it) }

        if (isCompound) {
            addOrUpdateMembersToCompound(archivePath, items)
        } else {
            val accessiblePath = resolveAccessibleArchivePath(archivePath)
            val cacheParent = globalClass.externalCacheDir ?: globalClass.cacheDir
            val stagingDir = File(cacheParent, "add_batch_staging_${UUID.randomUUID()}").apply { mkdirs() }
            try {
                val pathsToAdd = mutableListOf<String>()
                for ((localFile, internalPath) in items) {
                    val stagedTarget = File(stagingDir, internalPath)
                    if (localFile.isDirectory) {
                        stagedTarget.mkdirs()
                    } else {
                        stagedTarget.parentFile?.mkdirs()
                        localFile.copyTo(stagedTarget, overwrite = true)
                    }
                    pathsToAdd.add(internalPath)
                }

                // -bsp1 enables 7za's streaming progress output on stdout.
                val args = mutableListOf("a", accessiblePath, "-y", "-r", "-bsp1")
                if (!password.isNullOrEmpty()) {
                    args.add("-p$password")
                }
                args.addAll(pathsToAdd)
                val result = NativeBinaryExecutor.run(
                    context = globalClass,
                    binaryName = "lib7za.so",
                    arguments = args,
                    workingDir = stagingDir.absolutePath,
                    isAborted = isAborted,
                    onProgressUpdate = onProgress
                )
                if (!result.success && isAborted?.invoke() != true) {
                    throw build7zaFailureException("Batch add/update failed", result.exitCode, result.output, args)
                }
                syncBackIfCached(archivePath, accessiblePath)
            } finally {
                stagingDir.deleteRecursively()
            }
        }
    }

    /**
     * Add or update members in a compound archive (.tar.gz, .tar.bz2, .tar.xz).
     */
    private suspend fun addOrUpdateMembersToCompound(
        archivePath: String,
        items: List<Pair<File, String>>
    ) {
        val cacheParent = globalClass.externalCacheDir ?: globalClass.cacheDir
        val tempDir = File(cacheParent, "compound_add_${UUID.randomUUID()}").apply { mkdirs() }
        val tempTar = File(tempDir, "archive.tar")
        val stagingDir = File(tempDir, "staging").apply { mkdirs() }
        try {
            extractAll(archivePath, tempDir.absolutePath)
            val extractedTar = tempDir.listFiles()?.firstOrNull { it.extension.lowercase() == "tar" }
                ?: throw Exception("Could not extract intermediate tar from $archivePath")
            extractedTar.renameTo(tempTar)

            val pathsToAdd = mutableListOf<String>()
            for ((localFile, internalPath) in items) {
                val stagedTarget = File(stagingDir, internalPath)
                if (localFile.isDirectory) {
                    stagedTarget.mkdirs()
                } else {
                    stagedTarget.parentFile?.mkdirs()
                    localFile.copyTo(stagedTarget, overwrite = true)
                }
                pathsToAdd.add(internalPath)
            }

            val addArgs = mutableListOf("a", tempTar.absolutePath, "-y", "-r")
            addArgs.addAll(pathsToAdd)
            val addResult = NativeBinaryExecutor.run(
                context = globalClass,
                binaryName = "lib7za.so",
                arguments = addArgs,
                workingDir = stagingDir.absolutePath
            )
            if (!addResult.success) {
                throw build7zaFailureException("Add to intermediate tar failed", addResult.exitCode, addResult.output, addArgs)
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
            val compArgs = listOf("a", outerFlag, tempOuter.absolutePath, "-mx=5", tempTar.absolutePath)
            val compResult = NativeBinaryExecutor.run(
                context = globalClass,
                binaryName = "lib7za.so",
                arguments = compArgs
            )
            if (!compResult.success) {
                throw build7zaFailureException("Compound recompress failed", compResult.exitCode, compResult.output, compArgs)
            }
            tempOuter.copyTo(File(archivePath), overwrite = true)
        } finally {
            tempDir.deleteRecursively()
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
            }
            args.add(internalPath)
            val result = NativeBinaryExecutor.run(
                context = globalClass,
                binaryName = "lib7za.so",
                arguments = args,
                workingDir = stagingDir.absolutePath
            )
            if (!result.success) {
                throw build7zaFailureException("Create empty entry failed", result.exitCode, result.output, args)
            }
            syncBackIfCached(archivePath, accessiblePath)
        } finally {
            stagingDir.deleteRecursively()
        }
    }

    /**
     * Compress files/directories into a native archive format using lib7za, optionally splitting
     * into multi-part volumes when [splitVolumeSize] is provided (e.g. "10m", "100m", "1g").
     *
     * Supported output formats: 7z, zip, tar, gz, bz2, xz, wim, tgz, tbz2, txz
     * (determined by [archivePath] extension).
     *
     * @param sourcePaths      List of paths to files or folders to add.
     * @param archivePath      Destination archive path. The extension determines the format.
     * @param password         Optional password (supported for 7z and zip formats only).
     * @param compressionLevel Compression level 0–9 (0=store, 9=ultra). Mapped to 7za -mx= flag.
     * @param splitVolumeSize  Optional volume split specification for 7za `-v` flag (e.g. "10m", "500m", "1g").
     */
    suspend fun compress(
        sourcePaths: List<String>,
        archivePath: String,
        password: String? = null,
        compressionLevel: Int = 5,
        splitVolumeSize: String? = null,
        workingDir: String? = null,
        isAborted: (() -> Boolean)? = null,
        onProgress: ((progressPercent: Float, currentFile: String) -> Unit)? = null
    ) = withContext(Dispatchers.IO) {
        val cleanArchivePath = if (!splitVolumeSize.isNullOrBlank() && archivePath.endsWith(".001")) {
            archivePath.removeSuffix(".001")
        } else {
            archivePath
        }
        val lowerName = cleanArchivePath.lowercase()
        val ext = when {
            lowerName.endsWith(".tar.gz") || lowerName.endsWith(".tgz") -> "tgz"
            lowerName.endsWith(".tar.bz2") || lowerName.endsWith(".tbz2") || lowerName.endsWith(".tbz") -> "tbz2"
            lowerName.endsWith(".tar.xz") || lowerName.endsWith(".txz") -> "txz"
            else -> cleanArchivePath.substringAfterLast('.', "").lowercase()
        }

        when (ext) {
            "tgz", "tbz2", "txz" -> compressCompound(
                sourcePaths = sourcePaths,
                archivePath = cleanArchivePath,
                compoundExt = ext,
                compressionLevel = compressionLevel,
                splitVolumeSize = splitVolumeSize,
                workingDir = workingDir,
                isAborted = isAborted,
                onProgress = onProgress
            )
            else -> compressSimple(
                sourcePaths = sourcePaths,
                archivePath = cleanArchivePath,
                ext = ext,
                password = password,
                compressionLevel = compressionLevel,
                splitVolumeSize = splitVolumeSize,
                workingDir = workingDir,
                isAborted = isAborted,
                onProgress = onProgress
            )
        }
    }

    private suspend fun compressSimple(
        sourcePaths: List<String>,
        archivePath: String,
        ext: String,
        password: String?,
        compressionLevel: Int,
        splitVolumeSize: String? = null,
        workingDir: String? = null,
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

        val args = mutableListOf("a", formatFlag, archivePath, "-bsp1", "-y")
        args.add("-mx=$compressionLevel")

        if (!splitVolumeSize.isNullOrBlank()) {
            args.add("-v${splitVolumeSize.trim().lowercase()}")
        }

        if (!password.isNullOrEmpty() && (ext == "7z" || ext == "zip")) {
            args.add("-p$password")
            if (ext == "7z") {
                args.add("-mhe=on")
            } else if (ext == "zip") {
                args.add("-mem=AES256")
            }
        }

        args.addAll(sourcePaths)

        android.util.Log.d("PrismArchive", "ArchiveManager: compress command=7z ${args.joinToString(" ")}")
        val result = NativeBinaryExecutor.run(
            context = globalClass,
            binaryName = "lib7za.so",
            arguments = args,
            workingDir = workingDir,
            isAborted = isAborted,
            onProgressUpdate = onProgress
        )

        android.util.Log.d("PrismArchive", "ArchiveManager: compress exitCode=${result.exitCode}, success=${result.success}")

        if (isAborted?.invoke() == true) return

        if (!result.success) {
            android.util.Log.e("PrismArchive", "ArchiveManager: compress failed. Output: ${result.output}")
            throw build7zaFailureException("Compression failed", result.exitCode, result.output, args)
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
        splitVolumeSize: String? = null,
        workingDir: String? = null,
        isAborted: (() -> Boolean)? = null,
        onProgress: ((progressPercent: Float, currentFile: String) -> Unit)? = null
    ) {
        val cacheParent = globalClass.externalCacheDir ?: globalClass.cacheDir
        val tarTemp = File(cacheParent, "compress_tar_${System.currentTimeMillis()}.tar")
        try {
            // Step 1: create intermediate tar
            val tarArgs = mutableListOf("a", "-ttar", tarTemp.absolutePath, "-bsp1", "-y")
            tarArgs.addAll(sourcePaths)
            android.util.Log.d("PrismArchive", "ArchiveManager: compressCompound tar step: 7z ${tarArgs.joinToString(" ")}")
            val tarResult = NativeBinaryExecutor.run(
                context = globalClass,
                binaryName = "lib7za.so",
                arguments = tarArgs,
                workingDir = workingDir,
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
                throw build7zaFailureException("Intermediate tar step failed", tarResult.exitCode, tarResult.output, tarArgs)
            }

            // Step 2: compress the tar with the outer format (and optional volume split)
            val outerFlag = when (compoundExt) {
                "tgz" -> "-tgzip"
                "tbz2" -> "-tbzip2"
                "txz" -> "-txz"
                else -> "-tgzip"
            }
            val outerArgs = mutableListOf(
                "a", outerFlag, archivePath, "-mx=$compressionLevel", "-bsp1", "-y"
            )
            if (!splitVolumeSize.isNullOrBlank()) {
                outerArgs.add("-v${splitVolumeSize.trim().lowercase()}")
            }
            outerArgs.add(tarTemp.absolutePath)

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
                throw build7zaFailureException("Outer compression failed", outerResult.exitCode, outerResult.output, outerArgs)
            }
            android.util.Log.d("PrismArchive", "ArchiveManager: Compound compression successful -> $archivePath")
        } finally {
            tarTemp.delete()
        }
    }

    /**
     * Parse the 7za -slt verbose listing output into [ArchiveEntry] objects.
     */
    private fun parseListOutput(output: String, archivePath: String): List<ArchiveEntry> {
        val entries = mutableListOf<ArchiveEntry>()
        // 7za -slt separates property blocks with blank lines; also tolerate \r\n
        val blocks = output.replace("\r\n", "\n").split("\n\n")
        val archiveFileName = archivePath.substringAfterLast('/')
        val strippedArchiveName = stripSplitSuffix(archiveFileName)
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
                        path == archiveFileName || path == strippedArchiveName ||
                        path.endsWith("/$strippedArchiveName")
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
            val innerName = strippedArchiveName.substringBeforeLast('.')
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
