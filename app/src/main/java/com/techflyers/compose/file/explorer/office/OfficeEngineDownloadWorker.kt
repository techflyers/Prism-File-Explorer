package com.techflyers.compose.file.explorer.office

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.techflyers.compose.file.explorer.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import org.tukaani.xz.XZInputStream
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlin.coroutines.coroutineContext

class OfficeEngineDownloadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    companion object {
        const val UNIQUE_NAME = "office-engine-install"
        const val KEY_PHASE = "phase"
        const val KEY_DONE = "done"
        const val KEY_TOTAL = "total"
        const val KEY_ERROR = "error"
        const val PHASE_DOWNLOAD = "download"
        const val PHASE_INSTALL = "install"

        private const val CHANNEL_ID = "office_engine"
        private const val NOTIFICATION_ID = 0x0FF1CE
        private const val BUFFER = 1 shl 16
        private const val XZ_MEMORY_LIMIT_KIB = 300 * 1024
    }

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val bundle = OfficeEngineManifest.bundleForDevice()
            ?: return@withContext Result.failure(workDataOf(KEY_ERROR to "This device is not supported"))
        try {
            safeSetForeground(foregroundInfo(0, bundle.sizeBytes, indeterminate = true, installing = false))
            val archive = download(bundle)
            setProgress(workDataOf(KEY_PHASE to PHASE_INSTALL))
            safeSetForeground(foregroundInfo(0, 0, indeterminate = true, installing = true))
            install(archive, bundle.abi)
            archive.delete()
            Result.success()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: OutOfMemoryError) {
            OfficeEngineStore.stagingDir(applicationContext).deleteRecursively()
            Result.failure(workDataOf(KEY_ERROR to "Not enough memory to unpack the engine on this device"))
        } catch (e: Exception) {
            OfficeEngineStore.stagingDir(applicationContext).deleteRecursively()
            Result.failure(workDataOf(KEY_ERROR to (e.message ?: e.javaClass.simpleName)))
        }
    }

    private suspend fun download(bundle: OfficeEngineManifest.Bundle): File {
        val dir = OfficeEngineStore.downloadDir(applicationContext).apply { mkdirs() }
        dir.listFiles()?.filter { !it.name.startsWith(bundle.sha256) }?.forEach { it.delete() }
        val part = File(dir, "${bundle.sha256}.part")
        val digest = MessageDigest.getInstance("SHA-256")

        if (part.length() > bundle.sizeBytes) part.delete()
        var have = part.length()
        if (have > 0) part.inputStream().use { input -> hashInto(digest, input) }

        if (have < bundle.sizeBytes) {
            val conn = URL(bundle.url).openConnection() as HttpURLConnection
            conn.instanceFollowRedirects = true
            conn.connectTimeout = 20_000
            conn.readTimeout = 30_000
            if (have > 0) conn.setRequestProperty("Range", "bytes=$have-")

            val code = conn.responseCode
            val append = code == HttpURLConnection.HTTP_PARTIAL
            if (!append && code != HttpURLConnection.HTTP_OK) {
                throw IOException("Server returned HTTP $code")
            }
            if (!append) {
                have = 0
                digest.reset()
            }

            val total = bundle.sizeBytes
            var lastUpdate = 0L
            conn.inputStream.use { input ->
                FileOutputStream(part, append).use { out ->
                    val buf = ByteArray(BUFFER)
                    while (coroutineContext.isActive) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        digest.update(buf, 0, n)
                        have += n
                        val now = System.currentTimeMillis()
                        if (now - lastUpdate > 300) {
                            lastUpdate = now
                            setProgress(workDataOf(KEY_PHASE to PHASE_DOWNLOAD, KEY_DONE to have, KEY_TOTAL to total))
                            safeSetForeground(foregroundInfo(have, total, indeterminate = false, installing = false))
                        }
                    }
                }
            }
            coroutineContext.ensureActive()
        }

        check(have == bundle.sizeBytes) { "Download incomplete: expected ${bundle.sizeBytes}, got $have" }
        val sha = digest.digest().joinToString("") { "%02x".format(it) }
        check(sha.equals(bundle.sha256, ignoreCase = true)) {
            part.delete()
            "SHA-256 mismatch (expected ${bundle.sha256}, got $sha)"
        }
        val complete = File(dir, "${bundle.sha256}.tar.xz")
        if (!part.renameTo(complete)) throw IOException("Cannot finalize archive")
        return complete
    }

    private suspend fun install(archive: File, abi: String) {
        val staging = OfficeEngineStore.resetStaging(applicationContext)
        archive.inputStream().use { fileIn ->
            BufferedInputStream(fileIn, BUFFER).use { buffered ->
                XZInputStream(buffered, XZ_MEMORY_LIMIT_KIB).use { xz ->
                    EngineArchiveLayout.extractTar(xz, staging, abi) { !coroutineContext.isActive }
                }
            }
        }
        coroutineContext.ensureActive()
        OfficeEngineStore.commit(applicationContext, staging, abi)
    }

    private fun hashInto(digest: MessageDigest, input: java.io.InputStream) {
        val buf = ByteArray(BUFFER)
        while (true) {
            val n = input.read(buf)
            if (n < 0) break
            digest.update(buf, 0, n)
        }
    }

    private fun foregroundInfo(done: Long, total: Long, indeterminate: Boolean, installing: Boolean): ForegroundInfo {
        ensureNotificationChannel()
        val title = if (installing) "Installing LibreOffice Engine…" else "Downloading LibreOffice Engine…"
        val percent = if (!indeterminate && total > 0) ((done * 100) / total).toInt() else 0
        val text = if (installing) {
            "Setting up office document renderer"
        } else if (!indeterminate && total > 0) {
            "$percent% (${done / (1024 * 1024)} MB / ${total / (1024 * 1024)} MB)"
        } else {
            "Connecting…"
        }

        val builder = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Cancel",
                androidx.work.WorkManager.getInstance(applicationContext).createCancelPendingIntent(id)
            )

        if (installing || indeterminate) {
            builder.setProgress(0, 0, true)
        } else {
            builder.setProgress(100, percent, false)
        }

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(NOTIFICATION_ID, builder.build(), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIFICATION_ID, builder.build())
        }
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Office Engine Downloads",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Download and installation progress for LibreOffice document engine"
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private suspend fun safeSetForeground(info: ForegroundInfo) {
        runCatching {
            setForeground(info)
        }
    }
}
