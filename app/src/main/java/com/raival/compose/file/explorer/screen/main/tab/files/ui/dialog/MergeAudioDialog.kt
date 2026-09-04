package com.raival.compose.file.explorer.screen.main.tab.files.ui.dialog

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
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
import androidx.compose.ui.semantics.Role
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
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

@Composable
fun MergeAudioDialog(
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

    val allWav = remember(orderedFiles) {
        orderedFiles.isNotEmpty() && orderedFiles.all { it.file.extension.equals("wav", ignoreCase = true) }
    }
    val allMp3 = remember(orderedFiles) {
        orderedFiles.isNotEmpty() && orderedFiles.all { it.file.extension.equals("mp3", ignoreCase = true) }
    }

    var selectedFormat by remember {
        mutableStateOf(
            when {
                allWav -> "wav"
                allMp3 -> "mp3"
                else -> "m4a"
            }
        )
    }

    var outputName by remember(selectedFormat) {
        mutableStateOf("merged_${System.currentTimeMillis()}.$selectedFormat")
    }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text("Merge Audio Files") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "Track Order (${orderedFiles.size} tracks):",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Tracks will be stitched sequentially in this order.",
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

                Text(
                    text = "Output Format:",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Column(modifier = Modifier.selectableGroup()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = selectedFormat == "m4a",
                                onClick = {
                                    selectedFormat = "m4a"
                                    outputName = "${outputName.substringBeforeLast('.')}.m4a"
                                },
                                role = Role.RadioButton
                            )
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = selectedFormat == "m4a", onClick = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("M4A / AAC Container (.m4a)")
                    }

                    if (allMp3) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = selectedFormat == "mp3",
                                    onClick = {
                                        selectedFormat = "mp3"
                                        outputName = "${outputName.substringBeforeLast('.')}.mp3"
                                    },
                                    role = Role.RadioButton
                                )
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = selectedFormat == "mp3", onClick = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("MP3 Direct Audio (.mp3)")
                        }
                    }

                    if (allWav) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = selectedFormat == "wav",
                                    onClick = {
                                        selectedFormat = "wav"
                                        outputName = "${outputName.substringBeforeLast('.')}.wav"
                                    },
                                    role = Role.RadioButton
                                )
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = selectedFormat == "wav", onClick = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Uncompressed WAV (.wav)")
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
                        Text("Concatenating audio streams...")
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
                                throw Exception("Select at least 2 audio files")
                            }

                            val parentDir = localPaths.first().parentFile ?: File("/")
                            val finalOutputName = if (outputName.isBlank()) "merged_${System.currentTimeMillis()}.$selectedFormat" else outputName.trim()
                            val outputFile = File(parentDir, if (finalOutputName.endsWith(".$selectedFormat", ignoreCase = true)) finalOutputName else "$finalOutputName.$selectedFormat")

                            withContext(Dispatchers.IO) {
                                when (selectedFormat) {
                                    "wav" -> mergeWavFiles(localPaths, outputFile)
                                    "mp3" -> mergeMp3Files(localPaths, outputFile)
                                    else -> mergeAudioMuxer(localPaths, outputFile)
                                }
                            }

                            withContext(Dispatchers.Main) {
                                isProcessing = false
                                Toast.makeText(context, "Audio merged: ${outputFile.name}", Toast.LENGTH_LONG).show()
                                tab.unselectAllFiles()
                                tab.reloadFiles()
                                onDismissRequest()
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                            withContext(Dispatchers.Main) {
                                isProcessing = false
                                Toast.makeText(context, "Audio merge failed: ${e.message}", Toast.LENGTH_LONG).show()
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
 * Merge audio files using MediaExtractor and MediaMuxer into an MP4/M4A container.
 */
private fun mergeAudioMuxer(files: List<File>, outputFile: File) {
    var muxer: MediaMuxer? = null
    var cumulativeDurationUs = 0L

    try {
        // Inspect first audio format
        val firstExtractor = MediaExtractor()
        firstExtractor.setDataSource(files.first().absolutePath)
        var audioTrackIndex = -1
        var audioFormat: MediaFormat? = null

        for (i in 0 until firstExtractor.trackCount) {
            val format = firstExtractor.getTrackFormat(i)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
            if (mime.startsWith("audio/")) {
                audioTrackIndex = i
                audioFormat = format
                break
            }
        }
        firstExtractor.release()

        if (audioTrackIndex == -1 || audioFormat == null) {
            throw Exception("Could not find audio track in ${files.first().name}")
        }

        muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        val muxerAudioTrack = muxer.addTrack(audioFormat)
        muxer.start()

        val bufferSize = 1024 * 512
        val byteBuffer = ByteBuffer.allocateDirect(bufferSize)
        val bufferInfo = MediaCodec.BufferInfo()

        for (file in files) {
            val extractor = MediaExtractor()
            extractor.setDataSource(file.absolutePath)

            var trackIndex = -1
            for (i in 0 until extractor.trackCount) {
                val mime = extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("audio/")) {
                    trackIndex = i
                    break
                }
            }

            if (trackIndex == -1) {
                extractor.release()
                continue
            }

            extractor.selectTrack(trackIndex)
            var lastPtsUs = 0L

            while (true) {
                val sampleSize = extractor.readSampleData(byteBuffer, 0)
                if (sampleSize < 0) break

                val sampleTime = extractor.sampleTime
                bufferInfo.offset = 0
                bufferInfo.size = sampleSize
                bufferInfo.presentationTimeUs = cumulativeDurationUs + sampleTime
                bufferInfo.flags = extractor.sampleFlags

                muxer.writeSampleData(muxerAudioTrack, byteBuffer, bufferInfo)
                lastPtsUs = sampleTime
                extractor.advance()
            }

            // Estimate track duration
            val fileFormat = extractor.getTrackFormat(trackIndex)
            val trackDuration = if (fileFormat.containsKey(MediaFormat.KEY_DURATION)) {
                fileFormat.getLong(MediaFormat.KEY_DURATION)
            } else {
                lastPtsUs + 25_000L
            }

            cumulativeDurationUs += trackDuration
            extractor.release()
        }
    } finally {
        try {
            muxer?.stop()
            muxer?.release()
        } catch (_: Exception) {}
    }
}

/**
 * Merge standard WAV audio files into a single WAV file.
 */
private fun mergeWavFiles(files: List<File>, outputFile: File) {
    if (files.isEmpty()) return

    // Read header from first file (first 44 bytes)
    val header = ByteArray(44)
    FileInputStream(files.first()).use { input ->
        val read = input.read(header)
        if (read < 44) throw Exception("Invalid WAV header")
    }

    var totalAudioBytes = 0L
    for (file in files) {
        val audioBytes = maxOf(0L, file.length() - 44)
        totalAudioBytes += audioBytes
    }

    val totalDataLen = totalAudioBytes + 36
    val byteBuffer = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
    byteBuffer.putInt(4, totalDataLen.toInt())
    byteBuffer.putInt(40, totalAudioBytes.toInt())

    FileOutputStream(outputFile).use { out ->
        out.write(header)
        val copyBuffer = ByteArray(64 * 1024)
        for (file in files) {
            FileInputStream(file).use { inStream ->
                inStream.skip(44) // Skip header
                var len: Int
                while (inStream.read(copyBuffer).also { len = it } > 0) {
                    out.write(copyBuffer, 0, len)
                }
            }
        }
    }
}

/**
 * Merge MP3 files by stripping ID3 tags and concatenating MPEG audio frames.
 */
private fun mergeMp3Files(files: List<File>, outputFile: File) {
    FileOutputStream(outputFile).use { out ->
        val buffer = ByteArray(64 * 1024)
        for (file in files) {
            FileInputStream(file).use { inStream ->
                // Check for ID3v2 tag
                val id3Header = ByteArray(10)
                val read = inStream.read(id3Header)
                var skipBytes = 0L

                if (read == 10 && id3Header[0] == 'I'.code.toByte() && id3Header[1] == 'D'.code.toByte() && id3Header[2] == '3'.code.toByte()) {
                    // Syncsafe integer for size
                    val size = ((id3Header[6].toInt() and 0x7F) shl 21) or
                            ((id3Header[7].toInt() and 0x7F) shl 14) or
                            ((id3Header[8].toInt() and 0x7F) shl 7) or
                            (id3Header[9].toInt() and 0x7F)
                    skipBytes = size.toLong()
                } else {
                    // Re-read from beginning
                    inStream.channel.position(0)
                }

                if (skipBytes > 0) {
                    inStream.skip(skipBytes)
                }

                var len: Int
                while (inStream.read(buffer).also { len = it } > 0) {
                    out.write(buffer, 0, len)
                }
            }
        }
    }
}
