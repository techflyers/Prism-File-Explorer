package com.raival.compose.file.explorer.screen.textEditor.scheme

import android.content.Context
import android.os.Build
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.raival.compose.file.explorer.common.isDarkTheme
import io.github.rosemoe.sora.langs.textmate.TextMateColorScheme
import io.github.rosemoe.sora.langs.textmate.registry.ThemeRegistry
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme

object PrismColorScheme {

    fun resolveMaterialColorScheme(context: Context, isDark: Boolean = context.isDarkTheme()): ColorScheme {
        return if (isDark) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                dynamicDarkColorScheme(context)
            } else {
                darkColorScheme()
            }
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                dynamicLightColorScheme(context)
            } else {
                lightColorScheme()
            }
        }
    }

    fun applyTheme(codeEditor: CodeEditor, context: Context, isTextMate: Boolean = true) {
        val isSystemDark = context.isDarkTheme()
        val themeRegistry = ThemeRegistry.getInstance()
        val userTheme = com.raival.compose.file.explorer.App.Companion.globalClass.preferencesManager.textEditorTheme

        val effectiveTheme = when (userTheme) {
            "auto" -> if (isSystemDark) "darcula" else "quietlight"
            "darcula" -> "darcula"
            "quietlight" -> "quietlight"
            "dark" -> "dark"
            "light" -> "light"
            "black_darcula" -> "black_darcula"
            else -> if (isSystemDark) "darcula" else "quietlight"
        }

        val isDarkTheme = when (effectiveTheme) {
            "darcula", "dark", "black_darcula" -> true
            "quietlight", "light" -> false
            else -> isSystemDark
        }

        val isPureBlack = effectiveTheme == "black_darcula"

        if (isTextMate) {
            val loaded = themeRegistry.setTheme(effectiveTheme)
            if (!loaded) {
                val fallback = if (isDarkTheme) "dark" else "light"
                themeRegistry.setTheme(fallback)
            }

            var colorScheme = codeEditor.colorScheme
            if (colorScheme !is TextMateColorScheme) {
                colorScheme = TextMateColorScheme.create(themeRegistry)
                codeEditor.colorScheme = colorScheme
            }
            applyPatches(colorScheme, resolveMaterialColorScheme(context, isDarkTheme), isPureBlack)
        } else {
            val fallbackScheme = if (isDarkTheme) DarkScheme() else LightScheme()
            codeEditor.colorScheme = fallbackScheme
            applyPatches(fallbackScheme, resolveMaterialColorScheme(context, isDarkTheme), isPureBlack)
        }
    }

    fun applyPatches(scheme: EditorColorScheme, colorScheme: ColorScheme, isPureBlack: Boolean = false) {
        val surfaceLowest = if (isPureBlack) Color.Black.toArgb() else colorScheme.surfaceContainerLowest.toArgb()
        val surface = if (isPureBlack) Color.Black.toArgb() else colorScheme.surface.toArgb()
        val surfaceContainer = if (isPureBlack) Color.Black.toArgb() else colorScheme.surfaceContainer.toArgb()
        val surfaceContainerHigh = if (isPureBlack) 0xFF141414.toInt() else colorScheme.surfaceContainerHigh.toArgb()
        val surfaceContainerHighest = if (isPureBlack) 0xFF202020.toInt() else colorScheme.surfaceContainerHighest.toArgb()
        val onSurface = if (isPureBlack) 0xFFE0E0E0.toInt() else colorScheme.onSurface.toArgb()
        val primary = colorScheme.primary.toArgb()
        val selectionBg = colorScheme.primary.copy(alpha = 0.35f).toArgb()
        val matchedBg = colorScheme.secondary.copy(alpha = 0.35f).toArgb()

        scheme.apply {
            // General Backgrounds & Lines
            setColor(EditorColorScheme.WHOLE_BACKGROUND, surfaceLowest)
            setColor(EditorColorScheme.LINE_NUMBER_BACKGROUND, surfaceContainer)
            setColor(EditorColorScheme.LINE_NUMBER, colorScheme.onSurface.copy(alpha = 0.45f).toArgb())
            setColor(EditorColorScheme.LINE_NUMBER_CURRENT, onSurface)
            setColor(EditorColorScheme.CURRENT_LINE, surfaceContainerHigh)

            // Selections & Handles
            setColor(EditorColorScheme.SELECTION_HANDLE, primary)
            setColor(EditorColorScheme.SELECTION_INSERT, primary)
            setColor(EditorColorScheme.SELECTED_TEXT_BACKGROUND, selectionBg)
            setColor(EditorColorScheme.MATCHED_TEXT_BACKGROUND, matchedBg)

            // Delimiters
            setColor(EditorColorScheme.HIGHLIGHTED_DELIMITERS_FOREGROUND, primary)
            setColor(EditorColorScheme.HIGHLIGHTED_DELIMITERS_UNDERLINE, Color.Transparent.toArgb())

            // Code Completion Window
            setColor(EditorColorScheme.COMPLETION_WND_BACKGROUND, surfaceContainer)
            setColor(EditorColorScheme.COMPLETION_WND_ITEM_CURRENT, surfaceContainerHighest)
            setColor(EditorColorScheme.COMPLETION_WND_TEXT_PRIMARY, onSurface)
            setColor(EditorColorScheme.COMPLETION_WND_TEXT_SECONDARY, colorScheme.onSurfaceVariant.toArgb())
            setColor(EditorColorScheme.COMPLETION_WND_CORNER, surfaceContainerHighest)

            // Snippet tab stops editing highlights
            setColor(EditorColorScheme.SNIPPET_BACKGROUND_EDITING, surfaceContainerHighest)
            setColor(EditorColorScheme.SNIPPET_BACKGROUND_RELATED, surfaceContainerHigh)
            setColor(EditorColorScheme.SNIPPET_BACKGROUND_INACTIVE, surfaceContainer)
        }
    }
}
