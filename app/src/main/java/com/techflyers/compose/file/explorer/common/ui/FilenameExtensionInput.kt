package com.techflyers.compose.file.explorer.common.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.techflyers.compose.file.explorer.App.Companion.globalClass
import com.techflyers.compose.file.explorer.R
import com.techflyers.compose.file.explorer.common.joinFileName
import com.techflyers.compose.file.explorer.common.splitFileName

@Composable
fun FilenameExtensionInput(
    initialFullName: String,
    modifier: Modifier = Modifier,
    isFolder: Boolean = false,
    label: String = stringResource(R.string.name),
    extensionLabel: String = stringResource(R.string.extension),
    autoFocusName: Boolean = false,
    outlined: Boolean = false,
    isError: Boolean = false,
    supportingText: @Composable (() -> Unit)? = null,
    onFullNameChanged: (String) -> Unit,
    onConfirm: ((String) -> Unit)? = null
) {
    val initialSplit = remember(initialFullName, isFolder) {
        splitFileName(initialFullName, isFolder)
    }

    var nameField by remember(initialFullName) {
        mutableStateOf(TextFieldValue(initialSplit.first, TextRange(initialSplit.first.length)))
    }
    var extField by remember(initialFullName) {
        mutableStateOf(TextFieldValue(initialSplit.second, TextRange(initialSplit.second.length)))
    }

    val nameFocusRequester = remember { FocusRequester() }
    val extFocusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val disableEnterFocusExt = globalClass.preferencesManager.disableEnterFocusExtension

    fun currentJoinedName(): String =
        joinFileName(nameField.text, if (isFolder) "" else extField.text)

    LaunchedEffect(initialFullName) {
        val (b, e) = splitFileName(initialFullName, isFolder)
        if (nameField.text != b) {
            nameField = TextFieldValue(b, TextRange(b.length))
        }
        if (extField.text != e) {
            extField = TextFieldValue(e, TextRange(e.length))
        }
    }

    LaunchedEffect(nameField.text, extField.text) {
        onFullNameChanged(currentJoinedName())
    }

    LaunchedEffect(autoFocusName) {
        if (autoFocusName) {
            try {
                nameFocusRequester.requestFocus()
            } catch (_: Exception) {}
        }
    }

    fun handleNameEnter() {
        if (isFolder || disableEnterFocusExt) {
            focusManager.clearFocus()
            onConfirm?.invoke(currentJoinedName())
        } else {
            extField = extField.copy(selection = TextRange(extField.text.length))
            try {
                extFocusRequester.requestFocus()
            } catch (_: Exception) {}
        }
    }

    fun handleExtEnter() {
        focusManager.clearFocus()
        onConfirm?.invoke(currentJoinedName())
    }

    val textFieldColors = TextFieldDefaults.colors(
        errorIndicatorColor = Color.Transparent,
        focusedIndicatorColor = Color.Transparent,
        unfocusedIndicatorColor = Color.Transparent,
        disabledIndicatorColor = Color.Transparent
    )
    val shape = RoundedCornerShape(6.dp)

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (outlined) {
            OutlinedTextField(
                value = nameField,
                onValueChange = { updated ->
                    val hasNewline = updated.text.contains('\n') || updated.text.contains('\r')
                    val cleaned = updated.text.replace("\n", "").replace("\r", "")
                    nameField = if (cleaned != updated.text) {
                        updated.copy(
                            text = cleaned,
                            selection = TextRange(
                                updated.selection.start.coerceIn(0, cleaned.length),
                                updated.selection.end.coerceIn(0, cleaned.length)
                            )
                        )
                    } else updated
                    if (hasNewline) {
                        handleNameEnter()
                    }
                },
                label = { Text(label) },
                singleLine = false,
                minLines = 1,
                maxLines = 5,
                isError = isError,
                keyboardOptions = KeyboardOptions(
                    imeAction = if (isFolder || disableEnterFocusExt) ImeAction.Done else ImeAction.Next
                ),
                keyboardActions = KeyboardActions(
                    onNext = { handleNameEnter() },
                    onDone = { handleNameEnter() }
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (autoFocusName) Modifier.autoShowKeyboard() else Modifier)
                    .focusRequester(nameFocusRequester)
                    .onPreviewKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown &&
                            (event.key == Key.Enter || event.key == Key.NumPadEnter)
                        ) {
                            handleNameEnter()
                            true
                        } else false
                    }
            )

            if (!isFolder) {
                OutlinedTextField(
                    value = extField,
                    onValueChange = { updated ->
                        val hasNewline = updated.text.contains('\n') || updated.text.contains('\r')
                        val cleaned = updated.text.replace(".", "").replace("\n", "").replace("\r", "")
                        extField = if (cleaned != updated.text) {
                            updated.copy(
                                text = cleaned,
                                selection = TextRange(
                                    updated.selection.start.coerceIn(0, cleaned.length),
                                    updated.selection.end.coerceIn(0, cleaned.length)
                                )
                            )
                        } else updated
                        if (hasNewline) {
                            handleExtEnter()
                        }
                    },
                    prefix = { Text(".") },
                    label = { Text(extensionLabel) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(
                        onDone = { handleExtEnter() },
                        onNext = { handleExtEnter() },
                        onGo = { handleExtEnter() }
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(extFocusRequester)
                        .onPreviewKeyEvent { event ->
                            if (event.type == KeyEventType.KeyDown &&
                                (event.key == Key.Enter || event.key == Key.NumPadEnter)
                            ) {
                                handleExtEnter()
                                true
                            } else false
                        }
                )
            }
        } else {
            TextField(
                value = nameField,
                onValueChange = { updated ->
                    val hasNewline = updated.text.contains('\n') || updated.text.contains('\r')
                    val cleaned = updated.text.replace("\n", "").replace("\r", "")
                    nameField = if (cleaned != updated.text) {
                        updated.copy(
                            text = cleaned,
                            selection = TextRange(
                                updated.selection.start.coerceIn(0, cleaned.length),
                                updated.selection.end.coerceIn(0, cleaned.length)
                            )
                        )
                    } else updated
                    if (hasNewline) {
                        handleNameEnter()
                    }
                },
                label = { Text(label) },
                singleLine = false,
                minLines = 1,
                maxLines = 5,
                shape = shape,
                colors = textFieldColors,
                isError = isError,
                keyboardOptions = KeyboardOptions(
                    imeAction = if (isFolder || disableEnterFocusExt) ImeAction.Done else ImeAction.Next
                ),
                keyboardActions = KeyboardActions(
                    onNext = { handleNameEnter() },
                    onDone = { handleNameEnter() }
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (autoFocusName) Modifier.autoShowKeyboard() else Modifier)
                    .focusRequester(nameFocusRequester)
                    .onPreviewKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown &&
                            (event.key == Key.Enter || event.key == Key.NumPadEnter)
                        ) {
                            handleNameEnter()
                            true
                        } else false
                    }
            )

            if (!isFolder) {
                TextField(
                    value = extField,
                    onValueChange = { updated ->
                        val hasNewline = updated.text.contains('\n') || updated.text.contains('\r')
                        val cleaned = updated.text.replace(".", "").replace("\n", "").replace("\r", "")
                        extField = if (cleaned != updated.text) {
                            updated.copy(
                                text = cleaned,
                                selection = TextRange(
                                    updated.selection.start.coerceIn(0, cleaned.length),
                                    updated.selection.end.coerceIn(0, cleaned.length)
                                )
                            )
                        } else updated
                        if (hasNewline) {
                            handleExtEnter()
                        }
                    },
                    prefix = { Text(".") },
                    label = { Text(extensionLabel) },
                    singleLine = true,
                    shape = shape,
                    colors = textFieldColors,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(
                        onDone = { handleExtEnter() },
                        onNext = { handleExtEnter() },
                        onGo = { handleExtEnter() }
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(extFocusRequester)
                        .onPreviewKeyEvent { event ->
                            if (event.type == KeyEventType.KeyDown &&
                                (event.key == Key.Enter || event.key == Key.NumPadEnter)
                            ) {
                                handleExtEnter()
                                true
                            } else false
                        }
                )
            }
        }

        if (supportingText != null) {
            supportingText()
        }
    }
}
