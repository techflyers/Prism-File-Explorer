package com.techflyers.compose.file.explorer.screen.main.tab.files.service.remote

import android.content.Context
import jcifs.CIFSContext
import jcifs.CIFSException
import jcifs.config.PropertyConfiguration
import jcifs.context.BaseContext
import jcifs.smb.NtlmPasswordAuthenticator
import jcifs.smb.SSPContext
import jcifs.smb.SmbAuthException
import jcifs.smb.SmbException
import jcifs.smb.SmbFile
import org.bouncycastle.asn1.ASN1ObjectIdentifier
import java.io.File
import java.io.FileNotFoundException
import java.net.URI
import java.util.Date
import java.util.Properties

class LanRemoteClient(
    private val context: Context,
    private val conn: NetworkConnectionModel
) : RemoteClient {
    private var cifsContext: CIFSContext? = null

    private data class EndpointParts(
        val host: String,
        val port: Int,
        val configuredRoot: String
    )

    /**
     * Strips duplicate protocol schemas, UNC backslashes, leading redundant hostnames,
     * and port prefixes (e.g. ":1445/share", "1445/share", "\\host:port\share") from path inputs.
     */
    private fun sanitizePathPrefix(rawPath: String, host: String, port: Int): String {
        var s = rawPath.trim().replace('\\', '/')
        if (s.startsWith("smb://", ignoreCase = true)) {
            s = s.substring(6)
        }
        s = s.trimStart('/')

        // Strip host if present at beginning of path (e.g. "192.168.1.100/share" or "[fe80::1]/share")
        if (host.isNotBlank()) {
            val bracketHost = "[$host]"
            if (s.startsWith(bracketHost, ignoreCase = true)) {
                s = s.substring(bracketHost.length).trimStart('/')
            } else if (s.startsWith(host, ignoreCase = true)) {
                s = s.substring(host.length).trimStart('/')
            }
        }

        // Strip port prefix if present (e.g. ":1445/share", ":1445", "1445/share", or any ":<digits>/")
        val portStr = port.toString()
        if (s.startsWith(":$portStr/", ignoreCase = true) || s.equals(":$portStr", ignoreCase = true)) {
            s = s.substring(portStr.length + 1).trimStart('/')
        } else if (s.startsWith(":") && s.contains('/')) {
            val potentialPort = s.substring(1).substringBefore('/')
            if (potentialPort.isNotEmpty() && potentialPort.all { it.isDigit() }) {
                s = s.substringAfter('/').trimStart('/')
            }
        } else if (s.startsWith("$portStr/", ignoreCase = true) || s.equals(portStr, ignoreCase = true)) {
            s = s.substring(portStr.length).trimStart('/')
        }

        return s.trim('/')
    }

    private fun parseEndpoint(): EndpointParts {
        var rawHost = conn.host.trim().replace('\\', '/')
        if (rawHost.startsWith("smb://", ignoreCase = true)) {
            rawHost = rawHost.substring(6)
        }
        rawHost = rawHost.trimStart('/')

        val hostPortPart = rawHost.substringBefore('/')
        val embeddedPath = rawHost.substringAfter('/', "").trim('/')

        var parsedHost = hostPortPart
        var parsedPort = conn.port

        // Handle IPv6 bracket notation [fe80::1]:port or [fe80::1]
        if (hostPortPart.startsWith("[")) {
            val endBracket = hostPortPart.indexOf(']')
            if (endBracket > 0) {
                parsedHost = hostPortPart.substring(1, endBracket)
                val after = hostPortPart.substring(endBracket + 1)
                if (after.startsWith(":")) {
                    val p = after.substring(1).toIntOrNull()
                    if (p != null && p > 0 && (parsedPort <= 0 || parsedPort == 445)) {
                        parsedPort = p
                    }
                }
            }
        } else if (hostPortPart.contains(':')) {
            val h = hostPortPart.substringBeforeLast(':')
            val p = hostPortPart.substringAfterLast(':').toIntOrNull()
            if (h.isNotBlank() && p != null && p > 0) {
                parsedHost = h
                if (parsedPort <= 0 || parsedPort == 445) {
                    parsedPort = p
                }
            }
        }

        val effectivePort = if (parsedPort > 0) parsedPort else 445
        val explicitRoot = sanitizePathPrefix(conn.rootPath, parsedHost, effectivePort)
        val cleanEmbedded = sanitizePathPrefix(embeddedPath, parsedHost, effectivePort)

        val combinedRoot = when {
            cleanEmbedded.isEmpty() && explicitRoot.isEmpty() -> ""
            cleanEmbedded.isEmpty() -> explicitRoot
            explicitRoot.isEmpty() -> cleanEmbedded
            explicitRoot.equals(cleanEmbedded, ignoreCase = true) ||
                explicitRoot.startsWith("$cleanEmbedded/", ignoreCase = true) -> explicitRoot
            cleanEmbedded.startsWith("$explicitRoot/", ignoreCase = true) -> cleanEmbedded
            else -> "$cleanEmbedded/$explicitRoot"
        }

        return EndpointParts(
            host = parsedHost,
            port = effectivePort,
            configuredRoot = combinedRoot
        )
    }

    private fun createCifsContext(
        enableSmb2: Boolean = conn.effectiveSmbEnableSmb2,
        authOverride: String? = null
    ): CIFSContext {
        val connTimeoutMs = (conn.effectiveConnectTimeoutSec.coerceAtLeast(3) * 1000).toString()
        val readTimeoutMs = (conn.effectiveReadTimeoutSec.coerceAtLeast(5) * 1000).toString()

        val prop = Properties().apply {
            setProperty("jcifs.smb.client.enableSMB2", enableSmb2.toString())
            setProperty("jcifs.smb.client.useSMB2Negotiation", enableSmb2.toString())
            setProperty("jcifs.smb.client.disableSMB1", "false")
            setProperty("jcifs.smb.client.minVersion", "SMB1")
            setProperty("jcifs.smb.client.maxVersion", if (enableSmb2) "SMB311" else "SMB1")
            setProperty("jcifs.smb.client.ipcSigningEnforced", "false")
            setProperty("jcifs.smb.client.signingEnforced", "false")
            // DFS referral queries cause STATUS_OBJECT_PATH_INVALID (0xC0000039 / "The specified path is invalid")
            // on standalone Windows/Samba/NAS servers when non-root paths or non-standard ports are accessed.
            setProperty("jcifs.smb.client.dfs.disabled", "true")
            setProperty("jcifs.smb.client.responseTimeout", readTimeoutMs)
            setProperty("jcifs.smb.client.soTimeout", readTimeoutMs)
            setProperty("jcifs.smb.client.connTimeout", connTimeoutMs)
            setProperty("jcifs.netbios.cachePolicy", "600")
            setProperty("jcifs.resolveOrder", "LMHOSTS,DNS,WINS,BCAST")
            if (conn.effectiveKeepAliveSec > 0) {
                setProperty(
                    "jcifs.smb.client.sessionTimeout",
                    (conn.effectiveKeepAliveSec.coerceAtLeast(15) * 1000).toString()
                )
            }
        }

        val base = BaseContext(PropertyConfiguration(prop))
        val authMethod = (authOverride ?: conn.effectiveAuthMethod).uppercase()

        return when {
            authMethod == "ANONYMOUS" -> base.withAnonymousCredentials()
            authMethod == "GUEST" -> base.withCredentials(
                PrismNtlmAuthenticator(null, null, null, NtlmPasswordAuthenticator.AuthenticationType.GUEST)
            )
            conn.username.isNotBlank() -> {
                val explicitDomain = conn.effectiveSmbDomain.trim()
                val rawUser = conn.username.trim()
                val domain = when {
                    explicitDomain.isNotEmpty() -> explicitDomain
                    rawUser.contains("\\") -> rawUser.substringBefore("\\")
                    rawUser.contains("@") -> rawUser.substringAfter("@")
                    else -> null
                }
                val user = when {
                    rawUser.contains("\\") -> rawUser.substringAfter("\\")
                    rawUser.contains("@") && explicitDomain.isEmpty() -> rawUser.substringBefore("@")
                    else -> rawUser
                }
                base.withCredentials(PrismNtlmAuthenticator(domain, user, conn.password))
            }
            else -> base.withAnonymousCredentials()
        }
    }

    @Synchronized
    private fun getContext(): CIFSContext {
        cifsContext?.let { return it }
        return createCifsContext().also { cifsContext = it }
    }

    /**
     * Resolves a caller-supplied [path] against the connection's configured root share/folder
     * without duplicating [EndpointParts.configuredRoot] when [path] already begins with it,
     * handling ancestors, UNC syntax, protocol prefixes, and relative sub-paths safely.
     */
    private fun resolveCombinedPath(path: String): String {
        val endpoint = parseEndpoint()
        val configuredRoot = endpoint.configuredRoot
        val requestPath = sanitizePathPrefix(path, endpoint.host, endpoint.port)

        return when {
            configuredRoot.isEmpty() && requestPath.isEmpty() -> ""
            configuredRoot.isEmpty() -> requestPath
            requestPath.isEmpty() -> configuredRoot
            requestPath.equals(configuredRoot, ignoreCase = true) -> configuredRoot
            requestPath.startsWith("$configuredRoot/", ignoreCase = true) -> requestPath
            configuredRoot.startsWith("$requestPath/", ignoreCase = true) -> requestPath
            else -> "$configuredRoot/$requestPath"
        }
    }

    private fun buildUrl(path: String, isDirectory: Boolean = false): String {
        val endpoint = parseEndpoint()
        val hostPart = if (endpoint.host.contains(':') && !endpoint.host.startsWith('[')) {
            "[${endpoint.host}]"
        } else {
            endpoint.host
        }
        val portPart = if (endpoint.port == 445 || endpoint.port <= 0) "" else ":${endpoint.port}"
        val combined = resolveCombinedPath(path)

        val encodedPath = if (combined.isEmpty()) {
            ""
        } else {
            combined.split('/')
                .filter { it.isNotEmpty() }
                .joinToString("/") { segment ->
                    try {
                        URI(null, null, segment, null).rawPath ?: segment
                    } catch (_: Throwable) {
                        segment
                    }
                }
        }

        val trailing = if (encodedPath.isEmpty() || isDirectory) "/" else ""
        return "smb://$hostPart$portPart/$encodedPath$trailing"
    }

    private fun <T> executeWithFallback(action: (CIFSContext) -> T): T {
        val initialCtx = getContext()
        try {
            return action(initialCtx)
        } catch (authEx: SmbAuthException) {
            // If guest/empty credentials failed, try anonymous or guest fallback once
            val fallbackAuth = when {
                conn.username.isBlank() && conn.effectiveAuthMethod.uppercase() != "GUEST" -> "GUEST"
                conn.username.isBlank() && conn.effectiveAuthMethod.uppercase() == "GUEST" -> "ANONYMOUS"
                else -> null
            }
            if (fallbackAuth != null) {
                try {
                    val altCtx = createCifsContext(authOverride = fallbackAuth)
                    val result = action(altCtx)
                    synchronized(this) { cifsContext = altCtx }
                    return result
                } catch (_: Throwable) {
                }
            }
            throw authEx
        } catch (e: Throwable) {
            // If SMB2/3 negotiation failed against a legacy SMB1 NAS, try SMB1 fallback once
            if (conn.effectiveSmbEnableSmb2) {
                try {
                    val smb1Ctx = createCifsContext(enableSmb2 = false)
                    val result = action(smb1Ctx)
                    synchronized(this) { cifsContext = smb1Ctx }
                    return result
                } catch (_: Throwable) {
                }
            }
            throw e
        }
    }

    override fun connect() {
        executeWithFallback { ctx ->
            val testUrl = buildUrl("", isDirectory = true)
            val rootFile = SmbFile(testUrl, ctx)
            val combined = resolveCombinedPath("")
            if (combined.isEmpty()) {
                // Server root ("smb://host:port/"): verify by enumerating shares or connecting
                try {
                    rootFile.listFiles()
                } catch (e: SmbAuthException) {
                    throw e
                } catch (_: Throwable) {
                    rootFile.connect()
                }
            } else {
                try {
                    rootFile.connect()
                } catch (authEx: SmbAuthException) {
                    throw authEx
                } catch (e: Exception) {
                    // If connecting directly to configured share/subfolder failed, test whether server root is reachable
                    // to provide a clear error message distinguishing host/port connection failure from bad share/path.
                    val serverRootUrl = "smb://${parseEndpoint().let { ep ->
                        val h = if (ep.host.contains(':') && !ep.host.startsWith('[')) "[${ep.host}]" else ep.host
                        val p = if (ep.port == 445 || ep.port <= 0) "" else ":${ep.port}"
                        "$h$p/"
                    }}"
                    try {
                        val serverFile = SmbFile(serverRootUrl, ctx)
                        serverFile.connect()
                        // Server is reachable, so the problem is specifically the remote path/share
                        throw SmbException(
                            "Connected to SMB server, but remote path '$combined' is invalid or not accessible: ${e.message ?: e.javaClass.simpleName}",
                            e
                        )
                    } catch (probeEx: Exception) {
                        if (probeEx is SmbAuthException) throw probeEx
                        // Server root also unreachable, rethrow original error
                        throw e
                    }
                }
            }
        }
    }

    @Synchronized
    override fun disconnect() {
        try {
            cifsContext?.close()
        } catch (_: Throwable) {
        }
        cifsContext = null
    }

    override fun listDirectory(path: String): List<RemoteFileItem> {
        return executeWithFallback { ctx ->
            val url = buildUrl(path, isDirectory = true)
            val dir = SmbFile(url, ctx)
            val files = dir.listFiles() ?: emptyArray()
            val combinedResolved = resolveCombinedPath(path)
            val isServerRoot = combinedResolved.isEmpty()

            val normalizedParent = when {
                path == "/" || path.isBlank() -> if (combinedResolved.isNotEmpty()) "/$combinedResolved" else ""
                else -> "/" + path.trim().replace('\\', '/').trim('/')
            }
            files.mapNotNull { file ->
                val rawName = file.name ?: return@mapNotNull null
                val cleanName = rawName.trimEnd('/')
                if (cleanName.isBlank() || cleanName == "." || cleanName == "..") {
                    return@mapNotNull null
                }

                // Filter out IPC$ and non-disk administrative pipes/printers when listing server root shares
                if (isServerRoot) {
                    if (cleanName.equals("IPC$", ignoreCase = true) ||
                        cleanName.equals("print$", ignoreCase = true)
                    ) {
                        return@mapNotNull null
                    }
                    val smbType = try { file.type } catch (_: Throwable) { SmbFile.TYPE_SHARE }
                    if (smbType == SmbFile.TYPE_PRINTER ||
                        smbType == SmbFile.TYPE_NAMED_PIPE ||
                        smbType == SmbFile.TYPE_COMM
                    ) {
                        return@mapNotNull null
                    }
                }

                val isDir = try {
                    isServerRoot || file.isDirectory || rawName.endsWith("/")
                } catch (_: Throwable) {
                    isServerRoot || rawName.endsWith("/")
                }

                val size = if (isDir) 0L else try { file.length() } catch (_: Throwable) { 0L }
                val modified = try {
                    val ts = file.lastModified()
                    if (ts > 0L) Date(ts) else Date()
                } catch (_: Throwable) {
                    Date()
                }
                val itemPath = "$normalizedParent/$cleanName"

                RemoteFileItem(
                    name = cleanName,
                    path = itemPath,
                    isDirectory = isDir,
                    size = size,
                    modified = modified
                )
            }
        }
    }

    override fun createDirectory(path: String) {
        executeWithFallback { ctx ->
            val url = buildUrl(path, isDirectory = true)
            val smbFile = SmbFile(url, ctx)
            if (!smbFile.exists()) {
                smbFile.mkdirs()
            }
        }
    }

    override fun delete(path: String, isDir: Boolean) {
        executeWithFallback { ctx ->
            val url = buildUrl(path, isDirectory = isDir)
            val smbFile = SmbFile(url, ctx)
            if (smbFile.exists()) {
                smbFile.delete()
            } else if (!isDir) {
                val dirFile = SmbFile(buildUrl(path, isDirectory = true), ctx)
                if (dirFile.exists()) dirFile.delete()
            }
        }
    }

    override fun deleteRecursive(path: String, isDir: Boolean) {
        executeWithFallback { ctx ->
            // jcifs-ng SmbFile.delete() on a directory URL (ending with '/') recursively deletes its contents!
            val targetAsDir = SmbFile(buildUrl(path, isDirectory = true), ctx)
            val targetAsFile = SmbFile(buildUrl(path, isDirectory = false), ctx)
            when {
                isDir && targetAsDir.exists() -> targetAsDir.delete()
                targetAsFile.exists() -> {
                    if (targetAsFile.isDirectory) {
                        targetAsDir.delete()
                    } else {
                        targetAsFile.delete()
                    }
                }
                targetAsDir.exists() -> targetAsDir.delete()
            }
        }
    }

    override fun downloadFile(remotePath: String, localPath: String, onProgress: (Double) -> Unit) {
        executeWithFallback { ctx ->
            val url = buildUrl(remotePath, isDirectory = false)
            val smbFile = SmbFile(url, ctx)
            val totalSize = try { smbFile.length() } catch (_: Throwable) { -1L }
            val localFile = File(localPath)
            localFile.parentFile?.mkdirs()

            onProgress(0.0)
            var bytesCopied = 0L
            smbFile.inputStream.use { input ->
                localFile.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        bytesCopied += read
                        if (totalSize > 0) {
                            onProgress((bytesCopied.toDouble() / totalSize).coerceIn(0.0, 1.0))
                        }
                    }
                }
            }
            onProgress(1.0)
        }
    }

    override fun uploadFile(localPath: String, remotePath: String, onProgress: (Double) -> Unit) {
        val localFile = File(localPath)
        if (!localFile.exists()) throw FileNotFoundException("Local file not found: $localPath")
        val totalSize = localFile.length()

        val parent = RemotePaths.parent(remotePath)
        if (parent != null && parent.isNotEmpty() && parent != "/") {
            try {
                createDirectory(parent)
            } catch (_: Throwable) {
            }
        }

        executeWithFallback { ctx ->
            val url = buildUrl(remotePath, isDirectory = false)
            val smbFile = SmbFile(url, ctx)

            onProgress(0.0)
            var bytesCopied = 0L
            localFile.inputStream().use { input ->
                smbFile.outputStream.use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        bytesCopied += read
                        if (totalSize > 0) {
                            onProgress((bytesCopied.toDouble() / totalSize).coerceIn(0.0, 1.0))
                        }
                    }
                }
            }
            onProgress(1.0)
        }
    }

    override fun createFile(path: String) {
        val parent = RemotePaths.parent(path)
        if (parent != null && parent.isNotEmpty() && parent != "/") {
            try {
                createDirectory(parent)
            } catch (_: Throwable) {
            }
        }
        executeWithFallback { ctx ->
            val url = buildUrl(path, isDirectory = false)
            val smbFile = SmbFile(url, ctx)
            if (!smbFile.exists()) {
                smbFile.createNewFile()
            }
        }
    }

    override fun rename(fromPath: String, toPath: String) {
        executeWithFallback { ctx ->
            val probeFile = SmbFile(buildUrl(fromPath, isDirectory = false), ctx)
            val isDir = try {
                probeFile.isDirectory
            } catch (_: Throwable) {
                false
            }
            val fromFile = SmbFile(buildUrl(fromPath, isDirectory = isDir), ctx)
            val toFile = SmbFile(buildUrl(toPath, isDirectory = isDir), ctx)
            fromFile.renameTo(toFile)
        }
    }

    override fun exists(path: String): Boolean {
        return try {
            executeWithFallback { ctx ->
                val combined = resolveCombinedPath(path)
                if (combined.isEmpty()) {
                    true
                } else {
                    val fileCandidate = SmbFile(buildUrl(path, isDirectory = false), ctx)
                    if (fileCandidate.exists()) {
                        true
                    } else {
                        SmbFile(buildUrl(path, isDirectory = true), ctx).exists()
                    }
                }
            }
        } catch (_: Throwable) {
            false
        }
    }
}

/**
 * Custom [NtlmPasswordAuthenticator] that handles non-Windows/embedded SMB2 servers
 * (such as Android SMB servers, Samba, NAS appliances, and macOS file sharing) where
 * the server responds with STATUS_SUCCESS with an empty security blob after NTLMSSP
 * Authenticate (Type 3) rather than a wrapping SPNEGO NegTokenTarg.
 *
 * In vanilla JCIFS-NG, SpnegoContext requires a final NegTokenTarg from the server to set
 * completed=true; when an empty STATUS_SUCCESS is returned, SpnegoContext.isEstablished()
 * remains false, which causes JCIFS-NG to loop back and re-enter NtlmContext with an empty
 * token, throwing "SmbException: Invalid state".
 *
 * This authenticator wraps the SSPContext in a [LenientSpnegoContext] that marks the
 * context as established once the NTLMSSP Type 3 (Authenticate) message has been produced,
 * allowing session setup to complete cleanly.
 */
class PrismNtlmAuthenticator : NtlmPasswordAuthenticator {
    constructor() : super()
    constructor(type: AuthenticationType) : super(type)
    constructor(domain: String?, user: String?, pass: String?) : super(domain, user, pass)
    constructor(domain: String?, user: String?, pass: String?, type: AuthenticationType) : super(domain, user, pass, type)

    override fun clone(): PrismNtlmAuthenticator {
        val copy = PrismNtlmAuthenticator(userDomain, username, password)
        cloneInternal(this, copy)
        return copy
    }

    override fun createContext(
        context: CIFSContext?,
        targetHost: String?,
        targetDomain: String?,
        token: ByteArray?,
        isSigning: Boolean
    ): SSPContext {
        val delegate = super.createContext(context, targetHost, targetDomain, token, isSigning)
        return LenientSpnegoContext(delegate)
    }
}

/**
 * A decorating [SSPContext] that delegates all operations to JCIFS-NG's SPNEGO context,
 * but returns [isEstablished] = true once the outgoing NTLMSSP Type 3 (Authenticate)
 * message has been generated. This prevents "SmbException: Invalid state" when servers
 * return STATUS_SUCCESS with an empty payload.
 */
private class LenientSpnegoContext(private val delegate: SSPContext) : SSPContext {
    private var type3Generated = false

    private fun containsType3(bytes: ByteArray?): Boolean {
        if (bytes == null || bytes.size < 12) return false
        val pattern = byteArrayOf(
            'N'.code.toByte(), 'T'.code.toByte(), 'L'.code.toByte(), 'M'.code.toByte(),
            'S'.code.toByte(), 'S'.code.toByte(), 'P'.code.toByte(), 0, 3, 0, 0, 0
        )
        for (i in 0..bytes.size - pattern.size) {
            var match = true
            for (j in pattern.indices) {
                if (bytes[i + j] != pattern[j]) {
                    match = false
                    break
                }
            }
            if (match) return true
        }
        return false
    }

    override fun isEstablished(): Boolean {
        return delegate.isEstablished || type3Generated
    }

    @Throws(CIFSException::class)
    override fun initSecContext(token: ByteArray?, off: Int, len: Int): ByteArray? {
        val out = delegate.initSecContext(token, off, len)
        if (out != null && containsType3(out)) {
            type3Generated = true
        }
        return out
    }

    @Throws(CIFSException::class)
    override fun getSigningKey(): ByteArray? = delegate.signingKey

    override fun getNetbiosName(): String? = delegate.netbiosName

    @Throws(CIFSException::class)
    override fun dispose() = delegate.dispose()

    override fun isSupported(oid: ASN1ObjectIdentifier?): Boolean = delegate.isSupported(oid)

    override fun isPreferredMech(oid: ASN1ObjectIdentifier?): Boolean = delegate.isPreferredMech(oid)

    override fun getFlags(): Int = delegate.flags

    override fun getSupportedMechs(): Array<ASN1ObjectIdentifier> = delegate.supportedMechs

    override fun supportsIntegrity(): Boolean = delegate.supportsIntegrity()

    @Throws(CIFSException::class)
    override fun calculateMIC(data: ByteArray?): ByteArray? = delegate.calculateMIC(data)

    @Throws(CIFSException::class)
    override fun verifyMIC(data: ByteArray?, mic: ByteArray?) = delegate.verifyMIC(data, mic)

    override fun isMICAvailable(): Boolean = delegate.isMICAvailable
}
