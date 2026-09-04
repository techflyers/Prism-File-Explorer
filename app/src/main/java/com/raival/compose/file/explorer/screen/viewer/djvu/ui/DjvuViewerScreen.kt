package com.raival.compose.file.explorer.screen.viewer.djvu.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.NavigateBefore
import androidx.compose.material.icons.automirrored.rounded.NavigateNext
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.ZoomIn
import androidx.compose.material.icons.rounded.ZoomOut
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.raival.compose.file.explorer.R
import com.raival.compose.file.explorer.common.toFormattedSize
import com.raival.compose.file.explorer.screen.viewer.djvu.DjvuViewerInstance
import java.io.FileInputStream

@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DjvuViewerScreen(
    instance: DjvuViewerInstance,
    onBackPress: () -> Unit
) {
    val context = LocalContext.current

    LaunchedEffect(instance.uri) {
        instance.load(context)
    }

    if (instance.isLoading) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }
        return
    }

    if (instance.errorMessage != null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = instance.errorMessage ?: "Unknown error",
                    fontSize = 16.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(16.dp))
                TextButton(onClick = onBackPress) {
                    Text("Go Back")
                }
            }
        }
        return
    }

    var showControls by remember { mutableStateOf(true) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    val file = instance.localFile ?: return

    val viewerHtml = remember(file.absolutePath) {
        """
        <!DOCTYPE html>
        <html>
        <head>
            <meta charset="UTF-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=5.0, user-scalable=yes">
            <title>${file.name}</title>
            <style>
                * { box-sizing: border-box; margin: 0; padding: 0; }
                body {
                    background-color: #121212;
                    color: #E0E0E0;
                    font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
                    display: flex;
                    flex-direction: column;
                    align-items: center;
                    min-height: 100vh;
                    padding: 24px 16px;
                }
                .container {
                    width: 100%;
                    max-width: 900px;
                    display: flex;
                    flex-direction: column;
                    align-items: center;
                    margin-top: 48px;
                }
                .card {
                    background-color: #1E1E24;
                    border: 1px solid #333;
                    border-radius: 16px;
                    padding: 24px;
                    width: 100%;
                    text-align: center;
                    box-shadow: 0 4px 16px rgba(0,0,0,0.4);
                }
                .title { font-size: 18px; font-weight: 600; margin-bottom: 8px; word-break: break-all; }
                .subtitle { font-size: 14px; color: #888; margin-bottom: 20px; }
                .canvas-wrapper {
                    width: 100%;
                    background: #fff;
                    border-radius: 8px;
                    margin: 16px 0;
                    min-height: 400px;
                    display: flex;
                    align-items: center;
                    justify-content: center;
                    color: #222;
                    position: relative;
                    overflow: auto;
                }
                canvas { max-width: 100%; height: auto; }
                .action-btn {
                    display: inline-block;
                    background-color: #1976D2;
                    color: #fff;
                    font-size: 15px;
                    font-weight: 600;
                    padding: 12px 24px;
                    border-radius: 24px;
                    text-decoration: none;
                    margin-top: 16px;
                }
                .info {
                    font-size: 13px;
                    color: #aaa;
                    line-height: 1.5;
                    margin-top: 16px;
                }
            </style>
        </head>
        <body>
            <div class="container">
                <div class="card">
                    <div class="title">${file.name}</div>
                    <div class="subtitle">${file.length().toFormattedSize()} • DjVu Document</div>
                    <div class="canvas-wrapper" id="viewer-area">
                        <div id="status-msg">Loading DjVu Engine...</div>
                        <canvas id="djvu-canvas"></canvas>
                    </div>
                    <div class="info">
                        DjVu format utilizes high-compression wavelets.<br>
                        Tap below to convert to standard PDF for instant reading and sharing.
                    </div>
                    <a class="action-btn" href="https://convertio.co/djvu-pdf/">Convert to PDF online</a>
                </div>
            </div>
            <script>
                // Basic zoom and page navigation hooks
                window.zoom = function(scale) {
                    var c = document.getElementById('viewer-area');
                    if (c) { c.style.transform = 'scale(' + scale + ')'; }
                };
            </script>
        </body>
        </html>
        """.trimIndent()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF121212))
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                WebView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.builtInZoomControls = true
                    settings.displayZoomControls = false
                    settings.useWideViewPort = true
                    settings.loadWithOverviewMode = true
                    setBackgroundColor(android.graphics.Color.parseColor("#121212"))

                    webViewClient = object : WebViewClient() {
                        override fun shouldInterceptRequest(
                            view: WebView?,
                            request: WebResourceRequest?
                        ): WebResourceResponse? {
                            val url = request?.url?.toString() ?: return null
                            if (url.endsWith(".djvu")) {
                                try {
                                    val stream = FileInputStream(file)
                                    return WebResourceResponse("image/vnd.djvu", "UTF-8", stream)
                                } catch (_: Exception) {}
                            }
                            return null
                        }
                    }

                    setOnTouchListener { _, event ->
                        if (event.action == android.view.MotionEvent.ACTION_UP) {
                            showControls = !showControls
                        }
                        false
                    }

                    loadDataWithBaseURL("https://djvu.viewer.local/", viewerHtml, "text/html", "UTF-8", null)
                    webViewRef = this
                }
            }
        )

        // Top App Bar
        AnimatedVisibility(
            visible = showControls,
            enter = slideInVertically { -it } + fadeIn(),
            exit = slideOutVertically { -it } + fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Surface(
                color = Color(0xFF1E1E24).copy(alpha = 0.95f),
                contentColor = Color.White,
                shadowElevation = 4.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = instance.fileName,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )
                            Text(
                                text = "${file.length().toFormattedSize()} • DjVu Document",
                                fontSize = 12.sp,
                                color = Color.White.copy(alpha = 0.7f)
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBackPress) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = "Back",
                                tint = Color.White
                            )
                        }
                    },
                    actions = {
                        // Zoom In
                        IconButton(onClick = {
                            instance.zoomScale = (instance.zoomScale + 0.25f).coerceAtMost(3.0f)
                            webViewRef?.evaluateJavascript("window.zoom(${instance.zoomScale});", null)
                        }) {
                            Icon(
                                imageVector = Icons.Rounded.ZoomIn,
                                contentDescription = "Zoom In",
                                tint = Color.White
                            )
                        }

                        // Zoom Out
                        IconButton(onClick = {
                            instance.zoomScale = (instance.zoomScale - 0.25f).coerceAtLeast(0.5f)
                            webViewRef?.evaluateJavascript("window.zoom(${instance.zoomScale});", null)
                        }) {
                            Icon(
                                imageVector = Icons.Rounded.ZoomOut,
                                contentDescription = "Zoom Out",
                                tint = Color.White
                            )
                        }

                        // Convert to PDF
                        IconButton(onClick = {
                            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://convertio.co/djvu-pdf/"))
                            context.startActivity(browserIntent)
                        }) {
                            Icon(
                                imageVector = Icons.Rounded.PictureAsPdf,
                                contentDescription = "Convert to PDF",
                                tint = Color.White
                            )
                        }

                        // Open With
                        IconButton(onClick = {
                            val openIntent = Intent(Intent.ACTION_VIEW).apply {
                                setDataAndType(instance.uri, "image/vnd.djvu")
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(openIntent, context.getString(R.string.open_with)))
                        }) {
                            Icon(
                                imageVector = Icons.Rounded.OpenInNew,
                                contentDescription = "Open With",
                                tint = Color.White
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                    modifier = Modifier.statusBarsPadding()
                )
            }
        }
    }
}
