package com.techflyers.compose.file.explorer.common.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val PrismIcons.SortNameAscending: ImageVector by lazy {
    ImageVector.Builder(
        name = "SortNameAscending",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        // "A" (left) and "Z" (right)
        path(
            fill = SolidColor(Color.White),
            pathFillType = PathFillType.EvenOdd
        ) {
            // Letter 'A' outer
            moveTo(7.0f, 4.5f)
            lineTo(2.5f, 19.5f)
            lineTo(5.1f, 19.5f)
            lineTo(6.2f, 15.6f)
            lineTo(7.8f, 15.6f)
            lineTo(8.9f, 19.5f)
            lineTo(11.5f, 19.5f)
            close()
            // Letter 'A' inner cutout
            moveTo(7.0f, 8.0f)
            lineTo(8.2f, 13.0f)
            lineTo(5.8f, 13.0f)
            close()

            // Letter 'Z'
            moveTo(12.5f, 5.0f)
            lineTo(21.5f, 5.0f)
            lineTo(21.5f, 7.3f)
            lineTo(16.0f, 17.2f)
            lineTo(21.5f, 17.2f)
            lineTo(21.5f, 19.5f)
            lineTo(12.5f, 19.5f)
            lineTo(12.5f, 17.2f)
            lineTo(18.0f, 7.3f)
            lineTo(12.5f, 7.3f)
            close()
        }
    }.build()
}

val PrismIcons.SortNameDescending: ImageVector by lazy {
    ImageVector.Builder(
        name = "SortNameDescending",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        // "Z" (left) and "A" (right)
        path(
            fill = SolidColor(Color.White),
            pathFillType = PathFillType.EvenOdd
        ) {
            // Letter 'Z'
            moveTo(2.5f, 5.0f)
            lineTo(11.5f, 5.0f)
            lineTo(11.5f, 7.3f)
            lineTo(6.0f, 17.2f)
            lineTo(11.5f, 17.2f)
            lineTo(11.5f, 19.5f)
            lineTo(2.5f, 19.5f)
            lineTo(2.5f, 17.2f)
            lineTo(8.0f, 7.3f)
            lineTo(2.5f, 7.3f)
            close()

            // Letter 'A' outer
            moveTo(17.0f, 4.5f)
            lineTo(12.5f, 19.5f)
            lineTo(15.1f, 19.5f)
            lineTo(16.2f, 15.6f)
            lineTo(17.8f, 15.6f)
            lineTo(18.9f, 19.5f)
            lineTo(21.5f, 19.5f)
            close()
            // Letter 'A' inner cutout
            moveTo(17.0f, 8.0f)
            lineTo(18.2f, 13.0f)
            lineTo(15.8f, 13.0f)
            close()
        }
    }.build()
}

val PrismIcons.SortSizeSmaller: ImageVector by lazy {
    ImageVector.Builder(
        name = "SortSizeSmaller",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        // 3 horizontal bars: short, medium, long (left-aligned)
        path(fill = SolidColor(Color.White)) {
            // Top bar (short)
            moveTo(4.5f, 6.0f)
            lineTo(9.5f, 6.0f)
            curveTo(10.3f, 6.0f, 11.0f, 6.7f, 11.0f, 7.5f)
            curveTo(11.0f, 8.3f, 10.3f, 9.0f, 9.5f, 9.0f)
            lineTo(4.5f, 9.0f)
            curveTo(3.7f, 9.0f, 3.0f, 8.3f, 3.0f, 7.5f)
            curveTo(3.0f, 6.7f, 3.7f, 6.0f, 4.5f, 6.0f)
            close()

            // Middle bar (medium)
            moveTo(4.5f, 11.0f)
            lineTo(14.5f, 11.0f)
            curveTo(15.3f, 11.0f, 16.0f, 11.7f, 16.0f, 12.5f)
            curveTo(16.0f, 13.3f, 15.3f, 14.0f, 14.5f, 14.0f)
            lineTo(4.5f, 14.0f)
            curveTo(3.7f, 14.0f, 3.0f, 13.3f, 3.0f, 12.5f)
            curveTo(3.0f, 11.7f, 3.7f, 11.0f, 4.5f, 11.0f)
            close()

            // Bottom bar (long)
            moveTo(4.5f, 16.0f)
            lineTo(19.5f, 16.0f)
            curveTo(20.3f, 16.0f, 21.0f, 16.7f, 21.0f, 17.5f)
            curveTo(21.0f, 18.3f, 20.3f, 19.0f, 19.5f, 19.0f)
            lineTo(4.5f, 19.0f)
            curveTo(3.7f, 19.0f, 3.0f, 18.3f, 3.0f, 17.5f)
            curveTo(3.0f, 16.7f, 3.7f, 16.0f, 4.5f, 16.0f)
            close()
        }
    }.build()
}

val PrismIcons.SortSizeLarger: ImageVector by lazy {
    ImageVector.Builder(
        name = "SortSizeLarger",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        // 3 horizontal bars: long, medium, short (left-aligned)
        path(fill = SolidColor(Color.White)) {
            // Top bar (long)
            moveTo(4.5f, 6.0f)
            lineTo(19.5f, 6.0f)
            curveTo(20.3f, 6.0f, 21.0f, 6.7f, 21.0f, 7.5f)
            curveTo(21.0f, 8.3f, 20.3f, 9.0f, 19.5f, 9.0f)
            lineTo(4.5f, 9.0f)
            curveTo(3.7f, 9.0f, 3.0f, 8.3f, 3.0f, 7.5f)
            curveTo(3.0f, 6.7f, 3.7f, 6.0f, 4.5f, 6.0f)
            close()

            // Middle bar (medium)
            moveTo(4.5f, 11.0f)
            lineTo(14.5f, 11.0f)
            curveTo(15.3f, 11.0f, 16.0f, 11.7f, 16.0f, 12.5f)
            curveTo(16.0f, 13.3f, 15.3f, 14.0f, 14.5f, 14.0f)
            lineTo(4.5f, 14.0f)
            curveTo(3.7f, 14.0f, 3.0f, 13.3f, 3.0f, 12.5f)
            curveTo(3.0f, 11.7f, 3.7f, 11.0f, 4.5f, 11.0f)
            close()

            // Bottom bar (short)
            moveTo(4.5f, 16.0f)
            lineTo(9.5f, 16.0f)
            curveTo(10.3f, 16.0f, 11.0f, 16.7f, 11.0f, 17.5f)
            curveTo(11.0f, 18.3f, 10.3f, 19.0f, 9.5f, 19.0f)
            lineTo(4.5f, 19.0f)
            curveTo(3.7f, 19.0f, 3.0f, 18.3f, 3.0f, 17.5f)
            curveTo(3.0f, 16.7f, 3.7f, 16.0f, 4.5f, 16.0f)
            close()
        }
    }.build()
}

val PrismIcons.SortTypeAscending: ImageVector by lazy {
    ImageVector.Builder(
        name = "SortTypeAscending",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        // Puzzle piece
        path(fill = SolidColor(Color.White)) {
            moveTo(15.0f, 10.5f)
            lineTo(14.0f, 10.5f)
            lineTo(14.0f, 7.5f)
            curveTo(14.0f, 6.7f, 13.3f, 6.0f, 12.5f, 6.0f)
            lineTo(9.5f, 6.0f)
            lineTo(9.5f, 4.8f)
            curveTo(9.5f, 3.8f, 8.7f, 3.0f, 7.7f, 3.0f)
            curveTo(6.7f, 3.0f, 5.9f, 3.8f, 5.9f, 4.8f)
            lineTo(5.9f, 6.0f)
            lineTo(3.5f, 6.0f)
            curveTo(2.7f, 6.0f, 2.0f, 6.7f, 2.0f, 7.5f)
            lineTo(2.0f, 10.3f)
            lineTo(3.2f, 10.3f)
            curveTo(4.2f, 10.3f, 5.0f, 11.1f, 5.0f, 12.1f)
            curveTo(5.0f, 13.1f, 4.2f, 13.9f, 3.2f, 13.9f)
            lineTo(2.0f, 13.9f)
            lineTo(2.0f, 19.5f)
            curveTo(2.0f, 20.3f, 2.7f, 21.0f, 3.5f, 21.0f)
            lineTo(6.5f, 21.0f)
            lineTo(6.5f, 19.8f)
            curveTo(6.5f, 18.8f, 7.3f, 18.0f, 8.3f, 18.0f)
            curveTo(9.3f, 18.0f, 10.1f, 18.8f, 10.1f, 19.8f)
            lineTo(10.1f, 21.0f)
            lineTo(12.5f, 21.0f)
            curveTo(13.3f, 21.0f, 14.0f, 20.3f, 14.0f, 19.5f)
            lineTo(14.0f, 16.5f)
            lineTo(15.0f, 16.5f)
            curveTo(16.0f, 16.5f, 16.8f, 15.7f, 16.8f, 14.7f)
            curveTo(16.8f, 13.7f, 16.0f, 12.9f, 15.0f, 12.9f)
            lineTo(14.0f, 12.9f)
            lineTo(14.0f, 10.5f)
            close()
        }
        // Downward indicator arrow at top-right
        path(fill = SolidColor(Color.White)) {
            moveTo(16.5f, 4.0f)
            lineTo(22.5f, 4.0f)
            lineTo(19.5f, 8.5f)
            close()
        }
    }.build()
}

val PrismIcons.SortTypeDescending: ImageVector by lazy {
    ImageVector.Builder(
        name = "SortTypeDescending",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        // Puzzle piece
        path(fill = SolidColor(Color.White)) {
            moveTo(15.0f, 10.5f)
            lineTo(14.0f, 10.5f)
            lineTo(14.0f, 7.5f)
            curveTo(14.0f, 6.7f, 13.3f, 6.0f, 12.5f, 6.0f)
            lineTo(9.5f, 6.0f)
            lineTo(9.5f, 4.8f)
            curveTo(9.5f, 3.8f, 8.7f, 3.0f, 7.7f, 3.0f)
            curveTo(6.7f, 3.0f, 5.9f, 3.8f, 5.9f, 4.8f)
            lineTo(5.9f, 6.0f)
            lineTo(3.5f, 6.0f)
            curveTo(2.7f, 6.0f, 2.0f, 6.7f, 2.0f, 7.5f)
            lineTo(2.0f, 10.3f)
            lineTo(3.2f, 10.3f)
            curveTo(4.2f, 10.3f, 5.0f, 11.1f, 5.0f, 12.1f)
            curveTo(5.0f, 13.1f, 4.2f, 13.9f, 3.2f, 13.9f)
            lineTo(2.0f, 13.9f)
            lineTo(2.0f, 19.5f)
            curveTo(2.0f, 20.3f, 2.7f, 21.0f, 3.5f, 21.0f)
            lineTo(6.5f, 21.0f)
            lineTo(6.5f, 19.8f)
            curveTo(6.5f, 18.8f, 7.3f, 18.0f, 8.3f, 18.0f)
            curveTo(9.3f, 18.0f, 10.1f, 18.8f, 10.1f, 19.8f)
            lineTo(10.1f, 21.0f)
            lineTo(12.5f, 21.0f)
            curveTo(13.3f, 21.0f, 14.0f, 20.3f, 14.0f, 19.5f)
            lineTo(14.0f, 16.5f)
            lineTo(15.0f, 16.5f)
            curveTo(16.0f, 16.5f, 16.8f, 15.7f, 16.8f, 14.7f)
            curveTo(16.8f, 13.7f, 16.0f, 12.9f, 15.0f, 12.9f)
            lineTo(14.0f, 12.9f)
            lineTo(14.0f, 10.5f)
            close()
        }
        // Upward indicator arrow at top-right
        path(fill = SolidColor(Color.White)) {
            moveTo(19.5f, 4.0f)
            lineTo(16.5f, 8.5f)
            lineTo(22.5f, 8.5f)
            close()
        }
    }.build()
}
