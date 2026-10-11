package com.techflyers.compose.file.explorer.screen.viewer.document

import android.net.Uri
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.techflyers.compose.file.explorer.App.Companion.globalClass
import com.techflyers.compose.file.explorer.common.ui.SafeSurface
import com.techflyers.compose.file.explorer.office.OfficeEngine
import com.techflyers.compose.file.explorer.screen.viewer.ViewerActivity
import com.techflyers.compose.file.explorer.screen.viewer.ViewerInstance
import com.techflyers.compose.file.explorer.screen.viewer.document.ui.LibreOfficeDownloadDialog
import com.techflyers.compose.file.explorer.screen.viewer.document.ui.OfficeConversionProgressScreen
import com.techflyers.compose.file.explorer.screen.viewer.pdf.ui.EnhancedPdfViewerContent
import com.techflyers.compose.file.explorer.theme.FileExplorerTheme

class DocumentViewerActivity : ViewerActivity() {

    private val viewModel: DocumentViewerViewModel by viewModels()

    override fun onCreateNewInstance(uri: Uri, uid: String): ViewerInstance {
        return DocumentViewerInstance(uri, uid)
    }

    override fun onReady(instance: ViewerInstance) {
        if (instance !is DocumentViewerInstance) {
            globalClass.showMsg("Invalid document file")
            finish()
            return
        }

        viewModel.initDocument(instance.uri)

        setContent {
            FileExplorerTheme {
                SafeSurface(false) {
                    val state by viewModel.uiState.collectAsState()
                    val context = LocalContext.current
                    val installer = remember(context) { OfficeEngine.installer(context) }
                    val engineState by installer.state.collectAsState()

                    AnimatedContent(
                        targetState = Triple(state.isLoading, state.activePdfUri != null, state.error != null),
                        transitionSpec = { fadeIn() togetherWith fadeOut() },
                        label = "doc_viewer_content"
                    ) { (isLoading, hasPdf, hasError) ->
                        when {
                            isLoading -> {
                                OfficeConversionProgressScreen(
                                    fileName = state.fileName,
                                    message = state.loadingMessage,
                                    progress = state.progress,
                                    onCancel = { finish() }
                                )
                            }
                            hasError && !hasPdf -> {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier.padding(32.dp)
                                    ) {
                                        Text(
                                            text = "Error Opening Document",
                                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.error
                                        )
                                        Spacer(Modifier.height(8.dp))
                                        Text(
                                            text = state.error ?: "Unknown error",
                                            style = MaterialTheme.typography.bodyMedium,
                                            textAlign = TextAlign.Center,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(Modifier.height(24.dp))
                                        Button(onClick = { viewModel.loadDocument(instance.uri, preferLibreOffice = false) }) {
                                            Text("Retry with Built-in Engine")
                                        }
                                    }
                                }
                            }
                            hasPdf -> {
                                EnhancedPdfViewerContent(
                                    documentUri = state.activePdfUri!!,
                                    fileName = state.fileName,
                                    isOfficeConverted = state.isOfficeDocument,
                                    originalSourceUri = state.originalUri,
                                    onBackPress = { finish() }
                                )
                            }
                        }
                    }

                    // LibreOffice On-Demand Download Prompt Dialog
                    LibreOfficeDownloadDialog(
                        visible = state.showOfficeDownloadPrompt,
                        engineState = engineState,
                        onDownloadClick = {
                            viewModel.onDownloadEngineConfirmed()
                        },
                        onCancelDownload = {
                            installer.cancel()
                            viewModel.setShowOfficeDownloadPrompt(false)
                        },
                        onUseFallbackClick = {
                            viewModel.onDismissDownloadPromptAndUseFallback()
                        },
                        onReloadWithEngine = {
                            viewModel.setShowOfficeDownloadPrompt(false)
                            viewModel.loadDocument(instance.uri, preferLibreOffice = true)
                        },
                        onDismiss = {
                            viewModel.onDismissDownloadPromptAndUseFallback()
                        }
                    )
                }
            }
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

class DocumentViewerInstance(
    override val uri: Uri,
    override val id: String
) : ViewerInstance {
    override fun onClose() {}
}
