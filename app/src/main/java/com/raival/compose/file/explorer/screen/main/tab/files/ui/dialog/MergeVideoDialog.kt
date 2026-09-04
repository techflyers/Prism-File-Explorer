package com.raival.compose.file.explorer.screen.main.tab.files.ui.dialog

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.raival.compose.file.explorer.screen.main.tab.files.FilesTab
import com.raival.compose.file.explorer.screen.main.tab.files.holder.ContentHolder
import com.raival.compose.file.explorer.screen.main.tab.files.holder.LocalFileHolder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer

@Composable
fun MergeVideoDialog(
    show: Boolean,
    targetFiles: List<ContentHolder>,
    tab: FilesTab,
    onDismissRequest: () -> Unit
) {
    if (!show) return

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var isProcessing by remember { mutableStateOf(false) }
    var orderedFiles by remember { mutableStateOf(targetFiles.filterIsInstance<LocalFileHolder>()) }
    var outputName by remember { mutableStateOf("merged_${System.currentTimeMillis()}.mp4") }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text("Merge Video Files") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "Video Sequence (${orderedFiles.size} videos):",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Videos will be stitched sequentially in this order into an MP4 container.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))

                Column(modifier = Modifier.fillMaxWidth()) {
                    orderedFiles.forEachIndexed { index, fileHolder ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${index + 1}.",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.width(24.dp)
                            )
                            Text(
                                text = fileHolder.displayName,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = {
                                    if (index > 0) {
                                        val list = orderedFiles.toMutableList()
                                        val item = list.removeAt(index)
                                        list.add(index - 1, item)
                                        orderedFiles = list
                                    }
                                },
                                enabled = index > 0 && !isProcessing
                            ) {
                                Icon(Icons.Rounded.KeyboardArrowUp, contentDescription = "Move Up")
                            }
                            IconButton(
                                onClick = {
                                    if (index < orderedFiles.size - 1) {
                                        val list = orderedFiles.toMutableList()
                                        val item = list.removeAt(index)
                                        list.add(index + 1, item)
                                        orderedFiles = list
                                    }
                                },
                                enabled = index < orderedFiles.size - 1 && !isProcessing
                            ) {
                                Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Move Down")
                            }
                            IconButton(
                                onClick = {
                                    val list = orderedFiles.toMutableList()
                                    list.removeAt(index)
                                    orderedFiles = list
                                },
                                enabled = orderedFiles.size > 2 && !isProcessing
                            ) {
                                Icon(Icons.Rounded.Close, contentDescription = "Remove")
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = outputName,
                    onValueChange = { outputName = it },
                    label = { Text("Output filename") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                if (isProcessing) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Stitching video streams...")
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    isProcessing = true
                    scope.launch {
                        try {
                            val localPaths = orderedFiles.map { it.file }
                            if (localPaths.size < 2) {
                                throw Exception("Select at least 2 video files")
                            }

                            val parentDir = localPaths.first().parentFile ?: File("/")
                            val finalOutputName = if (outputName.isBlank()) "merged_${System.currentTimeMillis()}.mp4" else outputName.trim()
                            val outputFile = File(parentDir, if (finalOutputName.endsWith(".mp4", ignoreCase = true)) finalOutputName else "$finalOutputName.mp4")

                            withContext(Dispatchers.IO) {
                                mergeVideoMuxer(localPaths, outputFile)
                            }

                            withContext(Dispatchers.Main) {
                                isProcessing = false
                                Toast.makeText(context, "Videos merged: ${outputFile.name}", Toast.LENGTH_LONG).show()
                                tab.unselectAllFiles()
                                tab.reloadFiles()
                                onDismissRequest()
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                            withContext(Dispatchers.Main) {
                                isProcessing = false
                                Toast.makeText(context, "Video merge failed: ${e.message}", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                },
                enabled = !isProcessing && orderedFiles.size >= 2
            ) {
                Text("Merge")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismissRequest,
                enabled = !isProcessing
            ) {
                Text("Cancel")
            }
        }
    )
}

/**
 * Merge video files by extracting video and audio tracks and remuxing them sequentially with adjusted timestamps.
 */
private fun mergeVideoMuxer(files: List<File>, outputFile: File) {
    var muxer: MediaMuxer? = null
    var cumulativeVideoPtsUs = 0L
    var cumulativeAudioPtsUs = 0L

    try {
        // Inspect first video tracks
        val firstExtractor = MediaExtractor()
        firstExtractor.setDataSource(files.first().absolutePath)
        var firstVideoIndex = -1
        var firstAudioIndex = -1
        var videoFormat: MediaFormat? = null
        var audioFormat: MediaFormat? = null

        for (i in 0 until firstExtractor.trackCount) {
            val format = firstExtractor.getTrackFormat(i)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
            if (mime.startsWith("video/") && firstVideoIndex == -1) {
                firstVideoIndex = i
                videoFormat = format
            } else if (mime.startsWith("audio/") && firstAudioIndex == -1) {
                firstAudioIndex = i
                audioFormat = format
            }
        }
        firstExtractor.release()

        if (firstVideoIndex == -1 || videoFormat == null) {
            throw Exception("Could not find video track in ${files.first().name}")
        }

        muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        val muxerVideoTrack = muxer.addTrack(videoFormat)
        val muxerAudioTrack = if (audioFormat != null) muxer.addTrack(audioFormat) else -1
        muxer.start()

        val bufferSize = 1024 * 1024 // 1MB buffer for video frames
        val byteBuffer = ByteBuffer.allocateDirect(bufferSize)
        val bufferInfo = MediaCodec.BufferInfo()

        for (file in files) {
            val extractor = MediaExtractor()
            extractor.setDataSource(file.absolutePath)

            var videoTrackIndex = -1
            var audioTrackIndex = -1
            for (i in 0 until extractor.trackCount) {
                val mime = extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("video/") && videoTrackIndex == -1) {
                    videoTrackIndex = i
                } else if (mime.startsWith("audio/") && audioTrackIndex == -1) {
                    audioTrackIndex = i
                }
            }

            var fileVideoDurationUs = 0L
            var fileAudioDurationUs = 0L
            var lastVideoPts = 0L
            var lastAudioPts = 0L

            // 1. Process Video Track
            if (videoTrackIndex != -1) {
                extractor.selectTrack(videoTrackIndex)
                val format = extractor.getTrackFormat(videoTrackIndex)
                if (format.containsKey(MediaFormat.KEY_DURATION)) {
                    fileVideoDurationUs = format.getLong(MediaFormat.KEY_DURATION)
                }

                while (true) {
                    val sampleSize = extractor.readSampleData(byteBuffer, 0)
                    if (sampleSize < 0) break

                    val sampleTime = extractor.sampleTime
                    bufferInfo.offset = 0
                    bufferInfo.size = sampleSize
                    bufferInfo.presentationTimeUs = cumulativeVideoPtsUs + sampleTime
                    bufferInfo.flags = extractor.sampleFlags

                    muxer.writeSampleData(muxerVideoTrack, byteBuffer, bufferInfo)
                    lastVideoPts = sampleTime
                    extractor.advance()
                }
                extractor.unselectTrack(videoTrackIndex)
            }

            // 2. Process Audio Track (if muxer has audio track)
            if (audioTrackIndex != -1 && muxerAudioTrack != -1) {
                extractor.selectTrack(audioTrackIndex)
                val format = extractor.getTrackFormat(audioTrackIndex)
                if (format.containsKey(MediaFormat.KEY_DURATION)) {
                    fileAudioDurationUs = format.getLong(MediaFormat.KEY_DURATION)
                }

                while (true) {
                    val sampleSize = extractor.readSampleData(byteBuffer, 0)
                    if (sampleSize < 0) break

                    val sampleTime = extractor.sampleTime
                    bufferInfo.offset = 0
                    bufferInfo.size = sampleSize
                    bufferInfo.presentationTimeUs = cumulativeAudioPtsUs + sampleTime
                    bufferInfo.flags = extractor.sampleFlags

                    muxer.writeSampleData(muxerAudioTrack, byteBuffer, bufferInfo)
                    lastAudioPts = sampleTime
                    extractor.advance()
                }
                extractor.unselectTrack(audioTrackIndex)
            }

            val effectiveVideoDuration = if (fileVideoDurationUs > 0) fileVideoDurationUs else lastVideoPts + 33_333L
            val effectiveAudioDuration = if (fileAudioDurationUs > 0) fileAudioDurationUs else lastAudioPts + 23_000L

            cumulativeVideoPtsUs += effectiveVideoDuration
            cumulativeAudioPtsUs += if (muxerAudioTrack != -1) effectiveAudioDuration else effectiveVideoDuration
            extractor.release()
        }
    } finally {
        try {
            muxer?.stop()
            muxer?.release()
        } catch (_: Exception) {}
    }
}
