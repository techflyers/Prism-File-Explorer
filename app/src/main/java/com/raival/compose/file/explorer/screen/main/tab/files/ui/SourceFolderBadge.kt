package com.raival.compose.file.explorer.screen.main.tab.files.ui

import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.raival.compose.file.explorer.screen.main.tab.files.misc.SourceFolderInfo

/**
 * Renders a circular badge indicating the source app or folder of a file.
 */
@Composable
fun SourceFolderBadge(
    sourceInfo: SourceFolderInfo,
    modifier: Modifier = Modifier,
    badgeSize: Dp = 16.dp,
    iconSize: Dp = 12.dp
) {
    Box(
        modifier = modifier
            .size(badgeSize)
            .shadow(1.dp, CircleShape)
            .background(
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                shape = CircleShape
            )
            .border(
                width = 0.75.dp,
                color = MaterialTheme.colorScheme.surface,
                shape = CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        when (val icon = sourceInfo.icon) {
            is Bitmap -> {
                AsyncImage(
                    model = icon,
                    contentDescription = null,
                    modifier = Modifier
                        .size(iconSize)
                        .clip(CircleShape),
                    filterQuality = FilterQuality.Low,
                    contentScale = ContentScale.Fit
                )
            }
            is Drawable -> {
                AsyncImage(
                    model = icon,
                    contentDescription = null,
                    modifier = Modifier
                        .size(iconSize)
                        .clip(CircleShape),
                    filterQuality = FilterQuality.Low,
                    contentScale = ContentScale.Fit
                )
            }
            is ImageVector -> {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(iconSize),
                    tint = if (sourceInfo.isApp) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            is Int -> {
                Icon(
                    painter = painterResource(id = icon),
                    contentDescription = null,
                    modifier = Modifier.size(iconSize),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

/**
 * Renders just the source folder or app icon (e.g. for use in category tabs or subtitles).
 */
@Composable
fun SourceFolderIcon(
    sourceInfo: SourceFolderInfo,
    modifier: Modifier = Modifier,
    size: Dp = 16.dp
) {
    when (val icon = sourceInfo.icon) {
        is Bitmap -> {
            AsyncImage(
                model = icon,
                contentDescription = null,
                modifier = modifier
                    .size(size)
                    .clip(CircleShape),
                filterQuality = FilterQuality.Low,
                contentScale = ContentScale.Fit
            )
        }
        is Drawable -> {
            AsyncImage(
                model = icon,
                contentDescription = null,
                modifier = modifier
                    .size(size)
                    .clip(CircleShape),
                filterQuality = FilterQuality.Low,
                contentScale = ContentScale.Fit
            )
        }
        is ImageVector -> {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = modifier.size(size),
                tint = if (sourceInfo.isApp) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        is Int -> {
            Icon(
                painter = painterResource(id = icon),
                contentDescription = null,
                modifier = modifier.size(size),
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}
