package com.techflyers.compose.file.explorer.screen.main.tab.files.ui.dialog

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.techflyers.compose.file.explorer.App.Companion.globalClass
import com.techflyers.compose.file.explorer.R
import com.techflyers.compose.file.explorer.common.ui.Space
import com.techflyers.compose.file.explorer.screen.main.tab.files.holder.VirtualFileHolder
import com.techflyers.compose.file.explorer.screen.main.tab.files.misc.CompactFilterChip
import com.techflyers.compose.file.explorer.screen.main.tab.files.misc.CustomFilterItem
import com.techflyers.compose.file.explorer.screen.main.tab.files.misc.FilterConfigManager
import com.techflyers.compose.file.explorer.screen.main.tab.files.misc.FilterScope
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@Composable
fun FilterCustomizationDialog(
    initialScope: FilterScope = FilterScope.GENERAL,
    initialCategoryType: Int = VirtualFileHolder.IMAGE,
    onDismiss: () -> Unit,
    onUpdated: () -> Unit = {}
) {
    val prefs = globalClass.preferencesManager
    var activeScope by remember { mutableStateOf(initialScope) }
    var activeCategoryType by remember {
        mutableIntStateOf(
            if (initialCategoryType in listOf(
                    VirtualFileHolder.IMAGE,
                    VirtualFileHolder.VIDEO,
                    VirtualFileHolder.AUDIO,
                    VirtualFileHolder.DOCUMENT,
                    VirtualFileHolder.ARCHIVE,
                    VirtualFileHolder.APK
                )
            ) initialCategoryType else VirtualFileHolder.IMAGE
        )
    }

    val filterItems = remember(activeScope, activeCategoryType) {
        mutableStateListOf<CustomFilterItem>().apply {
            val loaded = when (activeScope) {
                FilterScope.GENERAL -> FilterConfigManager.getGeneralFilters()
                FilterScope.SEARCH -> FilterConfigManager.getSearchFilters()
                FilterScope.CATEGORY -> FilterConfigManager.getCategoryFilters(activeCategoryType)
            }
            addAll(loaded)
        }
    }

    fun persistCurrentList() {
        when (activeScope) {
            FilterScope.GENERAL -> FilterConfigManager.saveGeneralFilters(filterItems.toList())
            FilterScope.SEARCH -> FilterConfigManager.saveSearchFilters(filterItems.toList())
            FilterScope.CATEGORY -> FilterConfigManager.saveCategoryFilters(activeCategoryType, filterItems.toList())
        }
        prefs.filterConfigVersion++
        onUpdated()
    }

    var editingItem by remember { mutableStateOf<CustomFilterItem?>(null) }
    var isAddingNew by remember { mutableStateOf(false) }

    if (editingItem != null || isAddingNew) {
        val current = editingItem
        var nameInput by remember(current) { mutableStateOf(current?.name ?: "") }
        var extInput by remember(current) {
            mutableStateOf(
                current?.extensions?.joinToString(", ") ?: ""
            )
        }

        AlertDialog(
            onDismissRequest = {
                editingItem = null
                isAddingNew = false
            },
            title = {
                Text(if (isAddingNew) "Add Filter" else "Edit Filter")
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = nameInput,
                        onValueChange = { nameInput = it },
                        label = { Text("Filter Name") },
                        placeholder = { Text("e.g. RAW Photos, Code, Logs") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = extInput,
                        onValueChange = { extInput = it },
                        label = { Text("File Extensions (comma-separated)") },
                        placeholder = { Text("e.g. jpg, png, webp (or __folder__ for folders)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = "Enter extensions separated by commas without dots (e.g. mp4, mkv, mov). Use __folder__ to match directories.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val trimmedName = nameInput.trim()
                        val parsedExts = extInput
                            .split(",", ";", " ")
                            .map { it.trim().lowercase().removePrefix(".") }
                            .filter { it.isNotEmpty() }
                            .distinct()
                        if (trimmedName.isNotEmpty() && parsedExts.isNotEmpty()) {
                            if (isAddingNew) {
                                val newId = "custom_${System.currentTimeMillis()}"
                                filterItems.add(
                                    CustomFilterItem(
                                        id = newId,
                                        name = trimmedName,
                                        extensions = parsedExts,
                                        isEnabled = true
                                    )
                                )
                            } else if (current != null) {
                                val idx = filterItems.indexOfFirst { it.id == current.id }
                                if (idx >= 0) {
                                    filterItems[idx] = current.copy(
                                        name = trimmedName,
                                        extensions = parsedExts
                                    )
                                }
                            }
                            persistCurrentList()
                            editingItem = null
                            isAddingNew = false
                        }
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        editingItem = null
                        isAddingNew = false
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Rounded.FilterList,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text(
                text = "Customize Filters",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Position Selector: Top vs Bottom
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Filter Bar Position",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = if (prefs.filterBarAtBottom) "Displayed at the bottom" else "Displayed at the top",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        CompactFilterChip(
                            label = "Top",
                            selected = !prefs.filterBarAtBottom,
                            onClick = {
                                prefs.filterBarAtBottom = false
                                onUpdated()
                            }
                        )
                        CompactFilterChip(
                            label = "Bottom",
                            selected = prefs.filterBarAtBottom,
                            onClick = {
                                prefs.filterBarAtBottom = true
                                onUpdated()
                            }
                        )
                    }
                }

                // Scope Selector: General / Search / Category
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    CompactFilterChip(
                        label = "General Filters",
                        selected = activeScope == FilterScope.GENERAL,
                        onClick = { activeScope = FilterScope.GENERAL },
                        modifier = Modifier.weight(1f)
                    )
                    CompactFilterChip(
                        label = "Search Filters",
                        selected = activeScope == FilterScope.SEARCH,
                        onClick = { activeScope = FilterScope.SEARCH },
                        modifier = Modifier.weight(1f)
                    )
                    CompactFilterChip(
                        label = "Categories",
                        selected = activeScope == FilterScope.CATEGORY,
                        onClick = { activeScope = FilterScope.CATEGORY },
                        modifier = Modifier.weight(1f)
                    )
                }

                if (activeScope == FilterScope.CATEGORY) {
                    val categoryTabs = listOf(
                        VirtualFileHolder.IMAGE to "Images",
                        VirtualFileHolder.VIDEO to "Videos",
                        VirtualFileHolder.AUDIO to "Audio",
                        VirtualFileHolder.DOCUMENT to "Docs",
                        VirtualFileHolder.ARCHIVE to "Archives",
                        VirtualFileHolder.APK to "APKs"
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        categoryTabs.forEach { (type, label) ->
                            CompactFilterChip(
                                label = label,
                                selected = activeCategoryType == type,
                                onClick = { activeCategoryType = type }
                            )
                        }
                    }
                }

                HorizontalDivider()

                val listState = rememberLazyListState()
                val reorderableState = rememberReorderableLazyListState(listState) { from, to ->
                    filterItems.add(to.index, filterItems.removeAt(from.index))
                    persistCurrentList()
                }

                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 280.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    itemsIndexed(filterItems, key = { _, item -> item.id }) { index, item ->
                        ReorderableItem(reorderableState, key = item.id) { isDragging ->
                            val bgAnim by animateColorAsState(
                                targetValue = if (isDragging) MaterialTheme.colorScheme.surfaceContainerHighest
                                else MaterialTheme.colorScheme.surfaceContainerLow,
                                label = "filterDragBg"
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(bgAnim, RoundedCornerShape(8.dp))
                                    .clickable { editingItem = item }
                                    .padding(horizontal = 4.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .draggableHandle(),
                                    onClick = {}
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.DragHandle,
                                        contentDescription = "Reorder",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(horizontal = 4.dp)
                                ) {
                                    Text(
                                        text = item.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = item.extensions.joinToString(", "),
                                        style = MaterialTheme.typography.bodySmall,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                IconButton(
                                    onClick = { editingItem = item },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Edit,
                                        contentDescription = "Rename & Edit Extensions",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        filterItems.removeAt(index)
                                        persistCurrentList()
                                    },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Delete,
                                        contentDescription = "Remove Filter",
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                Switch(
                                    checked = item.isEnabled,
                                    onCheckedChange = { checked ->
                                        filterItems[index] = item.copy(isEnabled = checked)
                                        persistCurrentList()
                                    },
                                    modifier = Modifier.padding(start = 2.dp)
                                )
                            }
                        }
                    }
                }

                OutlinedButton(
                    onClick = { isAddingNew = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Add New Filter")
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    filterItems.clear()
                    val defaults = when (activeScope) {
                        FilterScope.GENERAL -> FilterConfigManager.defaultGeneralFilters()
                        FilterScope.SEARCH -> FilterConfigManager.defaultSearchFilters()
                        FilterScope.CATEGORY -> FilterConfigManager.defaultCategoryFilters(activeCategoryType)
                    }
                    filterItems.addAll(defaults)
                    persistCurrentList()
                }
            ) {
                Icon(
                    imageVector = Icons.Rounded.RestartAlt,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Space(4.dp)
                Text("Reset")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}
