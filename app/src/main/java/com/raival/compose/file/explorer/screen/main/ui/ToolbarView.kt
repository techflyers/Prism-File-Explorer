package com.raival.compose.file.explorer.screen.main.ui

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesomeMotion
import androidx.compose.material.icons.rounded.CheckBox
import androidx.compose.material.icons.rounded.CheckBoxOutlineBlank
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.EditAttributes
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.RemoveRedEye
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SortByAlpha
import androidx.compose.material.icons.rounded.ViewComfy
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import com.raival.compose.file.explorer.App.Companion.globalClass
import com.raival.compose.file.explorer.R
import com.raival.compose.file.explorer.common.icons.PrismIcons
import com.raival.compose.file.explorer.common.icons.Upgrade
import com.raival.compose.file.explorer.screen.main.tab.files.FilesTab
import com.raival.compose.file.explorer.screen.main.tab.files.misc.SortingMethod
import com.raival.compose.file.explorer.screen.main.tab.home.HomeTab
import com.raival.compose.file.explorer.screen.preferences.PreferencesActivity

@Composable
fun Toolbar(
    title: String,
    subtitle: String,
    onToggleAppInfoDialog: (Boolean) -> Unit,
    hasNewUpdate: Boolean
) {
    val mainActivityManager = globalClass.mainActivityManager

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.surfaceContainer
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box {
            IconButton(
                onClick = { onToggleAppInfoDialog(true) }
            ) {
                if (hasNewUpdate) {
                    Icon(
                        imageVector = PrismIcons.Upgrade,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error
                    )
                } else {
                    Icon(imageVector = Icons.Rounded.Menu, contentDescription = null)
                }

            }
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = title,
                fontSize = 17.sp,
                maxLines = 1,
                lineHeight = 20.sp,
                overflow = TextOverflow.Ellipsis
            )
            AnimatedVisibility(visible = subtitle.isNotEmpty()) {
                Text(
                    modifier = Modifier.alpha(0.7f),
                    text = subtitle,
                    fontSize = 10.sp,
                    maxLines = 1,
                    lineHeight = 16.sp,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Quick sort flyout — only visible on Files tabs
        if (mainActivityManager.getActiveTab() is FilesTab) {
            QuickSortButton(mainActivityManager.getActiveTab() as FilesTab)
        }

        MoreOptionsButton()
    }
}

@Composable
fun MoreOptionsButton() {
    val mainActivityManager = globalClass.mainActivityManager
    val context = LocalContext.current

    var showOptionsMenu by remember { mutableStateOf(false) }

    Box {
        IconButton(
            onClick = { showOptionsMenu = true }
        ) {
            Icon(imageVector = Icons.Rounded.MoreVert, contentDescription = null)
        }

        DropdownMenu(
            expanded = showOptionsMenu,
            onDismissRequest = { showOptionsMenu = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            val state by mainActivityManager.state.collectAsState()

            if (state.hasNewUpdate && globalClass.preferencesManager.hideToolbar) {
                DropdownMenuItem(
                    text = {
                        Text(text = stringResource(R.string.new_update_available))
                    },
                    onClick = {
                        mainActivityManager.toggleAppInfoDialog(true)
                        showOptionsMenu = false
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = PrismIcons.Upgrade,
                            contentDescription = null
                        )
                    }
                )
            }

            if (mainActivityManager.getActiveTab() is FilesTab) {
                val showHiddenFiles = globalClass.preferencesManager.showHiddenFiles
                DropdownMenuItem(
                    text = {
                        Text(
                            text = stringResource(R.string.show_hidden_files)
                        )
                    },
                    onClick = {
                        globalClass.preferencesManager.showHiddenFiles =
                            !showHiddenFiles
                        (mainActivityManager.getActiveTab() as FilesTab).onTabResumed()
                        showOptionsMenu = false
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.RemoveRedEye,
                            contentDescription = null
                        )
                    },
                    trailingIcon = {
                        Icon(
                            imageVector = if (showHiddenFiles)
                                Icons.Rounded.CheckBox else Icons.Rounded.CheckBoxOutlineBlank,
                            contentDescription = null
                        )
                    }
                )

                HorizontalDivider()

                DropdownMenuItem(
                    text = {
                        Text(text = stringResource(R.string.view_type))
                    },
                    onClick = {
                        (mainActivityManager.getActiveTab() as FilesTab).toggleViewConfigDialog(
                            true
                        )
                        showOptionsMenu = false
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.ViewComfy,
                            contentDescription = null
                        )
                    }
                )
            }

            if (mainActivityManager.getActiveTab() is HomeTab) {
                DropdownMenuItem(
                    text = { Text(text = stringResource(R.string.customize_home_tab)) },
                    onClick = {
                        (mainActivityManager.getActiveTab() as HomeTab).showCustomizeHomeTabDialog =
                            true
                        showOptionsMenu = false
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.EditAttributes,
                            contentDescription = null
                        )
                    }
                )
            }

            DropdownMenuItem(
                text = { Text(text = stringResource(R.string.manage_startup_tabs)) },
                onClick = {
                    mainActivityManager.toggleStartupTabsDialog(true)
                    showOptionsMenu = false
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.AutoAwesomeMotion,
                        contentDescription = null
                    )
                }
            )

            DropdownMenuItem(
                text = { Text(text = stringResource(R.string.preferences)) },
                onClick = {
                    context.startActivity(Intent(context, PreferencesActivity::class.java))
                    showOptionsMenu = false
                },
                leadingIcon = {
                    Icon(imageVector = Icons.Rounded.Settings, contentDescription = null)
                }
            )

            if (globalClass.preferencesManager.hideToolbar) {
                DropdownMenuItem(
                    text = { Text(text = stringResource(R.string.about)) },
                    onClick = {
                        mainActivityManager.toggleAppInfoDialog(true)
                        showOptionsMenu = false
                    },
                    leadingIcon = {
                        Icon(imageVector = Icons.Rounded.Info, contentDescription = null)
                    }
                )
            }
        }
    }
}

@Composable
fun QuickSortButton(tab: FilesTab) {
    val prefs = globalClass.preferencesManager
    var showMenu by remember { mutableStateOf(false) }

    val sortOptions = listOf(
        SortingMethod.SORT_BY_NAME to "Name",
        SortingMethod.SORT_BY_DATE to "Date",
        SortingMethod.SORT_BY_SIZE to "Size",
        SortingMethod.SORT_BY_TYPE to "Type"
    )

    Box {
        IconButton(onClick = { showMenu = true }) {
            Icon(imageVector = Icons.Rounded.SortByAlpha, contentDescription = "Quick sort")
        }

        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            sortOptions.forEach { (method, label) ->
                val isCurrent = prefs.defaultSortMethod == method
                DropdownMenuItem(
                    text = { Text(text = label) },
                    onClick = {
                        prefs.defaultSortMethod = method
                        // Reload the current folder with the new sort order
                        tab.openFolder(tab.activeFolder, rememberListState = false)
                        showMenu = false
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.SortByAlpha,
                            contentDescription = null
                        )
                    },
                    trailingIcon = {
                        if (isCurrent) {
                            Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = null
                            )
                        }
                    }
                )
            }
        }
    }
}