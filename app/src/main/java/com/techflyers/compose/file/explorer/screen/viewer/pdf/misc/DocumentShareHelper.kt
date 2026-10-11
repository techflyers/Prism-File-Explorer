package com.techflyers.compose.file.explorer.screen.viewer.pdf.misc

import android.app.SearchManager
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File

object DocumentShareHelper {

    /**
     * Obtains a shareable FileProvider URI with an authentic, clean file name.
     * If [desiredFileName] is given, or if the URI points to an internal temporary working file,
     * copies it into a uniquely timestamped folder inside cacheDir/shared_documents/
     * so receiving apps display the authentic file name.
     */
    fun getShareableUri(context: Context, uri: Uri, desiredFileName: String? = null): Uri {
        val resolvedDesiredName = desiredFileName?.trim()?.ifEmpty { null }
        val lastSegment = uri.lastPathSegment ?: ""
        val isTempName = lastSegment.startsWith("pdf_working_")
                || lastSegment.startsWith("compressed_")
                || lastSegment.startsWith("watermarked_")
                || lastSegment.startsWith("organized_")

        if (resolvedDesiredName != null || isTempName) {
            val cleanName = resolvedDesiredName ?: run {
                when {
                    lastSegment.startsWith("pdf_working_") -> {
                        val afterPrefix = lastSegment.substringAfter("pdf_working_").substringAfter("_", "")
                        if (afterPrefix.isNotEmpty()) {
                            if (afterPrefix.endsWith(".pdf", ignoreCase = true)) afterPrefix else "$afterPrefix.pdf"
                        } else "Document.pdf"
                    }
                    lastSegment.startsWith("compressed_") -> "Compressed_Document.pdf"
                    lastSegment.startsWith("watermarked_") -> "Watermarked_Document.pdf"
                    lastSegment.startsWith("organized_") -> "Organized_Document.pdf"
                    else -> lastSegment
                }
            }

            val sanitizedName = if (cleanName.endsWith(".pdf", ignoreCase = true)) cleanName else "$cleanName.pdf"
            val shareDir = File(context.cacheDir, "shared_documents/${System.currentTimeMillis()}").apply { mkdirs() }
            val cleanFile = File(shareDir, sanitizedName)

            try {
                if (uri.scheme == "file") {
                    val src = File(uri.path ?: "")
                    if (src.exists()) {
                        src.copyTo(cleanFile, overwrite = true)
                    }
                } else {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        cleanFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                }

                if (cleanFile.exists() && cleanFile.length() > 0) {
                    return FileProvider.getUriForFile(context, "${context.packageName}.provider", cleanFile)
                }
            } catch (_: Exception) {
                // fallback to default resolution below
            }
        }

        if (uri.scheme == "content") return uri
        if (uri.scheme == "file") {
            val path = uri.path ?: return uri
            val file = File(path)
            if (file.exists()) {
                return runCatching {
                    FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.provider",
                        file
                    )
                }.getOrDefault(uri)
            }
        }
        return uri
    }

    /**
     * Resolves the parent directory where the document originated from.
     * Supports file:// URIs, FileProvider content URIs (/storage_root/..., /external/...),
     * and MediaStore content URIs. Defaults to the public Downloads folder.
     */
    fun resolveTargetDirectory(context: Context, uri: Uri?): File {
        if (uri != null) {
            if (uri.scheme == "file") {
                val f = uri.path?.let { File(it) }
                if (f != null && f.parentFile != null && f.parentFile!!.exists() && f.parentFile!!.canWrite()) {
                    return f.parentFile!!
                }
            }

            val path = uri.path
            if (path != null) {
                val candidates = mutableListOf<String>()
                if (path.startsWith("/storage_root/")) {
                    candidates.add(path.removePrefix("/storage_root"))
                }
                if (path.startsWith("/external/")) {
                    val extDir = Environment.getExternalStorageDirectory().absolutePath
                    candidates.add("$extDir/${path.removePrefix("/external/")}")
                }
                candidates.add(path)

                for (cand in candidates) {
                    val f = File(cand)
                    val dir = if (f.isDirectory) f else f.parentFile
                    if (dir != null && dir.exists() && dir.canWrite()) {
                        return dir
                    }
                }
            }

            if (uri.scheme == "content") {
                try {
                    val projection = arrayOf(MediaStore.MediaColumns.DATA)
                    context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val colIdx = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)
                            if (colIdx >= 0) {
                                val dataPath = cursor.getString(colIdx)
                                if (!dataPath.isNullOrEmpty()) {
                                    val f = File(dataPath)
                                    val dir = f.parentFile
                                    if (dir != null && dir.exists() && dir.canWrite()) {
                                        return dir
                                    }
                                }
                            }
                        }
                    }
                } catch (_: Exception) {}
            }
        }

        val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        if (downloads.exists() && downloads.canWrite()) return downloads
        return context.filesDir
    }

    /**
     * Resolves a direct writable File instance for an in-place overwrite, if accessible.
     */
    fun resolveLocalFile(context: Context, uri: Uri?): File? {
        if (uri == null) return null
        if (uri.scheme == "file") {
            val f = uri.path?.let { File(it) }
            if (f != null && f.exists()) return f
        }
        val path = uri.path
        if (path != null) {
            val candidates = mutableListOf<String>()
            if (path.startsWith("/storage_root/")) {
                candidates.add(path.removePrefix("/storage_root"))
            }
            if (path.startsWith("/external/")) {
                val extDir = Environment.getExternalStorageDirectory().absolutePath
                candidates.add("$extDir/${path.removePrefix("/external/")}")
            }
            candidates.add(path)
            for (cand in candidates) {
                val f = File(cand)
                if (f.exists() && f.isFile) return f
            }
        }
        if (uri.scheme == "content") {
            try {
                val projection = arrayOf(MediaStore.MediaColumns.DATA)
                context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val colIdx = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)
                        if (colIdx >= 0) {
                            val dataPath = cursor.getString(colIdx)
                            if (!dataPath.isNullOrEmpty()) {
                                val f = File(dataPath)
                                if (f.exists() && f.isFile) return f
                            }
                        }
                    }
                }
            } catch (_: Exception) {}
        }
        return null
    }

    fun openWith(
        context: Context,
        uri: Uri,
        mimeType: String = "application/pdf",
        title: String = "Open with"
    ) {
        val shareableUri = getShareableUri(context, uri)
        val openIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(shareableUri, mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            clipData = ClipData.newRawUri("Document", shareableUri)
        }
        val chooser = Intent.createChooser(openIntent, title).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching {
            context.startActivity(chooser)
        }.onFailure { _ ->
            Toast.makeText(context, "No app available to open this document", Toast.LENGTH_SHORT).show()
        }
    }

    fun shareDocument(
        context: Context,
        uri: Uri,
        titleOrMimeType: String = "Share",
        title: String = "Share",
        mimeType: String = "application/pdf",
        desiredFileName: String? = null
    ) {
        val actualMimeType = if (titleOrMimeType.contains("/")) titleOrMimeType else mimeType
        val actualTitle = if (!titleOrMimeType.contains("/")) titleOrMimeType else title
        val shareableUri = getShareableUri(context, uri, desiredFileName)
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = actualMimeType
            putExtra(Intent.EXTRA_STREAM, shareableUri)
            clipData = ClipData.newRawUri(desiredFileName ?: "Document", shareableUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(shareIntent, actualTitle).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching {
            context.startActivity(chooser)
        }.onFailure { _ ->
            Toast.makeText(context, "Failed to share document", Toast.LENGTH_SHORT).show()
        }
    }

    fun shareText(context: Context, text: String, title: String = "Share Text") {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        val chooser = Intent.createChooser(shareIntent, title).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching {
            context.startActivity(chooser)
        }.onFailure {
            Toast.makeText(context, "Failed to share text", Toast.LENGTH_SHORT).show()
        }
    }

    fun searchWeb(context: Context, query: String) {
        val searchIntent = Intent(Intent.ACTION_WEB_SEARCH).apply {
            putExtra(SearchManager.QUERY, query)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching {
            context.startActivity(searchIntent)
        }.onFailure {
            val browserIntent = Intent(
                Intent.ACTION_VIEW,
                Uri.parse("https://www.google.com/search?q=" + Uri.encode(query))
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            runCatching { context.startActivity(browserIntent) }
        }
    }
}
