package com.techflyers.compose.file.explorer.screen.viewer.comic

import android.net.Uri
import androidx.activity.compose.setContent
import com.techflyers.compose.file.explorer.App.Companion.globalClass
import com.techflyers.compose.file.explorer.common.ui.SafeSurface
import com.techflyers.compose.file.explorer.screen.viewer.ViewerActivity
import com.techflyers.compose.file.explorer.screen.viewer.ViewerInstance
import com.techflyers.compose.file.explorer.screen.viewer.comic.ui.ComicViewerScreen
import com.techflyers.compose.file.explorer.theme.FileExplorerTheme

class ComicViewerActivity : ViewerActivity() {

    override fun onCreateNewInstance(uri: Uri, uid: String): ViewerInstance {
        val extraPath = intent.getStringExtra("extra_file_path")
        return ComicViewerInstance(uri, uid, extraPath)
    }

    override fun onReady(instance: ViewerInstance) {
        if (instance !is ComicViewerInstance) {
            globalClass.showMsg("Invalid comic file")
            finish()
            return
        }

        setContent {
            FileExplorerTheme {
                SafeSurface(enableStatusBarsPadding = false) {
                    ComicViewerScreen(
                        instance = instance,
                        onBackPress = { onBackPressedDispatcher.onBackPressed() }
                    )
                }
            }
        }
    }
}
