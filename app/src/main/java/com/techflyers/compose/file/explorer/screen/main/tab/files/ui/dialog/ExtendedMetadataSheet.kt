package com.techflyers.compose.file.explorer.screen.main.tab.files.ui.dialog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.techflyers.compose.file.explorer.R
import com.techflyers.compose.file.explorer.screen.main.tab.files.holder.ContentHolder
import com.techflyers.compose.file.explorer.screen.main.tab.files.provider.ExtendedMetadataProvider
import com.techflyers.compose.file.explorer.screen.main.tab.files.provider.MetadataSection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExtendedMetadataSheet(
    holder: ContentHolder?,
    onDismiss: () -> Unit
) {
    if (holder == null) {
        androidx.compose.runtime.LaunchedEffect(Unit) { onDismiss() }
        return
    }
    var sections by remember { mutableStateOf<List<MetadataSection>>(emptyList()) }
    LaunchedEffect(holder.uniquePath) {
        sections = withContext(Dispatchers.IO) { ExtendedMetadataProvider.collect(holder) }
    }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(stringResource(R.string.extended_metadata), style = MaterialTheme.typography.titleLarge)
            if (sections.isEmpty()) {
                Text(stringResource(R.string.no_extended_metadata), color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(sections) { section ->
                        Text(section.title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                        section.fields.forEach { field ->
                            PropertyRow(
                                icon = Icons.Rounded.Info,
                                label = field.label,
                                value = field.value
                            )
                        }
                    }
                }
            }
        }
    }
}
