package com.raival.compose.file.explorer.screen.main.tab.files.service.remote

import com.raival.compose.file.explorer.App.Companion.globalClass

object RemoteConnectionPool {
    private val lock = Any()
    private val sessions = mutableMapOf<String, PooledRemoteClient>()

    fun createRaw(connection: NetworkConnectionModel): RemoteClient {
        return when (connection.type) {
            "FTP" -> FtpRemoteClient(connection)
            "SFTP" -> SftpRemoteClient(connection)
            "WebDav" -> WebDavRemoteClient(connection)
            else -> LanRemoteClient(globalClass, connection)
        }
    }

    fun clientFor(connection: NetworkConnectionModel): RemoteClient {
        synchronized(lock) {
            sessions[connection.id]?.let { return it }
            val pooled = PooledRemoteClient(connection, createRaw(connection))
            pooled.connect()
            sessions[connection.id] = pooled
            return pooled
        }
    }

    fun release(connectionId: String) {
        synchronized(lock) {
            sessions.remove(connectionId)?.let { client ->
                try {
                    client.disconnect()
                } catch (_: Exception) {
                }
            }
        }
    }

    private class PooledRemoteClient(
        private val connection: NetworkConnectionModel,
        private var inner: RemoteClient
    ) : RemoteClient {
        @Synchronized
        override fun connect() {
            inner.connect()
        }

        @Synchronized
        override fun disconnect() {
            try {
                inner.disconnect()
            } catch (_: Exception) {
            }
        }

        @Synchronized
        override fun listDirectory(path: String) = withReconnect { listDirectory(path) }

        @Synchronized
        override fun createDirectory(path: String) = withReconnect { createDirectory(path) }

        @Synchronized
        override fun createFile(path: String) = withReconnect { createFile(path) }

        @Synchronized
        override fun delete(path: String, isDir: Boolean) = withReconnect { delete(path, isDir) }

        @Synchronized
        override fun deleteRecursive(path: String, isDir: Boolean) =
            withReconnect { deleteRecursive(path, isDir) }

        @Synchronized
        override fun rename(fromPath: String, toPath: String) =
            withReconnect { rename(fromPath, toPath) }

        @Synchronized
        override fun exists(path: String) = withReconnect { exists(path) }

        @Synchronized
        override fun downloadFile(
            remotePath: String,
            localPath: String,
            onProgress: (Double) -> Unit
        ) = withReconnect { downloadFile(remotePath, localPath, onProgress) }

        @Synchronized
        override fun uploadFile(
            localPath: String,
            remotePath: String,
            onProgress: (Double) -> Unit
        ) = withReconnect { uploadFile(localPath, remotePath, onProgress) }

        private fun <T> withReconnect(block: RemoteClient.() -> T): T {
            return try {
                inner.block()
            } catch (first: Exception) {
                reconnect()
                inner.block()
            }
        }

        private fun reconnect() {
            try {
                inner.disconnect()
            } catch (_: Exception) {
            }
            inner = createRaw(connection)
            inner.connect()
        }
    }
}
