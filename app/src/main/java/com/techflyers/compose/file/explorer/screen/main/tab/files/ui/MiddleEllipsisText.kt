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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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
    maxLines: Int = 1,
    enableMarquee: Boolean = true,
    marqueeVelocity: Dp = 30.dp
) {
    val shouldMarquee = enableMarquee && isSelected
    val dotIndex = if (!isFolder) text.lastIndexOf('.') else -1

    if (!isFolder && dotIndex > 0 && dotIndex < text.length - 1 && maxLines == 1) {
        val baseName = text.substring(0, dotIndex)
        val ext = text.substring(dotIndex)

        Row(
            modifier = modifier,
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = if (textAlign == TextAlign.Center) Arrangement.Center else Arrangement.Start
        ) {
            Text(
                text = baseName,
                fontSize = fontSize,
                color = color,
                fontWeight = fontWeight,
                maxLines = 1,
                softWrap = false,
                lineHeight = lineHeight,
                overflow = if (shouldMarquee) TextOverflow.Clip else TextOverflow.Ellipsis,
                textAlign = textAlign,
                modifier = Modifier
                    .weight(1f, fill = false)
                    .then(
                        if (shouldMarquee) {
                            Modifier.basicMarquee(
                                iterations = Int.MAX_VALUE,
                                animationMode = MarqueeAnimationMode.Immediately,
                                velocity = marqueeVelocity
                            )
                        } else Modifier
                    )
            )
            Text(
                text = ext,
                fontSize = fontSize,
                color = color,
                fontWeight = fontWeight,
                maxLines = 1,
                lineHeight = lineHeight
            )
        }
    } else {
        Text(
            text = text,
            fontSize = fontSize,
            color = color,
            fontWeight = fontWeight,
            maxLines = if (shouldMarquee) 1 else maxLines,
            softWrap = if (shouldMarquee) false else (maxLines > 1),
            lineHeight = lineHeight,
            overflow = if (shouldMarquee) TextOverflow.Clip else TextOverflow.Ellipsis,
            textAlign = textAlign,
            modifier = modifier.then(
                if (shouldMarquee) {
                    Modifier.basicMarquee(
                        iterations = Int.MAX_VALUE,
                        animationMode = MarqueeAnimationMode.Immediately,
                        velocity = marqueeVelocity
                    )
                } else Modifier
            )
        )
    }
}
