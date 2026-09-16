package com.raival.compose.file.explorer.screen.main.tab.files.ui

import android.content.res.Configuration
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import com.raival.compose.file.explorer.screen.main.tab.files.FilesTab
import com.raival.compose.file.explorer.screen.main.tab.files.holder.LocalFileHolder
import com.raival.compose.file.explorer.screen.main.tab.files.ui.dialog.ArchivePasswordDialog
import com.raival.compose.file.explorer.screen.main.tab.files.ui.dialog.ApkPreviewDialog
import com.raival.compose.file.explorer.screen.main.tab.files.ui.dialog.BookmarksDialog
import com.raival.compose.file.explorer.screen.main.tab.files.ui.dialog.CreateNewFileDialog
import com.raival.compose.file.explorer.screen.main.tab.files.ui.dialog.DeleteConfirmationDialog
import com.raival.compose.file.explorer.screen.main.tab.files.ui.dialog.FileCompressionDialog
import com.raival.compose.file.explorer.screen.main.tab.files.ui.dialog.FileOptionsMenuDialog
import com.raival.compose.file.explorer.screen.main.tab.files.ui.dialog.FilePropertiesDialog
import com.raival.compose.file.explorer.screen.main.tab.files.ui.dialog.FileSortingMenuDialog
import com.raival.compose.file.explorer.screen.main.tab.files.ui.dialog.FileViewConfigDialog
import com.raival.compose.file.explorer.screen.main.tab.files.ui.dialog.ImportPrefsDialog
import com.raival.compose.file.explorer.screen.main.tab.files.ui.dialog.OpenWithAppListDialog
import com.raival.compose.file.explorer.screen.main.tab.files.ui.dialog.RenameDialog
import com.raival.compose.file.explorer.screen.main.tab.files.ui.dialog.SearchDialog
import com.raival.compose.file.explorer.screen.main.tab.files.ui.dialog.ShareFolderCompressDialog
import com.raival.compose.file.explorer.screen.main.tab.files.ui.dialog.TaskConflictDialog
import com.raival.compose.file.explorer.screen.main.tab.files.ui.dialog.TaskPanel
import com.raival.compose.file.explorer.screen.main.tab.files.ui.dialog.TaskRunningDialog

import com.raival.compose.file.explorer.App.Companion.globalClass

@Composable
fun ColumnScope.FilesTabContentView(tab: FilesTab) {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    Dialogs(tab)
    // In landscape, breadcrumb is shown inline in the toolbar instead
    if (!isLandscape && globalClass.preferencesManager.showPathBar) {
        BreadcrumbBar(tab)
    }
    InfoRow()
    HorizontalDivider(modifier = Modifier, thickness = 1.dp)
    FilesList(tab)
    BottomOptionsBar(tab, forceLandscapeOverride = isLandscape)
}


@Composable
fun Dialogs(tab: FilesTab) {
    val dialogsState = tab.dialogsState.collectAsState()

    ApkPreviewDialog(
        show = dialogsState.value.showApkDialog && tab.targetFile != null && tab.targetFile is LocalFileHolder,
        tab = tab,
        onDismissRequest = { tab.toggleApkDialog(null) }
    )

    OpenWithAppListDialog(
        show = dialogsState.value.showOpenWithDialog && tab.targetFile != null && tab.targetFile!! is LocalFileHolder,
        tab = tab,
        onDismissRequest = { tab.toggleOpenWithDialog(false) }
    )

    BookmarksDialog(
        show = dialogsState.value.showBookmarkDialog,
        tab = tab,
        onDismissRequest = { tab.toggleBookmarksDialog(false) }
    )

    SearchDialog(
        show = dialogsState.value.showSearchPenal,
        tab = tab,
        onDismissRequest = { tab.toggleSearchPenal(false) }
    )

    FileSortingMenuDialog(
        show = dialogsState.value.showSortingMenu,
        tab = tab,
        onDismissRequest = {
            tab.toggleSortingMenu(false)
            tab.reloadFiles()
        }
    )

    FileViewConfigDialog(
        show = dialogsState.value.showViewConfigDialog,
        tab = tab,
        onDismissRequest = {
            tab.toggleViewConfigDialog(false)
            tab.updateDisplayConfig()
        }
    )

    DeleteConfirmationDialog(
        show = dialogsState.value.showConfirmDeleteDialog,
        tab = tab,
        onDismissRequest = { tab.toggleDeleteConfirmationDialog(false) }
    )

    CreateNewFileDialog(
        show = dialogsState.value.showCreateNewFileDialog,
        tab = tab,
        onDismissRequest = { tab.toggleCreateNewFileDialog(false) }
    )

    RenameDialog(
        show = dialogsState.value.showRenameDialog && tab.selectedFiles.isNotEmpty(),
        tab = tab,
        onDismissRequest = { tab.toggleRenameDialog(false) }
    )

    FileCompressionDialog(
        show = dialogsState.value.showNewZipFileDialog && tab.compressTaskHolder != null,
        tab = tab,
        onDismissRequest = { tab.toggleCompressTaskDialog(null) }
    )

    FileOptionsMenuDialog(
        show = dialogsState.value.showFileOptionsDialog && tab.targetFile != null,
        tab = tab,
        onDismissRequest = { tab.toggleFileOptionsMenu(null, false) }
    )

    FilePropertiesDialog(
        show = dialogsState.value.showFileProperties,
        tab = tab,
        onDismissRequest = { tab.toggleFilePropertiesDialog(false) }
    )

    TaskPanel(
        show = dialogsState.value.showTasksPanel,
        tab = tab,
        onDismissRequest = { tab.toggleTasksPanel(false) }
    )

    ImportPrefsDialog(
        show = dialogsState.value.showImportPrefsDialog,
        tab = tab,
        onDismissRequest = { tab.toggleImportPrefsDialog(null) }
    )

    ArchivePasswordDialog(
        show = dialogsState.value.showArchivePasswordDialog,
        tab = tab,
        onDismissRequest = { tab.toggleArchivePasswordDialog(null) }
    )

    ShareFolderCompressDialog(
        show = dialogsState.value.showShareFolderCompressDialog && tab.selectedFiles.isNotEmpty(),
        tab = tab,
        onDismissRequest = { tab.toggleShareFolderCompressDialog(false) }
    )

    TaskRunningDialog()

    TaskConflictDialog()
}