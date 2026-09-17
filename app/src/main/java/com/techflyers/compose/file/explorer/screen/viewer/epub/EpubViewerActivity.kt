package com.techflyers.compose.file.explorer.screen.viewer.epub

import android.net.Uri
import androidx.activity.compose.setContent
import com.techflyers.compose.file.explorer.App.Companion.globalClass
import com.techflyers.compose.file.explorer.common.ui.SafeSurface
import com.techflyers.compose.file.explorer.screen.viewer.ViewerActivity
import com.techflyers.compose.file.explorer.screen.viewer.ViewerInstance
import com.techflyers.compose.file.explorer.screen.viewer.epub.ui.EpubViewerScreen
import com.techflyers.compose.file.explorer.theme.FileExplorerTheme

class EpubViewerActivity : ViewerActivity() {

    override fun onCreateNewInstance(uri: Uri, uid: String): ViewerInstance {
        val extraPath = intent.getStringExtra("extra_file_path")
        return EpubViewerInstance(uri, uid, extraPath)
    }

    override fun onReady(instance: ViewerInstance) {
        if (instance !is EpubViewerInstance) {
            globalClass.showMsg("Invalid EPUB file")
            finish()
            return
        }

        setContent {
            FileExplorerTheme {
                SafeSurface(enableStatusBarsPadding = false) {
                    EpubViewerScreen(
                        instance = instance,
                        onBackPress = { onBackPressedDispatcher.onBackPressed() }
                    )
                }
            }
        }
    }
}
