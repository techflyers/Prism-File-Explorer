package com.techflyers.compose.file.explorer.screen.main.tab.files.zip

import com.techflyers.compose.file.explorer.App.Companion.globalClass
import com.techflyers.compose.file.explorer.common.isNot
import com.techflyers.compose.file.explorer.screen.main.tab.files.FilesTab
import com.techflyers.compose.file.explorer.screen.main.tab.files.holder.LocalFileHolder
import com.techflyers.compose.file.explorer.screen.main.tab.files.holder.VirtualFileHolder

class ZipManager {
    val archiveList = hashMapOf<LocalFileHolder, ZipTree>()

    /**
     * Validates the archive trees by checking if their source files are still valid.
     * Removes any invalid archive trees from the `archiveList`.
     *
     * @return A set of unique paths of the invalid archive trees.
     */
    suspend fun validateArchiveTrees(): Set<String> {
        val toRemove = mutableListOf<LocalFileHolder>()
        val invalidTrees = mutableSetOf<String>()
        for ((holder, tree) in archiveList) {
            if (!holder.isValid() || !tree.source.isValid()) {
                toRemove.add(holder)
                invalidTrees.add(holder.uniquePath)
            }
        }
        for (holder in toRemove) {
            val tree = archiveList.remove(holder)
            try {
                tree?.cleanOnExitDir?.file?.deleteRecursively()
            } catch (_: Exception) {
            }
        }
        return invalidTrees
    }

    /**
     * Immediately evicts an archive by its unique path and purges temporary extraction data.
     */
    fun evictArchive(path: String) {
        val keysToRemove = archiveList.keys.filter { it.uniquePath == path }
        for (key in keysToRemove) {
            val tree = archiveList.remove(key)
            try {
                tree?.cleanOnExitDir?.file?.deleteRecursively()
            } catch (_: Exception) {
            }
        }
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
     * If [archive] is a non-first part of a multi-part split archive (e.g., `.7z.002` or `.part2.rar`),
     * automatically resolves the primary volume in the parent folder when present.
     *
     * @param archive The archive file to open.
     * @param password Optional password for encrypted archives.
     */
    fun openArchive(archive: LocalFileHolder, password: String? = null) {
        val multiPart = ArchiveManager.resolveMultiPartArchive(archive.file)
        val effectiveArchive = if (
            multiPart.isMultiPart &&
            multiPart.missingParts.isEmpty() &&
            multiPart.primaryFile.exists() &&
            multiPart.primaryFile.absolutePath != archive.file.absolutePath
        ) {
            android.util.Log.d(
                "PrismArchive",
                "ZipManager: Redirecting split part ${archive.displayName} -> primary volume ${multiPart.primaryFile.name}"
            )
            LocalFileHolder(multiPart.primaryFile)
        } else {
            archive
        }

        android.util.Log.d(
            "PrismArchive",
            "ZipManager: openArchive() archive=${effectiveArchive.displayName}, path=${effectiveArchive.uniquePath}, passwordProvided=${!password.isNullOrEmpty()}"
        )

        val existingTreeKey = archiveList.keys.find { effectiveArchive.uniquePath == it.uniquePath }

        if (existingTreeKey != null) {
            archiveList[existingTreeKey]?.let { existingTree ->
                android.util.Log.d("PrismArchive", "ZipManager: Found existing ZipTree for archive.")
                if (password != null) {
                    android.util.Log.d("PrismArchive", "ZipManager: Updating existing tree password and forcing rebuild.")
                    existingTree.password = password
                    existingTree.invalidate()
                }

                if (existingTree.timeStamp == effectiveArchive.lastModified) {
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
        val newTree = ZipTree(effectiveArchive).apply {
            if (password != null) this.password = password
        }

        archiveList[effectiveArchive] = newTree
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
