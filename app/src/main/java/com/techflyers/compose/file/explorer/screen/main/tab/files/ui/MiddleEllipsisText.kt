package com.techflyers.compose.file.explorer.screen.main.tab.files.ui

import android.content.res.Configuration
import androidx.compose.foundation.MarqueeAnimationMode
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    fontStyle: FontStyle? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
    textAlign: TextAlign = TextAlign.Start,
    maxLines: Int = globalClass.preferencesManager.filenameMaxLines,
    enableMarquee: Boolean = true,
    marqueeVelocity: Dp = 80.dp,
    endCharsCount: Int = run {
        val prefs = globalClass.preferencesManager
        val landscapeVal = prefs.filenameEndCharsCountLandscape
        val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
        if (isLandscape && landscapeVal >= 0) landscapeVal else prefs.filenameEndCharsCount
    }
) {
    val effectiveMaxLines = maxLines.coerceAtLeast(1)
    val shouldMarquee = enableMarquee && isSelected

    // Single-line mode: preserve prefix + stationary suffix (last X chars + extension)
    // and scroll the prefix smoothly when selected without changing height or dimensions.
    if (effectiveMaxLines == 1) {
        val parts = computeMiddleEllipsisParts(text, isFolder, endCharsCount)
        if (parts != null) {
            val (prefix, suffix) = parts
            Row(
                modifier = modifier,
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = if (textAlign == TextAlign.Center) Arrangement.Center else Arrangement.Start
            ) {
                Text(
                    text = if (shouldMarquee) "$prefix   " else prefix,
                    fontSize = fontSize,
                    color = color,
                    fontWeight = fontWeight,
                    fontStyle = fontStyle,
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
                    text = suffix,
                    fontSize = fontSize,
                    color = color,
                    fontWeight = fontWeight,
                    fontStyle = fontStyle,
                    maxLines = 1,
                    softWrap = false,
                    lineHeight = lineHeight
                )
            }
            return
        }

        Text(
            text = text,
            fontSize = fontSize,
            color = color,
            fontWeight = fontWeight,
            fontStyle = fontStyle,
            maxLines = 1,
            softWrap = false,
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
        return
    }

    // Multiline mode (maxLines > 1):
    // 1. Measure text against available width so selection/deselection NEVER changes line count or item height.
    // 2. If text fits within maxLines without overflow, render standard multiline Text (stationary, no layout shift).
    // 3. If text overflows maxLines:
    //    - Keep the first line stationary.
    //    - Keep the filename ending (last X characters + extension) stationary at the end of the last line.
    //    - When unselected: ellipsis the middle text on the last line before the stationary ending.
    //    - When selected: marquee-scroll the middle text while keeping line 1 and the ending stationary!
    val textMeasurer = rememberTextMeasurer()
    val baseStyle = LocalTextStyle.current
    val density = LocalDensity.current

    BoxWithConstraints(modifier = modifier) {
        val maxWidthPx = with(density) { maxWidth.roundToPx() }.coerceAtLeast(1)
        val resolvedStyle = remember(baseStyle, fontSize, fontWeight, fontStyle, lineHeight, textAlign) {
            baseStyle.merge(
                TextStyle(
                    fontSize = fontSize,
                    fontWeight = fontWeight,
                    fontStyle = fontStyle,
                    lineHeight = lineHeight,
                    textAlign = textAlign
                )
            )
        }

        val layoutResult = remember(text, resolvedStyle, effectiveMaxLines, maxWidthPx) {
            textMeasurer.measure(
                text = text,
                style = resolvedStyle,
                maxLines = effectiveMaxLines,
                overflow = TextOverflow.Clip,
                constraints = Constraints(maxWidth = maxWidthPx)
            )
        }

        if (!layoutResult.hasVisualOverflow || layoutResult.lineCount <= 1) {
            Text(
                text = text,
                fontSize = fontSize,
                color = color,
                fontWeight = fontWeight,
                fontStyle = fontStyle,
                maxLines = effectiveMaxLines,
                softWrap = true,
                lineHeight = lineHeight,
                overflow = TextOverflow.Ellipsis,
                textAlign = textAlign,
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            val totalLines = layoutResult.lineCount.coerceAtMost(effectiveMaxLines)
            val stationaryLinesCount = (totalLines - 1).coerceAtLeast(1)
            val stationaryLines = remember(text, layoutResult, stationaryLinesCount) {
                (0 until stationaryLinesCount).map { lineIdx ->
                    val start = layoutResult.getLineStart(lineIdx)
                    val end = layoutResult.getLineEnd(lineIdx, visibleEnd = true)
                    text.substring(start.coerceIn(0, text.length), end.coerceIn(0, text.length))
                }
            }
            val tailStart = layoutResult.getLineStart(stationaryLinesCount).coerceIn(0, text.length)
            val remainingTail = remember(text, tailStart) {
                text.substring(tailStart)
            }
            val tailParts = remember(remainingTail, isFolder, endCharsCount) {
                computeMiddleEllipsisParts(remainingTail, isFolder, endCharsCount)
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = if (textAlign == TextAlign.Center) Alignment.CenterHorizontally else Alignment.Start
            ) {
                // Stationary upper line(s) (including the first line)
                stationaryLines.forEach { lineStr ->
                    Text(
                        text = lineStr,
                        fontSize = fontSize,
                        color = color,
                        fontWeight = fontWeight,
                        fontStyle = fontStyle,
                        maxLines = 1,
                        softWrap = false,
                        lineHeight = lineHeight,
                        overflow = TextOverflow.Clip,
                        textAlign = textAlign,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Bottom line: scrolling/ellipsized middle portion + stationary filename ending (last X chars + extension)
                if (tailParts != null) {
                    val (midPrefix, endSuffix) = tailParts
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = if (textAlign == TextAlign.Center) Arrangement.Center else Arrangement.Start
                    ) {
                        Text(
                            text = if (shouldMarquee) "$midPrefix   " else midPrefix,
                            fontSize = fontSize,
                            color = color,
                            fontWeight = fontWeight,
                            fontStyle = fontStyle,
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
                            text = endSuffix,
                            fontSize = fontSize,
                            color = color,
                            fontWeight = fontWeight,
                            fontStyle = fontStyle,
                            maxLines = 1,
                            softWrap = false,
                            lineHeight = lineHeight
                        )
                    }
                } else {
                    Text(
                        text = remainingTail,
                        fontSize = fontSize,
                        color = color,
                        fontWeight = fontWeight,
                        fontStyle = fontStyle,
                        maxLines = 1,
                        softWrap = false,
                        lineHeight = lineHeight,
                        overflow = if (shouldMarquee) TextOverflow.Clip else TextOverflow.Ellipsis,
                        textAlign = textAlign,
                        modifier = Modifier
                            .fillMaxWidth()
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
                }
            }
        }
    }
}
