package com.raival.compose.file.explorer.screen.terminal

import android.Manifest
import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.raival.compose.file.explorer.theme.FileExplorerTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.lang.ref.WeakReference
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

class TerminalActivity : AppCompatActivity() {

    var sessionBinder by mutableStateOf<WeakReference<TerminalSessionService.SessionBinder>?>(null)
    var installNextStage by mutableStateOf<NEXT_STAGE?>(null)
    var progressText by mutableStateOf("Installing Ubuntu…")
    private var isBound = false

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            sessionBinder = WeakReference(service as TerminalSessionService.SessionBinder)
            isBound = true
            handleIntent(intent)
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            isBound = false
            sessionBinder = null
        }
    }

    override fun onStart() {
        super.onStart()
        ContextCompat.startForegroundService(this, Intent(this, TerminalSessionService::class.java))
        bindService(Intent(this, TerminalSessionService::class.java), serviceConnection, BIND_AUTO_CREATE)
    }

    override fun onStop() {
        super.onStop()
        if (isBound) { unbindService(serviceConnection); isBound = false }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        instance = this
    }

    fun handleIntent(intent: Intent?) {
        if (intent == null) return
        val pwd = intent.getStringExtra("cwd") ?: return
        intent.removeExtra("cwd")
        this.intent = intent

        // Keep a pending run command set by FileRunner; otherwise just cd into the folder.
        val sessionId = pendingTerminalCommand?.id ?: File(pwd).name.ifBlank { "prism" }
        if (pendingTerminalCommand == null) {
            openFolderInTerminal(this, pwd)
        }
        val binder = sessionBinder?.get() ?: return
        if (terminalView.get() == null) return

        lifecycleScope.launch(Dispatchers.Main) {
            val client = TerminalBackEnd()
            val existing = binder.getSession(sessionId)
            val info = if (existing != null) {
                SessionInfo(sessionId, pwd, existing)
            } else {
                binder.createSession(sessionId, client, this@TerminalActivity)
            }
            changeSession(info.id)
        }
    }

    private val notifPermLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        instance = this

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED) {
            notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent {
            FileExplorerTheme {
                Surface {
                    if (sessionBinder != null) {
                        TerminalSetupHost(this)
                    } else {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                }
            }
        }
    }

    @Suppress("OPT_IN_USAGE")
    @Composable
    fun TerminalSetupHost(context: android.content.Context) {
        var progress by remember { mutableFloatStateOf(0f) }
        var needsDownload by remember { mutableStateOf(false) }
        var downloadedBytes by remember { mutableLongStateOf(0L) }
        var totalBytes by remember { mutableLongStateOf(0L) }

        fun formatMB(b: Long) = "%.2f".format(b / (1024.0 * 1024.0))

        LaunchedEffect(Unit) {
            try {
                val abi = Build.SUPPORTED_ABIS
                val filesToDownload = mutableListOf<DownloadFile>()
                if (!isTerminalInstalled(context)) {
                    val url = when {
                        abi.contains("x86_64")     -> ROOTFS_X64
                        abi.contains("arm64-v8a")  -> ROOTFS_ARM64
                        abi.contains("armeabi-v7a") -> ROOTFS_ARM
                        else -> throw RuntimeException("Unsupported CPU: ${abi.joinToString()}")
                    }
                    filesToDownload.add(DownloadFile(url, File(getTempDir(context), "sandbox.tar.gz")))
                }
                needsDownload = filesToDownload.any { !it.outputFile.exists() }

                setupEnvironment(
                    filesToDownload = filesToDownload,
                    onProgress = { name, dl, total ->
                        downloadedBytes = dl; totalBytes = total
                        if (total > 0) {
                            progressText = "Downloading ${name.removeSuffix(".tar.gz")} (${formatMB(dl)}/${formatMB(total)} MB)"
                        }
                    },
                    onComplete = { installNextStage = it },
                    onError = { e, _ ->
                        when (e) {
                            is UnknownHostException -> android.widget.Toast.makeText(this@TerminalActivity, "Network error", android.widget.Toast.LENGTH_SHORT).show()
                            else -> android.widget.Toast.makeText(this@TerminalActivity, "Setup failed: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
                        }
                        finish()
                    }
                )
            } catch (e: Exception) {
                android.widget.Toast.makeText(this@TerminalActivity, "Setup error: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
                finish()
            }
        }

        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            val ctx = LocalContext.current
            val activity = ctx as? Activity
            DisposableEffect(Unit) {
                activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                onDispose { activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
            }

            if (installNextStage == null) {
                if (needsDownload) {
                    Box(Modifier.fillMaxSize().padding(16.dp)) {
                        Column(
                            Modifier.align(Alignment.Center),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(progressText, style = MaterialTheme.typography.bodyLarge)
                            Spacer(Modifier.height(16.dp))
                            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth(0.8f))
                            if (totalBytes > 0) {
                                progress = downloadedBytes.toFloat() / totalBytes
                                Text(
                                    "${(progress * 100).toInt()}%",
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(top = 8.dp)
                                )
                            }
                        }
                        Text(
                            "Do not leave this screen during setup",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp)
                        )
                    }
                }
            } else {
                TerminalScreen(activity = this@TerminalActivity)
            }
        }
    }

    data class DownloadFile(val url: String, val outputFile: File)

    private suspend fun setupEnvironment(
        filesToDownload: List<DownloadFile>,
        onProgress: (String, Long, Long) -> Unit,
        onComplete: (NEXT_STAGE) -> Unit,
        onError: (Exception, File?) -> Unit,
    ) {
        var currentFile: File? = null
        withContext(Dispatchers.IO) {
            try {
                filesToDownload.forEach { df ->
                    currentFile = df.outputFile
                    df.outputFile.parentFile?.mkdirs()
                    if (!df.outputFile.exists()) {
                        downloadFile(df.url, df.outputFile) { dl, total -> onProgress(df.outputFile.name, dl, total) }
                    } else {
                        onProgress(df.outputFile.name, df.outputFile.length(), df.outputFile.length())
                    }
                    runCatching { df.outputFile.setExecutable(true) }
                }
                val stage = getNextStage(this@TerminalActivity)
                onComplete(stage)
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) { onError(e, currentFile) }
                currentFile?.delete()
            }
        }
    }

    private suspend fun downloadFile(url: String, outputFile: File, onProgress: (Long, Long) -> Unit) {
        withContext(Dispatchers.IO) {
            val client = OkHttpClient.Builder()
                .connectTimeout(1, TimeUnit.MINUTES)
                .readTimeout(1, TimeUnit.MINUTES)
                .callTimeout(10, TimeUnit.MINUTES)
                .build()
            client.newCall(Request.Builder().url(url).build()).execute().use { resp ->
                if (!resp.isSuccessful) throw Exception("HTTP ${resp.code}")
                val body = resp.body ?: throw Exception("Empty response body")
                val total = body.contentLength()
                var downloaded = 0L
                outputFile.outputStream().use { out ->
                    body.byteStream().use { inp ->
                        val buf = ByteArray(8 * 1024)
                        var n: Int
                        while (inp.read(buf).also { n = it } != -1) {
                            out.write(buf, 0, n)
                            downloaded += n
                            withContext(Dispatchers.Main) { onProgress(downloaded, total) }
                        }
                    }
                }
            }
        }
    }

    companion object {
        private var activityRef = WeakReference<TerminalActivity?>(null)
        var instance: TerminalActivity?
            get() = activityRef.get()
            private set(value) { activityRef = WeakReference(value) }
    }
}
