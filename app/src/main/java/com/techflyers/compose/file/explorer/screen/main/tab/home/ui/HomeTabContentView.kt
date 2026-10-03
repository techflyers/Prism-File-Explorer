package com.techflyers.compose.file.explorer.screen.main.tab.home.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowOutward
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.google.gson.Gson
import com.techflyers.compose.file.explorer.App.Companion.globalClass
import com.techflyers.compose.file.explorer.App.Companion.logger
import com.techflyers.compose.file.explorer.R
import com.techflyers.compose.file.explorer.common.ui.Space
import com.techflyers.compose.file.explorer.common.ui.fastScrollbar
import com.techflyers.compose.file.explorer.screen.main.MainActivityManager
import com.techflyers.compose.file.explorer.screen.main.tab.files.FilesTab
import com.techflyers.compose.file.explorer.screen.main.tab.files.coil.canUseCoil
import com.techflyers.compose.file.explorer.screen.main.tab.files.holder.LocalFileHolder
import com.techflyers.compose.file.explorer.screen.main.tab.files.misc.SourceFolderResolver
import com.techflyers.compose.file.explorer.screen.main.tab.files.ui.SourceFolderBadge
import com.techflyers.compose.file.explorer.screen.main.tab.files.holder.StorageDevice
import com.techflyers.compose.file.explorer.screen.main.tab.files.holder.VirtualFileHolder
import com.techflyers.compose.file.explorer.screen.main.tab.files.holder.VirtualFileHolder.Companion.BOOKMARKS
import com.techflyers.compose.file.explorer.screen.main.tab.files.holder.VirtualFileHolder.Companion.RECENT
import com.techflyers.compose.file.explorer.screen.main.tab.files.provider.StorageProvider
import com.techflyers.compose.file.explorer.screen.main.tab.files.ui.FileContentIcon
import com.techflyers.compose.file.explorer.screen.main.tab.home.HomeTab
import com.techflyers.compose.file.explorer.screen.main.tab.home.holder.HomeCategory
import com.techflyers.compose.file.explorer.screen.main.tab.home.data.HomeLayout
import com.techflyers.compose.file.explorer.screen.main.tab.home.data.HomeSectionConfig
import com.techflyers.compose.file.explorer.screen.main.tab.home.data.HomeSectionType
import com.techflyers.compose.file.explorer.screen.main.tab.home.data.getDefaultHomeLayout
import com.techflyers.compose.file.explorer.screen.main.ui.SimpleNewTabViewItem
import com.techflyers.compose.file.explorer.screen.main.ui.StorageDeviceView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import androidx.compose.foundation.LocalOverscrollConfiguration
import androidx.compose.foundation.OverscrollConfiguration
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.rounded.Reorder
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.google.accompanist.systemuicontroller.rememberSystemUiController
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ColumnScope.HomeTabContentView(tab: HomeTab) {
    val mainActivityManager = globalClass.mainActivityManager
    val preferencesManager = globalClass.preferencesManager
    val scope = rememberCoroutineScope()
    val enabledSections = remember { mutableStateListOf<HomeSectionConfig>() }
    var isRefreshing by remember { mutableStateOf(false) }

    val refreshHome: () -> Unit = {
        isRefreshing = true
        scope.launch {
            withContext(Dispatchers.IO) {
                val d1 = async { tab.refreshRecentFiles() }
                val d2 = async { tab.getPinnedFiles() }
                val d3 = async { mainActivityManager.updateStorageDevices() }
                d1.await()
                d2.await()
                d3.await()
            }
            delay(150)
            isRefreshing = false
        }
    }

    LaunchedEffect(tab.id) {
        withContext(Dispatchers.IO) {
            async {
                tab.fetchRecentFiles()
            }
            async {
                tab.getPinnedFiles()
            }
            async {
                mainActivityManager.updateStorageDevices()
            }
            async {
                val config = try {
                    Gson().fromJson(
                        globalClass.preferencesManager.homeTabLayout,
                        HomeLayout::class.java
                    )
                } catch (e: Exception) {
                    logger.logError(e)
                    getDefaultHomeLayout()
                }.getSections().filter { it.isEnabled }.sortedBy { it.order }

                enabledSections.clear()
                enabledSections.addAll(config)
            }
        }
        // Auto-update storage stats periodically while Home tab is active
        while (isActive) {
            delay(3000)
            mainActivityManager.updateStorageDevices()
        }
    }

    if (tab.showCustomizeHomeTabDialog) {
        HomeLayoutSettingsScreen { sections ->
            tab.showCustomizeHomeTabDialog = false
            var isAllDisabled = false

            // Prevent disabling all sections
            if (sections.all { !it.isEnabled }) {
                isAllDisabled = true
            }

            sections.forEachIndexed { index, config ->
                config.order = index
            }

            enabledSections.apply {
                clear()
                if (isAllDisabled) {
                    addAll(getDefaultHomeLayout(true).getSections().filter { it.isEnabled }
                        .sortedBy { it.order })
                } else {
                    addAll(sections.filter { it.isEnabled }.sortedBy { it.order })
                }

            }

            scope.launch {
                if (isAllDisabled) {
                    globalClass.preferencesManager.homeTabLayout = Gson().toJson(
                        getDefaultHomeLayout(true)
                    )
                } else {
                    globalClass.preferencesManager.homeTabLayout = Gson().toJson(
                        HomeLayout(sections)
                    )
                }
            }
        }
    }

    val homeScroll = rememberScrollState()
    val overscrollConfig = if (preferencesManager.disableSpringEffect) null else OverscrollConfiguration()

    val homeSectionsContent = @Composable {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(homeScroll)
                .fastScrollbar(homeScroll),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            enabledSections.forEach { section ->
                when (section.type) {
                    HomeSectionType.RECENT_FILES -> {
                        RecentFilesSection(tab = tab, mainActivityManager = mainActivityManager)
                    }

                    HomeSectionType.CATEGORIES -> {
                        CategoriesSection(tab = tab)
                    }

                    HomeSectionType.STORAGE -> {
                        StorageSection(mainActivityManager = mainActivityManager)
                    }

                    HomeSectionType.BOOKMARKS -> {
                        BookmarksSection(mainActivityManager = mainActivityManager)
                    }

                    HomeSectionType.RECYCLE_BIN -> {
                        RecycleBinSection(mainActivityManager = mainActivityManager)
                    }

                    HomeSectionType.JUMP_TO_PATH -> {
                        JumpToPathSection(mainActivityManager = mainActivityManager)
                    }

                    HomeSectionType.PINNED_FILES -> {
                        PinnedFilesSection(tab = tab, mainActivityManager = mainActivityManager)
                    }
                }
            }
        }
    }

    CompositionLocalProvider(LocalOverscrollConfiguration provides overscrollConfig) {
        if (preferencesManager.disablePullDownToRefresh) {
            homeSectionsContent()
        } else if (preferencesManager.disableSpringEffect) {
            val noSpringState = rememberPullToRefreshState()
            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = refreshHome,
                modifier = Modifier.fillMaxSize(),
                state = noSpringState,
                indicator = {
                    PullToRefreshDefaults.Indicator(
                        state = noSpringState,
                        isRefreshing = isRefreshing,
                        modifier = Modifier.align(Alignment.TopCenter)
                    )
                }
            ) {
                homeSectionsContent()
            }
        } else {
            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = refreshHome,
                modifier = Modifier.fillMaxSize()
            ) {
                homeSectionsContent()
            }
        }
    }
}

@Composable
fun PinnedFilesSection(
    tab: HomeTab,
    mainActivityManager: MainActivityManager
) {
    val context = LocalContext.current
    var renameTarget by remember { mutableStateOf<LocalFileHolder?>(null) }
    var shortcutNameInput by remember { mutableStateOf("") }
    val pinnedFiles = remember {
        mutableStateListOf<LocalFileHolder>().apply {
            addAll(tab.pinnedFiles)
        }
    }

    LaunchedEffect(tab.pinnedFiles.size) {
        pinnedFiles.clear()
        pinnedFiles.addAll(tab.pinnedFiles)
    }

    if (pinnedFiles.isNotEmpty()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .padding(top = 12.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = stringResource(R.string.pinned_files),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )

            var showReorderDialog by remember { mutableStateOf(false) }

            IconButton(
                onClick = { showReorderDialog = true },
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Reorder,
                    contentDescription = stringResource(R.string.reorder_pinned_files),
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }

            if (showReorderDialog) {
                ReorderPinnedFilesDialog(
                    pinnedFilesList = pinnedFiles,
                    onDismiss = { showReorderDialog = false },
                    onSave = { newOrder ->
                        pinnedFiles.clear()
                        pinnedFiles.addAll(newOrder)
                        tab.pinnedFiles.clear()
                        tab.pinnedFiles.addAll(newOrder)
                        globalClass.preferencesManager.pinnedFiles = newOrder.map { it.uniquePath }
                        showReorderDialog = false
                    }
                )
            }
        }

        Column(
            modifier = Modifier
                .animateContentSize()
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .background(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow
                )
                .border(
                    width = 0.5.dp,
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    shape = RoundedCornerShape(12.dp)
                )
                .clip(RoundedCornerShape(12.dp))
        ) {
            pinnedFiles.forEachIndexed { index, it ->
                var showDeleteOption by remember(it.uniquePath) { mutableStateOf(false) }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .combinedClickable(
                                onClick = {
                                    if (showDeleteOption) {
                                        showDeleteOption = false
                                    } else if (it.isFile()) {
                                        it.open(
                                            context = context,
                                            anonymous = false,
                                            skipSupportedExtensions = !globalClass.preferencesManager.useBuiltInViewer,
                                            customMimeType = null
                                        )
                                    } else {
                                        mainActivityManager.replaceCurrentTabWith(FilesTab(it))
                                    }
                                },
                                onLongClick = {
                                    showDeleteOption = !showDeleteOption
                                }
                            )
                            .padding(12.dp)
                            .padding(end = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        var canUseCoil by remember(it.uniquePath) {
                            mutableStateOf(canUseCoil(it))
                        }
                        if (canUseCoil) {
                            AsyncImage(
                                modifier = Modifier
                                    .size(45.dp)
                                    .clip(RoundedCornerShape(12.dp)),
                                model = ImageRequest
                                    .Builder(globalClass)
                                    .data(it)
                                    .build(),
                                filterQuality = FilterQuality.Low,
                                contentScale = ContentScale.Fit,
                                contentDescription = null,
                                onError = { canUseCoil = false }
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(45.dp)
                                    .clip(RoundedCornerShape(12.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                FileContentIcon(it)
                            }
                        }
                        Space(size = 8.dp)
                        Text(
                            text = globalClass.preferencesManager.pinnedFileNames[it.uniquePath]
                                ?: it.displayName,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    AnimatedVisibility(visible = showDeleteOption) {
                        Row(
                            modifier = Modifier
                                .fillMaxHeight()
                                .background(color = MaterialTheme.colorScheme.surfaceContainerHighest)
                        ) {
                            IconButton(
                                onClick = {
                                    shortcutNameInput = globalClass.preferencesManager.pinnedFileNames[it.uniquePath]
                                        ?: it.displayName
                                    renameTarget = it
                                    showDeleteOption = false
                                },
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Edit,
                                    contentDescription = stringResource(R.string.rename_shortcut),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                            IconButton(
                                onClick = {
                                    pinnedFiles.remove(it)
                                    tab.removePinnedFile(it)
                                },
                                modifier = Modifier
                                    .size(48.dp)
                                    .background(color = MaterialTheme.colorScheme.errorContainer)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Delete,
                                    contentDescription = stringResource(R.string.remove_shortcut),
                                    tint = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    }
                }
                if (index != pinnedFiles.lastIndex) HorizontalDivider(thickness = 0.5.dp)
            }
        }
    }
    renameTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text(stringResource(R.string.rename_shortcut)) },
            text = {
                OutlinedTextField(
                    value = shortcutNameInput,
                    onValueChange = { shortcutNameInput = it },
                    label = { Text(stringResource(R.string.shortcut_name)) },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val displayName = shortcutNameInput.trim().ifEmpty { target.displayName }
                        val names = globalClass.preferencesManager.pinnedFileNames.toMutableMap()
                        if (displayName == target.displayName) {
                            names.remove(target.uniquePath)
                        } else {
                            names[target.uniquePath] = displayName
                        }
                        globalClass.preferencesManager.pinnedFileNames = names
                        FilesTab.updateHomeScreenShortcutLabel(context, target, displayName)
                        renameTarget = null
                    }
                ) {
                    Text(stringResource(R.string.apply))
                }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@Composable
private fun RecentFilesSection(
    tab: HomeTab,
    mainActivityManager: MainActivityManager
) {
    val context = LocalContext.current

    // Recent files
    Text(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .padding(top = 12.dp),
        text = stringResource(R.string.recent_files),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
    )

    if (tab.recentFiles.isEmpty()) {
        Text(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
                .padding(horizontal = 12.dp, vertical = 12.dp)
                .clickable {
                    mainActivityManager.replaceCurrentTabWith(
                        FilesTab(VirtualFileHolder(RECENT))
                    )
                },
            text = stringResource(R.string.no_recent_files),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center
        )
    } else {
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            item { Space(6.dp) }

            items(tab.recentFiles, key = { it.path }) {
                Column(
                    modifier = Modifier
                        .size(110.dp, 140.dp)
                        .padding(horizontal = 6.dp)
                        .background(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLow
                        )
                        .border(
                            width = 0.5.dp,
                            color = MaterialTheme.colorScheme.surfaceContainerHighest,
                            shape = RoundedCornerShape(8.dp)
                        )
                        .clip(RoundedCornerShape(8.dp))
                        .combinedClickable(
                            onClick = {
                                it.file.open(
                                    context = context,
                                    anonymous = false,
                                    skipSupportedExtensions = !globalClass.preferencesManager.useBuiltInViewer,
                                    customMimeType = null
                                )
                            },
                            onLongClick = {
                                mainActivityManager.replaceCurrentTabWith(
                                    FilesTab(it.file)
                                )
                            }
                        )
                ) {
                    var useCoil by remember(it.file.uniquePath) {
                        mutableStateOf(canUseCoil(it.file))
                    }

                    Box(modifier = Modifier.weight(2f)) {
                        if (useCoil) {
                            AsyncImage(
                                modifier = Modifier.fillMaxSize(),
                                model = ImageRequest
                                    .Builder(globalClass)
                                    .data(it.file)
                                    .build(),
                                filterQuality = FilterQuality.Low,
                                contentScale = ContentScale.Crop,
                                contentDescription = null,
                                onError = { useCoil = false }
                            )
                        } else {
                            FileContentIcon(it.file)
                        }

                        if (globalClass.preferencesManager.showSourceBadges) {
                            val sourceInfo = remember(it.path) {
                                SourceFolderResolver.resolve(it.file)
                            }
                            if (sourceInfo != null) {
                                SourceFolderBadge(
                                    sourceInfo = sourceInfo,
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(4.dp),
                                    badgeSize = 20.dp,
                                    iconSize = 14.dp
                                )
                            }
                        }
                    }
                    Box(modifier = Modifier
                        .weight(1.2f)
                        .padding(6.dp)
                    ) {
                        Text(
                            modifier = Modifier
                                .fillMaxSize(),
                            text = it.name,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            softWrap = true
                        )
                    }
                }
            }
            item {
                TextButton(
                    onClick = {
                        mainActivityManager.replaceCurrentTabWith(
                            FilesTab(VirtualFileHolder(RECENT))
                        )
                    }
                ) {
                    Text(text = stringResource(R.string.more))
                }
            }

            item { Space(6.dp) }
        }
    }
}

@Composable
private fun CategoriesSection(
    tab: HomeTab
) {
    // Quick access tiles
    Text(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .padding(top = 12.dp),
        text = stringResource(R.string.categories),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
    )

    val categories = tab.getMainCategories()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
    ) {
        categories.dropLast(1).chunked(3).forEach { rowItems ->
            Row(Modifier.fillMaxWidth()) {
                rowItems.forEach { category ->
                    HomeCategoryTile(category, Modifier.weight(1f))
                }
                repeat(3 - rowItems.size) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
        categories.lastOrNull()?.let { category ->
            HomeCategoryTile(category, Modifier.fillMaxWidth(), fullWidth = true)
        }
    }
}

@Composable
private fun HomeCategoryTile(
    category: HomeCategory,
    modifier: Modifier,
    fullWidth: Boolean = false
) {
    val tileModifier = modifier
        .padding(4.dp)
        .background(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow
        )
        .border(
            width = 0.5.dp,
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            shape = RoundedCornerShape(8.dp)
        )
        .clickable { category.onClick() }
        .padding(8.dp)

    if (fullWidth) {
        Row(
            modifier = tileModifier,
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(imageVector = category.icon, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(text = category.name, maxLines = 1)
        }
    } else {
        Column(
            modifier = tileModifier,
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                modifier = Modifier.padding(8.dp),
                imageVector = category.icon,
                contentDescription = null
            )
            Text(
                modifier = Modifier.basicMarquee(velocity = 90.dp),
                text = category.name,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun StorageSection(
    mainActivityManager: MainActivityManager
) {
    val mainActivityState by mainActivityManager.state.collectAsState()
    val storageList = mainActivityState.storageDevices

    if (storageList.isNotEmpty()) {
        // Storage options
        Text(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .padding(top = 12.dp),
            text = stringResource(R.string.storage),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .background(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow
                )
                .border(
                    width = 0.5.dp,
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    shape = RoundedCornerShape(12.dp)
                )
                .clip(RoundedCornerShape(12.dp))
        ) {
            storageList.forEachIndexed { index, device ->
                StorageDeviceView(storageDevice = device) {
                    mainActivityManager.replaceCurrentTabWith(FilesTab(device.contentHolder))
                }
                if (index != storageList.lastIndex) HorizontalDivider(thickness = 0.5.dp)
            }
        }
    }
}

@Composable
private fun BookmarksSection(
    mainActivityManager: MainActivityManager
) {
    if (globalClass.preferencesManager.bookmarks.isNotEmpty()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .background(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow
                )
                .border(
                    width = 0.5.dp,
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    shape = RoundedCornerShape(12.dp)
                )
                .clip(RoundedCornerShape(12.dp))
        ) {
            SimpleNewTabViewItem(
                title = stringResource(R.string.bookmarks),
                imageVector = Icons.Rounded.Bookmark
            ) {
                mainActivityManager.replaceCurrentTabWith(
                    FilesTab(VirtualFileHolder(BOOKMARKS))
                )
            }
        }
    }
}

@Composable
private fun RecycleBinSection(
    mainActivityManager: MainActivityManager
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .background(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow
            )
            .border(
                width = 0.5.dp,
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                shape = RoundedCornerShape(12.dp)
            )
            .clip(RoundedCornerShape(12.dp))
    ) {
        SimpleNewTabViewItem(
            title = stringResource(R.string.recycle_bin),
            imageVector = Icons.Rounded.DeleteSweep
        ) {
            mainActivityManager.replaceCurrentTabWith(FilesTab(globalClass.recycleBinDir))
        }
    }
}

@Composable
private fun JumpToPathSection(
    mainActivityManager: MainActivityManager
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .background(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow
            )
            .border(
                width = 0.5.dp,
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                shape = RoundedCornerShape(12.dp)
            )
            .clip(RoundedCornerShape(12.dp))
    ) {
        SimpleNewTabViewItem(
            title = stringResource(R.string.jump_to_path),
            imageVector = Icons.Rounded.ArrowOutward
        ) {
            mainActivityManager.toggleJumpToPathDialog(true)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReorderPinnedFilesDialog(
    pinnedFilesList: List<LocalFileHolder>,
    onDismiss: () -> Unit,
    onSave: (List<LocalFileHolder>) -> Unit
) {
    val items = remember { mutableStateListOf<LocalFileHolder>().apply { addAll(pinnedFilesList) } }
    val lazyListState = rememberLazyListState()
    val reorderableState = rememberReorderableLazyListState(
        lazyListState = lazyListState,
        onMove = { from, to ->
            items.add(to.index, items.removeAt(from.index))
        }
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnClickOutside = false,
            decorFitsSystemWindows = false,
            usePlatformDefaultWidth = false
        )
    ) {
        val color = MaterialTheme.colorScheme.surfaceContainerHigh
        val useDarkIcons = !isSystemInDarkTheme()
        val systemUiController = rememberSystemUiController()
        DisposableEffect(systemUiController, useDarkIcons) {
            systemUiController.setStatusBarColor(color = color, darkIcons = useDarkIcons)
            onDispose {}
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = stringResource(R.string.reorder_pinned_files),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = null
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = { onSave(items) }) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Save"
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
                LazyColumn(
                    state = lazyListState,
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(items, key = { it.uniquePath }) { item ->
                        ReorderableItem(
                            state = reorderableState,
                            key = item.uniquePath
                        ) { isDragging ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp)
                                    .clip(RoundedCornerShape(8.dp)),
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
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier.size(36.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        FileContentIcon(item)
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = item.displayName,
                                        modifier = Modifier.weight(1f),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Icon(
                                        imageVector = Icons.Default.DragHandle,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier
                                            .padding(end = 8.dp)
                                            .draggableHandle()
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
