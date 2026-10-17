package com.techflyers.compose.file.explorer.screen.main.tab.files.ui.dialog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.techflyers.compose.file.explorer.App.Companion.globalClass
import com.techflyers.compose.file.explorer.R
import com.techflyers.compose.file.explorer.common.block
import com.techflyers.compose.file.explorer.common.ui.CheckableText
import com.techflyers.compose.file.explorer.screen.main.tab.files.task.TaskContentStatus

@Composable
fun TaskConflictDialog() {
    val interceptor = globalClass.taskManager.taskInterceptor
    var applyToOtherConflicts by remember(interceptor.taskContentItem) { mutableStateOf(false) }

    if (interceptor.hasConflict) {
        val isFolderConflict = interceptor.isFolderConflict
        val folderName = interceptor.rootFolderName
        val buttonShape = RoundedCornerShape(8.dp)
        val rowPadding = PaddingValues(horizontal = 6.dp, vertical = 10.dp)
        val fullWidthPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)

        Dialog(
            onDismissRequest = { interceptor.cancel() },
            properties = DialogProperties(
                dismissOnBackPress = true,
                dismissOnClickOutside = false
            )
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .block()
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    modifier = Modifier.fillMaxWidth(),
                    text = globalClass.getString(R.string.conflict),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    modifier = Modifier.fillMaxWidth(),
                    text = interceptor.message,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (isFolderConflict && !folderName.isNullOrBlank()) {
                    Text(
                        modifier = Modifier.fillMaxWidth(),
                        text = "• Duplicate: keep both files inside \"$folderName\"\n• Duplicate Folder: copy into \"$folderName (copy)\"",
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )
                }
                CheckableText(
                    checked = applyToOtherConflicts,
                    onCheckedChange = { applyToOtherConflicts = it }
                ) {
                    Text(text = globalClass.getString(R.string.apply_to_other_conflicts))
                }

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Row 1: Duplicate, Skip, Overwrite in the same row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Button(
                            modifier = Modifier.weight(1f),
                            shape = buttonShape,
                            contentPadding = rowPadding,
                            onClick = {
                                interceptor.resolve(TaskContentStatus.AUTO_RENAME, applyToOtherConflicts)
                            }
                        ) {
                            Text(
                                text = "Duplicate",
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        OutlinedButton(
                            modifier = Modifier.weight(1f),
                            shape = buttonShape,
                            contentPadding = rowPadding,
                            onClick = {
                                interceptor.resolve(TaskContentStatus.SKIP, applyToOtherConflicts)
                            }
                        ) {
                            Text(
                                text = globalClass.getString(R.string.skip),
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        OutlinedButton(
                            modifier = Modifier.weight(1f),
                            shape = buttonShape,
                            contentPadding = rowPadding,
                            onClick = {
                                interceptor.resolve(TaskContentStatus.REPLACE, applyToOtherConflicts)
                            }
                        ) {
                            Text(
                                text = "Overwrite",
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    // Row 2 (only when copying a folder): Duplicate Folder
                    if (isFolderConflict) {
                        Button(
                            modifier = Modifier.fillMaxWidth(),
                            shape = buttonShape,
                            contentPadding = fullWidthPadding,
                            onClick = {
                                interceptor.resolve(TaskContentStatus.DUPLICATE_FOLDER, applyToOtherConflicts)
                            }
                        ) {
                            Text(
                                text = "Duplicate Folder",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    // Bottom Row: Full-width Cancel button
                    OutlinedButton(
                        modifier = Modifier.fillMaxWidth(),
                        shape = buttonShape,
                        contentPadding = fullWidthPadding,
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        ),
                        onClick = {
                            interceptor.cancel()
                        }
                    ) {
                        Text(
                            text = globalClass.getString(R.string.cancel),
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}
