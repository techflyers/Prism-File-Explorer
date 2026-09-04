package com.raival.compose.file.explorer.epub

import android.webkit.WebResourceResponse
import com.raival.compose.file.explorer.ebook.EbookChapter
import com.raival.compose.file.explorer.ebook.EbookDocument
import java.io.InputStream

enum class EpubTheme(val id: String, val bgHex: String, val textHex: String, val linkHex: String) {
    LIGHT("light", "#FFFFFF", "#1C1B1F", "#1976D2"),
    DARK("dark", "#1E1E24", "#E6E1E5", "#90CAF9"),
    AMOLED("amoled", "#000000", "#D8D8D8", "#80CBC4"),
    SEPIA("sepia", "#F8F1E3", "#4F321C", "#8D4E12")
}

object EpubAssetStreamer {

    val MIME_MAP = mapOf(
        "jpg" to "image/jpeg",
        "jpeg" to "image/jpeg",
        "png" to "image/png",
        "webp" to "image/webp",
        "gif" to "image/gif",
        "svg" to "image/svg+xml",
        "css" to "text/css",
        "js" to "application/javascript",
        "woff" to "font/woff",
        "woff2" to "font/woff2",
        "ttf" to "font/ttf",
        "otf" to "font/otf",
        "xhtml" to "application/xhtml+xml",
        "html" to "text/html"
    )

    fun intercept(book: EbookDocument, currentChapter: EbookChapter, url: String): WebResourceResponse? {
        val cleanUrl = url.substringBefore('?').substringBefore('#')
        val relativePath = try {
            val path = java.net.URI(cleanUrl).path ?: cleanUrl
            path.trimStart('/')
        } catch (_: Exception) {
            cleanUrl.substringAfterLast("://", cleanUrl).trimStart('/')
        }

        var stream: InputStream? = null

        if (book is EpubBook) {
            val chapterDir = (currentChapter.fullZipPath ?: "").substringBeforeLast('/', "")
            val candidatePath = EpubParser.resolvePath(chapterDir, relativePath)
            stream = book.getEntryStream(candidatePath)
            if (stream == null) {
                val opfCandidate = EpubParser.resolvePath(book.opfDir, relativePath)
                stream = book.getEntryStream(opfCandidate)
            }
            if (stream == null) {
                stream = book.getEntryStream(relativePath)
            }
        } else {
            stream = book.getEntryStream(relativePath)
            if (stream == null) {
                stream = book.getEntryStream(relativePath.substringAfterLast('/'))
            }
        }

        if (stream != null) {
            val ext = relativePath.substringAfterLast('.', "").lowercase()
            val mime = MIME_MAP[ext] ?: "application/octet-stream"
            return WebResourceResponse(mime, "UTF-8", stream)
        }

        return null
    }

    /**
     * Injects reader CSS styles into the chapter HTML so it matches the reader theme,
     * typography, margins, font family, line spacing, and paged/scroll mode.
     */
    fun injectStyles(
        html: String,
        theme: EpubTheme,
        fontSizePercent: Int,
        fontFamily: String = "sans-serif",
        lineHeight: Float = 1.65f,
        textAlign: String = "justify",
        marginHorizontal: Int = 20,
        isPaged: Boolean = false
    ): String {
        val fontCss = when (fontFamily) {
            "serif" -> "Georgia, 'Times New Roman', Cambria, serif"
            "monospace" -> "'JetBrains Mono', 'Fira Code', 'Courier New', monospace"
            "sans-serif" -> "-apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif"
            else -> "system-ui, sans-serif"
        }

        val pagedCss = if (isPaged) {
            """
            html {
                height: 100vh !important;
                overflow: hidden !important;
            }
            body {
                height: 100vh !important;
                box-sizing: border-box !important;
                column-width: 100vw !important;
                column-gap: ${marginHorizontal * 2}px !important;
                column-fill: auto !important;
                overflow-x: scroll !important;
                overflow-y: hidden !important;
                scroll-snap-type: x mandatory !important;
            }
            """
        } else {
            """
            html, body {
                min-height: 100% !important;
            }
            """
        }

        val css = """
            <style id="prism-reader-style">
                $pagedCss
                html, body {
                    background-color: ${theme.bgHex} !important;
                    color: ${theme.textHex} !important;
                    font-family: $fontCss !important;
                    font-size: ${fontSizePercent}% !important;
                    line-height: $lineHeight !important;
                    margin: 0 !important;
                    padding: 16px ${marginHorizontal}px 48px ${marginHorizontal}px !important;
                    box-sizing: border-box !important;
                    word-wrap: break-word !important;
                    user-select: text !important;
                    -webkit-user-select: text !important;
                }
                a, a:visited {
                    color: ${theme.linkHex} !important;
                    text-decoration: underline !important;
                }
                img, svg, picture, video {
                    max-width: 100% !important;
                    height: auto !important;
                    display: block !important;
                    margin: 12px auto !important;
                    border-radius: 4px !important;
                }
                table {
                    max-width: 100% !important;
                    overflow-x: auto !important;
                    display: block !important;
                    border-collapse: collapse !important;
                }
                p {
                    margin-top: 0.8em !important;
                    margin-bottom: 0.8em !important;
                    text-align: $textAlign !important;
                }
                h1, h2, h3, h4, h5, h6 {
                    color: ${theme.textHex} !important;
                    line-height: 1.3 !important;
                    margin-top: 1.2em !important;
                    margin-bottom: 0.5em !important;
                    text-align: left !important;
                }
                blockquote {
                    margin: 1em 0 !important;
                    padding-left: 16px !important;
                    border-left: 3px solid ${theme.linkHex} !important;
                    opacity: 0.85 !important;
                }
                pre, code {
                    background-color: rgba(128, 128, 128, 0.15) !important;
                    border-radius: 4px !important;
                    font-family: monospace !important;
                }
            </style>
            <script id="prism-reader-script">
                window.getScrollProgress = function() {
                    var total = document.documentElement.scrollHeight - window.innerHeight;
                    if (total <= 0) return 0;
                    return window.scrollY / total;
                };
                window.restoreScrollProgress = function(progress) {
                    var total = document.documentElement.scrollHeight - window.innerHeight;
                    if (total > 0 && progress > 0) {
                        window.scrollTo(0, progress * total);
                    }
                };
                window.pageTurn = function(forward) {
                    var step = window.innerWidth;
                    if (forward) {
                        window.scrollBy({ left: step, behavior: 'smooth' });
                    } else {
                        window.scrollBy({ left: -step, behavior: 'smooth' });
                    }
                };
            </script>
        """.trimIndent()

        return if (html.contains("</head>", ignoreCase = true)) {
            html.replaceFirst("(?i)</head>".toRegex(), "$css</head>")
        } else if (html.contains("<body>", ignoreCase = true)) {
            html.replaceFirst("(?i)<body>".toRegex(), "<head>$css</head><body>")
        } else {
            "$css$html"
        }
    }
}
