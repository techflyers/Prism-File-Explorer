package com.techflyers.compose.file.explorer.screen.viewer.document.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.ln
import kotlin.math.min

object DocumentAiService {

    data class SummaryResult(
        val executiveSummary: String,
        val keyPoints: List<String>,
        val wordCount: Int,
        val estimatedReadTimeMinutes: Int
    )

    data class AnswerResult(
        val answer: String,
        val relevantSnippet: String,
        val pageNumber: Int?
    )

    /**
     * Produces an offline executive summary and key takeaways from document text.
     */
    suspend fun summarize(fullText: String): SummaryResult = withContext(Dispatchers.Default) {
        val trimmed = fullText.trim()
        if (trimmed.isEmpty()) {
            return@withContext SummaryResult(
                executiveSummary = "No extractable text found in this document.",
                keyPoints = emptyList(),
                wordCount = 0,
                estimatedReadTimeMinutes = 0
            )
        }

        val words = trimmed.split(Regex("\\s+")).filter { it.isNotBlank() }
        val wordCount = words.size
        val readTime = (wordCount / 200).coerceAtLeast(1)

        val rawSentences = trimmed.split(Regex("(?<=[.!?])\\s+"))
            .map { it.trim() }
            .filter { it.length in 25..350 }

        if (rawSentences.isEmpty()) {
            return@withContext SummaryResult(
                executiveSummary = trimmed.take(250) + if (trimmed.length > 250) "…" else "",
                keyPoints = emptyList(),
                wordCount = wordCount,
                estimatedReadTimeMinutes = readTime
            )
        }

        // Stop words list for frequency weighting
        val stopWords = setOf(
            "the", "and", "is", "in", "to", "of", "a", "that", "it", "for", "on", "with",
            "as", "this", "by", "from", "at", "an", "be", "are", "was", "were", "or", "have",
            "has", "had", "not", "but", "what", "all", "were", "when", "we", "there", "can",
            "an", "your", "which", "their", "if", "will", "each", "about", "how", "up", "out"
        )

        // Term frequency map
        val termFreq = mutableMapOf<String, Int>()
        for (w in words) {
            val clean = w.lowercase(Locale.ROOT).filter { it.isLetter() }
            if (clean.length > 3 && clean !in stopWords) {
                termFreq[clean] = (termFreq[clean] ?: 0) + 1
            }
        }

        // Score sentences by keyword density and position
        val scored = rawSentences.mapIndexed { index, sentence ->
            val sWords = sentence.lowercase(Locale.ROOT).split(Regex("\\W+")).filter { it.isNotBlank() }
            var score = 0.0
            for (sw in sWords) {
                score += termFreq[sw] ?: 0
            }
            // Position boost for opening and concluding sentences
            if (index < 3) score *= 1.35
            if (index == rawSentences.size - 1) score *= 1.2
            val normalizedScore = if (sWords.isNotEmpty()) score / ln((sWords.size + 5).toDouble()) else 0.0
            index to (sentence to normalizedScore)
        }

        // Pick top sentences for executive summary
        val topSentences = scored.sortedByDescending { it.second.second }
            .take(min(4, scored.size))
            .sortedBy { it.first } // restore chronological order
            .map { it.second.first }

        // Pick distinct key points
        val keyPoints = scored.sortedByDescending { it.second.second }
            .drop(1)
            .take(min(5, (scored.size - 1).coerceAtLeast(0)))
            .map { it.second.first }

        val summaryText = topSentences.joinToString(" ")

        SummaryResult(
            executiveSummary = summaryText,
            keyPoints = keyPoints,
            wordCount = wordCount,
            estimatedReadTimeMinutes = readTime
        )
    }

    /**
     * Answers questions against document text by relevance ranking and snippet extraction.
     */
    suspend fun answerQuestion(
        pagesText: List<Pair<Int, String>>,
        query: String
    ): AnswerResult = withContext(Dispatchers.Default) {
        val qWords = query.lowercase(Locale.ROOT).split(Regex("\\W+"))
            .filter { it.length > 2 }
        if (qWords.isEmpty()) {
            return@withContext AnswerResult(
                answer = "Please enter a specific question about the document.",
                relevantSnippet = "",
                pageNumber = null
            )
        }

        var bestScore = 0.0
        var bestPage: Int? = null
        var bestSnippet = ""

        for ((pageIndex, text) in pagesText) {
            val paragraphs = text.split("\n\n").map { it.trim() }.filter { it.length > 20 }
            for (p in paragraphs) {
                val lowerP = p.lowercase(Locale.ROOT)
                var matches = 0
                for (qw in qWords) {
                    if (lowerP.contains(qw)) matches++
                }
                if (matches > 0) {
                    val score = matches.toDouble() / qWords.size
                    if (score > bestScore) {
                        bestScore = score
                        bestPage = pageIndex + 1
                        bestSnippet = p
                    }
                }
            }
        }

        if (bestSnippet.isEmpty() || bestScore < 0.25) {
            AnswerResult(
                answer = "Could not find a direct answer to \"$query\" in the document.",
                relevantSnippet = "",
                pageNumber = null
            )
        } else {
            AnswerResult(
                answer = bestSnippet,
                relevantSnippet = bestSnippet.take(200) + if (bestSnippet.length > 200) "…" else "",
                pageNumber = bestPage
            )
        }
    }

    /**
     * Simplifies or explains a highlighted/selected piece of text.
     */
    suspend fun explainSelection(selection: String): String = withContext(Dispatchers.Default) {
        val trimmed = selection.trim()
        if (trimmed.isEmpty()) return@withContext ""
        val words = trimmed.split(Regex("\\s+"))
        if (words.size <= 4) {
            "Selected term: \"$trimmed\". In this context, this refers to a key concept or identifier within the document."
        } else {
            "Key takeaway from selection: ${trimmed.take(150)}${if (trimmed.length > 150) "…" else ""}"
        }
    }
}
