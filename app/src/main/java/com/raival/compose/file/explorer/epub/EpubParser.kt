package com.raival.compose.file.explorer.epub

import com.raival.compose.file.explorer.ebook.EbookChapter
import com.raival.compose.file.explorer.ebook.EbookDocument
import com.raival.compose.file.explorer.ebook.EbookTocItem
import org.w3c.dom.Element
import java.io.File
import java.io.InputStream
import java.net.URLDecoder
import java.util.zip.ZipFile
import javax.xml.parsers.DocumentBuilderFactory

typealias EpubChapter = EbookChapter
typealias EpubTocItem = EbookTocItem

data class EpubBook(
    override val file: File,
    override val title: String,
    override val author: String?,
    val coverZipPath: String?,
    val opfDir: String,
    override val chapters: List<EbookChapter>,
    override val toc: List<EbookTocItem>
) : EbookDocument {
    private val zip = ZipFile(file)

    override val coverBytes: ByteArray?
        get() {
            if (coverZipPath == null) return null
            return getEntryStream(coverZipPath)?.use { it.readBytes() }
        }

    override fun getEntryStream(relativePath: String): InputStream? {
        val clean = relativePath.trimStart('/')
        val entry = zip.getEntry(clean) ?: return null
        return zip.getInputStream(entry)
    }

    override fun getChapterHtml(chapter: EbookChapter): String {
        val path = chapter.fullZipPath ?: chapter.id
        val stream = getEntryStream(path)
            ?: throw IllegalStateException("Cannot find chapter entry: $path")
        return stream.use { it.bufferedReader().readText() }
    }

    override fun close() {
        try {
            zip.close()
        } catch (_: Exception) {}
    }
}

object EpubParser {

    fun parse(file: File): EpubBook {
        ZipFile(file).use { zip ->
            // 1. Locate rootfile from META-INF/container.xml
            val containerEntry = zip.getEntry("META-INF/container.xml")
                ?: throw IllegalArgumentException("Invalid EPUB: Missing META-INF/container.xml")

            val containerDoc = zip.getInputStream(containerEntry).use { stream ->
                DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(stream)
            }

            val rootfiles = containerDoc.getElementsByTagName("rootfile")
            if (rootfiles.length == 0) {
                throw IllegalArgumentException("Invalid EPUB: No rootfile declared in container.xml")
            }

            val opfPath = (rootfiles.item(0) as Element).getAttribute("full-path")
            val opfEntry = zip.getEntry(opfPath)
                ?: throw IllegalArgumentException("Invalid EPUB: Missing OPF file at $opfPath")

            val opfDir = opfPath.substringBeforeLast('/', "")

            // 2. Parse OPF file
            val opfDoc = zip.getInputStream(opfEntry).use { stream ->
                DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(stream)
            }

            // Title & Author
            var bookTitle = file.nameWithoutExtension
            val titleNodes = opfDoc.getElementsByTagName("dc:title")
            if (titleNodes.length > 0 && titleNodes.item(0).textContent.isNotBlank()) {
                bookTitle = titleNodes.item(0).textContent.trim()
            }

            var bookAuthor: String? = null
            val creatorNodes = opfDoc.getElementsByTagName("dc:creator")
            if (creatorNodes.length > 0 && creatorNodes.item(0).textContent.isNotBlank()) {
                bookAuthor = creatorNodes.item(0).textContent.trim()
            }

            // Manifest (id -> href, mediaType, properties)
            data class ManifestItem(val id: String, val href: String, val mediaType: String, val properties: String)
            val manifest = mutableMapOf<String, ManifestItem>()
            val manifestNodes = opfDoc.getElementsByTagName("item")
            for (i in 0 until manifestNodes.length) {
                val node = manifestNodes.item(i) as? Element ?: continue
                val id = node.getAttribute("id")
                val rawHref = node.getAttribute("href")
                val href = try { URLDecoder.decode(rawHref, "UTF-8") } catch (_: Exception) { rawHref }
                val mediaType = node.getAttribute("media-type")
                val props = node.getAttribute("properties")
                if (id.isNotEmpty() && href.isNotEmpty()) {
                    manifest[id] = ManifestItem(id, href, mediaType, props)
                }
            }

            // Cover Image detection
            var coverZipPath: String? = null
            // Check meta name="cover"
            val metaNodes = opfDoc.getElementsByTagName("meta")
            for (i in 0 until metaNodes.length) {
                val meta = metaNodes.item(i) as? Element ?: continue
                if (meta.getAttribute("name").equals("cover", ignoreCase = true)) {
                    val coverId = meta.getAttribute("content")
                    val item = manifest[coverId]
                    if (item != null) {
                        coverZipPath = resolvePath(opfDir, item.href)
                        break
                    }
                }
            }
            // Check manifest properties="cover-image" or id="cover" / "cover-image"
            if (coverZipPath == null) {
                val coverItem = manifest.values.firstOrNull {
                    it.properties.contains("cover-image") ||
                    it.id.equals("cover", ignoreCase = true) ||
                    it.id.equals("cover-image", ignoreCase = true) ||
                    it.href.lowercase().contains("cover") && it.mediaType.startsWith("image/")
                }
                if (coverItem != null) {
                    coverZipPath = resolvePath(opfDir, coverItem.href)
                }
            }

            // 3. Spine (Linear reading order)
            val chapters = mutableListOf<EpubChapter>()
            val spineNodes = opfDoc.getElementsByTagName("itemref")
            for (i in 0 until spineNodes.length) {
                val itemref = spineNodes.item(i) as? Element ?: continue
                val idref = itemref.getAttribute("idref")
                val manifestItem = manifest[idref] ?: continue
                val fullPath = resolvePath(opfDir, manifestItem.href)
                val chapterTitle = "Chapter ${chapters.size + 1}"
                chapters.add(
                    EpubChapter(
                        id = idref,
                        title = chapterTitle,
                        href = manifestItem.href,
                        fullZipPath = fullPath
                    )
                )
            }

            // 4. Table of Contents (NCX or EPUB 3 Nav)
            val tocItems = mutableListOf<EpubTocItem>()
            val ncxItem = manifest.values.firstOrNull { it.mediaType == "application/x-dtbncx+xml" }
            if (ncxItem != null) {
                val ncxPath = resolvePath(opfDir, ncxItem.href)
                val ncxEntry = zip.getEntry(ncxPath)
                if (ncxEntry != null) {
                    try {
                        val ncxDoc = zip.getInputStream(ncxEntry).use { stream ->
                            DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(stream)
                        }
                        val navPoints = ncxDoc.getElementsByTagName("navPoint")
                        for (i in 0 until navPoints.length) {
                            val nav = navPoints.item(i) as? Element ?: continue
                            val textNodes = nav.getElementsByTagName("text")
                            val label = if (textNodes.length > 0) textNodes.item(0).textContent.trim() else "Chapter ${i + 1}"
                            val contentNodes = nav.getElementsByTagName("content")
                            val src = if (contentNodes.length > 0) (contentNodes.item(0) as Element).getAttribute("src") else ""
                            val cleanSrc = src.substringBefore('#')
                            val chapterIdx = chapters.indexOfFirst {
                                val chHref = it.href ?: ""
                                chHref == cleanSrc || chHref.endsWith(cleanSrc) || cleanSrc.endsWith(chHref)
                            }.coerceAtLeast(0)
                            tocItems.add(EpubTocItem(title = label, href = src, chapterIndex = chapterIdx))
                        }
                    } catch (_: Exception) {}
                }
            }

            // Fallback: If no TOC, use the chapters list directly
            val finalToc = if (tocItems.isNotEmpty()) tocItems else {
                chapters.mapIndexed { idx, ch ->
                    EpubTocItem(title = ch.title, href = ch.href ?: ch.id, chapterIndex = idx)
                }
            }

            return EpubBook(
                file = file,
                title = bookTitle,
                author = bookAuthor,
                coverZipPath = coverZipPath,
                opfDir = opfDir,
                chapters = chapters,
                toc = finalToc
            )
        }
    }

    fun resolvePath(baseDir: String, relativeHref: String): String {
        val cleanHref = relativeHref.substringBefore('#')
        if (baseDir.isEmpty()) return cleanHref.trimStart('/')
        val combined = "$baseDir/$cleanHref"
        val parts = combined.split('/')
        val stack = mutableListOf<String>()
        for (part in parts) {
            when (part) {
                "", "." -> continue
                ".." -> if (stack.isNotEmpty()) stack.removeAt(stack.size - 1)
                else -> stack.add(part)
            }
        }
        return stack.joinToString("/")
    }
}
