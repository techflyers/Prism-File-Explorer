package com.raival.compose.file.explorer.screen.main.tab.apps.provider

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import com.raival.compose.file.explorer.App.Companion.globalClass
import com.raival.compose.file.explorer.R
import com.raival.compose.file.explorer.common.emptyString
import com.raival.compose.file.explorer.common.isNot
import com.raival.compose.file.explorer.screen.main.tab.apps.holder.AppHolder
import java.io.File
import java.util.Date

suspend fun getInstalledApps(context: Context): List<AppHolder> {
    val packageManager = context.packageManager
    val packages = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getInstalledPackages(
                PackageManager.PackageInfoFlags.of(
                    PackageManager.GET_PERMISSIONS.toLong()
                )
            )
        } else {
            @Suppress("DEPRECATION")
            packageManager.getInstalledPackages(
                PackageManager.GET_PERMISSIONS
            )
        }
    } catch (_: Exception) {
        // Fallback if bulk query encounters TransactionTooLargeException
        try {
            packageManager.getInstalledApplications(0).mapNotNull { appInfo ->
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        packageManager.getPackageInfo(
                            appInfo.packageName,
                            PackageManager.PackageInfoFlags.of(
                                PackageManager.GET_PERMISSIONS.toLong()
                            )
                        )
                    } else {
                        @Suppress("DEPRECATION")
                        packageManager.getPackageInfo(
                            appInfo.packageName,
                            PackageManager.GET_PERMISSIONS
                        )
                    }
                } catch (_: Exception) { null }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    return packages.mapNotNull { packageInfo ->
        try {
            val appInfo = packageInfo.applicationInfo ?: return@mapNotNull null
            createAppHolder(packageManager, packageInfo, appInfo)
        } catch (_: Exception) {
            null // Skip apps that can't be processed
        }
    }
}

private fun createAppHolder(
    packageManager: PackageManager,
    packageInfo: android.content.pm.PackageInfo,
    appInfo: ApplicationInfo
): AppHolder {
    val appPath = appInfo.sourceDir ?: ""
    val appFile = File(appPath)
    val permissions = packageInfo.requestedPermissions?.toList() ?: emptyList()
    val category = getCategoryName(appInfo.category)

    return AppHolder(
        name = appInfo.loadLabel(packageManager).toString(),
        packageName = appInfo.packageName ?: "",
        path = appPath,
        versionName = packageInfo.versionName ?: globalClass.getString(R.string.unknown),
        versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.longVersionCode.toInt()
        } else {
            @Suppress("DEPRECATION")
            packageInfo.versionCode
        },
        size = if (appPath.isNotEmpty()) appFile.length() else 0L,
        isSystemApp = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) isNot 0,
        installDate = Date(packageInfo.firstInstallTime),
        lastUpdateDate = Date(packageInfo.lastUpdateTime),
        targetSdkVersion = appInfo.targetSdkVersion,
        minSdkVersion = appInfo.minSdkVersion,
        permissions = permissions,
        category = category,
        dataDir = appInfo.dataDir ?: "",
        uid = appInfo.uid,
        enabled = appInfo.enabled,
        debuggable = (appInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) isNot 0
    )
}

private fun getCategoryName(category: Int): String {
    return when (category) {
        ApplicationInfo.CATEGORY_AUDIO -> globalClass.getString(R.string.audio)
        ApplicationInfo.CATEGORY_GAME -> globalClass.getString(R.string.game)
        ApplicationInfo.CATEGORY_IMAGE -> globalClass.getString(R.string.image)
        ApplicationInfo.CATEGORY_MAPS -> globalClass.getString(R.string.maps)
        ApplicationInfo.CATEGORY_NEWS -> globalClass.getString(R.string.news)
        ApplicationInfo.CATEGORY_PRODUCTIVITY -> globalClass.getString(R.string.productivity)
        ApplicationInfo.CATEGORY_SOCIAL -> globalClass.getString(R.string.social)
        ApplicationInfo.CATEGORY_VIDEO -> globalClass.getString(R.string.video)
        else -> emptyString
    }
}