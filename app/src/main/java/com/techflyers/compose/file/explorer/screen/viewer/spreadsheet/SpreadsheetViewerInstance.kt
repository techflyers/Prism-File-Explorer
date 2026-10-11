package com.techflyers.compose.file.explorer.screen.viewer.spreadsheet

import android.net.Uri
import com.techflyers.compose.file.explorer.screen.viewer.ViewerInstance

class SpreadsheetViewerInstance(
    override val uri: Uri,
    override val id: String,
    val extraFilePath: String? = null
) : ViewerInstance {
    override fun onClose() {}
}
