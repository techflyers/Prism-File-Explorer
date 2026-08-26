package com.raival.compose.file.explorer.common.magika

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.reflect.TypeToken
import com.raival.compose.file.explorer.App.Companion.globalClass
import com.raival.compose.file.explorer.common.DetectedMimeType
import com.raival.compose.file.explorer.common.MimeTypeDetector
import android.util.LruCache
import java.io.File
import java.io.RandomAccessFile
import java.util.Collections

/**
 * On-device Magika file-type detector (ArDoCo / Google Magika v0.6 ONNX model).
 *
 * Used when MIME/header detection is insufficient — missing, empty, or incorrect
 * extensions. Falls back to [MimeTypeDetector] if the model cannot be loaded.
 */
object MagikaFileTypeDetector {
    private const val TAG = "Magika"
    private const val MIN_CONFIDENCE = 0.50f

    private val detectionCache = LruCache<String, CacheEntry>(1000)
    private class CacheEntry(val result: Result?)

    @Volatile
    private var session: OrtSession? = null
    private var env: OrtEnvironment? = null
    private var labels: List<String> = emptyList()
    private var overwriteMap: Map<String, String> = emptyMap()
    private var begSize = 1024
    private var midSize = 0
    private var endSize = 1024
    private var paddingToken = 256
    private var minSize = 8
    private val lock = Any()

    data class Result(
        val label: String,
        val probability: Float,
        val mimeType: String,
        val isText: Boolean,
        val suggestedExtension: String
    ) {
        fun toDetectedMimeType() = DetectedMimeType(mimeType, isText, probability)
    }

    fun getCachedResult(file: File): Result? {
        val cacheKey = "${file.absolutePath}:${file.lastModified()}:${file.length()}"
        synchronized(detectionCache) {
            return detectionCache.get(cacheKey)?.result
        }
    }

    fun detect(file: File): Result? {
        if (!file.exists() || !file.isFile || !file.canRead()) return null

        val cacheKey = "${file.absolutePath}:${file.lastModified()}:${file.length()}"
        synchronized(detectionCache) {
            val cached = detectionCache.get(cacheKey)
            if (cached != null) return cached.result
        }

        val result = performDetect(file)
        synchronized(detectionCache) {
            detectionCache.put(cacheKey, CacheEntry(result))
        }
        return result
    }

    private fun performDetect(file: File): Result? {
        val magika = detectWithMagika(file)
        if (magika != null && magika.probability >= MIN_CONFIDENCE && magika.label != "unknown") {
            return magika
        }
        val fallback = MimeTypeDetector.detect(file) ?: return magika
        return Result(
            label = fallback.mimeType.substringAfterLast('/'),
            probability = fallback.confidence,
            mimeType = fallback.mimeType,
            isText = fallback.isText,
            suggestedExtension = extensionForMime(fallback.mimeType)
        )
    }

    private fun detectWithMagika(file: File): Result? {
        ensureLoaded()
        val activeSession = session ?: return null
        val activeEnv = env ?: return null
        if (file.length() == 0L) {
            return Result("empty", 1f, "application/x-empty", false, "")
        }
        if (file.length() <= minSize.toLong()) {
            return Result("txt", 1f, "text/plain", true, "txt")
        }
        return try {
            val input = readFileToInputBuffer(file)
            val inputName = activeSession.inputNames.iterator().next()
            OnnxTensor.createTensor(activeEnv, input).use { tensor ->
                activeSession.run(Collections.singletonMap(inputName, tensor)).use { result ->
                    val probs = result[0].value as Array<FloatArray>
                    val scores = probs[0]
                    var best = 0
                    var bestScore = Float.NEGATIVE_INFINITY
                    for (i in scores.indices) {
                        if (scores[i] > bestScore) {
                            bestScore = scores[i]
                            best = i
                        }
                    }
                    var label = labels.getOrNull(best) ?: "unknown"
                    label = overwriteMap[label] ?: label
                    val mapping = mapLabel(label)
                    Result(
                        label = label,
                        probability = bestScore,
                        mimeType = mapping.mimeType,
                        isText = mapping.isText,
                        suggestedExtension = mapping.extension
                    )
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Magika inference failed", e)
            null
        }
    }

    private fun ensureLoaded() {
        if (session != null) return
        synchronized(lock) {
            if (session != null) return
            try {
                val assets = globalClass.assets
                val configText = assets.open("magika/config.json").bufferedReader().use { it.readText() }
                val root = Gson().fromJson(configText, JsonObject::class.java)
                begSize = root.get("beg_size").asInt
                midSize = root.get("mid_size").asInt
                endSize = root.get("end_size").asInt
                paddingToken = root.get("padding_token").asInt
                minSize = root.get("min_file_size_for_dl").asInt
                labels = Gson().fromJson(
                    root.get("target_labels_space"),
                    object : TypeToken<List<String>>() {}.type
                )
                overwriteMap = if (root.has("overwrite_map")) {
                    Gson().fromJson(
                        root.get("overwrite_map"),
                        object : TypeToken<Map<String, String>>() {}.type
                    )
                } else emptyMap()

                val modelBytes = assets.open("magika/model.onnx").use { it.readBytes() }
                env = OrtEnvironment.getEnvironment()
                session = env!!.createSession(modelBytes, OrtSession.SessionOptions())
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load Magika model", e)
                session = null
            }
        }
    }

    private fun readFileToInputBuffer(file: File): Array<IntArray> {
        val fileLength = file.length().toInt().coerceAtLeast(0)
        val bufferSize = begSize + midSize + endSize
        val beginningBuffer = ByteArray(minOf(fileLength, begSize))
        val midBuffer = ByteArray(minOf(fileLength, midSize))
        val endBuffer = ByteArray(minOf(fileLength, endSize))

        RandomAccessFile(file, "r").use { raf ->
            raf.read(beginningBuffer)
            if (midSize > 0) {
                val halfInputSize = Math.round(fileLength.toFloat() / 2)
                val offset = maxOf(0, halfInputSize - (midSize / 2))
                raf.seek(offset.toLong())
                raf.read(midBuffer)
            }
            val endOffset = maxOf(0, fileLength - endSize)
            raf.seek(endOffset.toLong())
            raf.read(endBuffer)
        }

        val inputArray = IntArray(bufferSize) { paddingToken }
        for (i in beginningBuffer.indices) {
            inputArray[i] = beginningBuffer[i].toInt() and 0xFF
        }
        for (i in midBuffer.indices) {
            inputArray[begSize + i] = midBuffer[i].toInt() and 0xFF
        }
        for (i in endBuffer.indices) {
            inputArray[inputArray.lastIndex - i] =
                endBuffer[endBuffer.lastIndex - i].toInt() and 0xFF
        }
        return arrayOf(inputArray)
    }

    private data class LabelMapping(
        val mimeType: String,
        val isText: Boolean,
        val extension: String
    )

    private fun mapLabel(label: String): LabelMapping {
        return when (label) {
            "png" -> LabelMapping("image/png", false, "png")
            "jpeg" -> LabelMapping("image/jpeg", false, "jpg")
            "gif" -> LabelMapping("image/gif", false, "gif")
            "webp" -> LabelMapping("image/webp", false, "webp")
            "bmp" -> LabelMapping("image/bmp", false, "bmp")
            "tiff" -> LabelMapping("image/tiff", false, "tiff")
            "ico" -> LabelMapping("image/x-icon", false, "ico")
            "svg" -> LabelMapping("image/svg+xml", true, "svg")
            "psd" -> LabelMapping("image/vnd.adobe.photoshop", false, "psd")
            "mp4", "3gp" -> LabelMapping("video/mp4", false, label)
            "mkv" -> LabelMapping("video/x-matroska", false, "mkv")
            "webm" -> LabelMapping("video/webm", false, "webm")
            "flv" -> LabelMapping("video/x-flv", false, "flv")
            "mp3" -> LabelMapping("audio/mpeg", false, "mp3")
            "wav" -> LabelMapping("audio/wav", false, "wav")
            "ogg" -> LabelMapping("audio/ogg", false, "ogg")
            "flac" -> LabelMapping("audio/flac", false, "flac")
            "midi" -> LabelMapping("audio/midi", false, "midi")
            "pdf" -> LabelMapping("application/pdf", false, "pdf")
            "zip", "jar", "apk", "nupkg", "xpi", "crx" ->
                LabelMapping("application/zip", false, label)
            "gzip" -> LabelMapping("application/gzip", false, "gz")
            "bzip" -> LabelMapping("application/x-bzip2", false, "bz2")
            "zstd", "zstandard" -> LabelMapping("application/zstd", false, "zst")
            "lz4" -> LabelMapping("application/x-lz4", false, "lz4")
            "sevenzip" -> LabelMapping("application/x-7z-compressed", false, "7z")
            "rar" -> LabelMapping("application/x-rar-compressed", false, "rar")
            "tar" -> LabelMapping("application/x-tar", false, "tar")
            "xz" -> LabelMapping("application/x-xz", false, "xz")
            "iso" -> LabelMapping("application/x-iso9660-image", false, "iso")
            "dmg" -> LabelMapping("application/x-apple-diskimage", false, "dmg")
            "cab" -> LabelMapping("application/vnd.ms-cab-compressed", false, "cab")
            "html" -> LabelMapping("text/html", true, "html")
            "xml" -> LabelMapping("text/xml", true, "xml")
            "json", "jsonl", "ipynb" -> LabelMapping("application/json", true, "json")
            "javascript" -> LabelMapping("application/javascript", true, "js")
            "typescript" -> LabelMapping("application/typescript", true, "ts")
            "css", "scss" -> LabelMapping("text/css", true, label)
            "python" -> LabelMapping("text/x-python", true, "py")
            "java" -> LabelMapping("text/x-java-source", true, "java")
            "kotlin" -> LabelMapping("text/x-kotlin", true, "kt")
            "c" -> LabelMapping("text/x-csrc", true, "c")
            "cpp" -> LabelMapping("text/x-c++src", true, "cpp")
            "go" -> LabelMapping("text/x-go", true, "go")
            "rust" -> LabelMapping("text/x-rust", true, "rs")
            "ruby" -> LabelMapping("text/x-ruby", true, "rb")
            "php" -> LabelMapping("text/x-php", true, "php")
            "perl" -> LabelMapping("text/x-perl", true, "pl")
            "shell" -> LabelMapping("application/x-sh", true, "sh")
            "markdown" -> LabelMapping("text/markdown", true, "md")
            "yaml" -> LabelMapping("application/x-yaml", true, "yml")
            "sql" -> LabelMapping("application/sql", true, "sql")
            "latex" -> LabelMapping("application/x-tex", true, "tex")
            "csv" -> LabelMapping("text/csv", true, "csv")
            "tsv" -> LabelMapping("text/tab-separated-values", true, "tsv")
            "ini", "toml" -> LabelMapping("text/plain", true, label)
            "txt", "rst", "diff" -> LabelMapping("text/plain", true, "txt")
            "rtf" -> LabelMapping("application/rtf", true, "rtf")
            "doc" -> LabelMapping("application/msword", false, "doc")
            "docx" -> LabelMapping("application/vnd.openxmlformats-officedocument.wordprocessingml.document", false, "docx")
            "xls" -> LabelMapping("application/vnd.ms-excel", false, "xls")
            "xlsx" -> LabelMapping("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", false, "xlsx")
            "ppt" -> LabelMapping("application/vnd.ms-powerpoint", false, "ppt")
            "pptx" -> LabelMapping("application/vnd.openxmlformats-officedocument.presentationml.presentation", false, "pptx")
            "odt" -> LabelMapping("application/vnd.oasis.opendocument.text", false, "odt")
            "ods" -> LabelMapping("application/vnd.oasis.opendocument.spreadsheet", false, "ods")
            "odp" -> LabelMapping("application/vnd.oasis.opendocument.presentation", false, "odp")
            "elf" -> LabelMapping("application/x-elf", false, "")
            "dex" -> LabelMapping("application/x-dex", false, "dex")
            "ttf" -> LabelMapping("font/ttf", false, "ttf")
            "otf" -> LabelMapping("font/otf", false, "otf")
            "unknown", "empty" -> LabelMapping("application/octet-stream", false, "")
            else -> {
                val isText = TEXT_LABELS.contains(label)
                LabelMapping(
                    mimeType = if (isText) "text/plain" else "application/octet-stream",
                    isText = isText,
                    extension = if (isText) label else ""
                )
            }
        }
    }

    private val TEXT_LABELS = setOf(
        "python", "java", "kotlin", "javascript", "typescript", "c", "cpp", "go", "rust",
        "ruby", "php", "perl", "shell", "html", "xml", "json", "css", "scss", "markdown",
        "yaml", "sql", "latex", "txt", "ini", "toml", "csv", "tsv", "swift", "scala",
        "lua", "r", "dart", "vue", "asm", "haskell", "lisp", "lua", "makefile", "cmake",
        "dockerfile", "powershell", "batch", "awk", "proto", "gradle", "groovy"
    )

    private fun extensionForMime(mime: String): String = when {
        mime == "text/html" -> "html"
        mime == "application/pdf" -> "pdf"
        mime == "application/json" -> "json"
        mime.startsWith("image/") -> mime.substringAfter('/')
        mime.startsWith("audio/") -> mime.substringAfter('/')
        mime.startsWith("video/") -> mime.substringAfter('/')
        mime.startsWith("text/") -> "txt"
        else -> ""
    }
}
