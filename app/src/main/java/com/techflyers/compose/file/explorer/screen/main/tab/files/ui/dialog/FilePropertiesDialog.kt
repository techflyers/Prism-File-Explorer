package com.techflyers.compose.file.explorer.screen.main.tab.files.ui.dialog

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.InsertDriveFile
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.automirrored.rounded.Shortcut
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.DataUsage
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.rememberCoroutineScope
import com.techflyers.compose.file.explorer.common.toFormattedSize
import com.techflyers.compose.file.explorer.screen.main.ui.TooltipIconButton
import com.techflyers.compose.file.explorer.screen.main.tab.files.task.DeleteTask
import com.techflyers.compose.file.explorer.screen.main.tab.files.task.DeleteTaskParameters
import kotlinx.coroutines.launch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.techflyers.compose.file.explorer.App.Companion.globalClass
import com.techflyers.compose.file.explorer.R
import com.techflyers.compose.file.explorer.common.block
import com.techflyers.compose.file.explorer.common.copyToClipboard
import com.techflyers.compose.file.explorer.common.showMsg
import com.techflyers.compose.file.explorer.common.ui.BottomSheetDialog
import com.techflyers.compose.file.explorer.common.ui.Space
import com.techflyers.compose.file.explorer.common.ui.fastScrollbar
import com.techflyers.compose.file.explorer.screen.main.tab.files.FilesTab
import com.techflyers.compose.file.explorer.screen.main.tab.files.provider.CalculationProgress
import com.techflyers.compose.file.explorer.screen.main.tab.files.provider.ContentPropertiesProvider
import com.techflyers.compose.file.explorer.screen.main.tab.files.provider.PropertiesState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow

@Composable
fun FilePropertiesDialog(
    show: Boolean,
    tab: FilesTab,
    onDismissRequest: () -> Unit
) {
    if (show) {
        val selection = tab.selectedFiles.map { it.value }.toList()
        val contentPropertiesProvider = remember { ContentPropertiesProvider(selection) }
        val uiState by contentPropertiesProvider.uiState.collectAsState()

        DisposableEffect(contentPropertiesProvider) {
            onDispose {
                contentPropertiesProvider.cleanup()
            }
        }

        val title = when (uiState.details) {
            is PropertiesState.SingleContentProperties -> stringResource(R.string.file_properties)
            is PropertiesState.MultipleContentProperties -> stringResource(R.string.selection_properties)
            else -> stringResource(R.string.loading_properties)
        }

        BottomSheetDialog(
            onDismissRequest = onDismissRequest
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 24.dp)
            ) {
                Column {
                    Text(
                        modifier = Modifier.fillMaxWidth(),
                        text = title,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Space(8.dp)
                    HorizontalDivider(
                        thickness = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant
                    )
                }

                Space(12.dp)

                val propertiesListState = rememberLazyListState()
                LazyColumn(
                    state = propertiesListState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 600.dp)
                        .fastScrollbar(propertiesListState),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    item {
                        AnimatedContent(
                            targetState = uiState.details,
                            transitionSpec = {
                                fadeIn(animationSpec = tween(300)) togetherWith
                                        fadeOut(animationSpec = tween(300))
                            },
                            label = "content_transition"
                        ) { details ->
                            when (details) {
                                is PropertiesState.Loading -> {
                                    LoadingContent()
                                }

                                is PropertiesState.SingleContentProperties -> {
                                    SingleFileContent(
                                        details = details,
                                        onReload = { contentPropertiesProvider.reload() }
                                    )
                                }

                                is PropertiesState.MultipleContentProperties -> {
                                    MultipleFilesContent(details, tab, onDismissRequest)
                                }
                            }
                        }
                    }
                }

                Space(16.dp)

                Button(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(6.dp),
                    onClick = onDismissRequest
                ) {
                    Text(stringResource(R.string.close))
                }
            }
        }
    }
}

@Composable
private fun LoadingContent() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp)
    ) {
        CircularProgressIndicator()
        Space(16.dp)
        Text(
            text = stringResource(R.string.analyzing_files),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SingleFileContent(
    details: PropertiesState.SingleContentProperties,
    onReload: () -> Unit
) {
    var showSetModeDialog by remember { mutableStateOf(false) }
    var showSetOwnerDialog by remember { mutableStateOf(false) }
    var showSetGroupDialog by remember { mutableStateOf(false) }
    var showSetSeLinuxDialog by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Basic properties
        PropertySection(title = stringResource(R.string.general)) {
            PropertyRow(
                icon = Icons.Default.DriveFileRenameOutline,
                label = stringResource(R.string.name),
                value = details.name,
                maxLines = Int.MAX_VALUE
            )
            PropertyRow(
                icon = Icons.Default.FolderOpen,
                label = stringResource(R.string.location),
                value = details.path,
                maxLines = Int.MAX_VALUE
            )
            PropertyRow(
                icon = Icons.Default.Category,
                label = stringResource(R.string.type),
                value = details.type
            )
            if (!details.symbolicLinkTarget.isNullOrBlank()) {
                PropertyRow(
                    icon = Icons.AutoMirrored.Rounded.Shortcut,
                    label = stringResource(R.string.file_properties_basic_symbolic_link_target),
                    value = details.symbolicLinkTarget,
                    maxLines = 3
                )
            }
            PropertyRow(
                icon = Icons.Default.DataUsage,
                label = stringResource(R.string.size),
                value = details.size
            )
            PropertyRow(
                icon = Icons.Default.Schedule,
                label = stringResource(R.string.modified),
                value = details.lastModified
            )
        }

        // System properties
        PropertySection(title = stringResource(R.string.system)) {
            PropertyRow(
                icon = Icons.Default.Person,
                label = stringResource(R.string.owner),
                value = details.owner,
                onClick = if (details.fileHolder != null && details.owner.isNotBlank()) {
                    { showSetOwnerDialog = true }
                } else null
            )
            PropertyRow(
                icon = Icons.Rounded.Groups,
                label = stringResource(R.string.file_properties_permission_group),
                value = details.group,
                onClick = if (details.fileHolder != null && details.group.isNotBlank()) {
                    { showSetGroupDialog = true }
                } else null
            )
            PropertyRow(
                icon = Icons.Default.Security,
                label = stringResource(R.string.permissions),
                value = details.permissions,
                onClick = if (details.fileHolder != null && details.permissions.isNotBlank()) {
                    { showSetModeDialog = true }
                } else null
            )
            if (details.seLinuxContext.isNotBlank()) {
                PropertyRow(
                    icon = Icons.Rounded.Security,
                    label = stringResource(R.string.file_properties_permission_selinux_context),
                    value = details.seLinuxContext,
                    maxLines = 4,
                    onClick = if (details.fileHolder != null) {
                        { showSetSeLinuxDialog = true }
                    } else null
                )
            }
        }

        // Computed properties
        PropertySection(title = stringResource(R.string.analysis)) {
            AsyncPropertyRow(
                icon = Icons.Default.Inventory,
                label = stringResource(R.string.contents),
                valueFlow = details.contentCount,
                progressFlow = details.contentProgress
            )
            AsyncPropertyRow(
                icon = Icons.Default.Fingerprint,
                label = stringResource(R.string.md5_checksum),
                valueFlow = details.checksum,
                progressFlow = details.checksumProgress
            )
            AsyncPropertyRow(
                icon = Icons.Default.Fingerprint,
                label = stringResource(R.string.sha1_checksum),
                valueFlow = details.sha256,
                progressFlow = details.sha256Progress
            )
        }
    }

    if (showSetModeDialog && details.fileHolder != null) {
        SetModeDialog(
            file = details.fileHolder,
            onDismissRequest = { showSetModeDialog = false },
            onPermissionsChanged = {
                onReload()
            }
        )
    }

    if (showSetOwnerDialog && details.fileHolder != null) {
        SetOwnerGroupDialog(
            file = details.fileHolder,
            isOwner = true,
            onDismissRequest = { showSetOwnerDialog = false },
            onChanged = {
                onReload()
            }
        )
    }

    if (showSetGroupDialog && details.fileHolder != null) {
        SetOwnerGroupDialog(
            file = details.fileHolder,
            isOwner = false,
            onDismissRequest = { showSetGroupDialog = false },
            onChanged = {
                onReload()
            }
        )
    }

    if (showSetSeLinuxDialog && details.fileHolder != null) {
        SetSeLinuxContextDialog(
            file = details.fileHolder,
            onDismissRequest = { showSetSeLinuxDialog = false },
            onContextChanged = {
                onReload()
            }
        )
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun MultipleFilesContent(
    details: PropertiesState.MultipleContentProperties,
    tab: FilesTab,
    onDismissRequest: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var fileToDelete by remember { mutableStateOf<com.techflyers.compose.file.explorer.screen.main.tab.files.holder.ContentHolder?>(null) }
    var deletedPaths by remember { mutableStateOf(setOf<String>()) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PropertySection(title = stringResource(R.string.selection_summary)) {
            PropertyRow(
                icon = Icons.Default.SelectAll,
                label = stringResource(R.string.selected),
                value = stringResource(R.string.items_count, details.selectedFileCount)
            )
            AsyncPropertyRow(
                icon = Icons.Default.DataUsage,
                label = stringResource(R.string.total_size),
                valueFlow = details.totalSize,
                progressFlow = details.sizeProgress
            )
            AsyncPropertyRow(
                icon = Icons.Default.Inventory,
                label = stringResource(R.string.total_contents),
                valueFlow = details.totalFileCount,
                progressFlow = details.countProgress
            )
        }

        PropertySection(title = stringResource(R.string.checksums_and_duplicates)) {
            AsyncPropertyRow(
                icon = Icons.Default.Fingerprint,
                label = stringResource(R.string.md5_checksum),
                valueFlow = details.checksumStatus,
                progressFlow = details.checksumProgress
            )
            val duplicates by details.duplicateGroups.collectAsState()
            if (duplicates.isNotEmpty()) {
                duplicates.forEach { (hash, duplicateFiles) ->
                    val visibleFiles = duplicateFiles.filter { it.uniquePath !in deletedPaths }
                    if (visibleFiles.isNotEmpty()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    SelectionContainer {
                                        Text(
                                            text = hash,
                                            style = MaterialTheme.typography.labelMedium.copy(
                                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                                            ),
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Text(
                                        text = "${visibleFiles.size} duplicate files",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    TooltipIconButton(
                                        tooltip = stringResource(R.string.copy_hash),
                                        onClick = {
                                            hash.copyToClipboard()
                                            showMsg(globalClass.getString(R.string.copied_to_clipboard))
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.ContentCopy,
                                            contentDescription = stringResource(R.string.copy_hash),
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    TooltipIconButton(
                                        tooltip = stringResource(R.string.open_duplicates_in_new_tab),
                                        onClick = {
                                            onDismissRequest()
                                            globalClass.mainActivityManager.addTabAndSelect(
                                                FilesTab(
                                                    com.techflyers.compose.file.explorer.screen.main.tab.files.holder.VirtualFileHolder(
                                                        type = com.techflyers.compose.file.explorer.screen.main.tab.files.holder.VirtualFileHolder.DUPLICATES,
                                                        customItems = visibleFiles,
                                                        customTitle = "Duplicates (${hash.take(6)})"
                                                    )
                                                )
                                            )
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.OpenInNew,
                                            contentDescription = stringResource(R.string.open_duplicates_in_new_tab),
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }

                            visibleFiles.forEach { file ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 3.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                                    ),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 10.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (file.isFolder) Icons.Default.FolderOpen else androidx.compose.material.icons.Icons.AutoMirrored.Rounded.InsertDriveFile,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(22.dp)
                                        )
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = file.displayName,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = file.uniquePath,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = file.size.toFormattedSize(),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.secondary
                                            )
                                        }
                                        // Quick Action: Open File
                                        TooltipIconButton(
                                            tooltip = stringResource(R.string.open),
                                            onClick = {
                                                onDismissRequest()
                                                tab.openFile(context, file)
                                            },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                imageVector = androidx.compose.material.icons.Icons.AutoMirrored.Rounded.OpenInNew,
                                                contentDescription = stringResource(R.string.open),
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                        // Quick Action: Open containing folder in new tab
                                        TooltipIconButton(
                                            tooltip = stringResource(R.string.open_containing_folder),
                                            onClick = {
                                                coroutineScope.launch {
                                                    globalClass.mainActivityManager.addTabAndSelect(FilesTab(file))
                                                    onDismissRequest()
                                                }
                                            },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.FolderOpen,
                                                contentDescription = stringResource(R.string.open_containing_folder),
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                        // Quick Action: Delete file
                                        TooltipIconButton(
                                            tooltip = stringResource(R.string.delete),
                                            onClick = { fileToDelete = file },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.Delete,
                                                contentDescription = stringResource(R.string.delete),
                                                tint = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Delete Confirmation Dialog
    fileToDelete?.let { target ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { fileToDelete = null },
            title = { Text(stringResource(R.string.delete)) },
            text = { Text("Are you sure you want to delete \"${target.displayName}\"?") },
            confirmButton = {
                androidx.compose.material3.TextButton(
                    onClick = {
                        val path = target.uniquePath
                        fileToDelete = null
                        deletedPaths = deletedPaths + path
                        globalClass.taskManager.addTaskAndRun(
                            DeleteTask(listOf(target)),
                            DeleteTaskParameters()
                        )
                        globalClass.showMsg("Deleted ${target.displayName}")
                    }
                ) {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { fileToDelete = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@Composable
private fun PropertySection(
    title: String,
    content: @Composable () -> Unit
) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 8.dp, start = 6.dp)
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            ),
            shape = RoundedCornerShape(6.dp)
        ) {
            Column(
                modifier = Modifier
                    .block(
                        shape = RoundedCornerShape(6.dp),
                    )
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                content()
            }
        }
    }
}

@Composable
fun PropertyRow(
    icon: ImageVector,
    label: String,
    value: String,
    maxLines: Int = 3,
    onClick: (() -> Unit)? = null
) {
    if (value.isNotBlank()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (onClick != null) {
                        Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable(onClick = onClick)
                            .padding(vertical = 2.dp)
                    } else Modifier
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
            Space(12.dp)
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.width(100.dp),
                color = MaterialTheme.colorScheme.onSurface
            )
            Space(8.dp)
            CopiableText(
                text = value,
                modifier = Modifier.weight(1f),
                maxLines = maxLines,
                onClick = onClick
            )
            if (onClick != null) {
                Space(4.dp)
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "Edit",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
fun AsyncPropertyRow(
    icon: ImageVector,
    label: String,
    valueFlow: StateFlow<String>,
    progressFlow: StateFlow<CalculationProgress>
) {
    val value by valueFlow.collectAsState()
    val progress by progressFlow.collectAsState()

    if (value.isNotBlank()) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
                Space(12.dp)
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.width(100.dp),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Space(8.dp)
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (progress.isCalculating) {
                            PulsingDot()
                            Space(8.dp)
                        }
                        CopiableText(
                            text = value,
                            color = if (progress.isCalculating)
                                MaterialTheme.colorScheme.onSurfaceVariant
                            else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PulsingDot() {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    Box(
        modifier = Modifier
            .size(8.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = alpha))
    )
}

@Composable
fun CopiableText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurface,
    maxLines: Int = 1,
    onClick: (() -> Unit)? = null
) {
    var showCopiedFeedback by remember { mutableStateOf(false) }

    LaunchedEffect(showCopiedFeedback) {
        if (showCopiedFeedback) {
            delay(1500)
            showCopiedFeedback = false
        }
    }

    Box(modifier = modifier) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = color,
            maxLines = maxLines,
            overflow = if (maxLines == Int.MAX_VALUE) TextOverflow.Clip else TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .pointerInput(text, onClick) {
                    detectTapGestures(
                        onTap = if (onClick != null) { _ -> onClick() } else null,
                        onLongPress = {
                            text.copyToClipboard()
                            showCopiedFeedback = true
                            showMsg(globalClass.getString(R.string.copied_to_clipboard))
                        }
                    )
                }
        )

        // Subtle visual feedback
        if (showCopiedFeedback) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                        RoundedCornerShape(4.dp)
                    )
            )
        }
    }
}