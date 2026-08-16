package com.raival.compose.file.explorer.screen.viewer.html

import java.io.File
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URLConnection
import java.net.URLDecoder
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

/**
 * Loopback HTTP server used to preview HTML the way a desktop editor (Xed) would:
 * relative assets, scripts, and linked files resolve against the source folder.
 */
object LocalHtmlPreviewServer {
    private val serverRef = AtomicReference<ServerSocket?>(null)
    @Volatile
    private var rootDir: File? = null

    fun start(directory: File): String {
        stop()
        rootDir = directory
        val server = ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"))
        serverRef.set(server)
        thread(name = "prism-html-preview", isDaemon = true) {
            while (!server.isClosed) {
                try {
                    val client = server.accept()
                    thread(isDaemon = true) { handle(client) }
                } catch (_: Exception) {
                    break
                }
            }
        }
        return "http://127.0.0.1:${server.localPort}/"
    }

    fun stop() {
        try {
            serverRef.getAndSet(null)?.close()
        } catch (_: Exception) {
        }
        rootDir = null
    }

    private fun handle(socket: Socket) {
        socket.use { client ->
            val input = client.getInputStream().bufferedReader()
            val requestLine = input.readLine() ?: return
            val pathToken = requestLine.split(" ").getOrNull(1) ?: "/"
            val decoded = URLDecoder.decode(pathToken.substringBefore('?'), Charsets.UTF_8.name())
            val relative = decoded.trimStart('/').ifEmpty { indexName() }
            val root = rootDir ?: return
            val file = File(root, relative).canonicalFile
            if (!file.absolutePath.startsWith(root.canonicalFile.absolutePath) || !file.isFile) {
                writeResponse(client, 404, "text/plain", "Not found".toByteArray())
                return
            }
            val mime = URLConnection.guessContentTypeFromName(file.name) ?: "application/octet-stream"
            writeResponse(client, 200, mime, file.readBytes())
        }
    }

    private fun indexName(): String {
        val root = rootDir ?: return "index.html"
        return when {
            File(root, "index.html").isFile -> "index.html"
            File(root, "index.htm").isFile -> "index.htm"
            else -> root.listFiles()?.firstOrNull { it.extension.equals("html", true) }?.name
                ?: "index.html"
        }
    }

    private fun writeResponse(socket: Socket, code: Int, mime: String, body: ByteArray) {
        val status = if (code == 200) "OK" else "Error"
        val header = buildString {
            append("HTTP/1.1 $code $status\r\n")
            append("Content-Type: $mime\r\n")
            append("Content-Length: ${body.size}\r\n")
            append("Connection: close\r\n\r\n")
        }
        socket.getOutputStream().use { out ->
            out.write(header.toByteArray())
            out.write(body)
            out.flush()
        }
    }
}
