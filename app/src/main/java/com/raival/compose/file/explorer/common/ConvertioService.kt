package com.raival.compose.file.explorer.common

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Base64
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.raival.compose.file.explorer.App.Companion.globalClass
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

/**
 * Convertio API service for converting documents to PDF.
 * Ported from NFile's convertio_service.dart.
 */
object ConvertioService {

    var isConverting by mutableStateOf(false)
        private set
    var conversionProgress by mutableFloatStateOf(0f)
        private set
    var conversionStatus by mutableStateOf("")
        private set
    var showApiKeyDialog by mutableStateOf(false)
    var showProgressDialog by mutableStateOf(false)

    private var pendingFilePath: String? = null
    private var pendingContext: Context? = null

    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .build()

    private const val BASE_URL = "https://api.convertio.co/convert"

    /**
     * Initiate a PDF conversion. If API key is not set, shows the key dialog first.
     */
    fun convertToPdf(context: Context, filePath: String) {
        val apiKey = globalClass.preferencesManager.convertioApiKey
        if (apiKey.isBlank()) {
            pendingFilePath = filePath
            pendingContext = context
            showApiKeyDialog = true
        } else {
            pendingFilePath = filePath
            pendingContext = context
            showProgressDialog = true
        }
    }

    /**
     * Execute the conversion after API key is confirmed.
     */
    suspend fun executeConversion(context: Context, filePath: String, apiKey: String) {
        isConverting = true
        conversionProgress = 0f
        conversionStatus = "Preparing file…"

        try {
            val file = File(filePath)
            if (!file.exists()) {
                throw Exception("File not found: $filePath")
            }

            // Step 1: Encode file to Base64
            conversionStatus = "Encoding file…"
            conversionProgress = 0.1f
            val fileBytes = withContext(Dispatchers.IO) { file.readBytes() }
            val base64Data = Base64.encodeToString(fileBytes, Base64.NO_WRAP)

            // Step 2: Start conversion
            conversionStatus = "Starting conversion…"
            conversionProgress = 0.2f

            val extension = file.extension.ifEmpty { "txt" }
            val requestBody = JSONObject().apply {
                put("apikey", apiKey)
                put("input", "base64")
                put("file", base64Data)
                put("filename", file.name)
                put("outputformat", "pdf")
            }.toString()

            val startRequest = Request.Builder()
                .url(BASE_URL)
                .post(requestBody.toRequestBody("application/json".toMediaType()))
                .build()

            val startResponse = withContext(Dispatchers.IO) {
                client.newCall(startRequest).execute()
            }

            val startJson = JSONObject(startResponse.body?.string() ?: "")
            if (startJson.optInt("code") != 200) {
                throw Exception("Convertio API error: ${startJson.optString("error", "Unknown error")}")
            }

            val conversionId = startJson.getJSONObject("data").getString("id")

            // Step 3: Poll for completion
            conversionStatus = "Converting…"
            var finished = false
            var downloadUrl = ""

            while (!finished) {
                kotlinx.coroutines.delay(2000)
                conversionProgress = minOf(conversionProgress + 0.05f, 0.85f)

                val statusRequest = Request.Builder()
                    .url("$BASE_URL/$conversionId/status")
                    .get()
                    .build()

                val statusResponse = withContext(Dispatchers.IO) {
                    client.newCall(statusRequest).execute()
                }

                val statusJson = JSONObject(statusResponse.body?.string() ?: "")
                val data = statusJson.optJSONObject("data")
                val step = data?.optString("step", "") ?: ""

                when (step) {
                    "finish" -> {
                        finished = true
                        downloadUrl = data?.getJSONObject("output")?.getString("url") ?: ""
                    }
                    "convert" -> conversionStatus = "Converting document…"
                    "upload" -> conversionStatus = "Processing upload…"
                    else -> if (step.isNotEmpty()) conversionStatus = "Status: $step"
                }
            }

            // Step 4: Download the PDF
            conversionStatus = "Downloading PDF…"
            conversionProgress = 0.9f

            val downloadRequest = Request.Builder()
                .url(downloadUrl)
                .get()
                .build()

            val downloadResponse = withContext(Dispatchers.IO) {
                client.newCall(downloadRequest).execute()
            }

            val pdfBytes = withContext(Dispatchers.IO) {
                downloadResponse.body?.bytes() ?: throw Exception("Empty response")
            }

            // Step 5: Save and open PDF
            val pdfFile = File(
                globalClass.cleanOnExitDir.file,
                "${file.nameWithoutExtension}_converted.pdf"
            )
            withContext(Dispatchers.IO) {
                FileOutputStream(pdfFile).use { it.write(pdfBytes) }
            }

            conversionProgress = 1.0f
            conversionStatus = "Done!"

            // Open the converted PDF
            openPdfFile(context, pdfFile)

        } catch (e: CancellationException) {
            conversionStatus = "Cancelled"
        } catch (e: Exception) {
            conversionStatus = "Error: ${e.message}"
            globalClass.showMsg("Conversion failed: ${e.message}")
        } finally {
            isConverting = false
            showProgressDialog = false
        }
    }

    private fun openPdfFile(context: Context, pdfFile: File) {
        try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                pdfFile
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/pdf")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            globalClass.showMsg("Could not open PDF: ${e.message}")
        }
    }

    fun onApiKeyConfirmed(apiKey: String) {
        globalClass.preferencesManager.convertioApiKey = apiKey
        showApiKeyDialog = false
        val ctx = pendingContext
        val path = pendingFilePath
        if (ctx != null && path != null) {
            showProgressDialog = true
        }
    }

    fun cancelConversion() {
        showProgressDialog = false
        isConverting = false
        pendingFilePath = null
        pendingContext = null
    }

    fun getPendingFilePath(): String? = pendingFilePath
    fun getPendingContext(): Context? = pendingContext
}

/**
 * Dialog to input and save the Convertio API key.
 */
@Composable
fun ConvertioApiKeyDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var apiKey by mutableStateOf(globalClass.preferencesManager.convertioApiKey)

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.PictureAsPdf, contentDescription = null) },
        title = { Text("Convertio API Key") },
        text = {
            Column {
                Text(
                    "Enter your Convertio API key to convert documents to PDF. " +
                            "Get a free key at convertio.co/api",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text("API Key") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(apiKey) },
                enabled = apiKey.isNotBlank()
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

/**
 * Progress dialog shown during Convertio PDF conversion.
 */
@Composable
fun ConvertioProgressDialog(
    onCancel: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { /* non-dismissable during conversion */ },
        icon = { Icon(Icons.Rounded.PictureAsPdf, contentDescription = null) },
        title = { Text("Converting to PDF") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    ConvertioService.conversionStatus,
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(16.dp))
                if (ConvertioService.conversionProgress > 0) {
                    LinearProgressIndicator(
                        progress = { ConvertioService.conversionProgress },
                        modifier = Modifier.fillMaxWidth()
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
