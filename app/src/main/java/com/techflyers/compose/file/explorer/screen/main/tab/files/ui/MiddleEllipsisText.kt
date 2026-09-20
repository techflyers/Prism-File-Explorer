package com.techflyers.compose.file.explorer.screen.main.tab.files.ui

import androidx.compose.foundation.MarqueeAnimationMode
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.content.res.Configuration
import com.techflyers.compose.file.explorer.App.Companion.globalClass

fun computeMiddleEllipsisParts(
    text: String,
    isFolder: Boolean = false,
    endCharsCount: Int = 0
): Pair<String, String>? {
    val dotIndex = if (!isFolder) text.lastIndexOf('.') else -1
    val hasExtension = !isFolder && dotIndex > 0 && dotIndex < text.length - 1

    return if (hasExtension) {
        val baseName = text.substring(0, dotIndex)
        val ext = text.substring(dotIndex)
        if (endCharsCount > 0 && baseName.length > endCharsCount) {
            val p = baseName.substring(0, baseName.length - endCharsCount)
            val s = baseName.substring(baseName.length - endCharsCount) + ext
            Pair(p, s)
        } else {
            Pair(baseName, ext)
        }
    } else {
        if (endCharsCount > 0 && text.length > endCharsCount) {
            val p = text.substring(0, text.length - endCharsCount)
            val s = text.substring(text.length - endCharsCount)
            Pair(p, s)
        } else {
            null
        }
    }
}

@Composable
fun MiddleEllipsisText(
    text: String,
    modifier: Modifier = Modifier,
    isFolder: Boolean = false,
    isSelected: Boolean = false,
    fontSize: TextUnit = 14.sp,
    color: Color = Color.Unspecified,
    fontWeight: FontWeight? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
    textAlign: TextAlign = TextAlign.Start,
    maxLines: Int = globalClass.preferencesManager.filenameMaxLines,
    enableMarquee: Boolean = true,
    marqueeVelocity: Dp = 90.dp,
    endCharsCount: Int = run {
        val prefs = globalClass.preferencesManager
        val landscapeVal = prefs.filenameEndCharsCountLandscape
        val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
        if (isLandscape && landscapeVal >= 0) landscapeVal else prefs.filenameEndCharsCount
    }
) {
    val shouldMarquee = enableMarquee && isSelected

    if (shouldMarquee) {
        Text(
            text = text,
            fontSize = fontSize,
            color = color,
            fontWeight = fontWeight,
            maxLines = 1,
            softWrap = false,
            lineHeight = lineHeight,
            overflow = TextOverflow.Clip,
            textAlign = textAlign,
            modifier = modifier.basicMarquee(
                iterations = Int.MAX_VALUE,
                animationMode = MarqueeAnimationMode.Immediately,
                velocity = marqueeVelocity
            )
        )
        return
    }

    if (maxLines == 1) {
        val parts = computeMiddleEllipsisParts(text, isFolder, endCharsCount)
        if (parts != null) {
            val (prefix, suffix) = parts
            Row(
                modifier = modifier,
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = if (textAlign == TextAlign.Center) Arrangement.Center else Arrangement.Start
            ) {
                Text(
                    text = prefix,
                    fontSize = fontSize,
                    color = color,
                    fontWeight = fontWeight,
                    maxLines = 1,
                    softWrap = false,
                    lineHeight = lineHeight,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = textAlign,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Text(
                    text = suffix,
                    fontSize = fontSize,
                    color = color,
                    fontWeight = fontWeight,
                    maxLines = 1,
                    softWrap = false,
                    lineHeight = lineHeight
                )
            }
            return
        }
    }

    Text(
        text = text,
        fontSize = fontSize,
        color = color,
        fontWeight = fontWeight,
        maxLines = maxLines,
        softWrap = (maxLines > 1),
        lineHeight = lineHeight,
        overflow = TextOverflow.Ellipsis,
        textAlign = textAlign,
        modifier = modifier
    )
}
