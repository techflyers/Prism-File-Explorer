package com.techflyers.compose.file.explorer.screen.main.tab.files.ui.dialog

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.techflyers.compose.file.explorer.App.Companion.globalClass
import com.techflyers.compose.file.explorer.R
import com.techflyers.compose.file.explorer.common.isNot
import com.techflyers.compose.file.explorer.common.trimToLastTwoSegments
import com.techflyers.compose.file.explorer.common.ui.BottomSheetDialog
import com.techflyers.compose.file.explorer.common.ui.Space
import com.techflyers.compose.file.explorer.screen.main.tab.files.FilesTab
import com.techflyers.compose.file.explorer.screen.main.tab.files.holder.LocalFileHolder
import com.techflyers.compose.file.explorer.screen.main.tab.files.ui.FileItemRow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterialApi::class, ExperimentalFoundationApi::class)
@Composable
fun BookmarksDialog(
    show: Boolean,
    tab: FilesTab,
    onDismissRequest: () -> Unit
) {
    if (show) {
        val context = LocalContext.current
        val bookmarks = remember {
            mutableStateListOf<LocalFileHolder>()
        }
        var hasLoaded by remember { mutableStateOf(false) }
        var bookmarkToRename by remember { mutableStateOf<LocalFileHolder?>(null) }
        var showManualAddDialog by remember { mutableStateOf(false) }

        LaunchedEffect(Unit) {
            withContext(Dispatchers.IO) {
                val originalList = globalClass.preferencesManager.bookmarks
                val titles = globalClass.preferencesManager.bookmarkTitles
                val loaded = originalList.mapNotNull { path ->
                    val file = File(path)
                    val customTitle = titles[path]
                    val holder = LocalFileHolder(file, cachedName = customTitle)
                    if (holder.isValid()) holder else null
                }

                withContext(Dispatchers.Main) {
                    bookmarks.clear()
                    bookmarks.addAll(loaded)
                    hasLoaded = true
                }

                if (loaded.size isNot originalList.size) {
                    globalClass.preferencesManager.bookmarks =
                        loaded.map { it.uniquePath }.toSet()
                }
            }
        }

        if (showManualAddDialog) {
            AddCustomFolderDialog(
                show = showManualAddDialog,
                initialPath = tab.activeFolder.uniquePath,
                onDismissRequest = { showManualAddDialog = false },
                onConfirm = { path ->
                    globalClass.preferencesManager.bookmarks = globalClass.preferencesManager.bookmarks + path
                    val titles = globalClass.preferencesManager.bookmarkTitles
                    bookmarks.add(0, LocalFileHolder(File(path), cachedName = titles[path]))
                    globalClass.showMsg(globalClass.getString(R.string.added_to_bookmarks))
                }
            )
        }

        if (bookmarkToRename != null) {
            val target = bookmarkToRename!!
            val currentTitle = globalClass.preferencesManager.getBookmarkTitle(target.uniquePath)
                ?: target.file.name.ifEmpty { target.uniquePath }
            RenameBookmarkDialog(
                show = true,
                currentName = currentTitle,
                targetPath = target.uniquePath,
                onDismissRequest = { bookmarkToRename = null },
                onConfirm = { newName ->
                    globalClass.preferencesManager.setBookmarkTitle(target.uniquePath, newName)
                    val index = bookmarks.indexOfFirst { it.uniquePath == target.uniquePath }
                    if (index != -1) {
                        bookmarks[index] = LocalFileHolder(
                            file = target.file,
                            cachedName = newName.trim().ifEmpty { null }
                        )
                    }
                    bookmarkToRename = null
                }
            )
        }

        BottomSheetDialog(
            onDismissRequest = onDismissRequest
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Rounded.Bookmark,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Space(8.dp)
                Text(
                    text = stringResource(R.string.bookmarks),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = { showManualAddDialog = true },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = stringResource(R.string.add_folder_path),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // "Add Current Folder" button if activeFolder is a folder and not already bookmarked
            val canBookmarkCurrent = tab.activeFolder is LocalFileHolder && tab.activeFolder.isFolder &&
                    !globalClass.preferencesManager.bookmarks.contains(tab.activeFolder.uniquePath)
            if (canBookmarkCurrent) {
                OutlinedButton(
                    onClick = {
                        val path = tab.activeFolder.uniquePath
                        globalClass.preferencesManager.bookmarks = globalClass.preferencesManager.bookmarks + path
                        val titles = globalClass.preferencesManager.bookmarkTitles
                        bookmarks.add(0, LocalFileHolder(File(path), cachedName = titles[path]))
                        globalClass.showMsg(globalClass.getString(R.string.added_to_bookmarks))
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.BookmarkAdd,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Space(8.dp)
                    Text(
                        text = "${stringResource(R.string.add_current_folder)} (${tab.activeFolder.displayName})",
                        maxLines = 1
                    )
                }
            }

            Space(size = 8.dp)

            if (bookmarks.isNotEmpty()) {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    itemsIndexed(
                        items = bookmarks,
                        key = { _, item -> item.uniquePath }
                    ) { index, item ->
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .animateItem()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .combinedClickable(
                                        onClick = {
                                            if (!item.file.exists()) {
                                                globalClass.showMsg(
                                                    if (item.isFolder) R.string.folder_does_not_exist else R.string.file_no_longer_exists
                                                )
                                                return@combinedClickable
                                            }
                                            if (item.isFile()) {
                                                tab.openFile(context, item)
                                            } else {
                                                tab.openFolder(LocalFileHolder(item.file))
                                            }
                                            onDismissRequest()
                                        },
                                        onLongClick = {
                                            bookmarkToRename = item
                                        }
                                    ),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(modifier = Modifier.weight(1f)) {
                                    FileItemRow(
                                        item = item,
                                        fileDetails = item.uniquePath.trimToLastTwoSegments(),
                                        ignoreSizePreferences = true
                                    )
                                }
                                IconButton(
                                    onClick = { bookmarkToRename = item },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Edit,
                                        contentDescription = stringResource(R.string.rename),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        val path = item.uniquePath
                                        globalClass.preferencesManager.removeBookmark(path)
                                        bookmarks.remove(item)
                                    },
                                    modifier = Modifier
                                        .padding(end = 12.dp)
                                        .size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Delete,
                                        contentDescription = stringResource(R.string.delete),
                                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                            Space(size = 4.dp)
                            HorizontalDivider(
                                modifier = Modifier.padding(start = 56.dp),
                                thickness = 0.5.dp
                            )
                        }
                    }
                }
            } else if (hasLoaded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 64.dp, horizontal = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Rounded.BookmarkBorder,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(48.dp)
                    )
                    Space(16.dp)
                    Text(
                        text = stringResource(R.string.empty),
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Space(8.dp)
                    Text(
                        text = stringResource(R.string.no_bookmarks_available),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outline,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}
