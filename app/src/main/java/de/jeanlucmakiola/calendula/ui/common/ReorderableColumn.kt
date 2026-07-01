package de.jeanlucmakiola.calendula.ui.common

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.zIndex

/**
 * A vertical list whose rows can be dragged into a new order by their handle.
 *
 * Built for the short, fixed grouped-card lists in Settings (#24) — no external
 * dependency and no [androidx.compose.foundation.lazy.LazyColumn] (the settings
 * screens are a single [androidx.compose.foundation.verticalScroll] column, which
 * can't nest a scrolling list). [rowContent] receives the [Position] for the
 * row's current place in the order (so it can reuse [GroupedRow]'s card shaping)
 * and a `dragHandle` [Modifier] to attach to whatever element should start a
 * drag. While a row is held it floats above the others, which reflow live; the
 * final order is committed once via [onReorder] on release, so the persistence
 * layer sees one write per gesture, not one per crossed row.
 *
 * Reordering is measurement-driven: each row reports its own height, so rows of
 * unequal height still swap at the right point.
 */
@Composable
fun <T> ReorderableColumn(
    items: List<T>,
    keyOf: (T) -> Any,
    onReorder: (List<T>) -> Unit,
    modifier: Modifier = Modifier,
    rowContent: @Composable (item: T, position: Position, dragHandle: Modifier, isDragging: Boolean) -> Unit,
) {
    // Local working copy so rows reflow instantly during a drag; re-seeded when
    // the incoming list changes (including the echo of our own committed order).
    var order by remember(items) { mutableStateOf(items) }
    var draggedKey by remember { mutableStateOf<Any?>(null) }
    // Translation of the held row from its settled slot; a swap folds one slot
    // height back into this so the row stays under the finger across the swap.
    var dragOffsetY by remember { mutableStateOf(0f) }
    val heights = remember { mutableStateMapOf<Any, Int>() }

    Column(modifier) {
        order.forEachIndexed { index, item ->
            val key = keyOf(item)
            val isDragging = key == draggedKey
            val dragHandle = Modifier.pointerInput(key) {
                detectDragGestures(
                    onDragStart = {
                        draggedKey = key
                        dragOffsetY = 0f
                    },
                    onDragEnd = {
                        draggedKey = null
                        dragOffsetY = 0f
                        onReorder(order)
                    },
                    onDragCancel = {
                        draggedKey = null
                        dragOffsetY = 0f
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        dragOffsetY += dragAmount.y
                        val cur = order.indexOfFirst { keyOf(it) == key }
                        if (cur < 0) return@detectDragGestures
                        // Swap once the row has travelled past half of the
                        // neighbour it's heading toward, in the drag direction.
                        if (dragAmount.y > 0 && cur < order.lastIndex) {
                            val nextHeight = heights[keyOf(order[cur + 1])] ?: 0
                            if (nextHeight > 0 && dragOffsetY > nextHeight / 2f) {
                                order = order.toMutableList().apply { add(cur + 1, removeAt(cur)) }
                                dragOffsetY -= nextHeight
                            }
                        } else if (dragAmount.y < 0 && cur > 0) {
                            val prevHeight = heights[keyOf(order[cur - 1])] ?: 0
                            if (prevHeight > 0 && dragOffsetY < -prevHeight / 2f) {
                                order = order.toMutableList().apply { add(cur - 1, removeAt(cur)) }
                                dragOffsetY += prevHeight
                            }
                        }
                    },
                )
            }
            Box(
                Modifier
                    .onSizeChanged { heights[key] = it.height }
                    .zIndex(if (isDragging) 1f else 0f)
                    .graphicsLayer { translationY = if (isDragging) dragOffsetY else 0f },
            ) {
                rowContent(item, positionOf(index, order.size), dragHandle, isDragging)
            }
        }
    }
}
