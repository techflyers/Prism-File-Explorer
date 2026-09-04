package com.raival.compose.file.explorer.ebook.rtf

import android.util.Base64
import com.raival.compose.file.explorer.ebook.EbookChapter
import com.raival.compose.file.explorer.ebook.EbookDocument
import com.raival.compose.file.explorer.ebook.EbookTocItem
import com.rtfparserkit.converter.text.AbstractTextConverter
import com.rtfparserkit.parser.IRtfParser
import com.rtfparserkit.parser.IRtfSource
import com.rtfparserkit.parser.RtfStreamSource
import com.rtfparserkit.parser.standard.StandardRtfParser
import com.rtfparserkit.rtf.Command
import com.rtfparserkit.utils.HexUtils
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.util.regex.Pattern

class RtfBook(
    override val file: File,
    override val title: String,
    override val author: String? = null,
    override val coverBytes: ByteArray?,
    override val chapters: List<EbookChapter>,
    override val toc: List<EbookTocItem>
) : EbookDocument {

    override fun getChapterHtml(chapter: EbookChapter): String {
        return chapter.htmlContent ?: ""
    }

    override fun getEntryStream(relativePath: String): InputStream? {
        return null
    }

    override fun close() {
    }
}

object RtfParser {

    fun parse(file: File): RtfBook {
        val bookTitle = file.nameWithoutExtension
        val htmlBuilder = StringBuilder()
        var firstImageBytes: ByteArray? = null

        htmlBuilder.append("<!DOCTYPE html>\n<html><head><meta charset=\"UTF-8\">")
        htmlBuilder.append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">")
        htmlBuilder.append("<style>")
        htmlBuilder.append("body { font-family: sans-serif; line-height: 1.6; padding: 16px; word-wrap: break-word; }")
        htmlBuilder.append("img { max-width: 100%; height: auto; display: block; margin: 12px auto; }")
        htmlBuilder.append("p { margin: 0 0 1em 0; }")
        htmlBuilder.append("</style></head><body>\n")

        val parser: IRtfParser = object : StandardRtfParser() {
            override fun processCommand(command: Command, parameter: Int, hasParameter: Boolean, optional: Boolean) {
                try {
                    super.processCommand(command, parameter, hasParameter, optional)
                } catch (_: Exception) {}
            }
        }

        FileInputStream(file).use { isStream ->
            val source: IRtfSource = RtfStreamSource(isStream)
            val currentTags = mutableSetOf<Command>()
            val tagStack = ArrayDeque<Set<Command>>()
            var isImage = false
            var imageFormat = "jpeg"
            var inParagraph = false

            parser.parse(source, object : AbstractTextConverter() {

                override fun processGroupStart() {
                    super.processGroupStart()
                    tagStack.addLast(HashSet(currentTags))
                }

                override fun processGroupEnd() {
                    super.processGroupEnd()
                    if (tagStack.isNotEmpty()) {
                        currentTags.clear()
                        currentTags.addAll(tagStack.removeLast())
                    }
                }

                override fun processExtractedText(text: String) {
                    if (text.isEmpty() || text == "\n") return

                    if (!inParagraph) {
                        htmlBuilder.append("<p>")
                        inParagraph = true
                    }

                    if (currentTags.contains(Command.sub)) htmlBuilder.append("<sub>")
                    if (currentTags.contains(Command.supercmd)) htmlBuilder.append("<sup>")
                    if (currentTags.contains(Command.b)) htmlBuilder.append("<b>")
                    if (currentTags.contains(Command.i)) htmlBuilder.append("<i>")
                    if (currentTags.contains(Command.ul)) htmlBuilder.append("<u>")
                    if (currentTags.contains(Command.strike)) htmlBuilder.append("<s>")

                    htmlBuilder.append(escapeHtml(text))

                    if (currentTags.contains(Command.strike)) htmlBuilder.append("</s>")
                    if (currentTags.contains(Command.ul)) htmlBuilder.append("</u>")
                    if (currentTags.contains(Command.i)) htmlBuilder.append("</i>")
                    if (currentTags.contains(Command.b)) htmlBuilder.append("</b>")
                    if (currentTags.contains(Command.supercmd)) htmlBuilder.append("</sup>")
                    if (currentTags.contains(Command.sub)) htmlBuilder.append("</sub>")
                }

                override fun processString(string: String) {
                    super.processString(string)
                    if (isImage) {
                        try {
                            isImage = false
                            val imgBytes = HexUtils.parseHexString(string)
                            if (imgBytes != null && imgBytes.isNotEmpty()) {
                                if (firstImageBytes == null) {
                                    firstImageBytes = imgBytes
                                }
                                val mimeType = if (imageFormat == "png") "image/png" else "image/jpeg"
                                val base64 = try {
                                    java.util.Base64.getEncoder().encodeToString(imgBytes)
                                } catch (_: Throwable) {
                                    android.util.Base64.encodeToString(imgBytes, android.util.Base64.NO_WRAP)
                                }
                                htmlBuilder.append("<img src=\"data:$mimeType;base64,$base64\" />\n")
                            }
                        } catch (_: Exception) {}
                    }
                }

                override fun processCommand(command: Command, parameter: Int, hasParameter: Boolean, optional: Boolean) {
                    try {
                        super.processCommand(command, parameter, hasParameter, optional)
                    } catch (_: Exception) {}

                    when (command) {
                        Command.par -> {
                            if (inParagraph) {
                                htmlBuilder.append("</p>\n")
                                inParagraph = false
                            } else {
                                htmlBuilder.append("<p></p>\n")
                            }
                        }
                        Command.line -> {
                            htmlBuilder.append("<br/>\n")
                        }
                        Command.page -> {
                            if (inParagraph) {
                                htmlBuilder.append("</p>\n")
                                inParagraph = false
                            }
                            htmlBuilder.append("<hr class=\"page-break\" />\n")
                        }
                        Command.b, Command.i, Command.ul, Command.strike, Command.sub, Command.supercmd -> {
                            if (hasParameter && parameter == 0) {
                                currentTags.remove(command)
                            } else {
                                currentTags.add(command)
                            }
                        }
                        Command.ulnone -> {
                            currentTags.remove(Command.ul)
                        }
                        Command.nosupersub -> {
                            currentTags.remove(Command.sub)
                            currentTags.remove(Command.supercmd)
                        }
                        Command.plain -> {
                            currentTags.clear()
                        }
                        Command.pngblip -> {
                            isImage = true
                            imageFormat = "png"
                        }
                        Command.jpegblip -> {
                            isImage = true
                            imageFormat = "jpeg"
                        }
                        else -> {}
                    }
                }
            })

            if (inParagraph) {
                htmlBuilder.append("</p>\n")
            }
        }

        htmlBuilder.append("</body></html>")

        val fullHtml = htmlBuilder.toString()
        val (chapters, toc) = splitHtmlIntoChapters(fullHtml, bookTitle)

        return RtfBook(
            file = file,
            title = bookTitle,
            coverBytes = firstImageBytes,
            chapters = chapters,
            toc = toc
        )
    }

    private fun escapeHtml(text: String): String {
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;")
    }

    private fun splitHtmlIntoChapters(html: String, bookTitle: String): Pair<List<EbookChapter>, List<EbookTocItem>> {
        val pageBreakRegex = Regex("""(?i)<hr class=["']page-break["']\s*/?>""")
        val rawParts = html.split(pageBreakRegex)

        val parts = mutableListOf<String>()
        if (rawParts.size > 1) {
            for (p in rawParts) {
                if (p.isNotBlank()) parts.add(p)
            }
        }

        if (parts.isEmpty()) {
            parts.add(html)
        }

        val chapters = mutableListOf<EbookChapter>()
        val toc = mutableListOf<EbookTocItem>()

        for ((idx, content) in parts.withIndex()) {
            val chapterTitle = if (parts.size == 1) bookTitle else "Section ${idx + 1}"
            val wrapped = if (!content.contains("<html", ignoreCase = true)) {
                """
                <!DOCTYPE html>
                <html>
                <head>
                    <meta charset="UTF-8">
                    <meta name="viewport" content="width=device-width, initial-scale=1.0">
                    <style>
                        body { font-family: sans-serif; line-height: 1.6; padding: 16px; word-wrap: break-word; }
                        img { max-width: 100%; height: auto; }
                    </style>
                </head>
                <body>
                    $content
                </body>
                </html>
                """.trimIndent()
            } else {
                content
            }

            val chapter = EbookChapter(
                id = "rtf_ch_$idx",
                title = chapterTitle,
                htmlContent = wrapped
            )
            chapters.add(chapter)
            toc.add(EbookTocItem(title = chapterTitle, href = "rtf_ch_$idx", chapterIndex = idx))
        }

        return Pair(chapters, toc)
    }
}
