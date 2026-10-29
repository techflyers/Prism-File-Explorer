package com.techflyers.compose.file.explorer.screen.preferences.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.techflyers.compose.file.explorer.common.block

@Composable
fun Container(
    title: String,
    icon: ImageVector? = null,
    content: @Composable () -> Unit
) {
    val searchQuery = LocalPreferenceSearchQuery.current.trim()
    val isSearching = searchQuery.isNotEmpty()
    val titleMatches = isSearching && title.contains(searchQuery, ignoreCase = true)
    var expanded by remember { mutableStateOf(true) }
    val effectiveExpanded = if (isSearching) true else expanded

    if (isSearching && !titleMatches) {
        SubcomposeLayout { constraints ->
            val bodyMeasurables = subcompose("searchBody") {
                CompositionLocalProvider(LocalContainerSearchMatch provides false) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        content()
                    }
                }
            }
            val bodyPlaceables = bodyMeasurables.map { it.measure(constraints) }
            val totalBodyHeight = bodyPlaceables.sumOf { it.height }
            if (totalBodyHeight <= 0) {
                layout(0, 0) {}
            } else {
                val cardMeasurables = subcompose("card") {
                    CompositionLocalProvider(LocalContainerSearchMatch provides false) {
                        ContainerCard(
                            title = title,
                            icon = icon,
                            expanded = true,
                            onToggleExpand = {},
                            content = content
                        )
                    }
                }
                val cardPlaceables = cardMeasurables.map { it.measure(constraints) }
                val h = cardPlaceables.sumOf { it.height }
                val w = cardPlaceables.maxOfOrNull { it.width } ?: constraints.maxWidth
                layout(w, h) {
                    var y = 0
                    cardPlaceables.forEach { p ->
                        p.placeRelative(0, y)
                        y += p.height
                    }
                }
            }
        }
        return
    }

    CompositionLocalProvider(LocalContainerSearchMatch provides titleMatches) {
        ContainerCard(
            title = title,
            icon = icon,
            expanded = effectiveExpanded,
            onToggleExpand = { if (!isSearching) expanded = !expanded },
            content = content
        )
    }
}

@Composable
private fun ContainerCard(
    title: String,
    icon: ImageVector?,
    expanded: Boolean,
    onToggleExpand: () -> Unit,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp, vertical = 4.dp)
            .block(
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                borderSize = 0.dp,
                shape = RoundedCornerShape(8.dp)
            )
            .padding(12.dp)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = null,
                    indication = null,
                    onClick = onToggleExpand
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
            }
            Text(
                modifier = Modifier.weight(1f),
                text = title,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
            IconButton(onClick = onToggleExpand) {
                Icon(
                    imageVector = if (expanded) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
                    contentDescription = null
                )
            }
        }

        AnimatedVisibility(visible = expanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .block(
                        color = MaterialTheme.colorScheme.surfaceContainerLowest,
                        borderSize = 0.dp
                    ),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                content()
            }
        }
    }
}
