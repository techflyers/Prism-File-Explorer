package com.techflyers.compose.file.explorer.screen.viewer.markdown

import android.content.Context
import android.net.Uri
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.TextView
import android.widget.Toast
import androidx.activity.compose.setContent
import com.techflyers.compose.file.explorer.screen.main.tab.files.holder.LocalFileHolder
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
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.PictureAsPdf
import com.techflyers.compose.file.explorer.screen.main.tab.files.ui.dialog.ExtendedMetadataSheet
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
import com.techflyers.compose.file.explorer.App.Companion.globalClass
import com.techflyers.compose.file.explorer.common.ConvertioApiKeyDialog
import com.techflyers.compose.file.explorer.common.ConvertioProgressDialog
import com.techflyers.compose.file.explorer.common.ConvertioService
import com.techflyers.compose.file.explorer.common.ui.SafeSurface
import com.techflyers.compose.file.explorer.screen.viewer.ViewerActivity
import com.techflyers.compose.file.explorer.screen.viewer.ViewerInstance
import com.techflyers.compose.file.explorer.theme.FileExplorerTheme
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
import com.techflyers.compose.file.explorer.common.resolveUriToPath

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
    var showMetadataSheet by remember { mutableStateOf(false) }
    var isPrintLayout by remember { mutableStateOf(false) }
    var searchVisible by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var activeMatchIndex by remember { mutableStateOf(0) }
    var totalMatchesCount by remember { mutableStateOf(0) }
    var textViewRef by remember { mutableStateOf<TextView?>(null) }
    var printWebViewRef by remember { mutableStateOf<WebView?>(null) }
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
                val tempFile = File(context.cacheDir, fileName.ifEmpty { "temp.md" })
                val targetFile = if (file.exists()) file else if (tempFile.exists()) tempFile else null
                val newText = when {
                    targetFile != null && targetFile.exists() -> targetFile.readText()
                    else -> context.contentResolver.openInputStream(instance.uri)?.use { stream ->
                        stream.bufferedReader().readText()
                    }
                }
                if (newText != null && newText != sourceContent) {
                    withContext(Dispatchers.Main) { sourceContent = newText }
                    if (instance.uri.scheme == "content") {
                        try {
                            context.contentResolver.openOutputStream(instance.uri, "wt")?.use { out ->
                                out.write(newText.toByteArray())
                            }
                        } catch (_: Exception) {}
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
                                context.getString(com.techflyers.compose.file.explorer.R.string.open_with)
                            )
                        )
                    }) {
                        Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = "Open with")
                    }
                    IconButton(onClick = { showMetadataSheet = true }) {
                        Icon(Icons.Rounded.Info, contentDescription = "Extended Metadata")
                    }
                    IconButton(onClick = {
                        ConvertioService.convertToPdf(context, filePath)
                    }) {
                        Icon(Icons.Rounded.PictureAsPdf, contentDescription = "Convert to PDF")
                    }
                }
            )
            if (showMetadataSheet) {
                ExtendedMetadataSheet(
                    uri = instance.uri,
                    extraFilePath = filePath,
                    displayNameHint = fileName,
                    onDismiss = { showMetadataSheet = false }
                )
            }
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
                            if (isPrintLayout && !showSource) {
                                if (it.length >= 2) {
                                    printWebViewRef?.findAllAsync(it)
                                } else {
                                    printWebViewRef?.clearMatches()
                                    totalMatchesCount = 0
                                }
                            }
                        },
                        placeholder = { Text("Search text...") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = {
                                    searchQuery = ""
                                    printWebViewRef?.clearMatches()
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
                        if (isPrintLayout && !showSource) {
                            printWebViewRef?.findNext(false)
                        } else {
                            activeMatchIndex = if (activeMatchIndex <= 0) maxOf(0, totalMatchesCount - 1) else activeMatchIndex - 1
                        }
                    }) {
                        Icon(Icons.Rounded.KeyboardArrowUp, contentDescription = "Previous")
                    }
                    IconButton(onClick = {
                        if (isPrintLayout && !showSource) {
                            printWebViewRef?.findNext(true)
                        } else {
                            activeMatchIndex = (activeMatchIndex + 1) % maxOf(1, totalMatchesCount)
                        }
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
                    // Dedicated A4 document/sheet print preview layout
                    val isDark = isSystemInDarkTheme()
                    val baseDir = remember(filePath) {
                        try {
                            val f = File(filePath)
                            if (f.exists() && f.parentFile != null) "file://${f.parentFile!!.absolutePath}/" else null
                        } catch (_: Exception) {
                            null
                        }
                    }
                    val printHtml = remember(fileName, sourceContent, isDark) {
                        generatePrintHtml(
                            title = fileName,
                            markdown = sourceContent,
                            isScreenPreview = true,
                            isDarkBackdrop = isDark
                        )
                    }
                    AndroidView(
                        factory = { ctx ->
                            WebView(ctx).apply {
                                webViewClient = WebViewClient()
                                setFindListener { activeMatchOrdinal, numberOfMatches, _ ->
                                    activeMatchIndex = activeMatchOrdinal
                                    totalMatchesCount = numberOfMatches
                                }
                                settings.apply {
                                    javaScriptEnabled = false
                                    allowFileAccess = true
                                    useWideViewPort = true
                                    loadWithOverviewMode = true
                                    builtInZoomControls = true
                                    displayZoomControls = false
                                    textZoom = 100
                                    layoutAlgorithm = android.webkit.WebSettings.LayoutAlgorithm.NORMAL
                                }
                                setInitialScale(0)
                                printWebViewRef = this
                            }
                        },
                        update = { webView ->
                            if (webView.tag != printHtml) {
                                webView.tag = printHtml
                                webView.loadDataWithBaseURL(baseDir, printHtml, "text/html", "UTF-8", null)
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
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
        val webView = WebView(context).apply {
            settings.apply {
                javaScriptEnabled = false
                allowFileAccess = true
                useWideViewPort = true
                loadWithOverviewMode = true
                textZoom = 100
                layoutAlgorithm = android.webkit.WebSettings.LayoutAlgorithm.NORMAL
            }
            // Lay out offscreen WebView at A4 CSS pixel dimensions (794 x 1123 at 96 DPI)
            measure(
                android.view.View.MeasureSpec.makeMeasureSpec(794, android.view.View.MeasureSpec.EXACTLY),
                android.view.View.MeasureSpec.makeMeasureSpec(1123, android.view.View.MeasureSpec.UNSPECIFIED)
            )
            layout(0, 0, 794, 1123)
        }
        val html = generatePrintHtml(fileName, content, isScreenPreview = false)

        val baseDir = try {
            val f = File(sourcePath)
            if (f.exists() && f.parentFile != null) "file://${f.parentFile!!.absolutePath}/" else null
        } catch (_: Exception) {
            null
        }

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                webView.measure(
                    android.view.View.MeasureSpec.makeMeasureSpec(794, android.view.View.MeasureSpec.EXACTLY),
                    android.view.View.MeasureSpec.makeMeasureSpec(0, android.view.View.MeasureSpec.UNSPECIFIED)
                )
                webView.layout(0, 0, 794, webView.measuredHeight.coerceAtLeast(1123))
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

private fun generatePrintHtml(
    title: String,
    markdown: String,
    isScreenPreview: Boolean = false,
    isDarkBackdrop: Boolean = false
): String {
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

    val escapedTitle = title.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
    val backdropColor = if (isDarkBackdrop) "#141416" else "#E8ECEF"
    val screenBodyStyle = if (isScreenPreview) {
        """
        html, body {
            background-color: $backdropColor;
            margin: 0;
            padding: 20px 16px;
        }
        .print-sheet {
            width: 794px;
            min-height: 1123px;
            margin: 0 auto;
            padding: 56px 64px;
            background-color: #ffffff;
            border: 1px solid #d6d8dc;
            border-radius: 4px;
            box-shadow: 0 6px 24px rgba(0, 0, 0, 0.22);
            box-sizing: border-box;
            display: flex;
            flex-direction: column;
        }
        .doc-content {
            flex: 1;
        }
        .doc-footer {
            margin-top: 32px;
            padding-top: 12px;
            border-top: 1px solid #e8e8e8;
            display: flex;
            justify-content: space-between;
            font-size: 12px;
            color: #9e9e9e;
            font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
        }
        """
    } else {
        """
        html, body {
            background-color: #ffffff;
            margin: 0;
            padding: 0;
        }
        .print-sheet {
            width: 794px;
            margin: 0 auto;
            padding: 16px;
            background-color: #ffffff;
            box-sizing: border-box;
        }
        .print-badge, .doc-footer {
            display: none;
        }
        """
    }

    return """
        <!DOCTYPE html>
        <html>
        <head>
            <meta charset="utf-8">
            <meta name="viewport" content="width=840">
            <title>$escapedTitle</title>
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
                    -webkit-text-size-adjust: 100%;
                }
                $screenBodyStyle
                @media print {
                    html, body {
                        background-color: #ffffff !important;
                        margin: 0 !important;
                        padding: 0 !important;
                    }
                    .print-sheet {
                        width: 100% !important;
                        min-height: 0 !important;
                        margin: 0 !important;
                        padding: 0 !important;
                        border: none !important;
                        box-shadow: none !important;
                    }
                    .print-badge, .doc-footer {
                        display: none !important;
                    }
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
                .print-badge {
                    font-size: 11px;
                    font-weight: 700;
                    letter-spacing: 1px;
                    color: #757575;
                    font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
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
            <div class="print-sheet">
                <div class="doc-header">
                    <div class="doc-title">$escapedTitle</div>
                    <div class="print-badge">PRINT PREVIEW</div>
                </div>
                <div class="doc-content">
                    $bodyHtml
                </div>
                <div class="doc-footer">
                    <span>Prism File Explorer +</span>
                    <span>Document View</span>
                </div>
            </div>
        </body>
        </html>
    """.trimIndent()
}
