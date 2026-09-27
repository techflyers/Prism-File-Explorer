package com.techflyers.compose.file.explorer.screen.main.tab.files.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Done
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.techflyers.compose.file.explorer.R
import com.techflyers.compose.file.explorer.screen.main.tab.files.FilesTab
import com.techflyers.compose.file.explorer.screen.main.tab.files.holder.VirtualFileHolder

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FormatRibbon(tab: FilesTab, compact: Boolean = false) {
    val folder = tab.activeFolder as? VirtualFileHolder ?: return
    if (folder.type !in setOf(
            VirtualFileHolder.IMAGE,
            VirtualFileHolder.VIDEO,
            VirtualFileHolder.AUDIO,
            VirtualFileHolder.DOCUMENT,
            VirtualFileHolder.ARCHIVE,
            VirtualFileHolder.APK
        )
    ) return
    val formats = folder.availableFormats()
    if (formats.isEmpty()) return

    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = if (compact) 0.dp else 4.dp),
        contentPadding = PaddingValues(horizontal = if (compact) 4.dp else 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        item("all") {
            val allSelected = tab.selectedFormats.isEmpty()
            FilterChip(
                selected = allSelected,
                onClick = {
                    tab.selectedFormats = emptySet()
                    (tab.activeFolder as? VirtualFileHolder)?.selectedFormats = emptySet()
                    tab.reloadFiles()
                },
                label = { Text(stringResource(R.string.all), style = MaterialTheme.typography.labelMedium) },
                leadingIcon = if (allSelected) {
                    {
                        Icon(Icons.Rounded.Done, contentDescription = null, modifier = Modifier.size(16.dp))
                    }
                } else null,
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                shape = RoundedCornerShape(8.dp)
            )
        }
        items(formats, key = { it }) { ext ->
            val selected = tab.selectedFormats.contains(ext)
            FilterChip(
                selected = selected,
                onClick = {
                    val next = tab.selectedFormats.toMutableSet()
                    if (selected) next.remove(ext) else next.add(ext)
                    tab.selectedFormats = next
                    (tab.activeFolder as? VirtualFileHolder)?.selectedFormats = next
                    tab.reloadFiles()
                },
                label = {
                    Text(ext.uppercase(), style = MaterialTheme.typography.labelMedium)
                },
                leadingIcon = if (selected) {
                    {
                        Icon(Icons.Rounded.Done, contentDescription = null, modifier = Modifier.size(16.dp))
                    }
                } else null,
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                shape = RoundedCornerShape(8.dp)
            )
        }
    }
}
