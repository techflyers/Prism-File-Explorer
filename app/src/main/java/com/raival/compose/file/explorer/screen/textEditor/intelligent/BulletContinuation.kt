package com.raival.compose.file.explorer.screen.textEditor.intelligent

import android.view.KeyEvent
import com.raival.compose.file.explorer.App.Companion.globalClass
import io.github.rosemoe.sora.event.EditorKeyEvent
import io.github.rosemoe.sora.widget.CodeEditor

object BulletContinuation {
    val supportedExtensions = setOf("md", "markdown", "mdown", "mkd", "mkdn")

    private val QUOTE_REGEX = Regex("^> ")
    private val LIST_WHITESPACE_REGEX = Regex("^\\s*([-+*]|[0-9]+[.)]) +(\\[[ x]] +)?")
    private val LIST_REGEX = Regex("^([-+*]|[0-9]+[.)])( +\\[[ x]])?\$")
    private val UL_LIST_REGEX = Regex("^((\\s*[-+*] +)(\\[[ x]] +)?)")
    private val OL_LIST_REGEX = Regex("^(\\s*)([0-9]+)([.)])( +)((\\[[ x]] +)?)")

    fun handleKeyEvent(event: EditorKeyEvent, editor: CodeEditor, extension: String) {
        if (!isEnabled() || !supportedExtensions.contains(extension.lowercase())) return
        if (event.action != KeyEvent.ACTION_DOWN) return

        if (event.keyCode == KeyEvent.KEYCODE_ENTER && event.modifiers == 0) {
            onEnter(editor) {
                event.result = true
                event.intercept()
            }
        } else if (event.keyCode == KeyEvent.KEYCODE_TAB && !event.isCtrlPressed && !event.isAltPressed) {
            onTab(editor, event.isShiftPressed) {
                event.result = true
                event.intercept()
            }
        }
    }

    private fun onTab(editor: CodeEditor, shiftPressed: Boolean, consumeEvent: () -> Unit) {
        if (editor.cursor.leftLine != editor.cursor.rightLine) return
        val lineIndexBefore = editor.cursor.leftLine
        val columnIndexBefore = editor.cursor.leftColumn

        val line = editor.text.getLine(lineIndexBefore)
        val lineToCursor = line.take(columnIndexBefore)

        val listMatch = LIST_WHITESPACE_REGEX.find(line)
        if (listMatch != null && (lineToCursor.endsWith(listMatch.value) || editor.cursor.isSelected)) {
            if (!shiftPressed) {
                editor.indentLines(false)
            } else {
                editor.unindentSelection()
            }
            consumeEvent()
        }
    }

    private fun onEnter(editor: CodeEditor, consumeEvent: () -> Unit) {
        if (editor.cursor.isSelected) return
        val lineIndexBefore = editor.cursor.leftLine
        val columnIndexBefore = editor.cursor.leftColumn

        val line = editor.text.getLine(lineIndexBefore)
        val lineToCursor = line.take(columnIndexBefore)

        // Handle quotes
        val quoteMatch = QUOTE_REGEX.find(line)
        if (quoteMatch != null) {
            if (line.trim().toString() == ">") {
                editor.text.delete(lineIndexBefore, 0, lineIndexBefore, line.length)
            } else {
                editor.text.insert(lineIndexBefore, columnIndexBefore, "\n> ")
            }
            consumeEvent()
            return
        }

        // If list item is empty -> remove it on enter
        val liMatch = LIST_REGEX.matchEntire(line.trim())
        if (liMatch != null) {
            editor.text.delete(lineIndexBefore, 0, lineIndexBefore, line.length)
            consumeEvent()
            return
        }

        // If unordered list item with text -> add empty list item on enter
        val ulLiMatch = UL_LIST_REGEX.find(lineToCursor)
        if (ulLiMatch != null) {
            val listPrefix = ulLiMatch.groupValues[1]
            val appendedListItem = '\n' + listPrefix.replace("[x]", "[ ]")
            editor.text.insert(lineIndexBefore, columnIndexBefore, appendedListItem)
            consumeEvent()
            return
        }

        // If numbered list item with text -> add empty numbered list item on enter
        val olLiMatch = OL_LIST_REGEX.find(lineToCursor)
        if (olLiMatch != null) {
            val leadingSpace = olLiMatch.groupValues[1]
            val previousMarker = olLiMatch.groupValues[2]
            val delimiter = olLiMatch.groupValues[3]
            var trailingSpace = olLiMatch.groupValues[4]
            val checkbox = olLiMatch.groupValues[5].replace("[x]", "[ ]")

            val marker = (previousMarker.toInt() + 1).toString()
            val markerDiff = previousMarker.length - marker.length
            val newTrailingSpaceLength = (trailingSpace.length + markerDiff).coerceAtLeast(1)
            trailingSpace = " ".repeat(newTrailingSpaceLength)

            val appendedListItem = '\n' + leadingSpace + marker + delimiter + trailingSpace + checkbox
            editor.text.insert(lineIndexBefore, columnIndexBefore, appendedListItem)
            consumeEvent()
            return
        }
    }

    fun isEnabled(): Boolean {
        return globalClass.preferencesManager.bulletContinuation
    }
}
