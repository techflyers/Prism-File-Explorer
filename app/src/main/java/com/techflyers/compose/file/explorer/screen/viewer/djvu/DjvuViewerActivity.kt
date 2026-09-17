package com.techflyers.compose.file.explorer.screen.viewer.djvu

import android.net.Uri
import androidx.activity.compose.setContent
import com.techflyers.compose.file.explorer.App.Companion.globalClass
import com.techflyers.compose.file.explorer.common.ui.SafeSurface
import com.techflyers.compose.file.explorer.screen.viewer.ViewerActivity
import com.techflyers.compose.file.explorer.screen.viewer.ViewerInstance
import com.techflyers.compose.file.explorer.screen.viewer.djvu.ui.DjvuViewerScreen
import com.techflyers.compose.file.explorer.theme.FileExplorerTheme

class DjvuViewerActivity : ViewerActivity() {

    override fun onCreateNewInstance(uri: Uri, uid: String): ViewerInstance {
        val extraPath = intent.getStringExtra("extra_file_path")
        return DjvuViewerInstance(uri, uid, extraPath)
    }

    override fun onReady(instance: ViewerInstance) {
        if (instance !is DjvuViewerInstance) {
            globalClass.showMsg("Invalid DJVU file")
            finish()
            return
        }

        setContent {
            FileExplorerTheme {
                SafeSurface(enableStatusBarsPadding = false) {
                    DjvuViewerScreen(
                        instance = instance,
                        onBackPress = { onBackPressedDispatcher.onBackPressed() }
                    )
                }
            }
        }
    }
}
