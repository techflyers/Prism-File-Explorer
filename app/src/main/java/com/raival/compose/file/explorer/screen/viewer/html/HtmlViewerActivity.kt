package com.raival.compose.file.explorer.screen.viewer.html

import android.net.Uri
import android.webkit.WebView
import android.webkit.WebViewClient
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
import androidx.compose.material.icons.rounded.Preview
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Alignment
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import com.raival.compose.file.explorer.App.Companion.globalClass
import com.raival.compose.file.explorer.common.ConvertioApiKeyDialog
import com.raival.compose.file.explorer.common.ConvertioProgressDialog
import com.raival.compose.file.explorer.common.ConvertioService
import com.raival.compose.file.explorer.common.ui.SafeSurface
import com.raival.compose.file.explorer.screen.viewer.ViewerActivity
import com.raival.compose.file.explorer.screen.viewer.ViewerInstance
import com.raival.compose.file.explorer.theme.FileExplorerTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class HtmlViewerActivity : ViewerActivity() {
    override fun onCreateNewInstance(uri: Uri, uid: String): ViewerInstance {
        return HtmlViewerInstance(uri, uid)
    }

    override fun onReady(instance: ViewerInstance) {
        if (instance !is HtmlViewerInstance) {
            globalClass.showMsg("Invalid HTML file")
            finish()
            return
        }

        setContent {
            FileExplorerTheme {
                SafeSurface(false) {
                    HtmlViewerScreen(
                        instance = instance,
                        onBackPress = { onBackPressedDispatcher.onBackPressed() }
                    )
                }
            }
        }
    }
}

class HtmlViewerInstance(
    override val uri: Uri,
    override val id: String
) : ViewerInstance {
    override fun onClose() {}
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HtmlViewerScreen(
    instance: HtmlViewerInstance,
    onBackPress: () -> Unit
) {
    var sourceContent by remember { mutableStateOf("") }
    var showSource by remember { mutableStateOf(false) }
    var searchVisible by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var activeMatchIndex by remember { mutableStateOf(0) }
    var totalMatchesCount by remember { mutableStateOf(0) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    val filePath = remember {
        try {
            val cursor = globalClass.contentResolver.query(instance.uri, null, null, null, null)
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

    // Load source
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            try {
                val file = File(filePath)
                if (file.exists()) {
                    sourceContent = file.readText()
                } else {
                    globalClass.contentResolver.openInputStream(instance.uri)?.use { stream ->
                        sourceContent = stream.bufferedReader().readText()
                    }
                }
            } catch (e: Exception) {
                sourceContent = "Failed to load file: ${e.message}"
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
                    if (!showSource) {
                        IconButton(onClick = {
                            searchVisible = !searchVisible
                            if (!searchVisible) {
                                searchQuery = ""
                                webViewRef?.clearMatches()
                            }
                        }) {
                            Icon(
                                imageVector = if (searchVisible) Icons.Rounded.Close else Icons.Rounded.Search,
                                contentDescription = "Search"
                            )
                        }
                    }
                    IconButton(onClick = {
                        if (showSource) {
                            showSource = false
                        } else {
                            // Open in text editor
                            val file = java.io.File(filePath)
                            if (file.exists()) {
                                globalClass.textEditorManager.openTextEditor(
                                    LocalFileHolder(file),
                                    globalClass
                                )
                            } else {
                                showSource = true
                            }
                        }
                    }) {
                        Icon(
                            if (showSource) Icons.Rounded.Preview else Icons.Rounded.Code,
                            contentDescription = if (showSource) "Preview" else "View Source"
                        )
                    }
                    IconButton(onClick = {
                        ConvertioService.convertToPdf(globalClass, filePath)
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
            if (searchVisible && !showSource) {
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
                            webViewRef?.findAllAsync(it)
                        },
                        placeholder = { Text("Search text...") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = {
                                    searchQuery = ""
                                    webViewRef?.clearMatches()
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
                    IconButton(onClick = { webViewRef?.findNext(false) }) {
                        Icon(Icons.Rounded.KeyboardArrowUp, contentDescription = "Previous")
                    }
                    IconButton(onClick = { webViewRef?.findNext(true) }) {
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
                                .verticalScroll(rememberScrollState())
                        ) {
                            Text(
                                sourceContent,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 13.sp
                            )
                        }
                    }
                } else {
                    // WebView preview
                    AndroidView(
                        factory = { ctx ->
                            WebView(ctx).apply {
                                webViewClient = WebViewClient()
                                setFindListener { activeMatchOrdinal, numberOfMatches, isDoneCounting ->
                                    activeMatchIndex = activeMatchOrdinal
                                    totalMatchesCount = numberOfMatches
                                }
                                settings.apply {
                                    javaScriptEnabled = false
                                    allowFileAccess = true
                                    loadWithOverviewMode = true
                                    useWideViewPort = true
                                    builtInZoomControls = true
                                    displayZoomControls = false
                                }
                                webViewRef = this
                            }
                        },
                        update = { webView ->
                            val file = File(filePath)
                            if (file.exists()) {
                                webView.loadUrl("file://${file.absolutePath}")
                            } else {
                                webView.loadDataWithBaseURL(
                                    null,
                                    sourceContent,
                                    "text/html",
                                    "UTF-8",
                                    null
                                )
                            }
                        },
                        modifier = Modifier.fillMaxSize()
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
