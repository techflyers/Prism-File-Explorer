package com.raival.compose.file.explorer.screen.viewer.pdf.ui

import android.content.Intent
import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.fragment.compose.AndroidFragment
import androidx.pdf.ExperimentalPdfApi
import androidx.pdf.view.PdfView
import com.raival.compose.file.explorer.App.Companion.globalClass
import com.raival.compose.file.explorer.R
import com.raival.compose.file.explorer.screen.viewer.pdf.CustomPdfViewerFragment
import com.raival.compose.file.explorer.screen.viewer.pdf.PdfViewerInstance
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalPdfApi::class)
@Composable
fun PdfViewerContent(instance: PdfViewerInstance, onBackPress: () -> Unit) {
    val context = LocalContext.current
    var showToolbars by remember { mutableStateOf(true) }
    var showInfoDialog by remember { mutableStateOf(false) }
    var showGoToPageDialog by remember { mutableStateOf(false) }
    var isSearchActive by remember { mutableStateOf(false) }

    // Page tracking
    var currentPage by remember { mutableIntStateOf(0) } // 0-based
    var totalPages by remember { mutableIntStateOf(0) }

    // Keep track of user interaction state to handle auto-hide
    var gestureState by remember { mutableIntStateOf(PdfView.GESTURE_STATE_IDLE) }

    // Counter incremented on every touch/scroll interaction to reset the 3s auto-hide timer
    var interactionCount by remember { mutableIntStateOf(0) }

    // Flag to ensure document loads only once
    var documentLoaded by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    var pdfFragment by remember { mutableStateOf<CustomPdfViewerFragment?>(null) }

    // ── Auto-hide control bars after 3 seconds of inactivity ──────────────────
    LaunchedEffect(showToolbars, gestureState, interactionCount) {
        if (showToolbars && gestureState == PdfView.GESTURE_STATE_IDLE) {
            delay(3000)
            showToolbars = false
        }
    }

    // Sync search state
    LaunchedEffect(isSearchActive, pdfFragment) {
        pdfFragment?.isTextSearchActive = isSearchActive
    }

    // ── Info bottom sheet ─────────────────────────────────────────────────────
    if (showInfoDialog) {
        InfoDialog(
            title = globalClass.getString(R.string.pdf_info),
            properties = instance.getInfo(),
            onDismiss = { showInfoDialog = false }
        )
    }

    // ── Go To Page Dialog ─────────────────────────────────────────────────────
    if (showGoToPageDialog && totalPages > 0) {
        GoToPageDialog(
            currentPage = currentPage + 1,
            totalPages = totalPages,
            onDismiss = { showGoToPageDialog = false },
            onConfirm = { page ->
                showGoToPageDialog = false
                pdfFragment?.scrollToPage(page - 1)
                currentPage = page - 1
                // Briefly keep toolbars visible when jumping pages
                showToolbars = true
                interactionCount++
            }
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopToolbar(
            visible = showToolbars,
            title = instance.metadata.name,
            onBackClick = onBackPress,
            onInfoClick = { showInfoDialog = true },
            onSearchClick = {
                isSearchActive = !isSearchActive
                interactionCount++
            },
            onShareClick = {
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/pdf"
                    putExtra(Intent.EXTRA_STREAM, instance.uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(
                    Intent.createChooser(shareIntent, globalClass.getString(R.string.share))
                )
            },
            onOpenWithClick = {
                val openIntent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(instance.uri, "application/pdf")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(
                    Intent.createChooser(openIntent, globalClass.getString(R.string.open_with))
                )
            }
        )

        // Container for the PDF view fragment & custom indicators
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxSize()
        ) {
            AndroidFragment<CustomPdfViewerFragment>(
                modifier = Modifier.fillMaxSize(),
                onUpdate = { fragment ->
                    pdfFragment = fragment

                    // Async load document once
                    if (!documentLoaded) {
                        val uriLoadStart = System.currentTimeMillis()
                        Log.d("CustomPdfViewer", "onUpdate: setDocumentUri start to ${instance.uri} (time=$uriLoadStart)")
                        fragment.documentUri = instance.uri
                        documentLoaded = true
                        Log.d("CustomPdfViewer", "onUpdate: setDocumentUri finished in ${System.currentTimeMillis() - uriLoadStart}ms")
                    }

                    // Listeners
                    fragment.onPageChanged = { page, total ->
                        currentPage = page
                        totalPages = total
                        // Show toolbars briefly on page scroll & reset timer
                        showToolbars = true
                        interactionCount++
                    }

                    fragment.onScrollDirection = { _ ->
                        // Any scroll/viewport change brings back toolbars & resets timer
                        showToolbars = true
                        interactionCount++
                    }

                    fragment.onGestureStateChanged = { state ->
                        gestureState = state
                        if (state == PdfView.GESTURE_STATE_INTERACTING) {
                            showToolbars = true
                            interactionCount++
                        }
                    }

                    fragment.onSingleTap = {
                        // Tap toggles controls & resets timer
                        showToolbars = !showToolbars
                        interactionCount++
                    }
                }
            )

            // ── Gorgeous Page Navigator Overlay ─────────────────────
            // zIndex(1f) is critical so it draws on top of native AndroidFragment.
            androidx.compose.animation.AnimatedVisibility(
                visible = showToolbars && totalPages > 0,
                enter = slideInVertically(initialOffsetY = { it / 2 }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it / 2 }) + fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(bottom = 24.dp)
                    .zIndex(1f)
            ) {
                Surface(
                    modifier = Modifier
                        .clip(RoundedCornerShape(24.dp))
                        .clickable { showGoToPageDialog = true },
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shadowElevation = 8.dp,
                    tonalElevation = 6.dp
                ) {
                    Text(
                        text = "${currentPage + 1} / $totalPages",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
                    )
                }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            instance.onClose()
        }
    }
}