package com.raival.compose.file.explorer.screen.main.tab.nfile_tools.ui

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.raival.compose.file.explorer.screen.main.tab.files.service.WebSharingForegroundService
import com.raival.compose.file.explorer.screen.main.tab.files.service.WebSharingServer
import com.raival.compose.file.explorer.screen.main.tab.nfile_tools.WebSharingTab
import java.io.File

@Composable
fun WebSharingTabContentView(tab: WebSharingTab) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    var isLocalRunning by remember { mutableStateOf(WebSharingServer.isLocalActive()) }
    var isInternetRunning by remember { mutableStateOf(WebSharingServer.isInternetActive()) }
    var localUrl by remember { mutableStateOf(WebSharingServer.getLocalUrl()) }
    var internetUrl by remember { mutableStateOf(WebSharingServer.getInternetUrl()) }
    var sharedPath by remember { mutableStateOf(Environment.getExternalStorageDirectory().absolutePath) }

    var showFolderPicker by remember { mutableStateOf(false) }

    val activeUrl = when {
        isInternetRunning && internetUrl.startsWith("https://") -> internetUrl
        isLocalRunning -> localUrl
        else -> ""
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        // Status cards
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Wi-Fi Local Share",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (isLocalRunning) localUrl else "Local sharing is disabled",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    if (isLocalRunning) {
                        IconButton(onClick = {
                            clipboardManager.setText(AnnotatedString(localUrl))
                            Toast.makeText(context, "Copied local link", Toast.LENGTH_SHORT).show()
                        }) {
                            Icon(Icons.Rounded.ContentCopy, contentDescription = "Copy")
                        }
                    }
                }
            }
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isInternetRunning) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Secure Internet Share (localhost.run)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (isInternetRunning) internetUrl else "Tunnel proxy is closed",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    if (isInternetRunning && internetUrl.startsWith("https://")) {
                        IconButton(onClick = {
                            clipboardManager.setText(AnnotatedString(internetUrl))
                            Toast.makeText(context, "Copied internet link", Toast.LENGTH_SHORT).show()
                        }) {
                            Icon(Icons.Rounded.ContentCopy, contentDescription = "Copy")
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Selected directory path
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                .clickable(enabled = !isLocalRunning && !isInternetRunning) { showFolderPicker = true }
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Rounded.FolderOpen, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Directory to Share",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = sharedPath,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
            if (!isLocalRunning && !isInternetRunning) {
                Icon(Icons.Rounded.ChevronRight, contentDescription = null)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // QR Code Display
        if (activeUrl.isNotEmpty() && activeUrl.startsWith("http")) {
            val qrBitmap = remember(activeUrl) {
                try {
                    val writer = QRCodeWriter()
                    val qrSize = 300
                    val bitMatrix = writer.encode(activeUrl, BarcodeFormat.QR_CODE, qrSize, qrSize)
                    val bitmap = Bitmap.createBitmap(qrSize, qrSize, Bitmap.Config.RGB_565)
                    for (x in 0 until qrSize) {
                        for (y in 0 until qrSize) {
                            bitmap.setPixel(x, y, if (bitMatrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
                        }
                    }
                    bitmap
                } catch (_: Exception) {
                    null
                }
            }
            if (qrBitmap != null) {
                Card(
                    modifier = Modifier
                        .size(200.dp)
                        .padding(8.dp),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Image(
                        bitmap = qrBitmap.asImageBitmap(),
                        contentDescription = "QR Code to scan",
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Scan QR to view shared folder",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else if (isInternetRunning) {
            CircularProgressIndicator(modifier = Modifier.size(48.dp))
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Connecting secure bridge...",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        // Action controls
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Local Sharing toggle button
            Button(
                onClick = {
                    if (isLocalRunning) {
                        WebSharingServer.stop {
                            isLocalRunning = false
                            isInternetRunning = false
                        }
                        val serviceIntent = Intent(context, WebSharingForegroundService::class.java)
                        context.stopService(serviceIntent)
                    } else {
                        WebSharingServer.start(context, sharedPath) {
                            isLocalRunning = WebSharingServer.isLocalActive()
                            localUrl = WebSharingServer.getLocalUrl()
                        }
                        val serviceIntent = Intent(context, WebSharingForegroundService::class.java).apply {
                            putExtra("url", WebSharingServer.getLocalUrl())
                            putExtra("isInternet", false)
                        }
                        context.startService(serviceIntent)
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isLocalRunning) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(
                    imageVector = if (isLocalRunning) Icons.Rounded.WifiOff else Icons.Rounded.Wifi,
                    contentDescription = null
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (isLocalRunning) "Stop Local" else "Start Local")
            }

            // Internet tunnel toggle button
            Button(
                onClick = {
                    if (isInternetRunning) {
                        WebSharingServer.stopInternetTunnel()
                        isInternetRunning = false
                        internetUrl = ""
                        val serviceIntent = Intent(context, WebSharingForegroundService::class.java).apply {
                            putExtra("url", WebSharingServer.getLocalUrl())
                            putExtra("isInternet", false)
                        }
                        context.startService(serviceIntent)
                    } else {
                        WebSharingServer.startInternetTunnel(context, sharedPath) {
                            isLocalRunning = WebSharingServer.isLocalActive()
                            isInternetRunning = WebSharingServer.isInternetActive()
                            localUrl = WebSharingServer.getLocalUrl()
                            internetUrl = WebSharingServer.getInternetUrl()

                            if (internetUrl.startsWith("https://")) {
                                val serviceIntent = Intent(context, WebSharingForegroundService::class.java).apply {
                                    putExtra("url", internetUrl)
                                    putExtra("isInternet", true)
                                }
                                context.startService(serviceIntent)
                            }
                        }
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isInternetRunning) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.secondary
                )
            ) {
                Icon(
                    imageVector = if (isInternetRunning) Icons.Rounded.CloudOff else Icons.Rounded.CloudQueue,
                    contentDescription = null
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (isInternetRunning) "Stop Tunnel" else "Start Tunnel")
            }
        }
    }

    // Directory selection dialog
    FileSelectionDialog(
        show = showFolderPicker,
        onDismissRequest = { showFolderPicker = false },
        onItemsSelected = { selected ->
            val first = selected.firstOrNull()
            if (first != null && first.isDirectory) {
                sharedPath = first.absolutePath
            } else {
                Toast.makeText(context, "Please select folders only", Toast.LENGTH_SHORT).show()
            }
        }
    )
}
