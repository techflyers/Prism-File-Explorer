package com.raival.compose.file.explorer.screen.viewer.pdf.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * PDF text reflow view. Displays extracted text from all PDF pages
 * as a scrollable, reflowable text composable.
 */
@Composable
fun PdfReflowView(
    extractedText: String,
    modifier: Modifier = Modifier
) {
    SelectionContainer {
        Text(
            text = if (extractedText.isBlank()) "No text could be extracted from this PDF."
            else extractedText,
            style = MaterialTheme.typography.bodyMedium,
            lineHeight = 26.sp,
            fontSize = 15.sp,
            modifier = modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        )
    }
}
