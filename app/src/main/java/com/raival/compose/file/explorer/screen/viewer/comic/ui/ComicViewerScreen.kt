package com.raival.compose.file.explorer.screen.viewer.comic.ui

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.automirrored.rounded.NavigateBefore
import androidx.compose.material.icons.automirrored.rounded.NavigateNext
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.FitScreen
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.raival.compose.file.explorer.R
import com.raival.compose.file.explorer.screen.viewer.comic.ComicFitMode
import com.raival.compose.file.explorer.screen.viewer.comic.ComicReadingDirection
import com.raival.compose.file.explorer.screen.viewer.comic.ComicViewerInstance
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import me.saket.telephoto.zoomable.coil3.ZoomableAsyncImage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComicViewerScreen(
    instance: ComicViewerInstance,
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
                .background(Color.Black),
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
                .background(Color.Black)
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = instance.errorMessage ?: "Unknown error",
                    color = Color.White,
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

    val pageCount = instance.pageCount
    if (pageCount == 0) return

    val pagerState = rememberPagerState(
        initialPage = instance.currentPage.coerceIn(0, pageCount - 1),
        pageCount = { pageCount }
    )

    var showControls by remember { mutableStateOf(true) }
    var showFitMenu by remember { mutableStateOf(false) }
    var showBookmarksMenu by remember { mutableStateOf(false) }
    var showJumpDialog by remember { mutableStateOf(false) }
    var showThumbnailsSheet by remember { mutableStateOf(false) }

    // Sync pager position with instance state
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }
            .distinctUntilChanged()
            .collect { page ->
                instance.currentPage = page
            }
    }

    val isRtl = instance.readingDirection == ComicReadingDirection.RTL

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Main Horizontal Pager
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            reverseLayout = isRtl,
            beyondViewportPageCount = 1,
            key = { it }
        ) { pageIndex ->
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                val pageData = remember(pageIndex) {
                    instance.getPageBytes(pageIndex)
                }

                val contentScale = when (instance.fitMode) {
                    ComicFitMode.FIT_WIDTH -> ContentScale.FillWidth
                    ComicFitMode.FIT_HEIGHT -> ContentScale.FillHeight
                    ComicFitMode.FIT_WHOLE -> ContentScale.Fit
                }

                if (pageData != null) {
                    ZoomableAsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(pageData)
                            .build(),
                        contentDescription = "Page ${pageIndex + 1}",
                        contentScale = contentScale,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    CircularProgressIndicator(
                        modifier = Modifier.size(36.dp),
                        color = Color.White.copy(alpha = 0.5f)
                    )
                }
            }
        }

        // Tap zones for navigation: Left third (prev), Center third (toggle UI), Right third (next)
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val totalWidth = maxWidth
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(pageCount, isRtl) {
                        detectTapGestures(
                            onTap = { offset ->
                                val xRatio = offset.x / totalWidth.toPx()
                                when {
                                    xRatio < 0.33f -> {
                                        // Left tap
                                        val targetPage = if (isRtl) pagerState.currentPage + 1 else pagerState.currentPage - 1
                                        if (targetPage in 0 until pageCount) {
                                            coroutineScope.launch {
                                                pagerState.animateScrollToPage(targetPage)
                                            }
                                        }
                                    }
                                    xRatio > 0.67f -> {
                                        // Right tap
                                        val targetPage = if (isRtl) pagerState.currentPage - 1 else pagerState.currentPage + 1
                                        if (targetPage in 0 until pageCount) {
                                            coroutineScope.launch {
                                                pagerState.animateScrollToPage(targetPage)
                                            }
                                        }
                                    }
                                    else -> {
                                        // Center tap: toggle controls
                                        showControls = !showControls
                                    }
                                }
                            }
                        )
                    }
            )
        }

        // Floating Top App Bar
        AnimatedVisibility(
            visible = showControls,
            enter = slideInVertically { -it } + fadeIn(),
            exit = slideOutVertically { -it } + fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Surface(
                color = Color.Black.copy(alpha = 0.75f),
                contentColor = Color.White,
                modifier = Modifier.fillMaxWidth()
            ) {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = instance.title,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )
                            Text(
                                text = "Page ${pagerState.currentPage + 1} of $pageCount",
                                fontSize = 12.sp,
                                color = Color.White.copy(alpha = 0.7f)
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBackPress) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = "Back",
                                tint = Color.White
                            )
                        }
                    },
                    actions = {
                        // Fit Mode Toggle
                        Box {
                            IconButton(onClick = { showFitMenu = true }) {
                                Icon(
                                    imageVector = Icons.Rounded.FitScreen,
                                    contentDescription = "Fit Mode",
                                    tint = Color.White
                                )
                            }
                            DropdownMenu(
                                expanded = showFitMenu,
                                onDismissRequest = { showFitMenu = false }
                            ) {
                                ComicFitMode.entries.forEach { mode ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = mode.label,
                                                fontWeight = if (instance.fitMode == mode) FontWeight.Bold else FontWeight.Normal
                                            )
                                        },
                                        onClick = {
                                            instance.fitMode = mode
                                            showFitMenu = false
                                        }
                                    )
                                }
                            }
                        }

                        // Reading Direction Toggle (LTR vs RTL)
                        IconButton(onClick = {
                            instance.readingDirection = if (isRtl) ComicReadingDirection.LTR else ComicReadingDirection.RTL
                        }) {
                            Icon(
                                imageVector = Icons.Rounded.SwapHoriz,
                                contentDescription = "Reading Direction: ${instance.readingDirection.label}",
                                tint = if (isRtl) MaterialTheme.colorScheme.primary else Color.White
                            )
                        }

                        // Bookmarks Menu (if available)
                        if (instance.bookmarks.isNotEmpty()) {
                            Box {
                                IconButton(onClick = { showBookmarksMenu = true }) {
                                    Icon(
                                        imageVector = Icons.Rounded.Bookmark,
                                        contentDescription = "Bookmarks",
                                        tint = Color.White
                                    )
                                }
                                DropdownMenu(
                                    expanded = showBookmarksMenu,
                                    onDismissRequest = { showBookmarksMenu = false }
                                ) {
                                    instance.bookmarks.forEach { bookmark ->
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    text = "${bookmark.title} (Page ${bookmark.pageIndex + 1})"
                                                )
                                            },
                                            onClick = {
                                                coroutineScope.launch {
                                                    pagerState.scrollToPage(bookmark.pageIndex)
                                                }
                                                showBookmarksMenu = false
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        // Open With external app
                        IconButton(onClick = {
                            val openIntent = Intent(Intent.ACTION_VIEW).apply {
                                setDataAndType(instance.uri, "application/octet-stream")
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(openIntent, context.getString(R.string.open_with)))
                        }) {
                            Icon(
                                imageVector = Icons.Rounded.OpenInNew,
                                contentDescription = "Open With",
                                tint = Color.White
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                    modifier = Modifier.statusBarsPadding()
                )
            }
        }

        // Floating Bottom Scrubber Bar
        AnimatedVisibility(
            visible = showControls,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Surface(
                color = Color.Black.copy(alpha = 0.75f),
                contentColor = Color.White,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    var sliderPosition by remember(pagerState.currentPage) {
                        mutableFloatStateOf(pagerState.currentPage.toFloat())
                    }

                    // Scrubber row
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // Previous Page button
                        IconButton(
                            onClick = {
                                val prev = pagerState.currentPage - 1
                                if (prev >= 0) {
                                    coroutineScope.launch { pagerState.animateScrollToPage(prev) }
                                }
                            },
                            enabled = pagerState.currentPage > 0
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.NavigateBefore,
                                contentDescription = "Previous Page",
                                tint = if (pagerState.currentPage > 0) Color.White else Color.White.copy(alpha = 0.3f)
                            )
                        }

                        // Slider
                        Slider(
                            value = sliderPosition,
                            onValueChange = { sliderPosition = it },
                            onValueChangeFinished = {
                                val target = sliderPosition.toInt().coerceIn(0, pageCount - 1)
                                coroutineScope.launch {
                                    pagerState.scrollToPage(target)
                                }
                            },
                            valueRange = 0f..(pageCount - 1).toFloat(),
                            modifier = Modifier.weight(1f)
                        )

                        // Next Page button
                        IconButton(
                            onClick = {
                                val next = pagerState.currentPage + 1
                                if (next < pageCount) {
                                    coroutineScope.launch { pagerState.animateScrollToPage(next) }
                                }
                            },
                            enabled = pagerState.currentPage < pageCount - 1
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.NavigateNext,
                                contentDescription = "Next Page",
                                tint = if (pagerState.currentPage < pageCount - 1) Color.White else Color.White.copy(alpha = 0.3f)
                            )
                        }

                        // Grid overview button
                        IconButton(onClick = { showThumbnailsSheet = true }) {
                            Icon(
                                imageVector = Icons.Rounded.GridView,
                                contentDescription = "Page Grid",
                                tint = Color.White
                            )
                        }
                    }

                    // Page indicator & jump button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${pagerState.currentPage + 1} / $pageCount",
                            fontSize = 13.sp,
                            color = Color.White.copy(alpha = 0.8f),
                            modifier = Modifier
                                .clickable { showJumpDialog = true }
                                .padding(vertical = 4.dp, horizontal = 8.dp)
                        )

                        Spacer(Modifier.weight(1f))

                        Text(
                            text = if (isRtl) "Manga (RTL)" else "Standard (LTR)",
                            fontSize = 11.sp,
                            color = Color.White.copy(alpha = 0.5f)
                        )
                    }
                }
            }
        }

        // Jump to Page Dialog
        if (showJumpDialog) {
            var inputPage by remember { mutableStateOf("${pagerState.currentPage + 1}") }
            AlertDialog(
                onDismissRequest = { showJumpDialog = false },
                title = { Text("Jump to Page") },
                text = {
                    Column {
                        Text("Enter a page number (1 - $pageCount):", fontSize = 14.sp)
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = inputPage,
                            onValueChange = { inputPage = it },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            keyboardActions = KeyboardActions(onDone = {
                                val p = inputPage.toIntOrNull()
                                if (p != null && p in 1..pageCount) {
                                    coroutineScope.launch { pagerState.scrollToPage(p - 1) }
                                    showJumpDialog = false
                                }
                            })
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        val p = inputPage.toIntOrNull()
                        if (p != null && p in 1..pageCount) {
                            coroutineScope.launch { pagerState.scrollToPage(p - 1) }
                            showJumpDialog = false
                        }
                    }) {
                        Text("Jump")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showJumpDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }

        // Page Grid Overview Sheet
        if (showThumbnailsSheet) {
            val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ModalBottomSheet(
                onDismissRequest = { showThumbnailsSheet = false },
                sheetState = sheetState
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = "Pages ($pageCount)",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 64.dp),
                        modifier = Modifier.height(380.dp)
                    ) {
                        items(pageCount) { index ->
                            val isSelected = index == pagerState.currentPage
                            Box(
                                modifier = Modifier
                                    .padding(4.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (isSelected) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.surfaceVariant
                                    )
                                    .clickable {
                                        coroutineScope.launch {
                                            pagerState.scrollToPage(index)
                                            sheetState.hide()
                                            showThumbnailsSheet = false
                                        }
                                    }
                                    .height(64.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "${index + 1}",
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
