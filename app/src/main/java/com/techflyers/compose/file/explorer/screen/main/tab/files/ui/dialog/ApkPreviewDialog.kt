package com.techflyers.compose.file.explorer.screen.main.tab.files.ui.dialog

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Update
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.techflyers.compose.file.explorer.App.Companion.globalClass
import com.techflyers.compose.file.explorer.App.Companion.logger
import com.techflyers.compose.file.explorer.R
import com.techflyers.compose.file.explorer.coil.apk.ApkBundleHelper
import com.techflyers.compose.file.explorer.common.toFormattedDate
import com.techflyers.compose.file.explorer.common.toFormattedSize
import com.techflyers.compose.file.explorer.common.ui.BottomSheetDialog
import com.techflyers.compose.file.explorer.common.ui.Space
import com.techflyers.compose.file.explorer.screen.main.tab.files.FilesTab
import com.techflyers.compose.file.explorer.screen.main.tab.files.holder.LocalFileHolder
import com.techflyers.compose.file.explorer.screen.main.tab.files.misc.ApkInfo
import com.techflyers.compose.file.explorer.screen.main.tab.files.task.ApkBundleInstaller
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApkPreviewDialog(
    show: Boolean,
    tab: FilesTab,
    onDismissRequest: () -> Unit
) {
    val apkFile = if (show) tab.targetFile as? LocalFileHolder else null
    ApkPreviewDialog(
        show = show && apkFile != null,
        apkFile = apkFile,
        onDismissRequest = onDismissRequest
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApkPreviewDialog(
    show: Boolean,
    apkFile: LocalFileHolder?,
    onDismissRequest: () -> Unit
) {
    if (show && apkFile != null) {
        val context = LocalContext.current
        val packageManager = globalClass.packageManager

        var apkInfo by remember(apkFile.uniquePath) { mutableStateOf<ApkInfo?>(null) }
        var isLoading by remember(apkFile.uniquePath) { mutableStateOf(true) }
        var selectedTab by remember(apkFile.uniquePath) { mutableIntStateOf(0) }
        var showExtraDetails by remember(apkFile.uniquePath) { mutableStateOf(false) }

        LaunchedEffect(apkFile.uniquePath) {
            if (!ApkBundleInstaller.isInstalling &&
                ApkBundleInstaller.activeBundlePath != apkFile.file.absolutePath
            ) {
                ApkBundleInstaller.resetStatus()
            }
        }

        LaunchedEffect(apkFile.uniquePath, ApkBundleInstaller.installVersion) {
            if (apkInfo == null) {
                isLoading = true
            }
            try {
                if (apkFile.isApkBundle()) {
                    apkInfo = withContext(Dispatchers.IO) {
                        ApkBundleHelper.loadBundleApkInfo(
                            bundleFile = apkFile.file,
                            formattedSize = apkFile.size.toFormattedSize()
                        )
                    }
                } else {
                    val parsePath = apkFile.uniquePath
                    val archiveInfo = withContext(Dispatchers.IO) {
                        packageManager.getPackageArchiveInfo(
                            parsePath,
                            PackageManager.GET_PERMISSIONS
                        )
                    }

                    if (archiveInfo != null) {
                        archiveInfo.applicationInfo?.sourceDir = parsePath
                        archiveInfo.applicationInfo?.publicSourceDir = parsePath

                        val icon = archiveInfo.applicationInfo?.loadIcon(packageManager)
                        val appName = archiveInfo.applicationInfo?.loadLabel(packageManager).toString()

                        // Check if app is installed
                        var isInstalled = false
                        var installedVersion: String? = null
                        var installTime: String? = null
                        var lastUpdateTime: String? = null

                        try {
                            val installedInfo =
                                packageManager.getPackageInfo(archiveInfo.packageName, 0)
                            isInstalled = true
                            installedVersion = installedInfo.versionName
                            installTime = installedInfo.firstInstallTime.toFormattedDate()
                            lastUpdateTime = installedInfo.lastUpdateTime.toFormattedDate()
                        } catch (_: PackageManager.NameNotFoundException) {
                            // App not installed
                        }

                        val permissions = archiveInfo.requestedPermissions?.toList() ?: emptyList()

                        apkInfo = ApkInfo(
                            appName = appName,
                            packageName = archiveInfo.packageName,
                            versionName = archiveInfo.versionName
                                ?: globalClass.getString(R.string.unknown),
                            versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                                archiveInfo.longVersionCode.toString()
                            } else {
                                archiveInfo.versionCode.toString()
                            },
                            icon = icon,
                            size = apkFile.size.toFormattedSize(),
                            isInstalled = isInstalled,
                            installedVersion = installedVersion,
                            minSdkVersion = archiveInfo.applicationInfo?.minSdkVersion,
                            targetSdkVersion = archiveInfo.applicationInfo?.targetSdkVersion,
                            permissions = permissions,
                            installTime = installTime,
                            lastUpdateTime = lastUpdateTime
                        )
                    }
                }
            } catch (e: Exception) {
                logger.logError(e)
            } finally {
                isLoading = false
            }
        }

        BottomSheetDialog(onDismissRequest = onDismissRequest) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
            ) {
                if (isLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                } else if (apkInfo == null) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Android,
                            contentDescription = null,
                            modifier = Modifier
                                .size(80.dp)
                                .clip(RoundedCornerShape(20.dp)),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Space(12.dp)
                        Text(
                            text = stringResource(R.string.invalid_apk_file),
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Space(12.dp)
                        OutlinedButton(
                            onClick = {
                                globalClass.zipManager.openArchive(apkFile)
                                onDismissRequest()
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                Icons.Default.FolderOpen,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Space(size = 8.dp)
                            Text(stringResource(R.string.explore))
                        }
                    }
                } else {
                    apkInfo?.let { info ->
                        // App Header
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            AsyncImage(
                                modifier = Modifier
                                    .size(80.dp)
                                    .clip(RoundedCornerShape(20.dp)),
                                model = info.icon,
                                filterQuality = FilterQuality.Low,
                                error = painterResource(id = R.drawable.apk_file_placeholder),
                                contentScale = ContentScale.Fit,
                                contentDescription = null
                            )

                            Space(size = 12.dp)

                            Text(
                                text = info.appName,
                                style = MaterialTheme.typography.headlineSmall.copy(
                                    fontWeight = FontWeight.Bold
                                ),
                                textAlign = TextAlign.Center,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )

                            Space(size = 4.dp)

                            Text(
                                text = info.packageName,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            Space(size = 8.dp)

                            Row(
                                modifier = Modifier
                                    .wrapContentWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                AssistChip(
                                    onClick = { },
                                    label = { Text("v${info.versionName}") },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Default.Info,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                )

                                if (info.isInstalled) {
                                    AssistChip(
                                        onClick = { },
                                        label = { Text(stringResource(R.string.installed)) },
                                        leadingIcon = {
                                            Icon(
                                                Icons.Default.CheckCircle,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        },
                                        colors = AssistChipDefaults.assistChipColors(
                                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                                            labelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    )
                                }
                            }
                        }

                        Space(size = 16.dp)

                        val scope = rememberCoroutineScope()
                        // Action buttons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    globalClass.zipManager.openArchive(apkFile)
                                    onDismissRequest()
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    Icons.Default.FolderOpen,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Space(size = 8.dp)
                                Text(stringResource(R.string.explore))
                            }

                            Button(
                                onClick = {
                                    if (apkFile.isApkBundle()) {
                                        scope.launch {
                                            ApkBundleInstaller.install(
                                                context = context,
                                                bundlePath = apkFile.file.absolutePath,
                                                packageNameHint = info.packageName
                                            )
                                        }
                                    } else {
                                        apkFile.open(
                                            context,
                                            anonymous = false,
                                            skipSupportedExtensions = true,
                                            customMimeType = "application/vnd.android.package-archive"
                                        )
                                        onDismissRequest()
                                    }
                                },
                                enabled = !ApkBundleInstaller.isInstalling,
                                modifier = Modifier.weight(1f)
                            ) {
                                if (ApkBundleInstaller.isInstalling &&
                                    ApkBundleInstaller.activeBundlePath == apkFile.file.absolutePath
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.onPrimary
                                    )
                                    Space(size = 8.dp)
                                    Text("Installing…")
                                } else {
                                    Icon(
                                        if (info.isInstalled) Icons.Default.Update else Icons.Default.Download,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Space(size = 8.dp)
                                    Text(
                                        if (info.isInstalled) stringResource(R.string.update) else stringResource(
                                            R.string.install
                                        )
                                    )
                                }
                            }
                        }

                        val showStatusForThisFile = apkFile.isApkBundle() &&
                            ApkBundleInstaller.installState != ApkBundleInstaller.InstallState.IDLE &&
                            (ApkBundleInstaller.activeBundlePath == null ||
                                ApkBundleInstaller.activeBundlePath == apkFile.file.absolutePath)

                        if (showStatusForThisFile) {
                            Space(size = 14.dp)
                            LiveBundleInstallStatusCard(
                                packageName = info.packageName,
                                onRetry = {
                                    scope.launch {
                                        ApkBundleInstaller.install(
                                            context = context,
                                            bundlePath = apkFile.file.absolutePath,
                                            packageNameHint = info.packageName
                                        )
                                    }
                                },
                                onDoneOrDismiss = {
                                    ApkBundleInstaller.resetStatus()
                                }
                            )
                        }
                        Space(size = 12.dp)

                        if (!showExtraDetails) {
                            OutlinedButton(
                                onClick = { showExtraDetails = true },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(stringResource(R.string.properties))
                            }
                        }

                        if (showExtraDetails) {
                            Space(size = 12.dp)
                            // Tab Row
                            TabRow(
                                selectedTabIndex = selectedTab,
                                containerColor = Color.Transparent,
                                indicator = { tabPositions ->
                                    if (selectedTab < tabPositions.size) {
                                        TabRowDefaults.SecondaryIndicator(
                                            modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            ) {
                                Tab(
                                    selected = selectedTab == 0,
                                    onClick = { selectedTab = 0 },
                                    text = { Text(stringResource(R.string.details)) }
                                )
                                Tab(
                                    selected = selectedTab == 1,
                                    onClick = { selectedTab = 1 },
                                    text = { Text(stringResource(R.string.permissions)) }
                                )
                            }

                            Space(size = 16.dp)

                            // Tab Content
                            when (selectedTab) {
                                0 -> DetailsTab(info)
                                1 -> PermissionsTab(info.permissions)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LiveBundleInstallStatusCard(
    packageName: String,
    onRetry: () -> Unit,
    onDoneOrDismiss: () -> Unit
) {
    val context = LocalContext.current
    val state = ApkBundleInstaller.installState
    val phase = ApkBundleInstaller.installPhase
    val status = ApkBundleInstaller.installStatus
    val detail = ApkBundleInstaller.installDetail
    val progress = ApkBundleInstaller.installProgress
    val isIndeterminate = ApkBundleInstaller.isIndeterminate
    val isInstalling = ApkBundleInstaller.isInstalling

    val containerColor = when (state) {
        ApkBundleInstaller.InstallState.SUCCESS -> MaterialTheme.colorScheme.secondaryContainer
        ApkBundleInstaller.InstallState.FAILED -> MaterialTheme.colorScheme.errorContainer
        ApkBundleInstaller.InstallState.CANCELLED -> MaterialTheme.colorScheme.surfaceContainerHigh
        else -> MaterialTheme.colorScheme.primaryContainer
    }
    val contentColor = when (state) {
        ApkBundleInstaller.InstallState.SUCCESS -> MaterialTheme.colorScheme.onSecondaryContainer
        ApkBundleInstaller.InstallState.FAILED -> MaterialTheme.colorScheme.onErrorContainer
        ApkBundleInstaller.InstallState.CANCELLED -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.onPrimaryContainer
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = containerColor,
            contentColor = contentColor
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    when (state) {
                        ApkBundleInstaller.InstallState.SUCCESS -> {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = contentColor
                            )
                        }
                        ApkBundleInstaller.InstallState.FAILED -> {
                            Icon(
                                imageVector = Icons.Default.ErrorOutline,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = contentColor
                            )
                        }
                        ApkBundleInstaller.InstallState.CANCELLED -> {
                            Icon(
                                imageVector = Icons.Default.Cancel,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = contentColor
                            )
                        }
                        else -> {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.2.dp,
                                color = contentColor
                            )
                        }
                    }
                    Space(size = 10.dp)
                    Text(
                        text = phase.ifBlank { "Installing bundle" },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = contentColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (isInstalling && !isIndeterminate) {
                    Space(size = 8.dp)
                    Text(
                        text = "${(progress.coerceIn(0f, 1f) * 100).toInt()}%",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = contentColor
                    )
                }
            }

            if (isInstalling) {
                Space(size = 10.dp)
                if (isIndeterminate) {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = contentColor,
                        trackColor = contentColor.copy(alpha = 0.2f)
                    )
                } else {
                    LinearProgressIndicator(
                        progress = { progress.coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = contentColor,
                        trackColor = contentColor.copy(alpha = 0.2f)
                    )
                }
            }

            if (status.isNotBlank()) {
                Space(size = 10.dp)
                Text(
                    text = status,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = contentColor
                )
            }

            if (detail.isNotBlank()) {
                Space(size = 4.dp)
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = contentColor.copy(alpha = 0.85f)
                )
            }

            if (state == ApkBundleInstaller.InstallState.SUCCESS ||
                state == ApkBundleInstaller.InstallState.FAILED ||
                state == ApkBundleInstaller.InstallState.CANCELLED
            ) {
                Space(size = 12.dp)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDoneOrDismiss) {
                        Text(
                            text = if (state == ApkBundleInstaller.InstallState.SUCCESS) "Done" else "Dismiss",
                            color = contentColor
                        )
                    }

                    if (state == ApkBundleInstaller.InstallState.SUCCESS) {
                        val targetPkg = ApkBundleInstaller.activePackageName?.takeIf { it.isNotBlank() }
                            ?: packageName
                        val launchIntent = remember(targetPkg, ApkBundleInstaller.installVersion) {
                            runCatching {
                                context.packageManager.getLaunchIntentForPackage(targetPkg)
                            }.getOrNull()
                        }
                        if (launchIntent != null) {
                            Space(size = 8.dp)
                            Button(
                                onClick = {
                                    runCatching {
                                        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        context.startActivity(launchIntent)
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Space(size = 6.dp)
                                Text("Open App")
                            }
                        }
                    } else {
                        Space(size = 8.dp)
                        Button(onClick = onRetry) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Space(size = 6.dp)
                            Text("Retry")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.DetailsTab(info: ApkInfo) {
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .weight(1f),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            DetailItem(
                icon = Icons.Default.Badge,
                label = stringResource(R.string.version_code),
                value = info.versionCode
            )
        }

        item {
            DetailItem(
                icon = Icons.Default.Storage,
                label = stringResource(R.string.size),
                value = info.size
            )
        }

        if (info.minSdkVersion != null) {
            item {
                DetailItem(
                    icon = Icons.Default.Android,
                    label = stringResource(R.string.minimum_sdk),
                    value = "API ${info.minSdkVersion}"
                )
            }
        }

        if (info.targetSdkVersion != null) {
            item {
                DetailItem(
                    icon = Icons.Default.GpsFixed,
                    label = stringResource(R.string.target_sdk),
                    value = "API ${info.targetSdkVersion}"
                )
            }
        }

        if (info.isInstalled) {
            item {
                DetailItem(
                    icon = Icons.Default.Update,
                    label = stringResource(R.string.installed_version),
                    value = info.installedVersion ?: globalClass.getString(R.string.unknown)
                )
            }

            info.installTime?.let { time ->
                item {
                    DetailItem(
                        icon = Icons.Default.Schedule,
                        label = stringResource(R.string.install_date),
                        value = time
                    )
                }
            }

            info.lastUpdateTime?.let { time ->
                item {
                    DetailItem(
                        icon = Icons.Default.History,
                        label = stringResource(R.string.last_update),
                        value = time
                    )
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.PermissionsTab(permissions: List<String>) {
    if (permissions.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stringResource(R.string.no_permissions_requested),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    } else {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(permissions) { permission ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Security,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Space(size = 12.dp)
                        Text(
                            text = permission.removePrefix("android.permission."),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailItem(
    icon: ImageVector,
    label: String,
    value: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Space(size = 16.dp)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}
