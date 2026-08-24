package com.raival.compose.file.explorer.screen.main.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.google.accompanist.systemuicontroller.rememberSystemUiController
import com.raival.compose.file.explorer.App.Companion.globalClass
import com.raival.compose.file.explorer.App.Companion.logger
import com.raival.compose.file.explorer.R
import com.raival.compose.file.explorer.common.fromJson
import com.raival.compose.file.explorer.screen.main.startup.StartupTab
import com.raival.compose.file.explorer.screen.main.startup.StartupTabType
import com.raival.compose.file.explorer.screen.main.startup.StartupTabs
import sh.calvin.reorderable.ReorderableCollectionItemScope
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import java.util.UUID

import androidx.compose.foundation.layout.height
import androidx.compose.material3.FilterChip
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material3.AssistChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import com.raival.compose.file.explorer.common.emptyString
import com.raival.compose.file.explorer.screen.main.startup.PlusButtonOverride

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StartupTabsSettingsScreen(
    show: Boolean,
    onBackClick: (StartupTabs) -> Unit
) {
    if (show) {
        val useDarkIcons = !isSystemInDarkTheme()
        val tabs = remember { mutableStateListOf<StartupTab>() }
        var plusButtonOverride by remember { mutableStateOf(PlusButtonOverride.DEFAULT) }
        var plusButtonCustomPath by remember { mutableStateOf(emptyString) }
        var showFolderPicker by remember { mutableStateOf(false) }
        val lazyListState = rememberLazyListState()
        val reorderableState = rememberReorderableLazyListState(
            lazyListState = lazyListState,
            onMove = { from, to ->
                tabs.add(
                    to.index,
                    tabs.removeAt(from.index)
                )
            }
        )

        Dialog(
            onDismissRequest = { onBackClick(StartupTabs(tabs, plusButtonOverride, plusButtonCustomPath)) },
            properties = DialogProperties(
                dismissOnClickOutside = false,
                decorFitsSystemWindows = false,
                usePlatformDefaultWidth = false
            )
        ) {
            val color = MaterialTheme.colorScheme.surfaceContainerHigh
            val systemUiController = rememberSystemUiController()
            DisposableEffect(systemUiController, useDarkIcons) {
                systemUiController.setStatusBarColor(color = color, darkIcons = useDarkIcons)
                onDispose {}
            }

            LaunchedEffect(Unit) {
                val startupTabsObj = try {
                    fromJson<StartupTabs>(
                        globalClass.preferencesManager.startupTabs
                    ) ?: StartupTabs.default()
                } catch (e: Exception) {
                    logger.logError(e)
                    StartupTabs.default()
                }

                plusButtonOverride = startupTabsObj.plusButtonOverride ?: PlusButtonOverride.DEFAULT
                plusButtonCustomPath = startupTabsObj.plusButtonCustomPath ?: emptyString
                val config = startupTabsObj.tabs

                config.forEach {
                    // Gson can actually make this null
                    if (it.id == null) {
                        it.id = UUID.randomUUID()
                    }
                }

                tabs.addAll(config)
            }

            Scaffold(
                topBar = {
                    TopAppBar(
                        title = {
                            Text(
                                text = stringResource(R.string.customize_startup_tabs),
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold
                            )
                        },
                        navigationIcon = {
                            IconButton(onClick = { onBackClick(StartupTabs(tabs, plusButtonOverride, plusButtonCustomPath)) }) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = null
                                )
                            }
                        },
                        actions = {
                            IconButton(
                                onClick = {
                                    tabs.clear()
                                    tabs.addAll(StartupTabs.default().tabs)
                                    plusButtonOverride = PlusButtonOverride.DEFAULT
                                    plusButtonCustomPath = emptyString
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.RestartAlt,
                                    contentDescription = null
                                )
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            titleContentColor = MaterialTheme.colorScheme.onSurface
                        )
                    )
                }
            ) { paddingValues ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .padding(vertical = 16.dp)
                ) {
                    // '+' Button Action Override Card
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                            .clip(RoundedCornerShape(12.dp)),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "'+' Button Action Override",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Choose what tab type the '+' button creates",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                PlusButtonOverride.values().forEach { mode ->
                                    FilterChip(
                                        selected = plusButtonOverride == mode,
                                        onClick = { plusButtonOverride = mode },
                                        label = {
                                            Text(
                                                text = when (mode) {
                                                    PlusButtonOverride.DEFAULT -> "First Tab"
                                                    PlusButtonOverride.HOME -> "Home"
                                                    PlusButtonOverride.APPS -> "Apps"
                                                    PlusButtonOverride.FILES -> "Files"
                                                    PlusButtonOverride.CUSTOM_FOLDER -> "Custom Folder"
                                                },
                                                style = MaterialTheme.typography.labelSmall
                                            )
                                        }
                                    )
                                }
                            }

                            AnimatedVisibility(visible = plusButtonOverride == PlusButtonOverride.CUSTOM_FOLDER) {
                                Column(modifier = Modifier.padding(top = 12.dp)) {
                                    HorizontalDivider(
                                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                    )
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Text(
                                        text = "Custom Folder Location",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    OutlinedTextField(
                                        value = plusButtonCustomPath,
                                        onValueChange = { plusButtonCustomPath = it },
                                        label = { Text("Folder Path") },
                                        placeholder = { Text("/storage/emulated/0") },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.Rounded.Folder,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        },
                                        trailingIcon = {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                if (plusButtonCustomPath.isNotEmpty()) {
                                                    IconButton(onClick = { plusButtonCustomPath = emptyString }) {
                                                        Icon(
                                                            imageVector = Icons.Rounded.Clear,
                                                            contentDescription = "Clear"
                                                        )
                                                    }
                                                }
                                                IconButton(onClick = { showFolderPicker = true }) {
                                                    Icon(
                                                        imageVector = Icons.Rounded.FolderOpen,
                                                        contentDescription = "Browse folder"
                                                    )
                                                }
                                            }
                                        },
                                        singleLine = true,
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    )

                                    Spacer(modifier = Modifier.height(8.dp))

                                    // Quick Preset Shortcuts
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        val shortcuts = listOf(
                                            "Internal Storage" to "/storage/emulated/0",
                                            "Downloads" to "/storage/emulated/0/Download",
                                            "Documents" to "/storage/emulated/0/Documents",
                                            "DCIM" to "/storage/emulated/0/DCIM",
                                            "Pictures" to "/storage/emulated/0/Pictures",
                                            "Music" to "/storage/emulated/0/Music",
                                            "Root (/)" to "/"
                                        )
                                        shortcuts.forEach { (name, path) ->
                                            AssistChip(
                                                onClick = { plusButtonCustomPath = path },
                                                label = {
                                                    Text(
                                                        text = name,
                                                        style = MaterialTheme.typography.labelSmall
                                                    )
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    DirectorySelectionDialog(
                        show = showFolderPicker,
                        initialPath = plusButtonCustomPath.ifEmpty { "/storage/emulated/0" },
                        onDismissRequest = { showFolderPicker = false },
                        onDirectorySelected = { selectedPath ->
                            plusButtonCustomPath = selectedPath
                        }
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Tabs list
                    LazyColumn(
                        state = lazyListState,
                        modifier = Modifier
                            .fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(tabs, key = { it.id }) { tab ->
                            ReorderableItem(
                                state = reorderableState,
                                key = tab.id
                            ) { isDragging ->
                                StartupTabItem(
                                    reorderableScope = this,
                                    tab = tab,
                                    isDragging = isDragging,
                                    canRemove = tabs.size > 1,
                                    onRemove = {
                                        if (tabs.size > 1) {
                                            tabs.remove(tab)
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StartupTabItem(
    reorderableScope: ReorderableCollectionItemScope,
    tab: StartupTab,
    isDragging: Boolean,
    canRemove: Boolean,
    onRemove: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(12.dp)),
        colors = CardDefaults.cardColors(
            containerColor = if (isDragging)
                MaterialTheme.colorScheme.surfaceVariant
            else
                MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isDragging) 8.dp else 2.dp
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Tab type icon
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(12.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = tab.type.getIcon(),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Tab info
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = tab.type.getTitle(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                if (tab.extra.isNotEmpty() && tab.type == StartupTabType.FILES) {
                    Text(
                        text = tab.extra,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        text = tab.type.getDescription(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Drag handle
            Icon(
                imageVector = Icons.Default.DragHandle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = with(reorderableScope) {
                    Modifier
                        .padding(end = 8.dp)
                        .draggableHandle()
                }
            )

            // Remove button
            if (canRemove) {
                IconButton(onClick = onRemove) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

fun StartupTabType.getIcon(): ImageVector {
    return when (this) {
        StartupTabType.HOME -> Icons.Rounded.Home
        StartupTabType.APPS -> Icons.Rounded.Apps
        StartupTabType.FILES -> Icons.Rounded.Folder
    }
}

fun StartupTabType.getTitle(): String {
    return when (this) {
        StartupTabType.HOME -> globalClass.getString(R.string.home)
        StartupTabType.APPS -> globalClass.getString(R.string.apps)
        StartupTabType.FILES -> globalClass.getString(R.string.files)
    }
}

fun StartupTabType.getDescription(): String {
    return when (this) {
        StartupTabType.HOME -> globalClass.getString(R.string.quick_access_to_common_folders_and_shortcuts)
        StartupTabType.APPS -> globalClass.getString(R.string.browse_and_manage_installed_applications)
        StartupTabType.FILES -> globalClass.getString(R.string.navigate_and_manage_your_files_and_folders)
    }
}