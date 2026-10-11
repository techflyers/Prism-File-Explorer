package com.techflyers.compose.file.explorer.screen.viewer.document.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.SmartToy
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.net.URLEncoder

private data class PromptPreset(
    val title: String,
    val promptPrefix: String
)

private val PRESETS = listOf(
    PromptPreset("Summarize", "Summarize this document concisely, highlighting key points and actionable insights:"),
    PromptPreset("Explain", "Explain the core concepts and findings of this text in simple, easy-to-understand language:"),
    PromptPreset("Key Takeaways", "Extract the top 5 key takeaways and bullet points from the following content:"),
    PromptPreset("Translate", "Translate the following document text into clear, natural English:"),
    PromptPreset("Proofread", "Review and proofread this text for grammar, clarity, and tone improvements:")
)

/**
 * Modern ClearPDF-style Ask AI launcher sheet.
 *
 * Quickly formats prompts + document or selected text, copies to clipboard,
 * and launches ChatGPT, Grok, Claude, or Google Gemini.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentAiSheet(
    visible: Boolean,
    documentTitle: String,
    fullText: String,
    pagesText: List<Pair<Int, String>> = emptyList(),
    selectedText: String?,
    onDismiss: () -> Unit
) {
    if (!visible) return

    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val targetContent = remember(selectedText, fullText) {
        if (!selectedText.isNullOrBlank()) selectedText.trim() else fullText.trim()
    }
    val isSelection = !selectedText.isNullOrBlank()

    var activePreset by remember { mutableStateOf(PRESETS[0]) }
    var userPrompt by remember { mutableStateOf(PRESETS[0].promptPrefix) }
    var showPreview by remember { mutableStateOf(false) }

    fun launchAi(urlTemplate: (encoded: String) -> String, serviceName: String) {
        val combinedText = buildString {
            appendLine(userPrompt.trim())
            appendLine()
            appendLine("--- DOCUMENT CONTENT ---")
            appendLine(targetContent.take(12000))
            if (targetContent.length > 12000) {
                appendLine("[Content truncated for length]")
            }
        }

        // 1. Copy full prompt & document text to clipboard
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        val clip = ClipData.newPlainText("AI Prompt ($documentTitle)", combinedText)
        clipboard?.setPrimaryClip(clip)

        Toast.makeText(context, "Prompt copied! Opening $serviceName…", Toast.LENGTH_SHORT).show()

        // 2. Launch web or native app
        runCatching {
            val encodedQuery = URLEncoder.encode(userPrompt.trim(), "UTF-8")
            val targetUrl = urlTemplate(encodedQuery)
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(targetUrl)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }.onFailure {
            Toast.makeText(context, "Could not open browser for $serviceName", Toast.LENGTH_SHORT).show()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 8.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.AutoAwesome,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "Ask AI",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (isSelection) "Using selected text (${targetContent.length} chars)" else documentTitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Close",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            // Preset prompt chips
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Quick Prompts",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.primary
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PRESETS.forEach { preset ->
                        val selected = activePreset == preset
                        FilterChip(
                            selected = selected,
                            onClick = {
                                activePreset = preset
                                userPrompt = preset.promptPrefix
                            },
                            label = { Text(preset.title) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        )
                    }
                }
            }

            // Editable Prompt Field
            OutlinedTextField(
                value = userPrompt,
                onValueChange = { userPrompt = it },
                label = { Text("Prompt") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                minLines = 2,
                maxLines = 4,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                )
            )

            // Content preview toggle
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { showPreview = !showPreview }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = if (showPreview) "Hide attached document text ▲" else "Preview attached text (${targetContent.length} chars) ▼",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            AnimatedVisibility(visible = showPreview) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                ) {
                    Text(
                        text = targetContent.ifBlank { "No text available" },
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, lineHeight = 16.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .padding(12.dp)
                            .verticalScroll(rememberScrollState())
                    )
                }
            }

            // AI Destination Grid / Cards
            Text(
                text = "Ask With",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.primary
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AiDestinationCard(
                    title = "Ask ChatGPT",
                    subtitle = "OpenAI • chatgpt.com",
                    icon = Icons.Rounded.SmartToy,
                    badgeColor = Color(0xFF10A37F),
                    onClick = {
                        launchAi(
                            urlTemplate = { q -> "https://chatgpt.com/?q=$q" },
                            serviceName = "ChatGPT"
                        )
                    }
                )

                AiDestinationCard(
                    title = "Ask Grok",
                    subtitle = "xAI • x.com/i/grok",
                    icon = Icons.Rounded.Psychology,
                    badgeColor = Color(0xFF1D9BF0),
                    onClick = {
                        launchAi(
                            urlTemplate = { q -> "https://x.com/i/grok?text=$q" },
                            serviceName = "Grok"
                        )
                    }
                )

                AiDestinationCard(
                    title = "Ask Google Gemini",
                    subtitle = "Google • gemini.google.com",
                    icon = Icons.Rounded.Lightbulb,
                    badgeColor = Color(0xFF1A73E8),
                    onClick = {
                        launchAi(
                            urlTemplate = { q -> "https://gemini.google.com/app?text=$q" },
                            serviceName = "Google Gemini"
                        )
                    }
                )

                AiDestinationCard(
                    title = "Ask Claude",
                    subtitle = "Anthropic • claude.ai",
                    icon = Icons.Rounded.Language,
                    badgeColor = Color(0xFFD97706),
                    onClick = {
                        launchAi(
                            urlTemplate = { q -> "https://claude.ai/new?q=$q" },
                            serviceName = "Claude"
                        )
                    }
                )
            }

            // Copy to clipboard fallback button
            OutlinedButton(
                onClick = {
                    val combinedText = buildString {
                        appendLine(userPrompt.trim())
                        appendLine()
                        appendLine("--- DOCUMENT CONTENT ---")
                        appendLine(targetContent)
                    }
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                    clipboard?.setPrimaryClip(ClipData.newPlainText("AI Prompt", combinedText))
                    Toast.makeText(context, "Full prompt & document text copied to clipboard", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.ContentCopy,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text("Copy Full Prompt & Text Only")
            }

            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun AiDestinationCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    badgeColor: Color,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(badgeColor.copy(alpha = 0.15f))
                        .border(1.dp, badgeColor.copy(alpha = 0.35f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = badgeColor,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Icon(
                imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
