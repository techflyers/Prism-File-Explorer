package com.techflyers.compose.file.explorer.screen.main.tab.files.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material.icons.rounded.Tab
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateSetOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.techflyers.compose.file.explorer.App.Companion.globalClass

enum class EmptyAreaAction(
    val id: String,
    val title: String,
    val icon: ImageVector
) {
    NEW_TAB("new_tab", "New tab", Icons.Rounded.Tab),
    DUPLICATE_TAB("duplicate_tab", "Duplicate current tab", Icons.Rounded.ContentCopy),
    CLOSE_TAB("close_tab", "Close current tab", Icons.Rounded.Close),
    REOPEN_CLOSED_TAB("reopen_closed_tab", "Reopen closed tab", Icons.Rounded.Restore),
    OPEN_IN_TERMINAL("open_in_terminal", "Open in terminal", Icons.Rounded.Terminal),
    CREATE_NEW("create_new", "Create new file / folder", Icons.Rounded.CreateNewFolder),
    PASTE_HERE("paste_here", "Paste here", Icons.Rounded.ContentPaste),
    SELECT_ALL("select_all", "Select all", Icons.Rounded.SelectAll),
    SORT_BY("sort_by", "Sort by…", Icons.AutoMirrored.Rounded.Sort),
    TOGGLE_HIDDEN("toggle_hidden", "Show / hide hidden files", Icons.Rounded.Visibility),
    REFRESH("refresh", "Refresh", Icons.Rounded.Refresh),
    FOLDER_PROPERTIES("folder_properties", "Folder properties", Icons.Rounded.Info);

    companion object {
        fun getOrderedVisibleActions(): List<EmptyAreaAction> {
            val savedOrder = globalClass.preferencesManager.emptyAreaMenuOrder
                .split(",")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
            val hiddenIds = globalClass.preferencesManager.emptyAreaMenuHidden
                .split(",")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .toSet()

            val byId = entries.associateBy { it.id }
            val ordered = mutableListOf<EmptyAreaAction>()
            savedOrder.forEach { id -> byId[id]?.let { ordered.add(it) } }
            entries.forEach { action -> if (action !in ordered) ordered.add(action) }
            return ordered.filter { it.id !in hiddenIds }
        }
    }
}

@Composable
fun CustomizeEmptyAreaMenuDialog(
    onDismiss: () -> Unit
) {
    val allById = remember { EmptyAreaAction.entries.associateBy { it.id } }
    val currentOrder = remember {
        val raw = globalClass.preferencesManager.emptyAreaMenuOrder
            .split(",")
            .mapNotNull { allById[it.trim()] }
        val full = raw.toMutableList()
        EmptyAreaAction.entries.forEach { if (it !in full) full.add(it) }
        mutableStateListOf(*full.toTypedArray())
    }
    val hiddenSet = remember {
        val raw = globalClass.preferencesManager.emptyAreaMenuHidden
            .split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        mutableStateSetOf(*raw.toTypedArray())
    }

    fun persist() {
        globalClass.preferencesManager.emptyAreaMenuOrder = currentOrder.joinToString(",") { it.id }
        globalClass.preferencesManager.emptyAreaMenuHidden = hiddenSet.joinToString(",")
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Customize Empty-Area Context Menu") },
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                itemsIndexed(currentOrder, key = { _, item -> item.id }) { index, item ->
                    val isVisible = item.id !in hiddenSet
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = isVisible,
                            onCheckedChange = { checked ->
                                if (checked) hiddenSet.remove(item.id) else hiddenSet.add(item.id)
                                persist()
                            }
                        )
                        Icon(
                            imageVector = item.icon,
                            contentDescription = null,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Text(
                            text = item.title,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            enabled = index > 0,
                            onClick = {
                                val prev = currentOrder.removeAt(index)
                                currentOrder.add(index - 1, prev)
                                persist()
                            }
                        ) {
                            Icon(Icons.Rounded.KeyboardArrowUp, contentDescription = "Move up")
                        }
                        IconButton(
                            enabled = index < currentOrder.lastIndex,
                            onClick = {
                                val next = currentOrder.removeAt(index)
                                currentOrder.add(index + 1, next)
                                persist()
                            }
                        ) {
                            Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Move down")
                        }
                    }
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    currentOrder.clear()
                    currentOrder.addAll(EmptyAreaAction.entries)
                    hiddenSet.clear()
                    persist()
                }
            ) {
                Text("Reset")
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done")
            }
        }
    )
}
