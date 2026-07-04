package com.raival.compose.file.explorer.screen.main.tab.files.zip

import com.raival.compose.file.explorer.App.Companion.globalClass
import com.raival.compose.file.explorer.common.isNot
import com.raival.compose.file.explorer.common.removeIf
import com.raival.compose.file.explorer.screen.main.tab.files.FilesTab
import com.raival.compose.file.explorer.screen.main.tab.files.holder.LocalFileHolder
import com.raival.compose.file.explorer.screen.main.tab.files.holder.VirtualFileHolder
import kotlinx.coroutines.runBlocking

class ZipManager {
    val archiveList = hashMapOf<LocalFileHolder, ZipTree>()

    /**
     * Validates the archive trees by checking if their source files are still valid.
     * Removes any invalid archive trees from the `archiveList`.
     *
     * @return A set of unique paths of the invalid archive trees.
     */
    suspend fun validateArchiveTrees(): Set<String> {
        val invalidTrees =
            archiveList.values.filter { !it.source.isValid() }.map { it.source.uniquePath }

        archiveList.removeIf { localFileHolder, zipTree ->
            runBlocking { !localFileHolder.isValid() }
        }

        return invalidTrees.toSet()
    }

    suspend fun checkForSourceChanges(): Boolean {
        var foundChanges = false
        archiveList.values.forEach { zipTree ->
            if (zipTree.source.lastModified isNot zipTree.timeStamp || zipTree.checkExtractedFiles()
                    .isNotEmpty()
            ) {
                foundChanges = true
            }
        }
        return foundChanges
    }

    /**
     * Opens an archive, navigating into it as a virtual folder.
     *
     * For zip-based (zip4j) archives: checks `isEncrypted` before opening and shows
     * the password dialog if needed.
     *
     * For native (7z/rar) archives: attempts to open; if the tree build fails with
     * [ZipTree.ArchivePasswordRequiredException], shows the password dialog.
     *
     * @param archive The archive file to open.
     * @param password Optional password for encrypted archives.
     */
    fun openArchive(archive: LocalFileHolder, password: String? = null) {
        android.util.Log.d("PrismArchive", "ZipManager: openArchive() archive=${archive.displayName}, path=${archive.uniquePath}, passwordProvided=${!password.isNullOrEmpty()}")

        val isNative = ArchiveManager.isNativeArchive(archive.extension)
        android.util.Log.d("PrismArchive", "ZipManager: isNativeArchive=$isNative")

        // For zip4j archives, check encryption before building the tree
        if (!isNative && password == null) {
            try {
                val zipFile = net.lingala.zip4j.ZipFile(archive.file)
                if (zipFile.isEncrypted) {
                    android.util.Log.d("PrismArchive", "ZipManager: zip4j archive is encrypted. Showing password dialog...")
                    val tab = globalClass.mainActivityManager.getActiveTab() as? FilesTab
                    tab?.toggleArchivePasswordDialog(archive)
                    return
                }
            } catch (e: Exception) {
                android.util.Log.w("PrismArchive", "ZipManager: failed to check zip4j encryption: ${e.message}")
            }
        }

        val existingTreeKey = archiveList.keys.find { archive.uniquePath == it.uniquePath }

        if (existingTreeKey != null) {
            archiveList[existingTreeKey]?.let { existingTree ->
                android.util.Log.d("PrismArchive", "ZipManager: Found existing ZipTree for archive.")
                // Update password if one was just entered
                if (password != null) {
                    android.util.Log.d("PrismArchive", "ZipManager: Updating existing tree password and forcing rebuild.")
                    existingTree.password = password
                    existingTree.invalidate() // FORCE REBUILD WITH NEW PASSWORD!
                }

                if (existingTree.timeStamp == archive.lastModified) {
                    android.util.Log.d("PrismArchive", "ZipManager: Existing tree is valid, navigating...")
                    navigateTo(existingTree)
                    return
                } else {
                    android.util.Log.d("PrismArchive", "ZipManager: Timestamp changed, removing existing tree.")
                    archiveList.remove(existingTreeKey)
                }
            }
        }

        android.util.Log.d("PrismArchive", "ZipManager: Creating fresh ZipTree...")
        val newTree = ZipTree(archive).apply {
            if (password != null) this.password = password
        }

        // Register immediately and navigate — ZipFileHolder.listContent() will call
        // prepare() asynchronously via the tab's loading coroutine, so we never block the main thread.
        archiveList[archive] = newTree
        android.util.Log.d("PrismArchive", "PrismArchive: Registered ZipTree in archiveList. Navigating...")
        navigateTo(newTree)
    }

    private fun navigateTo(tree: ZipTree) {
        globalClass.mainActivityManager.let { mainManager ->
            val tab = mainManager.getActiveTab()
            if (tab is FilesTab) {
                if (tab.activeFolder is VirtualFileHolder) {
                    mainManager.replaceCurrentTabWith(
                        tab = FilesTab(tree.createRootContentHolder()),
                        keepCurrentTabAsParent = true
                    )
                } else {
                    tab.openFolder(tree.createRootContentHolder())
                }
            } else {
                mainManager.replaceCurrentTabWith(
                    FilesTab(tree.createRootContentHolder())
                )
            }
        }
    }
}