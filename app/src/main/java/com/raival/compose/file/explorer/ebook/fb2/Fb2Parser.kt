package com.raival.compose.file.explorer.ebook.fb2

import android.util.Base64
import com.raival.compose.file.explorer.ebook.EbookChapter
import com.raival.compose.file.explorer.ebook.EbookDocument
import com.raival.compose.file.explorer.ebook.EbookTocItem
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.nio.charset.Charset
import java.util.Locale
import java.util.zip.ZipFile
import javax.xml.parsers.DocumentBuilderFactory

class Fb2Book(
    override val file: File,
    override val title: String,
    override val author: String?,
    override val coverBytes: ByteArray?,
    override val chapters: List<EbookChapter>,
    override val toc: List<EbookTocItem>,
    private val binaries: Map<String, ByteArray> = emptyMap()
) : EbookDocument {

    override fun getChapterHtml(chapter: EbookChapter): String {
        return chapter.htmlContent ?: ""
    }

    override fun getEntryStream(relativePath: String): InputStream? {
        val clean = relativePath.substringAfterLast('#').substringAfterLast('/')
        val bytes = binaries[clean] ?: return null
        return ByteArrayInputStream(bytes)
    }

    override fun close() {
    }
}

object Fb2Parser {

    fun parse(file: File): Fb2Book {
        val xmlInputStream = if (file.name.lowercase(Locale.ROOT).endsWith(".zip")) {
            val zip = ZipFile(file)
            val fb2Entry = zip.entries().asSequence().firstOrNull { it.name.lowercase(Locale.ROOT).endsWith(".fb2") }
                ?: throw IllegalArgumentException("No .fb2 found inside zip")
            zip.getInputStream(fb2Entry)
        } else {
            file.inputStream()
        }

        val rawBytes = xmlInputStream.use { it.readBytes() }

        // Detect encoding from XML declaration (e.g. windows-1251, utf-8)
        val headerSample = String(rawBytes.copyOfRange(0, minOf(200, rawBytes.size)), Charsets.US_ASCII).lowercase(Locale.ROOT)
        val charset = when {
            headerSample.contains("windows-1251") -> Charset.forName("windows-1251")
            headerSample.contains("windows-1252") -> Charset.forName("windows-1252")
            headerSample.contains("koi8-r") -> Charset.forName("KOI8-R")
            else -> Charsets.UTF_8
        }

        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(ByteArrayInputStream(rawBytes))

        // 1. Binaries (Base64 decoded images)
        val binaries = mutableMapOf<String, ByteArray>()
        val binaryNodes = doc.getElementsByTagName("binary")
        for (i in 0 until binaryNodes.length) {
            val binEl = binaryNodes.item(i) as? Element ?: continue
            val id = binEl.getAttribute("id").trim()
            val text = binEl.textContent.trim().replace("\n", "").replace("\r", "").replace(" ", "")
            if (id.isNotEmpty() && text.isNotEmpty()) {
                try {
                    val decoded = try {
                        java.util.Base64.getDecoder().decode(text)
                    } catch (_: Throwable) {
                        android.util.Base64.decode(text, android.util.Base64.DEFAULT)
                    }
                    binaries[id] = decoded
                } catch (_: Exception) {}
            }
        }

        // 2. Metadata (title, author, cover)
        var bookTitle = file.nameWithoutExtension.removeSuffix(".fb2")
        var author: String? = null
        var coverBytes: ByteArray? = null

        val bookTitleNodes = doc.getElementsByTagName("book-title")
        if (bookTitleNodes.length > 0 && bookTitleNodes.item(0).textContent.isNotBlank()) {
            bookTitle = bookTitleNodes.item(0).textContent.trim()
        }

        val authorNodes = doc.getElementsByTagName("author")
        if (authorNodes.length > 0) {
            val authorEl = authorNodes.item(0) as? Element
            if (authorEl != null) {
                val first = authorEl.getElementsByTagName("first-name")
                val last = authorEl.getElementsByTagName("last-name")
                val fn = if (first.length > 0) first.item(0).textContent.trim() else ""
                val ln = if (last.length > 0) last.item(0).textContent.trim() else ""
                val full = "$fn $ln".trim()
                if (full.isNotEmpty()) author = full
            }
        }

        val coverNodes = doc.getElementsByTagName("coverpage")
        if (coverNodes.length > 0) {
            val coverEl = coverNodes.item(0) as? Element
            val imgNodes = coverEl?.getElementsByTagName("image")
            if (imgNodes != null && imgNodes.length > 0) {
                val imgEl = imgNodes.item(0) as? Element
                val href = (imgEl?.getAttribute("l:href") ?: imgEl?.getAttribute("xlink:href") ?: "").removePrefix("#")
                if (href.isNotEmpty()) {
                    coverBytes = binaries[href]
                }
            }
        }
        if (coverBytes == null && binaries.isNotEmpty()) {
            val coverKey = binaries.keys.firstOrNull { it.lowercase(Locale.ROOT).contains("cover") } ?: binaries.keys.first()
            coverBytes = binaries[coverKey]
        }

        // 3. Sections / Body to HTML chapters
        val chapters = mutableListOf<EbookChapter>()
        val toc = mutableListOf<EbookTocItem>()

        val bodyNodes = doc.getElementsByTagName("body")
        for (b in 0 until bodyNodes.length) {
            val bodyEl = bodyNodes.item(b) as? Element ?: continue
            val bodyName = bodyEl.getAttribute("name")
            // Skip notes bodies from main flow if marked as notes
            if (bodyName.equals("notes", ignoreCase = true) && chapters.isNotEmpty()) {
                // Add as notes chapter
                val notesHtml = convertElementToHtml(bodyEl, binaries)
                val idx = chapters.size
                val id = "fb2_notes"
                chapters.add(EbookChapter(id = id, title = "Notes", htmlContent = wrapHtml(notesHtml)))
                toc.add(EbookTocItem(title = "Notes", href = id, chapterIndex = idx))
                continue
            }

            // Top-level sections in this body
            val sectionNodes = mutableListOf<Element>()
            val bodyChildren = bodyEl.childNodes
            for (c in 0 until bodyChildren.length) {
                val child = bodyChildren.item(c)
                if (child is Element && child.nodeName == "section") {
                    sectionNodes.add(child)
                }
            }

            if (sectionNodes.isNotEmpty()) {
                for ((secIdx, secEl) in sectionNodes.withIndex()) {
                    val secTitleNodes = secEl.getElementsByTagName("title")
                    var secTitle = "Chapter ${chapters.size + 1}"
                    if (secTitleNodes.length > 0) {
                        val t = secTitleNodes.item(0).textContent.trim().replace(Regex("\\s+"), " ")
                        if (t.isNotBlank()) secTitle = t
                    }

                    val secHtml = convertElementToHtml(secEl, binaries)
                    val idx = chapters.size
                    val id = "fb2_ch_$idx"
                    chapters.add(EbookChapter(id = id, title = secTitle, htmlContent = wrapHtml(secHtml)))
                    toc.add(EbookTocItem(title = secTitle, href = id, chapterIndex = idx))
                }
            } else {
                // Single body without sections
                val fullBodyHtml = convertElementToHtml(bodyEl, binaries)
                val idx = chapters.size
                val id = "fb2_ch_$idx"
                chapters.add(EbookChapter(id = id, title = bookTitle, htmlContent = wrapHtml(fullBodyHtml)))
                toc.add(EbookTocItem(title = bookTitle, href = id, chapterIndex = idx))
            }
        }

        if (chapters.isEmpty()) {
            val placeholder = "<p>No content in FB2 document.</p>"
            chapters.add(EbookChapter(id = "fb2_ch_0", title = bookTitle, htmlContent = wrapHtml(placeholder)))
            toc.add(EbookTocItem(title = bookTitle, href = "fb2_ch_0", chapterIndex = 0))
        }

        return Fb2Book(
            file = file,
            title = bookTitle,
            author = author,
            coverBytes = coverBytes,
            chapters = chapters,
            toc = toc,
            binaries = binaries
        )
    }

    private fun wrapHtml(bodyContent: String): String {
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <style>
                    body { font-family: sans-serif; line-height: 1.6; padding: 16px; word-wrap: break-word; }
                    img { max-width: 100%; height: auto; display: block; margin: 12px auto; }
                    p { margin: 0 0 1em 0; text-indent: 1.5em; }
                    h1, h2, h3 { text-align: center; margin-top: 1.5em; margin-bottom: 0.8em; }
                    blockquote { margin: 1em 2em; font-style: italic; }
                    .poem { margin: 1em 2em; }
                    .verse { margin: 0.2em 0; }
                </style>
            </head>
            <body>
                $bodyContent
            </body>
            </html>
        """.trimIndent()
    }

    private fun convertElementToHtml(element: Element, binaries: Map<String, ByteArray>): String {
        val sb = StringBuilder()
        walkChildren(element, sb, binaries)
        return sb.toString()
    }

    private fun walkNode(node: Node, sb: StringBuilder, binaries: Map<String, ByteArray>) {
        when (node.nodeType) {
            Node.TEXT_NODE -> {
                sb.append(escapeHtml(node.textContent))
            }
            Node.ELEMENT_NODE -> {
                val el = node as Element
                when (el.nodeName) {
                    "title" -> {
                        sb.append("<h2>")
                        walkChildren(el, sb, binaries)
                        sb.append("</h2>\n")
                    }
                    "subtitle" -> {
                        sb.append("<h3>")
                        walkChildren(el, sb, binaries)
                        sb.append("</h3>\n")
                    }
                    "p" -> {
                        sb.append("<p>")
                        walkChildren(el, sb, binaries)
                        sb.append("</p>\n")
                    }
                    "strong" -> {
                        sb.append("<b>")
                        walkChildren(el, sb, binaries)
                        sb.append("</b>")
                    }
                    "emphasis" -> {
                        sb.append("<i>")
                        walkChildren(el, sb, binaries)
                        sb.append("</i>")
                    }
                    "strikethrough" -> {
                        sb.append("<s>")
                        walkChildren(el, sb, binaries)
                        sb.append("</s>")
                    }
                    "sub" -> {
                        sb.append("<sub>")
                        walkChildren(el, sb, binaries)
                        sb.append("</sub>")
                    }
                    "sup" -> {
                        sb.append("<sup>")
                        walkChildren(el, sb, binaries)
                        sb.append("</sup>")
                    }
                    "empty-line" -> {
                        sb.append("<br/>\n")
                    }
                    "image" -> {
                        val href = (el.getAttribute("l:href").ifEmpty { el.getAttribute("xlink:href") }).removePrefix("#")
                        val bin = binaries[href]
                        if (bin != null) {
                            val base64 = try {
                                java.util.Base64.getEncoder().encodeToString(bin)
                            } catch (_: Throwable) {
                                android.util.Base64.encodeToString(bin, android.util.Base64.NO_WRAP)
                            }
                            sb.append("<img src=\"data:image/jpeg;base64,$base64\" />\n")
                        }
                    }
                    "cite" -> {
                        sb.append("<blockquote>\n")
                        walkChildren(el, sb, binaries)
                        sb.append("</blockquote>\n")
                    }
                    "poem" -> {
                        sb.append("<div class=\"poem\">\n")
                        walkChildren(el, sb, binaries)
                        sb.append("</div>\n")
                    }
                    "stanza" -> {
                        sb.append("<div class=\"stanza\">\n")
                        walkChildren(el, sb, binaries)
                        sb.append("</div>\n")
                    }
                    "v" -> {
                        sb.append("<div class=\"verse\">")
                        walkChildren(el, sb, binaries)
                        sb.append("</div>\n")
                    }
                    "a" -> {
                        val href = el.getAttribute("l:href").ifEmpty { el.getAttribute("xlink:href") }
                        sb.append("<a href=\"${escapeHtml(href)}\">")
                        walkChildren(el, sb, binaries)
                        sb.append("</a>")
                    }
                    else -> {
                        walkChildren(el, sb, binaries)
                    }
                }
            }
        }
    }

    private fun walkChildren(node: Node, sb: StringBuilder, binaries: Map<String, ByteArray>) {
        val children = node.childNodes
        for (i in 0 until children.length) {
            walkNode(children.item(i), sb, binaries)
        }
    }

    private fun escapeHtml(text: String): String {
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;")
    }
}
