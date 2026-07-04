package com.raival.compose.file.explorer.screen.main.tab.files.search.ai

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File

/**
 * Pure Kotlin BERT WordPiece tokenizer.
 * Ported from NFile's fileai/lib/src/tokenizer.dart.
 *
 * Parses the HuggingFace tokenizer.json format and implements:
 * normalize → pre-tokenize → WordPiece subword lookup → special tokens → truncation → padding
 */
class WordPieceTokenizer(
    private val vocab: Map<String, Int>,
    private val maxSeqLen: Int = 128,
    private val clsId: Int,
    private val sepId: Int,
    private val unkId: Int,
    private val padId: Int,
    private val continuingPrefix: String = "##",
    private val maxInputCharsPerWord: Int = 200,
    private val doLowerCase: Boolean = true
) {

    data class Encoding(
        val inputIds: IntArray,
        val attentionMask: IntArray
    )

    companion object {
        /**
         * Create a tokenizer from a HuggingFace tokenizer.json file.
         */
        fun fromJsonFile(jsonFile: File, maxSeqLen: Int = 128): WordPieceTokenizer {
            return fromJsonString(jsonFile.readText(), maxSeqLen)
        }

        /**
         * Create a tokenizer from a JSON string.
         */
        fun fromJsonString(jsonString: String, maxSeqLen: Int = 128): WordPieceTokenizer {
            val gson = Gson()
            val type = object : TypeToken<Map<String, Any>>() {}.type
            val json: Map<String, Any> = gson.fromJson(jsonString, type)

            // Parse vocab from model.vocab
            val model = json["model"] as? Map<*, *>
            val vocabRaw = model?.get("vocab") as? Map<*, *> ?: emptyMap<String, Double>()
            val vocab = mutableMapOf<String, Int>()
            for ((key, value) in vocabRaw) {
                val k = key as? String ?: continue
                val v = (value as? Double)?.toInt() ?: continue
                vocab[k] = v
            }

            // Parse special tokens
            val addedTokens = json["added_tokens"] as? List<*> ?: emptyList<Any>()
            var clsId = vocab["[CLS]"] ?: 101
            var sepId = vocab["[SEP]"] ?: 102
            var unkId = vocab["[UNK]"] ?: 100
            var padId = vocab["[PAD]"] ?: 0

            for (token in addedTokens) {
                val tokenMap = token as? Map<*, *> ?: continue
                val content = tokenMap["content"] as? String ?: continue
                val id = (tokenMap["id"] as? Double)?.toInt() ?: continue
                when (content) {
                    "[CLS]" -> clsId = id
                    "[SEP]" -> sepId = id
                    "[UNK]" -> unkId = id
                    "[PAD]" -> padId = id
                }
            }

            // Parse continuing prefix
            val continuingPrefix = (model?.get("continuing_subword_prefix") as? String) ?: "##"
            val maxInputCharsPerWord = (model?.get("max_input_chars_per_word") as? Double)?.toInt() ?: 200

            // Parse normalization settings
            val normalizer = json["normalizer"] as? Map<*, *>
            val doLowerCase = (normalizer?.get("lowercase") as? Boolean) ?: true

            return WordPieceTokenizer(
                vocab = vocab,
                maxSeqLen = maxSeqLen,
                clsId = clsId,
                sepId = sepId,
                unkId = unkId,
                padId = padId,
                continuingPrefix = continuingPrefix,
                maxInputCharsPerWord = maxInputCharsPerWord,
                doLowerCase = doLowerCase
            )
        }
    }

    /**
     * Tokenize a single text.
     */
    fun encode(text: String): Encoding {
        val normalized = normalize(text)
        val words = preTokenize(normalized)
        val tokenIds = mutableListOf(clsId) // [CLS]

        for (word in words) {
            val subTokens = wordPieceTokenize(word)
            tokenIds.addAll(subTokens)

            // Check if we're already at max length (minus 1 for [SEP])
            if (tokenIds.size >= maxSeqLen - 1) {
                break
            }
        }

        // Truncate to maxSeqLen - 1 to leave room for [SEP]
        while (tokenIds.size > maxSeqLen - 1) {
            tokenIds.removeAt(tokenIds.size - 1)
        }

        tokenIds.add(sepId) // [SEP]

        // Create attention mask and pad
        val attentionMask = IntArray(maxSeqLen)
        val inputIds = IntArray(maxSeqLen) { padId }

        for (i in tokenIds.indices) {
            inputIds[i] = tokenIds[i]
            attentionMask[i] = 1
        }

        return Encoding(inputIds = inputIds, attentionMask = attentionMask)
    }

    /**
     * Tokenize a batch of texts.
     */
    fun encodeBatch(texts: List<String>): List<Encoding> {
        return texts.map { encode(it) }
    }

    private fun normalize(text: String): String {
        var result = text.trim()
        if (doLowerCase) {
            result = result.lowercase()
        }
        // Remove accents (simple NFD decomposition)
        result = java.text.Normalizer.normalize(result, java.text.Normalizer.Form.NFD)
            .replace(Regex("[\\p{InCombiningDiacriticalMarks}]"), "")
        return result
    }

    private fun preTokenize(text: String): List<String> {
        // Simple whitespace + punctuation pre-tokenization
        return text.split(Regex("\\s+")).filter { it.isNotEmpty() }
    }

    private fun wordPieceTokenize(word: String): List<Int> {
        if (word.length > maxInputCharsPerWord) {
            return listOf(unkId)
        }

        val tokens = mutableListOf<Int>()
        var start = 0

        while (start < word.length) {
            var end = word.length
            var found = false

            while (start < end) {
                val substr = if (start > 0) {
                    continuingPrefix + word.substring(start, end)
                } else {
                    word.substring(start, end)
                }

                val id = vocab[substr]
                if (id != null) {
                    tokens.add(id)
                    found = true
                    start = end
                    break
                }
                end--
            }

            if (!found) {
                tokens.add(unkId)
                start++
            }
        }

        return tokens
    }
}
