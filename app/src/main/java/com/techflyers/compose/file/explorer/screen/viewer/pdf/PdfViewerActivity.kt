package com.techflyers.compose.file.explorer.screen.viewer.pdf

import android.net.Uri
import androidx.activity.compose.setContent
import androidx.pdf.ExperimentalPdfApi
import com.techflyers.compose.file.explorer.App.Companion.globalClass
import com.techflyers.compose.file.explorer.R
import com.techflyers.compose.file.explorer.common.ui.SafeSurface
import com.techflyers.compose.file.explorer.screen.viewer.ViewerActivity
import com.techflyers.compose.file.explorer.screen.viewer.ViewerInstance
import com.techflyers.compose.file.explorer.screen.viewer.pdf.ui.EnhancedPdfViewerContent
import com.techflyers.compose.file.explorer.theme.FileExplorerTheme

class PdfViewerActivity : ViewerActivity() {

    override fun onCreateNewInstance(uri: Uri, uid: String): ViewerInstance {
        return PdfViewerInstance(uri, uid)
    }

    @OptIn(ExperimentalPdfApi::class)
    override fun onReady(instance: ViewerInstance) {
        if (instance is PdfViewerInstance) {
            setContent {
                FileExplorerTheme {
                    SafeSurface(false) {
                        EnhancedPdfViewerContent(
                            documentUri = instance.uri,
                            fileName = instance.metadata.name,
                            infoProperties = instance.getInfo(),
                            isOfficeConverted = false,
                            originalSourceUri = instance.uri,
                            onBackPress = { finish() }
                        )
                    }
                }
            }
        } else {
            globalClass.showMsg(getString(R.string.invalid_pdf))
            finish()
        }
    }

    override fun onWindowStartingActionMode(callback: android.view.ActionMode.Callback?, type: Int): android.view.ActionMode? {
        if (type == android.view.ActionMode.TYPE_FLOATING) {
            val wrapped = object : android.view.ActionMode.Callback2() {
                override fun onCreateActionMode(mode: android.view.ActionMode?, menu: android.view.Menu?): Boolean = false
                override fun onPrepareActionMode(mode: android.view.ActionMode?, menu: android.view.Menu?): Boolean = false
                override fun onActionItemClicked(mode: android.view.ActionMode?, item: android.view.MenuItem?): Boolean = false
                override fun onDestroyActionMode(mode: android.view.ActionMode?) {
                    callback?.onDestroyActionMode(mode)
                }
            }
            return super.onWindowStartingActionMode(wrapped, type)
        }
        return super.onWindowStartingActionMode(callback, type)
    }

    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
        if (event.keyCode == android.view.KeyEvent.KEYCODE_BACK && event.action == android.view.KeyEvent.ACTION_UP) {
            onBackPressedDispatcher.onBackPressed()
            return true
        }
        return super.dispatchKeyEvent(event)
    }
}