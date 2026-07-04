package com.raival.compose.file.explorer.screen.main.tab.files.search.ai

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.io.File
import java.nio.LongBuffer

/**
 * ONNX-based sentence embedding engine using all-MiniLM-L6-V2.
 * Generates 384-dimensional L2-normalized embeddings for input texts.
 * Ported from NFile's fileai/lib/src/embedding_engine.dart.
 */
class SemanticSearchEngine private constructor(
    private val session: OrtSession,
    private val tokenizer: WordPieceTokenizer,
    private val env: OrtEnvironment
) {

    companion object {
        const val EMBEDDING_DIM = 384
        const val MAX_SEQ_LEN = 128

        /**
         * Initialize the engine from model and tokenizer files on disk.
         * Called after model download.
         */
        fun fromPath(modelPath: String, tokenizerPath: String): SemanticSearchEngine {
            val env = OrtEnvironment.getEnvironment()
            val session = env.createSession(modelPath)
            val tokenizerJson = File(tokenizerPath).readText()
            val tokenizer = WordPieceTokenizer.fromJsonString(tokenizerJson, MAX_SEQ_LEN)
            return SemanticSearchEngine(session, tokenizer, env)
        }
    }

    /**
     * Generate L2-normalized embeddings for a list of texts.
     */
    fun embed(texts: List<String>, batchSize: Int = 32): List<FloatArray> {
        val allEmbeddings = mutableListOf<FloatArray>()

        for (start in texts.indices step batchSize) {
            val end = minOf(start + batchSize, texts.size)
            val batch = texts.subList(start, end)

            for (text in batch) {
                val embedding = embedSingle(text)
                allEmbeddings.add(embedding)
            }
        }

        return allEmbeddings
    }

    /**
     * Embed a single text string.
     */
    fun embedSingle(text: String): FloatArray {
        val encoding = tokenizer.encode(text)

        // Convert to Long arrays for ONNX (expects int64)
        val inputIdsLong = LongArray(MAX_SEQ_LEN) { encoding.inputIds[it].toLong() }
        val attentionMaskLong = LongArray(MAX_SEQ_LEN) { encoding.attentionMask[it].toLong() }

        val shape = longArrayOf(1, MAX_SEQ_LEN.toLong())

        val inputIdsTensor = OnnxTensor.createTensor(
            env, LongBuffer.wrap(inputIdsLong), shape
        )
        val attentionMaskTensor = OnnxTensor.createTensor(
            env, LongBuffer.wrap(attentionMaskLong), shape
        )

        val inputs = mutableMapOf<String, OnnxTensor>(
            "input_ids" to inputIdsTensor,
            "attention_mask" to attentionMaskTensor
        )

        // Check if model expects token_type_ids
        val inputNames = session.inputNames
        if (inputNames.contains("token_type_ids")) {
            val tokenTypeIdsLong = LongArray(MAX_SEQ_LEN) // all zeros
            val tokenTypeIdsTensor = OnnxTensor.createTensor(
                env, LongBuffer.wrap(tokenTypeIdsLong), shape
            )
            inputs["token_type_ids"] = tokenTypeIdsTensor
        }

        val results = session.run(inputs)

        val embedding: FloatArray

        // Check if model outputs 'sentence_embedding' directly
        val sentenceEmbOutput = results.get("sentence_embedding")
        if (sentenceEmbOutput.isPresent) {
            val raw = sentenceEmbOutput.get().value as Array<FloatArray>
            embedding = raw[0].copyOf()
        } else {
            // Fall back to mean pooling over last_hidden_state
            val firstOutput = results.get(0)
            val rawValue = firstOutput.value

            // Shape: [1, seq_len, 384]
            @Suppress("UNCHECKED_CAST")
            val hiddenStates = rawValue as Array<Array<FloatArray>>
            val tokenEmbeddings = hiddenStates[0].toList()

            embedding = VectorMath.meanPool(
                tokenEmbeddings,
                encoding.attentionMask,
                EMBEDDING_DIM
            )
        }

        VectorMath.l2Normalize(embedding)

        // Close tensors
        inputIdsTensor.close()
        attentionMaskTensor.close()
        inputs["token_type_ids"]?.close()
        results.close()

        return embedding
    }

    /**
     * Search an index for files matching a query.
     */
    fun search(
        query: String,
        index: FileSearchIndex,
        topK: Int = 5
    ): List<AiSearchResult> {
        val queryEmbedding = embedSingle(query)
        return index.search(queryEmbedding, topK)
    }

    /**
     * Release resources.
     */
    fun dispose() {
        try {
            session.close()
        } catch (_: Exception) {}
    }
}
