package com.raival.compose.file.explorer.ebook.odt

import com.raival.compose.file.explorer.ebook.EbookChapter
import com.raival.compose.file.explorer.ebook.EbookDocument
import com.raival.compose.file.explorer.ebook.EbookTocItem
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.Closeable
import java.io.File
import java.io.InputStream
import java.util.zip.ZipFile
import javax.xml.parsers.DocumentBuilderFactory

class OdtBook(
    override val file: File,
    override val title: String,
    override val author: String?,
    override val coverBytes: ByteArray?,
    override val chapters: List<EbookChapter>,
    override val toc: List<EbookTocItem>
) : EbookDocument {

    private val zip = ZipFile(file)

    override fun getChapterHtml(chapter: EbookChapter): String {
        return chapter.htmlContent ?: ""
    }

    override fun getEntryStream(relativePath: String): InputStream? {
        val clean = relativePath.trimStart('/')
        val entry = zip.getEntry(clean) ?: zip.getEntry("Pictures/" + clean.substringAfterLast('/'))
        return if (entry != null) zip.getInputStream(entry) else null
    }

    override fun close() {
        try {
            zip.close()
        } catch (_: Exception) {}
    }
}

object OdtParser {

    fun parse(file: File): OdtBook {
        ZipFile(file).use { zip ->
            // 1. Metadata from meta.xml if available
            var title = file.nameWithoutExtension
            var author: String? = null

            val metaEntry = zip.getEntry("meta.xml")
            if (metaEntry != null) {
                try {
                    val metaDoc = zip.getInputStream(metaEntry).use { stream ->
                        DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(stream)
                    }
                    val titleNodes = metaDoc.getElementsByTagName("dc:title")
                    if (titleNodes.length > 0 && titleNodes.item(0).textContent.isNotBlank()) {
                        title = titleNodes.item(0).textContent.trim()
                    }
                    val creatorNodes = metaDoc.getElementsByTagName("dc:creator")
                    if (creatorNodes.length > 0 && creatorNodes.item(0).textContent.isNotBlank()) {
                        author = creatorNodes.item(0).textContent.trim()
                    }
                } catch (_: Exception) {}
            }

            // 2. Cover image from Thumbnails/thumbnail.png or Pictures/
            var coverBytes: ByteArray? = null
            val thumbEntry = zip.getEntry("Thumbnails/thumbnail.png")
            if (thumbEntry != null) {
                coverBytes = zip.getInputStream(thumbEntry).use { it.readBytes() }
            } else {
                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    val lower = entry.name.lowercase()
                    if (lower.startsWith("pictures/") && (lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg"))) {
                        coverBytes = zip.getInputStream(entry).use { it.readBytes() }
                        break
                    }
                }
            }

            // 3. Parse content.xml into HTML
            val contentEntry = zip.getEntry("content.xml")
                ?: throw IllegalArgumentException("Invalid ODT: Missing content.xml")

            val contentDoc = zip.getInputStream(contentEntry).use { stream ->
                DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(stream)
            }

            val bodyNodes = contentDoc.getElementsByTagName("office:text")
            val textBody = if (bodyNodes.length > 0) bodyNodes.item(0) else contentDoc.documentElement

            val htmlBuilder = StringBuilder()
            val chapters = mutableListOf<EbookChapter>()
            val toc = mutableListOf<EbookTocItem>()
            var currentSectionHtml = StringBuilder()
            var currentSectionTitle = title

            fun flushCurrentSection() {
                if (currentSectionHtml.isNotBlank()) {
                    val wrapped = """
                        <!DOCTYPE html>
                        <html>
                        <head>
                            <meta charset="UTF-8">
                            <meta name="viewport" content="width=device-width, initial-scale=1.0">
                            <style>
                                body { font-family: sans-serif; line-height: 1.6; padding: 16px; word-wrap: break-word; }
                                img { max-width: 100%; height: auto; display: block; margin: 12px auto; }
                                p { margin: 0 0 1em 0; }
                                table { border-collapse: collapse; width: 100%; margin: 1em 0; }
                                th, td { border: 1px solid #888; padding: 8px; text-align: left; }
                            </style>
                        </head>
                        <body>
                            $currentSectionHtml
                        </body>
                        </html>
                    """.trimIndent()
                    val chapterIdx = chapters.size
                    val id = "odt_ch_$chapterIdx"
                    chapters.add(EbookChapter(id = id, title = currentSectionTitle, htmlContent = wrapped))
                    toc.add(EbookTocItem(title = currentSectionTitle, href = id, chapterIndex = chapterIdx))
                    currentSectionHtml = StringBuilder()
                }
            }

            if (textBody != null) {
                val children = textBody.childNodes
                for (i in 0 until children.length) {
                    convertNode(children.item(i), currentSectionHtml) { level, headingText ->
                        flushCurrentSection()
                        currentSectionTitle = headingText
                    }
                }
            }

            flushCurrentSection()

            if (chapters.isEmpty()) {
                val placeholder = "<p>No readable content found in document.</p>"
                val wrapped = "<!DOCTYPE html><html><body>$placeholder</body></html>"
                chapters.add(EbookChapter(id = "odt_ch_0", title = title, htmlContent = wrapped))
                toc.add(EbookTocItem(title = title, href = "odt_ch_0", chapterIndex = 0))
            }

            return OdtBook(
                file = file,
                title = title,
                author = author,
                coverBytes = coverBytes,
                chapters = chapters,
                toc = toc
            )
        }
    }

    private fun convertNode(node: Node, sb: StringBuilder, onHeading: (Int, String) -> Unit) {
        when (node.nodeType) {
            Node.TEXT_NODE -> {
                sb.append(escapeHtml(node.textContent))
            }
            Node.ELEMENT_NODE -> {
                val element = node as Element
                val localName = element.localName ?: element.nodeName.substringAfter(':')

                when (localName) {
                    "h" -> {
                        val level = element.getAttribute("text:outline-level").toIntOrNull() ?: 1
                        val headingText = element.textContent.trim()
                        if (level <= 2 && headingText.isNotBlank()) {
                            onHeading(level, headingText)
                        }
                        val hTag = if (level in 1..6) "h$level" else "h2"
                        sb.append("<$hTag>")
                        convertChildren(node, sb, onHeading)
                        sb.append("</$hTag>\n")
                    }
                    "p" -> {
                        sb.append("<p>")
                        convertChildren(node, sb, onHeading)
                        sb.append("</p>\n")
                    }
                    "span" -> {
                        sb.append("<span>")
                        convertChildren(node, sb, onHeading)
                        sb.append("</span>")
                    }
                    "line-break" -> {
                        sb.append("<br/>\n")
                    }
                    "tab" -> {
                        sb.append("&emsp;")
                    }
                    "s" -> {
                        val count = element.getAttribute("text:c").toIntOrNull() ?: 1
                        repeat(count) { sb.append("&nbsp;") }
                    }
                    "a" -> {
                        val href = element.getAttribute("xlink:href")
                        sb.append("<a href=\"${escapeHtml(href)}\">")
                        convertChildren(node, sb, onHeading)
                        sb.append("</a>")
                    }
                    "image" -> {
                        val href = element.getAttribute("xlink:href")
                        if (href.isNotBlank()) {
                            sb.append("<img src=\"${escapeHtml(href)}\" />\n")
                        }
                    }
                    "list" -> {
                        sb.append("<ul>\n")
                        convertChildren(node, sb, onHeading)
                        sb.append("</ul>\n")
                    }
                    "list-item" -> {
                        sb.append("<li>")
                        convertChildren(node, sb, onHeading)
                        sb.append("</li>\n")
                    }
                    "table" -> {
                        sb.append("<table>\n")
                        convertChildren(node, sb, onHeading)
                        sb.append("</table>\n")
                    }
                    "table-row" -> {
                        sb.append("<tr>")
                        convertChildren(node, sb, onHeading)
                        sb.append("</tr>\n")
                    }
                    "table-cell" -> {
                        sb.append("<td>")
                        convertChildren(node, sb, onHeading)
                        sb.append("</td>")
                    }
                    else -> {
                        convertChildren(node, sb, onHeading)
                    }
                }
            }
            else -> {}
        }
    }

    private fun convertChildren(node: Node, sb: StringBuilder, onHeading: (Int, String) -> Unit) {
        val childNodes = node.childNodes
        for (i in 0 until childNodes.length) {
            convertNode(childNodes.item(i), sb, onHeading)
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
