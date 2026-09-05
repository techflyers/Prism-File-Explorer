package com.raival.compose.file.explorer.screen.textEditor.intelligent

import com.raival.compose.file.explorer.App.Companion.globalClass
import io.github.rosemoe.sora.widget.CodeEditor

object AutoCloseTag {
    val supportedExtensions = setOf("html", "htm", "xhtml", "xml", "htmx", "jsx", "tsx", "svg")

    private val OPEN_TAG_REGEX = Regex("<([_a-zA-Z][a-zA-Z0-9:\\-_.]*)(?:\\s+[^<>]*?[^\\s/<>=]+?)*?\\s?(/|>)$")

    private val selfClosingTags = setOf(
        "area", "base", "br", "col", "command", "embed", "hr", "img",
        "input", "keygen", "link", "meta", "param", "source", "track", "wbr"
    )

    fun handleInsertChar(triggerCharacter: Char, editor: CodeEditor, extension: String) {
        if (!isEnabled() || !supportedExtensions.contains(extension.lowercase())) return
        if (editor.cursor.isSelected) return

        val lineIndexBefore = editor.cursor.leftLine
        val columnIndexBefore = editor.cursor.leftColumn
        val line = editor.text.getLine(lineIndexBefore)
        val lineToCursor = line.take(columnIndexBefore)

        val result = OPEN_TAG_REGEX.find(lineToCursor) ?: return
        val tagName = result.groupValues[1].lowercase()
        val endingChar = result.groupValues[2]

        val evenSingleQuotes = lineToCursor.count { it == '\'' } % 2 == 0
        val evenDoubleQuotes = lineToCursor.count { it == '\"' } % 2 == 0
        val evenBackticks = lineToCursor.count { it == '`' } % 2 == 0
        if (!evenSingleQuotes && !evenDoubleQuotes && !evenBackticks) return

        if (endingChar == ">") {
            if (selfClosingTags.contains(tagName)) return
            editor.text.insert(lineIndexBefore, columnIndexBefore, "</$tagName>")
            editor.setSelection(lineIndexBefore, columnIndexBefore)
        } else {
            if (lineToCursor.length < line.length) return
            if (columnIndexBefore >= 2 && lineToCursor[columnIndexBefore - 2] != ' ') {
                editor.text.insert(lineIndexBefore, columnIndexBefore - 1, " ")
            }
            editor.text.insert(editor.cursor.leftLine, editor.cursor.leftColumn, ">")
        }
    }

    fun isEnabled(): Boolean {
        return globalClass.preferencesManager.autoCloseTags
    }
}
