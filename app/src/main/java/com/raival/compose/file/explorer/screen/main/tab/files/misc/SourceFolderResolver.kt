package com.raival.compose.file.explorer.screen.main.tab.files.misc

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.provider.MediaStore
import android.util.LruCache
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.Podcasts
import androidx.compose.material.icons.rounded.Screenshot
import androidx.compose.material.icons.rounded.Videocam
import com.raival.compose.file.explorer.App.Companion.globalClass
import com.raival.compose.file.explorer.common.drawableToBitmap
import com.raival.compose.file.explorer.screen.main.tab.files.holder.ContentHolder
import com.raival.compose.file.explorer.screen.main.tab.files.holder.LocalFileHolder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

object SourceFolderResolver {
    private val cache = LruCache<String, SourceFolderInfo>(500)

    @Volatile
    private var installedAppsByLabel: Map<String, ApplicationInfo>? = null

    @Volatile
    private var cameraIconCache: Any? = null

    init {
        // Pre-warm installed apps cache in the background
        CoroutineScope(Dispatchers.IO).launch {
            loadInstalledApps()
        }
    }

    private fun loadInstalledApps(): Map<String, ApplicationInfo> {
        val existing = installedAppsByLabel
        if (existing != null) return existing

        val context = globalClass
        val pm = context.packageManager
        val map = HashMap<String, ApplicationInfo>()

        try {
            val apps = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getInstalledApplications(0)
            }
            for (app in apps) {
                try {
                    val label = app.loadLabel(pm).toString().lowercase().trim()
                    if (label.isNotEmpty() && !map.containsKey(label)) {
                        map[label] = app
                    }
                    val pkg = app.packageName.lowercase().trim()
                    if (!map.containsKey(pkg)) {
                        map[pkg] = app
                    }
                } catch (_: Exception) {
                }
            }
        } catch (_: Exception) {
        }

        installedAppsByLabel = map
        return map
    }

    // Mapping from known folder keywords to candidate Android package names
    private val knownAppPackages = mapOf(
        "whatsapp" to listOf("com.whatsapp", "com.whatsapp.w4b"),
        "telegram" to listOf("org.telegram.messenger", "org.telegram.messenger.web", "org.telegram.plus"),
        "instagram" to listOf("com.instagram.android"),
        "twitter" to listOf("com.twitter.android"),
        "x" to listOf("com.twitter.android"),
        "facebook" to listOf("com.facebook.katana"),
        "messenger" to listOf("com.facebook.orca"),
        "reddit" to listOf("com.reddit.frontpage"),
        "snapchat" to listOf("com.snapchat.android"),
        "tiktok" to listOf("com.zhiliaoapp.musically", "com.ss.android.ugc.trill"),
        "discord" to listOf("com.discord"),
        "pinterest" to listOf("com.pinterest"),
        "linkedin" to listOf("com.linkedin.android"),
        "tumblr" to listOf("com.tumblr"),
        "snapseed" to listOf("com.niksoftware.snapseed"),
        "capcut" to listOf("com.lemon.lvoverseas"),
        "inshot" to listOf("com.camerasideas.instashot"),
        "lightroom" to listOf("com.adobe.lrmobile"),
        "canva" to listOf("com.canva.editor"),
        "picsart" to listOf("com.picsart.studio"),
        "vlc" to listOf("org.videolan.vlc"),
        "spotify" to listOf("com.spotify.music"),
        "kinemaster" to listOf("com.nexstreaming.app.kinemasterfree"),
        "youtube" to listOf("com.google.android.youtube"),
        "chrome" to listOf("com.android.chrome"),
        "firefox" to listOf("org.mozilla.firefox"),
        "opera" to listOf("com.opera.browser"),
        "photos" to listOf("com.google.android.apps.photos")
    )

    // Fallback names for popular apps if uninstalled
    private val fallbackAppNames = mapOf(
        "com.whatsapp" to "WhatsApp",
        "com.whatsapp.w4b" to "WhatsApp Business",
        "org.telegram.messenger" to "Telegram",
        "com.instagram.android" to "Instagram",
        "com.twitter.android" to "X",
        "com.facebook.katana" to "Facebook",
        "com.reddit.frontpage" to "Reddit",
        "com.snapchat.android" to "Snapchat",
        "com.discord" to "Discord",
        "com.pinterest" to "Pinterest",
        "com.zhiliaoapp.musically" to "TikTok",
        "org.videolan.vlc" to "VLC",
        "com.spotify.music" to "Spotify"
    )

    fun resolve(item: ContentHolder): SourceFolderInfo? {
        val path = when (item) {
            is LocalFileHolder -> item.file.absolutePath
            else -> item.uniquePath
        }
        return resolve(path)
    }

    fun resolve(file: File): SourceFolderInfo? {
        return resolve(file.absolutePath)
    }

    fun resolve(filePath: String): SourceFolderInfo? {
        if (filePath.isEmpty()) return null

        val file = File(filePath)
        val parent = file.parentFile ?: return null
        val parentPath = parent.absolutePath

        // Check in-memory cache
        val cached = cache.get(parentPath)
        if (cached != null) {
            return cached
        }

        val result = resolveInternal(parent)
        cache.put(parentPath, result)
        return result
    }

    private fun resolveInternal(parentFolder: File): SourceFolderInfo {
        val pm = globalClass.packageManager
        val normalized = parentFolder.absolutePath.replace('\\', '/')
        val folderName = parentFolder.name

        // 1. Check for Android/media/<pkg>, Android/data/<pkg>, or Android/obb/<pkg>
        val pkgFromAndroid = extractPackageFromAndroidDir(normalized)
        if (pkgFromAndroid != null) {
            val appInfo = getAppInfoForPackage(pm, pkgFromAndroid)
            if (appInfo != null) {
                val label = try {
                    appInfo.loadLabel(pm).toString()
                } catch (_: Exception) {
                    folderName
                }
                val icon = try {
                    appInfo.loadIcon(pm).drawableToBitmap() ?: appInfo.loadIcon(pm)
                } catch (_: Exception) {
                    Icons.Rounded.Folder
                }
                return SourceFolderInfo(
                    folderName = if (folderName.equals(pkgFromAndroid, ignoreCase = true)) label else folderName,
                    icon = icon,
                    appPackage = pkgFromAndroid,
                    isApp = true
                )
            } else {
                // Fallback for uninstalled known package
                val fallbackName = fallbackAppNames[pkgFromAndroid] ?: folderName
                return SourceFolderInfo(
                    folderName = fallbackName,
                    icon = Icons.Rounded.Folder,
                    appPackage = pkgFromAndroid,
                    isApp = true
                )
            }
        }

        // 2. Check path segments against known apps
        val segments = normalized.split('/').filter { it.isNotEmpty() }
        for (segment in segments.reversed()) {
            val segLower = segment.lowercase()
            val candidatePkgs = knownAppPackages[segLower]
            if (candidatePkgs != null) {
                for (pkg in candidatePkgs) {
                    val appInfo = getAppInfoForPackage(pm, pkg)
                    if (appInfo != null) {
                        val icon = try {
                            appInfo.loadIcon(pm).drawableToBitmap() ?: appInfo.loadIcon(pm)
                        } catch (_: Exception) {
                            Icons.Rounded.Folder
                        }
                        return SourceFolderInfo(
                            folderName = folderName,
                            icon = icon,
                            appPackage = pkg,
                            isApp = true
                        )
                    }
                }
            }
        }

        // 3. Check for standard Android directories
        val resolvedStandard = resolveStandardDirectory(normalized, folderName, pm)
        if (resolvedStandard != null) {
            return resolvedStandard
        }

        // 4. Dynamic matching against installed applications
        val appsMap = installedAppsByLabel ?: loadInstalledApps()
        for (segment in segments.reversed()) {
            val segLower = segment.lowercase()
            val matchedApp = appsMap[segLower]
            if (matchedApp != null) {
                val icon = try {
                    matchedApp.loadIcon(pm).drawableToBitmap() ?: matchedApp.loadIcon(pm)
                } catch (_: Exception) {
                    Icons.Rounded.Folder
                }
                val label = try {
                    matchedApp.loadLabel(pm).toString()
                } catch (_: Exception) {
                    folderName
                }
                return SourceFolderInfo(
                    folderName = folderName,
                    icon = icon,
                    appPackage = matchedApp.packageName,
                    isApp = true
                )
            }
        }

        // 5. Generic folder fallback
        return SourceFolderInfo(
            folderName = if (folderName.isEmpty()) "Folder" else folderName,
            icon = Icons.Rounded.Folder,
            appPackage = null,
            isApp = false
        )
    }

    private fun resolveStandardDirectory(
        normalizedPath: String,
        folderName: String,
        pm: PackageManager
    ): SourceFolderInfo? {
        val lower = normalizedPath.lowercase()
        val folderLower = folderName.lowercase()

        // Camera
        if (folderLower == "camera" || lower.endsWith("/dcim/camera") || lower.contains("/camera/")) {
            val camIcon = cameraIconCache ?: resolveCameraIcon(pm).also { cameraIconCache = it }
            return SourceFolderInfo(
                folderName = "Camera",
                icon = camIcon,
                isApp = false
            )
        }

        // Screenshots
        if (folderLower == "screenshots" || lower.contains("/screenshots")) {
            return SourceFolderInfo(
                folderName = "Screenshots",
                icon = Icons.Rounded.Screenshot,
                isApp = false
            )
        }

        // Screen recordings
        if (folderLower.contains("screen record") || folderLower.contains("screenrecorder") || folderLower == "captures") {
            return SourceFolderInfo(
                folderName = "Screen Recordings",
                icon = Icons.Rounded.Videocam,
                isApp = false
            )
        }

        // Downloads
        if (folderLower == "download" || folderLower == "downloads") {
            return SourceFolderInfo(
                folderName = "Download",
                icon = Icons.Rounded.Download,
                isApp = false
            )
        }

        // Bluetooth
        if (folderLower == "bluetooth") {
            return SourceFolderInfo(
                folderName = "Bluetooth",
                icon = Icons.Rounded.Bluetooth,
                isApp = false
            )
        }

        // Music
        if (folderLower == "music") {
            return SourceFolderInfo(
                folderName = "Music",
                icon = Icons.Rounded.MusicNote,
                isApp = false
            )
        }

        // Podcasts
        if (folderLower == "podcasts") {
            return SourceFolderInfo(
                folderName = "Podcasts",
                icon = Icons.Rounded.Podcasts,
                isApp = false
            )
        }

        // Ringtones / Alarms / Notifications
        if (folderLower == "ringtones") {
            return SourceFolderInfo(
                folderName = "Ringtones",
                icon = Icons.Rounded.Notifications,
                isApp = false
            )
        }
        if (folderLower == "alarms") {
            return SourceFolderInfo(
                folderName = "Alarms",
                icon = Icons.Rounded.Alarm,
                isApp = false
            )
        }
        if (folderLower == "notifications") {
            return SourceFolderInfo(
                folderName = "Notifications",
                icon = Icons.Rounded.NotificationsActive,
                isApp = false
            )
        }

        // Documents
        if (folderLower == "documents" || folderLower == "doc") {
            return SourceFolderInfo(
                folderName = "Documents",
                icon = Icons.Rounded.Description,
                isApp = false
            )
        }

        // Recordings / VoiceRecorder
        if (folderLower.contains("voicerecorder") || folderLower == "recordings" || folderLower == "soundrecorder") {
            return SourceFolderInfo(
                folderName = "Recordings",
                icon = Icons.Rounded.Mic,
                isApp = false
            )
        }

        return null
    }

    private fun extractPackageFromAndroidDir(normalizedPath: String): String? {
        val markers = listOf("/android/media/", "/android/data/", "/android/obb/")
        for (marker in markers) {
            val idx = normalizedPath.indexOf(marker, ignoreCase = true)
            if (idx != -1) {
                val sub = normalizedPath.substring(idx + marker.length)
                val pkg = sub.substringBefore('/')
                if (pkg.contains('.')) {
                    return pkg
                }
            }
        }
        return null
    }

    private fun getAppInfoForPackage(pm: PackageManager, packageName: String): ApplicationInfo? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getApplicationInfo(packageName, 0)
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun resolveCameraIcon(pm: PackageManager): Any {
        return try {
            val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
            val resolveInfo = pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
                ?: pm.resolveActivity(intent, 0)
            resolveInfo?.loadIcon(pm)?.drawableToBitmap() ?: Icons.Rounded.PhotoCamera
        } catch (_: Exception) {
            Icons.Rounded.PhotoCamera
        }
    }
}
