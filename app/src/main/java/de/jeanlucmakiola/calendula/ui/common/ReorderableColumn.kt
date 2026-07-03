package de.jeanlucmakiola.calendula.ui.common

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Uniform row height for [ReorderableColumn]; a fixed pitch keeps drag maths exact. */
val ReorderableRowHeight: Dp = 64.dp
private val RowGap: Dp = 2.dp

/**
 * A vertical list whose rows can be dragged into a new order by their handle.
 *
 * Built for the short, fixed grouped-card lists in Settings (#24) — no external
 * dependency and no [androidx.compose.foundation.lazy.LazyColumn] (the settings
 * screens are a single [androidx.compose.foundation.verticalScroll] column, which
 * can't nest a scrolling list). Rows are a fixed [ReorderableRowHeight] with a
 * uniform gap, so a row's target slot is simply how many whole pitches it has
 * been dragged. The held row follows the finger while the others slide out of
 * its way (animated); on release the order is committed immediately with a
 * single [onReorder] call, then the held row eases into its new slot as a
 * purely visual settle — safe to interrupt with another drag, since the
 * commit itself never waits on it.
 *
 * [rowContent] receives the [Position] for the row's place in the order (to reuse
 * [GroupedRow]'s card shaping — pass `gapBelow = false` there, this owns spacing)
 * and a `dragHandle` [Modifier] to attach to the element that starts a drag.
 */
@Composable
fun <T> ReorderableColumn(
    items: List<T>,
    keyOf: (T) -> Any,
    onReorder: (List<T>) -> Unit,
    modifier: Modifier = Modifier,
    rowContent: @Composable (item: T, position: Position, dragHandle: Modifier, isDragging: Boolean) -> Unit,
) {
    val pitchPx = with(LocalDensity.current) { (ReorderableRowHeight + RowGap).toPx() }
    val scope = rememberCoroutineScope()

    // Local working copy; re-seeded when the incoming list changes (including the
    // echo of our own committed order).
    var order by remember(items) { mutableStateOf(items) }
    var draggedKey by remember { mutableStateOf<Any?>(null) }
    // Live translation of the held row from its slot (px); also drives the
    // release settle — re-based onto the row's new slot once the order commits
    // (see onDragEnd), then eased down to zero.
    var dragOffset by remember { mutableFloatStateOf(0f) }
    // The running release/cancel settle, cancelled if a new drag pre-empts it.
    // Purely visual — the order commit (see onDragEnd) never depends on it.
    var settleJob by remember { mutableStateOf<Job?>(null) }

    val draggedIndex = draggedKey?.let { key -> order.indexOfFirst { keyOf(it) == key }.takeIf { it >= 0 } }
    // Whole slots dragged → the slot the held row currently hovers over. Derived
    // so recomposition only fires when the *target slot* actually changes, not on
    // every dragged pixel: dragOffset moves every frame during a drag, but the
    // held row's own translation is already applied in the draw phase via
    // graphicsLayer below, so only the neighbours' shift (which depends on this)
    // needs to recompose, and only when a slot boundary is actually crossed.
    // (Read as a plain val, not `by`, below: a delegated property has a custom
    // getter and Kotlin won't smart-cast it in the `when` over `targetIndex` in
    // the loop, so the derived value is captured into a real local instead.)
    val targetIndex = remember(items, pitchPx) {
        derivedStateOf {
            val idx = draggedKey?.let { key -> order.indexOfFirst { keyOf(it) == key }.takeIf { it >= 0 } }
            idx?.let { (it + (dragOffset / pitchPx).roundToInt()).coerceIn(0, order.lastIndex) }
        }
    }.value

    Column(modifier, verticalArrangement = Arrangement.spacedBy(RowGap)) {
        order.forEachIndexed { index, item ->
            val key = keyOf(item)
            val isDragged = key == draggedKey

            // Slide neighbours by one pitch to open the gap the held row will drop
            // into. Snap (not animate) once idle, so committing the new order — which
            // moves each row's slot — doesn't visibly fight a lingering animation.
            val shift = when {
                draggedIndex == null || targetIndex == null || isDragged -> 0f
                index in (draggedIndex + 1)..targetIndex -> -pitchPx
                index in targetIndex until draggedIndex -> pitchPx
                else -> 0f
            }
            val animatedShift by animateFloatAsState(
                targetValue = shift,
                animationSpec = if (draggedKey != null) spring(stiffness = Spring.StiffnessMediumLow) else snap(),
                label = "reorderShift",
            )

            val dragHandle = Modifier.pointerInput(key) {
                detectDragGestures(
                    onDragStart = {
                        settleJob?.cancel()
                        draggedKey = key
                        dragOffset = 0f
                    },
                    onDrag = { change, amount ->
                        change.consume()
                        dragOffset += amount.y
                    },
                    onDragEnd = {
                        val from = order.indexOfFirst { keyOf(it) == key }
                        if (from < 0) return@detectDragGestures
                        val to = (from + (dragOffset / pitchPx).roundToInt()).coerceIn(0, order.lastIndex)
                        if (to != from) {
                            // Commit synchronously and unconditionally, before the settle
                            // animation below runs — the commit must not depend on that
                            // coroutine reaching its end, or a new drag starting within the
                            // ~160ms settle window would cancel it and silently revert an
                            // already-finished reorder.
                            order = order.toMutableList().apply { add(to, removeAt(from)) }
                            onReorder(order)
                            // The row now lays out at slot `to` instead of `from`; re-base
                            // the live offset onto that new slot (same visual position,
                            // expressed relative to the new one) so the settle below eases
                            // it the rest of the way instead of jumping.
                            dragOffset -= (to - from) * pitchPx
                        }
                        settleJob = scope.launch {
                            // Purely visual from here: ease the held row onto its slot, then
                            // release the drag state. Safe to cancel — the order was already
                            // committed above.
                            Animatable(dragOffset).animateTo(0f, tween(160)) { dragOffset = value }
                            draggedKey = null
                            dragOffset = 0f
                        }
                    },
                    onDragCancel = {
                        settleJob = scope.launch {
                            Animatable(dragOffset).animateTo(0f, tween(160)) { dragOffset = value }
                            draggedKey = null
                            dragOffset = 0f
                        }
                    },
                )
            }

            Box(
                Modifier
                    .height(ReorderableRowHeight)
                    .zIndex(if (isDragged) 1f else 0f)
                    .graphicsLayer {
                        translationY = if (isDragged) dragOffset else animatedShift
                        if (isDragged) {
                            scaleX = 1.02f
                            scaleY = 1.02f
                            shadowElevation = 8.dp.toPx()
                            shape = RoundedCornerShape(20.dp)
                            clip = false
                        }
                    },
            ) {
                rowContent(item, positionOf(index, order.size), dragHandle, isDragged)
            }
        }
    }
}
