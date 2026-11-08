package com.techflyers.compose.file.explorer.screen.main.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AddTask
import androidx.compose.material.icons.rounded.AutoAwesomeMotion
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.EditAttributes
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.RemoveRedEye
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material.icons.rounded.Title
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.ViewComfy
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.techflyers.compose.file.explorer.App.Companion.globalClass
import com.techflyers.compose.file.explorer.R
import com.techflyers.compose.file.explorer.common.ui.Space
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

data class TopToolbarItemConfig(
    val id: String,
    val isEnabled: Boolean = true
)

enum class TopToolbarButtonItem(
    val id: String,
    val label: String,
    val subtitle: String,
    val icon: ImageVector,
    val defaultEnabled: Boolean
) {
    HAMBURGER_MENU(
        id = "hamburger_menu",
        label = "Hamburger menu (Navigation drawer)",
        subtitle = "Opens the sidebar navigation drawer",
        icon = Icons.Rounded.Menu,
        defaultEnabled = true
    ),
    CONTEXT_NAVIGATION(
        id = "context_navigation",
        label = "Deselect / Forward button",
        subtitle = "Shows Clear Selection when selecting files or Forward when history is available",
        icon = Icons.AutoMirrored.Rounded.ArrowForward,
        defaultEnabled = true
    ),
    TITLE_AND_TABS(
        id = "title_and_tabs",
        label = "Title / Single-row tab switcher",
        subtitle = "Displays current tab title or inline tab switcher; separates left and right buttons",
        icon = Icons.Rounded.Title,
        defaultEnabled = true
    ),
    SEARCH(
        id = "search",
        label = "Search button",
        subtitle = "Opens file search dialog",
        icon = Icons.Rounded.Search,
        defaultEnabled = true
    ),
    CREATE_NEW(
        id = "create_new",
        label = "Create new (+) button",
        subtitle = "Creates a new file or folder in the current directory",
        icon = Icons.Rounded.Add,
        defaultEnabled = true
    ),
    FILTER_RIBBON(
        id = "filter_ribbon",
        label = "Directory filter ribbon toggle",
        subtitle = "Toggles the file type filter bar in directories",
        icon = Icons.Rounded.FilterList,
        defaultEnabled = true
    ),
    SORT(
        id = "sort",
        label = "Quick sort menu button",
        subtitle = "Opens the sorting popup menu",
        icon = Icons.AutoMirrored.Rounded.Sort,
        defaultEnabled = true
    ),
    TASKS(
        id = "tasks",
        label = "Tasks shortcut button",
        subtitle = "Quick toolbar button for active and pending file tasks",
        icon = Icons.Rounded.AddTask,
        defaultEnabled = false
    ),
    BOOKMARKS(
        id = "bookmarks",
        label = "Bookmarks shortcut button",
        subtitle = "Quick toolbar button to open saved bookmarks",
        icon = Icons.Rounded.Bookmark,
        defaultEnabled = false
    ),
    VIEW_TYPE(
        id = "view_type",
        label = "View type shortcut button",
        subtitle = "Quick toolbar button to switch between Grid and List views",
        icon = Icons.Rounded.ViewComfy,
        defaultEnabled = false
    ),
    MORE_OPTIONS(
        id = "more_options",
        label = "Overflow menu (⋮) button",
        subtitle = "Opens the top toolbar overflow menu",
        icon = Icons.Rounded.MoreVert,
        defaultEnabled = true
    );

    companion object {
        fun fromId(id: String): TopToolbarButtonItem? = entries.find { it.id == id }

        fun defaultConfigs(): List<TopToolbarItemConfig> = entries.map {
            TopToolbarItemConfig(id = it.id, isEnabled = it.defaultEnabled)
        }
    }
}

enum class TopToolbarOverflowItem(
    val id: String,
    val label: String,
    val icon: ImageVector,
    val defaultEnabled: Boolean = true
) {
    DRAWER_MENU(
        id = "drawer_menu",
        label = "Navigation drawer (Sidebar)",
        icon = Icons.Rounded.Menu,
        defaultEnabled = false
    ),
    FORWARD(
        id = "forward",
        label = "Forward navigation",
        icon = Icons.AutoMirrored.Rounded.ArrowForward,
        defaultEnabled = true
    ),
    SELECT_ALL(
        id = "select_all",
        label = "Select all / Deselect all",
        icon = Icons.Rounded.SelectAll,
        defaultEnabled = true
    ),
    EMPTY_RECYCLE_BIN(
        id = "empty_recycle_bin",
        label = "Empty Recycle Bin",
        icon = Icons.Rounded.DeleteSweep,
        defaultEnabled = true
    ),
    PASTE_HERE(
        id = "paste_here",
        label = "Paste / Move here",
        icon = Icons.Rounded.ContentPaste,
        defaultEnabled = true
    ),
    CREATE_NEW(
        id = "create_new",
        label = "Create new file or folder",
        icon = Icons.Rounded.Add,
        defaultEnabled = true
    ),
    TASKS(
        id = "tasks",
        label = "Tasks",
        icon = Icons.Rounded.AddTask,
        defaultEnabled = true
    ),
    BOOKMARKS(
        id = "bookmarks",
        label = "Bookmarks",
        icon = Icons.Rounded.Bookmark,
        defaultEnabled = true
    ),
    SHOW_HIDDEN_FILES(
        id = "show_hidden_files",
        label = "Show hidden files",
        icon = Icons.Rounded.RemoveRedEye,
        defaultEnabled = true
    ),
    VIEW_TYPE(
        id = "view_type",
        label = "View type",
        icon = Icons.Rounded.ViewComfy,
        defaultEnabled = true
    ),
    CUSTOMIZE_HOME_TAB(
        id = "customize_home_tab",
        label = "Customize Home tab",
        icon = Icons.Rounded.EditAttributes,
        defaultEnabled = true
    ),
    MANAGE_STARTUP_TABS(
        id = "manage_startup_tabs",
        label = "Manage startup tabs",
        icon = Icons.Rounded.AutoAwesomeMotion,
        defaultEnabled = true
    ),
    PREFERENCES(
        id = "preferences",
        label = "Preferences",
        icon = Icons.Rounded.Settings,
        defaultEnabled = true
    ),
    ABOUT(
        id = "about",
        label = "About",
        icon = Icons.Rounded.Info,
        defaultEnabled = true
    );

    companion object {
        fun fromId(id: String): TopToolbarOverflowItem? = entries.find { it.id == id }

        fun defaultConfigs(): List<TopToolbarItemConfig> = entries.map {
            TopToolbarItemConfig(id = it.id, isEnabled = it.defaultEnabled)
        }
    }
}

object TopToolbarConfigUtils {
    private val gson = Gson()
    private val listType = object : TypeToken<List<TopToolbarItemConfig>>() {}.type

    fun parseButtonConfigs(json: String): List<TopToolbarItemConfig> {
        val defaults = TopToolbarButtonItem.defaultConfigs()
        if (json.isBlank()) return defaults
        return try {
            val parsed: List<TopToolbarItemConfig>? = gson.fromJson(json, listType)
            if (parsed.isNullOrEmpty()) {
                defaults
            } else {
                val validIds = TopToolbarButtonItem.entries.map { it.id }.toSet()
                val filtered = parsed.filter { it.id in validIds }
                val existingIds = filtered.map { it.id }.toSet()
                val missing = defaults.filter { it.id !in existingIds }
                filtered + missing
            }
        } catch (_: Exception) {
            defaults
        }
    }

    fun parseOverflowConfigs(json: String): List<TopToolbarItemConfig> {
        val defaults = TopToolbarOverflowItem.defaultConfigs()
        if (json.isBlank()) return defaults
        return try {
            val parsed: List<TopToolbarItemConfig>? = gson.fromJson(json, listType)
            if (parsed.isNullOrEmpty()) {
                defaults
            } else {
                val validIds = TopToolbarOverflowItem.entries.map { it.id }.toSet()
                val filtered = parsed.filter { it.id in validIds }
                val existingIds = filtered.map { it.id }.toSet()
                val missing = defaults.filter { it.id !in existingIds }
                filtered + missing
            }
        } catch (_: Exception) {
            defaults
        }
    }

    fun serialize(configs: List<TopToolbarItemConfig>): String = gson.toJson(configs)
}

@Composable
fun TopToolbarSettingsDialog(
    onDismiss: () -> Unit
) {
    val prefs = globalClass.preferencesManager
    var selectedSection by remember { mutableIntStateOf(0) } // 0 = Toolbar Buttons, 1 = Overflow Menu

    val buttonConfigs = remember {
        mutableStateListOf<TopToolbarItemConfig>().apply {
            addAll(TopToolbarConfigUtils.parseButtonConfigs(prefs.topToolbarButtonsConfig))
        }
    }

    val overflowConfigs = remember {
        mutableStateListOf<TopToolbarItemConfig>().apply {
            addAll(TopToolbarConfigUtils.parseOverflowConfigs(prefs.topToolbarOverflowConfig))
        }
    }

    fun saveButtonConfigs() {
        prefs.topToolbarButtonsConfig = TopToolbarConfigUtils.serialize(buttonConfigs.toList())
    }

    fun saveOverflowConfigs() {
        prefs.topToolbarOverflowConfig = TopToolbarConfigUtils.serialize(overflowConfigs.toList())
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Rounded.Tune,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text(
                text = "Customize Top Toolbar",
                style = MaterialTheme.typography.titleMedium
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Section selector chips: Toolbar Buttons vs Overflow Menu
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = selectedSection == 0,
                        onClick = { selectedSection = 0 },
                        label = { Text("Toolbar Buttons") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Rounded.Menu,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = selectedSection == 1,
                        onClick = { selectedSection = 1 },
                        label = { Text("Menu (⋮) Items") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Rounded.MoreVert,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        modifier = Modifier.weight(1f)
                    )
                }

                Space(8.dp)

                if (selectedSection == 0) {
                    // Subtitle / folder & file counts toggle
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            .clickable { prefs.showToolbarSubtitle = !prefs.showToolbarSubtitle }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Subtitles,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Space(8.dp)
                            Column {
                                Text(
                                    text = "Show folder & file counts subtitle",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Display folder/file counts and selection size under the title",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Space(8.dp)
                        Switch(
                            checked = prefs.showToolbarSubtitle,
                            onCheckedChange = { prefs.showToolbarSubtitle = it }
                        )
                    }

                    Space(8.dp)

                    val listState = rememberLazyListState()
                    val reorderableState = rememberReorderableLazyListState(listState) { from, to ->
                        buttonConfigs.add(to.index, buttonConfigs.removeAt(from.index))
                        saveButtonConfigs()
                    }

                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 320.dp)
                    ) {
                        itemsIndexed(buttonConfigs, key = { _, item -> item.id }) { index, item ->
                            val buttonMeta = TopToolbarButtonItem.fromId(item.id)
                            if (buttonMeta != null) {
                                ReorderableItem(reorderableState, key = item.id) { isDragging ->
                                    val bgAnim by animateColorAsState(
                                        targetValue = if (isDragging) {
                                            MaterialTheme.colorScheme.surfaceContainerHighest
                                        } else {
                                            MaterialTheme.colorScheme.surface
                                        },
                                        label = "toolbarBtnDragBg"
                                    )
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 2.dp)
                                            .background(bgAnim, RoundedCornerShape(8.dp))
                                            .padding(horizontal = 4.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        IconButton(
                                            modifier = Modifier.longPressDraggableHandle(),
                                            onClick = {}
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.DragHandle,
                                                contentDescription = "Reorder",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Icon(
                                            imageVector = buttonMeta.icon,
                                            contentDescription = null,
                                            modifier = Modifier.size(20.dp),
                                            tint = if (item.isEnabled) {
                                                MaterialTheme.colorScheme.primary
                                            } else {
                                                MaterialTheme.colorScheme.outline
                                            }
                                        )
                                        Space(8.dp)
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = buttonMeta.label,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Medium,
                                                color = if (item.isEnabled) {
                                                    MaterialTheme.colorScheme.onSurface
                                                } else {
                                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                                }
                                            )
                                            Text(
                                                text = buttonMeta.subtitle,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                                    alpha = if (item.isEnabled) 0.85f else 0.45f
                                                )
                                            )
                                        }
                                        Switch(
                                            checked = item.isEnabled,
                                            onCheckedChange = { checked ->
                                                buttonConfigs[index] = item.copy(isEnabled = checked)
                                                saveButtonConfigs()
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
                    val overflowListState = rememberLazyListState()
                    val reorderableOverflowState = rememberReorderableLazyListState(overflowListState) { from, to ->
                        overflowConfigs.add(to.index, overflowConfigs.removeAt(from.index))
                        saveOverflowConfigs()
                    }

                    LazyColumn(
                        state = overflowListState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 360.dp)
                    ) {
                        itemsIndexed(overflowConfigs, key = { _, item -> item.id }) { index, item ->
                            val overflowMeta = TopToolbarOverflowItem.fromId(item.id)
                            if (overflowMeta != null) {
                                ReorderableItem(reorderableOverflowState, key = item.id) { isDragging ->
                                    val bgAnim by animateColorAsState(
                                        targetValue = if (isDragging) {
                                            MaterialTheme.colorScheme.surfaceContainerHighest
                                        } else {
                                            MaterialTheme.colorScheme.surface
                                        },
                                        label = "toolbarOverflowDragBg"
                                    )
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 2.dp)
                                            .background(bgAnim, RoundedCornerShape(8.dp))
                                            .padding(horizontal = 4.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        IconButton(
                                            modifier = Modifier.longPressDraggableHandle(),
                                            onClick = {}
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.DragHandle,
                                                contentDescription = "Reorder",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Icon(
                                            imageVector = overflowMeta.icon,
                                            contentDescription = null,
                                            modifier = Modifier.size(20.dp),
                                            tint = if (item.isEnabled) {
                                                MaterialTheme.colorScheme.primary
                                            } else {
                                                MaterialTheme.colorScheme.outline
                                            }
                                        )
                                        Space(8.dp)
                                        Text(
                                            text = overflowMeta.label,
                                            modifier = Modifier.weight(1f),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = if (item.isEnabled) {
                                                MaterialTheme.colorScheme.onSurface
                                            } else {
                                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                            }
                                        )
                                        Switch(
                                            checked = item.isEnabled,
                                            onCheckedChange = { checked ->
                                                overflowConfigs[index] = item.copy(isEnabled = checked)
                                                saveOverflowConfigs()
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (selectedSection == 0) {
                        prefs.showToolbarSubtitle = true
                        buttonConfigs.clear()
                        buttonConfigs.addAll(TopToolbarButtonItem.defaultConfigs())
                        saveButtonConfigs()
                    } else {
                        overflowConfigs.clear()
                        overflowConfigs.addAll(TopToolbarOverflowItem.defaultConfigs())
                        saveOverflowConfigs()
                    }
                }
            ) {
                Icon(
                    imageVector = Icons.Rounded.RestartAlt,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Space(4.dp)
                Text(stringResource(R.string.reset))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.close))
            }
        }
    )
}
