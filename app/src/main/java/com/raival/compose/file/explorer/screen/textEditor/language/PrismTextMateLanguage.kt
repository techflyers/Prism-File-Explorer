package com.raival.compose.file.explorer.screen.textEditor.language

import android.os.Bundle
import com.raival.compose.file.explorer.App.Companion.globalClass
import com.raival.compose.file.explorer.screen.textEditor.keyword.KeywordManager
import com.raival.compose.file.explorer.screen.textEditor.language.java.JavaFormatter
import com.raival.compose.file.explorer.screen.textEditor.language.json.JsonFormatter
import com.raival.compose.file.explorer.screen.textEditor.language.xml.XmlFormatter
import com.raival.compose.file.explorer.screen.textEditor.snippet.SnippetManager
import io.github.rosemoe.sora.lang.completion.CompletionHelper
import io.github.rosemoe.sora.lang.completion.CompletionPublisher
import io.github.rosemoe.sora.lang.format.Formatter
import io.github.rosemoe.sora.langs.textmate.TextMateLanguage
import io.github.rosemoe.sora.langs.textmate.registry.GrammarRegistry
import io.github.rosemoe.sora.langs.textmate.registry.ThemeRegistry
import io.github.rosemoe.sora.text.CharPosition
import io.github.rosemoe.sora.text.ContentReference
import org.eclipse.tm4e.core.grammar.IGrammar
import org.eclipse.tm4e.languageconfiguration.internal.model.LanguageConfiguration

class PrismTextMateLanguage(
    grammar: IGrammar,
    languageConfiguration: LanguageConfiguration?,
    grammarRegistry: GrammarRegistry = GrammarRegistry.getInstance(),
    themeRegistry: ThemeRegistry = ThemeRegistry.getInstance(),
    collectIdentifiers: Boolean = true,
    val scopeName: String = grammar.scopeName,
) : TextMateLanguage(grammar, languageConfiguration, grammarRegistry, themeRegistry, collectIdentifiers) {

    private val jsonFormatter by lazy { JsonFormatter() }
    private val xmlFormatter by lazy { XmlFormatter() }
    private val javaFormatter by lazy { JavaFormatter() }

    init {
        val keywords = KeywordManager.getKeywordsSync(scopeName)
        if (!keywords.isNullOrEmpty()) {
            setCompleterKeywords(keywords.toTypedArray())
        }
    }

    override fun requireAutoComplete(
        content: ContentReference,
        position: CharPosition,
        publisher: CompletionPublisher,
        extraArguments: Bundle
    ) {
        if (!globalClass.preferencesManager.codeCompletion) return

        super.requireAutoComplete(content, position, publisher, extraArguments)

        if (globalClass.preferencesManager.snippetSuggestions) {
            val prefix = CompletionHelper.computePrefix(content, position) { c ->
                Character.isJavaIdentifierPart(c) || c == '-' || c == '$' || c == '@'
            }
            if (!prefix.isNullOrEmpty()) {
                val snippets = SnippetManager.getSnippetsForScope(scopeName, prefix)
                for (item in snippets) {
                    publisher.addItem(item)
                }
            }
        }
    }

    override fun getFormatter(): Formatter {
        return when (scopeName) {
            "source.json" -> jsonFormatter
            "text.xml" -> xmlFormatter
            "source.java", "source.kotlin" -> javaFormatter
            else -> super.getFormatter()
        }
    }

    companion object {
        fun create(
            scopeName: String,
            grammarRegistry: GrammarRegistry = GrammarRegistry.getInstance(),
            themeRegistry: ThemeRegistry = ThemeRegistry.getInstance(),
            collectIdentifiers: Boolean = true,
        ): PrismTextMateLanguage? {
            val grammar = grammarRegistry.findGrammar(scopeName) ?: return null
            val languageConfiguration = grammarRegistry.findLanguageConfiguration(grammar.scopeName)
            return PrismTextMateLanguage(
                grammar = grammar,
                languageConfiguration = languageConfiguration,
                grammarRegistry = grammarRegistry,
                themeRegistry = themeRegistry,
                collectIdentifiers = collectIdentifiers,
                scopeName = scopeName
            )
        }
    }
}
