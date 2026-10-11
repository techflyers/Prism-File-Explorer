package com.techflyers.compose.file.explorer.screen.viewer.pdf.ui.dialog

import android.content.Context
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Draw
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.techflyers.compose.file.explorer.screen.viewer.pdf.operations.PdfOperations
import kotlinx.coroutines.launch
import java.io.File

private val WatermarkColors = listOf(
    Color(0xFF8A8A8A) to "Grey",
    Color(0xFFE53935) to "Red",
    Color(0xFF1E88E5) to "Blue",
    Color(0xFF43A047) to "Green",
    Color(0xFFFB8C00) to "Orange"
)

@Composable
fun WatermarkPdfDialog(
    visible: Boolean,
    currentFile: File,
    onApplySuccess: (newFile: File) -> Unit,
    onDismiss: () -> Unit
) {
    if (!visible) return

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var watermarkText by remember { mutableStateOf("CONFIDENTIAL") }
    var isDiagonal by remember { mutableStateOf(true) }
    var opacity by remember { mutableFloatStateOf(0.25f) }
    var selectedColorArgb by remember { mutableIntStateOf(WatermarkColors[0].first.toArgb()) }
    var isApplying by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Dialog(
        onDismissRequest = { if (!isApplying) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier
                    .widthIn(max = 520.dp)
                    .fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 8.dp,
                shadowElevation = 12.dp
            ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
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
                                imageVector = Icons.Rounded.Draw,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "Watermark PDF",
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Stamp watermark across all pages",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (!isApplying) {
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
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                // Input fields
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = watermarkText,
                        onValueChange = { watermarkText = it },
                        label = { Text("Watermark Text") },
                        placeholder = { Text("CONFIDENTIAL / DRAFT / SAMPLE") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline
                        )
                    )

                    // Orientation Switch
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Diagonal Angle (45°)", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                            Text(
                                text = if (isDiagonal) "Centered diagonal across page" else "Horizontal centered across page",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = isDiagonal,
                            onCheckedChange = { isDiagonal = it }
                        )
                    }

                    // Opacity Slider
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Opacity", style = MaterialTheme.typography.bodyMedium)
                            Text("${(opacity * 100).toInt()}%", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                        }
                        Slider(
                            value = opacity,
                            onValueChange = { opacity = it },
                            valueRange = 0.08f..0.85f,
                            steps = 15
                        )
                    }

                    // Color Selection
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Color", style = MaterialTheme.typography.bodyMedium)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            WatermarkColors.forEach { (c, _) ->
                                val selected = selectedColorArgb == c.toArgb()
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(c)
                                        .clickable { selectedColorArgb = c.toArgb() },
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (selected) {
                                        Box(
                                            modifier = Modifier
                                                .size(12.dp)
                                                .clip(CircleShape)
                                                .background(Color.White)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (errorMessage != null) {
                        Text(
                            text = errorMessage!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Buttons
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = onDismiss,
                            enabled = !isApplying,
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text("Cancel")
                        }

                        OutlinedButton(
                            onClick = {
                                if (watermarkText.isBlank()) {
                                    errorMessage = "Please enter watermark text"
                                    return@OutlinedButton
                                }
                                isApplying = true
                                errorMessage = null
                                scope.launch {
                                    val out = File(context.cacheDir, "watermarked_share_${System.currentTimeMillis()}.pdf")
                                    val ok = PdfOperations.applyWatermark(
                                        context = context,
                                        sourceUri = Uri.fromFile(currentFile),
                                        destinationFile = out,
                                        text = watermarkText.trim(),
                                        opacity = opacity,
                                        diagonal = isDiagonal,
                                        colorArgb = selectedColorArgb
                                    )
                                    isApplying = false
                                    if (ok && out.exists() && out.length() > 0) {
                                        com.techflyers.compose.file.explorer.screen.viewer.pdf.misc.DocumentShareHelper.shareDocument(
                                            context = context,
                                            uri = Uri.fromFile(out),
                                            title = "Share Watermarked PDF",
                                            desiredFileName = "Watermarked_${currentFile.name}"
                                        )
                                    } else {
                                        errorMessage = "Failed to apply watermark for sharing."
                                    }
                                }
                            },
                            enabled = watermarkText.isNotBlank() && !isApplying,
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Share")
                        }

                        Button(
                            onClick = {
                                if (watermarkText.isBlank()) {
                                    errorMessage = "Please enter watermark text"
                                    return@Button
                                }
                                isApplying = true
                                errorMessage = null
                                scope.launch {
                                    val out = File(context.cacheDir, "watermarked_${System.currentTimeMillis()}.pdf")
                                    val ok = PdfOperations.applyWatermark(
                                        context = context,
                                        sourceUri = Uri.fromFile(currentFile),
                                        destinationFile = out,
                                        text = watermarkText.trim(),
                                        opacity = opacity,
                                        diagonal = isDiagonal,
                                        colorArgb = selectedColorArgb
                                    )
                                    isApplying = false
                                    if (ok && out.exists() && out.length() > 0) {
                                        onApplySuccess(out)
                                    } else {
                                        errorMessage = "Failed to apply watermark."
                                    }
                                }
                            },
                            enabled = watermarkText.isNotBlank() && !isApplying,
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            if (isApplying) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                                Spacer(Modifier.width(8.dp))
                            }
                            Text("Apply")
                        }
                    }
                }
            }
        }
    }
}
}
