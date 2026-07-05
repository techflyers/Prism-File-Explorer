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
    val latexFileType = arrayOf("tex", "latex")

    @JvmField
    val officeFileType = arrayOf("doc", "docx", "xls", "xlsx", "ppt", "pptx")

    @JvmField
    val htmlFileType = arrayOf("html", "htm")

    @JvmField
    val docFileType = arrayOf("doc", "docx")

    @JvmField
    val excelFileType = arrayOf("xls", "xlsx")

    @JvmField
    val pptFileType = arrayOf("ppt", "pptx")

    @JvmField
    val fontFileType = arrayOf("ttf", "otf")

    @JvmField
    val vectorFileType = arrayOf(
        "svg", "ai", "eps", "pdf", "dxf",
        "wmf", "emf", "cdr", "odg", "swf"
    )

    /**
     * All archive-like extensions — used for icon recognition and hasKnownExtension().
     * Covers lib7za pack+unpack formats, unpack-only formats, and common aliases.
     */
    @JvmField
    val archiveFileType = arrayOf(
        // lib7za Pack + Unpack
        "7z", "xz", "bz2", "bzip2", "gz", "gzip", "tar", "zip", "wim",
        // lib7za Unpack Only
        "apfs", "ar", "arj", "cab", "chm", "cpio", "cramfs", "dmg",
        "ext", "fat", "gpt", "hfs", "ihex", "iso", "lzh", "lzma",
        "mbr", "msi", "nsis", "ntfs", "qcow2", "rar", "rpm", "squashfs",
        "udf", "uefi", "vdi", "vhd", "vhdx", "vmdk", "xar", "z",
        // Common aliases / wrappers
        "jar", "war", "ear", "tgz", "tbz2", "txz", "lz", "obb"
    )

    /**
     * Archive extensions that the app can browse (open in the archive viewer).
     * All are routed through lib7za native binary.
     */
    @JvmField
    val supportedArchiveFileType = arrayOf(
        // lib7za Pack + Unpack
        "7z", "xz", "bz2", "bzip2", "gz", "gzip", "tar", "zip", "wim",
        // lib7za Unpack Only
        "apfs", "ar", "arj", "cab", "chm", "cpio", "cramfs", "dmg",
        "ext", "fat", "gpt", "hfs", "ihex", "iso", "lzh", "lzma",
        "mbr", "msi", "nsis", "ntfs", "qcow2", "rar", "rpm", "squashfs",
        "udf", "uefi", "vdi", "vhd", "vhdx", "vmdk", "xar", "z",
        // Common aliases / wrappers
        "jar", "war", "ear", "tgz", "tbz2", "txz", "lz",
        // APK/APKS handled separately via ApkDialog but also zip-browsable
        "apk", "apks"
    )

    /**
     * Formats that lib7za can CREATE (pack), shown in the compression dialog.
     * The string is the output file extension.
     */
    @JvmField
    val nativeCompressFormats = setOf("7z", "zip", "tar", "gz", "bz2", "xz", "wim")

    @JvmField
    val videoFileType = arrayOf(
        "mp4", "mov", "avi", "mkv", "wmv", "m4v", "3gp",
        "webm", "flv", "mpeg", "mpg", "ogv", "mxf", "vob", "ts"
    )

    @JvmField
    val codeFileType = arrayOf(
        javaFileType, "xml", "py", "css", kotlinFileType, "cs", "xml", jsonFileType,
        "js", "ts", "php", "rb", "pl", "sh", "cpp", "c", "h", "swift", "go", "rs",
        "scala", "sql", "r", "ini", "yaml", "yml"
    )

    @JvmField
    val editableFileType = arrayOf(
        "txt", "text", "log", "dsc", "apt", "rtf", "rtx",
        "csv", "tsv", "ini", "conf", "cfg", "nfo", "json", "xml"
    )

    @JvmField
    val imageFileType = arrayOf(
        "png", "jpeg", "jpg", "heic", "tiff", "gif", "webp", svgFileType, "bmp", "raw"
    )

    @JvmField
    val audioFileType = arrayOf(
        "mp3", "4mp", "aup", "ogg", "3ga", "m4b", "wav", "acc",
        "m4a", "flac", "aac", "wma", "aiff", "amr", "midi", "mid", "opus"
    )

    @JvmField
    val apkBundleFileType = arrayOf("apks", "xapk", "apkm")

}