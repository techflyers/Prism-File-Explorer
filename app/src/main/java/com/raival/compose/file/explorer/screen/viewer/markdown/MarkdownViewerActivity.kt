package com.raival.compose.file.explorer.screen.viewer.markdown

import android.net.Uri
import android.widget.TextView
import androidx.activity.compose.setContent
import com.raival.compose.file.explorer.screen.main.tab.files.holder.LocalFileHolder
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.OpenInNew
import com.raival.compose.file.explorer.App.Companion.globalClass
import com.raival.compose.file.explorer.common.ConvertioService
import com.raival.compose.file.explorer.common.ConvertioApiKeyDialog
import com.raival.compose.file.explorer.common.ConvertioProgressDialog
import com.raival.compose.file.explorer.common.ui.SafeSurface
import com.raival.compose.file.explorer.screen.viewer.ViewerActivity
import com.raival.compose.file.explorer.screen.viewer.ViewerInstance
import com.raival.compose.file.explorer.theme.FileExplorerTheme
import io.noties.markwon.Markwon
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.ext.tasklist.TaskListPlugin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MarkdownViewerActivity : ViewerActivity() {
    override fun onCreateNewInstance(uri: Uri, uid: String): ViewerInstance {
        return MarkdownViewerInstance(uri, uid)
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
    override val id: String
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
    var searchVisible by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var activeMatchIndex by remember { mutableStateOf(0) }
    var totalMatchesCount by remember { mutableStateOf(0) }
    var textViewRef by remember { mutableStateOf<TextView?>(null) }
    val scope = rememberCoroutineScope()

    val filePath = remember {
        try {
            val cursor = context.contentResolver.query(instance.uri, null, null, null, null)
            var path = ""
            cursor?.use {
                if (it.moveToFirst()) {
                    val idx = it.getColumnIndex("_data")
                    if (idx >= 0) path = it.getString(idx) ?: ""
                }
            }
            if (path.isEmpty()) instance.uri.path ?: "" else path
        } catch (_: Exception) {
            instance.uri.path ?: ""
        }
    }

    val fileName = remember { filePath.substringAfterLast('/') }

    // Load content
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            try {
                val file = File(filePath)
                if (file.exists()) {
                    sourceContent = file.readText()
                } else {
                    // Try to read from URI
                    context.contentResolver.openInputStream(instance.uri)?.use { stream ->
                        sourceContent = stream.bufferedReader().readText()
                    }
                }
            } catch (e: Exception) {
                sourceContent = "Failed to load file: ${e.message}"
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
                        Icon(Icons.Rounded.OpenInNew, contentDescription = "Open with")
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
                    androidx.compose.material3.OutlinedTextField(
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
                } else {
                    // Markdown rendered preview
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
