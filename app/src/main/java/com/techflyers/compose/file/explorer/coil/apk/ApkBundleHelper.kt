package com.techflyers.compose.file.explorer.coil.apk

import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.LruCache
import com.techflyers.compose.file.explorer.App.Companion.globalClass
import com.techflyers.compose.file.explorer.R
import com.techflyers.compose.file.explorer.common.drawableToBitmap
import com.techflyers.compose.file.explorer.common.toFormattedDate
import com.techflyers.compose.file.explorer.screen.main.tab.files.misc.ApkInfo
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream

/**
 * Fast icon and metadata extractor for split APK bundles (`.apks`, `.xapk`, `.apkm`).
 *
 * Uses multi-tiered extraction to avoid slow full-archive decompression:
 * 1. Memory + disk icon cache keyed by file path, mtime, and size (< 1 ms).
 * 2. Central-directory lookup (`ZipFile`) for root `icon.png` / `icon.webp` inside `.xapk` / `.apkm` (< 2 ms).
 * 3. Direct in-memory scan of small density split APKs (`*xxxhdpi*`, `*xxhdpi*`, etc.) for raster launcher icons (< 8 ms).
 * 4. Targeted extraction of `base.apk` + non-ABI resource splits (skipping large `.obb` and native `.so` ABI splits)
 *    with `ApplicationInfo.splitSourceDirs` wired so Android's `PackageManager` resolves adaptive/vector/split icons natively.
 */
object ApkBundleHelper {

    data class CachedBundleMeta(
        val appName: String,
        val packageName: String,
        val versionName: String,
        val versionCode: String,
        val minSdkVersion: Int?,
        val targetSdkVersion: Int?,
        val permissions: List<String>
    )

    private val memoryIconCache = object : LruCache<String, Bitmap>(200) {}
    private val memoryMetaCache = object : LruCache<String, CachedBundleMeta>(300) {}

    private fun cacheKey(file: File): String =
        "${file.absolutePath}:${file.lastModified()}:${file.length()}"

    private fun diskIconFile(file: File): File {
        val dir = File(globalClass.cacheDir, "apk_bundle_icons").apply { mkdirs() }
        val safeHash = "${file.absolutePath.hashCode().toUInt()}_${file.lastModified()}_${file.length()}"
        return File(dir, "$safeHash.png")
    }

    /**
     * Quickly extracts the app icon [Bitmap] from a `.apks`, `.xapk`, or `.apkm` bundle file.
     */
    fun loadBundleIconBitmap(bundleFile: File): Bitmap? {
        if (!bundleFile.exists() || !bundleFile.canRead()) return null
        val key = cacheKey(bundleFile)

        memoryIconCache.get(key)?.takeIf { !it.isRecycled }?.let { return it }

        val cachedDiskFile = diskIconFile(bundleFile)
        if (cachedDiskFile.exists() && cachedDiskFile.length() > 0L) {
            BitmapFactory.decodeFile(cachedDiskFile.absolutePath)?.let { bmp ->
                memoryIconCache.put(key, bmp)
                return bmp
            }
        }

        val extracted = extractIconInternal(bundleFile)
        if (extracted != null) {
            memoryIconCache.put(key, extracted)
            runCatching {
                FileOutputStream(cachedDiskFile).use { out ->
                    extracted.compress(Bitmap.CompressFormat.PNG, 90, out)
                }
            }
        }
        return extracted
    }

    /**
     * Quickly extracts `(appName, packageName)` for a bundle file, using cache when available.
     */
    fun loadBundleLabelAndPackage(bundleFile: File): Pair<String, String>? {
        if (!bundleFile.exists() || !bundleFile.canRead()) return null
        val key = cacheKey(bundleFile)
        memoryMetaCache.get(key)?.let { meta ->
            if (meta.appName.isNotBlank() || meta.packageName.isNotBlank()) {
                return meta.appName.ifBlank { bundleFile.nameWithoutExtension } to meta.packageName
            }
        }

        // Fast check for manifest.json / info.json in .xapk / .apkm before extracting base.apk
        runCatching {
            ZipFile(bundleFile).use { zip ->
                parseManifestOrInfoJson(zip)?.let { (name, pkg, _, _) ->
                    if (name.isNotBlank() && pkg.isNotBlank()) {
                        return name to pkg
                    }
                }
            }
        }

        val info = loadBundleApkInfo(bundleFile, "")
        return if (info != null) {
            info.appName to info.packageName
        } else null
    }

    /**
     * Loads complete [ApkInfo] (including icon, app name, package name, version, SDKs, permissions,
     * and installed status) for `ApkPreviewDialog`.
     */
    fun loadBundleApkInfo(bundleFile: File, formattedSize: String): ApkInfo? {
        if (!bundleFile.exists() || !bundleFile.canRead()) return null
        val key = cacheKey(bundleFile)
        val pm = globalClass.packageManager

        var iconBitmap: Bitmap? = memoryIconCache.get(key)?.takeIf { !it.isRecycled }
        val cachedDiskFile = diskIconFile(bundleFile)
        if (iconBitmap == null && cachedDiskFile.exists() && cachedDiskFile.length() > 0L) {
            iconBitmap = BitmapFactory.decodeFile(cachedDiskFile.absolutePath)?.also {
                memoryIconCache.put(key, it)
            }
        }

        var meta = memoryMetaCache.get(key)

        if (meta == null || iconBitmap == null) {
            val tempDir = File(
                globalClass.cacheDir,
                "apk_bundle_parse_${bundleFile.absolutePath.hashCode().toUInt()}_${Thread.currentThread().id}"
            )
            try {
                ZipFile(bundleFile).use { zip ->
                    // 1. Check root icon entry inside bundle (.xapk / .apkm / .apks)
                    if (iconBitmap == null) {
                        iconBitmap = decodeRootIconFromZip(zip)
                    }

                    val jsonMeta = parseManifestOrInfoJson(zip)
                    val apkEntries = zip.entries().asSequence()
                        .filter { !it.isDirectory && it.name.endsWith(".apk", ignoreCase = true) && !it.name.startsWith("__MACOSX") }
                        .toList()

                    if (apkEntries.isNotEmpty()) {
                        val baseEntry = selectBaseApkEntry(apkEntries)
                        // Also select small resource/density splits (skipping large native ABI .so splits)
                        val resourceSplitEntries = apkEntries.filter { entry ->
                            entry.name != baseEntry.name && !isAbiSplitName(entry.name) && entry.size in 1..(25L * 1024 * 1024)
                        }

                        // Try fast in-memory raster icon scan if iconBitmap is still null
                        if (iconBitmap == null) {
                            iconBitmap = scanRasterIconFromApkEntries(zip, resourceSplitEntries + baseEntry)
                        }

                        tempDir.mkdirs()
                        val baseFile = File(tempDir, "base.apk")
                        zip.getInputStream(baseEntry).use { input ->
                            FileOutputStream(baseFile).use { output -> input.copyTo(output) }
                        }

                        val splitPaths = mutableListOf<String>()
                        // If we still need the icon or label from resource splits, extract resource splits too
                        for ((idx, splitEntry) in resourceSplitEntries.withIndex()) {
                            val splitFile = File(tempDir, "split_$idx.apk")
                            runCatching {
                                zip.getInputStream(splitEntry).use { input ->
                                    FileOutputStream(splitFile).use { output -> input.copyTo(output) }
                                }
                                if (splitFile.exists() && splitFile.length() > 0L) {
                                    splitPaths.add(splitFile.absolutePath)
                                }
                            }
                        }

                        val archiveInfo = pm.getPackageArchiveInfo(
                            baseFile.absolutePath,
                            PackageManager.GET_PERMISSIONS
                        )
                        if (archiveInfo != null) {
                            val appInfo = archiveInfo.applicationInfo
                            appInfo?.sourceDir = baseFile.absolutePath
                            appInfo?.publicSourceDir = baseFile.absolutePath
                            if (splitPaths.isNotEmpty()) {
                                appInfo?.splitSourceDirs = splitPaths.toTypedArray()
                                appInfo?.splitPublicSourceDirs = splitPaths.toTypedArray()
                            }

                            if (iconBitmap == null && appInfo != null) {
                                val loadedDrawable = runCatching { appInfo.loadIcon(pm) }.getOrNull()
                                if (loadedDrawable != null && !isDefaultSystemIcon(pm, loadedDrawable, appInfo.icon)) {
                                    iconBitmap = loadedDrawable.drawableToBitmap()
                                }
                            }

                            val rawLabel = runCatching { appInfo?.loadLabel(pm)?.toString() }.getOrNull()
                            val appName = when {
                                !rawLabel.isNullOrBlank() && rawLabel != archiveInfo.packageName -> rawLabel
                                !jsonMeta?.first.isNullOrBlank() -> jsonMeta!!.first
                                !rawLabel.isNullOrBlank() -> rawLabel
                                else -> bundleFile.nameWithoutExtension
                            }
                            val pkgName = archiveInfo.packageName.ifBlank { jsonMeta?.second.orEmpty() }
                            val verName = archiveInfo.versionName
                                ?: jsonMeta?.third?.takeIf { it.isNotBlank() }
                                ?: globalClass.getString(R.string.unknown)
                            val verCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                                archiveInfo.longVersionCode.toString()
                            } else {
                                @Suppress("DEPRECATION")
                                archiveInfo.versionCode.toString()
                            }
                            val permissions = archiveInfo.requestedPermissions?.toList() ?: emptyList()

                            meta = CachedBundleMeta(
                                appName = appName,
                                packageName = pkgName,
                                versionName = verName,
                                versionCode = verCode,
                                minSdkVersion = appInfo?.minSdkVersion,
                                targetSdkVersion = appInfo?.targetSdkVersion,
                                permissions = permissions
                            )
                            memoryMetaCache.put(key, meta)
                        } else if (jsonMeta != null) {
                            meta = CachedBundleMeta(
                                appName = jsonMeta.first.ifBlank { bundleFile.nameWithoutExtension },
                                packageName = jsonMeta.second,
                                versionName = jsonMeta.third.ifBlank { globalClass.getString(R.string.unknown) },
                                versionCode = jsonMeta.fourth.ifBlank { "0" },
                                minSdkVersion = null,
                                targetSdkVersion = null,
                                permissions = emptyList()
                            )
                            memoryMetaCache.put(key, meta)
                        }
                    }
                }
            } catch (_: Exception) {
            } finally {
                runCatching { tempDir.deleteRecursively() }
            }
        }

        val resolvedMeta = meta ?: return null

        // Fallback: if the app is already installed on the device, we can also obtain its icon from PackageManager
        if (iconBitmap == null && resolvedMeta.packageName.isNotBlank()) {
            iconBitmap = runCatching {
                pm.getApplicationIcon(resolvedMeta.packageName).drawableToBitmap()
            }.getOrNull()
        }

        if (iconBitmap != null) {
            memoryIconCache.put(key, iconBitmap)
            if (!cachedDiskFile.exists() || cachedDiskFile.length() == 0L) {
                runCatching {
                    FileOutputStream(cachedDiskFile).use { out ->
                        iconBitmap.compress(Bitmap.CompressFormat.PNG, 90, out)
                    }
                }
            }
        }

        var isInstalled = false
        var installedVersion: String? = null
        var installTime: String? = null
        var lastUpdateTime: String? = null
        if (resolvedMeta.packageName.isNotBlank()) {
            try {
                val installedInfo = pm.getPackageInfo(resolvedMeta.packageName, 0)
                isInstalled = true
                installedVersion = installedInfo.versionName
                installTime = installedInfo.firstInstallTime.toFormattedDate()
                lastUpdateTime = installedInfo.lastUpdateTime.toFormattedDate()
            } catch (_: PackageManager.NameNotFoundException) {
            }
        }

        val iconDrawable: Drawable? = iconBitmap?.let { BitmapDrawable(globalClass.resources, it) }

        return ApkInfo(
            appName = resolvedMeta.appName,
            packageName = resolvedMeta.packageName,
            versionName = resolvedMeta.versionName,
            versionCode = resolvedMeta.versionCode,
            icon = iconDrawable,
            size = formattedSize,
            isInstalled = isInstalled,
            installedVersion = installedVersion,
            minSdkVersion = resolvedMeta.minSdkVersion,
            targetSdkVersion = resolvedMeta.targetSdkVersion,
            permissions = resolvedMeta.permissions,
            installTime = installTime,
            lastUpdateTime = lastUpdateTime
        )
    }

    private fun extractIconInternal(bundleFile: File): Bitmap? {
        return try {
            ZipFile(bundleFile).use { zip ->
                // 1. Instant lookup for root icon.png / icon.webp (.xapk, .apkm, SAI .apks)
                decodeRootIconFromZip(zip)?.let { return it }

                val apkEntries = zip.entries().asSequence()
                    .filter { !it.isDirectory && it.name.endsWith(".apk", ignoreCase = true) && !it.name.startsWith("__MACOSX") }
                    .toList()
                if (apkEntries.isEmpty()) return null

                val baseEntry = selectBaseApkEntry(apkEntries)
                val resourceSplitEntries = apkEntries.filter { entry ->
                    entry.name != baseEntry.name && !isAbiSplitName(entry.name) && entry.size in 1..(25L * 1024 * 1024)
                }

                // 2. Fast in-stream scan of density/resource split APKs and base.apk for raster launcher icons
                val prioritizedForScan = resourceSplitEntries.sortedByDescending { dpiPriority(it.name) } + baseEntry
                scanRasterIconFromApkEntries(zip, prioritizedForScan)?.let { return it }

                // 3. Fallback: extract base.apk + resource splits and resolve via PackageManager (handles <adaptive-icon> & vector drawables)
                val tempDir = File(
                    globalClass.cacheDir,
                    "apk_bundle_icon_${bundleFile.absolutePath.hashCode().toUInt()}_${Thread.currentThread().id}"
                )
                try {
                    tempDir.mkdirs()
                    val baseFile = File(tempDir, "base.apk")
                    zip.getInputStream(baseEntry).use { input ->
                        FileOutputStream(baseFile).use { output -> input.copyTo(output) }
                    }
                    val splitPaths = mutableListOf<String>()
                    for ((idx, splitEntry) in resourceSplitEntries.withIndex()) {
                        val splitFile = File(tempDir, "split_$idx.apk")
                        runCatching {
                            zip.getInputStream(splitEntry).use { input ->
                                FileOutputStream(splitFile).use { output -> input.copyTo(output) }
                            }
                            if (splitFile.exists() && splitFile.length() > 0L) {
                                splitPaths.add(splitFile.absolutePath)
                            }
                        }
                    }

                    val pm = globalClass.packageManager
                    val archiveInfo = pm.getPackageArchiveInfo(baseFile.absolutePath, 0)
                    val appInfo = archiveInfo?.applicationInfo
                    if (appInfo != null) {
                        appInfo.sourceDir = baseFile.absolutePath
                        appInfo.publicSourceDir = baseFile.absolutePath
                        if (splitPaths.isNotEmpty()) {
                            appInfo.splitSourceDirs = splitPaths.toTypedArray()
                            appInfo.splitPublicSourceDirs = splitPaths.toTypedArray()
                        }
                        val drawable = runCatching { appInfo.loadIcon(pm) }.getOrNull()
                        if (drawable != null && !isDefaultSystemIcon(pm, drawable, appInfo.icon)) {
                            drawable.drawableToBitmap()?.let { return it }
                        }
                        if (!archiveInfo.packageName.isNullOrBlank()) {
                            runCatching {
                                pm.getApplicationIcon(archiveInfo.packageName).drawableToBitmap()
                            }.getOrNull()?.let { return it }
                        }
                    }
                    null
                } finally {
                    runCatching { tempDir.deleteRecursively() }
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun decodeRootIconFromZip(zip: ZipFile): Bitmap? {
        val exactNames = listOf("icon.png", "icon.webp", "icon.jpg", "icon.jpeg")
        for (name in exactNames) {
            val entry = zip.getEntry(name) ?: continue
            if (!entry.isDirectory && entry.size != 0L) {
                val bmp = runCatching {
                    zip.getInputStream(entry).use { BitmapFactory.decodeStream(it) }
                }.getOrNull()
                if (bmp != null) return bmp
            }
        }

        val fallbackEntry = zip.entries().asSequence().firstOrNull { entry ->
            !entry.isDirectory &&
                !entry.name.contains('/') &&
                entry.name.lowercase().let { n ->
                    n.contains("icon") && (n.endsWith(".png") || n.endsWith(".webp") || n.endsWith(".jpg"))
                }
        }
        if (fallbackEntry != null) {
            return runCatching {
                zip.getInputStream(fallbackEntry).use { BitmapFactory.decodeStream(it) }
            }.getOrNull()
        }
        return null
    }

    /**
     * Scans inner `.apk` streams for `res/mipmap*` or `res/drawable*` launcher icons (`ic_launcher*` / `app_icon*`).
     * Only scans `.apk` entries <= 35 MB in-stream so huge game base APKs don't block thumbnail loading.
     */
    private fun scanRasterIconFromApkEntries(
        outerZip: ZipFile,
        apkEntries: List<ZipEntry>
    ): Bitmap? {
        var bestFullIconBytes: ByteArray? = null
        var bestFullIconScore = -1

        var bestFgBytes: ByteArray? = null
        var bestFgScore = -1
        var bestBgBytes: ByteArray? = null
        var bestBgScore = -1

        for (apkEntry in apkEntries) {
            // Only stream-scan reasonably sized APKs (density splits are typically 100 KB - 5 MB)
            if (apkEntry.size > 35L * 1024 * 1024) continue

            runCatching {
                outerZip.getInputStream(apkEntry).use { rawStream ->
                    ZipInputStream(rawStream).use { innerZis ->
                        var innerEntry = innerZis.nextEntry
                        while (innerEntry != null) {
                            val path = innerEntry.name.lowercase()
                            if (!innerEntry.isDirectory &&
                                path.startsWith("res/") &&
                                (path.endsWith(".png") || path.endsWith(".webp")) &&
                                !path.endsWith(".9.png")
                            ) {
                                val fileName = path.substringAfterLast('/')
                                val isLauncherCandidate = fileName.startsWith("ic_launcher") ||
                                    fileName.startsWith("app_icon") ||
                                    fileName == "icon.png" ||
                                    fileName == "icon.webp"

                                if (isLauncherCandidate && !fileName.contains("monochrome")) {
                                    val score = iconPathScore(path, fileName)
                                    when {
                                        fileName.contains("foreground") -> {
                                            if (score > bestFgScore) {
                                                bestFgBytes = innerZis.readBytes()
                                                bestFgScore = score
                                            }
                                        }
                                        fileName.contains("background") -> {
                                            if (score > bestBgScore) {
                                                bestBgBytes = innerZis.readBytes()
                                                bestBgScore = score
                                            }
                                        }
                                        else -> {
                                            if (score > bestFullIconScore) {
                                                bestFullIconBytes = innerZis.readBytes()
                                                bestFullIconScore = score
                                            }
                                        }
                                    }
                                }
                            }
                            innerZis.closeEntry()
                            innerEntry = innerZis.nextEntry
                        }
                    }
                }
            }

            // If we already found a high-DPI full launcher icon in a density split, decode and return immediately
            if (bestFullIconBytes != null && bestFullIconScore >= 400) {
                decodeBitmapBytes(bestFullIconBytes!!)?.let { return it }
            }
        }

        if (bestFullIconBytes != null) {
            decodeBitmapBytes(bestFullIconBytes!!)?.let { return it }
        }

        if (bestFgBytes != null) {
            val fg = decodeBitmapBytes(bestFgBytes!!)
            val bg = bestBgBytes?.let { decodeBitmapBytes(it) }
            if (fg != null && bg != null) {
                return runCatching {
                    val size = maxOf(fg.width, bg.width, 192).coerceAtMost(512)
                    val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
                    val canvas = Canvas(out)
                    val scaledBg = Bitmap.createScaledBitmap(bg, size, size, true)
                    val scaledFg = Bitmap.createScaledBitmap(fg, size, size, true)
                    canvas.drawBitmap(scaledBg, 0f, 0f, null)
                    canvas.drawBitmap(scaledFg, 0f, 0f, null)
                    out
                }.getOrDefault(fg)
            }
            if (fg != null) return fg
        }

        return null
    }

    private fun decodeBitmapBytes(bytes: ByteArray): Bitmap? {
        if (bytes.isEmpty()) return null
        return runCatching {
            BitmapFactory.decodeStream(ByteArrayInputStream(bytes))
        }.getOrNull()
    }

    private fun iconPathScore(path: String, fileName: String): Int {
        var score = 0
        if (path.contains("mipmap")) score += 200 else if (path.contains("drawable")) score += 100
        score += when {
            path.contains("xxxhdpi") -> 300
            path.contains("xxhdpi") -> 260
            path.contains("xhdpi") -> 220
            path.contains("hdpi") -> 180
            path.contains("mdpi") -> 140
            path.contains("anydpi") -> 120
            else -> 100
        }
        val baseName = fileName.substringBeforeLast('.')
        score += when (baseName) {
            "ic_launcher" -> 80
            "ic_launcher_round" -> 70
            "app_icon" -> 60
            "icon" -> 50
            else -> 20
        }
        return score
    }

    private fun dpiPriority(entryName: String): Int {
        val lower = entryName.lowercase()
        return when {
            lower.contains("xxxhdpi") -> 6
            lower.contains("xxhdpi") -> 5
            lower.contains("xhdpi") -> 4
            lower.contains("hdpi") -> 3
            lower.contains("mdpi") -> 2
            lower.contains("dpi") -> 1
            else -> 0
        }
    }

    fun selectBaseApkEntry(apkEntries: List<ZipEntry>): ZipEntry {
        apkEntries.firstOrNull {
            val leaf = it.name.substringAfterLast('/').lowercase()
            leaf == "base.apk" || leaf == "base-master.apk"
        }?.let { return it }

        apkEntries.firstOrNull {
            val leaf = it.name.substringAfterLast('/').lowercase()
            !leaf.startsWith("split_") &&
                !leaf.startsWith("config.") &&
                !isAbiSplitName(leaf) &&
                !leaf.contains("dpi")
        }?.let { return it }

        return apkEntries.maxByOrNull { it.size } ?: apkEntries.first()
    }

    private fun isAbiSplitName(name: String): Boolean {
        val lower = name.lowercase()
        return lower.contains("arm64_v8a") ||
            lower.contains("arm64-v8a") ||
            lower.contains("armeabi_v7a") ||
            lower.contains("armeabi-v7a") ||
            lower.contains("armeabi") ||
            lower.contains("x86_64") ||
            lower.contains("x86-64") ||
            Regex("""(^|[._-])x86([._-]|$)""").containsMatchIn(lower)
    }

    private fun isDefaultSystemIcon(pm: PackageManager, drawable: Drawable, iconResId: Int): Boolean {
        if (iconResId == 0) return true
        return false
    }

    /**
     * Parses `manifest.json` (XAPK) or `info.json` (APKM) if present in the bundle root.
     * Returns `(appName, packageName, versionName, versionCode)`.
     */
    private fun parseManifestOrInfoJson(zip: ZipFile): Quadruple<String, String, String, String>? {
        val manifestEntry = zip.getEntry("manifest.json") ?: zip.getEntry("info.json") ?: return null
        if (manifestEntry.isDirectory || manifestEntry.size > 2L * 1024 * 1024) return null
        return runCatching {
            val text = zip.getInputStream(manifestEntry).bufferedReader().use { it.readText() }
            val json = JSONObject(text)
            val appName = json.optString("name")
                .ifBlank { json.optString("app_name") }
                .ifBlank { json.optString("label") }
            val pkgName = json.optString("package_name")
                .ifBlank { json.optString("pname") }
                .ifBlank { json.optString("package") }
            val verName = json.optString("version_name")
                .ifBlank { json.optString("version") }
            val verCode = json.optString("version_code")
                .ifBlank { json.optString("versioncode") }
            Quadruple(appName, pkgName, verName, verCode)
        }.getOrNull()
    }

    data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
}
