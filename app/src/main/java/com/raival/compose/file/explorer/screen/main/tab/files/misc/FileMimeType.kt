package com.raival.compose.file.explorer.screen.main.tab.files.misc

object FileMimeType {
    const val apkFileType = "apk"
    const val isoFileType = "iso"
    const val pdfFileType = "pdf"
    const val sqlFileType = "sql"
    const val svgFileType = "svg"
    const val javaFileType = "java"
    const val kotlinFileType = "kt"
    const val jsonFileType = "json"
    const val markdownFileType = "md"
    const val xmlFileType = "xml"
    const val prismPrefsFileType = "prismprefs"
    const val anyFileType = "*/*"

    @JvmField
    val latexFileType = setOf("tex", "latex")

    @JvmField
    val officeFileType = setOf("doc", "docx", "xls", "xlsx", "ppt", "pptx")

    @JvmField
    val htmlFileType = setOf("html", "htm")

    @JvmField
    val docFileType = setOf("doc", "docx")

    @JvmField
    val excelFileType = setOf("xls", "xlsx")

    @JvmField
    val pptFileType = setOf("ppt", "pptx")

    @JvmField
    val fontFileType = setOf("ttf", "otf")

    @JvmField
    val documentFileType = setOf(
        pdfFileType,
        "doc", "docx", "dot", "dotx", "docm", "dotm",
        "xls", "xlsx", "xlsm", "xlt", "xltx", "csv", "tsv",
        "ppt", "pptx", "pptm", "pot", "potx", "pps", "ppsx",
        "odt", "ods", "odp", "odg", "odf",
        "txt", "text", "rtf", "rtx", markdownFileType, "markdown",
        "tex", "latex",
        "epub", "mobi", "azw", "azw3", "fb2", "djvu",
        "pages", "numbers", "key", "log", "wps"
    )

    @JvmField
    val vectorFileType = setOf(
        "svg", "ai", "eps", "pdf", "dxf",
        "wmf", "emf", "cdr", "odg", "swf"
    )

    /**
     * All archive-like extensions — used for icon recognition and hasKnownExtension().
     * Covers lib7za pack+unpack formats, unpack-only formats, and common aliases.
     */
    @JvmField
    val archiveFileType = setOf(
        // lib7za Pack + Unpack
        "7z", "xz", "bz2", "bzip2", "gz", "gzip", "tar", "zip", "wim",
        // lib7za Unpack Only
        "apfs", "ar", "arj", "cab", "chm", "cpio", "cramfs", "dmg",
        "ext", "fat", "gpt", "hfs", "ihex", "iso", "lzh", "lzma",
        "mbr", "msi", "nsis", "ntfs", "qcow2", "rar", "rpm", "squashfs",
        "udf", "uefi", "vdi", "vhd", "vhdx", "vmdk", "xar", "z",
        // ZSTD / LZ4
        "zst", "zstd", "tzst", "lz4",
        // Common aliases / wrappers
        "jar", "war", "ear", "tgz", "tbz2", "tbz", "txz", "lz", "obb"
    )

    /**
     * Archive extensions that the app can browse (open in the archive viewer).
     * All are routed through lib7za native binary.
     */
    @JvmField
    val supportedArchiveFileType = setOf(
        // lib7za Pack + Unpack
        "7z", "xz", "bz2", "bzip2", "gz", "gzip", "tar", "zip", "wim",
        // lib7za Unpack Only
        "apfs", "ar", "arj", "cab", "chm", "cpio", "cramfs", "dmg",
        "ext", "fat", "gpt", "hfs", "ihex", "iso", "lzh", "lzma",
        "mbr", "msi", "nsis", "ntfs", "qcow2", "rar", "rpm", "squashfs",
        "udf", "uefi", "vdi", "vhd", "vhdx", "vmdk", "xar", "z",
        // ZSTD / LZ4
        "zst", "zstd", "tzst", "lz4",
        // Common aliases / wrappers
        "jar", "war", "ear", "tgz", "tbz2", "tbz", "txz", "lz",
        // APK/APKS handled separately via ApkDialog but also zip-browsable
        "apk", "apks"
    )

    /**
     * Formats that lib7za can CREATE (pack), shown in the compression dialog.
     * The string is the output file extension.
     */
    @JvmField
    val nativeCompressFormats = setOf("7z", "zip", "tar", "gz", "bz2", "xz", "wim", "tgz", "tbz2", "txz")

    @JvmField
    val videoFileType = setOf(
        "mp4", "mov", "avi", "mkv", "wmv", "m4v", "3gp",
        "webm", "flv", "mpeg", "mpg", "ogv", "mxf", "vob", "ts"
    )

    @JvmField
    val codeFileType = setOf(
        javaFileType, "xml", "py", "css", kotlinFileType, "cs", "xml", jsonFileType,
        "js", "ts", "php", "rb", "pl", "sh", "cpp", "c", "h", "swift", "go", "rs",
        "scala", "sql", "r", "ini", "yaml", "yml"
    )

    @JvmField
    val editableFileType = setOf(
        "txt", "text", "log", "dsc", "apt", "rtf", "rtx",
        "csv", "tsv", "ini", "conf", "cfg", "nfo", "json", "xml"
    )

    @JvmField
    val imageFileType = setOf(
        "png", "jpeg", "jpg", "heic", "tiff", "gif", "webp", svgFileType, "bmp", "raw"
    )

    @JvmField
    val audioFileType = setOf(
        "mp3", "4mp", "aup", "ogg", "3ga", "m4b", "wav", "acc",
        "m4a", "flac", "aac", "wma", "aiff", "amr", "midi", "mid", "opus"
    )

    @JvmField
    val apkBundleFileType = setOf("apks", "xapk", "apkm")

}