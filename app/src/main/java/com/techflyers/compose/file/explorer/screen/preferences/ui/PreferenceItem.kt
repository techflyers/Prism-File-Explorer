package com.techflyers.compose.file.explorer.screen.preferences.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.techflyers.compose.file.explorer.common.emptyString

val LocalPreferenceSearchQuery = compositionLocalOf { "" }
val LocalContainerSearchMatch = compositionLocalOf { false }

fun matchesPreferenceQuery(query: String, vararg texts: String?): Boolean {
    val q = query.trim()
    if (q.isEmpty()) return true
    return texts.any { it?.contains(q, ignoreCase = true) == true }
}

@Composable
fun PreferenceDivider() {
    if (LocalPreferenceSearchQuery.current.isBlank()) {
        HorizontalDivider(
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            thickness = 3.dp
        )
    }
}

@Composable
fun PreferenceItem(
    modifier: Modifier = Modifier,
    label: @Composable () -> Unit,
    supportingText: @Composable (() -> Unit)? = null,
    leadingContent: @Composable (() -> Unit)? = null,
    trailingContent: @Composable (() -> Unit)? = null,
) {
    Column {
        Row(
            modifier = modifier
                .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                .padding(start = 16.dp, end = 24.dp)
                .heightIn(min = if (supportingText == null) 56.dp else 72.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            leadingContent?.invoke()
            Column(
                Modifier
                    .weight(1f)
                    .padding(vertical = 8.dp)
            ) {
                label.invoke()
                supportingText?.invoke()
            }
            trailingContent?.invoke()
        }
        if (LocalPreferenceSearchQuery.current.isNotBlank()) {
            HorizontalDivider(
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                thickness = 2.dp
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PreferenceItem(
    modifier: Modifier = Modifier,
    label: String,
    supportingText: String,
    icon: ImageVector,
    enabled: Boolean = true,
    onClick: () -> Unit = {},
    onLongClick: () -> Unit = {},
    trailingContent: @Composable (() -> Unit)? = null
) {
    val query = LocalPreferenceSearchQuery.current
    val containerMatched = LocalContainerSearchMatch.current
    if (query.isNotBlank() && !containerMatched && !matchesPreferenceQuery(query, label, supportingText)) {
        return
    }

    PreferenceItem(
        modifier = modifier.combinedClickable(
            enabled = enabled,
            onClick = onClick,
            onLongClick = onLongClick
        ),
        leadingContent = {
            Icon(
                modifier = Modifier.alpha(if (enabled) 1f else 0.38f),
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        label = {
            Text(
                modifier = Modifier.alpha(if (enabled) 1f else 0.38f),
                text = label,
                fontSize = 16.sp
            )
        },
        supportingText = if (supportingText.isEmpty()) null else {
            {
                Text(
                    modifier = Modifier.alpha(if (enabled) 0.7f else 0.38f),
                    text = supportingText,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
            }
        },
        trailingContent = trailingContent
    )
}

@Composable
fun PreferenceItem(
    modifier: Modifier = Modifier,
    label: String,
    supportingText: String? = null,
    icon: ImageVector,
    enabled: Boolean = true,
    switchState: Boolean,
    onSwitchChange: (switched: Boolean) -> Unit
) {
    val query = LocalPreferenceSearchQuery.current
    val containerMatched = LocalContainerSearchMatch.current
    if (query.isNotBlank() && !containerMatched && !matchesPreferenceQuery(query, label, supportingText)) {
        return
    }

    var switch by remember(switchState) { mutableStateOf(switchState) }

    PreferenceItem(
        label = label,
        supportingText = supportingText ?: emptyString,
        icon = icon,
        enabled = enabled,
        modifier = modifier,
        trailingContent = {
            Switch(
                checked = switch,
                enabled = enabled,
                onCheckedChange = {
                    switch = it
                    onSwitchChange(it)
                }
            )
        },
        onClick = {
            if (enabled) {
                switch = !switch
                onSwitchChange(switch)
            }
        }
    )
}
