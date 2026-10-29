package com.techflyers.compose.file.explorer.screen.main.tab.nfile_tools.ui

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.techflyers.compose.file.explorer.common.toFormattedSize
import com.techflyers.compose.file.explorer.screen.main.tab.files.service.WebSharingForegroundService
import com.techflyers.compose.file.explorer.screen.main.tab.files.service.WebSharingServer
import com.techflyers.compose.file.explorer.screen.main.tab.nfile_tools.WebSharingTab
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private data class SharedFolderMediaSummary(
    val totalFiles: Int = 0,
    val totalFolders: Int = 0,
    val videoCount: Int = 0,
    val subtitleCount: Int = 0,
    val audioCount: Int = 0,
    val imageCount: Int = 0,
    val totalBytes: Long = 0L
)

@Composable
fun WebSharingTabContentView(tab: WebSharingTab) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    var isLocalRunning by remember { mutableStateOf(WebSharingServer.isLocalActive()) }
    var isInternetRunning by remember { mutableStateOf(WebSharingServer.isInternetActive()) }
    var localUrl by remember { mutableStateOf(WebSharingServer.getLocalUrl()) }
    var internetUrl by remember { mutableStateOf(WebSharingServer.getInternetUrl()) }

    var sharedPath by remember(tab.id) {
        mutableStateOf(
            if (WebSharingServer.isLocalActive() && WebSharingServer.getSharedRootDir().isNotBlank()) {
                WebSharingServer.getSharedRootDir()
            } else {
                tab.targetDirectoryPath.ifBlank {
                    Environment.getExternalStorageDirectory().absolutePath
                }
            }
        )
    }

    LaunchedEffect(tab.targetDirectoryPath) {
        if (!isLocalRunning && !isInternetRunning && tab.targetDirectoryPath.isNotBlank()) {
            sharedPath = tab.targetDirectoryPath
        }
    }

    var showFolderPicker by remember { mutableStateOf(false) }
    var folderSummary by remember { mutableStateOf(SharedFolderMediaSummary()) }

    LaunchedEffect(sharedPath) {
        tab.targetDirectoryPath = sharedPath
        if (isLocalRunning) {
            WebSharingServer.updateRootDir(sharedPath)
        }
        folderSummary = withContext(Dispatchers.IO) {
            val dir = File(sharedPath)
            val children = dir.listFiles() ?: emptyArray()
            var files = 0
            var folders = 0
            var videos = 0
            var subs = 0
            var audios = 0
            var images = 0
            var bytes = 0L
            for (f in children) {
                if (f.name.startsWith(".")) continue
                if (f.isDirectory) {
                    folders++
                } else {
                    files++
                    bytes += f.length()
                    when (f.extension.lowercase()) {
                        "mp4", "mkv", "webm", "avi", "mov", "m4v", "3gp", "ts", "flv" -> videos++
                        "srt", "vtt", "ass", "ssa", "sub" -> subs++
                        "mp3", "wav", "flac", "m4a", "aac", "ogg", "opus" -> audios++
                        "jpg", "jpeg", "png", "gif", "webp", "bmp", "svg", "heic" -> images++
                    }
                }
            }
            SharedFolderMediaSummary(
                totalFiles = files,
                totalFolders = folders,
                videoCount = videos,
                subtitleCount = subs,
                audioCount = audios,
                imageCount = images,
                totalBytes = bytes
            )
        }
    }

    val activeUrl = when {
        isInternetRunning && internetUrl.startsWith("https://") -> internetUrl
        isLocalRunning -> localUrl
        else -> ""
    }

    val activeTabFolder = remember { WebSharingTab.resolveInitialDirectoryPath(null) }
    val storageRoot = remember { Environment.getExternalStorageDirectory().absolutePath }
    val downloadsDir = remember { Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).absolutePath }
    val moviesDir = remember { Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES).absolutePath }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Shared Folder Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.FolderOpen,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = File(sharedPath).name.ifEmpty { "Storage Root" },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = sharedPath,
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    FilledTonalButton(
                        onClick = { showFolderPicker = true },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Icon(Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Change", style = MaterialTheme.typography.labelMedium)
                    }
                }

                // Folder shortcuts
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (activeTabFolder != storageRoot && activeTabFolder != sharedPath) {
                        SuggestionChip(
                            onClick = { sharedPath = activeTabFolder },
                            icon = { Icon(Icons.Rounded.Tab, contentDescription = null, modifier = Modifier.size(14.dp)) },
                            label = { Text("Tab: ${File(activeTabFolder).name}") }
                        )
                    }
                    if (sharedPath != downloadsDir && File(downloadsDir).exists()) {
                        SuggestionChip(
                            onClick = { sharedPath = downloadsDir },
                            label = { Text("Downloads") }
                        )
                    }
                    if (sharedPath != moviesDir && File(moviesDir).exists()) {
                        SuggestionChip(
                            onClick = { sharedPath = moviesDir },
                            label = { Text("Movies") }
                        )
                    }
                    if (sharedPath != storageRoot) {
                        SuggestionChip(
                            onClick = { sharedPath = storageRoot },
                            label = { Text("Storage Root") }
                        )
                    }
                }

                // Summary line
                if (folderSummary.totalFiles > 0 || folderSummary.totalFolders > 0) {
                    Text(
                        text = buildString {
                            append("${folderSummary.totalFolders} folders, ${folderSummary.totalFiles} files (${folderSummary.totalBytes.toFormattedSize()})")
                            val mediaList = mutableListOf<String>()
                            if (folderSummary.videoCount > 0) mediaList.add("🎬 ${folderSummary.videoCount}")
                            if (folderSummary.subtitleCount > 0) mediaList.add("💬 ${folderSummary.subtitleCount}")
                            if (folderSummary.audioCount > 0) mediaList.add("🎵 ${folderSummary.audioCount}")
                            if (mediaList.isNotEmpty()) {
                                append(" • ")
                                append(mediaList.joinToString(" "))
                            }
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // 2. Local Wi-Fi Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isLocalRunning) {
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                } else {
                    MaterialTheme.colorScheme.surfaceContainer
                }
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = if (isLocalRunning) Icons.Rounded.Wifi else Icons.Rounded.WifiOff,
                        contentDescription = null,
                        tint = if (isLocalRunning) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(26.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Local Wi-Fi",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Share on your local network",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Surface(
                        color = if (isLocalRunning) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = if (isLocalRunning) "ONLINE" else "OFFLINE",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (isLocalRunning) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }

                // URL & actions when online
                if (isLocalRunning) {
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = localUrl,
                                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = {
                                    clipboardManager.setText(AnnotatedString(localUrl))
                                    Toast.makeText(context, "Copied local link", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.size(34.dp)
                            ) {
                                Icon(Icons.Rounded.ContentCopy, contentDescription = "Copy", modifier = Modifier.size(17.dp))
                            }
                            IconButton(
                                onClick = {
                                    runCatching {
                                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(localUrl)))
                                    }
                                },
                                modifier = Modifier.size(34.dp)
                            ) {
                                Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = "Open in Browser", modifier = Modifier.size(17.dp))
                            }
                        }
                    }
                }

                // Toggle Button inside Card
                Button(
                    onClick = {
                        if (isLocalRunning) {
                            WebSharingServer.stop {
                                isLocalRunning = false
                                isInternetRunning = false
                            }
                            runCatching {
                                val serviceIntent = Intent(context, WebSharingForegroundService::class.java)
                                context.stopService(serviceIntent)
                            }
                        } else {
                            WebSharingServer.start(context, sharedPath) {
                                isLocalRunning = WebSharingServer.isLocalActive()
                                localUrl = WebSharingServer.getLocalUrl()
                            }
                            runCatching {
                                val serviceIntent = Intent(context, WebSharingForegroundService::class.java).apply {
                                    putExtra("url", WebSharingServer.getLocalUrl())
                                    putExtra("isInternet", false)
                                }
                                ContextCompat.startForegroundService(context, serviceIntent)
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isLocalRunning) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(
                        imageVector = if (isLocalRunning) Icons.Rounded.WifiOff else Icons.Rounded.Wifi,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isLocalRunning) "Stop Local Share" else "Start Local Share",
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        // 3. Internet Tunnel Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isInternetRunning) {
                    MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                } else {
                    MaterialTheme.colorScheme.surfaceContainer
                }
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = if (isInternetRunning) Icons.Rounded.CloudQueue else Icons.Rounded.CloudOff,
                        contentDescription = null,
                        tint = if (isInternetRunning) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(26.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Internet Tunnel",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Public link via localhost.run",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Surface(
                        color = if (isInternetRunning) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = if (isInternetRunning) "ACTIVE" else "CLOSED",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (isInternetRunning) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }

                // URL & actions when online
                if (isInternetRunning) {
                    if (internetUrl.startsWith("https://")) {
                        Surface(
                            color = MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = internetUrl,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.secondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(
                                    onClick = {
                                        clipboardManager.setText(AnnotatedString(internetUrl))
                                        Toast.makeText(context, "Copied internet link", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.size(34.dp)
                                ) {
                                    Icon(Icons.Rounded.ContentCopy, contentDescription = "Copy", modifier = Modifier.size(17.dp))
                                }
                                IconButton(
                                    onClick = {
                                        val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(Intent.EXTRA_TEXT, internetUrl)
                                        }
                                        context.startActivity(Intent.createChooser(sendIntent, "Share Web Portal Link"))
                                    },
                                    modifier = Modifier.size(34.dp)
                                ) {
                                    Icon(Icons.Rounded.Share, contentDescription = "Share", modifier = Modifier.size(17.dp))
                                }
                            }
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            Text(
                                text = internetUrl.ifBlank { "Connecting secure tunnel..." },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Toggle Button inside Card
                Button(
                    onClick = {
                        if (isInternetRunning) {
                            WebSharingServer.stopInternetTunnel()
                            isInternetRunning = false
                            internetUrl = ""
                            runCatching {
                                val serviceIntent = Intent(context, WebSharingForegroundService::class.java).apply {
                                    putExtra("url", WebSharingServer.getLocalUrl())
                                    putExtra("isInternet", false)
                                }
                                ContextCompat.startForegroundService(context, serviceIntent)
                            }
                        } else {
                            WebSharingServer.startInternetTunnel(context, sharedPath) {
                                isLocalRunning = WebSharingServer.isLocalActive()
                                isInternetRunning = WebSharingServer.isInternetActive()
                                localUrl = WebSharingServer.getLocalUrl()
                                internetUrl = WebSharingServer.getInternetUrl()

                                if (internetUrl.startsWith("https://")) {
                                    runCatching {
                                        val serviceIntent = Intent(context, WebSharingForegroundService::class.java).apply {
                                            putExtra("url", internetUrl)
                                            putExtra("isInternet", true)
                                        }
                                        ContextCompat.startForegroundService(context, serviceIntent)
                                    }
                                }
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isInternetRunning) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.secondary
                    )
                ) {
                    Icon(
                        imageVector = if (isInternetRunning) Icons.Rounded.CloudOff else Icons.Rounded.CloudQueue,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isInternetRunning) "Stop Tunnel" else "Start Tunnel",
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        // 4. QR Code Card (Clean, only shown when server is active)
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
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Card(
                            modifier = Modifier.size(160.dp),
                            shape = RoundedCornerShape(12.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Image(
                                bitmap = qrBitmap.asImageBitmap(),
                                contentDescription = "QR Code to scan",
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(8.dp)
                            )
                        }
                        Text(
                            text = "Scan to open on phone, tablet or TV",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }

    // Directory selection dialog
    FileSelectionDialog(
        show = showFolderPicker,
        initialDirectory = File(sharedPath).takeIf { it.exists() },
        onDismissRequest = { showFolderPicker = false },
        onItemsSelected = { selected ->
            val first = selected.firstOrNull()
            if (first != null) {
                sharedPath = if (first.isDirectory) {
                    first.absolutePath
                } else {
                    first.parentFile?.absolutePath ?: first.absolutePath
                }
            }
        }
    )
}
