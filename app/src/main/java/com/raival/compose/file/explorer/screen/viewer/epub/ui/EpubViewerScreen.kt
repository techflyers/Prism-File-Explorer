package com.raival.compose.file.explorer.screen.viewer.epub.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.NavigateBefore
import androidx.compose.material.icons.automirrored.rounded.NavigateNext
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.FormatAlignJustify
import androidx.compose.material.icons.rounded.FormatAlignLeft
import androidx.compose.material.icons.rounded.FormatSize
import androidx.compose.material.icons.rounded.List
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.raival.compose.file.explorer.R
import com.raival.compose.file.explorer.ebook.EbookBookmark
import com.raival.compose.file.explorer.ebook.EbookTocItem
import com.raival.compose.file.explorer.epub.EpubAssetStreamer
import com.raival.compose.file.explorer.epub.EpubTheme
import com.raival.compose.file.explorer.screen.viewer.epub.EpubViewerInstance
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EpubViewerScreen(
    instance: EpubViewerInstance,
    onBackPress: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(instance.uri) {
        instance.load(context)
    }

    if (instance.isLoading) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }
        return
    }

    if (instance.errorMessage != null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = instance.errorMessage ?: "Unknown error",
                    fontSize = 16.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(16.dp))
                TextButton(onClick = onBackPress) {
                    Text("Go Back")
                }
            }
        }
        return
    }

    val book = instance.book ?: return
    val chapters = book.chapters
    if (chapters.isEmpty()) return

    var showControls by remember { mutableStateOf(true) }
    var showTocSheet by remember { mutableStateOf(false) }
    var showSettingsSheet by remember { mutableStateOf(false) }
    var showThemeMenu by remember { mutableStateOf(false) }
    var tocSelectedTab by remember { mutableIntStateOf(0) } // 0 = Contents, 1 = Bookmarks

    val currentChapter = instance.currentChapter ?: return

    val currentTheme = instance.theme
    val backgroundColor = when (currentTheme) {
        EpubTheme.LIGHT -> Color.White
        EpubTheme.DARK -> Color(0xFF1E1E24)
        EpubTheme.AMOLED -> Color.Black
        EpubTheme.SEPIA -> Color(0xFFF8F1E3)
    }
    val contentColor = when (currentTheme) {
        EpubTheme.LIGHT -> Color(0xFF1C1B1F)
        EpubTheme.DARK -> Color(0xFFE6E1E5)
        EpubTheme.AMOLED -> Color(0xFFD8D8D8)
        EpubTheme.SEPIA -> Color(0xFF4F321C)
    }

    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var currentScrollProgress by remember { mutableFloatStateOf(0f) }

    // Save reading position when exiting
    DisposableEffect(Unit) {
        onDispose {
            webViewRef?.evaluateJavascript("window.getScrollProgress();") { res ->
                val progress = res?.toFloatOrNull() ?: currentScrollProgress
                instance.savePosition(context, progress)
            }
        }
    }

    // Reload content whenever chapter or reader preferences change
    val styledHtml = remember(
        instance.currentChapterIndex,
        instance.theme,
        instance.fontSizePercent,
        instance.fontFamily,
        instance.lineHeight,
        instance.isPaged,
        instance.marginHorizontal,
        instance.textAlign
    ) {
        instance.getCurrentChapterStyledHtml() ?: "<html><body><p>Error loading chapter</p></body></html>"
    }

    LaunchedEffect(styledHtml, webViewRef) {
        webViewRef?.let { wv ->
            val baseUrl = "https://prism.reader.local/${currentChapter.id}"
            wv.loadDataWithBaseURL(baseUrl, styledHtml, "text/html", "UTF-8", null)
        }
    }

    // Function to save position and change chapter
    fun changeChapter(newIndex: Int) {
        webViewRef?.evaluateJavascript("window.getScrollProgress();") { res ->
            val progress = res?.toFloatOrNull() ?: 0f
            instance.savePosition(context, progress)
            instance.currentChapterIndex = newIndex
            instance.initialScrollPercentage = 0f
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
    ) {
        // WebView Reader
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                WebView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = false
                    settings.allowFileAccess = false
                    settings.builtInZoomControls = false
                    settings.displayZoomControls = false
                    setBackgroundColor(android.graphics.Color.parseColor(currentTheme.bgHex))

                    setFindListener { activeMatchOrdinal, numberOfMatches, isDoneCounting ->
                        if (isDoneCounting) {
                            instance.searchMatchCount = numberOfMatches
                            instance.currentMatchIndex = if (numberOfMatches > 0) activeMatchOrdinal + 1 else 0
                        }
                    }

                    webViewClient = object : WebViewClient() {
                        override fun shouldInterceptRequest(
                            view: WebView?,
                            request: WebResourceRequest?
                        ): WebResourceResponse? {
                            val url = request?.url?.toString() ?: return null
                            return EpubAssetStreamer.intercept(book, currentChapter, url)
                        }

                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            if (instance.initialScrollPercentage > 0f) {
                                view?.evaluateJavascript(
                                    "window.restoreScrollProgress(${instance.initialScrollPercentage});",
                                    null
                                )
                                instance.initialScrollPercentage = 0f
                            }
                        }

                        override fun shouldOverrideUrlLoading(
                            view: WebView?,
                            request: WebResourceRequest?
                        ): Boolean {
                            val target = request?.url?.toString() ?: return false
                            if (target.contains('#')) {
                                val anchor = target.substringAfterLast('#')
                                view?.evaluateJavascript("location.hash = '$anchor';", null)
                                return true
                            }
                            return false
                        }
                    }

                    // Touch listener for controls toggle & paged tap zones
                    var downX = 0f
                    var downY = 0f
                    setOnTouchListener { v, event ->
                        when (event.action) {
                            android.view.MotionEvent.ACTION_DOWN -> {
                                downX = event.x
                                downY = event.y
                            }
                            android.view.MotionEvent.ACTION_UP -> {
                                val diffX = kotlin.math.abs(event.x - downX)
                                val diffY = kotlin.math.abs(event.y - downY)
                                if (diffX < 15 && diffY < 15) {
                                    val width = v.width
                                    if (instance.isPaged) {
                                        when {
                                            event.x < width * 0.25f -> {
                                                evaluateJavascript("window.pageTurn(false);", null)
                                            }
                                            event.x > width * 0.75f -> {
                                                evaluateJavascript("window.pageTurn(true);", null)
                                            }
                                            else -> {
                                                showControls = !showControls
                                            }
                                        }
                                    } else {
                                        showControls = !showControls
                                    }
                                }
                            }
                        }
                        false
                    }

                    webViewRef = this
                }
            },
            update = { wv ->
                wv.setBackgroundColor(android.graphics.Color.parseColor(currentTheme.bgHex))
            }
        )

        // Floating Top App Bar + Optional Search Bar
        AnimatedVisibility(
            visible = showControls,
            enter = slideInVertically { -it } + fadeIn(),
            exit = slideOutVertically { -it } + fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Surface(
                color = backgroundColor.copy(alpha = 0.95f),
                contentColor = contentColor,
                shadowElevation = 4.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.statusBarsPadding()) {
                    TopAppBar(
                        title = {
                            Column {
                                Text(
                                    text = book.title,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = contentColor
                                )
                                val sub = if (!book.author.isNullOrBlank()) book.author else "Chapter ${instance.currentChapterIndex + 1} of ${chapters.size}"
                                Text(
                                    text = sub ?: "",
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    fontSize = 12.sp,
                                    color = contentColor.copy(alpha = 0.7f)
                                )
                            }
                        },
                        navigationIcon = {
                            IconButton(onClick = onBackPress) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                    contentDescription = "Back",
                                    tint = contentColor
                                )
                            }
                        },
                        actions = {
                            // Find in Chapter
                            IconButton(onClick = {
                                instance.isSearchActive = !instance.isSearchActive
                                if (!instance.isSearchActive) {
                                    webViewRef?.clearMatches()
                                    instance.searchQuery = ""
                                }
                            }) {
                                Icon(
                                    imageVector = Icons.Rounded.Search,
                                    contentDescription = "Find in Chapter",
                                    tint = if (instance.isSearchActive) MaterialTheme.colorScheme.primary else contentColor
                                )
                            }

                            // Add Bookmark
                            IconButton(onClick = {
                                webViewRef?.evaluateJavascript("window.getScrollProgress();") { res ->
                                    val progress = res?.toFloatOrNull() ?: 0f
                                    instance.addBookmark(context, progress)
                                    Toast.makeText(context, "Bookmark added", Toast.LENGTH_SHORT).show()
                                }
                            }) {
                                Icon(
                                    imageVector = Icons.Rounded.BookmarkAdd,
                                    contentDescription = "Add Bookmark",
                                    tint = contentColor
                                )
                            }

                            // Theme Selector
                            Box {
                                IconButton(onClick = { showThemeMenu = true }) {
                                    Icon(
                                        imageVector = Icons.Rounded.Palette,
                                        contentDescription = "Theme",
                                        tint = contentColor
                                    )
                                }
                                DropdownMenu(
                                    expanded = showThemeMenu,
                                    onDismissRequest = { showThemeMenu = false }
                                ) {
                                    EpubTheme.entries.forEach { t ->
                                        DropdownMenuItem(
                                            text = {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(18.dp)
                                                            .clip(CircleShape)
                                                            .background(Color(android.graphics.Color.parseColor(t.bgHex)))
                                                            .border(1.dp, Color.Gray, CircleShape)
                                                    )
                                                    Spacer(Modifier.width(8.dp))
                                                    Text(
                                                        text = when (t) {
                                                            EpubTheme.LIGHT -> "Light"
                                                            EpubTheme.DARK -> "Dark"
                                                            EpubTheme.AMOLED -> "AMOLED Black"
                                                            EpubTheme.SEPIA -> "Sepia"
                                                        },
                                                        fontWeight = if (instance.theme == t) FontWeight.Bold else FontWeight.Normal
                                                    )
                                                }
                                            },
                                            onClick = {
                                                instance.updateTheme(context, t)
                                                showThemeMenu = false
                                            }
                                        )
                                    }
                                }
                            }

                            // Typography & Layout Settings
                            IconButton(onClick = { showSettingsSheet = true }) {
                                Icon(
                                    imageVector = Icons.Rounded.FormatSize,
                                    contentDescription = "Reading Settings",
                                    tint = contentColor
                                )
                            }

                            // Table of Contents & Bookmarks
                            IconButton(onClick = { showTocSheet = true }) {
                                Icon(
                                    imageVector = Icons.Rounded.List,
                                    contentDescription = "Table of Contents",
                                    tint = contentColor
                                )
                            }

                            // Open With
                            IconButton(onClick = {
                                val openIntent = Intent(Intent.ACTION_VIEW).apply {
                                    setDataAndType(instance.uri, "application/epub+zip")
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(openIntent, context.getString(R.string.open_with)))
                            }) {
                                Icon(
                                    imageVector = Icons.Rounded.OpenInNew,
                                    contentDescription = "Open With",
                                    tint = contentColor
                                )
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                    )

                    // In-Book Search Bar
                    AnimatedVisibility(visible = instance.isSearchActive) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp)
                        ) {
                            OutlinedTextField(
                                value = instance.searchQuery,
                                onValueChange = { query ->
                                    instance.searchQuery = query
                                    if (query.isNotEmpty()) {
                                        webViewRef?.findAllAsync(query)
                                    } else {
                                        webViewRef?.clearMatches()
                                        instance.searchMatchCount = 0
                                        instance.currentMatchIndex = 0
                                    }
                                },
                                placeholder = { Text("Search in chapter...", fontSize = 14.sp) },
                                singleLine = true,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(52.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    unfocusedBorderColor = contentColor.copy(alpha = 0.3f)
                                )
                            )

                            if (instance.searchMatchCount > 0) {
                                Text(
                                    text = "${instance.currentMatchIndex}/${instance.searchMatchCount}",
                                    fontSize = 12.sp,
                                    color = contentColor.copy(alpha = 0.7f),
                                    modifier = Modifier.padding(horizontal = 8.dp)
                                )
                            }

                            IconButton(
                                onClick = { webViewRef?.findNext(false) },
                                enabled = instance.searchMatchCount > 0
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.NavigateBefore,
                                    contentDescription = "Previous Match",
                                    tint = contentColor
                                )
                            }

                            IconButton(
                                onClick = { webViewRef?.findNext(true) },
                                enabled = instance.searchMatchCount > 0
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.NavigateNext,
                                    contentDescription = "Next Match",
                                    tint = contentColor
                                )
                            }

                            IconButton(onClick = {
                                instance.isSearchActive = false
                                webViewRef?.clearMatches()
                                instance.searchQuery = ""
                            }) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = "Close Search",
                                    tint = contentColor
                                )
                            }
                        }
                    }
                }
            }
        }

        // Floating Bottom Chapter Navigation Bar
        AnimatedVisibility(
            visible = showControls,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Surface(
                color = backgroundColor.copy(alpha = 0.95f),
                contentColor = contentColor,
                shadowElevation = 4.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        IconButton(
                            onClick = {
                                if (instance.currentChapterIndex > 0) {
                                    changeChapter(instance.currentChapterIndex - 1)
                                }
                            },
                            enabled = instance.currentChapterIndex > 0
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.NavigateBefore,
                                contentDescription = "Previous Chapter",
                                tint = if (instance.currentChapterIndex > 0) contentColor else contentColor.copy(alpha = 0.3f)
                            )
                        }

                        Text(
                            text = "Chapter ${instance.currentChapterIndex + 1} of ${chapters.size} • ${currentChapter.title}",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = contentColor,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .weight(1f)
                                .clickable { showTocSheet = true }
                                .padding(vertical = 4.dp)
                        )

                        IconButton(
                            onClick = {
                                if (instance.currentChapterIndex < chapters.size - 1) {
                                    changeChapter(instance.currentChapterIndex + 1)
                                }
                            },
                            enabled = instance.currentChapterIndex < chapters.size - 1
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.NavigateNext,
                                contentDescription = "Next Chapter",
                                tint = if (instance.currentChapterIndex < chapters.size - 1) contentColor else contentColor.copy(alpha = 0.3f)
                            )
                        }
                    }
                }
            }
        }

        // Reader Typography & Layout Settings BottomSheet
        if (showSettingsSheet) {
            val settingsSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ModalBottomSheet(
                onDismissRequest = { showSettingsSheet = false },
                sheetState = settingsSheetState
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = "Reader Preferences",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    // 1. Reading Mode (Paged vs Continuous)
                    Text("Reading Mode", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = !instance.isPaged,
                            onClick = { instance.updatePaged(context, false) },
                            label = { Text("Continuous Scroll") },
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = instance.isPaged,
                            onClick = { instance.updatePaged(context, true) },
                            label = { Text("Paged Flip") },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(Modifier.height(8.dp))

                    // 2. Font Size
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Font Size", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.weight(1f))
                        Text("${instance.fontSizePercent}%", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        TextButton(onClick = { instance.updateFontSize(context, (instance.fontSizePercent - 10).coerceAtLeast(70)) }) {
                            Text("A-", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                        Slider(
                            value = instance.fontSizePercent.toFloat(),
                            onValueChange = { instance.updateFontSize(context, it.toInt()) },
                            valueRange = 70f..250f,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = { instance.updateFontSize(context, (instance.fontSizePercent + 10).coerceAtMost(250)) }) {
                            Text("A+", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    // 3. Font Family
                    Text("Typeface", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val fonts = listOf("sans-serif" to "Sans", "serif" to "Serif", "monospace" to "Mono")
                        fonts.forEach { (family, label) ->
                            FilterChip(
                                selected = instance.fontFamily == family,
                                onClick = { instance.updateFontFamily(context, family) },
                                label = { Text(label) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    // 4. Line Spacing & Text Alignment
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Line Spacing", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Row(modifier = Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                val spacings = listOf(1.3f to "1.3", 1.65f to "1.65", 2.0f to "2.0")
                                spacings.forEach { (spc, label) ->
                                    FilterChip(
                                        selected = kotlin.math.abs(instance.lineHeight - spc) < 0.1f,
                                        onClick = { instance.updateLineHeight(context, spc) },
                                        label = { Text(label) }
                                    )
                                }
                            }
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text("Alignment", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Row(modifier = Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                FilterChip(
                                    selected = instance.textAlign == "justify",
                                    onClick = { instance.updateTextAlign(context, "justify") },
                                    label = { Icon(Icons.Rounded.FormatAlignJustify, contentDescription = "Justify", Modifier.size(18.dp)) }
                                )
                                FilterChip(
                                    selected = instance.textAlign == "left",
                                    onClick = { instance.updateTextAlign(context, "left") },
                                    label = { Icon(Icons.Rounded.FormatAlignLeft, contentDescription = "Left", Modifier.size(18.dp)) }
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(24.dp))
                }
            }
        }

        // Table of Contents & Bookmarks BottomSheet
        if (showTocSheet) {
            val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
            ModalBottomSheet(
                onDismissRequest = { showTocSheet = false },
                sheetState = sheetState
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    TabRow(selectedTabIndex = tocSelectedTab) {
                        Tab(
                            selected = tocSelectedTab == 0,
                            onClick = { tocSelectedTab = 0 },
                            text = { Text("Table of Contents") }
                        )
                        Tab(
                            selected = tocSelectedTab == 1,
                            onClick = { tocSelectedTab = 1 },
                            text = { Text("Bookmarks") }
                        )
                    }

                    Spacer(Modifier.height(12.dp))

                    if (tocSelectedTab == 0) {
                        // TOC List
                        val tocList = if (book.toc.isNotEmpty()) book.toc else {
                            chapters.mapIndexed { i, c ->
                                EbookTocItem(
                                    title = c.title,
                                    href = c.id,
                                    chapterIndex = i
                                )
                            }
                        }

                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(440.dp)
                        ) {
                            itemsIndexed(tocList) { _, item ->
                                val isCurrent = item.chapterIndex == instance.currentChapterIndex
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(
                                            if (isCurrent) MaterialTheme.colorScheme.primaryContainer
                                            else Color.Transparent
                                        )
                                        .clickable {
                                            changeChapter(item.chapterIndex.coerceIn(0, chapters.size - 1))
                                            coroutineScope.launch {
                                                sheetState.hide()
                                                showTocSheet = false
                                            }
                                        }
                                        .padding(vertical = 12.dp, horizontal = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = item.title,
                                        fontSize = 15.sp,
                                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isCurrent) MaterialTheme.colorScheme.onPrimaryContainer
                                        else MaterialTheme.colorScheme.onSurface,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    } else {
                        // Bookmarks List
                        var bookmarks by remember { mutableStateOf(instance.getBookmarks(context)) }
                        val dateFormat = remember { SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()) }

                        if (bookmarks.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(300.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "No bookmarks yet.\nTap the bookmark icon in the top bar to save a page.",
                                    textAlign = TextAlign.Center,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(440.dp)
                            ) {
                                itemsIndexed(bookmarks) { _, bookmark ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable {
                                                instance.currentChapterIndex = bookmark.chapterIndex.coerceIn(0, chapters.size - 1)
                                                instance.initialScrollPercentage = bookmark.scrollPercentage
                                                coroutineScope.launch {
                                                    sheetState.hide()
                                                    showTocSheet = false
                                                }
                                            }
                                            .padding(vertical = 10.dp, horizontal = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Bookmark,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.padding(end = 12.dp)
                                        )
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = bookmark.title,
                                                fontSize = 15.sp,
                                                fontWeight = FontWeight.Medium,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            val scrollPercent = (bookmark.scrollPercentage * 100).toInt()
                                            Text(
                                                text = "Chapter ${bookmark.chapterIndex + 1} ($scrollPercent%) • ${dateFormat.format(Date(bookmark.timestamp))}",
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        IconButton(onClick = {
                                            instance.removeBookmark(context, bookmark)
                                            bookmarks = instance.getBookmarks(context)
                                        }) {
                                            Icon(
                                                imageVector = Icons.Rounded.Delete,
                                                contentDescription = "Remove Bookmark",
                                                tint = MaterialTheme.colorScheme.error
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
