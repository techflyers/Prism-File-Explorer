package com.raival.compose.file.explorer.screen.viewer.markdown

import android.content.Context
import android.net.Uri
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.TextView
import android.widget.Toast
import androidx.activity.compose.setContent
import com.raival.compose.file.explorer.screen.main.tab.files.holder.LocalFileHolder
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Print
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.raival.compose.file.explorer.App.Companion.globalClass
import com.raival.compose.file.explorer.common.ConvertioApiKeyDialog
import com.raival.compose.file.explorer.common.ConvertioProgressDialog
import com.raival.compose.file.explorer.common.ConvertioService
import com.raival.compose.file.explorer.common.ui.SafeSurface
import com.raival.compose.file.explorer.screen.viewer.ViewerActivity
import com.raival.compose.file.explorer.screen.viewer.ViewerInstance
import com.raival.compose.file.explorer.theme.FileExplorerTheme
import io.noties.markwon.Markwon
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.ext.tasklist.TaskListPlugin
import io.noties.markwon.html.HtmlPlugin
import io.noties.markwon.image.ImagesPlugin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension
import org.commonmark.ext.gfm.tables.TablesExtension
import org.commonmark.parser.Parser
import org.commonmark.renderer.html.HtmlRenderer
import java.io.File

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.raival.compose.file.explorer.common.resolveUriToPath

class MarkdownViewerActivity : ViewerActivity() {
    override fun onCreateNewInstance(uri: Uri, uid: String): ViewerInstance {
        val extraPath = intent.getStringExtra("extra_file_path")
        return MarkdownViewerInstance(uri, uid, extraPath)
    }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onReady(instance: ViewerInstance) {
        if (instance !is MarkdownViewerInstance) {
            globalClass.showMsg("Invalid Markdown file")
            finish()
            return
        }

        setContent {
            FileExplorerTheme {
                SafeSurface(false) {
                    MarkdownViewerScreen(
                        instance = instance,
                        onBackPress = { onBackPressedDispatcher.onBackPressed() }
                    )
                }
            }
        }
    }
}

class MarkdownViewerInstance(
    override val uri: Uri,
    override val id: String,
    val extraFilePath: String? = null
) : ViewerInstance {
    override fun onClose() {}
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MarkdownViewerScreen(
    instance: MarkdownViewerInstance,
    onBackPress: () -> Unit
) {
    val context = LocalContext.current
    val density = androidx.compose.ui.platform.LocalDensity.current
    var sourceContent by remember { mutableStateOf("") }
    var showSource by remember { mutableStateOf(false) }
    var isPrintLayout by remember { mutableStateOf(false) }
    var searchVisible by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var activeMatchIndex by remember { mutableStateOf(0) }
    var totalMatchesCount by remember { mutableStateOf(0) }
    var textViewRef by remember { mutableStateOf<TextView?>(null) }
    val scope = rememberCoroutineScope()

    val filePath = remember {
        resolveUriToPath(context, instance.uri, instance.extraFilePath)
    }

    val fileName = remember { filePath.substringAfterLast('/') }

    // Load / reload content on resume
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        scope.launch(Dispatchers.IO) {
            try {
                val file = File(filePath)
                if (file.exists()) {
                    val text = file.readText()
                    withContext(Dispatchers.Main) { sourceContent = text }
                } else {
                    context.contentResolver.openInputStream(instance.uri)?.use { stream ->
                        val text = stream.bufferedReader().readText()
                        withContext(Dispatchers.Main) { sourceContent = text }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { sourceContent = "Failed to load file: ${e.message}" }
            }
        }
    }

    val textColor = MaterialTheme.colorScheme.onSurface.toArgb()
    val linkColor = MaterialTheme.colorScheme.primary.toArgb()

    val markwon = remember(context) {
        Markwon.builder(context)
            .usePlugin(StrikethroughPlugin.create())
            .usePlugin(TablePlugin.create(context))
            .usePlugin(TaskListPlugin.create(context))
            .usePlugin(HtmlPlugin.create())
            .usePlugin(ImagesPlugin.create())
            .build()
    }

    val previewScrollState = rememberScrollState()
    val sourceScrollState = rememberScrollState()

    // Highlight Markdown preview TextView
    LaunchedEffect(searchQuery, activeMatchIndex, textViewRef, sourceContent, showSource) {
        val textView = textViewRef
        if (textView != null && !showSource) {
            val text = textView.text
            if (text is android.text.Spannable) {
                // Clear existing highlights
                val spans = text.getSpans(0, text.length, android.text.style.BackgroundColorSpan::class.java)
                for (span in spans) {
                    text.removeSpan(span)
                }

                if (searchQuery.length >= 2) {
                    val string = text.toString()
                    var index = string.indexOf(searchQuery, ignoreCase = true)
                    val matchOffsets = mutableListOf<Int>()

                    while (index >= 0) {
                        matchOffsets.add(index)
                        index = string.indexOf(searchQuery, index + searchQuery.length, ignoreCase = true)
                    }

                    totalMatchesCount = matchOffsets.size

                    matchOffsets.forEachIndexed { idx, start ->
                        val end = start + searchQuery.length
                        val color = if (idx == activeMatchIndex) 0xFFFF9800.toInt() else 0x66FFFF00
                        text.setSpan(
                            android.text.style.BackgroundColorSpan(color),
                            start,
                            end,
                            android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                        )
                    }

                    // Scroll to the active match
                    if (activeMatchIndex in matchOffsets.indices) {
                        val activeOffset = matchOffsets[activeMatchIndex]
                        val layout = textView.layout
                        if (layout != null) {
                            val line = layout.getLineForOffset(activeOffset)
                            val y = layout.getLineTop(line)
                            previewScrollState.animateScrollTo(maxOf(0, y - 100))
                        }
                    }
                } else {
                    totalMatchesCount = 0
                }
            }
        }
    }

    val totalSourceMatches = remember(sourceContent, searchQuery) {
        if (searchQuery.length < 2) 0
        else {
            var count = 0
            var index = sourceContent.indexOf(searchQuery, ignoreCase = true)
            while (index >= 0) {
                count++
                index = sourceContent.indexOf(searchQuery, index + searchQuery.length, ignoreCase = true)
            }
            count
        }
    }

    LaunchedEffect(searchQuery, showSource) {
        if (showSource) {
            totalMatchesCount = totalSourceMatches
            activeMatchIndex = 0
        }
    }

    LaunchedEffect(activeMatchIndex, showSource, searchQuery) {
        if (showSource && totalSourceMatches > 0 && searchQuery.length >= 2) {
            var matchIdx = 0
            var charIdx = sourceContent.indexOf(searchQuery, ignoreCase = true)
            while (charIdx >= 0) {
                if (matchIdx == activeMatchIndex) {
                    val newlinesCount = sourceContent.take(charIdx).count { it == '\n' }
                    val approximateScrollY = newlinesCount * 18
                    val targetScrollPx = with(density) { approximateScrollY.dp.roundToPx() }
                    sourceScrollState.animateScrollTo(targetScrollPx)
                    break
                }
                matchIdx++
                charIdx = sourceContent.indexOf(searchQuery, charIdx + searchQuery.length, ignoreCase = true)
            }
        }
    }

    val annotatedSource = remember(sourceContent, searchQuery, activeMatchIndex, showSource) {
        if (searchQuery.length < 2 || !showSource) {
            androidx.compose.ui.text.AnnotatedString(sourceContent)
        } else {
            androidx.compose.ui.text.buildAnnotatedString {
                append(sourceContent)
                var index = sourceContent.indexOf(searchQuery, ignoreCase = true)
                var matchIdx = 0
                while (index >= 0) {
                    val isCurrent = matchIdx == activeMatchIndex
                    addStyle(
                        style = androidx.compose.ui.text.SpanStyle(
                            background = if (isCurrent) Color(0xFFFF9800).copy(alpha = 0.6f) else Color.Yellow.copy(alpha = 0.4f)
                        ),
                        start = index,
                        end = index + searchQuery.length
                    )
                    matchIdx++
                    index = sourceContent.indexOf(searchQuery, index + searchQuery.length, ignoreCase = true)
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = fileName,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackPress) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        searchVisible = !searchVisible
                        if (!searchVisible) {
                            searchQuery = ""
                            totalMatchesCount = 0
                        }
                    }) {
                        Icon(
                            imageVector = if (searchVisible) Icons.Rounded.Close else Icons.Rounded.Search,
                            contentDescription = "Search"
                        )
                    }
                    IconButton(onClick = {
                        isPrintLayout = !isPrintLayout
                        if (isPrintLayout) showSource = false
                    }) {
                        Icon(
                            imageVector = if (isPrintLayout) Icons.Rounded.Smartphone else Icons.Rounded.Description,
                            contentDescription = if (isPrintLayout) "Mobile View" else "Print Layout"
                        )
                    }
                    IconButton(onClick = {
                        printMarkdownDocument(context, fileName, sourceContent, filePath)
                    }) {
                        Icon(
                            imageVector = Icons.Rounded.Print,
                            contentDescription = "Print / Export PDF"
                        )
                    }
                    IconButton(onClick = {
                        android.util.Log.d("MarkdownViewer", "Opening in text editor: filePath=$filePath")
                        val file = java.io.File(filePath)
                        if (file.exists()) {
                            globalClass.textEditorManager.openTextEditor(
                                LocalFileHolder(file),
                                globalClass
                            )
                        } else {
                            // Fallback: copy content to a temp file and open that
                            try {
                                val tempFile = java.io.File(globalClass.cacheDir, fileName.ifEmpty { "temp.md" })
                                tempFile.writeText(sourceContent)
                                globalClass.textEditorManager.openTextEditor(
                                    LocalFileHolder(tempFile),
                                    globalClass
                                )
                                android.util.Log.d("MarkdownViewer", "Opened via temp file: ${tempFile.absolutePath}")
                            } catch (e: Exception) {
                                android.util.Log.e("MarkdownViewer", "Failed to open text editor: ${e.message}")
                                globalClass.showMsg("Could not open text editor")
                            }
                        }
                    }) {
                        Icon(
                            Icons.Rounded.Code,
                            contentDescription = "View in Text Editor"
                        )
                    }
                    IconButton(onClick = {
                        val openIntent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                            data = instance.uri
                            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(
                            android.content.Intent.createChooser(
                                openIntent,
                                context.getString(com.raival.compose.file.explorer.R.string.open_with)
                            )
                        )
                    }) {
                        Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = "Open with")
                    }
                    IconButton(onClick = {
                        ConvertioService.convertToPdf(context, filePath)
                    }) {
                        Icon(Icons.Rounded.PictureAsPdf, contentDescription = "Convert to PDF")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (searchVisible) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = {
                            searchQuery = it
                            activeMatchIndex = 0
                        },
                        placeholder = { Text("Search text...") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = {
                                    searchQuery = ""
                                    totalMatchesCount = 0
                                }) {
                                    Icon(Icons.Rounded.Close, contentDescription = "Clear")
                                }
                            }
                        }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    if (totalMatchesCount > 0) {
                        Text(
                            text = "${activeMatchIndex + 1}/$totalMatchesCount",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                    }
                    IconButton(onClick = {
                        activeMatchIndex = if (activeMatchIndex <= 0) maxOf(0, totalMatchesCount - 1) else activeMatchIndex - 1
                    }) {
                        Icon(Icons.Rounded.KeyboardArrowUp, contentDescription = "Previous")
                    }
                    IconButton(onClick = {
                        activeMatchIndex = (activeMatchIndex + 1) % maxOf(1, totalMatchesCount)
                    }) {
                        Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Next")
                    }
                }
            }

            Box(modifier = Modifier.weight(1f)) {
                if (showSource) {
                    androidx.compose.foundation.text.selection.SelectionContainer {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp)
                                .verticalScroll(sourceScrollState)
                        ) {
                            Text(
                                text = annotatedSource,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 13.sp
                            )
                        }
                    }
                } else if (isPrintLayout) {
                    // Dedicated document/sheet print layout
                    val isDark = isSystemInDarkTheme()
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(if (isDark) Color(0xFF141416) else Color(0xFFE8ECEF))
                            .verticalScroll(previewScrollState),
                        contentAlignment = Alignment.TopCenter
                    ) {
                        Card(
                            modifier = Modifier
                                .widthIn(min = 320.dp, max = 680.dp)
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 24.dp)
                                .shadow(elevation = 6.dp, shape = RoundedCornerShape(4.dp)),
                            shape = RoundedCornerShape(4.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = Color.White
                            ),
                            border = BorderStroke(1.dp, Color(0xFFD6D8DC))
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 28.dp, vertical = 28.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = fileName,
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = FontFamily.Serif
                                        ),
                                        color = Color(0xFF1C1B1F),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        text = "PRINT PREVIEW",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 1.sp
                                        ),
                                        color = Color(0xFF757575)
                                    )
                                }
                                Spacer(modifier = Modifier.height(10.dp))
                                HorizontalDivider(color = Color(0xFFE0E0E0), thickness = 1.dp)
                                Spacer(modifier = Modifier.height(16.dp))

                                AndroidView(
                                    factory = { ctx ->
                                        TextView(ctx).apply {
                                            textSize = 15f
                                            setTextColor(0xFF1C1B1F.toInt())
                                            setLinkTextColor(0xFF0B57D0.toInt())
                                            setPadding(0, 8, 0, 16)
                                            setTextIsSelectable(true)
                                            setLineSpacing(6f, 1.2f)
                                            typeface = android.graphics.Typeface.SERIF
                                            textViewRef = this
                                        }
                                    },
                                    update = { textView ->
                                        markwon.setMarkdown(textView, sourceContent)
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                )

                                Spacer(modifier = Modifier.height(20.dp))
                                HorizontalDivider(color = Color(0xFFE8E8E8), thickness = 1.dp)
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "Prism File Explorer",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color(0xFF9E9E9E)
                                    )
                                    Text(
                                        text = "Document View",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color(0xFF9E9E9E)
                                    )
                                }
                            }
                        }
                    }
                } else {
                    // Markdown rendered preview (mobile style)
                    AndroidView(
                        factory = { ctx ->
                            TextView(ctx).apply {
                                textSize = 16f
                                setTextColor(textColor)
                                setLinkTextColor(linkColor)
                                setPadding(48, 48, 48, 48)
                                setTextIsSelectable(true)
                                textViewRef = this
                            }
                        },
                        update = { textView ->
                            markwon.setMarkdown(textView, sourceContent)
                        },
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(previewScrollState)
                    )
                }
            }
        }
    }

    // Convertio dialogs
    if (ConvertioService.showApiKeyDialog) {
        ConvertioApiKeyDialog(
            onDismiss = { ConvertioService.showApiKeyDialog = false },
            onConfirm = { ConvertioService.onApiKeyConfirmed(it) }
        )
    }
    if (ConvertioService.showProgressDialog) {
        ConvertioProgressDialog(onCancel = { ConvertioService.cancelConversion() })
        LaunchedEffect(Unit) {
            val ctx = ConvertioService.getPendingContext()
            val path = ConvertioService.getPendingFilePath()
            if (ctx != null && path != null) {
                ConvertioService.executeConversion(ctx, path, globalClass.preferencesManager.convertioApiKey)
            }
        }
    }
}

private fun printMarkdownDocument(context: Context, fileName: String, content: String, sourcePath: String) {
    try {
        val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager
        if (printManager == null) {
            Toast.makeText(context, "Printing not supported on this device", Toast.LENGTH_SHORT).show()
            return
        }
        val jobName = "Prism_${fileName.substringBeforeLast('.')}"
        val webView = WebView(context)
        val html = generatePrintHtml(fileName, content)

        val baseDir = try {
            val f = File(sourcePath)
            if (f.exists() && f.parentFile != null) "file://${f.parentFile!!.absolutePath}/" else null
        } catch (_: Exception) {
            null
        }

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                val printAdapter = webView.createPrintDocumentAdapter(jobName)
                val printAttributes = PrintAttributes.Builder()
                    .setColorMode(PrintAttributes.COLOR_MODE_COLOR)
                    .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
                    .build()
                printManager.print(jobName, printAdapter, printAttributes)
            }
        }
        webView.loadDataWithBaseURL(baseDir, html, "text/html", "UTF-8", null)
    } catch (e: Exception) {
        e.printStackTrace()
        Toast.makeText(context, "Print error: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}

private fun generatePrintHtml(title: String, markdown: String): String {
    val bodyHtml = try {
        val extensions = listOf(
            TablesExtension.create(),
            StrikethroughExtension.create()
        )
        val parser = Parser.builder().extensions(extensions).build()
        val renderer = HtmlRenderer.builder().extensions(extensions).build()
        val document = parser.parse(markdown)
        renderer.render(document)
    } catch (_: Exception) {
        "<pre style=\"white-space: pre-wrap;\">${markdown.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")}</pre>"
    }

    return """
        <!DOCTYPE html>
        <html>
        <head>
            <meta charset="utf-8">
            <title>${title.replace("<", "&lt;").replace(">", "&gt;")}</title>
            <style>
                @page {
                    size: A4;
                    margin: 20mm 15mm 20mm 15mm;
                }
                body {
                    font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", "Georgia", "Times New Roman", Roboto, serif;
                    font-size: 14px;
                    line-height: 1.65;
                    color: #1a1a1a;
                    background-color: #ffffff;
                    margin: 0;
                    padding: 16px;
                }
                .doc-header {
                    border-bottom: 2px solid #333333;
                    padding-bottom: 8px;
                    margin-bottom: 24px;
                    display: flex;
                    justify-content: space-between;
                    align-items: flex-end;
                }
                .doc-title {
                    font-size: 20px;
                    font-weight: 700;
                    margin: 0;
                    color: #111111;
                }
                h1, h2, h3, h4, h5, h6 {
                    color: #111111;
                    font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
                    border-bottom: 1px solid #eaecef;
                    padding-bottom: .3em;
                    margin-top: 24px;
                    margin-bottom: 12px;
                    page-break-after: avoid;
                }
                h1 { font-size: 24px; }
                h2 { font-size: 20px; }
                h3 { font-size: 16px; }
                table {
                    border-collapse: collapse;
                    width: 100%;
                    margin: 16px 0;
                    page-break-inside: avoid;
                }
                th, td {
                    border: 1px solid #cccccc;
                    padding: 8px 12px;
                    text-align: left;
                }
                th {
                    background-color: #f2f2f2;
                    font-weight: 600;
                }
                tr:nth-child(even) {
                    background-color: #fafafa;
                }
                img {
                    max-width: 100%;
                    height: auto;
                    page-break-inside: avoid;
                    border-radius: 4px;
                }
                code {
                    padding: 2px 5px;
                    background-color: #f3f4f6;
                    border-radius: 4px;
                    font-family: "SFMono-Regular", Consolas, "Liberation Mono", Menlo, monospace;
                    font-size: 88%;
                }
                pre {
                    background-color: #f6f8fa;
                    border: 1px solid #e1e4e8;
                    border-radius: 6px;
                    padding: 14px;
                    overflow: auto;
                    page-break-inside: avoid;
                }
                pre code {
                    background-color: transparent;
                    padding: 0;
                }
                blockquote {
                    margin: 16px 0;
                    padding: 8px 16px;
                    border-left: 4px solid #0056b3;
                    background-color: #f8f9fa;
                    color: #495057;
                }
                hr {
                    border: 0;
                    border-top: 1px solid #d0d7de;
                    margin: 24px 0;
                }
                ul, ol {
                    padding-left: 24px;
                }
                li {
                    margin-bottom: 4px;
                }
            </style>
        </head>
        <body>
            <div class="doc-header">
                <div class="doc-title">${title.replace("<", "&lt;").replace(">", "&gt;")}</div>
            </div>
            $bodyHtml
        </body>
        </html>
    """.trimIndent()
}
