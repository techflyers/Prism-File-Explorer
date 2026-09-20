package com.techflyers.compose.file.explorer.screen.main.tab.files.ui

import android.annotation.SuppressLint
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.techflyers.compose.file.explorer.App.Companion.globalClass
import com.techflyers.compose.file.explorer.common.emptyString
import com.techflyers.compose.file.explorer.common.ui.Space
import com.techflyers.compose.file.explorer.screen.main.tab.files.coil.canUseCoil
import com.techflyers.compose.file.explorer.screen.main.tab.files.holder.ContentHolder
import com.techflyers.compose.file.explorer.screen.main.tab.files.misc.SourceFolderResolver
import com.techflyers.compose.file.explorer.screen.preferences.constant.FileItemSizeMap.FontSize
import com.techflyers.compose.file.explorer.screen.preferences.constant.FileItemSizeMap.IconSize
import com.techflyers.compose.file.explorer.screen.preferences.constant.FileItemSizeMap.getFileListFontSize
import com.techflyers.compose.file.explorer.screen.preferences.constant.FileItemSizeMap.getFileListIconSize
import com.techflyers.compose.file.explorer.screen.preferences.constant.FileItemSizeMap.getFileListSpace

@Composable
fun FileItemRow(
    item: ContentHolder,
    fileDetails: String,
    namePrefix: String = emptyString,
    ignoreSizePreferences: Boolean = false,
    showSourceBadge: Boolean = false,
    onFileIconClick: (() -> Unit)? = null,
    onItemClick: (() -> Unit)? = null,
) {
    ItemRow(
        title = namePrefix + item.displayName,
        subtitle = fileDetails,
        icon = {
            FileIcon(
                contentHolder = item,
                ignoreSizePreferences = ignoreSizePreferences,
                showSourceBadge = showSourceBadge,
                onClickListener = onFileIconClick
            )
        },
        ignoreSizePreferences = ignoreSizePreferences,
        isFolder = item.isFolder,
        onItemClick = onItemClick
    )
}

@Composable
fun ItemRow(
    title: String,
    subtitle: String,
    icon: @Composable () -> Unit = { },
    ignoreSizePreferences: Boolean = false,
    isFolder: Boolean = false,
    onItemClick: (() -> Unit)? = null,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .then(if (onItemClick != null) Modifier.clickable { onItemClick() } else Modifier)) {

        Space(size = if (ignoreSizePreferences) 4.dp else getFileListSpace().dp)

        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            icon()

            Space(size = 8.dp)

            Column(
                Modifier.weight(1f)
            ) {
                val fontSize = if (ignoreSizePreferences) FontSize.MEDIUM else getFileListFontSize()

                MiddleEllipsisText(
                    text = title,
                    isFolder = isFolder,
                    fontSize = fontSize.sp,
                    lineHeight = (fontSize + 2).sp
                )
                if (subtitle.isNotEmpty()) {
                    FileDetailsText(
                        details = subtitle,
                        fontSize = (fontSize - 4).sp
                    )
                }
            }
        }

        Space(size = if (ignoreSizePreferences) 4.dp else getFileListSpace().dp)
    }
}


private val folderCountPattern = Regex("(\\d+)\\s+folders?")
private val fileCountPattern = Regex("(\\d+)\\s+files?")

@Composable
private fun FileDetailsText(details: String, fontSize: androidx.compose.ui.unit.TextUnit) {
    val matches = (folderCountPattern.findAll(details).map { it to "folder" } +
        fileCountPattern.findAll(details).map { it to "file" })
        .sortedBy { it.first.range.first }
        .toList()

    if (matches.isEmpty()) {
        Text(
            modifier = Modifier.alpha(0.7f),
            text = details,
            fontSize = fontSize,
            maxLines = 1,
            lineHeight = (fontSize.value + 6).sp,
            overflow = TextOverflow.Ellipsis
        )
        return
    }

    val inlineContent = mapOf(
        "folder-count-icon" to InlineTextContent(
            placeholder = Placeholder(14.sp, 14.sp, PlaceholderVerticalAlign.TextCenter)
        ) {
            Icon(Icons.Default.Folder, contentDescription = "Folders", modifier = Modifier.size(14.dp))
        },
        "file-count-icon" to InlineTextContent(
            placeholder = Placeholder(14.sp, 14.sp, PlaceholderVerticalAlign.TextCenter)
        ) {
            Icon(Icons.Default.InsertDriveFile, contentDescription = "Files", modifier = Modifier.size(14.dp))
        }
    )
    val annotated = buildAnnotatedString {
        var cursor = 0
        matches.forEach { (match, type) ->
            append(details.substring(cursor, match.range.first))
            appendInlineContent("$type-count-icon", "[$type icon]")
            append(" ${match.groupValues[1]}")
            cursor = match.range.last + 1
        }
        append(details.substring(cursor))
    }

    Text(
        modifier = Modifier.alpha(0.7f),
        text = annotated,
        inlineContent = inlineContent,
        fontSize = fontSize,
        maxLines = 1,
        lineHeight = (fontSize.value + 6).sp,
        overflow = TextOverflow.Ellipsis
    )
}

@Composable
fun FileIcon(
    contentHolder: ContentHolder,
    ignoreSizePreferences: Boolean = false,
    showSourceBadge: Boolean = false,
    onClickListener: (() -> Unit)? = null
) {
    val iconSize = if (ignoreSizePreferences) IconSize.MEDIUM else getFileListIconSize()
    val sourceInfo = if (showSourceBadge) {
        remember(contentHolder.uniquePath) {
            SourceFolderResolver.resolve(contentHolder)
        }
    } else null

    Box(
        modifier = Modifier
            .size(iconSize.dp)
            .clip(RoundedCornerShape(4.dp))
            .then(if (onClickListener != null) Modifier.clickable { onClickListener() } else Modifier)
            .graphicsLayer { alpha = if (contentHolder.isHidden()) 0.4f else 1f },
    ) {
        var useCoil by remember(contentHolder.uniquePath) {
            mutableStateOf(canUseCoil(contentHolder))
        }

        if (useCoil) {
            AsyncImage(
                modifier = Modifier.size(iconSize.dp),
                model = ImageRequest
                    .Builder(globalClass)
                    .data(contentHolder)
                    .build(),
                filterQuality = FilterQuality.Low,
                contentScale = ContentScale.Fit,
                contentDescription = null,
                onError = { useCoil = false }
            )
        } else {
            FileContentIcon(contentHolder)
        }

        if (sourceInfo != null) {
            val badgeSize = (iconSize * 0.42f).coerceIn(14f, 22f).dp
            val badgeIconSize = (badgeSize.value * 0.72f).dp
            SourceFolderBadge(
                sourceInfo = sourceInfo,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(2.dp),
                badgeSize = badgeSize,
                iconSize = badgeIconSize
            )
        }

        if (contentHolder.isSymbolicLink) {
            val badgeSize = (iconSize * 0.42f).coerceIn(14f, 22f).dp
            val badgeIconSize = (badgeSize.value * 0.72f).dp
            SymbolicLinkBadge(
                isBroken = contentHolder.isSymbolicLinkBroken,
                modifier = Modifier
                    .align(if (sourceInfo != null) Alignment.BottomStart else Alignment.BottomEnd)
                    .padding(2.dp),
                badgeSize = badgeSize,
                iconSize = badgeIconSize
            )
        }
    }
}

@SuppressLint("CheckResult", "UseCompatLoadingForDrawables")
@Composable
fun ItemRowIcon(
    icon: Any?,
    alpha: Float = 1f,
    onClickListener: (() -> Unit)? = null,
    ignoreSizePreferences: Boolean = false,
    placeholder: Int,
) {
    val iconSize = if (ignoreSizePreferences) IconSize.MEDIUM else getFileListIconSize()

    val modifier = Modifier
        .size(iconSize.dp)
        .clip(RoundedCornerShape(4.dp))
        .then(if (onClickListener != null) Modifier.clickable { onClickListener() } else Modifier)

    AsyncImage(
        modifier = modifier,
        model = ImageRequest.Builder(globalClass).data(icon).build(),
        filterQuality = FilterQuality.Low,
        error = painterResource(id = placeholder),
        contentScale = ContentScale.Fit,
        alpha = alpha,
        contentDescription = null
    )
}