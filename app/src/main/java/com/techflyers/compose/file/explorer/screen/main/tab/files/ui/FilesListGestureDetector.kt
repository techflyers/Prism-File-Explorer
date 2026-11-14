package com.techflyers.compose.file.explorer.screen.main.tab.files.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventTimeoutCancellationException
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.ViewConfiguration
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.techflyers.compose.file.explorer.App.Companion.globalClass
import com.techflyers.compose.file.explorer.screen.main.tab.files.FilesTab
import com.techflyers.compose.file.explorer.screen.main.tab.files.holder.ContentHolder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

@Composable
fun Modifier.unifiedFileGestureDetector(
    tab: FilesTab,
    viewConfiguration: ViewConfiguration,
    haptic: HapticFeedback,
    coroutineScope: CoroutineScope,
    findItemIndexAt: (Offset, IntSize, Boolean) -> Int?,
    scrollBy: suspend (Float, Offset, IntSize) -> Unit,
    onOpenMenu: (ContentHolder) -> Unit,
    onEmptyAreaLongPress: ((Offset) -> Unit)? = null
): Modifier {
    var listCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val currentFindItemIndexAt by rememberUpdatedState(findItemIndexAt)
    val currentScrollBy by rememberUpdatedState(scrollBy)
    val currentOnOpenMenu by rememberUpdatedState(onOpenMenu)
    val currentOnEmptyAreaLongPress by rememberUpdatedState(onEmptyAreaLongPress)

    return this
        .onGloballyPositioned { listCoordinates = it }
        .pointerInput(tab.activeFolder.uniquePath) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                tab.cancelPendingAutoScroll()
                val detectedIndex = currentFindItemIndexAt(down.position, size, false)
                if (detectedIndex == null) {
                    val emptyCallback = currentOnEmptyAreaLongPress
                    if (emptyCallback == null) return@awaitEachGesture
                    var currentDown = down
                    var isLongPressTriggered = false
                    try {
                        withTimeout(300L) {
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                if (event.changes.size > 1) return@withTimeout
                                val change = event.changes.find { it.id == currentDown.id } ?: return@withTimeout
                                if (!change.pressed) return@withTimeout
                                val dist = (change.position - down.position).getDistance()
                                if (dist > viewConfiguration.touchSlop) return@withTimeout
                                currentDown = change
                            }
                        }
                    } catch (_: PointerEventTimeoutCancellationException) {
                        isLongPressTriggered = true
                    }
                    if (isLongPressTriggered) {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        emptyCallback(down.position)
                    }
                    return@awaitEachGesture
                }
                val initialIndex: Int = detectedIndex
                val initialItem = tab.contentSnapshot.getOrNull(initialIndex) ?: return@awaitEachGesture
                val wasAlreadySelected = tab.selectedFiles.containsKey(initialItem.uniquePath)
                val previousAnchorIndex = tab.lastSelectedFileIndex

                // Standard long-press timeout (300ms) to ensure quick taps pass through to Modifier.clickable
                val dwellTimeout = 300L
                var currentDown = down
                var isLongPressTriggered = false

                // 1. Dwell Phase
                try {
                    withTimeout(dwellTimeout) {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            if (event.changes.size > 1) {
                                return@withTimeout
                            }
                            val change = event.changes.find { it.id == currentDown.id } ?: return@withTimeout
                            if (!change.pressed) return@withTimeout
                            val dist = (change.position - down.position).getDistance()
                            if (dist > viewConfiguration.touchSlop) {
                                return@withTimeout
                            }
                            currentDown = change
                        }
                    }
                } catch (_: PointerEventTimeoutCancellationException) {
                    isLongPressTriggered = true
                }

                if (!isLongPressTriggered) return@awaitEachGesture

                // 2. Long-Press Confirmed
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                tab.transientPressHighlightPath = null

                val initialSelectionSnapshot = tab.selectedFiles.toMap()
                val isTwoPressRangeCandidate = !wasAlreadySelected &&
                        globalClass.preferencesManager.twoTapLongPressRangeSelection &&
                        initialSelectionSnapshot.isNotEmpty() &&
                        previousAnchorIndex in tab.contentSnapshot.indices &&
                        previousAnchorIndex != initialIndex

                if (!wasAlreadySelected) {
                    if (isTwoPressRangeCandidate) {
                        // Two-point long-press range selection: select all items between previousAnchorIndex and initialIndex inclusive
                        val minIdx = minOf(previousAnchorIndex, initialIndex)
                        val maxIdx = maxOf(previousAnchorIndex, initialIndex)
                        for (i in minIdx..maxIdx) {
                            val item = tab.contentSnapshot.getOrNull(i) ?: continue
                            tab.selectedFiles[item.uniquePath] = item
                        }
                    } else {
                        tab.selectedFiles[initialItem.uniquePath] = initialItem
                    }
                    tab.lastSelectedFileIndex = initialIndex
                    tab.selectedFilesCount = tab.selectedFiles.size
                    tab.dragSelectionVersion++
                    tab.onSelectionChange()
                }

                var hasMoved = false
                var currentPosition = down.position
                var lastAppliedRangeIndex: Int = initialIndex
                var autoScrollJob: Job? = null
                var tabHoverJob: Job? = null
                var lastHoveredTabIndex = -1
                val dragDropSlopThreshold = 24.dp.toPx()
                val rangeSelectSlopThreshold = viewConfiguration.touchSlop
                val edgeZonePx = 96.dp.toPx()
                val outerEdgeTriggerPx = 36.dp.toPx()
                val minSpeedPxPerSec = 380.dp.toPx()
                val maxSpeedPxPerSec = 2000.dp.toPx()

                fun computeRangeScrollVelocity(pos: Offset, currentSize: IntSize): Float {
                    val vh = (listCoordinates?.takeIf { it.isAttached }?.size?.height ?: currentSize.height).toFloat()
                    if (vh <= 0f) return 0f
                    val topZoneEnd = minOf(edgeZonePx, vh * 0.35f)
                    val bottomZoneStart = maxOf(vh - edgeZonePx, vh * 0.65f)

                    val notDraggingDown = pos.y <= down.position.y + rangeSelectSlopThreshold
                    val canScrollUp = down.position.y > topZoneEnd ||
                            pos.y <= down.position.y - rangeSelectSlopThreshold * 0.5f ||
                            (pos.y <= outerEdgeTriggerPx && notDraggingDown) ||
                            (lastAppliedRangeIndex in 0 until initialIndex)

                    if (pos.y < topZoneEnd && canScrollUp) {
                        val rawFraction = ((topZoneEnd - pos.y) / topZoneEnd.coerceAtLeast(1f)).coerceIn(0.1f, 1.5f)
                        val curved = if (rawFraction <= 1f) rawFraction * rawFraction else 1f + (rawFraction - 1f) * 0.8f
                        return -(minSpeedPxPerSec + (maxSpeedPxPerSec - minSpeedPxPerSec) * curved)
                    }

                    val notDraggingUp = pos.y >= down.position.y - rangeSelectSlopThreshold
                    val canScrollDown = down.position.y < bottomZoneStart ||
                            pos.y >= down.position.y + rangeSelectSlopThreshold * 0.5f ||
                            (pos.y >= vh - outerEdgeTriggerPx && notDraggingUp) ||
                            lastAppliedRangeIndex > initialIndex

                    if (pos.y > bottomZoneStart && canScrollDown) {
                        val zoneHeight = (vh - bottomZoneStart).coerceAtLeast(1f)
                        val rawFraction = ((pos.y - bottomZoneStart) / zoneHeight).coerceIn(0.1f, 1.5f)
                        val curved = if (rawFraction <= 1f) rawFraction * rawFraction else 1f + (rawFraction - 1f) * 0.8f
                        return minSpeedPxPerSec + (maxSpeedPxPerSec - minSpeedPxPerSec) * curved
                    }

                    return 0f
                }

                // 3. Movement / Release Loop
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    if (event.changes.size > 1) {
                        autoScrollJob?.cancel()
                        tabHoverJob?.cancel()
                        tab.cancelDragDrop()
                        break
                    }
                    val change = event.changes.find { it.id == down.id } ?: break

                    if (change.pressed) {
                        change.consume()
                        val distance = (change.position - down.position).getDistance()

                        if (!hasMoved) {
                            val activeSlop = if (wasAlreadySelected) dragDropSlopThreshold else rangeSelectSlopThreshold
                            if (distance > activeSlop) {
                                hasMoved = true

                                if (wasAlreadySelected) {
                                    val winPos = listCoordinates?.let {
                                        if (it.isAttached) it.localToWindow(change.position) else null
                                    } ?: change.position
                                    tab.startDragDrop(
                                        items = tab.selectedFiles.values.toList(),
                                        startOffset = change.position,
                                        initialWindowOffset = winPos
                                    )
                                }
                            }
                        }

                        if (hasMoved) {
                            currentPosition = change.position
                            val viewportHeight = (listCoordinates?.takeIf { it.isAttached }?.size?.height ?: size.height).toFloat()
                            val edgeThreshold = 72.dp.toPx()

                            if (wasAlreadySelected) {
                                val winPos = listCoordinates?.let {
                                    if (it.isAttached) it.localToWindow(currentPosition) else null
                                } ?: currentPosition
                                tab.updateDragDrop(currentPosition, winPos)

                                val hoveredTabIdx = globalClass.mainActivityManager.findTabDropTargetIndexAt(winPos)
                                if (hoveredTabIdx != lastHoveredTabIndex) {
                                    tabHoverJob?.cancel()
                                    lastHoveredTabIndex = hoveredTabIdx
                                    if (hoveredTabIdx >= 0 && hoveredTabIdx != globalClass.mainActivityManager.state.value.selectedTabIndex) {
                                        tabHoverJob = coroutineScope.launch {
                                            delay(450)
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            globalClass.mainActivityManager.selectTabAt(hoveredTabIdx)
                                        }
                                    }
                                }

                                val activeTargetTab = (globalClass.mainActivityManager.getActiveTab() as? FilesTab) ?: tab
                                if (hoveredTabIdx < 0 && currentPosition.y in 0f..edgeThreshold) {
                                    if (autoScrollJob == null || !autoScrollJob!!.isActive) {
                                        autoScrollJob = coroutineScope.launch {
                                            while (isActive && currentPosition.y in 0f..edgeThreshold) {
                                                val factor = ((edgeThreshold - currentPosition.y) / edgeThreshold).coerceIn(0.15f, 1f)
                                                if (activeTargetTab === tab) {
                                                    currentScrollBy(-30f * factor, currentPosition, size)
                                                } else {
                                                    activeTargetTab.scrollActiveListBy(-30f * factor)
                                                }
                                                val updatedWin = listCoordinates?.let {
                                                    if (it.isAttached) it.localToWindow(currentPosition) else null
                                                } ?: currentPosition
                                                tab.updateDragDrop(currentPosition, updatedWin)
                                                delay(16)
                                            }
                                        }
                                    }
                                } else if (hoveredTabIdx < 0 && currentPosition.y in (viewportHeight - edgeThreshold)..viewportHeight) {
                                    if (autoScrollJob == null || !autoScrollJob!!.isActive) {
                                        autoScrollJob = coroutineScope.launch {
                                            while (isActive && currentPosition.y in (viewportHeight - edgeThreshold)..viewportHeight) {
                                                val factor = ((currentPosition.y - (viewportHeight - edgeThreshold)) / edgeThreshold).coerceIn(0.15f, 1f)
                                                if (activeTargetTab === tab) {
                                                    currentScrollBy(30f * factor, currentPosition, size)
                                                } else {
                                                    activeTargetTab.scrollActiveListBy(30f * factor)
                                                }
                                                val updatedWin = listCoordinates?.let {
                                                    if (it.isAttached) it.localToWindow(currentPosition) else null
                                                } ?: currentPosition
                                                tab.updateDragDrop(currentPosition, updatedWin)
                                                delay(16)
                                            }
                                        }
                                    }
                                } else {
                                    autoScrollJob?.cancel()
                                    autoScrollJob = null
                                }
                            } else {
                                val targetIndex: Int = currentFindItemIndexAt(currentPosition, size, true)
                                    ?: lastAppliedRangeIndex
                                if (targetIndex != lastAppliedRangeIndex) {
                                    lastAppliedRangeIndex = targetIndex
                                    updateRangeSelection(tab, initialIndex, targetIndex, initialSelectionSnapshot)
                                }

                                val desiredVelocity = computeRangeScrollVelocity(currentPosition, size)
                                if (desiredVelocity != 0f) {
                                    if (autoScrollJob == null || !autoScrollJob!!.isActive) {
                                        autoScrollJob = coroutineScope.launch {
                                            var lastFrameNanos = 0L
                                            while (isActive) {
                                                val velocity = computeRangeScrollVelocity(currentPosition, size)
                                                if (velocity == 0f) break

                                                val dtSeconds = androidx.compose.runtime.withFrameNanos { nowNanos ->
                                                    val dt = if (lastFrameNanos == 0L) 0.016f
                                                    else ((nowNanos - lastFrameNanos) / 1_000_000_000f).coerceIn(0.004f, 0.05f)
                                                    lastFrameNanos = nowNanos
                                                    dt
                                                }

                                                val stepPx = velocity * dtSeconds
                                                currentScrollBy(stepPx, currentPosition, size)
                                                currentFindItemIndexAt(currentPosition, size, true)?.let { idx ->
                                                    if (idx != lastAppliedRangeIndex) {
                                                        lastAppliedRangeIndex = idx
                                                        updateRangeSelection(tab, initialIndex, idx, initialSelectionSnapshot)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                } else {
                                    autoScrollJob?.cancel()
                                    autoScrollJob = null
                                }
                            }
                        }
                    } else {
                        // POINTER UP (Release)
                        change.consume()
                        tab.transientPressHighlightPath = null
                        autoScrollJob?.cancel()
                        autoScrollJob = null
                        tabHoverJob?.cancel()
                        tabHoverJob = null

                        if (!hasMoved) {
                            if (wasAlreadySelected) {
                                if (globalClass.preferencesManager.showFileOptionMenuOnLongClick) {
                                    currentOnOpenMenu(initialItem)
                                } else {
                                    tab.selectedFiles.remove(initialItem.uniquePath)
                                    tab.lastSelectedFileIndex = -1
                                    tab.selectedFilesCount = tab.selectedFiles.size
                                    tab.dragSelectionVersion++
                                    tab.onSelectionChange()
                                }
                            } else {
                                tab.scrollItemAboveRibbon(initialIndex)
                            }
                        } else {
                            if (wasAlreadySelected) {
                                tab.completeDragDrop()
                            } else {
                                val finalIndex: Int = currentFindItemIndexAt(currentPosition, size, true)
                                    ?: lastAppliedRangeIndex
                                tab.lastSelectedFileIndex = finalIndex
                                tab.selectedFilesCount = tab.selectedFiles.size
                                tab.dragSelectionVersion++
                                tab.onSelectionChange()
                                tab.scrollItemAboveRibbon(finalIndex)
                            }
                        }
                        break
                    }
                }
            }
        }
}

private fun updateRangeSelection(
    tab: FilesTab,
    startIndex: Int,
    currentIndex: Int,
    initialSelectionSnapshot: Map<String, ContentHolder>
) {
    val minIdx = minOf(startIndex, currentIndex)
    val maxIdx = maxOf(startIndex, currentIndex)
    val currentGestureRangePaths = mutableSetOf<String>()

    for (i in minIdx..maxIdx) {
        val item = tab.contentSnapshot.getOrNull(i) ?: continue
        currentGestureRangePaths.add(item.uniquePath)
        tab.selectedFiles[item.uniquePath] = item
    }

    val toRemove = tab.selectedFiles.keys.filter { path ->
        path !in currentGestureRangePaths && path !in initialSelectionSnapshot
    }
    for (path in toRemove) {
        tab.selectedFiles.remove(path)
    }

    tab.lastSelectedFileIndex = currentIndex
    tab.selectedFilesCount = tab.selectedFiles.size
    tab.dragSelectionVersion++
}
