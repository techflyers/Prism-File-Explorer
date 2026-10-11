package com.techflyers.compose.file.explorer.screen.viewer.document.core

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color

enum class ScrollOrientation { Vertical, Horizontal }

enum class PdfEditTool {
    None, Draw, Highlight, Rect, Ellipse, Line, Arrow, Image, Text, Note, Eraser
}

sealed class PdfMarkup {
    data class StrokeMarkup(
        val points: List<Offset>,
        val color: Color,
        val width: Float,
        val alpha: Float = 1f
    ) : PdfMarkup()

    data class RectMarkup(
        val start: Offset,
        val end: Offset,
        val color: Color,
        val alpha: Float = 1f,
        val filled: Boolean = false
    ) : PdfMarkup()

    data class OvalMarkup(
        val start: Offset,
        val end: Offset,
        val color: Color,
        val alpha: Float = 1f,
        val filled: Boolean = false
    ) : PdfMarkup()

    data class LineMarkup(
        val start: Offset,
        val end: Offset,
        val color: Color,
        val width: Float = 3f,
        val alpha: Float = 1f,
        val arrowHead: Boolean = false
    ) : PdfMarkup()

    data class TextBlockHighlightMarkup(
        val blockId: String,
        val color: Color,
        val alpha: Float = 0.35f,
        val start: Int = 0,
        val end: Int = -1
    ) : PdfMarkup()

    data class TextBlockLineMarkup(
        val blockId: String,
        val color: Color,
        val width: Float = 3f,
        val alpha: Float = 1f,
        val strikeThrough: Boolean = false,
        val start: Int = 0,
        val end: Int = -1
    ) : PdfMarkup()

    data class ImageMarkup(
        val id: Long,
        val bitmap: Bitmap,
        val start: Offset,
        val end: Offset,
        val isSignature: Boolean = false
    ) : PdfMarkup()

    data class TextBoxMarkup(
        val id: Long,
        val position: Offset,
        val text: String,
        val color: Color = Color.Black,
        val fontSize: Float = 16f
    ) : PdfMarkup()

    data class StickyNoteMarkup(
        val id: Long,
        val position: Offset,
        val text: String,
        val color: Color = Color(0xFFFFD54F)
    ) : PdfMarkup()
}

data class NormalizedPoint(val x: Float, val y: Float)

sealed class ExportOverlay {
    data class Stroke(
        val points: List<NormalizedPoint>,
        val colorArgb: Int,
        val widthNorm: Float,
        val alpha: Float
    ) : ExportOverlay()

    data class RectShape(
        val start: NormalizedPoint,
        val end: NormalizedPoint,
        val colorArgb: Int,
        val alpha: Float,
        val filled: Boolean
    ) : ExportOverlay()

    data class OvalShape(
        val start: NormalizedPoint,
        val end: NormalizedPoint,
        val colorArgb: Int,
        val alpha: Float,
        val filled: Boolean
    ) : ExportOverlay()

    data class LineShape(
        val start: NormalizedPoint,
        val end: NormalizedPoint,
        val colorArgb: Int,
        val widthNorm: Float,
        val alpha: Float,
        val arrowHead: Boolean
    ) : ExportOverlay()

    data class ImageStamp(
        val bitmap: Bitmap,
        val start: NormalizedPoint,
        val end: NormalizedPoint
    ) : ExportOverlay()

    data class TextStamp(
        val position: NormalizedPoint,
        val text: String,
        val colorArgb: Int,
        val fontSizeNorm: Float
    ) : ExportOverlay()

    data class NoteStamp(
        val position: NormalizedPoint,
        val text: String,
        val colorArgb: Int
    ) : ExportOverlay()
}
