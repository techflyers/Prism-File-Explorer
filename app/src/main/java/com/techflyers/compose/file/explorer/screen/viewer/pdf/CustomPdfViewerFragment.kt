package com.techflyers.compose.file.explorer.screen.viewer.pdf

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.Log
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.pdf.PdfDocument
import androidx.pdf.PdfPoint
import androidx.pdf.PdfRect
import androidx.pdf.Highlight
import androidx.pdf.view.PdfView
import androidx.pdf.viewer.fragment.PdfViewerFragment
import androidx.pdf.ExperimentalPdfApi
import com.techflyers.compose.file.explorer.screen.viewer.pdf.operations.PdfStrokeItem

data class UnderlineItem(val rect: PdfRect, val colorArgb: Int)
data class StrikeItem(val rect: PdfRect, val colorArgb: Int)
data class StampedImageItem(val pageNum: Int, val bitmap: Bitmap, val x: Float, val y: Float, val width: Float, val height: Float)

/**
 * Subclass of [PdfViewerFragment] that exposes hooks into the PDF view lifecycle:
 *  - Current visible page tracking via [OnViewportChangedListener]
 *  - Scroll direction detection for toolbar auto-hide
 *  - Gesture state changes (idle/interacting/settling)
 *  - Document load success (with page count)
 *  - Programmatic navigation via [scrollToPage]
 *  - Scrollbar show/hide control via [FastScroller]
 *  - Tap detection to toggle toolbars
 *  - Dynamic, in-place page updating WITHOUT full document reload:
 *      Highlights via native [PdfView.setHighlights]
 *      Underlines, Strikethroughs, freehand drawings, and stamps via overlay view
 *  - Strict suppression of scrollbar page number indicators
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

    /** Fires when text selection is updated on the PDF canvas */
    var onTextSelected: ((text: String) -> Unit)? = null

    /** Fires when text selection is updated with exact bounding rectangles and screen anchor */
    var onTextSelectedWithBounds: ((text: String, bounds: List<PdfRect>, anchorPoint: androidx.compose.ui.geometry.Offset) -> Unit)? = null

    /** Screen anchor point where user touched/selected */
    var lastTouchPoint: androidx.compose.ui.geometry.Offset? = null
        private set

    /** The latest text selection bounding boxes */
    var currentSelectionBounds: List<PdfRect> = emptyList()
        private set

    /** Fires when text selection is cleared */
    var onSelectionCleared: (() -> Unit)? = null

    var totalPages: Int = 0
        private set

    var currentPage: Int = 0
        private set

    /** Lock scrolling/touch gestures when true (e.g. during free-transform stamp overlay or drawing) */
    var isScrollLocked: Boolean = false
        set(value) {
            field = value
            pdfViewRef?.let { pv ->
                if (value) {
                    pv.fastScroller?.hide()
                    pv.parent?.requestDisallowInterceptTouchEvent(true)
                }
            }
        }

    private var pdfViewRef: PdfView? = null
    private var overlayView: View? = null
    private var currentZoom: Float = 1f

    // In-memory active markups for instant in-place page updates
    private val activeHighlights = mutableListOf<Highlight>()
    private val activeUnderlines = mutableListOf<UnderlineItem>()
    private val activeStrikes = mutableListOf<StrikeItem>()
    private val activeStrokes = mutableListOf<Pair<Int, PdfStrokeItem>>()
    private val activeStamps = mutableListOf<StampedImageItem>()

    private val scrollHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val hideScrollbarRunnable = Runnable {
        pdfViewRef?.let { pdfView ->
            pdfView.fastScroller?.hide()
            pdfView.postInvalidate()
        }
    }

    // ── PdfViewerFragment overrides ────────────────────────────────────────────

    override fun onPdfViewCreated(pdfView: PdfView) {
        val startTime = System.currentTimeMillis()
        super.onPdfViewCreated(pdfView)
        pdfViewRef = pdfView
        Log.d(TAG, "onPdfViewCreated start: pdfView initialized (time=$startTime)")

        // Initially hide fast scroller until user scrolls
        pdfView.fastScroller?.hide()
        pdfView.postInvalidate()

        // Create overlay View to render dynamic real-time annotations without document reload
        val overlay = object : View(pdfView.context) {
            private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            private val strokePath = Path()
            private val dstRect = RectF()
            private val pdfPos = IntArray(2)
            private val overlayPos = IntArray(2)

            override fun onTouchEvent(event: MotionEvent?): Boolean = false

            override fun onDraw(canvas: Canvas) {
                super.onDraw(canvas)
                val pv = pdfViewRef ?: return

                pv.getLocationInWindow(pdfPos)
                getLocationInWindow(overlayPos)
                val dx = (pdfPos[0] - overlayPos[0]).toFloat()
                val dy = (pdfPos[1] - overlayPos[1]).toFloat()

                canvas.save()
                if (dx != 0f || dy != 0f) {
                    canvas.translate(dx, dy)
                }

                // 1. Draw Underlines
                paint.style = Paint.Style.STROKE
                paint.strokeCap = Paint.Cap.ROUND
                paint.strokeWidth = 3.5f * currentZoom.coerceIn(1f, 3f)
                for (item in activeUnderlines) {
                    val r = item.rect
                    val p1 = pv.pdfToViewPoint(PdfPoint(r.pageNum, r.left, r.bottom))
                    val p2 = pv.pdfToViewPoint(PdfPoint(r.pageNum, r.right, r.bottom))
                    if (p1 != null && p2 != null) {
                        paint.color = item.colorArgb
                        canvas.drawLine(p1.x, p1.y, p2.x, p2.y, paint)
                    }
                }

                // 2. Draw Strikethroughs
                for (item in activeStrikes) {
                    val r = item.rect
                    val midY = (r.top + r.bottom) / 2f
                    val p1 = pv.pdfToViewPoint(PdfPoint(r.pageNum, r.left, midY))
                    val p2 = pv.pdfToViewPoint(PdfPoint(r.pageNum, r.right, midY))
                    if (p1 != null && p2 != null) {
                        paint.color = item.colorArgb
                        canvas.drawLine(p1.x, p1.y, p2.x, p2.y, paint)
                    }
                }

                // 3. Draw freehand drawn strokes
                paint.style = Paint.Style.STROKE
                paint.strokeCap = Paint.Cap.ROUND
                paint.strokeJoin = Paint.Join.ROUND
                for ((pageNum, stroke) in activeStrokes) {
                    if (stroke.points.size < 2) continue
                    paint.color = stroke.colorArgb
                    paint.strokeWidth = stroke.strokeWidth * currentZoom.coerceIn(0.8f, 3.5f)
                    val p0 = pv.pdfToViewPoint(PdfPoint(pageNum, stroke.points[0].x, stroke.points[0].y)) ?: continue
                    strokePath.reset()
                    strokePath.moveTo(p0.x, p0.y)
                    for (i in 1 until stroke.points.size) {
                        val pt = pv.pdfToViewPoint(PdfPoint(pageNum, stroke.points[i].x, stroke.points[i].y)) ?: continue
                        strokePath.lineTo(pt.x, pt.y)
                    }
                    canvas.drawPath(strokePath, paint)
                }

                // 4. Draw Stamped Bitmaps (Signatures, Images)
                paint.style = Paint.Style.FILL
                for (stamp in activeStamps) {
                    val p1 = pv.pdfToViewPoint(PdfPoint(stamp.pageNum, stamp.x, stamp.y)) ?: continue
                    val p2 = pv.pdfToViewPoint(PdfPoint(stamp.pageNum, stamp.x + stamp.width, stamp.y + stamp.height)) ?: continue
                    dstRect.set(p1.x, p1.y, p2.x, p2.y)
                    canvas.drawBitmap(stamp.bitmap, null, dstRect, null)
                }

                canvas.restore()
            }
        }.apply {
            isClickable = false
            isFocusable = false
        }
        overlayView = overlay

        fun attachOverlayToParent() {
            if (overlay.parent != null) return
            val parentGroup = (pdfView.parent as? ViewGroup) ?: (view as? ViewGroup)
            if (parentGroup != null) {
                parentGroup.addView(
                    overlay,
                    ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                )
                overlay.bringToFront()
            }
        }

        attachOverlayToParent()
        pdfView.post { attachOverlayToParent() }

        pdfView.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            overlayView?.postInvalidate()
        }

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
                currentZoom = zoom
                overlayView?.postInvalidate()

                // Ensure scrollbar is visible during scrolling and schedule auto-hide after scrolling stops
                scrollHandler.removeCallbacks(hideScrollbarRunnable)
                pdfView.fastScroller?.show { pdfView.postInvalidate() }
                scrollHandler.postDelayed(hideScrollbarRunnable, 1500L)

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

                    val vh = pdfView.height.toFloat().takeIf { it > 0 } ?: Float.MAX_VALUE
                    var bestPage = visiblePages.keyAt(0)
                    var maxVisibleH = -1f
                    for (i in 0 until visiblePages.size()) {
                        val p = visiblePages.keyAt(i)
                        val rect = visiblePages.valueAt(i) ?: continue
                        val visTop = maxOf(0f, rect.top)
                        val visBottom = minOf(vh, rect.bottom)
                        val visH = visBottom - visTop
                        if (visH > maxVisibleH) {
                            maxVisibleH = visH
                            bestPage = p
                        }
                    }

                    currentPage = bestPage
                    if (bestPage != lastPage) {
                        Log.d(TAG, "onViewportChanged: page changed to $bestPage")
                        lastPage = bestPage
                        onPageChanged?.invoke(bestPage, totalPages)
                    }
                }
            }
        })

        pdfView.addOnGestureStateChangedListener(object : PdfView.OnGestureStateChangedListener {
            override fun onGestureStateChanged(state: Int) {
                Log.d(TAG, "onGestureStateChanged: state=$state")
                onGestureStateChanged?.invoke(state)
                if (state == PdfView.GESTURE_STATE_INTERACTING || state == PdfView.GESTURE_STATE_SETTLING) {
                    scrollHandler.removeCallbacks(hideScrollbarRunnable)
                    pdfView.fastScroller?.show { pdfView.postInvalidate() }
                } else if (state == PdfView.GESTURE_STATE_IDLE) {
                    scrollHandler.removeCallbacks(hideScrollbarRunnable)
                    scrollHandler.postDelayed(hideScrollbarRunnable, 1200L)
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

        // Text selection listener
        pdfView.addOnSelectionChangedListener(object : PdfView.OnSelectionChangedListener {
            override fun onSelectionChanged(selection: androidx.pdf.selection.Selection?) {
                Log.d(TAG, "onSelectionChanged: selection=$selection")
                if (selection is androidx.pdf.selection.model.TextSelection) {
                    val text = selection.text?.toString()?.trim() ?: ""
                    val bounds = selection.bounds ?: emptyList()
                    if (text.isNotEmpty() && bounds.isNotEmpty()) {
                        currentSelectionBounds = bounds
                        onTextSelected?.invoke(text)
                        val anchor = lastTouchPoint ?: androidx.compose.ui.geometry.Offset.Zero
                        onTextSelectedWithBounds?.invoke(text, bounds, anchor)
                    } else {
                        currentSelectionBounds = emptyList()
                        onSelectionCleared?.invoke()
                    }
                } else {
                    currentSelectionBounds = emptyList()
                    onSelectionCleared?.invoke()
                }
            }
        })

        // Tap detector to toggle toolbars
        val gestureDetector = GestureDetector(pdfView.context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean = false
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                Log.d(TAG, "onSingleTapConfirmed")
                onSingleTap?.invoke()
                return false
            }
        }).apply {
            setIsLongpressEnabled(false) // Allow long-press to pass to PdfView for text selection
        }

        pdfView.setOnTouchListener { v, event ->
            if (isScrollLocked) {
                v.parent?.requestDisallowInterceptTouchEvent(true)
                return@setOnTouchListener true
            }
            if (event.action == MotionEvent.ACTION_DOWN || event.action == MotionEvent.ACTION_MOVE) {
                lastTouchPoint = androidx.compose.ui.geometry.Offset(event.x, event.y)
            }
            gestureDetector.onTouchEvent(event)
            false // pass all touch events to child view for scrolling, zoom, text selection
        }
    }

    override fun onDestroyView() {
        scrollHandler.removeCallbacks(hideScrollbarRunnable)
        super.onDestroyView()
        (overlayView?.parent as? ViewGroup)?.removeView(overlayView)
        overlayView = null
        pdfViewRef = null
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

    /** Clear current text or image selection */
    fun clearSelection() {
        pdfViewRef?.clearCurrentSelection()
        currentSelectionBounds = emptyList()
    }

    /**
     * Add a highlight dynamically to the active PDF page without reloading the document.
     */
    fun addHighlight(rect: PdfRect, colorArgb: Int) {
        activeHighlights.add(Highlight(rect, colorArgb))
        pdfViewRef?.setHighlights(activeHighlights.toList())
        pdfViewRef?.postInvalidate()
    }

    /**
     * Add an underline dynamically to the active PDF page without reloading the document.
     */
    fun addUnderline(rect: PdfRect, colorArgb: Int) {
        activeUnderlines.add(UnderlineItem(rect, colorArgb))
        overlayView?.postInvalidate()
    }

    /**
     * Add a strikethrough dynamically to the active PDF page without reloading the document.
     */
    fun addStrikethrough(rect: PdfRect, colorArgb: Int) {
        activeStrikes.add(StrikeItem(rect, colorArgb))
        overlayView?.postInvalidate()
    }

    /**
     * Add freehand drawn strokes dynamically to the active PDF page without reloading the document.
     */
    fun addStrokeItem(pageNum: Int, stroke: PdfStrokeItem) {
        activeStrokes.add(pageNum to stroke)
        overlayView?.postInvalidate()
    }

    /**
     * Stamp a bitmap (signature or image) dynamically to the active PDF page without reloading the document.
     */
    fun addStampItem(pageNum: Int, bitmap: Bitmap, x: Float, y: Float, width: Float, height: Float) {
        activeStamps.add(StampedImageItem(pageNum, bitmap, x, y, width, height))
        overlayView?.postInvalidate()
    }

    /**
     * Convert view screen point to PDF coordinates on the current visible page.
     */
    fun viewToPdfPoint(x: Float, y: Float): PdfPoint? {
        return pdfViewRef?.viewToPdfPoint(x, y)
    }

    /**
     * Clear all active in-memory markups (highlights, underlines, strikes, strokes, stamps).
     */
    fun clearAllMarkups() {
        activeHighlights.clear()
        activeUnderlines.clear()
        activeStrikes.clear()
        activeStrokes.clear()
        activeStamps.clear()
        pdfViewRef?.setHighlights(emptyList())
        pdfViewRef?.postInvalidate()
        overlayView?.postInvalidate()
    }
}
