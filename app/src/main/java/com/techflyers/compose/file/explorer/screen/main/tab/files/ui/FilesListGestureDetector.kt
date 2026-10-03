package com.techflyers.compose.file.explorer.screen.main.tab.files.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
    findItemIndexAt: (Offset, IntSize) -> Int?,
    scrollBy: suspend (Float, Offset, IntSize) -> Unit,
    onOpenMenu: (ContentHolder) -> Unit
): Modifier {
    var listCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }

    return this
        .onGloballyPositioned { listCoordinates = it }
        .pointerInput(tab.activeFolder.uniquePath, tab.activeFolderContent.size) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                val initialIndex = findItemIndexAt(down.position, size) ?: return@awaitEachGesture
                val initialItem = tab.activeFolderContent.getOrNull(initialIndex) ?: return@awaitEachGesture
                val wasAlreadySelected = tab.selectedFiles.containsKey(initialItem.uniquePath)

                // Snappy selection latency: 140ms when already selecting, 220ms when 0 selected
                val dwellTimeout = if (tab.selectedFiles.isNotEmpty()) 140L else 220L
                var currentDown = down
                var isLongPressTriggered = false

                // 1. Dwell Phase
                try {
                    withTimeout(dwellTimeout) {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            if (event.changes.size > 1) {
                                // Multi-touch / pinch detected during dwell: reject
                                return@withTimeout
                            }
                            val change = event.changes.find { it.id == currentDown.id } ?: return@withTimeout
                            if (!change.pressed) return@withTimeout
                            val dist = (change.position - down.position).getDistance()
                            if (dist > viewConfiguration.touchSlop) {
                                // User is scrolling naturally; exit to let LazyLayout scroll
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

                if (!wasAlreadySelected) {
                    tab.selectedFiles[initialItem.uniquePath] = initialItem
                    tab.lastSelectedFileIndex = initialIndex
                    tab.selectedFilesCount = tab.selectedFiles.size
                    tab.dragSelectionVersion++
                    tab.onSelectionChange()
                }

                var hasMoved = false
                var currentPosition = down.position
                var autoScrollJob: Job? = null
                val dragSlopThreshold = 24.dp.toPx()

                // 3. Movement / Release Loop
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    if (event.changes.size > 1) {
                        // Multi-touch / two-finger pinch: cancel session
                        autoScrollJob?.cancel()
                        if (tab.dragDropSession != null) {
                            tab.dragDropSession = null
                        }
                        break
                    }
                    val change = event.changes.find { it.id == down.id } ?: break

                    if (change.pressed) {
                        change.consume()
                        val distance = (change.position - down.position).getDistance()
                        val currentIndex = findItemIndexAt(change.position, size)

                        if (!hasMoved) {
                            val shouldStartDrag = if (wasAlreadySelected) {
                                distance > dragSlopThreshold
                            } else {
                                (currentIndex != null && currentIndex != initialIndex) || distance > dragSlopThreshold
                            }

                            if (shouldStartDrag) {
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

                            if (wasAlreadySelected) {
                                // DRAG & DROP: Update coordinates and hit-test breadcrumb targets
                                val winPos = listCoordinates?.let {
                                    if (it.isAttached) it.localToWindow(currentPosition) else null
                                } ?: currentPosition
                                tab.updateDragDrop(currentPosition, winPos)
                            } else {
                                // RANGE SELECT: Update selection indices and edge auto-scroll
                                val targetIndex = currentIndex ?: initialIndex
                                updateRangeSelection(tab, initialIndex, targetIndex, initialSelectionSnapshot)

                                val viewportHeight = size.height.toFloat()
                                val edgeThreshold = 80.dp.toPx()

                                if (currentPosition.y in 0f..edgeThreshold) {
                                    if (autoScrollJob == null || !autoScrollJob!!.isActive) {
                                        autoScrollJob = coroutineScope.launch {
                                            while (isActive && currentPosition.y in 0f..edgeThreshold) {
                                                val factor = ((edgeThreshold - currentPosition.y) / edgeThreshold).coerceIn(0.1f, 1f)
                                                scrollBy(-35f * factor, currentPosition, size)
                                                findItemIndexAt(currentPosition, size)?.let {
                                                    updateRangeSelection(tab, initialIndex, it, initialSelectionSnapshot)
                                                }
                                                delay(16)
                                            }
                                        }
                                    }
                                } else if (currentPosition.y in (viewportHeight - edgeThreshold)..viewportHeight) {
                                    if (autoScrollJob == null || !autoScrollJob!!.isActive) {
                                        autoScrollJob = coroutineScope.launch {
                                            while (isActive && currentPosition.y in (viewportHeight - edgeThreshold)..viewportHeight) {
                                                val factor = ((currentPosition.y - (viewportHeight - edgeThreshold)) / edgeThreshold).coerceIn(0.1f, 1f)
                                                scrollBy(35f * factor, currentPosition, size)
                                                findItemIndexAt(currentPosition, size)?.let {
                                                    updateRangeSelection(tab, initialIndex, it, initialSelectionSnapshot)
                                                }
                                                delay(16)
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

                        if (!hasMoved) {
                            if (globalClass.preferencesManager.showFileOptionMenuOnLongClick) {
                                onOpenMenu(initialItem)
                            } else if (wasAlreadySelected) {
                                tab.selectedFiles.remove(initialItem.uniquePath)
                                tab.lastSelectedFileIndex = -1
                                tab.selectedFilesCount = tab.selectedFiles.size
                                tab.dragSelectionVersion++
                                tab.onSelectionChange()
                            }
                        } else {
                            if (wasAlreadySelected) {
                                tab.completeDragDrop()
                            } else {
                                tab.lastSelectedFileIndex = findItemIndexAt(currentPosition, size) ?: initialIndex
                                tab.selectedFilesCount = tab.selectedFiles.size
                                tab.dragSelectionVersion++
                                tab.onSelectionChange()
                            }
                        }
                        break
                    }
                }
            }
        }
}

/**
 * Formula: EffectiveSelection = (PreExistingSelection \ CurrentGestureRange) ∪ (CurrentGestureRange ∩ AnchorOperation)
 * Guarantees that shrinking a selection stroke cleanly restores previously selected items without losing user intent.
 */
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
        val item = tab.activeFolderContent.getOrNull(i) ?: continue
        currentGestureRangePaths.add(item.uniquePath)
        tab.selectedFiles[item.uniquePath] = item
    }

    val toRemove = tab.selectedFiles.keys.filter { path ->
        path !in currentGestureRangePaths && path !in initialSelectionSnapshot
    }
    for (path in toRemove) {
        tab.selectedFiles.remove(path)
    }

    tab.selectedFilesCount = tab.selectedFiles.size
    tab.dragSelectionVersion++
}
