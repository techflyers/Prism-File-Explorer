package com.techflyers.compose.file.explorer.screen.preferences

import android.annotation.SuppressLint
import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.techflyers.compose.file.explorer.R
import com.techflyers.compose.file.explorer.base.BaseActivity
import com.techflyers.compose.file.explorer.common.ui.SafeSurface
import com.techflyers.compose.file.explorer.common.ui.fastScrollbar
import com.techflyers.compose.file.explorer.screen.preferences.ui.AppInfoContainer
import com.techflyers.compose.file.explorer.screen.preferences.ui.AppearanceContainer
import com.techflyers.compose.file.explorer.screen.preferences.ui.BehaviorContainer
import com.techflyers.compose.file.explorer.screen.preferences.ui.FileListContainer
import com.techflyers.compose.file.explorer.screen.preferences.ui.FileOperationContainer
import com.techflyers.compose.file.explorer.screen.preferences.ui.LocalPreferenceSearchQuery
import com.techflyers.compose.file.explorer.screen.preferences.ui.RecentFilesContainer
import com.techflyers.compose.file.explorer.screen.preferences.ui.ShizukuContainer
import com.techflyers.compose.file.explorer.screen.preferences.ui.SingleChoiceDialog
import com.techflyers.compose.file.explorer.screen.preferences.ui.TerminalContainer
import com.techflyers.compose.file.explorer.screen.preferences.ui.TextEditorContainer
import com.techflyers.compose.file.explorer.theme.FileExplorerTheme

class PreferencesActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        checkPermissions()
    }

    @SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
    override fun onPermissionGranted() {
        setContent {
            FileExplorerTheme {
                SafeSurface {
                    val prefsListState = androidx.compose.foundation.lazy.rememberLazyListState()
                    var isSearchOpen by rememberSaveable { mutableStateOf(false) }
                    var searchQuery by rememberSaveable { mutableStateOf("") }

                    BackHandler(enabled = isSearchOpen) {
                        isSearchOpen = false
                        searchQuery = ""
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(60.dp)
                            .background(color = colorScheme.surfaceContainer)
                            .padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isSearchOpen) {
                            val focusRequester = remember { FocusRequester() }
                            LaunchedEffect(Unit) {
                                focusRequester.requestFocus()
                            }
                            IconButton(onClick = {
                                isSearchOpen = false
                                searchQuery = ""
                            }) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                    contentDescription = stringResource(id = R.string.back)
                                )
                            }
                            TextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                modifier = Modifier
                                    .weight(1f)
                                    .focusRequester(focusRequester),
                                placeholder = {
                                    Text(
                                        text = "Search settings…",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = colorScheme.onSurfaceVariant
                                    )
                                },
                                singleLine = true,
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                    disabledContainerColor = Color.Transparent,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent,
                                    disabledIndicatorColor = Color.Transparent
                                ),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                keyboardActions = KeyboardActions(onSearch = { })
                            )
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(
                                        imageVector = Icons.Rounded.Close,
                                        contentDescription = "Clear search"
                                    )
                                }
                            }
                        } else {
                            IconButton(onClick = { onBackPressedDispatcher.onBackPressed() }) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                    contentDescription = stringResource(id = R.string.back)
                                )
                            }
                            Text(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(start = 8.dp),
                                text = stringResource(id = R.string.preferences),
                                fontSize = 20.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            IconButton(onClick = { isSearchOpen = true }) {
                                Icon(
                                    imageVector = Icons.Rounded.Search,
                                    contentDescription = stringResource(id = R.string.search)
                                )
                            }
                        }
                    }

                    SingleChoiceDialog()

                    CompositionLocalProvider(LocalPreferenceSearchQuery provides searchQuery) {
                        androidx.compose.foundation.lazy.LazyColumn(
                            state = prefsListState,
                            modifier = Modifier
                                .fillMaxSize()
                                .fastScrollbar(prefsListState),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            item(key = "appearance") { AppearanceContainer() }
                            item(key = "file_list") { FileListContainer() }
                            item(key = "file_operation") { FileOperationContainer() }
                            item(key = "behavior") { BehaviorContainer() }
                            item(key = "shizuku") { ShizukuContainer() }
                            item(key = "recent_files") { RecentFilesContainer() }
                            item(key = "text_editor") { TextEditorContainer() }
                            item(key = "terminal") { TerminalContainer() }
                            item(key = "app_info") { AppInfoContainer() }
                        }
                    }
                }
            }
        }
    }
}
