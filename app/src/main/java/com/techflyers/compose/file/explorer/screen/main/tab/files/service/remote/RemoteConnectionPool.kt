package com.techflyers.compose.file.explorer.screen.main.tab.files.service.remote

import com.techflyers.compose.file.explorer.App.Companion.globalClass

object RemoteConnectionPool {
    private val lock = Any()
    private val sessions = mutableMapOf<String, PooledRemoteClient>()

    fun createRaw(connection: NetworkConnectionModel): RemoteClient {
        return when (connection.type.uppercase()) {
            "FTP", "FTPS" -> FtpRemoteClient(connection)
            "SFTP" -> SftpRemoteClient(connection)
            "WEBDAV" -> WebDavRemoteClient(connection)
            else -> LanRemoteClient(globalClass, connection)
        }
    }

    /**
     * Returns a pooled [RemoteClient] for [connection] without performing synchronous
     * network I/O on the calling thread (preventing NetworkOnMainThreadException when
     * called from Compose UI state initialization). The underlying socket connection is
     * established lazily on the first I/O call or explicit [RemoteClient.connect] call.
     */
    fun clientFor(connection: NetworkConnectionModel): RemoteClient {
        synchronized(lock) {
            val existing = sessions[connection.id]
            if (existing != null) {
                if (existing.matchesConfig(connection)) {
                    return existing
                }
                try {
                    existing.disconnect()
                } catch (_: Exception) {
                }
                sessions.remove(connection.id)
            }
            val pooled = PooledRemoteClient(connection, createRaw(connection))
            sessions[connection.id] = pooled
            return pooled
        }
    }

    fun release(connectionId: String) {
        val removed = synchronized(lock) {
            sessions.remove(connectionId)
        }
        if (removed != null) {
            try {
                removed.disconnect()
            } catch (_: Exception) {
            }
        }
    }

    private class PooledRemoteClient(
        private val connection: NetworkConnectionModel,
        private var inner: RemoteClient
    ) : RemoteClient {
        @Volatile
        private var connected: Boolean = false

        fun matchesConfig(other: NetworkConnectionModel): Boolean {
            return connection.type == other.type &&
                connection.host == other.host &&
                connection.port == other.port &&
                connection.username == other.username &&
                connection.password == other.password &&
                connection.protocol == other.protocol &&
                connection.rootPath == other.rootPath &&
                connection.authMethod == other.authMethod &&
                connection.smbDomain == other.smbDomain &&
                connection.smbEnableSmb2 == other.smbEnableSmb2 &&
                connection.ftpSecurityMode == other.ftpSecurityMode &&
                connection.ftpConnectionMode == other.ftpConnectionMode &&
                connection.privateKeyPath == other.privateKeyPath
        }

        @Synchronized
        override fun connect() {
            inner.connect()
            connected = true
        }

        @Synchronized
        override fun disconnect() {
            connected = false
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
                if (!connected) {
                    inner.connect()
                    connected = true
                }
                inner.block()
            } catch (first: Exception) {
                try {
                    reconnect()
                    inner.block()
                } catch (second: Exception) {
                    throw second
                }
            }
        }

        private fun reconnect() {
            connected = false
            try {
                inner.disconnect()
            } catch (_: Exception) {
            }
            inner = createRaw(connection)
            inner.connect()
            connected = true
        }
    }
}
