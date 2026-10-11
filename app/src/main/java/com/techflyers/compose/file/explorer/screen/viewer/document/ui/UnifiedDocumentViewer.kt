package com.techflyers.compose.file.explorer.screen.viewer.document.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.print.PrintAttributes
import android.print.PrintManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.techflyers.compose.file.explorer.R
import com.techflyers.compose.file.explorer.common.ui.fastScrollbar
import com.techflyers.compose.file.explorer.office.OfficeEngine
import com.techflyers.compose.file.explorer.screen.viewer.document.DocumentViewerViewModel
import com.techflyers.compose.file.explorer.screen.viewer.document.core.PdfEditTool
import com.techflyers.compose.file.explorer.screen.viewer.document.core.PdfMarkup
import com.techflyers.compose.file.explorer.screen.viewer.document.selection.PdfSelectionHandles
import com.techflyers.compose.file.explorer.screen.viewer.document.selection.PdfTextSelectionState
import com.techflyers.compose.file.explorer.screen.viewer.document.selection.PdfViewportTransform
import com.techflyers.compose.file.explorer.screen.viewer.document.selection.pdfTextSelectionGestures
import com.techflyers.compose.file.explorer.screen.viewer.document.selection.rememberSelectionAutoScroller
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(FlowPreview::class)
@Composable
fun UnifiedDocumentViewer(
    viewModel: DocumentViewerViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current

    val listState = rememberLazyListState()
    var controlsVisible by remember { mutableStateOf(true) }

    // Zoom & Pan state
    var scale by remember { mutableFloatStateOf(1.0f) }
    var offsetX by remember { mutableFloatStateOf(0.0f) }
    var containerWidthPx by remember { mutableIntStateOf(0) }
    var containerHeightPx by remember { mutableIntStateOf(0) }

    // Old scrollbar auto-hide indicator state (independent of toolbars)
    var showIndicator by remember { mutableStateOf(false) }
    var lastInteractionTime by remember { mutableLongStateOf(0L) }

    // Text selection state
    val textSelection = remember { PdfTextSelectionState() }
    textSelection.transformProvider = {
        PdfViewportTransform(containerWidthPx.toFloat().coerceAtLeast(1f), scale, offsetX)
    }
    textSelection.blocksProvider = { page ->
        viewModel.getPageTextBlocks(page)
    }
    val autoScroller = rememberSelectionAutoScroller(listState, textSelection)
    autoScroller.panBy = { delta ->
        val maxOffsetX = ((containerWidthPx * scale - containerWidthPx) / 2f).coerceAtLeast(0f)
        val prev = offsetX
        offsetX = (offsetX + delta).coerceIn(-maxOffsetX, maxOffsetX)
        offsetX - prev
    }

    // Office Engine Installer state
    val installer = remember(context) { OfficeEngine.installer(context) }
    val engineState by installer.state.collectAsState()

    // Activity launcher for inserting images / stamps
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    val bmp = BitmapFactory.decodeStream(stream)
                    if (bmp != null) {
                        val markup = PdfMarkup.ImageMarkup(
                            id = System.currentTimeMillis(),
                            bitmap = bmp,
                            start = Offset(100f, 100f),
                            end = Offset(100f + bmp.width.coerceAtMost(300), 100f + bmp.height.coerceAtMost(300))
                        )
                        viewModel.addMarkup(state.currentPage, markup)
                    }
                }
            }
        }
    }

    // Page tracking
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .distinctUntilChanged()
            .collect { page ->
                viewModel.setCurrentPage(page)
                lastInteractionTime = System.currentTimeMillis()
                showIndicator = true
            }
    }

    // Auto-hide page indicator overlay after 2.5 seconds of inactivity (NOT tied to toolbar)
    LaunchedEffect(listState.isScrollInProgress) {
        if (listState.isScrollInProgress) {
            lastInteractionTime = System.currentTimeMillis()
            showIndicator = true
        }
    }

    LaunchedEffect(lastInteractionTime) {
        if (lastInteractionTime > 0L) {
            delay(2500)
            showIndicator = false
        }
    }

    // Auto-scroll effect
    LaunchedEffect(state.autoScroll, state.autoScrollSpeed) {
        if (state.autoScroll) {
            while (state.autoScroll) {
                listState.scrollBy(state.autoScrollSpeed * 2.5f)
                delay(16)
            }
        }
    }

    // Search scrolling
    LaunchedEffect(state.searchMatches, state.currentMatchIndex) {
        val match = state.searchMatches.getOrNull(state.currentMatchIndex)
        if (match != null) {
            listState.animateScrollToItem(match.pageIndex)
        }
    }

    // Smooth animate zoom and pan
    val animateZoomPan: suspend (Float, Offset) -> Unit = { targetZoom, targetPan ->
        val spec = spring<Float>(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow
        )
        scope.launch {
            androidx.compose.animation.core.animate(scale, targetZoom, animationSpec = spec) { v, _ -> scale = v }
        }
        scope.launch {
            androidx.compose.animation.core.animate(offsetX, targetPan.x, animationSpec = spec) { v, _ -> offsetX = v }
        }
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val screenWidthPx = with(density) { maxWidth.toPx().toInt() }

        LaunchedEffect(state.originalUri) {
            if (state.document == null && state.originalUri != null && !state.showOfficeDownloadPrompt) {
                viewModel.renderPagesAround(0, screenWidthPx)
            }
        }

        // Re-render visible pages at higher DPI when zoomed in for crisp sharpness
        LaunchedEffect(state.currentPage, screenWidthPx) {
            snapshotFlow { scale }
                .distinctUntilChanged()
                .debounce(300)
                .collect { zoom ->
                    if (zoom < 1.15f) return@collect
                    val desired = (screenWidthPx * zoom.coerceAtMost(3.0f))
                        .roundToInt()
                        .coerceAtMost(3840)
                        .coerceAtLeast(screenWidthPx)
                    if (desired > screenWidthPx) {
                        viewModel.renderPagesAround(state.currentPage, desired)
                    }
                }
        }

        // Main Content Area
        when {
            state.isLoading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(24.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(48.dp),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = state.loadingMessage,
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            state.error != null && state.document == null -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(32.dp)
                    ) {
                        Text(
                            text = "Error Loading Document",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = state.error!!,
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            state.document != null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .onSizeChanged {
                            containerWidthPx = it.width
                            containerHeightPx = it.height
                        }
                        // Long press to select text
                        .pdfTextSelectionGestures(
                            state = textSelection,
                            autoScroller = autoScroller,
                            haptics = haptics,
                            enabled = { state.activeTool == PdfEditTool.None },
                            onSelectionStarted = {
                                lastInteractionTime = System.currentTimeMillis()
                            }
                        )
                        // Pinch to zoom and pan gestures
                        .pointerInput(state.activeTool) {
                            if (state.activeTool != PdfEditTool.None) return@pointerInput
                            awaitEachGesture {
                                awaitFirstDown(requireUnconsumed = false)
                                do {
                                    val event = awaitPointerEvent()
                                    if (textSelection.gestureActive) continue
                                    val zoomChange = event.calculateZoom()
                                    val panChange = event.calculatePan()
                                    val zoomed = scale > 1.001f
                                    if (zoomChange != 1f || zoomed) {
                                        val cw = size.width.toFloat()
                                        val oldScale = scale
                                        val newScale = (oldScale * zoomChange).coerceIn(1f, 5f)
                                        val effZoom = if (oldScale != 0f) newScale / oldScale else 1f
                                        val centroid = event.calculateCentroid(useCurrent = true)
                                        val center = cw / 2f
                                        val maxOffsetX = ((cw * newScale - cw) / 2f).coerceAtLeast(0f)
                                        scale = newScale
                                        if (newScale > 1f) {
                                            val focalDx = if (centroid.isSpecified) (1f - effZoom) * (centroid.x - center - offsetX) else 0f
                                            offsetX = (offsetX + focalDx + panChange.x).coerceIn(-maxOffsetX, maxOffsetX)
                                            val focalDy = if (centroid.isSpecified) centroid.y * (1f / oldScale - 1f / newScale) else 0f
                                            val totalDy = focalDy + (-panChange.y / newScale)
                                            if (totalDy != 0f) listState.dispatchRawDelta(totalDy)
                                        } else {
                                            offsetX = 0f
                                        }
                                        lastInteractionTime = System.currentTimeMillis()
                                    }
                                } while (event.changes.any { it.pressed })
                            }
                        }
                        // Tap & Double tap gestures
                        .pointerInput(state.activeTool) {
                            detectTapGestures(
                                onTap = {
                                    if (textSelection.hasSelection) {
                                        textSelection.clear()
                                    } else if (state.activeTool == PdfEditTool.None) {
                                        controlsVisible = !controlsVisible
                                    }
                                    lastInteractionTime = System.currentTimeMillis()
                                },
                                onDoubleTap = { tap ->
                                    if (state.activeTool != PdfEditTool.None) return@detectTapGestures
                                    val cw = size.width.toFloat()
                                    val s0 = scale
                                    val target = if (s0 > 1.4f) 1f else 2.5f
                                    val maxOffsetX = ((cw * target - cw) / 2f).coerceAtLeast(0f)
                                    val focalX = if (target > 1f) ((cw / 2f - tap.x) * (target - 1f)).coerceIn(-maxOffsetX, maxOffsetX) else 0f
                                    val scrollDelta = tap.y * (1f / s0 - 1f / target)
                                    val focalSpec = spring<Float>(
                                        dampingRatio = Spring.DampingRatioNoBouncy,
                                        stiffness = Spring.StiffnessMediumLow
                                    )
                                    scope.launch { animateZoomPan(target, Offset(focalX, 0f)) }
                                    scope.launch { listState.animateScrollBy(scrollDelta, focalSpec) }
                                    lastInteractionTime = System.currentTimeMillis()
                                }
                            )
                        }
                ) {
                    // Zoomable transformed layer
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                                translationX = offsetX
                                translationY = 0f
                                transformOrigin = TransformOrigin(0.5f, 0f)
                            }
                            .onGloballyPositioned { textSelection.registerLayer(it) }
                    ) {
                        LazyColumn(
                            state = listState,
                            userScrollEnabled = state.activeTool == PdfEditTool.None && scale <= 1.01f,
                            modifier = Modifier
                                .fillMaxSize()
                                .background(if (state.darkPageAppearance) Color(0xFF121212) else MaterialTheme.colorScheme.surface)
                                .fastScrollbar(listState)
                        ) {
                            item {
                                Spacer(Modifier.height(84.dp))
                            }

                            items(state.pageCount, key = { it }) { pageIndex ->
                                val bitmap = viewModel.pageBitmaps[pageIndex]
                                val aspect = viewModel.pageAspectRatios[pageIndex] ?: (1f / 1.414f)
                                val markups = viewModel.pageMarkups[pageIndex] ?: emptyList()
                                val pageMatches = state.searchMatches.filter { it.pageIndex == pageIndex }
                                val textBlocks = viewModel.pageTextBlocks[pageIndex] ?: emptyList()

                                DocumentContinuousPage(
                                    pageIndex = pageIndex,
                                    bitmap = bitmap,
                                    aspectRatio = aspect,
                                    darkPageAppearance = state.darkPageAppearance,
                                    markups = markups,
                                    searchMatches = pageMatches,
                                    currentMatchIndex = state.currentMatchIndex,
                                    allMatches = state.searchMatches,
                                    textBlocks = textBlocks,
                                    textSelection = textSelection,
                                    activeTool = state.activeTool,
                                    currentColor = state.currentColor,
                                    currentStrokeWidth = state.currentStrokeWidth,
                                    onTap = {
                                        if (textSelection.hasSelection) {
                                            textSelection.clear()
                                        } else if (state.activeTool == PdfEditTool.None) {
                                            controlsVisible = !controlsVisible
                                        }
                                        lastInteractionTime = System.currentTimeMillis()
                                    },
                                    onAddMarkup = { markup ->
                                        viewModel.addMarkup(pageIndex, markup)
                                    },
                                    onPlaceText = { offset ->
                                        val textMarkup = PdfMarkup.TextBoxMarkup(
                                            id = System.currentTimeMillis(),
                                            position = offset,
                                            text = "Tap to edit text",
                                            color = state.currentColor
                                        )
                                        viewModel.addMarkup(pageIndex, textMarkup)
                                        viewModel.setActiveTool(PdfEditTool.None)
                                    },
                                    onPlaceNote = { offset ->
                                        val noteMarkup = PdfMarkup.StickyNoteMarkup(
                                            id = System.currentTimeMillis(),
                                            position = offset,
                                            text = "New note"
                                        )
                                        viewModel.addMarkup(pageIndex, noteMarkup)
                                        viewModel.setActiveTool(PdfEditTool.None)
                                    },
                                    onEditNote = { _ -> }
                                )
                            }

                            item {
                                Spacer(Modifier.height(140.dp))
                            }
                        }
                    }

                    // Native text selection handles overlay (outside zoom layer to keep intrinsic handle size)
                    PdfSelectionHandles(
                        state = textSelection,
                        listState = listState,
                        autoScroller = autoScroller
                    )
                }
            }
        }

        // Floating Page Indicator Pill from old viewer (auto shows on scroll, auto disappears after 2.5s)
        AnimatedVisibility(
            visible = showIndicator && state.pageCount > 0,
            enter = slideInVertically(initialOffsetY = { it / 2 }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it / 2 }) + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(bottom = if (controlsVisible) 96.dp else 24.dp)
                .zIndex(2f)
        ) {
            Surface(
                modifier = Modifier
                    .clip(RoundedCornerShape(24.dp))
                    .clickable { viewModel.setShowJumpDialog(true) },
                color = MaterialTheme.colorScheme.surfaceVariant,
                shadowElevation = 8.dp,
                tonalElevation = 6.dp
            ) {
                Text(
                    text = "${state.currentPage + 1} / ${state.pageCount}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
                )
            }
        }

        // Top Bar
        DocumentTopBar(
            title = state.fileName,
            pageCount = state.pageCount,
            currentPage = state.currentPage,
            renderedByLibreOffice = state.renderedByLibreOffice,
            isBookmarked = state.currentPage in state.bookmarkedPages,
            visible = controlsVisible && !state.showSearch,
            onBack = onBack,
            onSearchClick = { viewModel.toggleSearch() },
            onBookmarkToggle = { viewModel.toggleBookmark(state.currentPage) },
            onAskAiClick = { viewModel.setShowAiSheet(true) },
            onInfoClick = { viewModel.setShowInfoDialog(true) },
            onOpenWithClick = {
                val uri = state.originalUri ?: state.activePdfUri ?: return@DocumentTopBar
                val name = state.fileName
                val mime = when {
                    name.endsWith(".pdf", ignoreCase = true) -> "application/pdf"
                    name.endsWith(".docx", ignoreCase = true) -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                    name.endsWith(".doc", ignoreCase = true) -> "application/msword"
                    name.endsWith(".pptx", ignoreCase = true) -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
                    name.endsWith(".ppt", ignoreCase = true) -> "application/vnd.ms-powerpoint"
                    name.endsWith(".xlsx", ignoreCase = true) -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                    name.endsWith(".xls", ignoreCase = true) -> "application/vnd.ms-excel"
                    else -> "*/*"
                }
                val openIntent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, mime)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(openIntent, context.getString(R.string.open_with)))
            },
            onShareClick = {
                val uri = state.activePdfUri ?: state.originalUri ?: return@DocumentTopBar
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/pdf"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(shareIntent, "Share document"))
            },
            onPrintClick = {
                val uri = state.activePdfUri ?: return@DocumentTopBar
                runCatching {
                    val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager
                    val adapter = com.techflyers.compose.file.explorer.screen.viewer.document.core.PdfPrintDocumentAdapter(context, uri, state.fileName)
                    printManager?.print(state.fileName, adapter, PrintAttributes.Builder().build())
                }
            },
            modifier = Modifier.align(Alignment.TopCenter)
        )

        // Floating Search Bar
        DocumentFloatingSearchBar(
            visible = state.showSearch,
            query = state.searchQuery,
            matchCount = state.searchMatches.size,
            currentMatchIndex = state.currentMatchIndex,
            onQueryChange = { viewModel.setSearchQuery(it) },
            onPrevMatch = { viewModel.prevMatch() },
            onNextMatch = { viewModel.nextMatch() },
            onClose = { viewModel.toggleSearch() },
            modifier = Modifier.align(Alignment.TopCenter)
        )

        // Floating Bottom Ribbon
        DocumentBottomRibbon(
            activeTool = state.activeTool,
            onSelectTool = { viewModel.setActiveTool(it) },
            currentColor = state.currentColor,
            onColorSelected = { viewModel.setColor(it) },
            currentStrokeWidth = state.currentStrokeWidth,
            onStrokeWidthChange = { viewModel.setStrokeWidth(it) },
            canUndo = state.canUndo,
            onUndo = { viewModel.undo() },
            onClearPage = {
                viewModel.pageMarkups[state.currentPage]?.clear()
            },
            isNightMode = state.darkPageAppearance,
            onToggleNightMode = { viewModel.toggleDarkPage() },
            isAutoScrolling = state.autoScroll,
            autoScrollSpeed = state.autoScrollSpeed,
            onToggleAutoScroll = { viewModel.toggleAutoScroll() },
            onAutoScrollSpeedChange = { viewModel.setAutoScrollSpeed(it) },
            onZoomIn = {
                scope.launch {
                    val target = (scale + 0.25f).coerceAtMost(4f)
                    animateZoomPan(target, Offset(offsetX, 0f))
                }
            },
            onZoomOut = {
                scope.launch {
                    val target = (scale - 0.25f).coerceAtLeast(1f)
                    animateZoomPan(target, if (target <= 1f) Offset.Zero else Offset(offsetX, 0f))
                }
            },
            onResetZoom = {
                scope.launch { animateZoomPan(1f, Offset.Zero) }
            },
            onAskAiClick = { viewModel.setShowAiSheet(true) },
            onSearchClick = { viewModel.toggleSearch() },
            onSignaturePadClick = { viewModel.setShowSignatureDialog(true) },
            onPickImageClick = { imagePickerLauncher.launch("image/*") },
            onShareClick = {
                val uri = state.activePdfUri ?: state.originalUri ?: return@DocumentBottomRibbon
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/pdf"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(shareIntent, "Share document"))
            },
            onInfoClick = { viewModel.setShowInfoDialog(true) },
            showLibreOfficeHint = state.isOfficeDocument && !state.renderedByLibreOffice,
            onDownloadLibreOffice = { viewModel.setShowOfficeDownloadPrompt(true) },
            visible = controlsVisible,
            modifier = Modifier.align(Alignment.BottomCenter)
        )

        // Text Selection Floating Toolbar
        DocumentSelectionToolbar(
            visible = textSelection.hasSelection && !textSelection.gestureActive,
            onCopy = {
                val txt = textSelection.selectedText()
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                clipboard?.setPrimaryClip(ClipData.newPlainText("Document text", txt))
                textSelection.clear()
            },
            onHighlight = { colorArgb ->
                textSelection.rangesByPage().forEach { (page, ranges) ->
                    ranges.forEach { range ->
                        val markup = PdfMarkup.TextBlockHighlightMarkup(
                            blockId = range.blockId,
                            color = Color(colorArgb.toULong()),
                            alpha = 0.38f,
                            start = range.start,
                            end = range.end
                        )
                        viewModel.addMarkup(page, markup)
                    }
                }
                textSelection.clear()
            },
            onUnderline = {
                textSelection.rangesByPage().forEach { (page, ranges) ->
                    ranges.forEach { range ->
                        val markup = PdfMarkup.TextBlockLineMarkup(
                            blockId = range.blockId,
                            color = Color(0xFF007AFF),
                            strikeThrough = false,
                            start = range.start,
                            end = range.end
                        )
                        viewModel.addMarkup(page, markup)
                    }
                }
                textSelection.clear()
            },
            onStrike = {
                textSelection.rangesByPage().forEach { (page, ranges) ->
                    ranges.forEach { range ->
                        val markup = PdfMarkup.TextBlockLineMarkup(
                            blockId = range.blockId,
                            color = Color(0xFFFF3B30),
                            strikeThrough = true,
                            start = range.start,
                            end = range.end
                        )
                        viewModel.addMarkup(page, markup)
                    }
                }
                textSelection.clear()
            },
            onAskAi = {
                val txt = textSelection.selectedText()
                viewModel.setShowAiSheet(true, txt)
                textSelection.clear()
            },
            onShare = {
                val txt = textSelection.selectedText()
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, txt)
                }
                context.startActivity(Intent.createChooser(shareIntent, "Share text"))
                textSelection.clear()
            },
            onSearch = {
                val txt = textSelection.selectedText().trim()
                viewModel.setSearchQuery(txt)
                if (!state.showSearch) viewModel.toggleSearch()
                textSelection.clear()
            },
            onDismiss = {
                textSelection.clear()
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = if (controlsVisible) 96.dp else 32.dp)
                .zIndex(3f)
        )
    }

    // ── Dialogs & Bottom Sheets ──────────────────────────────────────────────

    // AI Assistant Sheet
    DocumentAiSheet(
        visible = state.showAiSheet,
        documentTitle = state.fileName,
        fullText = state.fullText,
        pagesText = state.pagesText,
        selectedText = state.selectedText,
        onDismiss = { viewModel.setShowAiSheet(false) }
    )

    // Signature Pad Dialog
    DocumentSignatureDialog(
        visible = state.showSignatureDialog,
        onDismiss = { viewModel.setShowSignatureDialog(false) },
        onSignatureCaptured = { signatureBitmap ->
            val stamp = PdfMarkup.ImageMarkup(
                id = System.currentTimeMillis(),
                bitmap = signatureBitmap,
                start = Offset(150f, 250f),
                end = Offset(150f + signatureBitmap.width.coerceAtMost(350), 250f + signatureBitmap.height.coerceAtMost(200)),
                isSignature = true
            )
            viewModel.addMarkup(state.currentPage, stamp)
        }
    )

    // Document Info Dialog
    DocumentInfoDialog(
        visible = state.showInfoDialog,
        title = state.fileName,
        filePath = state.originalUri?.path,
        fileSizeString = state.fileSizeString,
        pageCount = state.pageCount,
        renderedByLibreOffice = state.renderedByLibreOffice,
        isEncrypted = state.isEncrypted,
        onDismiss = { viewModel.setShowInfoDialog(false) }
    )

    // Jump to Page Dialog
    DocumentJumpToPageDialog(
        visible = state.showJumpDialog,
        currentPage = state.currentPage,
        pageCount = state.pageCount,
        bookmarkedPages = state.bookmarkedPages.toList().sorted(),
        onJumpToPage = { page ->
            scope.launch { listState.scrollToItem(page) }
        },
        onToggleBookmark = { viewModel.toggleBookmark(state.currentPage) },
        onDismiss = { viewModel.setShowJumpDialog(false) }
    )

    // Password Unlock Dialog
    DocumentPasswordDialog(
        visible = state.showPasswordDialog,
        errorMessage = state.error,
        onSubmit = { password -> viewModel.unlockPasswordProtected(password) },
        onCancel = {
            viewModel.setShowPasswordDialog(false)
            onBack()
        }
    )

    // LibreOffice On-Demand Download Prompt Dialog
    LibreOfficeDownloadDialog(
        visible = state.showOfficeDownloadPrompt,
        engineState = engineState,
        onDownloadClick = {
            viewModel.onDownloadEngineConfirmed()
        },
        onCancelDownload = {
            installer.cancel()
            viewModel.setShowOfficeDownloadPrompt(false)
        },
        onUseFallbackClick = {
            viewModel.onDismissDownloadPromptAndUseFallback()
        },
        onReloadWithEngine = {
            viewModel.setShowOfficeDownloadPrompt(false)
            val uri = state.originalUri ?: return@LibreOfficeDownloadDialog
            viewModel.loadDocument(uri, preferLibreOffice = true)
        },
        onDismiss = {
            viewModel.onDismissDownloadPromptAndUseFallback()
        }
    )
}
