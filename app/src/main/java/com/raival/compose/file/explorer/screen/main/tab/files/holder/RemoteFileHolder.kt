package com.raival.compose.file.explorer.screen.main.tab.files.holder

import android.content.Context
import com.raival.compose.file.explorer.App.Companion.globalClass
import com.raival.compose.file.explorer.App.Companion.logger
import com.raival.compose.file.explorer.common.emptyString
import com.raival.compose.file.explorer.common.showMsg
import com.raival.compose.file.explorer.common.toFormattedDate
import com.raival.compose.file.explorer.common.toFormattedSize
import com.raival.compose.file.explorer.screen.main.tab.files.FilesTab
import com.raival.compose.file.explorer.screen.main.tab.files.misc.ContentCount
import com.raival.compose.file.explorer.screen.main.tab.files.service.remote.NetworkConnectionModel
import com.raival.compose.file.explorer.screen.main.tab.files.service.remote.RemoteConnectionPool
import com.raival.compose.file.explorer.screen.main.tab.files.service.remote.RemoteFileItem
import com.raival.compose.file.explorer.screen.main.tab.files.service.remote.RemotePaths
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Date

class RemoteFileHolder(
    val connection: NetworkConnectionModel,
    val remotePath: String,
    private val item: RemoteFileItem? = null,
    val isConnectionRoot: Boolean = false
) : ContentHolder() {

    val client
        get() = RemoteConnectionPool.clientFor(connection)

    override val uniquePath: String =
        "remote://${connection.id}${RemotePaths.normalize(remotePath)}"

    override val displayName: String =
        if (isConnectionRoot) connection.name else (item?.name ?: RemotePaths.name(remotePath))

    override val isFolder: Boolean = isConnectionRoot || (item?.isDirectory ?: true)

    override val lastModified: Long = item?.modified?.time ?: 0L

    override val size: Long = item?.size ?: 0L

    override val extension: String =
        if (isFolder) emptyString else displayName.substringAfterLast('.', missingDelimiterValue = emptyString)
            .lowercase()

    override val canRead: Boolean = true
    override val canWrite: Boolean = true
    override val canAddNewContent: Boolean = isFolder

    private var details = emptyString
    private var contentListCount = ContentCount()

    override suspend fun getDetails(): String {
        if (details.isNotEmpty()) return details

        val rightSide = if (lastModified > 0L) {
            lastModified.toFormattedDate(customFormat = "dd/MM/yy • HH:mm")
        } else {
            connection.type
        }

        val leftSide = if (isFolder) {
            if (globalClass.preferencesManager.showFolderContentCount) {
                val count = getContentCount()
                buildString {
                    if (count.folders > 0) {
                        append("📁 ${count.folders}")
                        if (count.files > 0) append(" • ")
                    }
                    if (count.files > 0) append("📄 ${count.files}")
                    if (count.folders == 0 && count.files == 0) {
                        append(globalClass.getString(com.raival.compose.file.explorer.R.string.empty_folder))
                    }
                }
            } else connection.host
        } else {
            size.toFormattedSize()
        }

        return "$leftSide\t$rightSide".also { details = it }
    }

    override suspend fun listContent(): ArrayList<out ContentHolder> {
        return try {
            val listed = client.listDirectory(remotePath)
            var files = 0
            var folders = 0
            val children = listed.map { child ->
                if (child.isDirectory) folders++ else files++
                RemoteFileHolder(
                    connection = connection,
                    remotePath = child.path,
                    item = child
                )
            }
            contentListCount = ContentCount(files, folders)
            ArrayList(children)
        } catch (e: Exception) {
            logger.logError(e)
            showMsg(e.message ?: "Remote listing failed")
            arrayListOf()
        }
    }

    override suspend fun getParent(): ContentHolder? {
        if (isConnectionRoot || RemotePaths.isRootOf(remotePath, connection.rootPath)) {
            return null
        }
        val parentPath = RemotePaths.parent(remotePath) ?: return null
        val root = RemotePaths.normalize(connection.rootPath)
        if (RemotePaths.normalize(parentPath) == root || parentPath == "/") {
            return rootHolder(connection)
        }
        return RemoteFileHolder(
            connection = connection,
            remotePath = parentPath,
            item = RemoteFileItem(
                name = RemotePaths.name(parentPath),
                path = parentPath,
                isDirectory = true,
                size = 0,
                modified = Date(0)
            )
        )
    }

    override suspend fun createSubFile(name: String, onCreated: (ContentHolder?) -> Unit) {
        val path = RemotePaths.join(remotePath, name)
        try {
            client.createFile(path)
            onCreated(
                RemoteFileHolder(
                    connection = connection,
                    remotePath = path,
                    item = RemoteFileItem(name, path, false, 0, Date())
                )
            )
        } catch (e: Exception) {
            logger.logError(e)
            onCreated(null)
        }
    }

    override suspend fun createSubFolder(name: String, onCreated: (ContentHolder?) -> Unit) {
        val path = RemotePaths.join(remotePath, name)
        try {
            client.createDirectory(path)
            onCreated(
                RemoteFileHolder(
                    connection = connection,
                    remotePath = path,
                    item = RemoteFileItem(name, path, true, 0, Date())
                )
            )
        } catch (e: Exception) {
            logger.logError(e)
            onCreated(null)
        }
    }

    override suspend fun getContentCount() = contentListCount

    override suspend fun findFile(name: String): ContentHolder? {
        return listContent().find { it.displayName == name }
    }

    override suspend fun isValid(): Boolean {
        return try {
            RemoteConnectionPool.clientFor(connection)
            true
        } catch (_: Exception) {
            false
        }
    }

    override fun open(
        context: Context,
        anonymous: Boolean,
        skipSupportedExtensions: Boolean,
        customMimeType: String?
    ) {
        if (isFolder) return
        val tab = globalClass.mainActivityManager.getActiveTab() as? FilesTab
        tab?.isLoading = true
        val opener: CoroutineScope = tab?.scope ?: CoroutineScope(Dispatchers.IO)
        opener.launch {
            try {
                val local = downloadToCache()
                withContext(Dispatchers.Main) {
                    tab?.isLoading = false
                    local.open(context, anonymous, skipSupportedExtensions, customMimeType)
                }
            } catch (e: Exception) {
                logger.logError(e)
                withContext(Dispatchers.Main) {
                    tab?.isLoading = false
                    showMsg(e.message ?: "Failed to open remote file")
                }
            }
        }
    }

    fun downloadToCache(): LocalFileHolder {
        val destDir = File(globalClass.cleanOnExitDir.file, "remote/${connection.id}")
        destDir.mkdirs()
        val dest = File(destDir, displayName)
        client.downloadFile(remotePath, dest.absolutePath) {}
        return LocalFileHolder(dest)
    }

    companion object {
        fun rootHolder(connection: NetworkConnectionModel): RemoteFileHolder {
            return RemoteFileHolder(
                connection = connection,
                remotePath = RemotePaths.normalize(connection.rootPath),
                isConnectionRoot = true
            )
        }
    }
}
