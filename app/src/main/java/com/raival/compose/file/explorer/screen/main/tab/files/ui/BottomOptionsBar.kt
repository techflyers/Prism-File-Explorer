package com.raival.compose.file.explorer.screen.main.tab.files.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandIn
import androidx.compose.animation.shrinkOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AddTask
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.FileCopy
import androidx.compose.material.icons.rounded.FormatColorText
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.RestoreFromTrash
import androidx.compose.material.icons.rounded.SaveAlt
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.raival.compose.file.explorer.App.Companion.globalClass
import com.raival.compose.file.explorer.R
import com.raival.compose.file.explorer.common.block
import com.raival.compose.file.explorer.common.detectVerticalSwipe
import com.raival.compose.file.explorer.common.ui.Space
import com.raival.compose.file.explorer.screen.main.tab.files.FilesTab
import com.raival.compose.file.explorer.screen.main.tab.files.holder.LocalFileHolder
import com.raival.compose.file.explorer.screen.main.tab.files.task.CopyTask
import com.raival.compose.file.explorer.screen.share.getSharedFileName
import com.raival.compose.file.explorer.screen.share.saveSharedFilesToFolder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun BottomOptionsBar(tab: FilesTab) {
    val state = tab.bottomOptionsBarState.collectAsState().value

    if (globalClass.isShareMode && (globalClass.shareUris.isNotEmpty() || !globalClass.shareText.isNullOrEmpty())) {
        ShareModeSaveBar(tab)
    }

    AnimatedVisibility(
        visible = state.showQuickOptions && tab.selectedFiles.isNotEmpty(),
        enter = expandIn(expandFrom = Alignment.TopCenter) + slideInVertically(
            initialOffsetY = { it }),
        exit = shrinkOut(shrinkTowards = Alignment.BottomCenter) + slideOutVertically(
            targetOffsetY = { it })

    ) {
        Column {
            HorizontalDivider()
            Row(
                Modifier
                    .fillMaxWidth()
                    .block(shape = RectangleShape)
                    .then(
                        if (!globalClass.preferencesManager.disableNavigationGestures) {
                            Modifier.detectVerticalSwipe(
                                onSwipeUp = {
                                    tab.toggleFileOptionsMenu(tab.selectedFiles[tab.selectedFiles.keys.first()]!!)
                                }
                            )
                        } else Modifier
                    )
            ) {
                // Delete
                IconButton(
                    modifier = Modifier.weight(1f),
                    onClick = {
                        tab.toggleDeleteConfirmationDialog(true)
                    }
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Delete,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error
                    )
                }

                // Cut
                IconButton(
                    modifier = Modifier.weight(1f),
                    onClick = {
                        globalClass.taskManager.addTask(
                            CopyTask(
                                tab.selectedFiles.values.toList(),
                                deleteSourceFiles = true
                            )
                        )
                        tab.unselectAllFiles()
                    }
                ) {
                    Icon(imageVector = Icons.Rounded.ContentCut, contentDescription = null)
                }

                // Copy
                IconButton(
                    modifier = Modifier.weight(1f),
                    onClick = {
                        globalClass.taskManager.addTask(
                            CopyTask(
                                tab.selectedFiles.values.toList(),
                                deleteSourceFiles = false
                            )
                        )
                        tab.unselectAllFiles()
                    }
                ) {
                    Icon(imageVector = Icons.Rounded.FileCopy, contentDescription = null)
                }

                // Rename
                IconButton(
                    modifier = Modifier.weight(1f),
                    onClick = {
                        tab.toggleRenameDialog(true)
                    }
                ) {
                    Icon(
                        imageVector = Icons.Rounded.FormatColorText,
                        contentDescription = null
                    )
                }

                // Open With — only for a single local file (not folder)
                val singleLocalFile = tab.selectedFiles.size == 1 &&
                        tab.selectedFiles.values.first().let {
                            it is LocalFileHolder && it.isFile()
                        }
                if (singleLocalFile) {
                    IconButton(
                        modifier = Modifier.weight(1f),
                        onClick = {
                            tab.targetFile = tab.selectedFiles.values.first()
                            tab.toggleOpenWithDialog(true)
                        }
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                            contentDescription = null
                        )
                    }
                }

                // Properties
                IconButton(
                    modifier = Modifier.weight(1f),
                    onClick = {
                        tab.toggleFilePropertiesDialog(true)
                    }
                ) {
                    Icon(imageVector = Icons.Rounded.Info, contentDescription = null)
                }
            }
        }
    }

    HorizontalDivider()

    Row(
        Modifier
            .fillMaxWidth()
            .block(shape = RectangleShape)
            .then(
                if (!globalClass.preferencesManager.disableNavigationGestures) {
                    Modifier.detectVerticalSwipe(
                        onSwipeUp = {
                            tab.toggleBookmarksDialog(true)
                        }
                    )
                } else Modifier
            )
    ) {
        if (state.showEmptyRecycleBinButton) {
            BottomOptionsBarButton(Icons.Rounded.DeleteSweep, stringResource(R.string.empty)) {
                tab.unselectAllFiles(false)
                tab.activeFolderContent.forEach {
                    tab.selectedFiles[it.uniquePath] = it
                }
                tab.quickReloadFiles()
                tab.toggleDeleteConfirmationDialog(true)
            }

            if (state.showRestoreButton && tab.selectedFiles.isNotEmpty()) {
                BottomOptionsBarButton(Icons.Rounded.RestoreFromTrash, "Restore") {
                    tab.restoreSelectedFiles()
                }
            }
        } else {
            BottomOptionsBarButton(Icons.Rounded.AddTask, stringResource(R.string.task)) {
                tab.toggleTasksPanel(true)
            }
        }

        BottomOptionsBarButton(Icons.Rounded.Search, stringResource(R.string.search)) {
            tab.toggleSearchPenal(true)
        }

        if (!state.showEmptyRecycleBinButton && state.showCreateNewContentButton) {
            BottomOptionsBarButton(Icons.Rounded.Add, stringResource(R.string.create)) {
                tab.toggleCreateNewFileDialog(true)
            }
        }

        if (state.showMoreOptionsButton && tab.selectedFiles.isNotEmpty()) {
            BottomOptionsBarButton(Icons.Rounded.SelectAll, stringResource(R.string.select_all)) {
                if (tab.selectedFiles.size == tab.activeFolderContent.size) {
                    tab.unselectAllFiles()
                } else {
                    tab.unselectAllFiles(false)
                    tab.activeFolderContent.forEach {
                        tab.selectedFiles[it.uniquePath] = it
                    }
                    tab.quickReloadFiles()
                }
            }

            BottomOptionsBarButton(Icons.Rounded.MoreVert, stringResource(R.string.options)) {
                tab.toggleFileOptionsMenu(tab.selectedFiles[tab.selectedFiles.keys.first()]!!)
            }
        } else {
            BottomOptionsBarButton(Icons.AutoMirrored.Rounded.Sort, stringResource(R.string.sort)) {
                tab.toggleSortingMenu(true)
            }

            BottomOptionsBarButton(Icons.Rounded.Bookmark, stringResource(R.string.bookmarks)) {
                tab.toggleBookmarksDialog(true)
            }
        }
    }
}

@Composable
fun RowScope.BottomOptionsBarButton(
    imageVector: ImageVector,
    text: String,
    view: @Composable () -> Unit = {},
    onClick: () -> Unit
) {
    val preferencesManager = globalClass.preferencesManager

    Column(
        modifier = Modifier
            .weight(1f)
            .clickable {
                onClick()
            }
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (!preferencesManager.showBottomBarLabels) {
            Space(size = 4.dp)
        }

        Icon(
            modifier = Modifier.size(20.dp),
            imageVector = imageVector,
            contentDescription = null
        )

        Space(size = 4.dp)

        if (preferencesManager.showBottomBarLabels) {
            Text(
                text = text,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
        }

        view()
    }
}

@Composable
private fun ShareModeSaveBar(tab: FilesTab) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var isSaving by remember { mutableStateOf(false) }
    val fileLabel = when {
        globalClass.shareUris.size == 1 ->
            getSharedFileName(context, globalClass.shareUris[0])
                ?: stringResource(R.string.unknown_file)
        globalClass.shareUris.size > 1 ->
            stringResource(R.string.shared_files_count, globalClass.shareUris.size)
        else -> stringResource(R.string.share_saving_file).format("text")
    }

    HorizontalDivider()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = fileLabel,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                modifier = Modifier.weight(1f),
                onClick = { globalClass.clearShareMode() }
            ) {
                Text(text = stringResource(R.string.cancel))
            }
            Button(
                modifier = Modifier.weight(1f),
                enabled = !isSaving && tab.activeFolder.canWrite,
                onClick = {
                    coroutineScope.launch {
                        isSaving = true
                        val ok = withContext(Dispatchers.IO) {
                            saveSharedFilesToFolder(
                                context,
                                globalClass.shareUris,
                                tab,
                                globalClass.shareText
                            )
                        }
                        isSaving = false
                        if (ok) {
                            globalClass.clearShareMode()
                            globalClass.showMsg(R.string.share_files_saved)
                            tab.reloadFiles()
                        } else {
                            globalClass.showMsg(R.string.share_failed_to_save)
                        }
                    }
                }
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                    Space(size = 8.dp)
                    Text(text = stringResource(R.string.saving))
                } else {
                    Icon(
                        modifier = Modifier.size(16.dp),
                        imageVector = Icons.Rounded.SaveAlt,
                        contentDescription = null
                    )
                    Space(size = 8.dp)
                    Text(text = stringResource(R.string.share_button_save_here))
                }
            }
        }
    }
}
