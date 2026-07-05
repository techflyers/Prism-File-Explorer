package com.raival.compose.file.explorer.screen.textEditor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.raival.compose.file.explorer.R
import com.raival.compose.file.explorer.common.ui.CheckableText
import com.raival.compose.file.explorer.common.ui.Space
import com.raival.compose.file.explorer.screen.textEditor.model.Searcher
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.EditorSearcher

@Composable
fun SearchPanel(
    codeEditor: CodeEditor,
    searcher: Searcher
) {
    fun codeEditorSearcher() = codeEditor.searcher
    fun hasQuery() =
        searcher.query.isNotEmpty() && codeEditorSearcher().matchedPositionCount > 0

    /** Safely run a search, silently ignoring invalid regex patterns. */
    fun safeSearch(query: String, options: EditorSearcher.SearchOptions) {
        try {
            codeEditor.searcher.search(query, options)
        } catch (_: Exception) {
            // Ignore PatternSyntaxException (and any other error) caused by
            // an incomplete / invalid regex so the app doesn't crash.
        }
    }

    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colorScheme.surfaceContainer)
            .padding(8.dp)
    ) {
        LaunchedEffect(Unit) {
            // Restore a previous search on re-open.
            if (searcher.query.isNotEmpty()) {
                safeSearch(
                    searcher.query,
                    EditorSearcher.SearchOptions(!searcher.caseSensitive, searcher.useRegex)
                )
            }
            // Pull up the keyboard automatically.
            focusRequester.requestFocus()
            keyboardController?.show()
        }

        TextField(
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester),
            value = searcher.query,
            onValueChange = {
                searcher.query = it
                if (it.isEmpty()) {
                    codeEditor.searcher.stopSearch()
                    codeEditor.invalidate()
                } else {
                    safeSearch(
                        it,
                        EditorSearcher.SearchOptions(!searcher.caseSensitive, searcher.useRegex)
                    )
                }
            },
            label = { Text(text = stringResource(R.string.find)) },
        )

        Space(size = 4.dp)

        TextField(
            modifier = Modifier.fillMaxWidth(),
            value = searcher.replace,
            onValueChange = { searcher.replace = it },
            label = { Text(text = stringResource(R.string.replace)) },
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            CheckableText(
                modifier = Modifier
                    .weight(1f)
                    .padding(12.dp),
                checked = searcher.useRegex,
                onCheckedChange = {
                    searcher.useRegex = it
                    if (searcher.query.isEmpty()) {
                        codeEditor.searcher.stopSearch()
                        codeEditor.invalidate()
                    } else {
                        safeSearch(
                            searcher.query,
                            EditorSearcher.SearchOptions(
                                !searcher.caseSensitive,
                                searcher.useRegex
                            )
                        )
                    }
                },
                uncheckedBoxBackgroundColor = colorScheme.surfaceColorAtElevation(8.dp),
                text = { Text(text = stringResource(R.string.regex)) }
            )

            CheckableText(
                modifier = Modifier
                    .weight(1f)
                    .padding(12.dp),
                checked = searcher.caseSensitive,
                onCheckedChange = {
                    searcher.caseSensitive = it
                    if (searcher.query.isEmpty()) {
                        codeEditor.searcher.stopSearch()
                        codeEditor.invalidate()
                    } else {
                        safeSearch(
                            searcher.query,
                            EditorSearcher.SearchOptions(
                                !searcher.caseSensitive,
                                searcher.useRegex
                            )
                        )
                    }
                },
                uncheckedBoxBackgroundColor = colorScheme.surfaceColorAtElevation(8.dp),
                text = { Text(text = stringResource(R.string.case_sensitive)) }
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth()
        ) {
            Space(size = 8.dp)
            OutlinedButton(
                onClick = {
                    if (hasQuery()) codeEditorSearcher().replaceCurrentMatch(searcher.replace)
                }
            ) {
                Text(text = stringResource(R.string.rep))
            }
            Space(size = 8.dp)
            OutlinedButton(
                onClick = {
                    if (hasQuery()) codeEditorSearcher().replaceAll(searcher.replace)
                }
            ) {
                Text(text = stringResource(R.string.all))
            }
            Space(size = 8.dp)
            OutlinedButton(
                modifier = Modifier.weight(1f),
                onClick = {
                    if (hasQuery()) codeEditorSearcher().gotoPrevious()
                }
            ) {
                Text(text = stringResource(R.string.prev))
            }
            Space(size = 8.dp)
            OutlinedButton(
                modifier = Modifier.weight(1f),
                onClick = {
                    if (hasQuery()) codeEditorSearcher().gotoNext()
                }
            ) {
                Text(text = stringResource(R.string.next))
            }
            Space(size = 8.dp)
        }
    }
}