package com.raival.compose.file.explorer.screen.main.tab.files.service.remote

import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.w3c.dom.Element
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.StringReader
import java.net.URLDecoder
import java.text.SimpleDateFormat
import java.util.*
import javax.xml.parsers.DocumentBuilderFactory

class WebDavRemoteClient(
    private val conn: NetworkConnectionModel
) : RemoteClient {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    private val baseUrl: String
        get() {
            var hostStr = conn.host.trim()
            if (hostStr.startsWith("http://")) {
                hostStr = hostStr.substring(7)
            } else if (hostStr.startsWith("https://")) {
                hostStr = hostStr.substring(8)
            }
            if (hostStr.contains("/")) {
                hostStr = hostStr.substringBefore("/")
            }
            return "${conn.protocol}://$hostStr:${conn.port}"
        }

    private fun authHeader(): String? {
        if (conn.username.isEmpty() && conn.password.isEmpty()) return null
        return Credentials.basic(conn.username, conn.password)
    }

    private fun getFullUrl(path: String): String {
        var cleanPath = path
        if (!cleanPath.startsWith("/")) {
            cleanPath = "/$cleanPath"
        }
        return baseUrl + cleanPath
    }

    override fun connect() {
        var cleanRoot = conn.rootPath
        if (!cleanRoot.startsWith("/")) cleanRoot = "/$cleanRoot"
        if (!cleanRoot.endsWith("/")) cleanRoot = "$cleanRoot/"

        val url = baseUrl + cleanRoot
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
        // No-op for HTTP client
    }

    override fun listDirectory(path: String): List<RemoteFileItem> {
        val url = getFullUrl(path)
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
            return parsePropfindXml(body, path)
        }
    }

    override fun createDirectory(path: String) {
        val url = getFullUrl(path)
        val builder = Request.Builder()
            .url(url)
            .method("MKCOL", "".toRequestBody("text/xml".toMediaType()))

        authHeader()?.let { builder.addHeader("Authorization", it) }

        client.newCall(builder.build()).execute().use { response ->
            if (!response.isSuccessful) {
                throw Exception("WebDAV MKCOL failed: ${response.code} ${response.message}")
            }
        }
    }

    override fun delete(path: String, isDir: Boolean) {
        val url = getFullUrl(path)
        val builder = Request.Builder()
            .url(url)
            .delete()

        authHeader()?.let { builder.addHeader("Authorization", it) }

        client.newCall(builder.build()).execute().use { response ->
            if (!response.isSuccessful) {
                throw Exception("WebDAV DELETE failed: ${response.code} ${response.message}")
            }
        }
    }

    override fun downloadFile(remotePath: String, localPath: String, onProgress: (Double) -> Unit) {
        val url = getFullUrl(remotePath)
        val builder = Request.Builder()
            .url(url)
            .get()

        authHeader()?.let { builder.addHeader("Authorization", it) }

        client.newCall(builder.build()).execute().use { response ->
            if (!response.isSuccessful) {
                throw Exception("WebDAV GET failed: ${response.code} ${response.message}")
            }
            val body = response.body ?: throw Exception("Response body is null")
            val localFile = File(localPath)
            localFile.parentFile?.mkdirs()

            body.byteStream().use { input ->
                FileOutputStream(localFile).use { output ->
                    input.copyTo(output)
                }
            }
            onProgress(1.0)
        }
    }

    override fun uploadFile(localPath: String, remotePath: String, onProgress: (Double) -> Unit) {
        val url = getFullUrl(remotePath)
        val localFile = File(localPath)
        if (!localFile.exists()) throw FileNotFoundException("Local file not found")

        val requestBody = localFile.asRequestBody("application/octet-stream".toMediaType())
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

            // The first element is usually the directory itself, we skip it
            var skipFirst = true

            for (i in 0 until length) {
                val element = responses[i]
                val hrefNode = getElementsByLocalName(element, "href").firstOrNull() ?: continue
                var href = hrefNode.textContent
                href = URLDecoder.decode(href, "UTF-8")

                if (skipFirst) {
                    skipFirst = false
                    // Verify if this is indeed the current dir
                    val decodedPath = URLDecoder.decode(href, "UTF-8").removeSuffix("/")
                    val decodedCurrent = currentDir.removeSuffix("/")
                    if (decodedPath.endsWith(decodedCurrent)) {
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

                val fullPath = if (currentDir.endsWith("/")) "$currentDir$name" else "$currentDir/$name"

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
