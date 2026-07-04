package com.raival.compose.file.explorer.screen.viewer.pdf

import android.util.Log
import android.view.GestureDetector
import android.view.MotionEvent
import androidx.pdf.PdfDocument
import androidx.pdf.view.PdfView
import androidx.pdf.viewer.fragment.PdfViewerFragment
import androidx.pdf.ExperimentalPdfApi

/**
 * Subclass of [PdfViewerFragment] that exposes hooks into the PDF view lifecycle:
 *  - Current visible page tracking via [OnViewportChangedListener]
 *  - Scroll direction detection for toolbar auto-hide
 *  - Gesture state changes (idle/interacting/settling)
 *  - Document load success (with page count)
 *  - Programmatic navigation via [scrollToPage]
 *  - Scrollbar show/hide control via [FastScroller]
 *  - Tap detection to toggle toolbars
 */
@ExperimentalPdfApi
class CustomPdfViewerFragment : PdfViewerFragment() {

    companion object {
        private const val TAG = "CustomPdfViewer"
    }

    /** Called with (currentPage 0-based, totalPages) whenever visible page changes */
    var onPageChanged: ((currentPage: Int, totalPages: Int) -> Unit)? = null

    /** true = user scrolling down, false = user scrolling up */
    var onScrollDirection: ((scrollingDown: Boolean) -> Unit)? = null

    /**
     * GESTURE_STATE_IDLE = 0, GESTURE_STATE_INTERACTING = 1, GESTURE_STATE_SETTLING = 2
     * (constants on [PdfView])
     */
    var onGestureStateChanged: ((state: Int) -> Unit)? = null

    /** Fires when the very first page content is rendered (marks visible load complete) */
    var onFirstContentLoad: (() -> Unit)? = null

    /** Fires when a single tap is detected on the PDF */
    var onSingleTap: (() -> Unit)? = null

    var totalPages: Int = 0
        private set

    var currentPage: Int = 0
        private set

    private var pdfViewRef: PdfView? = null

    // ── PdfViewerFragment overrides ────────────────────────────────────────────

    override fun onPdfViewCreated(pdfView: PdfView) {
        val startTime = System.currentTimeMillis()
        super.onPdfViewCreated(pdfView)
        pdfViewRef = pdfView
        Log.d(TAG, "onPdfViewCreated start: pdfView initialized (time=$startTime)")

        // Revert to manual scroller hide/show as requested by user
        pdfView.fastScroller?.hide()

        var lastScrollTop = Float.MAX_VALUE
        var lastPage = -1

        // Use anonymous object to be 100% compatible with non-fun interface SAM conversions
        pdfView.addOnViewportChangedListener(object : PdfView.OnViewportChangedListener {
            override fun onViewportChanged(
                verticalOffset: Int,
                horizontalOffset: Int,
                visiblePages: android.util.SparseArray<android.graphics.RectF>,
                zoom: Float
            ) {
                if (visiblePages.size() > 0) {
                    val rectTop = visiblePages.valueAt(0)?.top ?: 0f
                    if (lastScrollTop != Float.MAX_VALUE) {
                        val delta = rectTop - lastScrollTop
                        if (delta < -8f) {
                            Log.d(TAG, "onViewportChanged: scrolling down (delta=$delta)")
                            onScrollDirection?.invoke(true)   // scrolling down
                        } else if (delta > 8f) {
                            Log.d(TAG, "onViewportChanged: scrolling up (delta=$delta)")
                            onScrollDirection?.invoke(false)  // scrolling up
                        }
                    }
                    lastScrollTop = rectTop

                    val page = visiblePages.keyAt(0)
                    currentPage = page
                    if (page != lastPage) {
                        Log.d(TAG, "onViewportChanged: page changed to $page")
                        lastPage = page
                        onPageChanged?.invoke(page, totalPages)
                    }
                }
            }
        })

        pdfView.addOnGestureStateChangedListener(object : PdfView.OnGestureStateChangedListener {
            override fun onGestureStateChanged(state: Int) {
                Log.d(TAG, "onGestureStateChanged: state=$state")
                onGestureStateChanged?.invoke(state)
                // Show scrollbar while interacting; hide when idle
                if (state == PdfView.GESTURE_STATE_INTERACTING) {
                    pdfView.fastScroller?.show { }
                } else if (state == PdfView.GESTURE_STATE_IDLE) {
                    pdfView.fastScroller?.hide()
                }
            }
        })

        Log.d(TAG, "onPdfViewCreated end: configuration completed in ${System.currentTimeMillis() - startTime}ms")

        pdfView.addOnFirstContentLoadListener(object : PdfView.OnFirstContentLoadListener {
            override fun onFirstContentLoad() {
                Log.d(TAG, "onFirstContentLoad")
                onFirstContentLoad?.invoke()
            }
        })

        // Tap detector to toggle toolbars
        val gestureDetector = GestureDetector(pdfView.context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                Log.d(TAG, "onSingleTapConfirmed")
                onSingleTap?.invoke()
                return false
            }
        })

        pdfView.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
            false // pass all touch events to child view for scrolling, zoom, text selection
        }
    }

    override fun onLoadDocumentSuccess(document: PdfDocument) {
        super.onLoadDocumentSuccess(document)
        totalPages = document.pageCount
        Log.d(TAG, "onLoadDocumentSuccess: totalPages=$totalPages")
        onPageChanged?.invoke(currentPage, totalPages)
    }

    override fun onLoadDocumentError(error: Throwable) {
        super.onLoadDocumentError(error)
        Log.e(TAG, "onLoadDocumentError: failed to load PDF", error)
    }

    /** Suppress the fragment's default system-UI immersive behaviour — we manage it ourselves */
    override fun onRequestImmersiveMode(immersive: Boolean) = Unit

    // ── Public API ─────────────────────────────────────────────────────────────

    /** Navigate to a zero-based page index */
    fun scrollToPage(pageIndex: Int) {
        Log.d(TAG, "scrollToPage: target pageIndex=$pageIndex")
        pdfViewRef?.scrollToPage(pageIndex)
    }
}
