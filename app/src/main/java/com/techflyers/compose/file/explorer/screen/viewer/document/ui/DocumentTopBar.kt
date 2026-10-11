package com.techflyers.compose.file.explorer.screen.viewer.document.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Print
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.rounded.Compress
import androidx.compose.material.icons.rounded.Draw
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.ViewCarousel
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun DocumentTopBar(
    title: String,
    pageCount: Int,
    currentPage: Int,
    renderedByLibreOffice: Boolean,
    isBookmarked: Boolean,
    visible: Boolean,
    hasUnsavedChanges: Boolean = false,
    onBack: () -> Unit,
    onSearchClick: () -> Unit,
    onBookmarkToggle: () -> Unit,
    onAskAiClick: () -> Unit,
    onOpenWithClick: () -> Unit,
    onShareClick: () -> Unit,
    onSaveClick: (() -> Unit)? = null,
    onAnnotateClick: (() -> Unit)? = null,
    onSignClick: (() -> Unit)? = null,
    onAddImageClick: (() -> Unit)? = null,
    onCompressClick: (() -> Unit)? = null,
    onConvertToImagesClick: (() -> Unit)? = null,
    onOrganizePagesClick: (() -> Unit)? = null,
    onWatermarkClick: (() -> Unit)? = null,
    onInfoClick: () -> Unit,
    onPrintClick: () -> Unit,
    onMenuVisibilityChange: ((Boolean) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + slideInVertically { -it },
        exit = fadeOut() + slideOutVertically { -it },
        modifier = modifier
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.94f),
            tonalElevation = 6.dp,
            shadowElevation = 8.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Back Button
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }

                // Title Pill
                Surface(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.8f)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Description,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                if (pageCount > 0) {
                                    Text(
                                        text = "Page ${currentPage + 1} of $pageCount",
                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (renderedByLibreOffice) {
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = MaterialTheme.colorScheme.primaryContainer
                                    ) {
                                        Text(
                                            text = "LibreOffice",
                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            maxLines = 1,
                                            softWrap = false,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Search Action
                IconButton(
                    onClick = onSearchClick,
                    modifier = Modifier.size(38.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Search,
                        contentDescription = "Search",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }

                // Open with Action
                IconButton(
                    onClick = onOpenWithClick,
                    modifier = Modifier.size(38.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                        contentDescription = "Open with",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }

                // Share Action
                IconButton(
                    onClick = onShareClick,
                    modifier = Modifier.size(38.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Share,
                        contentDescription = "Share",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }

                // Save button (shown directly when unsaved changes exist)
                if (hasUnsavedChanges && onSaveClick != null) {
                    IconButton(
                        onClick = onSaveClick,
                        modifier = Modifier
                            .size(38.dp)
                            .background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Save,
                            contentDescription = "Save",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                // More Menu
                Box {
                    IconButton(
                        onClick = {
                            showMenu = true
                            onMenuVisibilityChange?.invoke(true)
                        },
                        modifier = Modifier.size(38.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.MoreVert,
                            contentDescription = "More",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = {
                            showMenu = false
                            onMenuVisibilityChange?.invoke(false)
                        }
                    ) {
                        if (onSaveClick != null) {
                            DropdownMenuItem(
                                text = { Text("Save Document") },
                                leadingIcon = { Icon(Icons.Rounded.Save, null) },
                                onClick = {
                                    showMenu = false
                                    onMenuVisibilityChange?.invoke(false)
                                    onSaveClick()
                                }
                            )
                        }
                        if (onCompressClick != null) {
                            DropdownMenuItem(
                                text = { Text("Compress PDF") },
                                leadingIcon = { Icon(Icons.Rounded.Compress, null) },
                                onClick = {
                                    showMenu = false
                                    onMenuVisibilityChange?.invoke(false)
                                    onCompressClick()
                                }
                            )
                        }
                        if (onConvertToImagesClick != null) {
                            DropdownMenuItem(
                                text = { Text("Convert to Images") },
                                leadingIcon = { Icon(Icons.Rounded.Image, null) },
                                onClick = {
                                    showMenu = false
                                    onMenuVisibilityChange?.invoke(false)
                                    onConvertToImagesClick()
                                }
                            )
                        }
                        if (onOrganizePagesClick != null) {
                            DropdownMenuItem(
                                text = { Text("Organize Pages") },
                                leadingIcon = { Icon(Icons.Rounded.ViewCarousel, null) },
                                onClick = {
                                    showMenu = false
                                    onMenuVisibilityChange?.invoke(false)
                                    onOrganizePagesClick()
                                }
                            )
                        }
                        if (onWatermarkClick != null) {
                            DropdownMenuItem(
                                text = { Text("Watermark PDF") },
                                leadingIcon = { Icon(Icons.Rounded.Draw, null) },
                                onClick = {
                                    showMenu = false
                                    onMenuVisibilityChange?.invoke(false)
                                    onWatermarkClick()
                                }
                            )
                        }
                        if (onAnnotateClick != null) {
                            DropdownMenuItem(
                                text = { Text("Annotate (Draw)") },
                                leadingIcon = { Icon(Icons.Rounded.Draw, null) },
                                onClick = {
                                    showMenu = false
                                    onMenuVisibilityChange?.invoke(false)
                                    onAnnotateClick()
                                }
                            )
                        }
                        if (onSignClick != null) {
                            DropdownMenuItem(
                                text = { Text("Sign Document") },
                                leadingIcon = { Icon(Icons.Rounded.Edit, null) },
                                onClick = {
                                    showMenu = false
                                    onMenuVisibilityChange?.invoke(false)
                                    onSignClick()
                                }
                            )
                        }
                        if (onAddImageClick != null) {
                            DropdownMenuItem(
                                text = { Text("Add Image / Stamp") },
                                leadingIcon = { Icon(Icons.Rounded.Image, null) },
                                onClick = {
                                    showMenu = false
                                    onMenuVisibilityChange?.invoke(false)
                                    onAddImageClick()
                                }
                            )
                        }
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                        DropdownMenuItem(
                            text = { Text("Ask AI (ChatGPT, Grok…)") },
                            leadingIcon = { Icon(Icons.Rounded.AutoAwesome, null) },
                            onClick = {
                                showMenu = false
                                onMenuVisibilityChange?.invoke(false)
                                onAskAiClick()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(if (isBookmarked) "Remove Bookmark" else "Bookmark Page") },
                            leadingIcon = { Icon(if (isBookmarked) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder, null) },
                            onClick = {
                                showMenu = false
                                onMenuVisibilityChange?.invoke(false)
                                onBookmarkToggle()
                            }
                        )
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                        DropdownMenuItem(
                            text = { Text("Document Info") },
                            leadingIcon = { Icon(Icons.Rounded.Info, null) },
                            onClick = {
                                showMenu = false
                                onMenuVisibilityChange?.invoke(false)
                                onInfoClick()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Print") },
                            leadingIcon = { Icon(Icons.Rounded.Print, null) },
                            onClick = {
                                showMenu = false
                                onMenuVisibilityChange?.invoke(false)
                                onPrintClick()
                            }
                        )
                    }
                }
            }
        }
    }
}
