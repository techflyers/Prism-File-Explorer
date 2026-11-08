package com.techflyers.compose.file.explorer.screen.main.tab.files.provider

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.ParcelFileDescriptor
import android.system.Os
import androidx.core.content.pm.PackageInfoCompat
import androidx.exifinterface.media.ExifInterface
import com.techflyers.compose.file.explorer.App.Companion.globalClass
import com.techflyers.compose.file.explorer.common.magika.MagikaFileTypeDetector
import com.techflyers.compose.file.explorer.common.MimeTypeDetector
import com.techflyers.compose.file.explorer.common.resolveUriToPath
import com.techflyers.compose.file.explorer.common.toFormattedSize
import com.techflyers.compose.file.explorer.screen.main.tab.files.holder.ContentHolder
import com.techflyers.compose.file.explorer.screen.main.tab.files.holder.LocalFileHolder
import com.techflyers.compose.file.explorer.screen.main.tab.files.misc.DocumentPageCount
import com.techflyers.compose.file.explorer.screen.main.tab.files.misc.FileMimeType
import com.techflyers.compose.file.explorer.screen.main.tab.files.shizuku.ShizukuFileHolder
import com.techflyers.compose.file.explorer.screen.main.tab.files.shizuku.ShizukuManager
import com.techflyers.compose.file.explorer.screen.main.tab.files.ui.extractAndroidAppPackageName
import com.techflyers.compose.file.explorer.screen.main.tab.files.zip.ArchiveManager
import com.tom_roush.pdfbox.pdmodel.PDDocument
import kotlinx.coroutines.runBlocking
import java.io.File
import java.io.FileInputStream
import java.nio.charset.Charset
import java.nio.file.Files
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.attribute.PosixFileAttributes
import java.nio.file.attribute.PosixFilePermissions
import java.security.MessageDigest
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.CRC32
import java.util.zip.ZipFile
import kotlin.math.roundToInt

data class MetadataField(val label: String, val value: String)
data class MetadataSection(val title: String, val fields: List<MetadataField>)

object ExtendedMetadataProvider {

    private val dateTimeFormatter: SimpleDateFormat
        get() = SimpleDateFormat("yyyy-MM-dd HH:mm:ss z", Locale.getDefault())

    fun collectFromUri(
        context: Context,
        uri: Uri,
        extraFilePath: String? = null,
        displayNameHint: String? = null
    ): List<MetadataSection> {
        val resolvedPath = runCatching { resolveUriToPath(context, uri, extraFilePath) }.getOrDefault("")
        val directFile = resolvedPath.takeIf { it.isNotBlank() }?.let { File(it) }
        if (directFile != null && (directFile.exists() || ShizukuManager.isPrivileged)) {
            val holder: ContentHolder = if (directFile.exists() && directFile.canRead()) {
                LocalFileHolder(directFile)
            } else {
                ShizukuFileHolder.fromPath(directFile.absolutePath)
            }
            val result = collect(holder)
            if (result.isNotEmpty()) return result
        }

        // Fallback: copy content:// stream to a temporary file in cacheDir so all extractors work
        var tempFile: File? = null
        return try {
            val fallbackName = displayNameHint?.takeIf { it.isNotBlank() }
                ?: uri.lastPathSegment?.substringAfterLast('/')
                ?: "inspected_file"
            tempFile = File(context.cacheDir, "meta_inspect_${System.currentTimeMillis()}_$fallbackName")
            context.contentResolver.openInputStream(uri)?.use { input ->
                tempFile.outputStream().use { output -> input.copyTo(output) }
            }
            if (tempFile.exists() && tempFile.length() > 0L) {
                val sections = collectFromFile(
                    file = tempFile,
                    displayPath = resolvedPath.ifBlank { uri.toString() },
                    displayName = fallbackName,
                    holder = null
                )
                sections
            } else {
                listOf(
                    MetadataSection(
                        "General",
                        listOf(
                            MetadataField("Name", fallbackName),
                            MetadataField("URI", uri.toString()),
                            MetadataField("Scheme", uri.scheme ?: "unknown")
                        )
                    )
                )
            }
        } catch (_: Exception) {
            emptyList()
        } finally {
            runCatching { tempFile?.delete() }
        }
    }

    fun collect(holder: ContentHolder): List<MetadataSection> {
        val path = holder.uniquePath
        val localFile = (holder as? LocalFileHolder)?.file ?: File(path)

        if (holder.isFolder || (localFile.exists() && localFile.isDirectory)) {
            return collectFolderMetadata(holder, localFile)
        }

        // If the file is only accessible via Shizuku/Root, copy a temporary snapshot if <= 150 MB
        var tempCopy: File? = null
        return try {
            val readableFile = if (localFile.exists() && localFile.canRead()) {
                localFile
            } else if (ShizukuManager.isPrivileged && holder.size in 1..(150L * 1024 * 1024)) {
                val tmp = File(globalClass.cacheDir, "shizuku_meta_${System.currentTimeMillis()}_${holder.displayName}")
                if (ShizukuManager.copyToLocal(path, tmp) && tmp.exists()) {
                    tempCopy = tmp
                    tmp
                } else {
                    localFile
                }
            } else {
                localFile
            }

            collectFromFile(
                file = readableFile,
                displayPath = path,
                displayName = holder.displayName.ifBlank { localFile.name },
                holder = holder
            )
        } finally {
            runCatching { tempCopy?.delete() }
        }
    }

    private fun collectFolderMetadata(holder: ContentHolder, dir: File): List<MetadataSection> {
        val sections = mutableListOf<MetadataSection>()
        val general = mutableListOf<MetadataField>()
        general += MetadataField("Name", holder.displayName.ifBlank { dir.name.ifBlank { "/" } })
        general += MetadataField("Type", "Directory / Folder")
        general += MetadataField("Full Path", holder.uniquePath)
        dir.parent?.let { general += MetadataField("Parent Path", it) }

        if (dir.exists() && dir.canRead()) {
            val children = dir.listFiles().orEmpty()
            val subFolders = children.count { it.isDirectory }
            val files = children.count { it.isFile }
            val hidden = children.count { it.isHidden || it.name.startsWith(".") }
            val directFilesSize = children.filter { it.isFile }.sumOf { it.length() }
            general += MetadataField("Direct Items", "${children.size} ($subFolders folders, $files files)")
            general += MetadataField("Hidden Items", hidden.toString())
            general += MetadataField("Direct Files Size", "${directFilesSize.toFormattedSize()} (${formatExactBytes(directFilesSize)})")
        } else if (ShizukuManager.isPrivileged) {
            val entries = ShizukuManager.listFiles(holder.uniquePath)
            val subFolders = entries.count { it.isDirectory }
            val files = entries.count { !it.isDirectory }
            val hidden = entries.count { it.name.startsWith(".") }
            val directFilesSize = entries.filter { !it.isDirectory }.sumOf { it.size }
            general += MetadataField("Direct Items", "${entries.size} ($subFolders folders, $files files)")
            general += MetadataField("Hidden Items", hidden.toString())
            general += MetadataField("Direct Files Size", "${directFilesSize.toFormattedSize()} (${formatExactBytes(directFilesSize)})")
        }
        sections += MetadataSection("General", general)

        // If this folder corresponds to an Android app inside Android/data, Android/obb, Android/media, or /data/data
        extractAndroidAppPackageName(holder)?.let { pkg ->
            appPackageDirectorySection(pkg)?.let { sections += it }
        }

        // File System & POSIX section
        fileSystemSection(dir, holder.uniquePath, holder)?.let { sections += it }

        return sections.filter { it.fields.isNotEmpty() }
    }

    private fun collectFromFile(
        file: File,
        displayPath: String,
        displayName: String,
        holder: ContentHolder?
    ): List<MetadataSection> {
        val sections = mutableListOf<MetadataSection>()
        val ext = displayName.substringAfterLast('.', "").lowercase()
        val lowerName = displayName.lowercase()

        // 1. General & Type Identification
        sections += generalFileSection(file, displayPath, displayName, ext)

        // 2. Format-specific Deep Metadata Sections
        when {
            ext in FileMimeType.imageFileType || ext in setOf("heic", "heif", "avif", "dng", "cr2", "nef", "arw", "orf", "rw2", "svg", "ico") -> {
                sections += imageSection(file, ext)
            }
            ext in FileMimeType.videoFileType || ext in FileMimeType.audioFileType ||
                ext in setOf("mov", "qt", "m4v", "ts", "m2ts", "mts", "vob", "ogv", "3gpp", "f4v", "divx", "alac", "ac3", "eac3", "dts", "mka", "ape", "wv") -> {
                val isVideo = ext in FileMimeType.videoFileType || ext in setOf("mov", "qt", "m4v", "ts", "m2ts", "mts", "vob", "ogv", "3gpp", "f4v", "divx")
                sections += mediaStreamsSection(file, isVideo)
            }
        }

        if (ext == "pdf") {
            sections += pdfSection(file)
        }

        if (ext in FileMimeType.docFileType || ext in FileMimeType.excelFileType || ext in FileMimeType.pptFileType ||
            ext in setOf("docx", "xlsx", "pptx", "doc", "xls", "ppt", "odt", "ods", "odp", "rtf", "csv", "tsv")
        ) {
            sections += officeAndSpreadsheetSection(file, ext)
        }

        if (ext in FileMimeType.ebookFileType || ext in FileMimeType.comicFileType || ext in FileMimeType.djvuFileTypes ||
            lowerName.endsWith(".fb2.zip") || ext in setOf("epub", "fb2", "mobi", "azw", "azw3", "cbz", "cbr", "cb7", "cbt", "djvu", "djv")
        ) {
            sections += ebookComicDjvuSection(file, displayName, ext)
        }

        if (holder?.isApk() == true || holder?.isApkBundle() == true ||
            ext in setOf("apk", "xapk", "apks", "apkm", "aab")
        ) {
            sections += apkSection(file, ext)
        }

        if (ext in FileMimeType.archiveFileType || ArchiveManager.isSplitArchivePart(displayName) ||
            ext in setOf("zip", "jar", "7z", "rar", "tar", "gz", "tgz", "bz2", "tbz2", "xz", "txz", "zst", "iso", "lz4", "lzma")
        ) {
            sections += archiveSection(file, displayName)
        }

        if (isTextOrCodeFile(file, ext)) {
            sections += textAndCodeSection(file, ext)
        }

        // 3. File System, Timestamps & POSIX Metadata
        fileSystemSection(file, displayPath, holder)?.let { sections += it }

        // 4. Cryptographic Checksums (MD5, SHA-1, SHA-256, CRC32)
        checksumSection(file)?.let { sections += it }

        return sections.filter { it.fields.isNotEmpty() }
    }

    private fun generalFileSection(
        file: File,
        displayPath: String,
        displayName: String,
        ext: String
    ): MetadataSection {
        val fields = mutableListOf<MetadataField>()
        fields += MetadataField("File Name", displayName)
        if (ext.isNotEmpty()) {
            fields += MetadataField("Extension", ".$ext")
        }
        fields += MetadataField("Full Path", displayPath)
        val parentPath = displayPath.substringBeforeLast('/', "")
        if (parentPath.isNotEmpty()) {
            fields += MetadataField("Parent Folder", parentPath)
        }

        val byteLength = if (file.exists()) file.length() else 0L
        fields += MetadataField("File Size", "${byteLength.toFormattedSize()} (${formatExactBytes(byteLength)})")

        if (file.exists() && file.canRead()) {
            val detectedMime = runCatching { MimeTypeDetector.detect(file)?.mimeType }.getOrNull()
            if (!detectedMime.isNullOrBlank()) {
                fields += MetadataField("MIME Type", detectedMime)
            }
            val magika = runCatching {
                MagikaFileTypeDetector.getCachedResult(file) ?: MagikaFileTypeDetector.detect(file)
            }.getOrNull()
            if (magika != null) {
                fields += MetadataField("AI Content Type (Magika)", "${magika.label} (${magika.mimeType})")
            }

            // Read first 16 bytes for Magic Header signature
            runCatching {
                val buf = ByteArray(16)
                val read = FileInputStream(file).use { it.read(buf) }
                if (read > 0) {
                    val slice = buf.copyOf(read)
                    val hex = slice.take(8).joinToString(" ") { "%02X".format(it) }
                    val ascii = slice.take(8).map { b ->
                        val c = b.toInt() and 0xFF
                        if (c in 32..126) c.toChar() else '.'
                    }.joinToString("")
                    fields += MetadataField("Magic Header (Hex)", "$hex  |$ascii|")
                }
            }
        }

        return MetadataSection("General & Identification", fields)
    }

    private fun fileSystemSection(
        file: File,
        displayPath: String,
        holder: ContentHolder?
    ): MetadataSection? {
        val fields = mutableListOf<MetadataField>()
        val targetFile = File(displayPath)

        if (targetFile.exists()) {
            val lastMod = targetFile.lastModified()
            if (lastMod > 0L) {
                fields += MetadataField("Modified", dateTimeFormatter.format(Date(lastMod)))
            }
            runCatching {
                val attrs = Files.readAttributes(targetFile.toPath(), BasicFileAttributes::class.java)
                val createdMs = attrs.creationTime().toMillis()
                val accessMs = attrs.lastAccessTime().toMillis()
                if (createdMs > 0L) {
                    fields += MetadataField("Created / Birth", dateTimeFormatter.format(Date(createdMs)))
                }
                if (accessMs > 0L) {
                    fields += MetadataField("Last Accessed", dateTimeFormatter.format(Date(accessMs)))
                }
            }
            runCatching {
                val posix = Files.readAttributes(targetFile.toPath(), PosixFileAttributes::class.java)
                val permString = PosixFilePermissions.toString(posix.permissions())
                val octal = permissionsToOctal(permString)
                fields += MetadataField("Permissions", "$permString ($octal)")
                fields += MetadataField("Owner / Group", "${posix.owner().name} : ${posix.group().name}")
            }
            runCatching {
                val stat = Os.lstat(targetFile.absolutePath)
                if (fields.none { it.label == "Permissions" }) {
                    val modeOctal = String.format(Locale.US, "%04o", stat.st_mode and 0xFFF)
                    fields += MetadataField("Mode (Octal)", modeOctal)
                }
                fields += MetadataField("UID / GID", "${stat.st_uid} / ${stat.st_gid}")
                fields += MetadataField("Inode", stat.st_ino.toString())
                fields += MetadataField("Device ID", stat.st_dev.toString())
                fields += MetadataField("Hard Links", stat.st_nlink.toString())
                fields += MetadataField("Allocated Blocks", "${stat.st_blocks} (blksize ${stat.st_blksize} B)")
            }

            val flags = buildList {
                if (targetFile.canRead()) add("Readable")
                if (targetFile.canWrite()) add("Writable")
                if (targetFile.canExecute()) add("Executable")
                if (targetFile.isHidden || targetFile.name.startsWith(".")) add("Hidden")
                if (holder?.isSymbolicLink == true || Files.isSymbolicLink(targetFile.toPath())) add("Symbolic Link")
            }
            if (flags.isNotEmpty()) {
                fields += MetadataField("Access Flags", flags.joinToString(", "))
            }
            val symTarget = holder?.symbolicLinkTarget ?: runCatching {
                if (Files.isSymbolicLink(targetFile.toPath())) Files.readSymbolicLink(targetFile.toPath()).toString() else null
            }.getOrNull()
            if (!symTarget.isNullOrBlank()) {
                fields += MetadataField("Symlink Target", symTarget)
            }
        } else if (ShizukuManager.isPrivileged) {
            holder?.lastModified?.takeIf { it > 0L }?.let {
                fields += MetadataField("Modified", dateTimeFormatter.format(Date(it)))
            }
            val statOut = ShizukuManager.runCommand(
                "stat -c \"%A|%a|%u|%g|%U|%G|%i|%h|%X|%Y|%Z|%C\" ${ShizukuManager.escapeShellArg(displayPath)} 2>/dev/null"
            )?.trim()
            if (!statOut.isNullOrBlank() && statOut.contains("|")) {
                val p = statOut.split("|")
                if (p.size >= 11) {
                    fields += MetadataField("Permissions", "${p[0]} (0${p[1]})")
                    fields += MetadataField("Owner / Group", "${p[4]} (${p[2]}) : ${p[5]} (${p[3]})")
                    fields += MetadataField("Inode", p[6])
                    fields += MetadataField("Hard Links", p[7])
                    p[8].toLongOrNull()?.takeIf { it > 0 }?.let {
                        fields += MetadataField("Last Accessed", dateTimeFormatter.format(Date(it * 1000L)))
                    }
                    p[9].toLongOrNull()?.takeIf { it > 0 }?.let {
                        fields += MetadataField("Modified (stat)", dateTimeFormatter.format(Date(it * 1000L)))
                    }
                    p[10].toLongOrNull()?.takeIf { it > 0 }?.let {
                        fields += MetadataField("Status Changed (ctime)", dateTimeFormatter.format(Date(it * 1000L)))
                    }
                    if (p.size >= 12 && p[11].isNotBlank() && p[11] != "?") {
                        fields += MetadataField("SELinux Context", p[11])
                    }
                }
            }
        }

        return if (fields.isEmpty()) null else MetadataSection("File System & Permissions", fields)
    }

    private fun checksumSection(file: File): MetadataSection? {
        if (!file.exists() || !file.isFile || !file.canRead()) return null
        val size = file.length()
        if (size > 512L * 1024 * 1024) {
            return MetadataSection(
                "Cryptographic Checksums",
                listOf(MetadataField("Note", "Skipped automatic hash calculation for file > 512 MB"))
            )
        }
        return try {
            val md5 = MessageDigest.getInstance("MD5")
            val sha1 = MessageDigest.getInstance("SHA-1")
            val sha256 = MessageDigest.getInstance("SHA-256")
            val crc32 = CRC32()
            val buffer = ByteArray(64 * 1024)
            FileInputStream(file).use { input ->
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    md5.update(buffer, 0, read)
                    sha1.update(buffer, 0, read)
                    sha256.update(buffer, 0, read)
                    crc32.update(buffer, 0, read)
                }
            }
            MetadataSection(
                "Cryptographic Checksums",
                listOf(
                    MetadataField("CRC32", "%08X".format(crc32.value)),
                    MetadataField("MD5", md5.digest().toHex()),
                    MetadataField("SHA-1", sha1.digest().toHex()),
                    MetadataField("SHA-256", sha256.digest().toHex())
                )
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun imageSection(file: File, ext: String): MetadataSection {
        val fields = mutableListOf<MetadataField>()
        if (!file.exists() || !file.canRead()) return MetadataSection("Image & EXIF", fields)

        // 1. Bitmap bounds & color config
        runCatching {
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, opts)
            if (opts.outWidth > 0 && opts.outHeight > 0) {
                val w = opts.outWidth
                val h = opts.outHeight
                val mp = (w.toLong() * h.toLong()) / 1_000_000.0
                val divisor = gcd(w, h).coerceAtLeast(1)
                fields += MetadataField("Dimensions", "$w × $h px")
                fields += MetadataField("Megapixels", String.format(Locale.US, "%.2f MP", mp))
                fields += MetadataField("Aspect Ratio", "${w / divisor}:${h / divisor} (${String.format(Locale.US, "%.2f", w.toDouble() / h)}:1)")
            }
            opts.outMimeType?.takeIf { it.isNotBlank() }?.let {
                fields += MetadataField("Decoded Format", it)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                opts.outConfig?.let { fields += MetadataField("Pixel Config", it.name) }
                opts.outColorSpace?.let { fields += MetadataField("Color Space (ICC)", it.name) }
            }
        }

        // 2. SVG viewBox inspection
        if (ext == "svg") {
            runCatching {
                val head = file.inputStream().bufferedReader().use { it.readText().take(4096) }
                Regex("""viewBox\s*=\s*["']([^"']+)["']""").find(head)?.groupValues?.get(1)?.let {
                    fields += MetadataField("SVG viewBox", it)
                }
                Regex("""width\s*=\s*["']([^"']+)["']""").find(head)?.groupValues?.get(1)?.let {
                    fields += MetadataField("SVG Width", it)
                }
                Regex("""height\s*=\s*["']([^"']+)["']""").find(head)?.groupValues?.get(1)?.let {
                    fields += MetadataField("SVG Height", it)
                }
            }
        }

        // 3. Comprehensive EXIF tags
        runCatching {
            val exif = ExifInterface(file.absolutePath)
            fun add(tag: String, label: String, transform: ((String) -> String)? = null) {
                val raw = exif.getAttribute(tag)?.trim()
                if (!raw.isNullOrEmpty()) {
                    val value = transform?.invoke(raw) ?: raw
                    if (fields.none { it.label == label }) {
                        fields += MetadataField(label, value)
                    }
                }
            }

            if (fields.none { it.label == "Dimensions" }) {
                val w = exif.getAttributeInt(ExifInterface.TAG_IMAGE_WIDTH, 0)
                val h = exif.getAttributeInt(ExifInterface.TAG_IMAGE_LENGTH, 0)
                if (w > 0 && h > 0) {
                    fields += MetadataField("Dimensions", "$w × $h px")
                }
            }

            val orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_UNDEFINED)
            if (orientation != ExifInterface.ORIENTATION_UNDEFINED) {
                val orientDesc = when (orientation) {
                    ExifInterface.ORIENTATION_NORMAL -> "Normal (0°)"
                    ExifInterface.ORIENTATION_ROTATE_90 -> "Rotate 90° CW"
                    ExifInterface.ORIENTATION_ROTATE_180 -> "Rotate 180°"
                    ExifInterface.ORIENTATION_ROTATE_270 -> "Rotate 270° CW"
                    ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> "Flip Horizontal"
                    ExifInterface.ORIENTATION_FLIP_VERTICAL -> "Flip Vertical"
                    else -> "Orientation ($orientation)"
                }
                fields += MetadataField("Orientation", orientDesc)
            }

            add(ExifInterface.TAG_MAKE, "Camera Make")
            add(ExifInterface.TAG_MODEL, "Camera Model")
            add(ExifInterface.TAG_LENS_MAKE, "Lens Make")
            add(ExifInterface.TAG_LENS_MODEL, "Lens Model")
            add(ExifInterface.TAG_SOFTWARE, "Software")
            add(ExifInterface.TAG_ARTIST, "Artist / Author")
            add(ExifInterface.TAG_COPYRIGHT, "Copyright")
            add(ExifInterface.TAG_IMAGE_DESCRIPTION, "Description")
            add(ExifInterface.TAG_USER_COMMENT, "User Comment")

            add(ExifInterface.TAG_DATETIME_ORIGINAL, "Date Taken")
            add(ExifInterface.TAG_DATETIME_DIGITIZED, "Date Digitized")
            add(ExifInterface.TAG_DATETIME, "EXIF Modified Date")
            add(ExifInterface.TAG_OFFSET_TIME_ORIGINAL, "Timezone Offset")

            add(ExifInterface.TAG_F_NUMBER, "Aperture") { "f/$it" }
            add(ExifInterface.TAG_EXPOSURE_TIME, "Exposure Time") { raw ->
                val sec = raw.toDoubleOrNull()
                if (sec != null && sec > 0 && sec < 1.0) {
                    "1/${(1.0 / sec).roundToInt()} s ($raw s)"
                } else {
                    "$raw s"
                }
            }
            add(ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY, "ISO Speed") { "ISO $it" }
            add(ExifInterface.TAG_EXPOSURE_BIAS_VALUE, "Exposure Bias") { "$it EV" }
            add(ExifInterface.TAG_FOCAL_LENGTH, "Focal Length") { raw ->
                if (raw.contains("/")) {
                    val parts = raw.split("/")
                    val num = parts[0].toDoubleOrNull()
                    val den = parts.getOrNull(1)?.toDoubleOrNull()
                    if (num != null && den != null && den > 0) {
                        String.format(Locale.US, "%.1f mm", num / den)
                    } else "$raw mm"
                } else "$raw mm"
            }
            add(ExifInterface.TAG_FOCAL_LENGTH_IN_35MM_FILM, "35mm Focal Equivalent") { "$it mm" }
            add(ExifInterface.TAG_DIGITAL_ZOOM_RATIO, "Digital Zoom")
            add(ExifInterface.TAG_BITS_PER_SAMPLE, "Bits Per Sample")
            add(ExifInterface.TAG_COLOR_SPACE, "EXIF Color Space") {
                when (it) {
                    "1" -> "sRGB"
                    "65535" -> "Uncalibrated"
                    else -> it
                }
            }

            val flash = exif.getAttributeInt(ExifInterface.TAG_FLASH, -1)
            if (flash >= 0) {
                fields += MetadataField("Flash", if ((flash and 1) != 0) "Fired ($flash)" else "Did not fire ($flash)")
            }
            val wb = exif.getAttributeInt(ExifInterface.TAG_WHITE_BALANCE, -1)
            if (wb >= 0) {
                fields += MetadataField("White Balance", if (wb == 0) "Auto" else "Manual")
            }
            val metering = exif.getAttributeInt(ExifInterface.TAG_METERING_MODE, -1)
            if (metering > 0) {
                val meteringName = when (metering) {
                    1 -> "Average"
                    2 -> "Center-Weighted Average"
                    3 -> "Spot"
                    4 -> "Multi-Spot"
                    5 -> "Pattern / Matrix"
                    6 -> "Partial"
                    else -> "Mode $metering"
                }
                fields += MetadataField("Metering Mode", meteringName)
            }

            val latLong = exif.latLong
            if (latLong != null && latLong.size >= 2) {
                fields += MetadataField(
                    "GPS Coordinates",
                    String.format(Locale.US, "%.6f, %.6f", latLong[0], latLong[1])
                )
                val alt = exif.getAltitude(Double.NaN)
                if (!alt.isNaN()) {
                    fields += MetadataField("GPS Altitude", String.format(Locale.US, "%.1f m", alt))
                }
            }
        }

        return MetadataSection("Image & Camera (EXIF)", fields)
    }

    private fun mediaStreamsSection(file: File, isVideo: Boolean): MetadataSection {
        val fields = mutableListOf<MetadataField>()
        if (!file.exists() || !file.canRead()) {
            return MetadataSection(if (isVideo) "Video & Audio Streams" else "Audio Stream & Tags", fields)
        }

        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(file.absolutePath)
            fun extract(key: Int): String? = retriever.extractMetadata(key)?.takeIf { it.isNotBlank() }

            extract(MediaMetadataRetriever.METADATA_KEY_MIMETYPE)?.let {
                fields += MetadataField("Container MIME", it)
            }
            extract(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()?.let { durationMs ->
                fields += MetadataField("Duration", "${formatDuration(durationMs)} ($durationMs ms)")
            }
            extract(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toLongOrNull()?.let { bps ->
                fields += MetadataField("Overall Bitrate", "${bps / 1000} kbps")
            }

            if (isVideo) {
                val w = extract(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull()
                val h = extract(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull()
                if (w != null && h != null && w > 0 && h > 0) {
                    val div = gcd(w, h).coerceAtLeast(1)
                    fields += MetadataField("Resolution", "$w × $h px (${w / div}:${h / div})")
                }
                extract(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.let {
                    fields += MetadataField("Rotation", "$it°")
                }
                extract(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE)?.let {
                    fields += MetadataField("Capture Frame Rate", "$it fps")
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    extract(MediaMetadataRetriever.METADATA_KEY_VIDEO_FRAME_COUNT)?.let {
                        fields += MetadataField("Total Frames", it)
                    }
                }
            }

            // Tags
            extract(MediaMetadataRetriever.METADATA_KEY_TITLE)?.let { fields += MetadataField("Title", it) }
            extract(MediaMetadataRetriever.METADATA_KEY_ARTIST)?.let { fields += MetadataField("Artist", it) }
            extract(MediaMetadataRetriever.METADATA_KEY_ALBUM)?.let { fields += MetadataField("Album", it) }
            extract(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST)?.let { fields += MetadataField("Album Artist", it) }
            extract(MediaMetadataRetriever.METADATA_KEY_COMPOSER)?.let { fields += MetadataField("Composer", it) }
            extract(MediaMetadataRetriever.METADATA_KEY_WRITER)?.let { fields += MetadataField("Writer", it) }
            extract(MediaMetadataRetriever.METADATA_KEY_AUTHOR)?.let { fields += MetadataField("Author", it) }
            extract(MediaMetadataRetriever.METADATA_KEY_GENRE)?.let { fields += MetadataField("Genre", it) }
            extract(MediaMetadataRetriever.METADATA_KEY_YEAR)?.let { fields += MetadataField("Year", it) }
            extract(MediaMetadataRetriever.METADATA_KEY_DATE)?.let { fields += MetadataField("Creation Date", it) }
            extract(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER)?.let { fields += MetadataField("Track Number", it) }
            extract(MediaMetadataRetriever.METADATA_KEY_DISC_NUMBER)?.let { fields += MetadataField("Disc Number", it) }
            extract(MediaMetadataRetriever.METADATA_KEY_LOCATION)?.let { fields += MetadataField("Location (GPS)", it) }

            retriever.embeddedPicture?.let { art ->
                val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(art, 0, art.size, opts)
                fields += MetadataField(
                    "Embedded Artwork",
                    "${opts.outWidth} × ${opts.outHeight} px (${art.size.toLong().toFormattedSize()})"
                )
            }
        } catch (_: Exception) {
        } finally {
            runCatching { retriever.release() }
        }

        // Inspect all elementary tracks using MediaExtractor
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(file.absolutePath)
            val trackCount = extractor.trackCount
            fields += MetadataField("Elementary Tracks", trackCount.toString())

            for (i in 0 until trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getStringOrNull(MediaFormat.KEY_MIME) ?: "unknown"
                val codecs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    format.getStringOrNull(MediaFormat.KEY_CODECS_STRING)
                } else null
                val lang = format.getStringOrNull(MediaFormat.KEY_LANGUAGE)

                when {
                    mime.startsWith("video/") -> {
                        val w = format.getIntOrNull(MediaFormat.KEY_WIDTH)
                        val h = format.getIntOrNull(MediaFormat.KEY_HEIGHT)
                        val fps = format.getFloatOrNull(MediaFormat.KEY_FRAME_RATE)
                            ?: format.getIntOrNull(MediaFormat.KEY_FRAME_RATE)?.toFloat()
                        val bitrate = format.getIntOrNull(MediaFormat.KEY_BIT_RATE)
                        val profile = format.getIntOrNull(MediaFormat.KEY_PROFILE)
                        val level = format.getIntOrNull(MediaFormat.KEY_LEVEL)
                        val colorStandard = format.getIntOrNull(MediaFormat.KEY_COLOR_STANDARD)
                        val colorTransfer = format.getIntOrNull(MediaFormat.KEY_COLOR_TRANSFER)

                        val summary = buildString {
                            append(mime)
                            if (!codecs.isNullOrBlank()) append(" ($codecs)")
                            if (w != null && h != null) append(" • ${w}×${h}")
                            if (fps != null && fps > 0f) append(" @ ${String.format(Locale.US, "%.2f", fps)} fps")
                            if (bitrate != null && bitrate > 0) append(" • ${bitrate / 1000} kbps")
                            if (profile != null) append(" • Profile $profile")
                            if (level != null) append("/Level $level")
                        }
                        fields += MetadataField("Video Track #$i", summary)
                        if (colorStandard != null || colorTransfer != null) {
                            val hdrDesc = when (colorTransfer) {
                                MediaFormat.COLOR_TRANSFER_ST2084 -> "HDR10 / PQ (ST2084)"
                                MediaFormat.COLOR_TRANSFER_HLG -> "HLG HDR"
                                MediaFormat.COLOR_TRANSFER_SDR_VIDEO -> "SDR"
                                MediaFormat.COLOR_TRANSFER_LINEAR -> "Linear"
                                else -> "Transfer $colorTransfer"
                            }
                            fields += MetadataField("Video #$i Color / HDR", "$hdrDesc (Standard ${colorStandard ?: "Default"})")
                        }
                    }

                    mime.startsWith("audio/") -> {
                        val sampleRate = format.getIntOrNull(MediaFormat.KEY_SAMPLE_RATE)
                        val channels = format.getIntOrNull(MediaFormat.KEY_CHANNEL_COUNT)
                        val bitrate = format.getIntOrNull(MediaFormat.KEY_BIT_RATE)
                        val summary = buildString {
                            append(mime)
                            if (!codecs.isNullOrBlank()) append(" ($codecs)")
                            if (sampleRate != null) append(" • $sampleRate Hz")
                            if (channels != null) {
                                val chName = when (channels) {
                                    1 -> "Mono (1ch)"
                                    2 -> "Stereo (2ch)"
                                    6 -> "5.1 Surround (6ch)"
                                    8 -> "7.1 Surround (8ch)"
                                    else -> "${channels}ch"
                                }
                                append(" • $chName")
                            }
                            if (bitrate != null && bitrate > 0) append(" • ${bitrate / 1000} kbps")
                            if (!lang.isNullOrBlank() && lang != "und") append(" • [$lang]")
                        }
                        fields += MetadataField("Audio Track #$i", summary)
                    }

                    else -> {
                        val summary = buildString {
                            append(mime)
                            if (!lang.isNullOrBlank()) append(" [$lang]")
                        }
                        fields += MetadataField("Track #$i", summary)
                    }
                }
            }
        } catch (_: Exception) {
        } finally {
            runCatching { extractor.release() }
        }

        return MetadataSection(if (isVideo) "Video & Audio Streams" else "Audio Stream & Tags", fields)
    }

    private fun pdfSection(file: File): MetadataSection {
        val fields = mutableListOf<MetadataField>()
        if (!file.exists() || !file.canRead()) return MetadataSection("PDF Document", fields)

        // 1. Native PdfRenderer for fast page count and first page dimensions
        runCatching {
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                android.graphics.pdf.PdfRenderer(pfd).use { renderer ->
                    fields += MetadataField("Page Count", renderer.pageCount.toString())
                    if (renderer.pageCount > 0) {
                        renderer.openPage(0).use { page ->
                            val wPt = page.width
                            val hPt = page.height
                            val wIn = wPt / 72.0
                            val hIn = hPt / 72.0
                            val wMm = (wIn * 25.4).roundToInt()
                            val hMm = (hIn * 25.4).roundToInt()
                            fields += MetadataField(
                                "Page Size (Page 1)",
                                "$wPt × $hPt pt ($wMm × $hMm mm / ${String.format(Locale.US, "%.1f × %.1f in", wIn, hIn)})"
                            )
                        }
                    }
                }
            }
        }

        // 2. PDFBox for PDF version, encryption, and document information dictionary
        runCatching {
            PDDocument.load(file).use { doc ->
                if (fields.none { it.label == "Page Count" }) {
                    fields += MetadataField("Page Count", doc.numberOfPages.toString())
                }
                fields += MetadataField("PDF Version", doc.version.toString())
                fields += MetadataField("Encrypted", if (doc.isEncrypted) "Yes" else "No")
                val info = doc.documentInformation
                if (info != null) {
                    info.title?.takeIf { it.isNotBlank() }?.let { fields += MetadataField("Title", it) }
                    info.author?.takeIf { it.isNotBlank() }?.let { fields += MetadataField("Author", it) }
                    info.subject?.takeIf { it.isNotBlank() }?.let { fields += MetadataField("Subject", it) }
                    info.keywords?.takeIf { it.isNotBlank() }?.let { fields += MetadataField("Keywords", it) }
                    info.creator?.takeIf { it.isNotBlank() }?.let { fields += MetadataField("Creator App", it) }
                    info.producer?.takeIf { it.isNotBlank() }?.let { fields += MetadataField("PDF Producer", it) }
                    info.creationDate?.time?.let { fields += MetadataField("PDF Created", dateTimeFormatter.format(it)) }
                    info.modificationDate?.time?.let { fields += MetadataField("PDF Modified", dateTimeFormatter.format(it)) }
                }
            }
        }

        // 3. Fallback header scan for PDF version and encryption if PDFBox failed (e.g. password-protected)
        if (fields.none { it.label == "PDF Version" }) {
            runCatching {
                val header = file.inputStream().bufferedReader().use { it.readLine().orEmpty() }
                if (header.startsWith("%PDF-")) {
                    fields += MetadataField("PDF Version", header.removePrefix("%PDF-").take(5))
                }
            }
        }

        return MetadataSection("PDF Document", fields)
    }

    private fun officeAndSpreadsheetSection(file: File, ext: String): MetadataSection {
        val fields = mutableListOf<MetadataField>()
        if (!file.exists() || !file.canRead()) return MetadataSection("Document & Spreadsheet", fields)

        DocumentPageCount.get(file)?.let {
            fields += MetadataField(it.unit.replaceFirstChar { c -> c.uppercase() }, it.value.toString())
        }

        if (ext in setOf("docx", "xlsx", "pptx")) {
            runCatching {
                ZipFile(file).use { zip ->
                    zip.getEntry("docProps/core.xml")?.let { coreEntry ->
                        val xml = zip.getInputStream(coreEntry).bufferedReader().use { it.readText() }
                        extractXmlTag(xml, "dc:title")?.let { fields += MetadataField("Title", it) }
                        extractXmlTag(xml, "dc:subject")?.let { fields += MetadataField("Subject", it) }
                        extractXmlTag(xml, "dc:creator")?.let { fields += MetadataField("Author / Creator", it) }
                        extractXmlTag(xml, "cp:lastModifiedBy")?.let { fields += MetadataField("Last Modified By", it) }
                        extractXmlTag(xml, "cp:revision")?.let { fields += MetadataField("Revision", it) }
                        extractXmlTag(xml, "cp:keywords")?.let { fields += MetadataField("Keywords", it) }
                        extractXmlTag(xml, "dc:description")?.let { fields += MetadataField("Description", it) }
                        extractXmlTag(xml, "dcterms:created")?.let { fields += MetadataField("Document Created", it) }
                        extractXmlTag(xml, "dcterms:modified")?.let { fields += MetadataField("Document Modified", it) }
                    }
                    zip.getEntry("docProps/app.xml")?.let { appEntry ->
                        val xml = zip.getInputStream(appEntry).bufferedReader().use { it.readText() }
                        extractXmlTag(xml, "Application")?.let { fields += MetadataField("Application", it) }
                        extractXmlTag(xml, "AppVersion")?.let { fields += MetadataField("App Version", it) }
                        extractXmlTag(xml, "Company")?.let { fields += MetadataField("Company", it) }
                        extractXmlTag(xml, "Template")?.let { fields += MetadataField("Template", it) }
                        extractXmlTag(xml, "Pages")?.let { fields += MetadataField("Pages", it) }
                        extractXmlTag(xml, "Slides")?.let { fields += MetadataField("Slides", it) }
                        extractXmlTag(xml, "Words")?.let { fields += MetadataField("Words", it) }
                        extractXmlTag(xml, "Characters")?.let { fields += MetadataField("Characters", it) }
                        extractXmlTag(xml, "Paragraphs")?.let { fields += MetadataField("Paragraphs", it) }
                        extractXmlTag(xml, "Lines")?.let { fields += MetadataField("Lines", it) }
                    }
                    if (ext == "xlsx") {
                        zip.getEntry("xl/workbook.xml")?.let { wbEntry ->
                            val xml = zip.getInputStream(wbEntry).bufferedReader().use { it.readText() }
                            val sheetNames = Regex("""<sheet[^>]*\bname=["']([^"']+)["']""")
                                .findAll(xml)
                                .map { it.groupValues[1] }
                                .toList()
                            if (sheetNames.isNotEmpty()) {
                                fields += MetadataField("Sheets Count", sheetNames.size.toString())
                                fields += MetadataField("Sheet Names", sheetNames.joinToString(", "))
                            }
                        }
                    }
                }
            }
        } else if (ext in setOf("odt", "ods", "odp")) {
            runCatching {
                ZipFile(file).use { zip ->
                    zip.getEntry("meta.xml")?.let { metaEntry ->
                        val xml = zip.getInputStream(metaEntry).bufferedReader().use { it.readText() }
                        extractXmlTag(xml, "dc:title")?.let { fields += MetadataField("Title", it) }
                        extractXmlTag(xml, "meta:initial-creator")?.let { fields += MetadataField("Initial Creator", it) }
                        extractXmlTag(xml, "dc:creator")?.let { fields += MetadataField("Last Modified By", it) }
                        extractXmlTag(xml, "meta:creation-date")?.let { fields += MetadataField("Created", it) }
                        extractXmlTag(xml, "dc:date")?.let { fields += MetadataField("Modified", it) }
                        extractXmlTag(xml, "meta:generator")?.let { fields += MetadataField("Generator", it) }
                    }
                }
            }
        } else if (ext in setOf("csv", "tsv")) {
            runCatching {
                val delimiter = if (ext == "tsv") '\t' else ','
                var rowCount = 0
                var maxCols = 0
                var headerPreview = ""
                file.bufferedReader().useLines { lines ->
                    lines.forEachIndexed { idx, line ->
                        rowCount++
                        val cols = line.count { it == delimiter } + 1
                        if (cols > maxCols) maxCols = cols
                        if (idx == 0) headerPreview = line.take(120)
                    }
                }
                fields += MetadataField("Total Rows", rowCount.toString())
                fields += MetadataField("Columns (Max)", maxCols.toString())
                if (headerPreview.isNotBlank()) {
                    fields += MetadataField("Header Columns", headerPreview)
                }
            }
        }

        return MetadataSection("Office & Document Details", fields)
    }

    private fun ebookComicDjvuSection(file: File, displayName: String, ext: String): MetadataSection {
        val fields = mutableListOf<MetadataField>()
        if (!file.exists() || !file.canRead()) return MetadataSection("E-Book / Comic / DjVu", fields)

        when {
            ext == "epub" -> {
                runCatching {
                    ZipFile(file).use { zip ->
                        val entries = zip.entries().asSequence().toList()
                        val htmlCount = entries.count {
                            val e = it.name.substringAfterLast('.', "").lowercase()
                            e in setOf("xhtml", "html", "htm")
                        }
                        val imgCount = entries.count {
                            val e = it.name.substringAfterLast('.', "").lowercase()
                            e in setOf("jpg", "jpeg", "png", "gif", "webp", "svg")
                        }
                        val opfEntry = entries.firstOrNull { it.name.endsWith(".opf", ignoreCase = true) }
                        if (opfEntry != null) {
                            val opf = zip.getInputStream(opfEntry).bufferedReader().use { it.readText() }
                            extractXmlTag(opf, "dc:title")?.let { fields += MetadataField("Book Title", it) }
                            extractXmlTag(opf, "dc:creator")?.let { fields += MetadataField("Author / Creator", it) }
                            extractXmlTag(opf, "dc:publisher")?.let { fields += MetadataField("Publisher", it) }
                            extractXmlTag(opf, "dc:language")?.let { fields += MetadataField("Language", it) }
                            extractXmlTag(opf, "dc:identifier")?.let { fields += MetadataField("Identifier / ISBN", it) }
                            extractXmlTag(opf, "dc:date")?.let { fields += MetadataField("Publication Date", it) }
                            extractXmlTag(opf, "dc:subject")?.let { fields += MetadataField("Subject / Genre", it) }
                            extractXmlTag(opf, "dc:rights")?.let { fields += MetadataField("Rights", it) }
                            extractXmlTag(opf, "dc:description")?.let {
                                val clean = it.replace(Regex("<[^>]+>"), " ").replace(Regex("\\s+"), " ").trim()
                                if (clean.isNotBlank()) fields += MetadataField("Synopsis", clean.take(280))
                            }
                        }
                        fields += MetadataField("Content Chapters (XHTML)", htmlCount.toString())
                        fields += MetadataField("Embedded Images", imgCount.toString())
                    }
                }
            }

            ext == "fb2" || displayName.lowercase().endsWith(".fb2.zip") -> {
                runCatching {
                    val xml = if (displayName.lowercase().endsWith(".zip")) {
                        ZipFile(file).use { zip ->
                            val entry = zip.entries().asSequence().firstOrNull { !it.isDirectory }
                            entry?.let { zip.getInputStream(it).bufferedReader().use { r -> r.readText().take(32_000) } }.orEmpty()
                        }
                    } else {
                        file.bufferedReader().use { it.readText().take(32_000) }
                    }
                    extractXmlTag(xml, "book-title")?.let { fields += MetadataField("Book Title", it) }
                    val first = extractXmlTag(xml, "first-name").orEmpty()
                    val last = extractXmlTag(xml, "last-name").orEmpty()
                    val author = "$first $last".trim()
                    if (author.isNotBlank()) fields += MetadataField("Author", author)
                    extractXmlTag(xml, "genre")?.let { fields += MetadataField("Genre", it) }
                    extractXmlTag(xml, "lang")?.let { fields += MetadataField("Language", it) }
                    extractXmlTag(xml, "publisher")?.let { fields += MetadataField("Publisher", it) }
                    extractXmlTag(xml, "isbn")?.let { fields += MetadataField("ISBN", it) }
                }
            }

            ext in setOf("cbz", "cbr", "cb7", "cbt") -> {
                fields += MetadataField("Comic Container", ext.uppercase())
                if (ext == "cbz") {
                    runCatching {
                        ZipFile(file).use { zip ->
                            val entries = zip.entries().asSequence().toList()
                            val pages = entries.count {
                                !it.isDirectory && it.name.substringAfterLast('.', "").lowercase() in
                                    setOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "avif")
                            }
                            fields += MetadataField("Comic Pages", pages.toString())
                            entries.firstOrNull { it.name.equals("ComicInfo.xml", ignoreCase = true) }?.let { ci ->
                                val xml = zip.getInputStream(ci).bufferedReader().use { it.readText() }
                                extractXmlTag(xml, "Series")?.let { fields += MetadataField("Series", it) }
                                extractXmlTag(xml, "Title")?.let { fields += MetadataField("Title", it) }
                                extractXmlTag(xml, "Number")?.let { fields += MetadataField("Issue Number", it) }
                                extractXmlTag(xml, "Writer")?.let { fields += MetadataField("Writer", it) }
                                extractXmlTag(xml, "Penciller")?.let { fields += MetadataField("Penciller", it) }
                                extractXmlTag(xml, "Publisher")?.let { fields += MetadataField("Publisher", it) }
                                extractXmlTag(xml, "Genre")?.let { fields += MetadataField("Genre", it) }
                            }
                        }
                    }
                }
            }

            ext in setOf("djvu", "djv") -> {
                runCatching {
                    val header = ByteArray(16)
                    val read = FileInputStream(file).use { it.read(header) }
                    if (read >= 16) {
                        val magic = String(header, 0, 4, Charsets.US_ASCII)
                        val subType = String(header, 12, 4, Charsets.US_ASCII)
                        if (magic == "AT&T") {
                            val typeDesc = when (subType) {
                                "DJVM" -> "Multi-page Bundled DjVu (DJVM)"
                                "DJVU" -> "Single-page DjVu (DJVU)"
                                "DJVI" -> "Shared DjVu Include (DJVI)"
                                else -> "DjVu ($subType)"
                            }
                            fields += MetadataField("DjVu Structure", typeDesc)
                        }
                    }
                }
            }
        }

        return MetadataSection("E-Book / Comic / DjVu Metadata", fields)
    }

    private fun isTextOrCodeFile(file: File, ext: String): Boolean {
        if (ext in FileMimeType.codeFileType || ext in FileMimeType.editableFileType ||
            ext in setOf("txt", "md", "markdown", "html", "htm", "tex", "latex", "bib", "sty", "cls", "json", "xml", "yaml", "yml", "toml", "ini", "cfg", "conf", "log", "sql", "sh", "bash", "zsh", "py", "js", "ts", "kt", "kts", "java", "c", "cpp", "h", "hpp", "rs", "go", "rb", "php", "css", "scss", "less")
        ) {
            return true
        }
        return false
    }

    private fun textAndCodeSection(file: File, ext: String): MetadataSection {
        val fields = mutableListOf<MetadataField>()
        if (!file.exists() || !file.canRead() || file.length() > 32L * 1024 * 1024) {
            return MetadataSection("Text & Source Code Analysis", fields)
        }

        runCatching {
            val bytes = file.readBytes()
            val encoding = detectCharsetAndBom(bytes)
            fields += MetadataField("Character Encoding", encoding)

            val text = String(bytes, Charset.forName("UTF-8"))
            val lines = text.lines()
            val nonEmptyLines = lines.count { it.isNotBlank() }
            val words = text.splitToSequence(Regex("\\s+")).count { it.isNotEmpty() }
            val charsNoSpace = text.count { !it.isWhitespace() }
            val maxLineLen = lines.maxOfOrNull { it.length } ?: 0

            val crlfCount = Regex("\r\n").findAll(text).count()
            val totalLfCount = text.count { it == '\n' }
            val loneLfCount = totalLfCount - crlfCount
            val lineEndingStyle = when {
                crlfCount > 0 && loneLfCount == 0 -> "CRLF (Windows)"
                loneLfCount > 0 && crlfCount == 0 -> "LF (Unix / Android)"
                crlfCount > 0 && loneLfCount > 0 -> "Mixed (LF + CRLF)"
                else -> "Single Line (None)"
            }

            fields += MetadataField("Lines", "${lines.size} ($nonEmptyLines non-empty)")
            fields += MetadataField("Words", NumberFormat.getIntegerInstance().format(words))
            fields += MetadataField("Characters", "${NumberFormat.getIntegerInstance().format(text.length)} (${NumberFormat.getIntegerInstance().format(charsNoSpace)} non-whitespace)")
            fields += MetadataField("Longest Line", "$maxLineLen chars")
            fields += MetadataField("Line Endings", lineEndingStyle)

            when (ext) {
                "md", "markdown" -> {
                    val headings = lines.count { it.trimStart().startsWith("#") }
                    val codeBlocks = lines.count { it.trimStart().startsWith("```") } / 2
                    val links = Regex("""\[[^\]]+\]\([^)]+\)""").findAll(text).count()
                    val tasks = lines.count { it.trimStart().matches(Regex("""^[-*+]\s+\[[ xX]\].*""")) }
                    fields += MetadataField("Markdown Headings", headings.toString())
                    fields += MetadataField("Markdown Links", links.toString())
                    fields += MetadataField("Code Blocks", codeBlocks.toString())
                    if (tasks > 0) fields += MetadataField("Task Items", tasks.toString())
                }

                "html", "htm" -> {
                    Regex("""<title[^>]*>(.*?)</title>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
                        .find(text)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() }?.let {
                            fields += MetadataField("HTML <title>", it)
                        }
                    val links = Regex("""<a\b""", RegexOption.IGNORE_CASE).findAll(text).count()
                    val images = Regex("""<img\b""", RegexOption.IGNORE_CASE).findAll(text).count()
                    val scripts = Regex("""<script\b""", RegexOption.IGNORE_CASE).findAll(text).count()
                    fields += MetadataField("HTML Elements", "$links links, $images images, $scripts scripts")
                }

                "tex", "latex" -> {
                    Regex("""\\documentclass(?:\[[^\]]*\])?\{([^}]+)\}""").find(text)?.groupValues?.get(1)?.let {
                        fields += MetadataField("Document Class", it)
                    }
                    Regex("""\\title\{([^}]+)\}""").find(text)?.groupValues?.get(1)?.let {
                        fields += MetadataField("LaTeX Title", it)
                    }
                    Regex("""\\author\{([^}]+)\}""").find(text)?.groupValues?.get(1)?.let {
                        fields += MetadataField("LaTeX Author", it)
                    }
                    val packages = Regex("""\\usepackage(?:\[[^\]]*\])?\{([^}]+)\}""").findAll(text).count()
                    val sectionsCount = Regex("""\\(part|chapter|section|subsection|subsubsection)\*?\{""").findAll(text).count()
                    fields += MetadataField("Packages (\\usepackage)", packages.toString())
                    fields += MetadataField("Section Headings", sectionsCount.toString())
                }
            }
        }

        return MetadataSection("Text, Code & Markup Statistics", fields)
    }

    private fun apkSection(file: File, ext: String): MetadataSection {
        val fields = mutableListOf<MetadataField>()
        if (!file.exists() || !file.canRead()) return MetadataSection("Android Package (APK)", fields)

        try {
            val pm = globalClass.packageManager
            val flags = PackageManager.GET_PERMISSIONS or
                PackageManager.GET_ACTIVITIES or
                PackageManager.GET_SERVICES or
                PackageManager.GET_RECEIVERS or
                PackageManager.GET_PROVIDERS or
                PackageManager.GET_META_DATA or
                (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) PackageManager.GET_SIGNING_CERTIFICATES else @Suppress("DEPRECATION") PackageManager.GET_SIGNATURES)

            @Suppress("DEPRECATION")
            val info = pm.getPackageArchiveInfo(file.absolutePath, flags)
            if (info != null) {
                val appInfo = info.applicationInfo
                appInfo?.sourceDir = file.absolutePath
                appInfo?.publicSourceDir = file.absolutePath
                val label = appInfo?.loadLabel(pm)?.toString()
                if (!label.isNullOrBlank()) fields += MetadataField("App Name", label)
                fields += MetadataField("Package Name", info.packageName.orEmpty())
                val verCode = PackageInfoCompat.getLongVersionCode(info)
                fields += MetadataField("Version Name", info.versionName ?: "N/A")
                fields += MetadataField("Version Code", verCode.toString())
                appInfo?.minSdkVersion?.let {
                    fields += MetadataField("Min SDK", "API $it (${androidVersionForSdk(it)})")
                }
                appInfo?.targetSdkVersion?.let {
                    fields += MetadataField("Target SDK", "API $it (${androidVersionForSdk(it)})")
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    appInfo?.compileSdkVersion?.takeIf { it > 0 }?.let {
                        fields += MetadataField("Compile SDK", "API $it")
                    }
                }

                fields += MetadataField("Activities", (info.activities?.size ?: 0).toString())
                fields += MetadataField("Services", (info.services?.size ?: 0).toString())
                fields += MetadataField("Receivers", (info.receivers?.size ?: 0).toString())
                fields += MetadataField("Providers", (info.providers?.size ?: 0).toString())
                fields += MetadataField("Permissions", (info.requestedPermissions?.size ?: 0).toString())

                // Signing certificate details
                val certBytes = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    info.signingInfo?.apkContentsSigners?.firstOrNull()?.toByteArray()
                        ?: info.signingInfo?.signingCertificateHistory?.firstOrNull()?.toByteArray()
                } else {
                    @Suppress("DEPRECATION")
                    info.signatures?.firstOrNull()?.toByteArray()
                }
                if (certBytes != null) {
                    runCatching {
                        val cert = CertificateFactory.getInstance("X.509")
                            .generateCertificate(certBytes.inputStream()) as? X509Certificate
                        cert?.subjectX500Principal?.name?.takeIf { it.isNotBlank() }?.let {
                            fields += MetadataField("Signer Subject", it)
                        }
                        cert?.sigAlgName?.let {
                            fields += MetadataField("Signature Algorithm", it)
                        }
                        fields += MetadataField("Cert SHA-256", MessageDigest.getInstance("SHA-256").digest(certBytes).toHex(":"))
                        fields += MetadataField("Cert SHA-1", MessageDigest.getInstance("SHA-1").digest(certBytes).toHex(":"))
                    }
                }
            }

            // Inspect ZIP entries for native ABIs and DEX files
            runCatching {
                ZipFile(file).use { zip ->
                    val abis = mutableSetOf<String>()
                    var dexCount = 0
                    zip.entries().asSequence().forEach { entry ->
                        val name = entry.name
                        if (name.startsWith("lib/") && name.endsWith(".so")) {
                            val abi = name.removePrefix("lib/").substringBefore('/', "")
                            if (abi.isNotEmpty()) abis += abi
                        } else if (!name.contains('/') && name.endsWith(".dex")) {
                            dexCount++
                        }
                    }
                    if (abis.isNotEmpty()) {
                        fields += MetadataField("Native ABIs", abis.sorted().joinToString(", "))
                    }
                    if (dexCount > 0) {
                        fields += MetadataField("DEX Files", dexCount.toString())
                    }
                }
            }
        } catch (_: Exception) {
        }

        return MetadataSection("Android Package (${ext.uppercase()})", fields)
    }

    private fun appPackageDirectorySection(packageName: String): MetadataSection? {
        val fields = mutableListOf<MetadataField>()
        fields += MetadataField("Package Name", packageName)
        try {
            val pm = globalClass.packageManager
            @Suppress("DEPRECATION")
            val pkgInfo = pm.getPackageInfo(
                packageName,
                PackageManager.MATCH_UNINSTALLED_PACKAGES or PackageManager.MATCH_DISABLED_COMPONENTS
            )
            val appInfo = pkgInfo.applicationInfo
            val appLabel = appInfo?.loadLabel(pm)?.toString()
            if (!appLabel.isNullOrBlank()) {
                fields.add(0, MetadataField("Application Name", appLabel))
            }
            val verCode = PackageInfoCompat.getLongVersionCode(pkgInfo)
            fields += MetadataField("Installed Version", "${pkgInfo.versionName ?: "N/A"} ($verCode)")
            appInfo?.uid?.let { fields += MetadataField("App UID", it.toString()) }
            appInfo?.minSdkVersion?.let { fields += MetadataField("Min SDK", "API $it (${androidVersionForSdk(it)})") }
            appInfo?.targetSdkVersion?.let { fields += MetadataField("Target SDK", "API $it (${androidVersionForSdk(it)})") }
            appInfo?.sourceDir?.let { fields += MetadataField("APK Path", it) }
            if (pkgInfo.firstInstallTime > 0L) {
                fields += MetadataField("First Installed", dateTimeFormatter.format(Date(pkgInfo.firstInstallTime)))
            }
            if (pkgInfo.lastUpdateTime > 0L) {
                fields += MetadataField("Last Updated", dateTimeFormatter.format(Date(pkgInfo.lastUpdateTime)))
            }
        } catch (_: Exception) {
            fields += MetadataField("Install Status", "Not currently installed for current user")
        }
        return MetadataSection("Associated Android Application", fields)
    }

    private fun archiveSection(file: File, displayName: String): MetadataSection {
        val fields = mutableListOf<MetadataField>()
        if (!file.exists() || !file.canRead()) return MetadataSection("Archive Details", fields)

        // Check if part of a multi-part archive
        if (ArchiveManager.isSplitArchivePart(displayName)) {
            val multiPart = ArchiveManager.resolveMultiPartArchive(file)
            val firstPart = multiPart.primaryFile
            val allParts = multiPart.allParts
            val combinedBytes = allParts.sumOf { it.length() }
            fields += MetadataField("Multi-Part Volume", "Yes (${allParts.size} parts)")
            fields += MetadataField("First Volume", firstPart.name)
            fields += MetadataField("Combined Volumes Size", "${combinedBytes.toFormattedSize()} (${formatExactBytes(combinedBytes)})")
            if (multiPart.missingParts.isNotEmpty()) {
                fields += MetadataField("Missing Volumes", multiPart.missingParts.joinToString(", "))
            }
        }

        // Try standard ZipFile first, then fallback to ArchiveManager (7za/Commons Compress)
        val populatedViaZip = runCatching {
            ZipFile(file).use { zip ->
                var filesCount = 0
                var foldersCount = 0
                var uncompressed = 0L
                var compressed = 0L
                zip.entries().asSequence().forEach { entry ->
                    if (entry.isDirectory) {
                        foldersCount++
                    } else {
                        filesCount++
                        uncompressed += entry.size.coerceAtLeast(0)
                        compressed += entry.compressedSize.coerceAtLeast(0)
                    }
                }
                fields += MetadataField("Entries", "${filesCount + foldersCount} ($filesCount files, $foldersCount folders)")
                fields += MetadataField("Uncompressed Size", "${uncompressed.toFormattedSize()} (${formatExactBytes(uncompressed)})")
                if (compressed > 0) {
                    fields += MetadataField("Compressed Size", "${compressed.toFormattedSize()} (${formatExactBytes(compressed)})")
                }
                if (uncompressed > 0 && compressed > 0) {
                    val savedPct = ((1.0 - compressed.toDouble() / uncompressed.toDouble()) * 100.0).coerceIn(-100.0, 100.0)
                    fields += MetadataField("Compression Ratio", String.format(Locale.US, "%.1f%% space saved", savedPct))
                }
                true
            }
        }.getOrDefault(false)

        if (!populatedViaZip) {
            runCatching {
                val targetArchive = if (ArchiveManager.isSplitArchivePart(displayName)) {
                    ArchiveManager.resolveMultiPartArchive(file).primaryFile
                } else file
                val entries = runBlocking {
                    ArchiveManager.listArchive(targetArchive.absolutePath)
                }
                val encrypted = entries.any { it.encrypted }
                fields += MetadataField("Password Protected", if (encrypted) "Yes" else "No")
                val filesCount = entries.count { !it.isDirectory }
                val foldersCount = entries.count { it.isDirectory }
                val uncompressed = entries.filter { !it.isDirectory }.sumOf { it.size.coerceAtLeast(0L) }
                fields += MetadataField("Entries", "${entries.size} ($filesCount files, $foldersCount folders)")
                fields += MetadataField("Uncompressed Size", "${uncompressed.toFormattedSize()} (${formatExactBytes(uncompressed)})")
            }.onFailure { ex ->
                if (ex is ArchiveManager.ArchiveOperationException && ex.isPasswordError) {
                    fields += MetadataField("Password Protected", "Yes (Encrypted Header)")
                }
            }
        }

        return MetadataSection("Archive Details", fields)
    }

    private fun extractXmlTag(xml: String, tagName: String): String? {
        val pattern = Regex("<$tagName(?:\\s[^>]*)?>(.*?)</$tagName>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        val value = pattern.find(xml)?.groupValues?.get(1)?.trim() ?: return null
        return value
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .takeIf { it.isNotBlank() }
    }

    private fun detectCharsetAndBom(bytes: ByteArray): String {
        if (bytes.size >= 3 &&
            (bytes[0].toInt() and 0xFF) == 0xEF &&
            (bytes[1].toInt() and 0xFF) == 0xBB &&
            (bytes[2].toInt() and 0xFF) == 0xBF
        ) {
            return "UTF-8 with BOM"
        }
        if (bytes.size >= 2) {
            val b0 = bytes[0].toInt() and 0xFF
            val b1 = bytes[1].toInt() and 0xFF
            if (b0 == 0xFF && b1 == 0xFE) return "UTF-16 LE (with BOM)"
            if (b0 == 0xFE && b1 == 0xFF) return "UTF-16 BE (with BOM)"
        }
        val isPureAscii = bytes.all { (it.toInt() and 0xFF) in 0x09..0x7F }
        return if (isPureAscii) "US-ASCII / UTF-8" else "UTF-8"
    }

    private fun permissionsToOctal(rwx: String): String {
        if (rwx.length < 9) return ""
        fun triad(s: String): Int {
            var v = 0
            if (s[0] == 'r') v += 4
            if (s[1] == 'w') v += 2
            if (s[2] in setOf('x', 's', 't')) v += 1
            return v
        }
        val u = triad(rwx.substring(0, 3))
        val g = triad(rwx.substring(3, 6))
        val o = triad(rwx.substring(6, 9))
        return "0$u$g$o"
    }

    private fun formatExactBytes(bytes: Long): String {
        return "${NumberFormat.getIntegerInstance().format(bytes)} bytes"
    }

    private fun formatDuration(durationMs: Long): String {
        val totalSec = (durationMs / 1000L).coerceAtLeast(0L)
        val hours = totalSec / 3600L
        val minutes = (totalSec % 3600L) / 60L
        val seconds = totalSec % 60L
        val millis = durationMs % 1000L
        return if (hours > 0) {
            String.format(Locale.US, "%02d:%02d:%02d.%03d", hours, minutes, seconds, millis)
        } else {
            String.format(Locale.US, "%02d:%02d.%03d", minutes, seconds, millis)
        }
    }

    private fun androidVersionForSdk(sdk: Int): String = when (sdk) {
        36 -> "Android 16"
        35 -> "Android 15"
        34 -> "Android 14"
        33 -> "Android 13"
        32 -> "Android 12L"
        31 -> "Android 12"
        30 -> "Android 11"
        29 -> "Android 10"
        28 -> "Android 9"
        27 -> "Android 8.1"
        26 -> "Android 8.0"
        25 -> "Android 7.1"
        24 -> "Android 7.0"
        23 -> "Android 6.0"
        22 -> "Android 5.1"
        21 -> "Android 5.0"
        else -> "API $sdk"
    }

    private fun ByteArray.toHex(separator: String = ""): String {
        return joinToString(separator) { "%02x".format(it) }
    }

    private fun MediaFormat.getStringOrNull(key: String): String? =
        runCatching { if (containsKey(key)) getString(key) else null }.getOrNull()

    private fun MediaFormat.getIntOrNull(key: String): Int? =
        runCatching { if (containsKey(key)) getInteger(key) else null }.getOrNull()

    private fun MediaFormat.getFloatOrNull(key: String): Float? =
        runCatching { if (containsKey(key)) getFloat(key) else null }.getOrNull()

    private tailrec fun gcd(a: Int, b: Int): Int = if (b == 0) a else gcd(b, a % b)
}
