package com.raival.compose.file.explorer.screen.main.tab.files.search.ai

/**
 * Vector math utilities for semantic search.
 * Ported from NFile's fileai/lib/src/math_utils.dart.
 */
object VectorMath {

    /**
     * Compute the dot product of two float arrays.
     */
    fun dotProduct(a: FloatArray, b: FloatArray): Float {
        require(a.size == b.size) { "Vectors must have the same length" }
        var sum = 0f
        for (i in a.indices) {
            sum += a[i] * b[i]
        }
        return sum
    }

    /**
     * L2-normalize a float array in-place.
     */
    fun l2Normalize(vector: FloatArray) {
        var norm = 0f
        for (v in vector) {
            norm += v * v
        }
        norm = kotlin.math.sqrt(norm)
        if (norm > 0f) {
            for (i in vector.indices) {
                vector[i] /= norm
            }
        }
    }

    /**
     * Mean-pool token embeddings using the attention mask.
     * Only tokens with attention_mask=1 contribute to the mean.
     *
     * @param tokenEmbeddings List of per-token embeddings
     * @param attentionMask The attention mask (1 for real tokens, 0 for padding)
     * @param dim Embedding dimensionality
     * @return L2-normalized mean-pooled embedding
     */
    fun meanPool(
        tokenEmbeddings: List<FloatArray>,
        attentionMask: IntArray,
        dim: Int
    ): FloatArray {
        val result = FloatArray(dim)
        var count = 0f

        for (i in tokenEmbeddings.indices) {
            if (attentionMask[i] == 1) {
                for (j in 0 until dim) {
                    result[j] += tokenEmbeddings[i][j]
                }
                count += 1f
            }
        }

        if (count > 0f) {
            for (j in 0 until dim) {
                result[j] /= count
            }
        }

        l2Normalize(result)
        return result
    }

    /**
     * Return indices that would sort scores in descending order.
     */
    fun argsortDescending(scores: List<Float>): List<Int> {
        return scores.indices.sortedByDescending { scores[it] }
    }
}
