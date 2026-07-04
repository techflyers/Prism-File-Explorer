package com.raival.compose.file.explorer.screen.viewer.latex

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.raival.compose.file.explorer.App.Companion.globalClass
import com.raival.compose.file.explorer.common.ConvertioApiKeyDialog
import com.raival.compose.file.explorer.common.ConvertioProgressDialog
import com.raival.compose.file.explorer.common.ConvertioService
import com.raival.compose.file.explorer.common.NativeBinaryExecutor
import com.raival.compose.file.explorer.common.TectonicCacheManager
import com.raival.compose.file.explorer.common.ui.SafeSurface
import com.raival.compose.file.explorer.screen.main.tab.files.holder.LocalFileHolder
import com.raival.compose.file.explorer.screen.viewer.ViewerActivity
import com.raival.compose.file.explorer.screen.viewer.ViewerInstance
import com.raival.compose.file.explorer.screen.viewer.pdf.PdfViewerActivity
import com.raival.compose.file.explorer.theme.FileExplorerTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class LatexViewerActivity : ViewerActivity() {
    override fun onCreateNewInstance(uri: Uri, uid: String): ViewerInstance {
        return LatexViewerInstance(uri, uid)
    }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onReady(instance: ViewerInstance) {
        if (instance !is LatexViewerInstance) {
            globalClass.showMsg("Invalid LaTeX file")
            finish()
            return
        }

        setContent {
            FileExplorerTheme {
                SafeSurface(false) {
                    LatexViewerScreen(
                        instance = instance,
                        onBackPress = { onBackPressedDispatcher.onBackPressed() }
                    )
                }
            }
        }
    }
}

class LatexViewerInstance(
    override val uri: Uri,
    override val id: String
) : ViewerInstance {
    override fun onClose() {}
}

private enum class LatexViewState { COMPILING, PDF, ERROR, SOURCE }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LatexViewerScreen(
    instance: LatexViewerInstance,
    onBackPress: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var viewState by remember { mutableStateOf(LatexViewState.COMPILING) }
    var pdfPath by remember { mutableStateOf<String?>(null) }
    var errorLog by remember { mutableStateOf("") }
    var statusMessage by remember { mutableStateOf("Preparing LaTeX engine…") }
    var sourceContent by remember { mutableStateOf("") }

    var searchVisible by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var activeMatchIndex by remember { mutableStateOf(0) }
    var totalMatchesCount by remember { mutableStateOf(0) }
    val sourceScrollState = rememberScrollState()

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

    LaunchedEffect(searchQuery, viewState) {
        if (viewState == LatexViewState.SOURCE) {
            totalMatchesCount = totalSourceMatches
            activeMatchIndex = 0
        }
    }

    val density = androidx.compose.ui.platform.LocalDensity.current

    LaunchedEffect(activeMatchIndex, viewState, searchQuery) {
        if (viewState == LatexViewState.SOURCE && totalSourceMatches > 0 && searchQuery.length >= 2) {
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

    val annotatedSource = remember(sourceContent, searchQuery, activeMatchIndex, viewState) {
        if (searchQuery.length < 2 || viewState != LatexViewState.SOURCE) {
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

    val context = globalClass
    val filePath = remember {
        com.raival.compose.file.explorer.common.resolveUriToPath(context, instance.uri)
    }

    val fileName = remember { filePath.substringAfterLast('/') }

    // Load source
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            try {
                val file = File(filePath)
                if (file.exists()) {
                    sourceContent = file.readText()
                }
            } catch (_: Exception) {}
        }
    }

    // Compile function
    fun compile() {
        scope.launch {
            viewState = LatexViewState.COMPILING
            statusMessage = "Extracting Tectonic cache…"
            errorLog = ""

            try {
                // Extract tectonic cache
                TectonicCacheManager.ensureExtracted(context)
                statusMessage = "Compiling LaTeX…"

                // Create work directory
                val workDir = File(context.cacheDir, "latex_work_${System.currentTimeMillis()}")
                workDir.mkdirs()

                // Copy source file to workDir/input.tex to avoid scoped storage restrictions
                val localTexFile = File(workDir, "input.tex")
                context.contentResolver.openInputStream(instance.uri)?.use { input ->
                    localTexFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }

                // Run tectonic
                val result = NativeBinaryExecutor.run(
                    context = context,
                    binaryName = "libtectonic.so",
                    arguments = listOf("--only-cached", "--outdir", workDir.absolutePath, localTexFile.absolutePath),
                    workingDir = workDir.absolutePath
                )

                if (result.success) {
                    // Find generated PDF (input.pdf)
                    val pdfFile = File(workDir, "input.pdf")
                    if (pdfFile.exists()) {
                        pdfPath = pdfFile.absolutePath
                        viewState = LatexViewState.PDF
                    } else {
                        // Check for any PDF in workdir
                        val anyPdf = workDir.listFiles()?.find { it.extension == "pdf" }
                        if (anyPdf != null) {
                            pdfPath = anyPdf.absolutePath
                            viewState = LatexViewState.PDF
                        } else {
                            errorLog = "Compilation succeeded but no PDF was generated.\n\n${result.output}"
                            viewState = LatexViewState.ERROR
                        }
                    }
                } else {
                    errorLog = result.output
                    viewState = LatexViewState.ERROR
                }
            } catch (e: Exception) {
                errorLog = "Error: ${e.message}"
                viewState = LatexViewState.ERROR
            }
        }
    }

    // Initial compilation
    LaunchedEffect(Unit) {
        compile()
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
                    if (viewState == LatexViewState.SOURCE) {
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
                    }
                    if (viewState == LatexViewState.PDF || viewState == LatexViewState.ERROR || viewState == LatexViewState.SOURCE) {
                        IconButton(onClick = {
                            if (viewState == LatexViewState.SOURCE) {
                                searchVisible = false
                                searchQuery = ""
                                viewState = LatexViewState.PDF
                            } else {
                                // Open in text editor
                                val file = File(filePath)
                                if (file.exists()) {
                                    globalClass.textEditorManager.openTextEditor(
                                        LocalFileHolder(file),
                                        context
                                    )
                                } else {
                                    viewState = LatexViewState.SOURCE
                                }
                            }
                        }) {
                            Icon(Icons.Rounded.Code, contentDescription = "View Source")
                        }
                    }
                    IconButton(onClick = { compile() }) {
                        Icon(Icons.Rounded.Refresh, contentDescription = "Recompile")
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
            if (searchVisible && viewState == LatexViewState.SOURCE) {
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
                        Icon(androidx.compose.material.icons.Icons.Rounded.KeyboardArrowUp, contentDescription = "Previous")
                    }
                    IconButton(onClick = {
                        activeMatchIndex = (activeMatchIndex + 1) % maxOf(1, totalMatchesCount)
                    }) {
                        Icon(androidx.compose.material.icons.Icons.Rounded.KeyboardArrowDown, contentDescription = "Next")
                    }
                }
            }

            Box(modifier = Modifier.weight(1f)) {
                when (viewState) {
                    LatexViewState.COMPILING -> {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(48.dp))
                            Spacer(Modifier.height(16.dp))
                            Text(statusMessage, style = MaterialTheme.typography.bodyLarge)
                        }
                    }

                    LatexViewState.ERROR -> {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp)
                                .verticalScroll(rememberScrollState())
                        ) {
                            Text(
                                "Compilation Failed",
                                style = MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.colorScheme.error
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(
                                errorLog,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp
                            )
                            Spacer(Modifier.height(16.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = { compile() }) {
                                    Text("Retry")
                                }
                                Button(onClick = { viewState = LatexViewState.SOURCE }) {
                                    Text("View Source")
                                }
                            }
                        }
                    }

                    LatexViewState.SOURCE -> {
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
                    }

                LatexViewState.PDF -> {
                    val path = pdfPath
                    if (path != null) {
                        // Launch compiled PDF in PdfViewerActivity
                        LaunchedEffect(path) {
                            val pdfUri = FileProvider.getUriForFile(
                                context,
                                "com.raival.compose.file.explorer.provider",
                                File(path)
                            )
                            val intent = Intent(context, PdfViewerActivity::class.java).apply {
                                data = pdfUri
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(intent)
                            // Reset to source after launching so user can see the source
                            viewState = LatexViewState.SOURCE
                        }
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(48.dp))
                            Spacer(Modifier.height(16.dp))
                            Text("Opening PDF…", style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
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
