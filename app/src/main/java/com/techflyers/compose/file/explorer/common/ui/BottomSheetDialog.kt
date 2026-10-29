package com.techflyers.compose.file.explorer.common.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Velocity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BottomSheetDialog(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    skipPartiallyExpanded: Boolean = true,
    openInFull: Boolean = false,
    preventSwipeDismiss: Boolean = false,
    content: @Composable () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = skipPartiallyExpanded || openInFull,
        confirmValueChange = { targetValue ->
            if (preventSwipeDismiss && targetValue == SheetValue.Hidden) {
                false
            } else {
                true
            }
        }
    )

    val blockDownwardSheetDrag = remember(preventSwipeDismiss) {
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                // Consume leftover downward scroll so the ModalBottomSheet never drags down while scrolling content
                return if (preventSwipeDismiss && available.y > 0f) {
                    Offset(0f, available.y)
                } else {
                    Offset.Zero
                }
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                return if (preventSwipeDismiss && available.y > 0f) {
                    Velocity(0f, available.y)
                } else {
                    Velocity.Zero
                }
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = {
            if (!preventSwipeDismiss) {
                onDismissRequest()
            }
        },
        modifier = if (openInFull) modifier.fillMaxHeight(0.96f) else modifier,
        sheetState = sheetState,
        dragHandle = if (preventSwipeDismiss) null else ({ BottomSheetDefaults.DragHandle() }),
        properties = ModalBottomSheetProperties(
            shouldDismissOnBackPress = !preventSwipeDismiss
        )
    ) {
        if (openInFull || preventSwipeDismiss) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (openInFull) Modifier.fillMaxHeight() else Modifier)
                    .nestedScroll(blockDownwardSheetDrag)
            ) {
                content()
            }
        } else {
            content()
        }
    }
}
