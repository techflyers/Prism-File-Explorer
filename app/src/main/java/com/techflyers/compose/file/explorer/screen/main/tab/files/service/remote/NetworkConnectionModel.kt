package com.techflyers.compose.file.explorer.screen.main.tab.files.service.remote

data class NetworkConnectionModel(
    val id: String,
    val name: String,
    val type: String, // "FTP", "SFTP", "LAN/SMB", "WebDav"
    val host: String,
    val port: Int,
    val username: String,
    val password: String,
    val rootPath: String = "/",
    val protocol: String = "http",
    // Granular protocol & transport options (nullable for backwards compatibility with Gson)
    val transferMode: String? = "BINARY", // "BINARY", "ASCII", "AUTO"
    val ftpConnectionMode: String? = "PASSIVE", // "PASSIVE", "ACTIVE"
    val ftpSecurityMode: String? = "PLAIN", // "PLAIN", "FTPS_EXPLICIT", "FTPS_IMPLICIT"
    val authMethod: String? = "PASSWORD", // "PASSWORD", "ANONYMOUS", "PRIVATE_KEY", "PASSWORD_AND_KEY", "GUEST", "BASIC", "BEARER"
    val privateKeyPath: String? = "",
    val privateKeyPassphrase: String? = "",
    val keepAliveIntervalSec: Int? = 60,
    val connectTimeoutSec: Int? = 15,
    val readTimeoutSec: Int? = 30,
    val encoding: String? = "UTF-8", // "UTF-8", "ISO-8859-1", "Windows-1252"
    val verifySslCert: Boolean? = true,
    val sftpCompression: Boolean? = false,
    val smbDomain: String? = "",
    val smbEnableSmb2: Boolean? = true
) {
    val effectiveTransferMode: String get() = transferMode?.ifBlank { "BINARY" } ?: "BINARY"
    val effectiveFtpConnectionMode: String get() = ftpConnectionMode?.ifBlank { "PASSIVE" } ?: "PASSIVE"
    val effectiveFtpSecurityMode: String get() = ftpSecurityMode?.ifBlank { "PLAIN" } ?: "PLAIN"
    val effectiveAuthMethod: String get() = authMethod?.ifBlank { "PASSWORD" } ?: "PASSWORD"
    val effectivePrivateKeyPath: String get() = privateKeyPath ?: ""
    val effectivePrivateKeyPassphrase: String get() = privateKeyPassphrase ?: ""
    val effectiveKeepAliveSec: Int get() = (keepAliveIntervalSec ?: 60).coerceIn(0, 3600)
    val effectiveConnectTimeoutSec: Int get() = (connectTimeoutSec ?: 15).coerceIn(3, 300)
    val effectiveReadTimeoutSec: Int get() = (readTimeoutSec ?: 30).coerceIn(5, 600)
    val effectiveEncoding: String get() = encoding?.ifBlank { "UTF-8" } ?: "UTF-8"
    val effectiveVerifySslCert: Boolean get() = verifySslCert ?: true
    val effectiveSftpCompression: Boolean get() = sftpCompression ?: false
    val effectiveSmbDomain: String get() = smbDomain ?: ""
    val effectiveSmbEnableSmb2: Boolean get() = smbEnableSmb2 ?: true
}
