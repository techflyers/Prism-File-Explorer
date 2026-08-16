package com.raival.compose.file.explorer.common.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import kotlinx.coroutines.delay

/**
 * Requests focus and shows the IME when a text field first appears
 * (rename dialogs, create-file dialogs, and other editable fields).
 */
@Composable
fun Modifier.autoShowKeyboard(delayMs: Long = 80L): Modifier {
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {
        delay(delayMs)
        runCatching { focusRequester.requestFocus() }
        keyboard?.show()
    }
    return this.focusRequester(focusRequester)
}
