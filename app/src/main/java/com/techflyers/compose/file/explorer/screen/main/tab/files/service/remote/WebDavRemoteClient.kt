package com.techflyers.compose.file.explorer.screen.main.tab.files.service.remote

import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSink
import org.w3c.dom.Element
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.StringReader
import java.net.URI
import java.net.URLDecoder
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import javax.xml.parsers.DocumentBuilderFactory

class WebDavRemoteClient(
    private val conn: NetworkConnectionModel
) : RemoteClient {
    private val client: OkHttpClient by lazy {
        val builder = OkHttpClient.Builder()
            .connectTimeout(conn.effectiveConnectTimeoutSec.toLong(), TimeUnit.SECONDS)
            .readTimeout(conn.effectiveReadTimeoutSec.toLong(), TimeUnit.SECONDS)
            .writeTimeout(conn.effectiveReadTimeoutSec.toLong(), TimeUnit.SECONDS)

        if (conn.effectiveKeepAliveSec > 0) {
            builder.pingInterval(conn.effectiveKeepAliveSec.toLong(), TimeUnit.SECONDS)
        }

        if (!conn.effectiveVerifySslCert) {
            try {
                val trustAllCerts = arrayOf<TrustManager>(
                    object : X509TrustManager {
                        override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
                        override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
                        override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
                    }
                )
                val sslContext = SSLContext.getInstance("TLS")
                sslContext.init(null, trustAllCerts, SecureRandom())
                builder.sslSocketFactory(sslContext.socketFactory, trustAllCerts[0] as X509TrustManager)
                builder.hostnameVerifier { _, _ -> true }
            } catch (_: Exception) {}
        }

        builder.build()
    }

    private val baseUrl: String
        get() {
            var hostStr = conn.host.trim()
            if (hostStr.startsWith("http://", ignoreCase = true)) {
                hostStr = hostStr.substring(7)
            } else if (hostStr.startsWith("https://", ignoreCase = true)) {
                hostStr = hostStr.substring(8)
            }
            if (hostStr.contains("/")) {
                hostStr = hostStr.substringBefore("/")
            }
            val effectivePort = if (conn.port > 0) conn.port else if (conn.protocol == "https") 443 else 80
            return "${conn.protocol}://$hostStr:$effectivePort"
        }

    private fun authHeader(): String? {
        return when (conn.effectiveAuthMethod) {
            "NONE", "ANONYMOUS" -> null
            "BEARER" -> if (conn.password.isNotBlank()) "Bearer ${conn.password.trim()}" else null
            else -> {
                if (conn.username.isEmpty() && conn.password.isEmpty()) null
                else Credentials.basic(conn.username, conn.password, java.nio.charset.Charset.forName(conn.effectiveEncoding))
            }
        }
    }

    private fun getFullUrl(path: String, isDirectory: Boolean = false): String {
        val rawSegments = path.trim().split('/').filter { it.isNotEmpty() }
        val encodedPath = rawSegments.joinToString("/") { seg ->
            try {
                URI(null, null, seg, null).rawPath ?: seg
            } catch (_: Exception) {
                seg
            }
        }
        val suffix = if (isDirectory && encodedPath.isNotEmpty()) "/" else ""
        return if (encodedPath.isEmpty()) "$baseUrl/" else "$baseUrl/$encodedPath$suffix"
    }

    private fun decodeUrlPath(rawHref: String): String {
        return try {
            // Preserve '+' characters when URL-decoding WebDAV href paths
            URLDecoder.decode(rawHref.replace("+", "%2B"), conn.effectiveEncoding)
        } catch (_: Exception) {
            rawHref
        }
    }

    override fun connect() {
        val cleanRoot = conn.rootPath.ifBlank { "/" }
        val url = getFullUrl(cleanRoot, isDirectory = true)
        val builder = Request.Builder()
            .url(url)
            .method("PROPFIND", "".toRequestBody("text/xml".toMediaType()))
            .addHeader("Depth", "0")

        authHeader()?.let { builder.addHeader("Authorization", it) }

        client.newCall(builder.build()).execute().use { response ->
            if (!response.isSuccessful) {
                throw Exception("WebDAV connection failed: ${response.code} ${response.message}")
            }
        }
    }

    override fun disconnect() {
    }

    override fun listDirectory(path: String): List<RemoteFileItem> {
        val targetPath = path.ifBlank { conn.rootPath.ifBlank { "/" } }
        val url = getFullUrl(targetPath, isDirectory = true)
        val builder = Request.Builder()
            .url(url)
            .method("PROPFIND", "".toRequestBody("text/xml".toMediaType()))
            .addHeader("Depth", "1")

        authHeader()?.let { builder.addHeader("Authorization", it) }

        client.newCall(builder.build()).execute().use { response ->
            if (!response.isSuccessful) {
                throw Exception("WebDAV PROPFIND failed: ${response.code} ${response.message}")
            }

            val body = response.body?.string() ?: return emptyList()
            return parsePropfindXml(body, targetPath)
        }
    }

    override fun createDirectory(path: String) {
        val url = getFullUrl(path, isDirectory = true)
        val builder = Request.Builder()
            .url(url)
            .method("MKCOL", "".toRequestBody("text/xml".toMediaType()))

        authHeader()?.let { builder.addHeader("Authorization", it) }

        client.newCall(builder.build()).execute().use { response ->
            if (!response.isSuccessful && response.code != 405) {
                throw Exception("WebDAV MKCOL failed: ${response.code} ${response.message}")
            }
        }
    }

    override fun delete(path: String, isDir: Boolean) {
        val url = getFullUrl(path, isDirectory = isDir)
        val builder = Request.Builder()
            .url(url)
            .delete()

        authHeader()?.let { builder.addHeader("Authorization", it) }

        client.newCall(builder.build()).execute().use { response ->
            if (!response.isSuccessful && response.code != 404) {
                throw Exception("WebDAV DELETE failed: ${response.code} ${response.message}")
            }
        }
    }

    override fun downloadFile(remotePath: String, localPath: String, onProgress: (Double) -> Unit) {
        val url = getFullUrl(remotePath, isDirectory = false)
        val builder = Request.Builder()
            .url(url)
            .get()

        authHeader()?.let { builder.addHeader("Authorization", it) }

        client.newCall(builder.build()).execute().use { response ->
            if (!response.isSuccessful) {
                throw Exception("WebDAV GET failed: ${response.code} ${response.message}")
            }
            val body = response.body ?: throw Exception("Response body is null")
            val totalSize = body.contentLength()
            val localFile = File(localPath)
            localFile.parentFile?.mkdirs()

            onProgress(0.0)
            var bytesCopied = 0L
            body.byteStream().use { input ->
                FileOutputStream(localFile).use { output ->
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
                ensureDirectory(parent)
            } catch (_: Exception) {}
        }

        val url = getFullUrl(remotePath, isDirectory = false)
        val contentType = (if (conn.effectiveTransferMode == "ASCII") "text/plain" else "application/octet-stream").toMediaType()

        onProgress(0.0)
        val requestBody = object : RequestBody() {
            override fun contentType(): MediaType = contentType
            override fun contentLength(): Long = totalSize
            override fun writeTo(sink: BufferedSink) {
                val buffer = ByteArray(64 * 1024)
                var uploaded = 0L
                FileInputStream(localFile).use { fis ->
                    var read: Int
                    while (fis.read(buffer).also { read = it } != -1) {
                        sink.write(buffer, 0, read)
                        uploaded += read
                        if (totalSize > 0) {
                            onProgress((uploaded.toDouble() / totalSize).coerceIn(0.0, 1.0))
                        }
                    }
                }
            }
        }

        val builder = Request.Builder()
            .url(url)
            .put(requestBody)

        authHeader()?.let { builder.addHeader("Authorization", it) }

        client.newCall(builder.build()).execute().use { response ->
            if (!response.isSuccessful) {
                throw Exception("WebDAV PUT failed: ${response.code} ${response.message}")
            }
            onProgress(1.0)
        }
    }

    override fun createFile(path: String) {
        val parent = RemotePaths.parent(path)
        if (parent != null && parent.isNotEmpty() && parent != "/") {
            try {
                ensureDirectory(parent)
            } catch (_: Exception) {}
        }
        val url = getFullUrl(path, isDirectory = false)
        val builder = Request.Builder()
            .url(url)
            .put(ByteArray(0).toRequestBody("application/octet-stream".toMediaType()))

        authHeader()?.let { builder.addHeader("Authorization", it) }

        client.newCall(builder.build()).execute().use { response ->
            if (!response.isSuccessful) {
                throw Exception("WebDAV PUT failed: ${response.code} ${response.message}")
            }
        }
    }

    override fun rename(fromPath: String, toPath: String) {
        val builder = Request.Builder()
            .url(getFullUrl(fromPath))
            .method("MOVE", "".toRequestBody(null))
            .addHeader("Destination", getFullUrl(toPath))
            .addHeader("Overwrite", "T")

        authHeader()?.let { builder.addHeader("Authorization", it) }

        client.newCall(builder.build()).execute().use { response ->
            if (!response.isSuccessful) {
                throw Exception("WebDAV MOVE failed: ${response.code} ${response.message}")
            }
        }
    }

    override fun exists(path: String): Boolean {
        val builder = Request.Builder()
            .url(getFullUrl(path))
            .method("PROPFIND", "".toRequestBody("text/xml".toMediaType()))
            .addHeader("Depth", "0")

        authHeader()?.let { builder.addHeader("Authorization", it) }

        return try {
            client.newCall(builder.build()).execute().use { response ->
                response.isSuccessful
            }
        } catch (_: Exception) {
            false
        }
    }

    override fun deleteRecursive(path: String, isDir: Boolean) {
        delete(path, isDir)
    }

    private fun getElementsByLocalName(element: Element, localName: String): List<Element> {
        val list = mutableListOf<Element>()
        val nodes = element.getElementsByTagName("*")
        for (i in 0 until nodes.length) {
            val node = nodes.item(i)
            if (node is Element) {
                val tagName = node.tagName ?: ""
                if (tagName.equals(localName, ignoreCase = true) || tagName.endsWith(":$localName", ignoreCase = true)) {
                    list.add(node)
                }
            }
        }
        return list
    }

    private fun getElementsByLocalName(doc: org.w3c.dom.Document, localName: String): List<Element> {
        val list = mutableListOf<Element>()
        val nodes = doc.getElementsByTagName("*")
        for (i in 0 until nodes.length) {
            val node = nodes.item(i)
            if (node is Element) {
                val tagName = node.tagName ?: ""
                if (tagName.equals(localName, ignoreCase = true) || tagName.endsWith(":$localName", ignoreCase = true)) {
                    list.add(node)
                }
            }
        }
        return list
    }

    private fun parsePropfindXml(xmlString: String, currentDir: String): List<RemoteFileItem> {
        val list = mutableListOf<RemoteFileItem>()
        try {
            val factory = DocumentBuilderFactory.newInstance()
            val builder = factory.newDocumentBuilder()
            val inputSource = org.xml.sax.InputSource(StringReader(xmlString))
            val doc = builder.parse(inputSource)

            val responses = getElementsByLocalName(doc, "response")
            val length = responses.size

            val sdf = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US)

            var skipFirst = true
            val normalizedCurrent = "/" + currentDir.trim('/')

            for (i in 0 until length) {
                val element = responses[i]
                val hrefNode = getElementsByLocalName(element, "href").firstOrNull() ?: continue
                val href = decodeUrlPath(hrefNode.textContent ?: "")

                if (skipFirst) {
                    skipFirst = false
                    val decodedPath = href.removeSuffix("/")
                    val decodedCurrent = normalizedCurrent.removeSuffix("/")
                    if (decodedPath.endsWith(decodedCurrent) || (decodedCurrent.isEmpty() && decodedPath.isEmpty())) {
                        continue
                    }
                }

                val name = href.trimEnd('/').substringAfterLast('/')
                if (name.isEmpty()) continue

                val propstatNode = getElementsByLocalName(element, "propstat").firstOrNull()
                val propNode = propstatNode?.let { getElementsByLocalName(it, "prop").firstOrNull() }

                var isDir = false
                val resTypeNode = propNode?.let { getElementsByLocalName(it, "resourcetype").firstOrNull() }
                if (resTypeNode != null) {
                    val collectionNode = getElementsByLocalName(resTypeNode, "collection").firstOrNull()
                    if (collectionNode != null) {
                        isDir = true
                    }
                }

                var size = 0L
                val sizeNode = propNode?.let { getElementsByLocalName(it, "getcontentlength").firstOrNull() }
                if (sizeNode != null) {
                    size = sizeNode.textContent.toLongOrNull() ?: 0L
                }

                var modified = Date()
                val dateNode = propNode?.let { getElementsByLocalName(it, "getlastmodified").firstOrNull() }
                if (dateNode != null) {
                    try {
                        modified = sdf.parse(dateNode.textContent) ?: Date()
                    } catch (_: Exception) {}
                }

                val fullPath = RemotePaths.join(currentDir, name)

                list.add(
                    RemoteFileItem(
                        name = name,
                        path = fullPath,
                        isDirectory = isDir,
                        size = size,
                        modified = modified
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }
}
