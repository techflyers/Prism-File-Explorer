package com.raival.compose.file.explorer.screen.main.tab.files.service.remote

data class NetworkConnectionModel(
    val id: String,
    val name: String,
    val type: String, // "FTP", "SFTP", "LAN/SMB", "WebDav"
    val host: String,
    val port: Int,
    val username: String,
    val password: String,
    val rootPath: String = "/",
    val protocol: String = "http"
)
