package com.techflyers.compose.file.explorer.screen.main.tab.files.ui.dialog

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.techflyers.compose.file.explorer.R
import com.techflyers.compose.file.explorer.common.copyToClipboard
import com.techflyers.compose.file.explorer.common.showMsg
import com.techflyers.compose.file.explorer.screen.main.tab.files.holder.ContentHolder
import com.techflyers.compose.file.explorer.screen.main.tab.files.provider.ExtendedMetadataProvider
import com.techflyers.compose.file.explorer.screen.main.tab.files.provider.MetadataField
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
        LaunchedEffect(Unit) { onDismiss() }
        return
    }
    var isLoading by remember { mutableStateOf(true) }
    var sections by remember { mutableStateOf<List<MetadataSection>>(emptyList()) }

    LaunchedEffect(holder.uniquePath) {
        isLoading = true
        sections = withContext(Dispatchers.IO) {
            ExtendedMetadataProvider.collect(holder)
        }
        isLoading = false
    }

    ExtendedMetadataSheetContent(
        title = holder.displayName.ifBlank { holder.uniquePath.substringAfterLast('/') },
        isLoading = isLoading,
        sections = sections,
        onDismiss = onDismiss
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExtendedMetadataSheet(
    uri: Uri,
    extraFilePath: String? = null,
    displayNameHint: String? = null,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var isLoading by remember { mutableStateOf(true) }
    var sections by remember { mutableStateOf<List<MetadataSection>>(emptyList()) }

    LaunchedEffect(uri, extraFilePath) {
        isLoading = true
        sections = withContext(Dispatchers.IO) {
            ExtendedMetadataProvider.collectFromUri(
                context = context,
                uri = uri,
                extraFilePath = extraFilePath,
                displayNameHint = displayNameHint
            )
        }
        isLoading = false
    }

    val title = displayNameHint?.takeIf { it.isNotBlank() }
        ?: extraFilePath?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
        ?: uri.lastPathSegment?.substringAfterLast('/')
        ?: stringResource(R.string.extended_metadata)

    ExtendedMetadataSheetContent(
        title = title,
        isLoading = isLoading,
        sections = sections,
        onDismiss = onDismiss
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExtendedMetadataSheetContent(
    title: String,
    isLoading: Boolean,
    sections: List<MetadataSection>,
    onDismiss: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val filteredSections = remember(sections, searchQuery) {
        val q = searchQuery.trim()
        if (q.isEmpty()) {
            sections
        } else {
            sections.mapNotNull { section ->
                if (section.title.contains(q, ignoreCase = true)) {
                    section
                } else {
                    val matchingFields = section.fields.filter {
                        it.label.contains(q, ignoreCase = true) ||
                            it.value.contains(q, ignoreCase = true)
                    }
                    if (matchingFields.isNotEmpty()) {
                        section.copy(fields = matchingFields)
                    } else null
                }
            }
        }
    }

    val totalFieldsCount = remember(sections) {
        sections.sumOf { it.fields.size }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header row with title, field count badge, and Copy All button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.extended_metadata),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (totalFieldsCount > 0) "$title • $totalFieldsCount properties" else title,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (sections.isNotEmpty()) {
                    Spacer(modifier = Modifier.width(8.dp))
                    OutlinedButton(
                        onClick = {
                            val fullReport = buildString {
                                appendLine("=== Extended Metadata: $title ===")
                                sections.forEach { sec ->
                                    appendLine()
                                    appendLine("[${sec.title}]")
                                    sec.fields.forEach { f ->
                                        appendLine("${f.label}: ${f.value}")
                                    }
                                }
                            }.trim()
                            fullReport.copyToClipboard()
                            showMsg(R.string.copied_to_clipboard)
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ContentCopy,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Copy All",
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }

            // Search / filter field when metadata has multiple fields
            if (!isLoading && totalFieldsCount > 5) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Filter metadata fields…") },
                    singleLine = true,
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.Search,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(
                                    imageVector = Icons.Rounded.Clear,
                                    contentDescription = "Clear",
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            when {
                isLoading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(36.dp))
                            Text(
                                text = "Analyzing file structure, streams & checksums…",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                filteredSections.isEmpty() -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (searchQuery.isNotBlank()) "No matching metadata fields" else stringResource(R.string.no_extended_metadata),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 560.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(filteredSections, key = { it.title }) { section ->
                            MetadataSectionCard(section = section)
                        }
                        item {
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MetadataSectionCard(section: MetadataSection) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = section.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                ) {
                    Text(
                        text = "${section.fields.size}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            section.fields.forEachIndexed { idx, field ->
                MetadataFieldRow(field = field)
                if (idx < section.fields.lastIndex) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
                    )
                }
            }
        }
    }
}

@Composable
private fun MetadataFieldRow(field: MetadataField) {
    val isMonospaceValue = remember(field.label) {
        val lower = field.label.lowercase()
        lower.contains("sha") || lower.contains("md5") || lower.contains("crc") ||
            lower.contains("hex") || lower.contains("inode") || lower.contains("permissions") ||
            lower.contains("path") || lower.contains("uid") || lower.contains("selinux")
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable {
                field.value.copyToClipboard()
                showMsg(R.string.copied_to_clipboard)
            }
            .padding(vertical = 6.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = field.label,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .weight(0.38f)
                .padding(end = 8.dp)
        )
        Text(
            text = field.value,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = if (isMonospaceValue) FontFamily.Monospace else FontFamily.Default,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(0.62f)
        )
    }
}
