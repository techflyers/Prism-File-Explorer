package com.raival.compose.file.explorer.screen.viewer.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import androidx.activity.compose.setContent
import com.raival.compose.file.explorer.common.showMsg
import com.raival.compose.file.explorer.common.ui.SafeSurface
import com.raival.compose.file.explorer.screen.viewer.ViewerActivity
import com.raival.compose.file.explorer.screen.viewer.ViewerInstance
import com.raival.compose.file.explorer.screen.viewer.image.ui.ImageEditorScreen
import com.raival.compose.file.explorer.theme.FileExplorerTheme
import java.io.File
import java.io.FileOutputStream

class ImageEditorActivity : ViewerActivity() {

    override fun onCreateNewInstance(uri: Uri, uid: String): ViewerInstance {
        return ImageViewerInstance(uri, uid)
    }

    override fun onReady(instance: ViewerInstance) {
        val uri = instance.uri
        val extraPath = intent.getStringExtra("extra_file_path")
        val bitmap = loadBitmapFromUri(this, uri)

        if (bitmap == null) {
            showMsg("Failed to load image for editing.")
            finish()
            return
        }

        setContent {
            FileExplorerTheme {
                SafeSurface(enableStatusBarsPadding = false) {
                    ImageEditorScreen(
                        originalBitmap = bitmap,
                        onSave = { editedBitmap, overwrite ->
                            val success = if (overwrite) {
                                saveBitmapToUri(this, editedBitmap, uri)
                            } else {
                                val savedUri = saveBitmapAsCopy(this, editedBitmap, uri, extraPath)
                                savedUri != null
                            }
                            
                            if (success) {
                                showMsg(if (overwrite) "Changes saved successfully!" else "Saved as a copy!")
                                finish()
                            } else {
                                showMsg("Failed to save edited image.")
                            }
                        },
                        onCancel = {
                            finish()
                        }
                    )
                }
            }
        }
    }

    private fun loadBitmapFromUri(context: Context, uri: Uri): Bitmap? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                // To prevent OutOfMemory on huge images, decode with scaling if necessary,
                // but let's keep it simple and high quality.
                val options = BitmapFactory.Options().apply {
                    inMutable = true // Allow in-place edits/canvas draws
                }
                BitmapFactory.decodeStream(inputStream, null, options)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun saveBitmapToUri(context: Context, bitmap: Bitmap, uri: Uri): Boolean {
        return try {
            val type = context.contentResolver.getType(uri) ?: "image/jpeg"
            val format = when {
                type.contains("png") -> Bitmap.CompressFormat.PNG
                type.contains("webp") -> Bitmap.CompressFormat.WEBP
                else -> Bitmap.CompressFormat.JPEG
            }
            context.contentResolver.openOutputStream(uri, "wt")?.use { outputStream ->
                bitmap.compress(format, 95, outputStream)
            } != null
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun saveBitmapAsCopy(context: Context, bitmap: Bitmap, originalUri: Uri, originalPath: String?): Uri? {
        return try {
            val originalFile = originalPath?.let { File(it) }
            val parentDir = originalFile?.parentFile ?: Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
            if (!parentDir.exists()) {
                parentDir.mkdirs()
            }
            
            val nameWithoutExtension = originalFile?.nameWithoutExtension ?: "edited_image"
            val extension = originalFile?.extension ?: "jpg"

            var targetFile = File(parentDir, "${nameWithoutExtension}_edited.${extension}")
            var counter = 1
            while (targetFile.exists()) {
                targetFile = File(parentDir, "${nameWithoutExtension}_edited_${counter}.${extension}")
                counter++
            }

            FileOutputStream(targetFile).use { out ->
                val format = when (extension.lowercase()) {
                    "png" -> Bitmap.CompressFormat.PNG
                    "webp" -> Bitmap.CompressFormat.WEBP
                    else -> Bitmap.CompressFormat.JPEG
                }
                bitmap.compress(format, 95, out)
            }

            // Tell MediaScanner about the new file so it shows in explorer/gallery immediately
            MediaScannerConnection.scanFile(context, arrayOf(targetFile.absolutePath), null, null)
            Uri.fromFile(targetFile)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
