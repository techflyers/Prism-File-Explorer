package com.techflyers.compose.file.explorer.screen.viewer.document.selection

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect

/**
 * Maps coordinates between the document layer (unscaled, inside graphicsLayer) and screen space
 * (un-transformed viewer overlay containing selection handles and toolbars).
 *
 * Page column sits in graphicsLayer with scaleX = scaleY = scale, translationX = offsetX,
 * translationY = 0, and transformOrigin = (0.5, 0).
 */
data class PdfViewportTransform(
    val containerWidth: Float,
    val scale: Float,
    val offsetX: Float
) {
    private val cx get() = containerWidth / 2f
    private val s get() = if (scale <= 0f) 1f else scale

    fun layerToScreen(p: Offset): Offset =
        Offset((p.x - cx) * s + cx + offsetX, p.y * s)

    fun screenToLayer(p: Offset): Offset =
        Offset((p.x - cx - offsetX) / s + cx, p.y / s)

    fun pageToScreen(pagePoint: Offset, pageOrigin: Offset): Offset =
        layerToScreen(pagePoint + pageOrigin)

    fun screenToPage(screenPoint: Offset, pageOrigin: Offset): Offset =
        screenToLayer(screenPoint) - pageOrigin

    fun pageRectToScreen(r: Rect, pageOrigin: Offset): Rect {
        val tl = pageToScreen(r.topLeft, pageOrigin)
        val br = pageToScreen(r.bottomRight, pageOrigin)
        return Rect(tl, br)
    }

    fun toScreenLength(pageLength: Float): Float = pageLength * s
}
