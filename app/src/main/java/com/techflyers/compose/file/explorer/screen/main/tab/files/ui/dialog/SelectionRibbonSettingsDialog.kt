package com.techflyers.compose.file.explorer.screen.main.tab.files.ui.dialog

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.ViewStream
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.techflyers.compose.file.explorer.App.Companion.globalClass
import com.techflyers.compose.file.explorer.R
import com.techflyers.compose.file.explorer.common.ui.Space
import com.techflyers.compose.file.explorer.screen.main.tab.files.ui.BottomBarActionConfig
import com.techflyers.compose.file.explorer.screen.main.tab.files.ui.BottomBarConfigUtils
import com.techflyers.compose.file.explorer.screen.main.tab.files.ui.BottomBarSelectionAction
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@Composable
fun SelectionRibbonSettingsDialog(
    onDismiss: () -> Unit
) {
    val prefs = globalClass.preferencesManager

    val selectionActions = remember {
        mutableStateListOf<BottomBarActionConfig>().apply {
            addAll(BottomBarConfigUtils.parseSelectionActions(prefs.selectionRibbonActions))
        }
    }

    fun saveSelectionActions() {
        prefs.selectionRibbonActions = BottomBarConfigUtils.serialize(selectionActions.toList())
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Rounded.ViewStream,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text(
                text = stringResource(R.string.customize_selection_ribbon_title),
                style = MaterialTheme.typography.titleMedium
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Two-row toggle option
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
                            text = stringResource(R.string.selection_ribbon_two_rows),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = stringResource(R.string.selection_ribbon_two_rows_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Space(8.dp)
                    Switch(
                        checked = prefs.selectionRibbonTwoRows,
                        onCheckedChange = { prefs.selectionRibbonTwoRows = it }
                    )
                }

                Space(12.dp)

                val listState = rememberLazyListState()
                val reorderableState = rememberReorderableLazyListState(listState) { from, to ->
                    selectionActions.add(to.index, selectionActions.removeAt(from.index))
                    saveSelectionActions()
                }

                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp)
                ) {
                    itemsIndexed(selectionActions, key = { _, item -> item.id }) { index, item ->
                        val actionEnum = BottomBarSelectionAction.fromId(item.id)
                        if (actionEnum != null) {
                            ReorderableItem(reorderableState, key = item.id) { isDragging ->
                                val bgAnim by animateColorAsState(
                                    targetValue = if (isDragging) MaterialTheme.colorScheme.surfaceContainerHighest
                                    else MaterialTheme.colorScheme.surface
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
                                        imageVector = actionEnum.icon,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp),
                                        tint = if (item.isEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                                    )
                                    Space(8.dp)
                                    Text(
                                        text = stringResource(actionEnum.labelResId),
                                        modifier = Modifier.weight(1f),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = if (item.isEnabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                    )
                                    Switch(
                                        checked = item.isEnabled,
                                        onCheckedChange = { checked ->
                                            selectionActions[index] = item.copy(isEnabled = checked)
                                            saveSelectionActions()
                                        }
                                    )
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
                    selectionActions.clear()
                    selectionActions.addAll(BottomBarSelectionAction.defaultConfigs())
                    saveSelectionActions()
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
