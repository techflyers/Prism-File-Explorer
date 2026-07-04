package com.raival.compose.file.explorer.screen.main.tab.files.holder

import com.raival.compose.file.explorer.App.Companion.globalClass
import com.raival.compose.file.explorer.R
import com.raival.compose.file.explorer.common.emptyString
import com.raival.compose.file.explorer.screen.main.tab.files.misc.ContentCount
import java.io.File

class RootFileHolder : ContentHolder() {
    companion object {
        const val rootDir = "/"
    }

    val virtualRootContent = listOf(
        "/acct", "/apex", "/bin", "/cache", "/config", "/data", "/dev", "/etc",
        "/mnt", "/odm", "/oem", "/proc", "/product", "/sbin", "/sdcard",
        "/storage", "/system", "/vendor"
    )

    var contentsCount = ContentCount()
    val content = arrayListOf<LocalFileHolder>()

    override val uniquePath = rootDir
    override val displayName = globalClass.getString(R.string.root_dir)
    override val isFolder = true
    override val lastModified = 0L
    override val size = 0L
    override val extension = emptyString
    override val canAddNewContent = false
    override val canRead = true
    override val canWrite = false

    override suspend fun listContent(): ArrayList<out ContentHolder> {
        val content = ArrayList<ContentHolder>()

        virtualRootContent.forEach { item ->
            File(item).let { file ->
                if (file.exists()) {
                    content.add(LocalFileHolder(file))
                } else if (com.raival.compose.file.explorer.screen.main.tab.files.shizuku.ShizukuManager.isPrivileged) {
                    content.add(com.raival.compose.file.explorer.screen.main.tab.files.shizuku.ShizukuFileHolder.fromPath(item))
                }
            }
        }

        // Try listing the actual root directory "/" using Shizuku/root if privileged
        if (com.raival.compose.file.explorer.screen.main.tab.files.shizuku.ShizukuManager.isPrivileged) {
            val rootHolder = com.raival.compose.file.explorer.screen.main.tab.files.shizuku.ShizukuFileHolder.fromPath("/")
            try {
                rootHolder.listContent().forEach { item ->
                    if (content.none { it.displayName == item.displayName }) {
                        content.add(item)
                    }
                }
            } catch (_: Exception) {}
        }

        contentsCount = ContentCount(folders = content.size)

        return content
    }

    override suspend fun getParent() = null
    override suspend fun getContentCount() = contentsCount
    override suspend fun findFile(name: String) = listContent().find { it.displayName == name }
    override suspend fun isValid() = true
}