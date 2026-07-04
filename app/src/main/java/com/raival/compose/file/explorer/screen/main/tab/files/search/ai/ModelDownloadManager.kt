package com.raival.compose.file.explorer.screen.main.tab.files.search.ai

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.raival.compose.file.explorer.App.Companion.globalClass
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

/**
 * Manages on-demand download of the ONNX model for FileAI semantic search.
 * When user first toggles AI search, prompts them to choose between
 * quantized (~23 MB) and full (~90 MB) model variants.
 */
object ModelDownloadManager {

    // HuggingFace model URLs
    private const val FULL_MODEL_URL =
        "https://huggingface.co/Xenova/all-MiniLM-L6-v2/resolve/main/onnx/model.onnx"
    private const val QUANTIZED_MODEL_URL =
        "https://huggingface.co/Xenova/all-MiniLM-L6-v2/resolve/main/onnx/model_quantized.onnx"
    private const val TOKENIZER_URL =
        "https://huggingface.co/Xenova/all-MiniLM-L6-v2/resolve/main/tokenizer.json"


    var showModelChoiceDialog by mutableStateOf(false)
    var isDownloading by mutableStateOf(false)
        private set
    var downloadProgress by mutableFloatStateOf(0f)
        private set
    var downloadStatus by mutableStateOf("")
        private set

    private var onModelReady: (() -> Unit)? = null

    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(300, TimeUnit.SECONDS)
        .build()

    /**
     * Get the directory where models are stored.
     */
    fun getModelDir(context: Context): File {
        return File(context.filesDir, "fileai")
    }

    /**
     * Get the path to the ONNX model file.
     */
    fun getModelPath(context: Context): String {
        val variant = globalClass.preferencesManager.aiModelVariant
        val fileName = if (variant == "quantized") "model_quantized.onnx" else "model.onnx"
        return File(getModelDir(context), fileName).absolutePath
    }

    /**
     * Get the path to the tokenizer.json file.
     */
    fun getTokenizerPath(context: Context): String {
        return File(getModelDir(context), "tokenizer.json").absolutePath
    }

    /**
     * Check if the model is already downloaded.
     */
    fun isModelDownloaded(context: Context): Boolean {
        if (!globalClass.preferencesManager.aiModelDownloaded) return false
        val modelPath = getModelPath(context)
        val tokenizerPath = getTokenizerPath(context)
        return File(modelPath).exists() && File(tokenizerPath).exists()
    }

    /**
     * Request model download. Shows choice dialog if not yet downloaded.
     */
    fun requestModelDownload(onReady: () -> Unit) {
        onModelReady = onReady
        showModelChoiceDialog = true
    }

    /**
     * Start downloading the chosen model variant.
     */
    suspend fun downloadModel(context: Context, variant: String) {
        isDownloading = true
        downloadProgress = 0f
        downloadStatus = "Preparing download…"

        try {
            val modelDir = getModelDir(context)
            modelDir.mkdirs()

            val modelUrl = if (variant == "quantized") QUANTIZED_MODEL_URL else FULL_MODEL_URL
            val modelFileName = if (variant == "quantized") "model_quantized.onnx" else "model.onnx"

            // Download model
            downloadStatus = "Downloading model…"
            downloadFile(
                url = modelUrl,
                destination = File(modelDir, modelFileName),
                onProgress = { progress ->
                    downloadProgress = progress * 0.9f
                }
            )

            // Download tokenizer
            downloadStatus = "Downloading tokenizer…"
            downloadProgress = 0.9f
            downloadFile(
                url = TOKENIZER_URL,
                destination = File(modelDir, "tokenizer.json"),
                onProgress = { progress ->
                    downloadProgress = 0.9f + progress * 0.1f
                }
            )

            // Mark as downloaded
            globalClass.preferencesManager.aiModelVariant = variant
            globalClass.preferencesManager.aiModelDownloaded = true

            downloadProgress = 1.0f
            downloadStatus = "Done!"

            // Notify caller
            onModelReady?.invoke()
            onModelReady = null

        } catch (e: Exception) {
            downloadStatus = "Error: ${e.message}"
            globalClass.showMsg("Model download failed: ${e.message}")
        } finally {
            isDownloading = false
        }
    }

    private suspend fun downloadFile(
        url: String,
        destination: File,
        onProgress: (Float) -> Unit
    ) = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).build()
        val response = client.newCall(request).execute()

        if (!response.isSuccessful) {
            throw Exception("Download failed: HTTP ${response.code}")
        }

        val body = response.body ?: throw Exception("Empty response body")
        val contentLength = body.contentLength()
        var bytesRead = 0L

        body.byteStream().use { input ->
            FileOutputStream(destination).use { output ->
                val buffer = ByteArray(8192)
                var len: Int
                while (input.read(buffer).also { len = it } > 0) {
                    output.write(buffer, 0, len)
                    bytesRead += len
                    if (contentLength > 0) {
                        onProgress(bytesRead.toFloat() / contentLength)
                    }
                }
            }
        }
    }

    fun cancelDownload() {
        isDownloading = false
        onModelReady = null
    }
}

/**
 * Dialog that lets the user choose between quantized and full model.
 */
@Composable
fun ModelChoiceDialog(
    onChoose: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                Icons.Rounded.Psychology,
                contentDescription = null,
                modifier = Modifier.size(36.dp)
            )
        },
        title = { Text("Download AI Search Model") },
        text = {
            Column {
                Text(
                    "Semantic search uses the all-MiniLM-L6-V2 ONNX model " +
                            "to understand file content by meaning, not just keywords.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    "Choose a model variant:",
                    style = MaterialTheme.typography.titleSmall
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "• Quantized (~23 MB) — Faster, smaller, slightly lower accuracy\n" +
                            "• Full (~90 MB) — Best accuracy, larger download",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        },
        confirmButton = {
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Button(
                    onClick = { onChoose("quantized") },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Download Quantized (~23 MB)")
                }
                OutlinedButton(
                    onClick = { onChoose("full") },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Download Full (~90 MB)")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

/**
 * Progress dialog shown during model download.
 */
@Composable
fun ModelDownloadProgressDialog(
    onCancel: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { /* non-dismissable */ },
        icon = { Icon(Icons.Rounded.Psychology, contentDescription = null) },
        title = { Text("Downloading AI Model") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    ModelDownloadManager.downloadStatus,
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(16.dp))
                if (ModelDownloadManager.downloadProgress > 0) {
                    LinearProgressIndicator(
                        progress = { ModelDownloadManager.downloadProgress },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "${(ModelDownloadManager.downloadProgress * 100).toInt()}%",
                        style = MaterialTheme.typography.labelSmall
                    )
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            OutlinedButton(onClick = onCancel) { Text("Cancel") }
        }
    )
}
