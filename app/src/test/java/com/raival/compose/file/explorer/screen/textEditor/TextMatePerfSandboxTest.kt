package com.raival.compose.file.explorer.screen.textEditor

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.raival.compose.file.explorer.screen.textEditor.filetype.FileTypeManager
import com.raival.compose.file.explorer.screen.textEditor.language.ZipFileResolver
import io.github.rosemoe.sora.langs.textmate.registry.FileProviderRegistry
import io.github.rosemoe.sora.langs.textmate.registry.GrammarRegistry
import io.github.rosemoe.sora.langs.textmate.registry.ThemeRegistry
import io.github.rosemoe.sora.langs.textmate.registry.model.ThemeModel
import org.eclipse.tm4e.core.registry.IThemeSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import java.io.File
import java.io.InputStreamReader
import java.util.zip.ZipFile
import kotlin.system.measureNanoTime
import kotlin.system.measureTimeMillis

class TextMatePerfSandboxTest {

    companion object {
        private lateinit var bundleFile: File
        private lateinit var zipResolver: ZipFileResolver

        @BeforeClass
        @JvmStatic
        fun setUp() {
            // Locate the bundle file in the assets directory
            val candidatePaths = listOf(
                File("src/main/assets/textmate.bundle"),
                File("app/src/main/assets/textmate.bundle"),
                File("../app/src/main/assets/textmate.bundle")
            )
            bundleFile = candidatePaths.firstOrNull { it.exists() }
                ?: throw IllegalStateException("textmate.bundle not found in candidate paths: $candidatePaths")

            zipResolver = ZipFileResolver(ZipFile(bundleFile))
            FileProviderRegistry.getInstance().addFileProvider(zipResolver)
        }
    }

    @Test
    fun benchmarkBundleMountAndLanguagesIndex() {
        println("=== [PERF SANDBOX] 1. Zip Bundle Index Benchmark ===")
        println("Bundle file path: ${bundleFile.absolutePath}")
        println("Bundle size: ${bundleFile.length() / 1024} KB")

        val grammarRegistry = GrammarRegistry.getInstance()
        val loadTimeMs = measureTimeMillis {
            val grammars = grammarRegistry.loadGrammars("textmate/languages.json")
            assertTrue("Expected grammars to be loaded", grammars.isNotEmpty())
            println("Successfully indexed ${grammars.size} grammars from textmate/languages.json")
        }

        println("Languages.json load & parse time: ${loadTimeMs}ms")
        assertTrue("Index loading should be under 500ms", loadTimeMs < 500)
    }

    @Test
    fun benchmarkLazyGrammarResolutionSpeed() {
        println("\n=== [PERF SANDBOX] 2. Lazy Grammar Resolution Speed ===")
        val grammarRegistry = GrammarRegistry.getInstance()
        grammarRegistry.loadGrammars("textmate/languages.json")

        val testScopes = listOf(
            "source.kotlin",
            "source.java",
            "source.python",
            "source.js",
            "source.ts",
            "text.html.basic",
            "text.html.markdown",
            "source.rust",
            "source.go",
            "source.cpp",
            "source.sql",
            "source.yaml",
            "source.json"
        )

        var totalNanos = 0L
        for (scope in testScopes) {
            val elapsedNanos = measureNanoTime {
                val grammar = grammarRegistry.findGrammar(scope)
                assertNotNull("Grammar for $scope must not be null", grammar)
            }
            totalNanos += elapsedNanos
            val ms = elapsedNanos / 1_000_000.0
            println("  -> Scope [$scope] resolved in ${String.format("%.3f", ms)} ms")
        }

        val avgMs = (totalNanos / testScopes.size) / 1_000_000.0
        println("Average lazy resolution time across ${testScopes.size} major languages: ${String.format("%.3f", avgMs)} ms")
        assertTrue("Average lazy grammar load time should be well under 50ms", avgMs < 50.0)
    }

    @Test
    fun validateAll49LanguagesResolvable() {
        println("\n=== [PERF SANDBOX] 3. Validate All 49 Languages ===")
        val grammarRegistry = GrammarRegistry.getInstance()
        grammarRegistry.loadGrammars("textmate/languages.json")

        var resolvedCount = 0
        val missingScopes = mutableListOf<String>()

        for (fileType in FileTypeManager.allTypes()) {
            val scope = fileType.textmateScope ?: continue
            val grammar = grammarRegistry.findGrammar(scope)
            if (grammar != null) {
                resolvedCount++
            } else {
                missingScopes.add("${fileType.title} ($scope)")
            }
        }

        println("Successfully resolved $resolvedCount languages from FileTypeManager")
        if (missingScopes.isNotEmpty()) {
            println("Missing scopes: $missingScopes")
        }
        assertEquals("Missing scopes detected: $missingScopes", 0, missingScopes.size)
    }

    @Test
    fun benchmarkKeywordsRegistryLoad() {
        println("\n=== [PERF SANDBOX] 4. Keywords Registry Benchmark ===")
        val stream = FileProviderRegistry.getInstance().tryGetInputStream("textmate/keywords.json")
        assertNotNull("keywords.json must be resolvable from bundle", stream)

        lateinit var keywordsMap: Map<String, List<String>>
        val parseTimeMs = measureTimeMillis {
            stream!!.use { s ->
                val typeToken = object : TypeToken<Map<String, List<String>>>() {}.type
                keywordsMap = Gson().fromJson(InputStreamReader(s), typeToken)
            }
        }

        println("Parsed keywords.json in ${parseTimeMs}ms (${keywordsMap.size} language scopes)")
        assertTrue("keywords.json should contain scopes", keywordsMap.isNotEmpty())
        assertTrue("source.kotlin keywords should exist", keywordsMap.containsKey("source.kotlin"))
        val kotlinKeywords = keywordsMap["source.kotlin"] ?: emptyList()
        println("Kotlin sample keywords (${kotlinKeywords.size} total): ${kotlinKeywords.take(10)}")
        assertTrue("Kotlin keywords should contain 'val'", kotlinKeywords.contains("val"))
    }

    @Test
    fun benchmarkThemeLoadingFromBundle() {
        println("\n=== [PERF SANDBOX] 5. Themes Loading Benchmark ===")
        val themeRegistry = ThemeRegistry.getInstance()
        val themes = listOf(
            "textmate/darcula.json" to "darcula",
            "textmate/quietlight.json" to "quietlight",
            "textmate/dark.json" to "dark",
            "textmate/light.tmTheme" to "light",
            "textmate/black/darcula.json" to "black_darcula"
        )

        for ((path, name) in themes) {
            val elapsedMs = measureTimeMillis {
                val stream = FileProviderRegistry.getInstance().tryGetInputStream(path)
                assertNotNull("Theme $path must be found in bundle", stream)
                stream!!.use { s ->
                    themeRegistry.loadTheme(
                        ThemeModel(
                            IThemeSource.fromInputStream(s, path, null),
                            name
                        )
                    )
                }
            }
            println("  -> Theme [$name] loaded from bundle in ${elapsedMs}ms")
        }
    }
}
