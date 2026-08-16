package com.raival.compose.file.explorer.screen.viewer.html

import android.annotation.SuppressLint
import android.os.Bundle
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.viewinterop.AndroidView
import com.raival.compose.file.explorer.base.BaseActivity
import com.raival.compose.file.explorer.common.ui.SafeSurface
import com.raival.compose.file.explorer.theme.FileExplorerTheme

class HtmlLivePreviewActivity : BaseActivity() {
    companion object {
        const val EXTRA_URL = "extra_preview_url"
        const val EXTRA_TITLE = "extra_preview_title"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        checkPermissions()
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @SuppressLint("SetJavaScriptEnabled")
    override fun onPermissionGranted() {
        val url = intent.getStringExtra(EXTRA_URL).orEmpty()
        val title = intent.getStringExtra(EXTRA_TITLE) ?: "Preview"
        if (url.isBlank()) {
            finish()
            return
        }

        setContent {
            FileExplorerTheme {
                SafeSurface(false) {
                    var webView by remember { mutableStateOf<WebView?>(null) }
                    DisposableEffect(Unit) {
                        onDispose {
                            LocalHtmlPreviewServer.stop()
                            webView?.destroy()
                        }
                    }
                    Scaffold(
                        topBar = {
                            TopAppBar(
                                title = {
                                    Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                },
                                navigationIcon = {
                                    IconButton(onClick = { finish() }) {
                                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null)
                                    }
                                },
                                actions = {
                                    IconButton(onClick = { webView?.reload() }) {
                                        Icon(Icons.Rounded.Refresh, contentDescription = "Reload")
                                    }
                                }
                            )
                        }
                    ) { padding ->
                        AndroidView(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(padding),
                            factory = { ctx ->
                                WebView(ctx).apply {
                                    webViewClient = object : WebViewClient() {
                                        override fun shouldOverrideUrlLoading(
                                            view: WebView?,
                                            request: WebResourceRequest?
                                        ): Boolean = false
                                    }
                                    webChromeClient = WebChromeClient()
                                    settings.javaScriptEnabled = true
                                    settings.domStorageEnabled = true
                                    settings.allowFileAccess = false
                                    loadUrl(url)
                                    webView = this
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        LocalHtmlPreviewServer.stop()
        super.onDestroy()
    }
}
