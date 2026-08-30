package com.raival.compose.file.explorer.screen.viewer.image

import android.net.Uri
import com.raival.compose.file.explorer.screen.viewer.ViewerInstance

class ImageViewerInstance(
    override val uri: Uri,
    override val id: String,
    /** Ordered image URIs for swipe (context list or folder siblings). Always includes [uri]. */
    val imageList: List<Uri> = listOf(uri),
    /** Absolute paths parallel to [imageList] when available (for stable indexing). */
    val imagePaths: List<String> = emptyList(),
    /** Index of the originally opened image. */
    val initialIndex: Int = 0,
) : ViewerInstance {
    override fun onClose() {
    }
}
