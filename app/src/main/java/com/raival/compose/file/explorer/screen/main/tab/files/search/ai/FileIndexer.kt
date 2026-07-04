package com.raival.compose.file.explorer.screen.main.tab.files.search.ai

import android.util.Base64
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import java.io.File
import java.io.FileInputStream
import java.nio.charset.Charset
import java.util.zip.ZipInputStream

/**
 * Progress callback for indexing operations.
 */
typealias IndexProgressCallback = (phase: String, current: Int, total: Int) -> Unit

/**
 * Represents a cached semantic file index with embeddings.
 * Ported from NFile's fileai/lib/src/file_index.dart.
 */
class FileSearchIndex(
    val filePaths: List<String>,
    val embeddings: List<FloatArray>,
    val fileHashes: Map<String, String>
) {
    /**
     * Search for files most similar to the query embedding.
     */
    fun search(queryEmbedding: FloatArray, topK: Int = 5): List<AiSearchResult> {
        if (filePaths.isEmpty()) return emptyList()

        val scores = embeddings.map { VectorMath.dotProduct(it, queryEmbedding) }
        val sortedIndices = VectorMath.argsortDescending(scores)
        val k = minOf(topK, sortedIndices.size)

        return (0 until k).map { i ->
            val idx = sortedIndices[i]
            AiSearchResult(
                filePath = filePaths[idx],
                displayName = filePaths[idx].substringAfterLast('/'),
                score = scores[idx].toDouble()
            )
        }
    }

    /**
     * Save the index to a JSON file.
     */
    suspend fun save(path: String) = withContext(Dispatchers.IO) {
        val data = mapOf(
            "version" to 1,
            "file_paths" to filePaths,
            "file_hashes" to fileHashes,
            "embeddings_b64" to embeddings.map { emb ->
                val bytes = ByteArray(emb.size * 4)
                val buffer = java.nio.ByteBuffer.wrap(bytes).order(java.nio.ByteOrder.LITTLE_ENDIAN)
                for (v in emb) buffer.putFloat(v)
                Base64.encodeToString(bytes, Base64.NO_WRAP)
            }
        )
        File(path).writeText(Gson().toJson(data))
    }

    companion object {
        private const val INDEX_FILENAME = ".fileai_index.json"

        /**
         * Load a cached index from disk.
         */
        suspend fun load(path: String): FileSearchIndex? = withContext(Dispatchers.IO) {
            try {
                val file = File(path)
                if (!file.exists()) return@withContext null

                val gson = Gson()
                val type = object : TypeToken<Map<String, Any>>() {}.type
                val data: Map<String, Any> = gson.fromJson(file.readText(), type)

                @Suppress("UNCHECKED_CAST")
                val filePaths = (data["file_paths"] as? List<String>) ?: return@withContext null
                @Suppress("UNCHECKED_CAST")
                val fileHashesRaw = (data["file_hashes"] as? Map<String, String>) ?: emptyMap()
                @Suppress("UNCHECKED_CAST")
                val embeddingsB64 = (data["embeddings_b64"] as? List<String>) ?: return@withContext null

                val embeddings = embeddingsB64.map { b64 ->
                    val bytes = Base64.decode(b64, Base64.NO_WRAP)
                    val buffer = java.nio.ByteBuffer.wrap(bytes).order(java.nio.ByteOrder.LITTLE_ENDIAN)
                    FloatArray(bytes.size / 4) { buffer.getFloat() }
                }

                FileSearchIndex(
                    filePaths = filePaths,
                    embeddings = embeddings,
                    fileHashes = fileHashesRaw
                )
            } catch (_: Exception) {
                null
            }
        }

        fun getIndexPath(rootPath: String): String = "$rootPath/$INDEX_FILENAME"
    }
}

/**
 * File indexer that discovers, reads, and indexes files for semantic search.
 * Ported from NFile's fileai/lib/src/file_discovery.dart and file_index.dart.
 */
object FileIndexer {

    /** Default file extensions to index. */
    val DEFAULT_EXTENSIONS = setOf(
        ".py", ".js", ".ts", ".tsx", ".jsx", ".java", ".kt", ".kts",
        ".c", ".cpp", ".h", ".hpp", ".cs", ".go", ".rs", ".rb",
        ".swift", ".m", ".mm",
        ".html", ".css", ".scss", ".less",
        ".json", ".yaml", ".yml", ".toml", ".xml",
        ".md", ".txt", ".rst", ".adoc",
        ".sh", ".bash", ".zsh", ".fish", ".bat", ".ps1",
        ".sql", ".graphql",
        ".r", ".jl", ".lua", ".php", ".pl", ".pm",
        ".gradle", ".cmake",
        ".docx", ".xlsx", ".pptx", ".pdf", ".tex"
    )

    /** Directories to skip. */
    private val SKIP_DIRS = setOf(
        ".git", ".hg", ".svn",
        "node_modules", "__pycache__", ".tox", ".nox",
        "venv", ".venv", "env", ".env",
        ".idea", ".vscode", ".vs",
        "build", "dist", "out", "target",
        ".gradle", ".cache", ".dart_tool"
    )

    /** Max file size for indexing (10 MB). */
    private const val MAX_FILE_SIZE = 10 * 1024 * 1024L

    /** Max characters to read from a file. */
    private const val MAX_CHARS = 8000

    /**
     * Build or incrementally update a semantic file index.
     */
    suspend fun buildIndex(
        rootPath: String,
        engine: SemanticSearchEngine,
        extensions: Set<String>? = null,
        forceReindex: Boolean = false,
        batchSize: Int = 32,
        onProgress: IndexProgressCallback? = null
    ): FileSearchIndex = withContext(Dispatchers.IO) {
        val tag = "FileIndexer"
        android.util.Log.d(tag, "buildIndex: started for $rootPath")
        val indexPath = FileSearchIndex.getIndexPath(rootPath)
        val exts = extensions ?: DEFAULT_EXTENSIONS
        val files = discoverFiles(rootPath, exts)

        if (files.isEmpty()) {
            android.util.Log.d(tag, "buildIndex: no files discovered. Returning empty index.")
            return@withContext FileSearchIndex(emptyList(), emptyList(), emptyMap())
        }

        // Compute current hashes
        val currentHashes = mutableMapOf<String, String>()
        for (f in files) {
            fileHash(f)?.let { currentHashes[f] = it }
        }

        // Try loading cached index
        val cachedIndex = if (!forceReindex) FileSearchIndex.load(indexPath) else null

        // Determine files needing embedding
        val filesToEmbed: List<String>
        val reusedPaths: List<String>
        val reusedIndices: List<Int>

        if (cachedIndex != null) {
            val cachedLookup = mutableMapOf<String, Int>()
            for (i in cachedIndex.filePaths.indices) {
                cachedLookup[cachedIndex.filePaths[i]] = i
            }

            val toEmbed = mutableListOf<String>()
            val rPaths = mutableListOf<String>()
            val rIndices = mutableListOf<Int>()

            for ((path, hash) in currentHashes) {
                val idx = cachedLookup[path]
                if (idx != null && cachedIndex.fileHashes[path] == hash) {
                    rPaths.add(path)
                    rIndices.add(idx)
                } else {
                    toEmbed.add(path)
                }
            }

            if (toEmbed.isEmpty()) {
                android.util.Log.d(tag, "buildIndex: all files are cached. Returning cached index.")
                return@withContext cachedIndex
            }

            filesToEmbed = toEmbed
            reusedPaths = rPaths
            reusedIndices = rIndices
        } else {
            filesToEmbed = currentHashes.keys.toList()
            reusedPaths = emptyList()
            reusedIndices = emptyList()
        }

        android.util.Log.d(tag, "buildIndex: files to embed: ${filesToEmbed.size}, reused: ${reusedPaths.size}")

        // Read files
        val texts = mutableListOf<String>()
        val validPaths = mutableListOf<String>()

        for (i in filesToEmbed.indices) {
            onProgress?.invoke("Reading files", i + 1, filesToEmbed.size)
            val content = readFileContent(filesToEmbed[i])
            if (content.isNotBlank()) {
                texts.add(content)
                validPaths.add(filesToEmbed[i])
            }
            if (i % 10 == 0) yield()
        }

        if (texts.isEmpty() && reusedPaths.isEmpty()) {
            android.util.Log.d(tag, "buildIndex: no readable content found. Returning empty index.")
            return@withContext FileSearchIndex(emptyList(), emptyList(), currentHashes)
        }

        // Generate embeddings
        val newEmbeddings = if (texts.isNotEmpty()) {
            val allEmbs = mutableListOf<FloatArray>()
            try {
                android.util.Log.d(tag, "buildIndex: embedding ${texts.size} files in batches of $batchSize")
                for (start in texts.indices step batchSize) {
                    val end = minOf(start + batchSize, texts.size)
                    val batch = texts.subList(start, end)
                    onProgress?.invoke("Generating embeddings", start + batch.size, texts.size)
                    val embs = engine.embed(batch, batchSize)
                    allEmbs.addAll(embs)
                    yield()
                }
            } catch (e: Exception) {
                android.util.Log.e(tag, "buildIndex: embedding generation failed", e)
                throw e
            }
            allEmbs
        } else null

        // Merge with cached
        val allPaths = mutableListOf<String>()
        val allEmbs = mutableListOf<FloatArray>()

        if (reusedIndices.isNotEmpty() && cachedIndex != null) {
            allPaths.addAll(reusedPaths)
            for (idx in reusedIndices) {
                allEmbs.add(cachedIndex.embeddings[idx])
            }
        }

        if (newEmbeddings != null) {
            allPaths.addAll(validPaths)
            allEmbs.addAll(newEmbeddings)
        }

        val index = FileSearchIndex(
            filePaths = allPaths,
            embeddings = allEmbs,
            fileHashes = currentHashes
        )
        try {
            index.save(indexPath)
            android.util.Log.d(tag, "buildIndex: successfully completed. Total files in index: ${allPaths.size}")
        } catch (e: Exception) {
            android.util.Log.w(tag, "buildIndex: failed to save index file", e)
        }
        index
    }

    /**
     * Discover all indexable files under rootPath.
     */
    private fun discoverFiles(rootPath: String, extensions: Set<String>): List<String> {
        val tag = "FileIndexer"
        android.util.Log.d(tag, "discoverFiles: rootPath = $rootPath")
        val root = File(rootPath)
        if (!root.exists() || !root.isDirectory) {
            android.util.Log.w(tag, "discoverFiles: path does not exist or is not a directory!")
            return emptyList()
        }

        val files = mutableListOf<String>()
        var totalWalked = 0

        root.walkTopDown()
            .onEnter { dir -> dir.name !in SKIP_DIRS }
            .forEach { file ->
                totalWalked++
                if (file.isFile) {
                    val ext = ".${file.extension.lowercase()}"
                    if (ext in extensions && file.length() <= MAX_FILE_SIZE) {
                        files.add(file.absolutePath)
                    }
                }
            }

        android.util.Log.d(tag, "discoverFiles: finished walking. Total files walked: $totalWalked, matches: ${files.size}")
        files.sort()
        return files
    }

    /**
     * Read file content with a header, truncated to MAX_CHARS.
     */
    private fun readFileContent(path: String): String {
        return try {
            val file = File(path)
            val ext = file.extension.lowercase()

            val content = when (ext) {
                "docx" -> extractDocxText(file)
                "xlsx" -> extractXlsxText(file)
                "pptx" -> extractPptxText(file)
                else -> file.readText(Charsets.UTF_8)
            }

            val header = "File: ${file.name}\n"
            val normalized = content.replace(Regex("\\s+"), " ").trim()
            val truncated = if (normalized.length > MAX_CHARS) normalized.substring(0, MAX_CHARS) else normalized
            header + truncated
        } catch (_: Exception) {
            ""
        }
    }

    private fun fileHash(path: String): String? {
        return try {
            val stat = File(path)
            if (!stat.exists()) null
            else "${stat.lastModified()}:${stat.length()}"
        } catch (_: Exception) {
            null
        }
    }

    // --- Simple Office XML extractors ---

    private fun extractDocxText(file: File): String {
        return try {
            ZipInputStream(FileInputStream(file)).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    if (entry.name == "word/document.xml") {
                        val xml = zis.bufferedReader().readText()
                        val regex = Regex("""<w:t[^>]*>(.*?)</w:t>""")
                        return@use regex.findAll(xml).map { it.groupValues[1] }.joinToString(" ")
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
                ""
            }
        } catch (_: Exception) { "" }
    }

    private fun extractXlsxText(file: File): String {
        return try {
            val sb = StringBuilder()
            ZipInputStream(FileInputStream(file)).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    if (entry.name.startsWith("xl/worksheets/sheet") && entry.name.endsWith(".xml")) {
                        val xml = zis.bufferedReader().readText()
                        val regex = Regex("""<t[^>]*>(.*?)</t>""")
                        regex.findAll(xml).forEach { sb.append(it.groupValues[1]).append(" ") }
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
            sb.toString()
        } catch (_: Exception) { "" }
    }

    private fun extractPptxText(file: File): String {
        return try {
            val sb = StringBuilder()
            ZipInputStream(FileInputStream(file)).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    if (entry.name.startsWith("ppt/slides/slide") && entry.name.endsWith(".xml")) {
                        val xml = zis.bufferedReader().readText()
                        val regex = Regex("""<a:t[^>]*>(.*?)</a:t>""")
                        regex.findAll(xml).forEach { sb.append(it.groupValues[1]).append(" ") }
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
            sb.toString()
        } catch (_: Exception) { "" }
    }
}
