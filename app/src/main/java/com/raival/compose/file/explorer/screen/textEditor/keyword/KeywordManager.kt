package com.raival.compose.file.explorer.screen.textEditor.keyword

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.raival.compose.file.explorer.App.Companion.logger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStreamReader

object KeywordManager {
    private const val KEYWORDS_ASSET_PATH = "textmate/keywords.json"
    private val keywordRegistryInitialized = CompletableDeferred<Unit>()
    private var keywords: Map<String, List<String>> = emptyMap()

    suspend fun initKeywordRegistry(context: Context) {
        if (keywordRegistryInitialized.isCompleted) return

        withContext(Dispatchers.IO) {
            try {
                val stream = io.github.rosemoe.sora.langs.textmate.registry.FileProviderRegistry.getInstance().tryGetInputStream(KEYWORDS_ASSET_PATH)
                    ?: context.assets.open(KEYWORDS_ASSET_PATH)

                stream.use { s ->
                    val typeToken = object : TypeToken<Map<String, List<String>>>() {}.type
                    keywords = Gson().fromJson(InputStreamReader(s), typeToken) ?: emptyMap()
                }
                keywordRegistryInitialized.complete(Unit)
            } catch (e: Exception) {
                logger.logError(e)
                keywords = emptyMap()
                keywordRegistryInitialized.complete(Unit)
            }
        }
    }

    suspend fun getKeywords(textmateScope: String): List<String>? {
        keywordRegistryInitialized.await()
        return keywords[textmateScope]
    }

    fun getKeywordsSync(textmateScope: String): List<String>? {
        return if (keywordRegistryInitialized.isCompleted) {
            keywords[textmateScope]
        } else {
            null
        }
    }
}
