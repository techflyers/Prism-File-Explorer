package com.techflyers.compose.file.explorer.screen.main.tab.apps.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.DataUsage
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderZip
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Merge
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Update
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import com.techflyers.compose.file.explorer.common.toFormattedDate
import com.techflyers.compose.file.explorer.common.toFormattedSize
import com.techflyers.compose.file.explorer.common.ui.BottomSheetDialog
import com.techflyers.compose.file.explorer.common.ui.Space
import com.techflyers.compose.file.explorer.screen.main.tab.apps.holder.AppHolder
import com.techflyers.compose.file.explorer.screen.main.tab.files.holder.LocalFileHolder
import com.techflyers.compose.file.explorer.screen.main.tab.files.task.ApkBundleInstaller
import com.techflyers.compose.file.explorer.screen.main.tab.files.task.CopyTask
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppInfoBottomSheet(
    app: AppHolder,
    onDismiss: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var showExtractOptionsDialog by remember { mutableStateOf(false) }
    var isExtractingBundle by remember { mutableStateOf(false) }
    var extractProgress by remember { mutableFloatStateOf(0f) }
    var extractStatusText by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    val cleanBaseFileName = remember(app.name, app.versionName) {
        val safeName = app.name.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim().ifEmpty { app.packageName }
        val safeVer = app.versionName.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim()
        if (safeVer.isNotEmpty()) "${safeName}_v${safeVer}" else safeName
    }

    if (showExtractOptionsDialog) {
        AlertDialog(
            onDismissRequest = {
                if (!isExtractingBundle) showExtractOptionsDialog = false
            },
            title = {
                Text(
                    text = if (isExtractingBundle) "Preparing APK Bundle…" else "Extract Split App",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                if (isExtractingBundle) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(36.dp))
                        Space(12.dp)
                        Text(
                            text = extractStatusText,
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center
                        )
                        if (extractProgress > 0f) {
                            Space(8.dp)
                            LinearProgressIndicator(
                                progress = { extractProgress },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "${app.name} is installed as a split APK bundle (${app.allApkPaths.size} APK files, ${app.size.toFormattedSize()}). Choose how to extract it:",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        // Option 1: Extract as .apks Bundle
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    scope.launch {
                                        isExtractingBundle = true
                                        extractProgress = 0.05f
                                        extractStatusText = "Packaging ${app.allApkPaths.size} split APKs into .apks bundle…"
                                        try {
                                            val outBundle = withContext(Dispatchers.IO) {
                                                createApksBundleFile(
                                                    apkPaths = app.allApkPaths,
                                                    outputFileName = "$cleanBaseFileName.apks",
                                                    onProgress = { pct, currentName ->
                                                        extractProgress = pct
                                                        extractStatusText = "Adding $currentName…"
                                                    }
                                                )
                                            }
                                            if (outBundle != null && outBundle.exists()) {
                                                val copyTask = CopyTask(
                                                    sourceFiles = arrayListOf(LocalFileHolder(outBundle)),
                                                    deleteSourceFiles = false
                                                ).apply {
                                                    setCustomTargetName(outBundle.absolutePath, "$cleanBaseFileName.apks")
                                                }
                                                globalClass.taskManager.addTask(copyTask)
                                                showExtractOptionsDialog = false
                                                onDismiss()
                                            } else {
                                                globalClass.showMsg("Failed to package .apks bundle")
                                            }
                                        } catch (e: Exception) {
                                            logger.logError(e)
                                            globalClass.showMsg("Extract failed: ${e.message}")
                                        } finally {
                                            isExtractingBundle = false
                                        }
                                    }
                                },
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.FolderZip,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        text = "APK Bundle (.apks)",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = "Fastest • Preserves original signatures ($cleanBaseFileName.apks)",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        // Option 2: Merge into Standalone .apk
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    scope.launch {
                                        isExtractingBundle = true
                                        extractProgress = 0.1f
                                        extractStatusText = "Packaging split APKs…"
                                        try {
                                            val mergedApk = withContext(Dispatchers.IO) {
                                                val tempBundle = createApksBundleFile(
                                                    apkPaths = app.allApkPaths,
                                                    outputFileName = "${cleanBaseFileName}_temp.apks"
                                                ) ?: return@withContext null
                                                try {
                                                    extractProgress = 0.45f
                                                    extractStatusText = "Merging & signing into standalone APK…"
                                                    val outDir = File(globalClass.cleanOnExitDir.file, "extracted_apps").apply { mkdirs() }
                                                    val merged = ApkBundleInstaller.mergeAndSignBundle(tempBundle, outDir)
                                                    if (merged != null && merged.exists()) {
                                                        val targetFile = File(outDir, "$cleanBaseFileName.apk")
                                                        if (targetFile.exists()) targetFile.delete()
                                                        if (merged.renameTo(targetFile)) targetFile else merged
                                                    } else null
                                                } finally {
                                                    tempBundle.delete()
                                                }
                                            }
                                            if (mergedApk != null && mergedApk.exists()) {
                                                val copyTask = CopyTask(
                                                    sourceFiles = arrayListOf(LocalFileHolder(mergedApk)),
                                                    deleteSourceFiles = false
                                                ).apply {
                                                    setCustomTargetName(mergedApk.absolutePath, "$cleanBaseFileName.apk")
                                                }
                                                globalClass.taskManager.addTask(copyTask)
                                                showExtractOptionsDialog = false
                                                onDismiss()
                                            } else {
                                                globalClass.showMsg("Failed to merge split APKs")
                                            }
                                        } catch (e: Exception) {
                                            logger.logError(e)
                                            globalClass.showMsg("Merge failed: ${e.message}")
                                        } finally {
                                            isExtractingBundle = false
                                        }
                                    }
                                },
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Merge,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        text = "Merge to Single APK (.apk)",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = "Merges & signs all splits into $cleanBaseFileName.apk",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        // Option 3: Extract Raw Split APK Files
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    val holders = app.allApkPaths
                                        .map { File(it) }
                                        .filter { it.exists() }
                                        .map { LocalFileHolder(it) }
                                    if (holders.isNotEmpty()) {
                                        val copyTask = CopyTask(
                                            sourceFiles = ArrayList(holders),
                                            deleteSourceFiles = false
                                        ).apply {
                                            setCustomTargetName(app.path, "${cleanBaseFileName}_base.apk")
                                        }
                                        globalClass.taskManager.addTask(copyTask)
                                        showExtractOptionsDialog = false
                                        onDismiss()
                                    }
                                },
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Archive,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        text = "Individual Split APKs (${app.allApkPaths.size} files)",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = "Copy base.apk and all split_*.apk files separately",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                if (!isExtractingBundle) {
                    TextButton(onClick = { showExtractOptionsDialog = false }) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            }
        )
    }

    BottomSheetDialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
        ) {
            // App Header
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                AsyncImage(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(12.dp)),
                    model = app.path,
                    filterQuality = FilterQuality.Low,
                    error = painterResource(id = R.drawable.apk_file_placeholder),
                    contentScale = ContentScale.Fit,
                    contentDescription = null
                )
                Space(size = 12.dp)
                Text(
                    text = app.name,
                    style = MaterialTheme.typography.headlineSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
                Space(size = 4.dp)
                Text(
                    text = app.packageName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
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
                        label = { Text("v${app.versionName}") },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Info,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    )
                    if (app.isSplitApp) {
                        AssistChip(
                            onClick = { },
                            label = { Text("Split (${app.allApkPaths.size} APKs)") },
                            leadingIcon = {
                                Icon(
                                    Icons.Rounded.FolderZip,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                labelColor = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        )
                    }
                    if (app.debuggable) {
                        AssistChip(
                            onClick = { },
                            label = { Text(stringResource(R.string.debug)) },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.BugReport,
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
                    VerticalDivider(Modifier.height(20.dp))
                    AssistChip(
                        onClick = {
                            if (app.isSplitApp) {
                                showExtractOptionsDialog = true
                            } else {
                                val copyTask = CopyTask(
                                    sourceFiles = arrayListOf(
                                        LocalFileHolder(
                                            file = File(app.path)
                                        )
                                    ),
                                    deleteSourceFiles = false
                                ).apply {
                                    setCustomTargetName(app.path, "$cleanBaseFileName.apk")
                                }
                                globalClass.taskManager.addTask(task = copyTask)
                                onDismiss()
                            }
                        },
                        label = {
                            Text(
                                if (app.isSplitApp) "${stringResource(R.string.extract)} Bundle"
                                else stringResource(R.string.extract)
                            )
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Upload,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    )
                }
            }

            Space(size = 16.dp)

            // Tab Buttons
            PrimaryScrollableTabRow(
                selectedTabIndex = selectedTab,
                containerColor = Color.Transparent
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text(stringResource(R.string.details)) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text(stringResource(R.string.advance)) }
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = { Text(stringResource(R.string.permissions)) }
                )
            }

            Space(size = 16.dp)

            // Tab Content
            when (selectedTab) {
                0 -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        item {
                            InfoCard(
                                title = stringResource(R.string.version),
                                value = "${app.versionName} (${app.versionCode})",
                                icon = Icons.Rounded.Info
                            )
                        }
                        item {
                            InfoCard(
                                title = stringResource(R.string.size),
                                value = if (app.isSplitApp) {
                                    "${app.size.toFormattedSize()} (${app.allApkPaths.size} split APKs)"
                                } else {
                                    app.size.toFormattedSize()
                                },
                                icon = Icons.Rounded.Memory
                            )
                        }
                        if (app.category.isNotEmpty()) {
                            item {
                                InfoCard(
                                    title = stringResource(R.string.category),
                                    value = app.category,
                                    icon = Icons.Rounded.Category
                                )
                            }
                        }
                        item {
                            InfoCard(
                                title = stringResource(R.string.install_date),
                                value = app.installDate.time.toFormattedDate(),
                                icon = Icons.Rounded.CalendarMonth
                            )
                        }
                        item {
                            InfoCard(
                                title = stringResource(R.string.last_update),
                                value = app.lastUpdateDate.time.toFormattedDate(),
                                icon = Icons.Rounded.Update
                            )
                        }
                    }
                }

                1 -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        item {
                            InfoCard(
                                title = stringResource(R.string.target_sdk),
                                value = app.targetSdkVersion.toString(),
                                icon = Icons.Rounded.Android
                            )
                        }
                        item {
                            InfoCard(
                                title = stringResource(R.string.minimum_sdk),
                                value = if (app.minSdkVersion > 0) app.minSdkVersion.toString() else stringResource(
                                    R.string.unknown
                                ),
                                icon = Icons.Rounded.Code
                            )
                        }
                        item {
                            InfoCard(
                                title = stringResource(R.string.uid),
                                value = app.uid.toString(),
                                icon = Icons.Rounded.Security
                            )
                        }
                        item {
                            InfoCard(
                                title = stringResource(R.string.data_directory),
                                value = app.dataDir,
                                icon = Icons.Rounded.Folder
                            )
                        }
                        item {
                            InfoCard(
                                title = stringResource(R.string.apk_path),
                                value = app.path,
                                icon = Icons.Rounded.DataUsage
                            )
                        }
                        if (app.isSplitApp) {
                            item {
                                InfoCard(
                                    title = "Split APKs (${app.splitPaths.size})",
                                    value = app.splitPaths.joinToString("\n") { File(it).name },
                                    icon = Icons.Rounded.FolderZip,
                                    maxLines = 10
                                )
                            }
                        }
                        item {
                            InfoCard(
                                title = stringResource(R.string.debug_mode),
                                value = if (app.debuggable) stringResource(R.string.enabled) else stringResource(
                                    R.string.disabled
                                ),
                                icon = Icons.Rounded.BugReport
                            )
                        }
                    }
                }

                2 -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        item {
                            Text(
                                text = stringResource(R.string.permissions),
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                        }
                        items(app.permissions) { permission ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
                                )
                            ) {
                                Text(
                                    text = permission.removePrefix("android.permission."),
                                    modifier = Modifier.padding(12.dp),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                }
            }

            Space(size = 16.dp)

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.close))
                }
            }
        }
    }
}

/**
 * Packages the installed `base.apk` + `split_*.apk` files into a single `.apks` bundle file
 * using uncompressed (`STORED`) ZIP entries (since `.apk` files are already compressed),
 * achieving near-instantaneous packaging speed.
 */
private fun createApksBundleFile(
    apkPaths: List<String>,
    outputFileName: String,
    onProgress: ((Float, String) -> Unit)? = null
): File? {
    val outDir = File(globalClass.cleanOnExitDir.file, "extracted_apps").apply { mkdirs() }
    val outFile = File(outDir, outputFileName)
    if (outFile.exists()) outFile.delete()

    val validFiles = apkPaths.map { File(it) }.filter { it.exists() && it.isFile && it.canRead() }
    if (validFiles.isEmpty()) return null

    val usedEntryNames = HashSet<String>()
    val buffer = ByteArray(64 * 1024)

    return try {
        ZipOutputStream(BufferedOutputStream(FileOutputStream(outFile), 64 * 1024)).use { zos ->
            validFiles.forEachIndexed { index, apkFile ->
                onProgress?.invoke(
                    (index.toFloat() / validFiles.size).coerceIn(0.05f, 0.95f),
                    apkFile.name
                )
                var entryName = apkFile.name
                if (!usedEntryNames.add(entryName)) {
                    entryName = "split_${index}_$entryName"
                    usedEntryNames.add(entryName)
                }

                // Compute CRC32 and size for STORED method (zero re-compression overhead)
                val crc = CRC32()
                var byteCount = 0L
                FileInputStream(apkFile).use { fis ->
                    while (true) {
                        val read = fis.read(buffer)
                        if (read <= 0) break
                        crc.update(buffer, 0, read)
                        byteCount += read
                    }
                }

                val entry = ZipEntry(entryName).apply {
                    method = ZipEntry.STORED
                    size = byteCount
                    compressedSize = byteCount
                    this.crc = crc.value
                    time = apkFile.lastModified()
                }
                zos.putNextEntry(entry)
                FileInputStream(apkFile).use { fis ->
                    while (true) {
                        val read = fis.read(buffer)
                        if (read <= 0) break
                        zos.write(buffer, 0, read)
                    }
                }
                zos.closeEntry()
            }
        }
        onProgress?.invoke(1f, outFile.name)
        if (outFile.exists() && outFile.length() > 0L) outFile else null
    } catch (e: Exception) {
        logger.logError(e)
        outFile.delete()
        null
    }
}

@Composable
private fun InfoCard(
    title: String,
    value: String,
    icon: ImageVector,
    maxLines: Int = 2
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Space(size = 12.dp)
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = maxLines,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
