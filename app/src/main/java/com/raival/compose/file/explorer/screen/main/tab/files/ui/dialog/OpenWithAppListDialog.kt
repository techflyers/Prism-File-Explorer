package com.raival.compose.file.explorer.screen.main.tab.files.ui.dialog

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.raival.compose.file.explorer.App.Companion.globalClass
import com.raival.compose.file.explorer.R
import com.raival.compose.file.explorer.common.emptyString
import com.raival.compose.file.explorer.common.fromJson
import com.raival.compose.file.explorer.common.toJson
import com.raival.compose.file.explorer.common.ui.BottomSheetDialog
import com.raival.compose.file.explorer.common.ui.DynamicSelectTextField
import com.raival.compose.file.explorer.common.ui.Space
import com.raival.compose.file.explorer.screen.main.tab.files.FilesTab
import com.raival.compose.file.explorer.screen.main.tab.files.holder.LocalFileHolder
import com.raival.compose.file.explorer.screen.main.tab.files.holder.OpenWithActivityHolder
import com.raival.compose.file.explorer.screen.main.tab.files.misc.DefaultOpeningMethods
import com.raival.compose.file.explorer.screen.main.tab.files.misc.FileMimeType.anyFileType
import com.raival.compose.file.explorer.screen.main.tab.files.misc.OpeningMethod
import com.raival.compose.file.explorer.screen.main.tab.files.misc.RecentOpenWithApps
import com.raival.compose.file.explorer.screen.main.tab.files.ui.ItemRow
import com.raival.compose.file.explorer.screen.main.tab.files.ui.ItemRowIcon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

private enum class OpenWithSort {
    RECENT,
    NAME_ASC,
    NAME_DESC,
    PACKAGE
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OpenWithAppListDialog(
    show: Boolean,
    tab: FilesTab,
    onDismissRequest: () -> Unit
) {
    if (show) {
        val contentHolder = tab.targetFile!! as LocalFileHolder
        val context = LocalContext.current
        val extension = contentHolder.extension.lowercase()

        val appsList = remember {
            mutableStateListOf<OpenWithActivityHolder>()
        }

        val loading = remember {
            mutableStateOf(true)
        }

        var searchQuery by remember { mutableStateOf("") }
        var sortOption by remember { mutableStateOf(OpenWithSort.RECENT) }

        val scope = rememberCoroutineScope()

        fun loadActivities(mimeType: String) {
            loading.value = true
            scope.launch(Dispatchers.IO) {
                val list = contentHolder.getAppsHandlingFile(mimeType)
                appsList.clear()
                appsList.addAll(list)
                loading.value = false
            }
        }

        LaunchedEffect(Unit) {
            loadActivities(emptyString)
        }

        val recentHistory: RecentOpenWithApps =
            fromJson(globalClass.preferencesManager.recentOpenWithApps) ?: RecentOpenWithApps()
        val recentForExt = remember(recentHistory.history, extension) {
            recentHistory.history
                .filter { it.extension == extension }
                .mapIndexed { index, entry -> Pair(entry.packageName + "/" + entry.className, index) }
                .toMap()
        }
        val recentKeys = remember(recentForExt) {
            recentForExt.keys
        }

        val filteredAndSortedApps = remember(appsList.toList(), searchQuery, sortOption, recentForExt) {
            val baseList = if (searchQuery.isBlank()) {
                appsList.toList()
            } else {
                val query = searchQuery.trim().lowercase()
                appsList.filter {
                    it.label.lowercase().contains(query) ||
                    it.packageName.lowercase().contains(query) ||
                    it.name.lowercase().contains(query)
                }
            }

            when (sortOption) {
                OpenWithSort.RECENT -> {
                    baseList.sortedWith(
                        compareBy { item ->
                            recentForExt[item.packageName + "/" + item.name] ?: Int.MAX_VALUE
                        }
                    )
                }
                OpenWithSort.NAME_ASC -> baseList.sortedBy { it.label.lowercase() }
                OpenWithSort.NAME_DESC -> baseList.sortedByDescending { it.label.lowercase() }
                OpenWithSort.PACKAGE -> baseList.sortedBy { it.packageName.lowercase() }
            }
        }

        BottomSheetDialog(
            onDismissRequest = onDismissRequest
        ) {
            Column(Modifier.animateContentSize()) {
                Text(
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .fillMaxWidth(),
                    text = stringResource(id = R.string.open_with),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )

                Space(size = 8.dp)

                DynamicSelectTextField(
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .fillMaxWidth(),
                    initValue = 0,
                    options = arrayListOf(
                        Pair(contentHolder.mimeType, contentHolder.mimeType),
                        Pair("image", "image/*"),
                        Pair("Video", "video/*"),
                        Pair("Audio", "audio/*"),
                        Pair("Text", "text/plain"),
                        Pair("Any", anyFileType)
                    ),
                    label = stringResource(R.string.mime_type),
                    onValueChangedEvent = {
                        loadActivities(it.second)
                    }
                )

                Space(size = 8.dp)

                // Search field
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text(stringResource(R.string.search)) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.Search,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp)
                )

                // Sort row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.sort_by),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    var showSortMenu by remember { mutableStateOf(false) }
                    Box {
                        TextButton(onClick = { showSortMenu = true }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.Sort,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Space(4.dp)
                            Text(
                                text = when (sortOption) {
                                    OpenWithSort.RECENT -> "Recent"
                                    OpenWithSort.NAME_ASC -> stringResource(R.string.name_a_z)
                                    OpenWithSort.NAME_DESC -> "Name (Z-A)"
                                    OpenWithSort.PACKAGE -> stringResource(R.string.package_name)
                                },
                                fontSize = 12.sp
                            )
                        }

                        DropdownMenu(
                            expanded = showSortMenu,
                            onDismissRequest = { showSortMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Recent") },
                                onClick = {
                                    sortOption = OpenWithSort.RECENT
                                    showSortMenu = false
                                },
                                trailingIcon = {
                                    if (sortOption == OpenWithSort.RECENT) {
                                        Icon(Icons.Rounded.Check, contentDescription = null)
                                    }
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.name_a_z)) },
                                onClick = {
                                    sortOption = OpenWithSort.NAME_ASC
                                    showSortMenu = false
                                },
                                trailingIcon = {
                                    if (sortOption == OpenWithSort.NAME_ASC) {
                                        Icon(Icons.Rounded.Check, contentDescription = null)
                                    }
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Name (Z-A)") },
                                onClick = {
                                    sortOption = OpenWithSort.NAME_DESC
                                    showSortMenu = false
                                },
                                trailingIcon = {
                                    if (sortOption == OpenWithSort.NAME_DESC) {
                                        Icon(Icons.Rounded.Check, contentDescription = null)
                                    }
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.package_name)) },
                                onClick = {
                                    sortOption = OpenWithSort.PACKAGE
                                    showSortMenu = false
                                },
                                trailingIcon = {
                                    if (sortOption == OpenWithSort.PACKAGE) {
                                        Icon(Icons.Rounded.Check, contentDescription = null)
                                    }
                                }
                            )
                        }
                    }
                }

                AnimatedVisibility(
                    visible = loading.value
                ) {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .padding(top = 4.dp)
                    )
                }

                LazyColumn {
                    itemsIndexed(filteredAndSortedApps, key = { _, item -> item.id }) { index, item ->
                        val itemKey = item.packageName + "/" + item.name
                        val isRecent = itemKey in recentKeys
                        val showHeaders = sortOption == OpenWithSort.RECENT && searchQuery.isBlank()

                        if (showHeaders && index == 0 && isRecent) {
                            Text(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 6.dp),
                                text = "Recent",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        val prevIsRecent = filteredAndSortedApps.getOrNull(index - 1)
                            ?.let { (it.packageName + "/" + it.name) in recentKeys } == true
                        if (showHeaders && !isRecent && index > 0 && prevIsRecent) {
                            HorizontalDivider(
                                modifier = Modifier.padding(
                                    horizontal = 16.dp,
                                    vertical = 4.dp
                                )
                            )
                            Text(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 6.dp),
                                text = "All apps",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        var showOptionsMenu by remember(item.id) {
                            mutableStateOf(false)
                        }

                        Column(
                            Modifier
                                .fillMaxWidth()
                                .animateItem()
                                .combinedClickable(
                                    onClick = {
                                        globalClass.preferencesManager.recordOpenWith(
                                            extension = extension,
                                            packageName = item.packageName,
                                            className = item.name
                                        )
                                        contentHolder.openFileWithPackage(
                                            context,
                                            item.packageName,
                                            item.name
                                        )
                                        onDismissRequest()
                                    },
                                    onLongClick = {
                                        showOptionsMenu = true
                                    }
                                )
                        ) {
                            Space(size = 4.dp)
                            ItemRow(
                                title = item.label,
                                subtitle = item.name,
                                ignoreSizePreferences = true,
                                icon = {
                                    ItemRowIcon(
                                        icon = item.icon,
                                        placeholder = R.drawable.apk_file_placeholder,
                                        ignoreSizePreferences = true
                                    )
                                }
                            )

                            Space(size = 4.dp)

                            HorizontalDivider(
                                modifier = Modifier.padding(start = 56.dp),
                                thickness = 0.5.dp
                            )

                            DropdownMenu(
                                expanded = showOptionsMenu,
                                onDismissRequest = { showOptionsMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text(text = stringResource(R.string.set_as_default_for_this_type)) },
                                    onClick = {
                                        val defOpeningMethods: DefaultOpeningMethods =
                                            fromJson(globalClass.preferencesManager.defaultOpeningMethods)
                                                ?: DefaultOpeningMethods()
                                        globalClass.preferencesManager.defaultOpeningMethods =
                                            DefaultOpeningMethods(
                                                (defOpeningMethods.openingMethods.filter { it.extension != contentHolder.extension } + OpeningMethod(
                                                    extension = contentHolder.extension,
                                                    packageName = item.packageName,
                                                    className = item.name
                                                ))
                                            ).toJson()
                                        globalClass.preferencesManager.recordOpenWith(
                                            extension = extension,
                                            packageName = item.packageName,
                                            className = item.name
                                        )
                                        contentHolder.openFileWithPackage(
                                            context,
                                            item.packageName,
                                            item.name
                                        )
                                        showOptionsMenu = false
                                        onDismissRequest()
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}