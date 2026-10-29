package com.techflyers.compose.file.explorer.screen.main.tab.files.ui.dialog

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.CompareArrows
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
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.EnhancedEncryption
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Tab
import androidx.compose.material.icons.rounded.TableChart
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material.icons.rounded.ViewAgenda
import androidx.compose.material.icons.rounded.ViewColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.techflyers.compose.file.explorer.App.Companion.globalClass
import com.techflyers.compose.file.explorer.R
import com.techflyers.compose.file.explorer.common.block
import com.techflyers.compose.file.explorer.common.copyToClipboard
import com.techflyers.compose.file.explorer.common.showMsg
import com.techflyers.compose.file.explorer.common.toFormattedDate
import com.techflyers.compose.file.explorer.common.toFormattedSize
import com.techflyers.compose.file.explorer.common.ui.BottomSheetDialog
import com.techflyers.compose.file.explorer.common.ui.Space
import com.techflyers.compose.file.explorer.common.ui.fastScrollbar
import com.techflyers.compose.file.explorer.screen.main.tab.files.FilesTab
import com.techflyers.compose.file.explorer.screen.main.tab.files.holder.ContentHolder
import com.techflyers.compose.file.explorer.screen.main.tab.files.holder.LocalFileHolder
import com.techflyers.compose.file.explorer.screen.main.tab.files.holder.VirtualFileHolder
import com.techflyers.compose.file.explorer.screen.main.tab.files.provider.CalculationProgress
import com.techflyers.compose.file.explorer.screen.main.tab.files.provider.ContentPropertiesProvider
import com.techflyers.compose.file.explorer.screen.main.tab.files.provider.PropertiesState
import com.techflyers.compose.file.explorer.screen.main.tab.files.provider.SavedDirectoryComparisonStore
import com.techflyers.compose.file.explorer.screen.main.tab.files.service.FolderLockStore
import com.techflyers.compose.file.explorer.screen.main.tab.files.task.DeleteTask
import com.techflyers.compose.file.explorer.screen.main.tab.files.task.DeleteTaskParameters
import com.techflyers.compose.file.explorer.screen.main.tab.nfile_tools.ui.FileSelectionDialog
import com.techflyers.compose.file.explorer.screen.main.ui.TooltipIconButton
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun FilePropertiesDialog(
    show: Boolean,
    tab: FilesTab,
    customSelection: List<ContentHolder>? = null,
    onDismissRequest: () -> Unit
) {
    if (show) {
        val initialSelection = remember(customSelection, tab.selectedFiles.keys.toList()) {
            customSelection ?: tab.selectedFiles.map { it.value }.toList()
        }
        var currentSelection by remember(initialSelection) {
            mutableStateOf(initialSelection)
        }
        var showComparisonPicker by remember { mutableStateOf(false) }

        val contentPropertiesProvider = remember(currentSelection) {
            ContentPropertiesProvider(currentSelection)
        }
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

        val initialPickerDirectory = remember(currentSelection) {
            val firstLocal = currentSelection.filterIsInstance<LocalFileHolder>().firstOrNull()
            if (firstLocal != null) {
                firstLocal.file.parentFile ?: firstLocal.file
            } else {
                (tab.activeFolder as? LocalFileHolder)?.file
            }
        }

        FileSelectionDialog(
            show = showComparisonPicker,
            initialDirectory = initialPickerDirectory,
            onDismissRequest = { showComparisonPicker = false },
            onItemsSelected = { files ->
                showComparisonPicker = false
                val addedHolders = files.map { LocalFileHolder(it) }
                val merged = (currentSelection + addedHolders).distinctBy { it.uniquePath }
                if (merged.size == currentSelection.size) {
                    showMsg("Selected item is already in comparison")
                } else {
                    currentSelection = merged
                }
            }
        )

        val context = LocalContext.current
        val coroutineScope = rememberCoroutineScope()
        var fileToDelete by remember { mutableStateOf<ContentHolder?>(null) }
        var deletedPaths by remember { mutableStateOf(setOf<String>()) }

        val details = uiState.details
        val multipleDetails = details as? PropertiesState.MultipleContentProperties

        // Collect StateFlows in @Composable scope so LazyColumn recomposes live when calculations finish
        val dirComp by (multipleDetails?.directoryComparison ?: remember { kotlinx.coroutines.flow.MutableStateFlow(null) }).collectAsState()
        val checksumGroups by (multipleDetails?.duplicateGroups ?: remember { kotlinx.coroutines.flow.MutableStateFlow(emptyList()) }).collectAsState()
        val twoFileComparison by (multipleDetails?.twoFileComparison ?: remember { kotlinx.coroutines.flow.MutableStateFlow(null) }).collectAsState()

        val handleExplicitClose: () -> Unit = {
            if (currentSelection.size >= 2) {
                val names = currentSelection.take(3).joinToString(" vs ") { it.displayName }
                globalClass.mainActivityManager.recordClosedComparison(
                    title = "Comparison: $names",
                    subtitle = "${currentSelection.size} items compared",
                    selectionPaths = currentSelection.map { it.uniquePath }
                )
            }
            onDismissRequest()
        }

        BottomSheetDialog(
            onDismissRequest = handleExplicitClose,
            preventSwipeDismiss = true
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(top = 12.dp, bottom = 24.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = handleExplicitClose,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = stringResource(R.string.close)
                        )
                    }
                }

                Space(8.dp)
                HorizontalDivider(
                    thickness = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant
                )
                Space(12.dp)

                val propertiesListState = rememberLazyListState()

                var selectedFilter by remember { mutableStateOf("ALL") }
                var viewMode by remember { mutableStateOf("CARDS") } // "CARDS" or "TABLE"
                var actionButtonLayout by remember { mutableStateOf("GRID_2X2") } // "GRID_2X2" or "COL_1X4"
                var showColumnSelector by remember { mutableStateOf(false) }
                var showHashAlgorithmHelp by remember { mutableStateOf(false) }
                val onDemandSha256Map = remember { mutableStateMapOf<String, String>() }
                var isComputingAllSha256 by remember { mutableStateOf(false) }

                val configuredColumnsCsv = globalClass.preferencesManager.comparisonTableColumnsJson
                var enabledColumns by remember(configuredColumnsCsv) {
                    mutableStateOf(
                        configuredColumnsCsv.split(",")
                            .map { it.trim() }
                            .filter { it.isNotEmpty() }
                            .ifEmpty {
                                listOf("Filename", "Result", "Left Date", "Right Date", "Left Size", "Right Size", "Extension")
                            }
                    )
                }

                LazyColumn(
                    state = propertiesListState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 600.dp)
                        .fastScrollbar(propertiesListState)
                        .padding(end = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 8.dp)
                ) {
                    when (details) {
                        is PropertiesState.Loading -> {
                            item(key = "loading") {
                                LoadingContent()
                            }
                        }

                        is PropertiesState.SingleContentProperties -> {
                            item(key = "single_content") {
                                SingleFileContent(
                                    details = details,
                                    onReload = { contentPropertiesProvider.reload() },
                                    onAddFilesForComparison = { showComparisonPicker = true },
                                    onReopenLastComparison = if (globalClass.preferencesManager.lastDirectoryComparisonJson.isNotBlank()) {
                                        {
                                            onDismissRequest()
                                            tab.showSavedComparisonDialog = true
                                        }
                                    } else null
                                )
                            }
                        }

                        is PropertiesState.MultipleContentProperties -> {
                            multipleFilesLazyItems(
                                details = details,
                                dirComp = dirComp,
                                checksumGroups = checksumGroups,
                                twoFileComparison = twoFileComparison,
                                tab = tab,
                                context = context,
                                coroutineScope = coroutineScope,
                                deletedPaths = deletedPaths,
                                selectedFilter = selectedFilter,
                                onSelectFilter = { selectedFilter = it },
                                viewMode = viewMode,
                                onToggleViewMode = { viewMode = if (viewMode == "CARDS") "TABLE" else "CARDS" },
                                actionButtonLayout = actionButtonLayout,
                                onToggleActionButtonLayout = {
                                    actionButtonLayout = if (actionButtonLayout == "GRID_2X2") "COL_1X4" else "GRID_2X2"
                                },
                                showColumnSelector = showColumnSelector,
                                onToggleColumnSelector = { showColumnSelector = !showColumnSelector },
                                enabledColumns = enabledColumns,
                                onUpdateColumns = { updated ->
                                    enabledColumns = updated
                                    globalClass.preferencesManager.comparisonTableColumnsJson = updated.joinToString(",")
                                },
                                showHashAlgorithmHelp = showHashAlgorithmHelp,
                                onToggleHashAlgorithmHelp = { showHashAlgorithmHelp = !showHashAlgorithmHelp },
                                onSwitchHashAlgorithm = { newAlgo ->
                                    globalClass.preferencesManager.comparisonHashAlgorithm = newAlgo
                                    contentPropertiesProvider.reload()
                                },
                                onDemandSha256Map = onDemandSha256Map,
                                isComputingAllSha256 = isComputingAllSha256,
                                onComputeAllSha256 = { entries ->
                                    if (!isComputingAllSha256) {
                                        isComputingAllSha256 = true
                                        coroutineScope.launch {
                                            try {
                                                for (entry in entries) {
                                                    for (info in entry.entriesByFolder) {
                                                        val path = info.holder.uniquePath
                                                        if (onDemandSha256Map[path].isNullOrBlank() && info.sha256.isBlank()) {
                                                            val local = info.holder as? LocalFileHolder
                                                            if (local != null && local.file.exists() && !local.file.isDirectory) {
                                                                onDemandSha256Map[path] = "Calculating..."
                                                                val sha = ContentPropertiesProvider.computeFileSha256(local.file)
                                                                onDemandSha256Map[path] = sha
                                                            }
                                                        }
                                                    }
                                                }
                                            } finally {
                                                isComputingAllSha256 = false
                                            }
                                        }
                                    }
                                },
                                onComputeSingleSha256 = { holder ->
                                    val path = holder.uniquePath
                                    val local = holder as? LocalFileHolder
                                    if (local != null && local.file.exists() && !local.file.isDirectory) {
                                        onDemandSha256Map[path] = "Calculating..."
                                        coroutineScope.launch {
                                            val sha = ContentPropertiesProvider.computeFileSha256(local.file)
                                            onDemandSha256Map[path] = sha
                                        }
                                    }
                                },
                                onInspectSingleFileInPopup = { holder ->
                                    currentSelection = listOf(holder)
                                },
                                onDeleteFile = { fileToDelete = it },
                                onAddMoreForComparison = { showComparisonPicker = true },
                                onDismissRequest = handleExplicitClose
                            )
                        }
                    }
                }
            }
        }

        // Delete Confirmation Dialog inside Properties/Comparison
        fileToDelete?.let { target ->
            AlertDialog(
                onDismissRequest = { fileToDelete = null },
                title = { Text(stringResource(R.string.delete)) },
                text = {
                    Text(
                        "Are you sure you want to delete \"${target.displayName}\" (${target.size.toFormattedSize()})?"
                    )
                },
                confirmButton = {
                    TextButton(
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
                    TextButton(onClick = { fileToDelete = null }) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            )
        }
    }
}

/**
 * Full-height popup to reopen the most recent saved directory & duplicates comparison
 * (accessible from Closed Tabs History or from Properties) with full Open, Locate, Copy Hash,
 * Delete, and Live Re-run in Popup support.
 */
@Composable
fun SavedDirectoryComparisonDialog(
    show: Boolean,
    tab: FilesTab,
    onDismissRequest: () -> Unit
) {
    if (!show) return

    val savedResult = remember(show) { SavedDirectoryComparisonStore.load() }
    var inspectSelectionInPopup by remember { mutableStateOf<List<ContentHolder>?>(null) }

    if (inspectSelectionInPopup != null) {
        FilePropertiesDialog(
            show = true,
            tab = tab,
            customSelection = inspectSelectionInPopup,
            onDismissRequest = {
                inspectSelectionInPopup = null
                onDismissRequest()
            }
        )
        return
    }

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var fileToDelete by remember { mutableStateOf<ContentHolder?>(null) }
    var deletedPaths by remember { mutableStateOf(setOf<String>()) }

    var selectedFilter by remember { mutableStateOf("ALL") }
    var viewMode by remember { mutableStateOf("CARDS") }
    var actionButtonLayout by remember { mutableStateOf("GRID_2X2") }
    var showColumnSelector by remember { mutableStateOf(false) }
    var showHashAlgorithmHelp by remember { mutableStateOf(false) }
    val onDemandSha256Map = remember { mutableStateMapOf<String, String>() }
    var isComputingAllSha256 by remember { mutableStateOf(false) }

    val configuredColumnsCsv = globalClass.preferencesManager.comparisonTableColumnsJson
    var enabledColumns by remember(configuredColumnsCsv) {
        mutableStateOf(
            configuredColumnsCsv.split(",")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .ifEmpty {
                    listOf("Filename", "Result", "Left Date", "Right Date", "Left Size", "Right Size", "Extension")
                }
        )
    }

    BottomSheetDialog(
        onDismissRequest = onDismissRequest,
        preventSwipeDismiss = true
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(top = 12.dp, bottom = 24.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Saved Directory Comparison",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (savedResult != null && savedResult.timestamp > 0L) {
                        Text(
                            text = "Saved ${savedResult.timestamp.toFormattedDate()} • ${savedResult.hashAlgorithm}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (savedResult != null && savedResult.folderPaths.isNotEmpty()) {
                    OutlinedButton(
                        onClick = {
                            val holders = savedResult.folderPaths.map { LocalFileHolder(File(it)) }
                            inspectSelectionInPopup = holders
                        },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.padding(end = 6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp)
                        )
                        Space(4.dp)
                        Text("Live Popup", style = MaterialTheme.typography.labelSmall)
                    }
                }
                IconButton(
                    onClick = onDismissRequest,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = stringResource(R.string.close)
                    )
                }
            }

            Space(8.dp)
            HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
            Space(12.dp)

            if (savedResult == null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No saved directory comparison found.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                val listState = rememberLazyListState()
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 600.dp)
                        .fastScrollbar(listState)
                        .padding(end = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 8.dp)
                ) {
                    directoryComparisonLazyItems(
                        comp = savedResult,
                        statusText = null,
                        progress = null,
                        tab = tab,
                        context = context,
                        coroutineScope = coroutineScope,
                        deletedPaths = deletedPaths,
                        selectedFilter = selectedFilter,
                        onSelectFilter = { selectedFilter = it },
                        viewMode = viewMode,
                        onToggleViewMode = { viewMode = if (viewMode == "CARDS") "TABLE" else "CARDS" },
                        actionButtonLayout = actionButtonLayout,
                        onToggleActionButtonLayout = {
                            actionButtonLayout = if (actionButtonLayout == "GRID_2X2") "COL_1X4" else "GRID_2X2"
                        },
                        showColumnSelector = showColumnSelector,
                        onToggleColumnSelector = { showColumnSelector = !showColumnSelector },
                        enabledColumns = enabledColumns,
                        onUpdateColumns = { updated ->
                            enabledColumns = updated
                            globalClass.preferencesManager.comparisonTableColumnsJson = updated.joinToString(",")
                        },
                        showHashAlgorithmHelp = showHashAlgorithmHelp,
                        onToggleHashAlgorithmHelp = { showHashAlgorithmHelp = !showHashAlgorithmHelp },
                        onSwitchHashAlgorithm = null,
                        onDemandSha256Map = onDemandSha256Map,
                        isComputingAllSha256 = isComputingAllSha256,
                        onComputeAllSha256 = { entries ->
                            if (!isComputingAllSha256) {
                                isComputingAllSha256 = true
                                coroutineScope.launch {
                                    try {
                                        for (entry in entries) {
                                            for (info in entry.entriesByFolder) {
                                                val path = info.holder.uniquePath
                                                if (onDemandSha256Map[path].isNullOrBlank() && info.sha256.isBlank()) {
                                                    val local = info.holder as? LocalFileHolder
                                                    if (local != null && local.file.exists() && !local.file.isDirectory) {
                                                        onDemandSha256Map[path] = "Calculating..."
                                                        val sha = ContentPropertiesProvider.computeFileSha256(local.file)
                                                        onDemandSha256Map[path] = sha
                                                    }
                                                }
                                            }
                                        }
                                    } finally {
                                        isComputingAllSha256 = false
                                    }
                                }
                            }
                        },
                        onComputeSingleSha256 = { holder ->
                            val path = holder.uniquePath
                            val local = holder as? LocalFileHolder
                            if (local != null && local.file.exists() && !local.file.isDirectory) {
                                onDemandSha256Map[path] = "Calculating..."
                                coroutineScope.launch {
                                    val sha = ContentPropertiesProvider.computeFileSha256(local.file)
                                    onDemandSha256Map[path] = sha
                                }
                            }
                        },
                        onInspectSingleFileInPopup = { holder ->
                            inspectSelectionInPopup = listOf(holder)
                        },
                        onDeleteFile = { fileToDelete = it },
                        onDismissRequest = onDismissRequest
                    )

                    // Also render the saved Checksums & Duplicates view inside the saved comparison popup!
                    if (savedResult.savedDuplicateGroups.isNotEmpty() || savedResult.savedTwoFileComparison != null) {
                        checksumsAndDuplicatesLazyItems(
                            checksumStatusFlow = null,
                            checksumProgressFlow = null,
                            twoFileComparison = savedResult.savedTwoFileComparison,
                            checksumGroups = savedResult.savedDuplicateGroups,
                            deletedPaths = deletedPaths,
                            tab = tab,
                            context = context,
                            coroutineScope = coroutineScope,
                            onInspectSingleFileInPopup = { holder ->
                                inspectSelectionInPopup = listOf(holder)
                            },
                            onDeleteFile = { fileToDelete = it },
                            onDismissRequest = onDismissRequest
                        )
                    }
                }
            }
        }
    }

    fileToDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { fileToDelete = null },
            title = { Text(stringResource(R.string.delete)) },
            text = {
                Text(
                    "Are you sure you want to delete \"${target.displayName}\" (${target.size.toFormattedSize()})?"
                )
            },
            confirmButton = {
                TextButton(
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
                TextButton(onClick = { fileToDelete = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
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
    onReload: () -> Unit,
    onAddFilesForComparison: () -> Unit,
    onReopenLastComparison: (() -> Unit)? = null
) {
    val context = LocalContext.current
    var showSetModeDialog by remember { mutableStateOf(false) }
    var showSetOwnerDialog by remember { mutableStateOf(false) }
    var showSetGroupDialog by remember { mutableStateOf(false) }
    var showSetSeLinuxDialog by remember { mutableStateOf(false) }
    var showExtendedMetadata by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Basic properties
        PropertySection(title = stringResource(R.string.general)) {
            PropertyRow(
                icon = Icons.Default.DriveFileRenameOutline,
                label = stringResource(R.string.name),
                value = details.name,
                maxLines = 2
            )
            PropertyRow(
                icon = Icons.Default.FolderOpen,
                label = stringResource(R.string.location),
                value = details.path,
                maxLines = 2
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
            val holder = details.fileHolder
            if (holder != null && holder.isFolder) {
                val isProtected = remember(holder.uniquePath) {
                    FolderLockStore.hasLock(holder.uniquePath)
                }
                val isEncrypted = remember(holder.uniquePath) {
                    FolderLockStore.isFolderEncrypted(holder.uniquePath)
                }
                val statusDesc = remember(holder.uniquePath) {
                    FolderLockStore.getDetailedFolderSecurityStatus(holder.uniquePath)
                }
                PropertyRow(
                    icon = when {
                        isEncrypted -> Icons.Rounded.EnhancedEncryption
                        isProtected -> Icons.Rounded.Lock
                        else -> Icons.Rounded.LockOpen
                    },
                    label = "Folder Security",
                    value = statusDesc,
                    maxLines = 3
                )
            }
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
                valueFlow = details.sha1,
                progressFlow = details.sha1Progress
            )
            AsyncPropertyRow(
                icon = Icons.Default.Fingerprint,
                label = "SHA-256",
                valueFlow = details.sha256,
                progressFlow = details.sha256Progress
            )
            if (details.fileHolder != null && details.fileHolder.isFile()) {
                val md5Val by details.checksum.collectAsState()
                val sha1Val by details.sha1.collectAsState()
                val sha256Val by details.sha256.collectAsState()
                var compareInput by remember { mutableStateOf("") }
                val normalizedInput = compareInput.trim().lowercase()

                androidx.compose.material3.OutlinedTextField(
                    value = compareInput,
                    onValueChange = { compareInput = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    singleLine = true,
                    label = { Text("Verify checksum (paste MD5 / SHA-1 / SHA-256)") },
                    supportingText = if (normalizedInput.isNotEmpty()) {
                        {
                            val matchedAlgo = when {
                                md5Val.isNotBlank() && md5Val.lowercase() == normalizedInput -> "MD5"
                                sha1Val.isNotBlank() && sha1Val.lowercase() == normalizedInput -> "SHA-1"
                                sha256Val.isNotBlank() && sha256Val.lowercase() == normalizedInput -> "SHA-256"
                                else -> null
                            }
                            Text(
                                text = if (matchedAlgo != null) {
                                    "Matches $matchedAlgo checksum"
                                } else {
                                    "Does not match computed checksums"
                                },
                                color = if (matchedAlgo != null) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.error
                                },
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    } else null
                )
            }

            if (details.fileHolder != null) {
                OutlinedButton(
                    onClick = onAddFilesForComparison,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.CompareArrows,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Space(8.dp)
                    Text(
                        text = if (details.fileHolder.isFolder) {
                            "Add folders / files for comparison"
                        } else {
                            "Add files for comparison"
                        }
                    )
                }
            }

            if (onReopenLastComparison != null) {
                OutlinedButton(
                    onClick = onReopenLastComparison,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Rounded.History,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Space(8.dp)
                    Text("Reopen Last Saved Comparison")
                }
            }
        }

        if (details.fileHolder != null && details.fileHolder.isFile()) {
            OutlinedButton(
                onClick = { showExtendedMetadata = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Inventory, contentDescription = null, modifier = Modifier.size(18.dp))
                Space(8.dp)
                Text(stringResource(R.string.extended_metadata))
            }
        }
    }

    if (showExtendedMetadata && details.fileHolder != null) {
        ExtendedMetadataSheet(
            holder = details.fileHolder,
            onDismiss = { showExtendedMetadata = false }
        )
    }

    if (showSetModeDialog && details.fileHolder != null) {
        SetModeDialog(
            file = details.fileHolder,
            onDismissRequest = { showSetModeDialog = false },
            onPermissionsChanged = { onReload() }
        )
    }

    if (showSetOwnerDialog && details.fileHolder != null) {
        SetOwnerGroupDialog(
            file = details.fileHolder,
            isOwner = true,
            onDismissRequest = { showSetOwnerDialog = false },
            onChanged = { onReload() }
        )
    }

    if (showSetGroupDialog && details.fileHolder != null) {
        SetOwnerGroupDialog(
            file = details.fileHolder,
            isOwner = false,
            onDismissRequest = { showSetGroupDialog = false },
            onChanged = { onReload() }
        )
    }

    if (showSetSeLinuxDialog && details.fileHolder != null) {
        SetSeLinuxContextDialog(
            file = details.fileHolder,
            onDismissRequest = { showSetSeLinuxDialog = false },
            onContextChanged = { onReload() }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
private fun LazyListScope.multipleFilesLazyItems(
    details: PropertiesState.MultipleContentProperties,
    dirComp: PropertiesState.DirectoryComparisonResult?,
    checksumGroups: List<Pair<String, List<ContentHolder>>>,
    twoFileComparison: PropertiesState.TwoFileChecksumComparison?,
    tab: FilesTab,
    context: android.content.Context,
    coroutineScope: kotlinx.coroutines.CoroutineScope,
    deletedPaths: Set<String>,
    selectedFilter: String,
    onSelectFilter: (String) -> Unit,
    viewMode: String,
    onToggleViewMode: () -> Unit,
    actionButtonLayout: String,
    onToggleActionButtonLayout: () -> Unit,
    showColumnSelector: Boolean,
    onToggleColumnSelector: () -> Unit,
    enabledColumns: List<String>,
    onUpdateColumns: (List<String>) -> Unit,
    showHashAlgorithmHelp: Boolean,
    onToggleHashAlgorithmHelp: () -> Unit,
    onSwitchHashAlgorithm: ((String) -> Unit)?,
    onDemandSha256Map: Map<String, String>,
    isComputingAllSha256: Boolean,
    onComputeAllSha256: (List<PropertiesState.DirectoryComparisonEntry>) -> Unit,
    onComputeSingleSha256: (ContentHolder) -> Unit,
    onInspectSingleFileInPopup: (ContentHolder) -> Unit,
    onDeleteFile: (ContentHolder) -> Unit,
    onAddMoreForComparison: () -> Unit,
    onDismissRequest: () -> Unit
) {
    // 1. Selection Summary Header
    item(key = "selection_summary", contentType = "header") {
        PropertySection(title = stringResource(R.string.selection_summary)) {
            PropertyRow(
                icon = Icons.Default.SelectAll,
                label = stringResource(R.string.selected),
                value = stringResource(R.string.items_count, details.selectedFileCount),
                maxLines = Int.MAX_VALUE
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
            OutlinedButton(
                onClick = onAddMoreForComparison,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Space(8.dp)
                Text("Add more files / folders for comparison")
            }
        }
    }

    // 2. Directory Comparison Section (Virtualized!)
    if (details.hasDirectoryComparison) {
        directoryComparisonLazyItems(
            comp = dirComp,
            statusText = details.directoryComparisonStatus,
            progress = details.directoryComparisonProgress,
            tab = tab,
            context = context,
            coroutineScope = coroutineScope,
            deletedPaths = deletedPaths,
            selectedFilter = selectedFilter,
            onSelectFilter = onSelectFilter,
            viewMode = viewMode,
            onToggleViewMode = onToggleViewMode,
            actionButtonLayout = actionButtonLayout,
            onToggleActionButtonLayout = onToggleActionButtonLayout,
            showColumnSelector = showColumnSelector,
            onToggleColumnSelector = onToggleColumnSelector,
            enabledColumns = enabledColumns,
            onUpdateColumns = onUpdateColumns,
            showHashAlgorithmHelp = showHashAlgorithmHelp,
            onToggleHashAlgorithmHelp = onToggleHashAlgorithmHelp,
            onSwitchHashAlgorithm = onSwitchHashAlgorithm,
            onDemandSha256Map = onDemandSha256Map,
            isComputingAllSha256 = isComputingAllSha256,
            onComputeAllSha256 = onComputeAllSha256,
            onComputeSingleSha256 = onComputeSingleSha256,
            onInspectSingleFileInPopup = onInspectSingleFileInPopup,
            onDeleteFile = onDeleteFile,
            onDismissRequest = onDismissRequest
        )
    }

    // 3. Classic Checksums & Duplicates Section (with 2-File Comparison + Duplicate & Checksum Groups)
    checksumsAndDuplicatesLazyItems(
        checksumStatusFlow = details.checksumStatus,
        checksumProgressFlow = details.checksumProgress,
        twoFileComparison = twoFileComparison,
        checksumGroups = checksumGroups,
        deletedPaths = deletedPaths,
        tab = tab,
        context = context,
        coroutineScope = coroutineScope,
        onInspectSingleFileInPopup = onInspectSingleFileInPopup,
        onDeleteFile = onDeleteFile,
        onDismissRequest = onDismissRequest
    )
}

/**
 * Preserves the full classic Checksums & Duplicates view (2-File Checksum Comparison + Checksum/Duplicate
 * Groups with Copy Hash, Open duplicates in new tab, Open, Locate, Inspect in Popup, and Delete).
 */
private fun LazyListScope.checksumsAndDuplicatesLazyItems(
    checksumStatusFlow: StateFlow<String>?,
    checksumProgressFlow: StateFlow<CalculationProgress>?,
    twoFileComparison: PropertiesState.TwoFileChecksumComparison?,
    checksumGroups: List<Pair<String, List<ContentHolder>>>,
    deletedPaths: Set<String>,
    tab: FilesTab,
    context: android.content.Context,
    coroutineScope: kotlinx.coroutines.CoroutineScope,
    onInspectSingleFileInPopup: (ContentHolder) -> Unit,
    onDeleteFile: (ContentHolder) -> Unit,
    onDismissRequest: () -> Unit
) {
    item(key = "checksums_header", contentType = "header") {
        PropertySection(title = stringResource(R.string.checksums_and_duplicates)) {
            if (checksumStatusFlow != null && checksumProgressFlow != null) {
                AsyncPropertyRow(
                    icon = Icons.Default.Fingerprint,
                    label = stringResource(R.string.md5_checksum),
                    valueFlow = checksumStatusFlow,
                    progressFlow = checksumProgressFlow
                )
            }
            twoFileComparison?.let { comp ->
                val bothMatch = comp.isMatch
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (bothMatch) {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                        } else {
                            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f)
                        }
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = if (bothMatch) {
                                "2-File Checksum Comparison: IDENTICAL"
                            } else {
                                "2-File Checksum Comparison: DIFFERENT"
                            },
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = if (bothMatch) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.error
                            }
                        )
                        TwoFileChecksumRow(
                            fileName = comp.file1Name,
                            primaryAlgo = comp.primaryAlgorithm,
                            primaryHash = comp.file1Md5,
                            sha256 = comp.file1Sha256
                        )
                        TwoFileChecksumRow(
                            fileName = comp.file2Name,
                            primaryAlgo = comp.primaryAlgorithm,
                            primaryHash = comp.file2Md5,
                            sha256 = comp.file2Sha256
                        )
                    }
                }
            }
        }
    }

    if (checksumGroups.isNotEmpty()) {
        items(
            items = checksumGroups,
            key = { (hash, _) -> "dup_group_$hash" },
            contentType = { "duplicate_group" }
        ) { (hash, groupFiles) ->
            val visibleFiles = groupFiles.filter { it.uniquePath !in deletedPaths }
            if (visibleFiles.isNotEmpty()) {
                DuplicateGroupCard(
                    hash = hash,
                    visibleFiles = visibleFiles,
                    tab = tab,
                    context = context,
                    coroutineScope = coroutineScope,
                    onInspectSingleFileInPopup = onInspectSingleFileInPopup,
                    onDeleteFile = onDeleteFile,
                    onDismissRequest = onDismissRequest
                )
            }
        }
    }
}

@Composable
private fun TwoFileChecksumRow(
    fileName: String,
    primaryAlgo: String,
    primaryHash: String,
    sha256: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = fileName,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                softWrap = false
            )
            Text(
                text = "$primaryAlgo: ${primaryHash.take(12)}... • SHA-256: ${sha256.take(12)}...",
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                softWrap = false
            )
        }
        if (sha256.isNotBlank()) {
            TooltipIconButton(
                tooltip = "Copy SHA-256 ($fileName)",
                onClick = {
                    sha256.copyToClipboard()
                    showMsg(globalClass.getString(R.string.copied_to_clipboard))
                },
                modifier = Modifier.size(30.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.ContentCopy,
                    contentDescription = "Copy SHA-256",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun DuplicateGroupCard(
    hash: String,
    visibleFiles: List<ContentHolder>,
    tab: FilesTab,
    context: android.content.Context,
    coroutineScope: kotlinx.coroutines.CoroutineScope,
    onInspectSingleFileInPopup: (ContentHolder) -> Unit,
    onDeleteFile: (ContentHolder) -> Unit,
    onDismissRequest: () -> Unit
) {
    val isDuplicateGroup = visibleFiles.size > 1
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    SelectionContainer {
                        Text(
                            text = hash,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontFamily = FontFamily.Monospace
                            ),
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            softWrap = false
                        )
                    }
                    Text(
                        text = if (isDuplicateGroup) {
                            "${visibleFiles.size} duplicate files"
                        } else {
                            "1 file (distinct checksum)"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
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
                    // Visually distinct "Open duplicates in new tab" icon (Icons.Rounded.Tab)
                    TooltipIconButton(
                        tooltip = stringResource(R.string.open_duplicates_in_new_tab),
                        onClick = {
                            onDismissRequest()
                            globalClass.mainActivityManager.addTabAndSelect(
                                FilesTab(
                                    VirtualFileHolder(
                                        type = VirtualFileHolder.DUPLICATES,
                                        customItems = visibleFiles,
                                        customTitle = if (isDuplicateGroup) {
                                            "Duplicates (${hash.take(6)})"
                                        } else {
                                            "Checksum (${hash.take(6)})"
                                        }
                                    )
                                )
                            )
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Tab,
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
                        .padding(vertical = 2.dp),
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
                            imageVector = if (file.isFolder) Icons.Default.FolderOpen else Icons.AutoMirrored.Rounded.InsertDriveFile,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { onInspectSingleFileInPopup(file) },
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = file.displayName,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                softWrap = false
                            )
                            Text(
                                text = file.uniquePath,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                softWrap = false
                            )
                            Text(
                                text = file.size.toFormattedSize(),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                        // 2x2 Grid of Quick Actions: Copy Hash, Open, Locate (Open Containing Folder), Delete
                        Column(
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                            horizontalAlignment = Alignment.End
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                TooltipIconButton(
                                    tooltip = stringResource(R.string.copy_hash),
                                    onClick = {
                                        hash.copyToClipboard()
                                        showMsg(globalClass.getString(R.string.copied_to_clipboard))
                                    },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.ContentCopy,
                                        contentDescription = stringResource(R.string.copy_hash),
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(15.dp)
                                    )
                                }
                                TooltipIconButton(
                                    tooltip = stringResource(R.string.open),
                                    onClick = {
                                        onDismissRequest()
                                        tab.openFile(context, file)
                                    },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                                        contentDescription = stringResource(R.string.open),
                                        modifier = Modifier.size(15.dp)
                                    )
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                TooltipIconButton(
                                    tooltip = stringResource(R.string.open_containing_folder),
                                    onClick = {
                                        coroutineScope.launch {
                                            globalClass.mainActivityManager.addTabAndSelect(FilesTab(file))
                                            onDismissRequest()
                                        }
                                    },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.FolderOpen,
                                        contentDescription = stringResource(R.string.open_containing_folder),
                                        modifier = Modifier.size(15.dp)
                                    )
                                }
                                TooltipIconButton(
                                    tooltip = stringResource(R.string.delete),
                                    onClick = { onDeleteFile(file) },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Delete,
                                        contentDescription = stringResource(R.string.delete),
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(15.dp)
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

@OptIn(ExperimentalLayoutApi::class)
private fun LazyListScope.directoryComparisonLazyItems(
    comp: PropertiesState.DirectoryComparisonResult?,
    statusText: StateFlow<String>?,
    progress: StateFlow<CalculationProgress>?,
    tab: FilesTab,
    context: android.content.Context,
    coroutineScope: kotlinx.coroutines.CoroutineScope,
    deletedPaths: Set<String>,
    selectedFilter: String,
    onSelectFilter: (String) -> Unit,
    viewMode: String,
    onToggleViewMode: () -> Unit,
    actionButtonLayout: String,
    onToggleActionButtonLayout: () -> Unit,
    showColumnSelector: Boolean,
    onToggleColumnSelector: () -> Unit,
    enabledColumns: List<String>,
    onUpdateColumns: (List<String>) -> Unit,
    showHashAlgorithmHelp: Boolean,
    onToggleHashAlgorithmHelp: () -> Unit,
    onSwitchHashAlgorithm: ((String) -> Unit)?,
    onDemandSha256Map: Map<String, String>,
    isComputingAllSha256: Boolean,
    onComputeAllSha256: (List<PropertiesState.DirectoryComparisonEntry>) -> Unit,
    onComputeSingleSha256: (ContentHolder) -> Unit,
    onInspectSingleFileInPopup: (ContentHolder) -> Unit,
    onDeleteFile: (ContentHolder) -> Unit,
    onDismissRequest: () -> Unit
) {
    val activeEntries = comp?.entries?.mapNotNull { entry ->
        val remaining = entry.entriesByFolder.filter { it.holder.uniquePath !in deletedPaths }
        if (remaining.isEmpty()) {
            null
        } else {
            val newStatus = when {
                remaining.size < comp.folderNames.size -> PropertiesState.DirectoryEntryStatus.UNIQUE
                else -> entry.status
            }
            entry.copy(status = newStatus, entriesByFolder = remaining)
        }
    } ?: emptyList()

    val matchingFilesCount = activeEntries.count { it.status == PropertiesState.DirectoryEntryStatus.IDENTICAL }
    val mismatchedFilesCount = activeEntries.count { it.status == PropertiesState.DirectoryEntryStatus.MODIFIED }
    val uniqueFilesCount = activeEntries.count { it.status == PropertiesState.DirectoryEntryStatus.UNIQUE }
    val uniqueByFolder = linkedMapOf<String, Int>().apply {
        comp?.folderNames?.forEach { put(it, 0) }
        activeEntries.filter { it.status == PropertiesState.DirectoryEntryStatus.UNIQUE }.forEach { entry ->
            entry.entriesByFolder.forEach { info ->
                put(info.folderName, (this[info.folderName] ?: 0) + 1)
            }
        }
    }

    val filteredEntries = activeEntries.filter { entry ->
        when (selectedFilter) {
            "MODIFIED" -> entry.status == PropertiesState.DirectoryEntryStatus.MODIFIED
            "UNIQUE" -> entry.status == PropertiesState.DirectoryEntryStatus.UNIQUE
            "IDENTICAL" -> entry.status == PropertiesState.DirectoryEntryStatus.IDENTICAL
            else -> true
        }
    }

    item(key = "dir_comp_header", contentType = "dir_comp_header") {
        PropertySection(title = "Directory Comparison") {
            if (statusText != null && progress != null) {
                AsyncPropertyRow(
                    icon = Icons.AutoMirrored.Rounded.CompareArrows,
                    label = "Status",
                    valueFlow = statusText,
                    progressFlow = progress
                )
            }

            if (comp != null) {
                val isAllMatching = mismatchedFilesCount == 0 && uniqueFilesCount == 0
                val leftColor = Color(0xFF1E88E5)
                val rightColor = Color(0xFFD84315)

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isAllMatching) {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                        } else {
                            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f)
                        }
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = if (isAllMatching) {
                                "Directory Comparison: IDENTICAL ($matchingFilesCount matching files)"
                            } else {
                                "Directory Comparison: DIFFERENT (${activeEntries.size} total file paths)"
                            },
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = if (isAllMatching) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.error
                            }
                        )

                        comp.folderNames.forEachIndexed { idx, name ->
                            val path = comp.folderPaths.getOrNull(idx) ?: ""
                            val sideTag = when (idx) {
                                0 -> "LEFT"
                                1 -> "RIGHT"
                                else -> "DIR #${idx + 1}"
                            }
                            val sideColor = when (idx) {
                                0 -> leftColor
                                1 -> rightColor
                                else -> MaterialTheme.colorScheme.tertiary
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Surface(
                                    color = sideColor.copy(alpha = 0.16f),
                                    shape = RoundedCornerShape(4.dp),
                                    border = BorderStroke(1.dp, sideColor.copy(alpha = 0.6f))
                                ) {
                                    Text(
                                        text = sideTag,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = sideColor,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = name,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        softWrap = false
                                    )
                                    if (path.isNotBlank()) {
                                        Text(
                                            text = path,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            softWrap = false
                                        )
                                    }
                                }
                            }
                        }

                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 2.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )

                        Text(
                            text = "Identical (Matching files): $matchingFilesCount  •  Different content (Mismatched): $mismatchedFilesCount",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        uniqueByFolder.entries.forEachIndexed { idx, (folderLabel, count) ->
                            val sideTag = when (idx) {
                                0 -> "LEFT"
                                1 -> "RIGHT"
                                else -> "DIR #${idx + 1}"
                            }
                            val sideColor = when (idx) {
                                0 -> leftColor
                                1 -> rightColor
                                else -> MaterialTheme.colorScheme.tertiary
                            }
                            Text(
                                text = "Only in $sideTag ($folderLabel): $count",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = if (count > 0) sideColor else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Hash Algorithm, On-Demand SHA-256 & Transparency Controls
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.VerifiedUser,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Column {
                                    Text(
                                        text = "Active Hash: ${comp.hashAlgorithm}",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = if (comp.hashAlgorithm == "xxHash3") {
                                            "Fast routine comparison • SHA-256 computed on demand"
                                        } else {
                                            "Full ${comp.hashAlgorithm} computed during scan"
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            TextButton(
                                onClick = onToggleHashAlgorithmHelp,
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Info,
                                    contentDescription = null,
                                    modifier = Modifier.size(15.dp)
                                )
                                Space(4.dp)
                                Text(
                                    text = if (showHashAlgorithmHelp) "Hide Info" else "Hash Info",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (onSwitchHashAlgorithm != null) {
                                listOf("xxHash3", "SHA-256", "MD5").forEach { algo ->
                                    FilterChip(
                                        selected = comp.hashAlgorithm == algo,
                                        onClick = {
                                            if (comp.hashAlgorithm != algo) {
                                                onSwitchHashAlgorithm(algo)
                                            }
                                        },
                                        label = {
                                            Text(
                                                text = if (algo == "xxHash3") "xxHash3 (Fast)" else algo,
                                                style = MaterialTheme.typography.labelSmall
                                            )
                                        }
                                    )
                                }
                            }
                            OutlinedButton(
                                onClick = { onComputeAllSha256(filteredEntries) },
                                enabled = !isComputingAllSha256 && filteredEntries.isNotEmpty(),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Fingerprint,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp)
                                )
                                Space(4.dp)
                                Text(
                                    text = if (isComputingAllSha256) "Computing SHA-256..." else "Verify All with SHA-256",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }

                        AnimatedVisibility(visible = showHashAlgorithmHelp) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        MaterialTheme.colorScheme.surfaceContainerLow,
                                        RoundedCornerShape(6.dp)
                                    )
                                    .padding(8.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "How Comparison & Hashing Work",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "• SHA-1 & SHA-256 Behavior: Single-file properties compute MD5, SHA-1, and SHA-256 automatically. Multi-file directory comparisons use fast xxHash3 by default and compute SHA-256 on demand per file or for all visible files.",
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Text(
                                    text = "• xxHash3 (64-bit Default): High-speed non-cryptographic algorithm ideal for routine comparisons after a byte-size check.",
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Text(
                                    text = "• SHA-256 (On-Demand / Integrity): Cryptographic 256-bit hash for tamper verification.",
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Text(
                                    text = "• MD5 (Legacy): Retained for legacy .md5 manifest verification.",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }

                // Filter Chips & View Mode / Layout Toolbar
                if (activeEntries.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilterChip(
                            selected = selectedFilter == "ALL",
                            onClick = { onSelectFilter("ALL") },
                            label = { Text("All (${activeEntries.size})") }
                        )
                        FilterChip(
                            selected = selectedFilter == "MODIFIED",
                            onClick = { onSelectFilter("MODIFIED") },
                            label = { Text("Different ($mismatchedFilesCount)") }
                        )
                        FilterChip(
                            selected = selectedFilter == "UNIQUE",
                            onClick = { onSelectFilter("UNIQUE") },
                            label = { Text("Unique ($uniqueFilesCount)") }
                        )
                        FilterChip(
                            selected = selectedFilter == "IDENTICAL",
                            onClick = { onSelectFilter("IDENTICAL") },
                            label = { Text("Identical ($matchingFilesCount)") }
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilterChip(
                            selected = viewMode == "TABLE",
                            onClick = onToggleViewMode,
                            leadingIcon = {
                                Icon(
                                    imageVector = if (viewMode == "TABLE") Icons.Rounded.TableChart else Icons.Rounded.ViewAgenda,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            label = {
                                Text(
                                    text = if (viewMode == "TABLE") "Spreadsheet View (Tap for Cards)" else "Cards View (Tap for Spreadsheet)",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        )
                        if (viewMode == "CARDS") {
                            FilterChip(
                                selected = actionButtonLayout == "GRID_2X2",
                                onClick = onToggleActionButtonLayout,
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Rounded.GridView,
                                        contentDescription = null,
                                        modifier = Modifier.size(15.dp)
                                    )
                                },
                                label = {
                                    Text(
                                        text = if (actionButtonLayout == "GRID_2X2") "Actions: 2×2 Grid" else "Actions: 1×4 Column",
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            )
                        } else {
                            FilterChip(
                                selected = showColumnSelector,
                                onClick = onToggleColumnSelector,
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Rounded.ViewColumn,
                                        contentDescription = null,
                                        modifier = Modifier.size(15.dp)
                                    )
                                },
                                label = {
                                    Text(
                                        text = "Columns (${enabledColumns.size})",
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            )
                        }
                    }

                    AnimatedVisibility(visible = viewMode == "TABLE" && showColumnSelector) {
                        val allColumns = listOf(
                            "Filename",
                            "Result",
                            "Left Date",
                            "Right Date",
                            "Left Size",
                            "Right Size",
                            "Extension"
                        )
                        FlowRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            allColumns.forEach { col ->
                                val isSelected = col in enabledColumns
                                FilterChip(
                                    selected = isSelected,
                                    onClick = {
                                        val next = if (isSelected && enabledColumns.size > 1) {
                                            enabledColumns - col
                                        } else if (!isSelected) {
                                            allColumns.filter { it in enabledColumns || it == col }
                                        } else {
                                            enabledColumns
                                        }
                                        onUpdateColumns(next)
                                    },
                                    label = { Text(col, style = MaterialTheme.typography.labelSmall) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (comp != null && filteredEntries.isNotEmpty()) {
        if (viewMode == "TABLE") {
            item(key = "winmerge_table_view", contentType = "table_view") {
                WinMergeSpreadsheetTable(
                    comp = comp,
                    entries = filteredEntries,
                    enabledColumns = enabledColumns,
                    tab = tab,
                    context = context,
                    coroutineScope = coroutineScope,
                    onInspectSingleFileInPopup = onInspectSingleFileInPopup,
                    onDeleteFile = onDeleteFile,
                    onDismissRequest = onDismissRequest
                )
            }
        } else {
            items(
                items = filteredEntries,
                key = { entry -> "comp_entry_${entry.relativePath}" },
                contentType = { "comp_entry_card" }
            ) { entry ->
                DirectoryComparisonEntryCard(
                    entry = entry,
                    actionButtonLayout = actionButtonLayout,
                    onDemandSha256Map = onDemandSha256Map,
                    onComputeSingleSha256 = onComputeSingleSha256,
                    onInspectSingleFileInPopup = onInspectSingleFileInPopup,
                    tab = tab,
                    context = context,
                    coroutineScope = coroutineScope,
                    onDeleteFile = onDeleteFile,
                    onDismissRequest = onDismissRequest
                )
            }
        }
    }
}

@Composable
private fun DirectoryComparisonEntryCard(
    entry: PropertiesState.DirectoryComparisonEntry,
    actionButtonLayout: String,
    onDemandSha256Map: Map<String, String>,
    onComputeSingleSha256: (ContentHolder) -> Unit,
    onInspectSingleFileInPopup: (ContentHolder) -> Unit,
    tab: FilesTab,
    context: android.content.Context,
    coroutineScope: kotlinx.coroutines.CoroutineScope,
    onDeleteFile: (ContentHolder) -> Unit,
    onDismissRequest: () -> Unit
) {
    val leftColor = Color(0xFF1E88E5)
    val rightColor = Color(0xFFD84315)

    val firstUniqueIdx = entry.entriesByFolder.firstOrNull()?.folderIndex ?: 0
    val statusLabel = when (entry.status) {
        PropertiesState.DirectoryEntryStatus.IDENTICAL -> "IDENTICAL"
        PropertiesState.DirectoryEntryStatus.MODIFIED -> "DIFFERENT"
        PropertiesState.DirectoryEntryStatus.UNIQUE -> {
            val sidePrefix = when (firstUniqueIdx) {
                0 -> "ONLY IN LEFT"
                1 -> "ONLY IN RIGHT"
                else -> "ONLY IN DIR #${firstUniqueIdx + 1}"
            }
            val presentIn = entry.entriesByFolder.joinToString(", ") { it.folderName }
            "$sidePrefix ($presentIn)"
        }
    }
    val statusColor = when (entry.status) {
        PropertiesState.DirectoryEntryStatus.IDENTICAL -> MaterialTheme.colorScheme.primary
        PropertiesState.DirectoryEntryStatus.MODIFIED -> MaterialTheme.colorScheme.error
        PropertiesState.DirectoryEntryStatus.UNIQUE -> when (firstUniqueIdx) {
            0 -> leftColor
            1 -> rightColor
            else -> MaterialTheme.colorScheme.tertiary
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        shape = RoundedCornerShape(8.dp),
        border = if (entry.status == PropertiesState.DirectoryEntryStatus.UNIQUE) {
            BorderStroke(1.dp, statusColor.copy(alpha = 0.45f))
        } else null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = if (entry.isFolder) Icons.Default.FolderOpen else Icons.AutoMirrored.Rounded.InsertDriveFile,
                        contentDescription = null,
                        tint = statusColor,
                        modifier = Modifier.size(18.dp)
                    )
                    SelectionContainer {
                        Text(
                            text = entry.relativePath,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            softWrap = false
                        )
                    }
                }
                Space(6.dp)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (entry.entriesByFolder.size > 1) {
                        TooltipIconButton(
                            tooltip = stringResource(R.string.open_duplicates_in_new_tab),
                            onClick = {
                                onDismissRequest()
                                globalClass.mainActivityManager.addTabAndSelect(
                                    FilesTab(
                                        VirtualFileHolder(
                                            type = VirtualFileHolder.DUPLICATES,
                                            customItems = entry.entriesByFolder.map { it.holder },
                                            customTitle = "Compare (${entry.relativePath.substringAfterLast('/')})"
                                        )
                                    )
                                )
                            },
                            modifier = Modifier.size(26.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Tab,
                                contentDescription = stringResource(R.string.open_duplicates_in_new_tab),
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }
                    Surface(
                        color = statusColor.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(4.dp),
                        border = BorderStroke(1.dp, statusColor.copy(alpha = 0.5f))
                    ) {
                        Text(
                            text = statusLabel,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = statusColor,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            entry.entriesByFolder.forEach { info ->
                val sideTag = when (info.folderIndex) {
                    0 -> "LEFT"
                    1 -> "RIGHT"
                    else -> "DIR #${info.folderIndex + 1}"
                }
                val sideColor = when (info.folderIndex) {
                    0 -> leftColor
                    1 -> rightColor
                    else -> MaterialTheme.colorScheme.tertiary
                }
                val sha256Val = onDemandSha256Map[info.holder.uniquePath]?.ifBlank { info.sha256 } ?: info.sha256

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            MaterialTheme.colorScheme.surfaceContainerLow,
                            RoundedCornerShape(6.dp)
                        )
                        .border(
                            width = 1.dp,
                            color = sideColor.copy(alpha = 0.28f),
                            shape = RoundedCornerShape(6.dp)
                        )
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onInspectSingleFileInPopup(info.holder) },
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Surface(
                                color = sideColor.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = sideTag,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = sideColor,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                )
                            }
                            Text(
                                text = "${info.folderName} • ${info.size.toFormattedSize()} • ${info.lastModified.toFormattedDate()}",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                softWrap = false
                            )
                        }

                        if (info.md5.isNotBlank()) {
                            SelectionContainer {
                                Text(
                                    text = "${info.hashAlgorithm}: ${info.md5}",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontFamily = FontFamily.Monospace
                                    ),
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        if (sha256Val.isNotBlank()) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                SelectionContainer(modifier = Modifier.weight(1f, fill = false)) {
                                    Text(
                                        text = "SHA-256: $sha256Val",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontFamily = FontFamily.Monospace
                                        ),
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                }
                                if (sha256Val != "Calculating...") {
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.6f)),
                                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f),
                                        modifier = Modifier.clickable {
                                            sha256Val.copyToClipboard()
                                            showMsg(globalClass.getString(R.string.copied_to_clipboard))
                                        }
                                    ) {
                                        Text(
                                            text = "Copy SHA-256",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.error,
                                            fontWeight = FontWeight.SemiBold,
                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                        )
                                    }
                                }
                            }
                        } else if (!entry.isFolder) {
                            Text(
                                text = "Tap [SHA-256] to compute cryptographic digest",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.clickable { onComputeSingleSha256(info.holder) }
                            )
                        }
                    }

                    // Right side: Action buttons (Copy Hash, Open, Locate / Open Containing Folder, Delete)
                    EntryActionButtons(
                        info = info,
                        isFolder = entry.isFolder,
                        layoutMode = actionButtonLayout,
                        sha256Val = sha256Val,
                        onComputeSingleSha256 = { onComputeSingleSha256(info.holder) },
                        onOpen = {
                            onDismissRequest()
                            tab.openFile(context, info.holder)
                        },
                        onOpenContainingFolder = {
                            coroutineScope.launch {
                                globalClass.mainActivityManager.addTabAndSelect(FilesTab(info.holder))
                                onDismissRequest()
                            }
                        },
                        onDelete = { onDeleteFile(info.holder) }
                    )
                }
            }
        }
    }
}

@Composable
private fun EntryActionButtons(
    info: PropertiesState.DirectoryEntryFileInfo,
    isFolder: Boolean,
    layoutMode: String,
    sha256Val: String,
    onComputeSingleSha256: () -> Unit,
    onOpen: () -> Unit,
    onOpenContainingFolder: () -> Unit,
    onDelete: () -> Unit
) {
    val copyAction: () -> Unit = {
        val textToCopy = if (sha256Val.isNotBlank() && sha256Val != "Calculating...") {
            sha256Val
        } else if (info.md5.isNotBlank()) {
            info.md5
        } else {
            onComputeSingleSha256()
            ""
        }
        if (textToCopy.isNotBlank()) {
            textToCopy.copyToClipboard()
            showMsg(globalClass.getString(R.string.copied_to_clipboard))
        }
    }

    if (layoutMode == "COL_1X4") {
        Column(
            verticalArrangement = Arrangement.spacedBy(2.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            TooltipIconButton(
                tooltip = stringResource(R.string.copy_hash),
                onClick = copyAction,
                modifier = Modifier.size(26.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.ContentCopy,
                    contentDescription = stringResource(R.string.copy_hash),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(15.dp)
                )
            }
            if (!isFolder) {
                TooltipIconButton(
                    tooltip = stringResource(R.string.open),
                    onClick = onOpen,
                    modifier = Modifier.size(26.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                        contentDescription = stringResource(R.string.open),
                        modifier = Modifier.size(15.dp)
                    )
                }
            }
            TooltipIconButton(
                tooltip = stringResource(R.string.open_containing_folder),
                onClick = onOpenContainingFolder,
                modifier = Modifier.size(26.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.FolderOpen,
                    contentDescription = stringResource(R.string.open_containing_folder),
                    modifier = Modifier.size(15.dp)
                )
            }
            TooltipIconButton(
                tooltip = stringResource(R.string.delete),
                onClick = onDelete,
                modifier = Modifier.size(26.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Delete,
                    contentDescription = stringResource(R.string.delete),
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(15.dp)
                )
            }
        }
    } else {
        Column(
            verticalArrangement = Arrangement.spacedBy(2.dp),
            horizontalAlignment = Alignment.End
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                TooltipIconButton(
                    tooltip = stringResource(R.string.copy_hash),
                    onClick = copyAction,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ContentCopy,
                        contentDescription = stringResource(R.string.copy_hash),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(15.dp)
                    )
                }
                if (!isFolder) {
                    TooltipIconButton(
                        tooltip = stringResource(R.string.open),
                        onClick = onOpen,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                            contentDescription = stringResource(R.string.open),
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                TooltipIconButton(
                    tooltip = stringResource(R.string.open_containing_folder),
                    onClick = onOpenContainingFolder,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.FolderOpen,
                        contentDescription = stringResource(R.string.open_containing_folder),
                        modifier = Modifier.size(15.dp)
                    )
                }
                TooltipIconButton(
                    tooltip = stringResource(R.string.delete),
                    onClick = onDelete,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Delete,
                        contentDescription = stringResource(R.string.delete),
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }
        }
    }
}

/**
 * Compact, mobile-friendly WinMerge-style spreadsheet view with configurable columns
 * plus an Actions column for Copy Hash, Open, Locate (Open Containing Folder), and Delete.
 */
@Composable
private fun WinMergeSpreadsheetTable(
    comp: PropertiesState.DirectoryComparisonResult,
    entries: List<PropertiesState.DirectoryComparisonEntry>,
    enabledColumns: List<String>,
    tab: FilesTab,
    context: android.content.Context,
    coroutineScope: kotlinx.coroutines.CoroutineScope,
    onInspectSingleFileInPopup: (ContentHolder) -> Unit,
    onDeleteFile: (ContentHolder) -> Unit,
    onDismissRequest: () -> Unit
) {
    val hScroll = rememberScrollState()
    val leftName = comp.folderNames.getOrNull(0) ?: "Left"
    val rightName = comp.folderNames.getOrNull(1) ?: "Right"
    val leftColor = Color(0xFF1E88E5)
    val rightColor = Color(0xFFD84315)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(hScroll)
        ) {
            Row(
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                enabledColumns.forEach { col ->
                    val width = when (col) {
                        "Filename" -> 190.dp
                        "Result" -> 135.dp
                        "Left Date", "Right Date" -> 130.dp
                        "Left Size", "Right Size" -> 90.dp
                        "Extension" -> 75.dp
                        else -> 110.dp
                    }
                    val headerTitle = when (col) {
                        "Left Date" -> "Left Date ($leftName)"
                        "Right Date" -> "Right Date ($rightName)"
                        "Left Size" -> "Left Size"
                        "Right Size" -> "Right Size"
                        else -> col
                    }
                    Text(
                        text = headerTitle,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .width(width)
                            .padding(end = 8.dp)
                    )
                }
                Text(
                    text = "Actions",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.width(124.dp)
                )
            }
            HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)

            entries.forEachIndexed { idx, entry ->
                val leftInfo = entry.entriesByFolder.find { it.folderIndex == 0 }
                val rightInfo = entry.entriesByFolder.find { it.folderIndex == 1 }
                val primaryInfo = leftInfo ?: rightInfo ?: entry.entriesByFolder.firstOrNull()
                val ext = entry.relativePath.substringAfterLast('.', "").lowercase()

                val resultText = when (entry.status) {
                    PropertiesState.DirectoryEntryStatus.IDENTICAL -> "Identical"
                    PropertiesState.DirectoryEntryStatus.MODIFIED -> "Different"
                    PropertiesState.DirectoryEntryStatus.UNIQUE -> {
                        if (leftInfo != null && rightInfo == null) {
                            "Left only"
                        } else if (rightInfo != null && leftInfo == null) {
                            "Right only"
                        } else {
                            "Only in ${entry.entriesByFolder.joinToString { it.folderName }}"
                        }
                    }
                }
                val resultColor = when (entry.status) {
                    PropertiesState.DirectoryEntryStatus.IDENTICAL -> MaterialTheme.colorScheme.primary
                    PropertiesState.DirectoryEntryStatus.MODIFIED -> MaterialTheme.colorScheme.error
                    PropertiesState.DirectoryEntryStatus.UNIQUE -> {
                        if (leftInfo != null && rightInfo == null) leftColor else rightColor
                    }
                }

                Row(
                    modifier = Modifier
                        .background(
                            if (idx % 2 == 0) Color.Transparent
                            else MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.5f)
                        )
                        .clickable {
                            val targetHolder = primaryInfo?.holder
                            if (targetHolder != null) {
                                onInspectSingleFileInPopup(targetHolder)
                            }
                        }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    enabledColumns.forEach { col ->
                        val width = when (col) {
                            "Filename" -> 190.dp
                            "Result" -> 135.dp
                            "Left Date", "Right Date" -> 130.dp
                            "Left Size", "Right Size" -> 90.dp
                            "Extension" -> 75.dp
                            else -> 110.dp
                        }
                        val cellText = when (col) {
                            "Filename" -> entry.relativePath
                            "Result" -> resultText
                            "Left Date" -> leftInfo?.lastModified?.toFormattedDate() ?: "—"
                            "Right Date" -> rightInfo?.lastModified?.toFormattedDate() ?: "—"
                            "Left Size" -> leftInfo?.size?.toFormattedSize() ?: "—"
                            "Right Size" -> rightInfo?.size?.toFormattedSize() ?: "—"
                            "Extension" -> ext.ifBlank { "—" }
                            else -> ""
                        }
                        Text(
                            text = cellText,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = if (col == "Filename" || col == "Result") FontWeight.Medium else FontWeight.Normal,
                            color = if (col == "Result") resultColor else MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .width(width)
                                .padding(end = 8.dp)
                        )
                    }

                    Row(
                        modifier = Modifier.width(124.dp),
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (primaryInfo != null) {
                            TooltipIconButton(
                                tooltip = stringResource(R.string.copy_hash),
                                onClick = {
                                    if (primaryInfo.md5.isNotBlank()) {
                                        primaryInfo.md5.copyToClipboard()
                                        showMsg(globalClass.getString(R.string.copied_to_clipboard))
                                    }
                                },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.ContentCopy,
                                    contentDescription = stringResource(R.string.copy_hash),
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                            TooltipIconButton(
                                tooltip = stringResource(R.string.open),
                                onClick = {
                                    onDismissRequest()
                                    tab.openFile(context, primaryInfo.holder)
                                },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                                    contentDescription = stringResource(R.string.open),
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                            TooltipIconButton(
                                tooltip = stringResource(R.string.open_containing_folder),
                                onClick = {
                                    coroutineScope.launch {
                                        globalClass.mainActivityManager.addTabAndSelect(FilesTab(primaryInfo.holder))
                                        onDismissRequest()
                                    }
                                },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FolderOpen,
                                    contentDescription = stringResource(R.string.open_containing_folder),
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                            TooltipIconButton(
                                tooltip = stringResource(R.string.delete),
                                onClick = { onDeleteFile(primaryInfo.holder) },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Delete,
                                    contentDescription = stringResource(R.string.delete),
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                        }
                    }
                }
                HorizontalDivider(
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                )
            }
        }
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
                    .padding(14.dp),
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
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(top = 2.dp)
                    .size(20.dp)
            )
            Space(12.dp)
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.width(90.dp),
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
                    modifier = Modifier
                        .padding(top = 2.dp)
                        .size(16.dp)
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
        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .padding(top = 2.dp)
                        .size(20.dp)
                )
                Space(12.dp)
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.width(90.dp),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Space(8.dp)
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (progress.isCalculating) {
                            PulsingDot()
                            Space(8.dp)
                        }
                        SelectionContainer(modifier = Modifier.weight(1f)) {
                            CopiableText(
                                text = value,
                                maxLines = 2,
                                color = if (progress.isCalculating)
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                else MaterialTheme.colorScheme.onSurface
                            )
                        }
                        if (!progress.isCalculating && value.isNotBlank() && value.length >= 10) {
                            Space(4.dp)
                            TooltipIconButton(
                                tooltip = "Copy $label",
                                onClick = {
                                    value.copyToClipboard()
                                    showMsg(globalClass.getString(R.string.copied_to_clipboard))
                                },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.ContentCopy,
                                    contentDescription = "Copy $label",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }
            }
            if (progress.isCalculating) {
                Spacer(modifier = Modifier.height(6.dp))
                if (progress.total > 0L) {
                    LinearProgressIndicator(
                        progress = {
                            val total = progress.total
                            if (total > 0L) {
                                val frac = progress.current.toFloat() / total.toFloat()
                                if (frac.isNaN() || frac.isInfinite()) 0f else frac.coerceIn(0f, 1f)
                            } else 0f
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 32.dp)
                            .height(4.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    )
                } else {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 32.dp)
                            .height(4.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 32.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val context = LocalContext.current
                    val progressText = if (progress.totalFiles > 0) {
                        val baseText = try {
                            context.getString(R.string.files_progress_format, progress.filesProcessed, progress.totalFiles)
                        } catch (_: Exception) {
                            "${progress.filesProcessed} / ${progress.totalFiles} files"
                        }
                        baseText + if (progress.percentage > 0) " (${progress.percentage}%)" else ""
                    } else if (progress.total > 0) {
                        "${progress.percentage}%"
                    } else ""
                    if (progressText.isNotBlank()) {
                        Text(
                            text = progressText,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    progress.formattedEta?.let { eta ->
                        val etaText = try {
                            context.getString(R.string.eta_format, eta)
                        } catch (_: Exception) {
                            "ETA: $eta"
                        }
                        Text(
                            text = etaText,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
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
    var isExpanded by remember { mutableStateOf(false) }
    var showCopiedFeedback by remember { mutableStateOf(false) }

    LaunchedEffect(showCopiedFeedback) {
        if (showCopiedFeedback) {
            delay(1500)
            showCopiedFeedback = false
        }
    }

    val effectiveMaxLines = if (isExpanded) 12 else maxLines

    Box(modifier = modifier) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = color,
            maxLines = effectiveMaxLines,
            overflow = if (effectiveMaxLines >= 12) TextOverflow.Clip else TextOverflow.Ellipsis,
            softWrap = effectiveMaxLines > 1,
            modifier = Modifier
                .fillMaxWidth()
                .pointerInput(text, onClick) {
                    detectTapGestures(
                        onTap = {
                            if (onClick != null) {
                                onClick()
                            } else {
                                isExpanded = !isExpanded
                            }
                        },
                        onLongPress = {
                            text.copyToClipboard()
                            showCopiedFeedback = true
                            showMsg(globalClass.getString(R.string.copied_to_clipboard))
                        }
                    )
                }
        )

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
