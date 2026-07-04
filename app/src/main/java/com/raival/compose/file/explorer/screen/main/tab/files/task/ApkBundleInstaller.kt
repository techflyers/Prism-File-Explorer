package com.raival.compose.file.explorer.screen.main.tab.files.task

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.os.Environment
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.raival.compose.file.explorer.App.Companion.globalClass
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipInputStream

/**
 * Handles installation of split APK bundles (.xapk, .apkm, .apks).
 * Ported from NFile's apk_installer_service.dart.
 *
 * These bundles are ZIP archives containing:
 * - Multiple .apk files (base + splits)
 * - Optional .obb files
 */
object ApkBundleInstaller {

    var isInstalling by mutableStateOf(false)
        private set
    var installProgress by mutableFloatStateOf(0f)
        private set
    var installStatus by mutableStateOf("")
        private set

    /**
     * Install an APK bundle file.
     *
     * @param context Activity context (needed for PackageInstaller session)
     * @param bundlePath Path to the .xapk/.apkm/.apks file
     */
    suspend fun install(context: Context, bundlePath: String) {
        isInstalling = true
        installProgress = 0f
        installStatus = "Extracting bundle…"

        try {
            // Step 1: Extract the bundle to a temp directory
            val tempDir = File(context.cacheDir, "apk_bundle_${System.currentTimeMillis()}")
            tempDir.mkdirs()

            installStatus = "Extracting APK files…"
            val apkFiles = mutableListOf<File>()
            val obbFiles = mutableListOf<File>()

            withContext(Dispatchers.IO) {
                ZipInputStream(FileInputStream(bundlePath)).use { zis ->
                    var entry = zis.nextEntry
                    var totalEntries = 0

                    while (entry != null) {
                        totalEntries++
                        val outFile = File(tempDir, entry.name)

                        if (entry.isDirectory) {
                            outFile.mkdirs()
                        } else {
                            outFile.parentFile?.mkdirs()
                            FileOutputStream(outFile).use { fos ->
                                val buffer = ByteArray(8192)
                                var len: Int
                                while (zis.read(buffer).also { len = it } > 0) {
                                    fos.write(buffer, 0, len)
                                }
                            }

                            when {
                                outFile.extension.equals("apk", true) -> apkFiles.add(outFile)
                                outFile.extension.equals("obb", true) -> obbFiles.add(outFile)
                            }
                        }

                        installProgress = 0.3f * (totalEntries.toFloat() / maxOf(totalEntries, 1))
                        zis.closeEntry()
                        entry = zis.nextEntry
                    }
                }
            }

            if (apkFiles.isEmpty()) {
                throw Exception("No APK files found in the bundle")
            }

            // Step 2: Copy OBB files if any
            if (obbFiles.isNotEmpty()) {
                installStatus = "Copying OBB files…"
                installProgress = 0.4f

                for (obb in obbFiles) {
                    // OBB files go to Android/obb/<package>/
                    // Try to extract package name from the obb filename
                    val obbName = obb.name // e.g., main.123.com.example.game.obb
                    val parts = obbName.split(".")
                    if (parts.size >= 4) {
                        // Package name is parts[2..n-1] (removing main/patch, version, obb)
                        val packageName = parts.drop(2).dropLast(1).joinToString(".")
                        val obbDir = File(
                            Environment.getExternalStorageDirectory(),
                            "Android/obb/$packageName"
                        )
                        obbDir.mkdirs()
                        withContext(Dispatchers.IO) {
                            obb.copyTo(File(obbDir, obb.name), overwrite = true)
                        }
                    }
                }
            }

            // Step 3: Install APK(s)
            installProgress = 0.5f

            if (apkFiles.size == 1) {
                // Single APK: use standard ACTION_VIEW intent
                installStatus = "Installing APK…"
                installSingleApk(context, apkFiles.first())
            } else {
                // Multiple APKs: use PackageInstaller session API
                installStatus = "Installing split APKs…"
                installSplitApks(context, apkFiles)
            }

            installProgress = 1.0f
            installStatus = "Installation started"

            // Clean up temp directory
            tempDir.deleteRecursively()

        } catch (e: Exception) {
            installStatus = "Error: ${e.message}"
            globalClass.showMsg("Installation failed: ${e.message}")
        } finally {
            isInstalling = false
        }
    }

    private fun installSingleApk(context: Context, apkFile: File) {
        val uri = androidx.core.content.FileProvider.getUriForFile(
            context,
            "${context.packageName}.provider",
            apkFile
        )

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    private suspend fun installSplitApks(context: Context, apkFiles: List<File>) {
        withContext(Dispatchers.IO) {
            val packageInstaller = context.packageManager.packageInstaller
            val params = PackageInstaller.SessionParams(
                PackageInstaller.SessionParams.MODE_FULL_INSTALL
            )

            val totalSize = apkFiles.sumOf { it.length() }
            params.setSize(totalSize)

            val sessionId = packageInstaller.createSession(params)
            val session = packageInstaller.openSession(sessionId)

            try {
                for ((index, apkFile) in apkFiles.withIndex()) {
                    installStatus = "Writing APK ${index + 1}/${apkFiles.size}…"
                    installProgress = 0.5f + (0.4f * index / apkFiles.size)

                    FileInputStream(apkFile).use { input ->
                        session.openWrite(apkFile.name, 0, apkFile.length()).use { output ->
                            val buffer = ByteArray(8192)
                            var len: Int
                            while (input.read(buffer).also { len = it } > 0) {
                                output.write(buffer, 0, len)
                            }
                            session.fsync(output)
                        }
                    }
                }

                // Commit the session
                val intent = Intent(context, context::class.java)
                val pendingIntent = PendingIntent.getActivity(
                    context,
                    sessionId,
                    intent,
                    PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
                session.commit(pendingIntent.intentSender)

            } catch (e: Exception) {
                session.abandon()
                throw e
            }
        }
    }

    /**
     * Check if a file is an APK bundle format.
     */
    fun isApkBundle(extension: String): Boolean {
        return extension.lowercase() in setOf("xapk", "apkm", "apks")
    }
}
