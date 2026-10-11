package com.techflyers.compose.file.explorer.screen.viewer.spreadsheet

import android.net.Uri
import androidx.activity.compose.setContent
import com.techflyers.compose.file.explorer.App.Companion.globalClass
import com.techflyers.compose.file.explorer.common.ui.SafeSurface
import com.techflyers.compose.file.explorer.screen.viewer.ViewerActivity
import com.techflyers.compose.file.explorer.screen.viewer.ViewerInstance
import com.techflyers.compose.file.explorer.theme.FileExplorerTheme

import androidx.activity.viewModels

class SpreadsheetViewerActivity : ViewerActivity() {

    private val viewModel: SpreadsheetViewModel by viewModels()

    override fun onCreateNewInstance(uri: Uri, uid: String): ViewerInstance {
        val extraPath = intent.getStringExtra("extra_file_path")
        return SpreadsheetViewerInstance(uri, uid, extraPath)
    }

    override fun onReady(instance: ViewerInstance) {
        if (instance !is SpreadsheetViewerInstance) {
            globalClass.showMsg("Invalid spreadsheet file")
            finish()
            return
        }

        setContent {
            FileExplorerTheme {
                SafeSurface(false) {
                    SpreadsheetViewerScreen(
                        instance = instance,
                        onBackPress = { finish() },
                        viewModel = viewModel
                    )
                }
            }
        }
    }
}
