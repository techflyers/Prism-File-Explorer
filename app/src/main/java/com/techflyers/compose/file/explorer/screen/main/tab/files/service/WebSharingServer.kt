package com.techflyers.compose.file.explorer.screen.main.tab.files.service

import android.content.Context
import android.net.Uri
import android.os.Environment
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.connection.channel.forwarded.RemotePortForwarder
import net.schmizz.sshj.connection.channel.forwarded.SocketForwardingConnectListener
import org.json.JSONArray
import org.json.JSONObject
import java.io.*
import java.net.*
import java.text.SimpleDateFormat
import java.util.*
import java.util.regex.Pattern
import kotlin.concurrent.thread

object WebSharingServer {
    private const val PORT = 8080

    private var serverSocket: ServerSocket? = null
    private var isLocalActive = false
    private var isInternetActive = false
    private var localIpAddress = "127.0.0.1"
    private var internetShareLink = ""
    private var sshClient: SSHClient? = null
    @Volatile
    private var rootDir = ""

    private val videoExtensions = setOf("mp4", "mkv", "webm", "avi", "mov", "m4v", "3gp", "ts", "flv")
    private val audioExtensions = setOf("mp3", "wav", "flac", "m4a", "aac", "ogg", "opus", "wma")
    private val imageExtensions = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "svg", "heic")
    private val subtitleExtensions = setOf("srt", "vtt", "ass", "ssa", "sub")

    fun start(context: Context, rootPath: String, onStatusChanged: () -> Unit) {
        val resolvedRoot = if (rootPath.isNotBlank() && File(rootPath).exists()) {
            rootPath
        } else {
            Environment.getExternalStorageDirectory().absolutePath
        }
        this.rootDir = resolvedRoot

        if (isLocalActive) {
            onStatusChanged()
            return
        }
        localIpAddress = getLocalIpAddress()
        isLocalActive = true

        thread(name = "WebSharingServer") {
            try {
                serverSocket = ServerSocket().apply {
                    reuseAddress = true
                    bind(InetSocketAddress(PORT))
                }
                while (isLocalActive) {
                    val socket = serverSocket?.accept() ?: break
                    socket.soTimeout = 30000
                    thread(name = "WebShareClient") {
                        handleClient(context, socket)
                    }
                }
            } catch (e: Exception) {
                if (isLocalActive) {
                    e.printStackTrace()
                }
            } finally {
                stop(onStatusChanged)
            }
        }
        onStatusChanged()
    }

    fun updateRootDir(newRootPath: String) {
        if (newRootPath.isNotBlank() && File(newRootPath).exists()) {
            this.rootDir = newRootPath
        }
    }

    fun getSharedRootDir(): String = rootDir

    fun stop(onStatusChanged: () -> Unit) {
        if (!isLocalActive) return
        isLocalActive = false
        stopInternetTunnel()
        try {
            serverSocket?.close()
        } catch (_: Exception) {}
        serverSocket = null
        onStatusChanged()
    }

    fun startInternetTunnel(context: Context, rootPath: String, onStatusChanged: () -> Unit) {
        val resolvedRoot = if (rootPath.isNotBlank() && File(rootPath).exists()) {
            rootPath
        } else {
            Environment.getExternalStorageDirectory().absolutePath
        }
        this.rootDir = resolvedRoot

        if (isInternetActive) return
        if (!isLocalActive) {
            start(context, resolvedRoot, onStatusChanged)
        }
        isInternetActive = true
        internetShareLink = "Establishing secure proxy tunnel..."
        onStatusChanged()

        thread(name = "WebShareInternetTunnel") {
            try {
                com.techflyers.compose.file.explorer.screen.main.tab.files.service.remote.SftpRemoteClient.ensureBouncyCastle()

                sshClient = SSHClient().apply {
                    addHostKeyVerifier(WebShareTunnelKeys.createTofuVerifier(context))
                    connect("localhost.run", 22)
                }
                val keys = WebShareTunnelKeys.loadKeys(sshClient!!, context)
                sshClient!!.auth(
                    "nokey",
                    net.schmizz.sshj.userauth.method.AuthNone(),
                    net.schmizz.sshj.userauth.method.AuthPublickey(keys)
                )

                sshClient!!.remotePortForwarder.bind(
                    RemotePortForwarder.Forward(80),
                    SocketForwardingConnectListener(InetSocketAddress("127.0.0.1", PORT))
                )

                val session = sshClient!!.startSession()
                WebShareTunnelKeys.requestShellNoReply(session)
                val reader = session.inputStream.bufferedReader(Charsets.UTF_8)
                var line: String? = null
                val pattern = Pattern.compile("([a-zA-Z0-9.-]+\\.(localhost\\.run|lhr\\.life))")
                while (isInternetActive && reader.readLine().also { line = it } != null) {
                    val l = line ?: break
                    val matcher = pattern.matcher(l)
                    if (matcher.find()) {
                        internetShareLink = "https://" + matcher.group(1)
                        onStatusChanged()
                        break
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                internetShareLink = "Failed: ${e.message}"
                stopInternetTunnel()
                onStatusChanged()
            }
        }
    }

    fun stopInternetTunnel() {
        if (!isInternetActive) return
        isInternetActive = false
        internetShareLink = ""
        try {
            sshClient?.disconnect()
            sshClient?.close()
        } catch (_: Exception) {}
        sshClient = null
    }

    fun isLocalActive(): Boolean = isLocalActive
    fun isInternetActive(): Boolean = isInternetActive
    fun getLocalUrl(): String = "http://$localIpAddress:$PORT"
    fun getInternetUrl(): String = internetShareLink

    private fun getLocalIpAddress(): String {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                val addresses = networkInterface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val address = addresses.nextElement()
                    if (!address.isLoopbackAddress && address is Inet4Address) {
                        return address.hostAddress ?: "127.0.0.1"
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return "127.0.0.1"
    }

    private fun handleClient(context: Context, socket: Socket) {
        val input = socket.getInputStream()
        val output = socket.getOutputStream()

        try {
            val headerBytes = readHttpHeaders(input) ?: return
            val headerText = headerBytes.toString(Charsets.UTF_8)
            val headerLines = headerText.split("\r\n")

            val requestLine = headerLines.firstOrNull()?.trim() ?: return
            val parts = requestLine.split(" ")
            if (parts.size < 2) return
            val method = parts[0].uppercase()
            val rawTarget = parts[1]
            val rawPathPart = rawTarget.substringBefore('?')
            val rawQueryPart = if ('?' in rawTarget) rawTarget.substringAfter('?') else ""
            val path = Uri.decode(rawPathPart)
            val queryParams = parseQueryParams(rawQueryPart)

            val headers = mutableMapOf<String, String>()
            for (i in 1 until headerLines.size) {
                val hLine = headerLines[i]
                val index = hLine.indexOf(':')
                if (index != -1) {
                    headers[hLine.substring(0, index).trim().lowercase()] = hLine.substring(index + 1).trim()
                }
            }

            if (method == "OPTIONS") {
                val pw = PrintWriter(output)
                pw.print("HTTP/1.1 204 No Content\r\n")
                pw.print("Access-Control-Allow-Origin: *\r\n")
                pw.print("Access-Control-Allow-Methods: GET, POST, OPTIONS, HEAD\r\n")
                pw.print("Access-Control-Allow-Headers: Range, Content-Type, X-File-Name, X-Folder-Path\r\n")
                pw.print("Access-Control-Max-Age: 86400\r\n\r\n")
                pw.flush()
                return
            }

            if (path.contains("..")) {
                sendErrorResponse(output, 403, "Forbidden")
                return
            }

            val safeRootDir = if (rootDir.isNotBlank() && File(rootDir).exists()) {
                File(rootDir)
            } else {
                Environment.getExternalStorageDirectory()
            }

            val cleanPath = path.trimStart('/')
            val isUploadEndpoint = cleanPath == "api/upload"

            val targetFile = if (cleanPath.isEmpty()) {
                safeRootDir
            } else if (isUploadEndpoint) {
                val requestedFolder = headers["x-folder-path"]?.let { Uri.decode(it).trimStart('/') } ?: ""
                if (requestedFolder.isEmpty()) safeRootDir else File(safeRootDir, requestedFolder)
            } else {
                File(safeRootDir, cleanPath)
            }

            if (!targetFile.canonicalPath.startsWith(safeRootDir.canonicalPath)) {
                sendErrorResponse(output, 403, "Forbidden")
                return
            }

            if (method == "POST") {
                val fileNameHeader = headers["x-file-name"]
                if (fileNameHeader != null) {
                    val decodedFileName = File(Uri.decode(fileNameHeader)).name
                    if (decodedFileName.isBlank() || decodedFileName == "." || decodedFileName == "..") {
                        sendErrorResponse(output, 400, "Invalid File Name")
                        return
                    }

                    val uploadDestination = if (targetFile.isDirectory) {
                        File(targetFile, decodedFileName)
                    } else {
                        File(targetFile.parentFile ?: safeRootDir, decodedFileName)
                    }

                    if (!uploadDestination.canonicalPath.startsWith(safeRootDir.canonicalPath)) {
                        sendErrorResponse(output, 403, "Forbidden")
                        return
                    }

                    val contentLength = headers["content-length"]?.toLongOrNull() ?: 0L
                    uploadDestination.outputStream().use { fos ->
                        val buffer = ByteArray(64 * 1024)
                        var totalRead = 0L
                        while (totalRead < contentLength) {
                            val toRead = minOf(buffer.size.toLong(), contentLength - totalRead).toInt()
                            val read = input.read(buffer, 0, toRead)
                            if (read == -1) break
                            fos.write(buffer, 0, read)
                            totalRead += read
                        }
                    }

                    sendStringResponse(output, 200, "OK", "Upload successful", "text/plain; charset=utf-8")
                } else {
                    sendErrorResponse(output, 400, "Bad Request: Missing X-File-Name header")
                }
                return
            }

            if (!targetFile.exists()) {
                sendErrorResponse(output, 404, "Not Found")
                return
            }

            if (targetFile.isDirectory) {
                serveDirectoryListing(context, output, if (cleanPath.isEmpty()) "/" else "/$cleanPath", targetFile)
            } else if (queryParams["format"] == "vtt" && targetFile.extension.lowercase() in subtitleExtensions) {
                serveSubtitleAsWebVtt(output, targetFile)
            } else {
                val forceDownload = queryParams["download"] == "1"
                serveFileStreaming(output, headers, targetFile, forceDownload)
            }
        } catch (_: SocketException) {
            // Socket closed or connection aborted during streaming/upload
        } catch (e: Exception) {
            e.printStackTrace()
            try {
                sendErrorResponse(output, 500, "Internal Server Error: ${e.message}")
            } catch (_: Exception) {}
        } finally {
            try { socket.close() } catch (_: Exception) {}
        }
    }

    private fun parseQueryParams(query: String): Map<String, String> {
        if (query.isBlank()) return emptyMap()
        val map = mutableMapOf<String, String>()
        query.split('&').forEach { pair ->
            if (pair.isNotBlank()) {
                val k = Uri.decode(pair.substringBefore('='))
                val v = Uri.decode(pair.substringAfter('=', ""))
                map[k] = v
            }
        }
        return map
    }

    /**
     * Converts .srt, .ass, .ssa, or .vtt subtitle files to valid WebVTT (text/vtt) on the fly
     * so HTML5 <track> elements render subtitles natively in any browser.
     */
    private fun serveSubtitleAsWebVtt(output: OutputStream, file: File) {
        val ext = file.extension.lowercase()
        val rawText = try {
            val bytes = file.readBytes()
            // Strip UTF-8 BOM if present
            if (bytes.size >= 3 &&
                bytes[0] == 0xEF.toByte() &&
                bytes[1] == 0xBB.toByte() &&
                bytes[2] == 0xBF.toByte()
            ) {
                String(bytes, 3, bytes.size - 3, Charsets.UTF_8)
            } else {
                String(bytes, Charsets.UTF_8)
            }
        } catch (_: Exception) {
            ""
        }

        val vttContent = when (ext) {
            "vtt" -> if (rawText.trimStart().startsWith("WEBVTT")) rawText else "WEBVTT\n\n$rawText"
            "ass", "ssa" -> convertAssToVtt(rawText)
            else -> convertSrtToVtt(rawText)
        }
        sendStringResponse(output, 200, "OK", vttContent, "text/vtt; charset=utf-8")
    }

    private fun convertSrtToVtt(srt: String): String {
        val normalized = srt.replace("\r\n", "\n").replace("\r", "\n")
        val timeRegex = Regex("""(\d{1,2}:\d{2}:\d{2})[,.](\d{3})\s*-->\s*(\d{1,2}:\d{2}:\d{2})[,.](\d{3})""")
        val converted = timeRegex.replace(normalized) { match ->
            val hms1 = match.groupValues[1].let { if (it.length == 7) "0$it" else it }
            val ms1 = match.groupValues[2]
            val hms2 = match.groupValues[3].let { if (it.length == 7) "0$it" else it }
            val ms2 = match.groupValues[4]
            "$hms1.$ms1 --> $hms2.$ms2"
        }
        return "WEBVTT\n\n$converted\n"
    }

    private fun convertAssToVtt(ass: String): String {
        val sb = StringBuilder("WEBVTT\n\n")
        val lines = ass.replace("\r\n", "\n").replace("\r", "\n").lines()
        val braceTagRegex = Regex("""\{[^}]*\}""")
        var cueIndex = 1
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.startsWith("Dialogue:", ignoreCase = true)) {
                val afterPrefix = trimmed.substringAfter(":").trim()
                val parts = afterPrefix.split(",", limit = 10)
                if (parts.size >= 10) {
                    val startAss = formatAssTimestampToVtt(parts[1].trim())
                    val endAss = formatAssTimestampToVtt(parts[2].trim())
                    val rawText = parts[9]
                        .replace(braceTagRegex, "")
                        .replace("\\N", "\n")
                        .replace("\\n", "\n")
                        .replace("\\h", " ")
                        .trim()
                    if (startAss != null && endAss != null && rawText.isNotEmpty()) {
                        sb.append(cueIndex++).append('\n')
                        sb.append(startAss).append(" --> ").append(endAss).append('\n')
                        sb.append(rawText).append("\n\n")
                    }
                }
            }
        }
        return sb.toString()
    }

    private fun formatAssTimestampToVtt(assTime: String): String? {
        val m = Regex("""(\d+):(\d{2}):(\d{2})\.(\d{2,3})""").matchEntire(assTime) ?: return null
        val h = m.groupValues[1].padStart(2, '0')
        val min = m.groupValues[2]
        val sec = m.groupValues[3]
        val frac = m.groupValues[4].padEnd(3, '0').take(3)
        return "$h:$min:$sec.$frac"
    }

    private fun readHttpHeaders(input: InputStream): ByteArray? {
        val buffer = ByteArrayOutputStream()
        var prev3 = 0; var prev2 = 0; var prev1 = 0
        while (true) {
            val b = input.read()
            if (b == -1) return if (buffer.size() > 0) buffer.toByteArray() else null
            buffer.write(b)
            if (prev3 == '\r'.code && prev2 == '\n'.code && prev1 == '\r'.code && b == '\n'.code) {
                val full = buffer.toByteArray()
                return full.copyOf(full.size - 4)
            }
            prev3 = prev2; prev2 = prev1; prev1 = b
        }
    }

    private fun encodeUrlPath(relPath: String): String {
        return relPath.split("/").joinToString("/") { seg ->
            if (seg.isEmpty()) "" else Uri.encode(seg)
        }
    }

    private fun serveDirectoryListing(context: Context, output: OutputStream, currentPath: String, directory: File) {
        val files = directory.listFiles() ?: emptyArray()
        Arrays.sort(files) { a, b ->
            if (a.isDirectory && !b.isDirectory) -1
            else if (!a.isDirectory && b.isDirectory) 1
            else a.name.lowercase().compareTo(b.name.lowercase())
        }

        // Discover all subtitle files in the current directory for auto-matching and selection
        val subtitleFiles = files.filter { !it.isDirectory && !it.name.startsWith(".") && it.extension.lowercase() in subtitleExtensions }
        val folderSubtitlesJson = JSONArray()
        subtitleFiles.forEach { sub ->
            val subRelUrl = encodeUrlPath(if (currentPath == "/") "/${sub.name}" else "$currentPath/${sub.name}")
            val obj = JSONObject()
            obj.put("name", sub.name)
            obj.put("baseName", sub.nameWithoutExtension)
            obj.put("ext", sub.extension.lowercase())
            obj.put("url", subRelUrl)
            obj.put("vttUrl", "$subRelUrl?format=vtt")
            folderSubtitlesJson.put(obj)
        }

        val folderSvg = """<svg class="svg-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M22 19a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h5l2 3h9a2 2 0 0 1 2 2z"></path></svg>"""
        val videoSvg = """<svg class="svg-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polygon points="23 7 16 12 23 17 23 7"></polygon><rect x="1" y="5" width="15" height="14" rx="2" ry="2"></rect></svg>"""
        val audioSvg = """<svg class="svg-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M9 18V5l12-2v13"></path><circle cx="6" cy="18" r="3"></circle><circle cx="18" cy="16" r="3"></circle></svg>"""
        val imageSvg = """<svg class="svg-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><rect x="3" y="3" width="18" height="18" rx="2" ry="2"></rect><circle cx="8.5" cy="8.5" r="1.5"></circle><polyline points="21 15 16 10 5 21"></polyline></svg>"""
        val pdfSvg = """<svg class="svg-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"></path><polyline points="14 2 14 8 20 8"></polyline><line x1="16" y1="13" x2="8" y2="13"></line><line x1="16" y1="17" x2="8" y2="17"></line></svg>"""
        val subtitleSvg = """<svg class="svg-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><rect x="2" y="4" width="20" height="16" rx="2"></rect><path d="M7 15h4M15 15h2M7 11h2M13 11h4"></path></svg>"""
        val fileSvg = """<svg class="svg-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M13 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V9z"></path><polyline points="13 2 13 9 20 9"></polyline></svg>"""
        val backSvg = """<svg class="svg-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polyline points="9 14 4 9 9 4"></polyline><path d="M20 20v-7a4 4 0 0 0-4-4H4"></path></svg>"""
        val downloadSvg = """<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"></path><polyline points="7 10 12 15 17 10"></polyline><line x1="12" y1="15" x2="12" y2="3"></line></svg>"""

        val rootFolderName = File(rootDir).name.ifEmpty { "Storage Root" }
        val title = if (currentPath == "/" || currentPath.isEmpty()) rootFolderName else currentPath.trimEnd('/').substringAfterLast('/')

        val parts = currentPath.split("/").filter { it.isNotEmpty() }
        var breadcrumbsHtml = """<a class="breadcrumb-item breadcrumb-root" href="/"><span class="home-icon">🏠</span> $rootFolderName</a>"""
        var pathAccumulator = ""
        for (part in parts) {
            pathAccumulator += "/$part"
            val encodedAcc = encodeUrlPath(pathAccumulator)
            val escapedPart = part.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
            breadcrumbsHtml += """ <span class="breadcrumb-sep">/</span> <a class="breadcrumb-item" href="$encodedAcc">$escapedPart</a>"""
        }

        var listHtml = ""
        if (currentPath != "/" && currentPath.isNotEmpty()) {
            val clean = currentPath.trimEnd('/')
            val parentPath = clean.substringBeforeLast('/', "")
            val encodedParent = if (parentPath.isEmpty()) "/" else encodeUrlPath(parentPath)
            listHtml += """
                <div class="explorer-item folder-item parent-dir" data-name=".. (Parent Directory)" data-type="directory" data-category="folder" data-size-bytes="0" data-mtime="0" data-url="$encodedParent" onclick="handleItemClick(this)">
                  <div class="item-icon-wrapper dir-icon">$backSvg</div>
                  <div class="item-details">
                    <div class="item-name">.. (Parent Directory)</div>
                    <div class="item-meta">Go up one level</div>
                  </div>
                </div>
            """.trimIndent()
        }

        val df = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)

        for (file in files) {
            val name = file.name
            if (name.startsWith(".")) continue

            val isDir = file.isDirectory
            val rawRelativeUrl = if (currentPath == "/" || currentPath.isEmpty()) "/$name" else "$currentPath/$name"
            val relativeUrl = encodeUrlPath(rawRelativeUrl)
            val escapedName = name.replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;").replace(">", "&gt;")

            var sizeStr = "-"
            var dateStr = "-"
            var iconClass = "file-icon"
            var mimeType = "application/octet-stream"
            var category = "other"
            var svgIcon = fileSvg
            var matchedSubBadge = ""
            var matchedSubtitleVttUrl = ""
            var matchedSubtitleName = ""

            if (isDir) {
                iconClass = "dir-icon"
                category = "folder"
                svgIcon = folderSvg
            } else {
                val sizeBytes = file.length()
                dateStr = df.format(Date(file.lastModified()))

                sizeStr = when {
                    sizeBytes >= 1024L * 1024L * 1024L -> "%.2f GB".format(sizeBytes / (1024.0 * 1024.0 * 1024.0))
                    sizeBytes >= 1024L * 1024L -> "%.1f MB".format(sizeBytes / (1024.0 * 1024.0))
                    sizeBytes >= 1024L -> "${sizeBytes / 1024L} KB"
                    else -> "$sizeBytes B"
                }

                val ext = file.extension.lowercase()
                when {
                    ext in videoExtensions -> {
                        iconClass = "video-icon"
                        category = "video"
                        mimeType = when (ext) {
                            "webm" -> "video/webm"
                            "mkv" -> "video/x-matroska"
                            "mov" -> "video/quicktime"
                            "avi" -> "video/x-msvideo"
                            else -> "video/mp4"
                        }
                        svgIcon = videoSvg

                        val videoBase = file.nameWithoutExtension.lowercase()
                        val matchingSub = subtitleFiles.firstOrNull { sub ->
                            val subBase = sub.nameWithoutExtension.lowercase()
                            subBase == videoBase || subBase.startsWith("$videoBase.")
                        }
                        if (matchingSub != null) {
                            val subRel = encodeUrlPath(if (currentPath == "/" || currentPath.isEmpty()) "/${matchingSub.name}" else "$currentPath/${matchingSub.name}")
                            matchedSubtitleVttUrl = "$subRel?format=vtt"
                            matchedSubtitleName = matchingSub.name.replace("\"", "&quot;")
                            matchedSubBadge = """<span class="cc-badge" title="Subtitle auto-detected: ${matchingSub.name.replace("\"", "&quot;")}">CC</span>"""
                        }
                    }
                    ext in audioExtensions -> {
                        iconClass = "audio-icon"
                        category = "audio"
                        mimeType = when (ext) {
                            "wav" -> "audio/wav"
                            "flac" -> "audio/flac"
                            "ogg", "opus" -> "audio/ogg"
                            "m4a", "aac" -> "audio/mp4"
                            else -> "audio/mpeg"
                        }
                        svgIcon = audioSvg
                    }
                    ext in imageExtensions -> {
                        iconClass = "image-icon"
                        category = "image"
                        mimeType = when (ext) {
                            "jpg", "jpeg" -> "image/jpeg"
                            "gif" -> "image/gif"
                            "webp" -> "image/webp"
                            "svg" -> "image/svg+xml"
                            else -> "image/png"
                        }
                        svgIcon = imageSvg
                    }
                    ext in subtitleExtensions -> {
                        iconClass = "subtitle-icon"
                        category = "subtitle"
                        mimeType = "text/plain"
                        svgIcon = subtitleSvg
                    }
                    ext == "pdf" -> {
                        iconClass = "pdf-icon"
                        category = "document"
                        mimeType = "application/pdf"
                        svgIcon = pdfSvg
                    }
                    else -> {
                        iconClass = "file-icon"
                        category = "document"
                        svgIcon = fileSvg
                    }
                }
            }

            if (isDir) {
                listHtml += """
                    <div class="explorer-item folder-item" data-name="$escapedName" data-type="directory" data-category="folder" data-size-bytes="0" data-mtime="${file.lastModified()}" data-url="$relativeUrl" onclick="handleItemClick(this)">
                      <div class="item-icon-wrapper dir-icon">$svgIcon</div>
                      <div class="item-details">
                        <div class="item-name" title="$escapedName">$escapedName</div>
                        <div class="item-meta">Folder</div>
                      </div>
                    </div>
                """.trimIndent()
            } else {
                listHtml += """
                    <div class="explorer-item file-item" data-name="$escapedName" data-type="file" data-category="$category" data-size-bytes="${file.length()}" data-mtime="${file.lastModified()}" data-url="$relativeUrl" data-size="$sizeStr" data-modified="$dateStr" data-mime="$mimeType" data-sub-url="$matchedSubtitleVttUrl" data-sub-name="$matchedSubtitleName" onclick="handleItemClick(this)">
                      <div class="item-icon-wrapper $iconClass">$svgIcon</div>
                      <div class="item-details">
                        <div class="item-name" title="$escapedName">$escapedName $matchedSubBadge</div>
                        <div class="item-meta">
                          <span class="item-size">$sizeStr</span>
                          <span class="item-sep">•</span>
                          <span class="item-date">$dateStr</span>
                        </div>
                      </div>
                      <div class="item-actions">
                        <button class="action-btn download-btn" onclick="downloadFile('$relativeUrl?download=1', '${name.replace("\\", "\\\\").replace("'", "\\'")}', event)" title="Download File">
                          $downloadSvg
                        </button>
                      </div>
                    </div>
                """.trimIndent()
            }
        }

        val badgeHtml = """
            <div class="header-actions">
              <div class="status-pill ${if (isInternetActive) "cloud" else "local"}" title="${if (isInternetActive) "Secure Internet Share (Active)" else "Local High-Speed Wi-Fi Share (Active)"}">
                <span class="status-indicator ${if (isInternetActive) "cloud" else "local"}"></span>
                <span class="status-label">${if (isInternetActive) "Internet Share" else "Local Wi-Fi"}</span>
              </div>
              <button class="header-upload-btn" onclick="triggerFileInput()" title="Upload Files to this Folder">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"></path><polyline points="17 8 12 3 7 8"></polyline><line x1="12" y1="3" x2="12" y2="15"></line></svg>
                <span>Upload</span>
              </button>
            </div>
            <script>window.FOLDER_SUBTITLES = ${folderSubtitlesJson.toString()};</script>
        """.trimIndent()

        val template = context.assets.open("web_share_portal.html").bufferedReader().use { it.readText() }
        val outputHtml = template
            .replace("${'$'}title", title)
            .replace("${'$'}badgeHtml", badgeHtml)
            .replace("${'$'}breadcrumbsHtml", breadcrumbsHtml)
            .replace("${'$'}listHtml", listHtml)

        sendStringResponse(output, 200, "OK", outputHtml, "text/html; charset=utf-8")
    }

    private fun serveFileStreaming(
        output: OutputStream,
        headers: Map<String, String>,
        file: File,
        forceDownload: Boolean = false
    ) {
        val ext = file.extension.lowercase()
        val contentType = when (ext) {
            "mp4", "m4v" -> "video/mp4"
            "webm" -> "video/webm"
            "mkv" -> "video/x-matroska"
            "mov" -> "video/quicktime"
            "avi" -> "video/x-msvideo"
            "mp3" -> "audio/mpeg"
            "m4a", "aac" -> "audio/mp4"
            "wav" -> "audio/wav"
            "flac" -> "audio/flac"
            "ogg", "opus" -> "audio/ogg"
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "gif" -> "image/gif"
            "webp" -> "image/webp"
            "svg" -> "image/svg+xml"
            "pdf" -> "application/pdf"
            "vtt" -> "text/vtt; charset=utf-8"
            "srt", "ass", "ssa", "sub", "txt", "md", "json", "xml", "log", "csv" -> "text/plain; charset=utf-8"
            else -> "application/octet-stream"
        }

        val fileSize = file.length()
        val rangeHeader = headers["range"]
        val encodedFilename = Uri.encode(file.name)
        val dispositionType = if (forceDownload) "attachment" else "inline"

        if (rangeHeader != null && rangeHeader.startsWith("bytes=")) {
            val range = rangeHeader.substring(6).split("-")
            var start = 0L
            var end = fileSize - 1

            try {
                if (range[0].isNotEmpty()) start = range[0].toLong()
                if (range.size > 1 && range[1].isNotEmpty()) end = range[1].toLong()
            } catch (_: Exception) {}

            if (start >= fileSize || end >= fileSize || start > end) {
                val pw = PrintWriter(output)
                pw.print("HTTP/1.1 416 Range Not Satisfiable\r\n")
                pw.print("Content-Range: bytes */$fileSize\r\n\r\n")
                pw.flush()
                return
            }

            val contentLength = end - start + 1
            val pw = PrintWriter(output)
            pw.print("HTTP/1.1 206 Partial Content\r\n")
            pw.print("Accept-Ranges: bytes\r\n")
            pw.print("Access-Control-Allow-Origin: *\r\n")
            pw.print("Content-Type: $contentType\r\n")
            pw.print("Content-Length: $contentLength\r\n")
            pw.print("Content-Range: bytes $start-$end/$fileSize\r\n")
            pw.print("Content-Disposition: $dispositionType; filename=\"$encodedFilename\"; filename*=UTF-8''$encodedFilename\r\n\r\n")
            pw.flush()

            file.inputStream().use { input ->
                var skippedTotal = 0L
                while (skippedTotal < start) {
                    val s = input.skip(start - skippedTotal)
                    if (s <= 0) break
                    skippedTotal += s
                }
                var remaining = contentLength
                val buffer = ByteArray(64 * 1024)
                while (remaining > 0) {
                    val read = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
                    if (read == -1) break
                    output.write(buffer, 0, read)
                    remaining -= read
                }
            }
            output.flush()
        } else {
            val pw = PrintWriter(output)
            pw.print("HTTP/1.1 200 OK\r\n")
            pw.print("Accept-Ranges: bytes\r\n")
            pw.print("Access-Control-Allow-Origin: *\r\n")
            pw.print("Content-Type: $contentType\r\n")
            pw.print("Content-Length: $fileSize\r\n")
            pw.print("Content-Disposition: $dispositionType; filename=\"$encodedFilename\"; filename*=UTF-8''$encodedFilename\r\n\r\n")
            pw.flush()

            file.inputStream().use { input ->
                input.copyTo(output, 64 * 1024)
            }
            output.flush()
        }
    }

    private fun sendStringResponse(output: OutputStream, code: Int, status: String, data: String, contentType: String) {
        val bytes = data.toByteArray(Charsets.UTF_8)
        val pw = PrintWriter(output)
        pw.print("HTTP/1.1 $code $status\r\n")
        pw.print("Access-Control-Allow-Origin: *\r\n")
        pw.print("Content-Type: $contentType\r\n")
        pw.print("Content-Length: ${bytes.size}\r\n\r\n")
        pw.flush()
        output.write(bytes)
        output.flush()
    }

    private fun sendErrorResponse(output: OutputStream, code: Int, message: String) {
        sendStringResponse(output, code, message, "$code $message", "text/plain; charset=utf-8")
    }
}
