package com.techflyers.compose.file.explorer.screen.main.tab.files.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Shortcut
import androidx.compose.material.icons.rounded.LinkOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Renders a circular badge indicating that a file is a symbolic link,
 * displaying a shortcut arrow for valid links or a broken link icon for broken targets.
 */
@Composable
fun SymbolicLinkBadge(
    isBroken: Boolean,
    modifier: Modifier = Modifier,
    badgeSize: Dp = 16.dp,
    iconSize: Dp = 11.dp
) {
    Box(
        modifier = modifier
            .size(badgeSize)
            .shadow(1.dp, CircleShape)
            .background(
                color = if (isBroken) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
                shape = CircleShape
            )
            .border(
                width = 0.75.dp,
                color = if (isBroken) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.surface,
                shape = CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = if (isBroken) Icons.Rounded.LinkOff else Icons.AutoMirrored.Rounded.Shortcut,
            contentDescription = if (isBroken) "Broken symbolic link" else "Symbolic link",
            tint = if (isBroken) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(iconSize)
        )
    }
}
