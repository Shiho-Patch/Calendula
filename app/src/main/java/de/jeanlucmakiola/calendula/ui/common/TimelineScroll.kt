package de.jeanlucmakiola.calendula.ui.common

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlin.math.roundToInt

/**
 * Where 00:00 sat in the last timeline shown, measured from the top of its
 * view's content, so the next timeline view opens on the same hours (#184): an
 * event in both keeps its place and only grows sideways.
 */
class TimelineScrollMemory {
    var midnightY: Float? = null
}

val LocalTimelineScroll = staticCompositionLocalOf<TimelineScrollMemory?> { null }

/** A timeline's scroll, and where it opens before it can scroll there. */
@Stable
class TimelineScroll(val state: ScrollState, noonPending: Boolean, private val openingHour: Int?) {
    /** Whether it is still to be put on noon, which it only can be once laid out. */
    var noonPending by mutableStateOf(noonPending)
        internal set

    /** The top of what it shows, or will once open, over [content] pixels in a [viewport] pixels high. */
    fun top(viewport: Float, content: Float): Float =
        if (noonPending) {
            (openingHour?.let { it * content / 24f } ?: (content - viewport) / 2f)
                .coerceIn(0f, (content - viewport).coerceAtLeast(0f))
        } else state.value.toFloat()
}

val LocalOpeningTimeline = staticCompositionLocalOf<TimelineScroll?> { null }

/**
 * The scroll state of a timeline whose scrolling area starts [timelineTop] below
 * its view's content top. Opens where the last timeline left off, or on noon the
 * first time.
 */
@Composable
fun rememberTimelineScrollState(timelineTop: Dp): TimelineScroll {
    val memory = LocalTimelineScroll.current
    val density = LocalDensity.current
    val zoom = LocalTimelineZoom.current
    val topPx = with(density) { timelineTop.toPx() }
    val remembered = memory?.midnightY
    val scrollState = rememberScrollState(
        initial = remembered?.let { (topPx - it).roundToInt().coerceAtLeast(0) } ?: 0,
    )
    val scroll = remember(scrollState) {
        TimelineScroll(
            scrollState,
            noonPending = remembered == null,
            openingHour = zoom.hours.takeIf { it.enabled }?.startHour,
        )
    }
    val currentTop by rememberUpdatedState(topPx)
    var firstOpen by remember(scrollState) { mutableStateOf(true) }
    LaunchedEffect(scrollState, zoom, zoom.hours) {
        if (firstOpen && remembered != null) {
            firstOpen = false
            return@LaunchedEffect
        }
        firstOpen = false
        val startHour = zoom.open()
        if (startHour == null) {
            snapshotFlow { scrollState.maxValue }.first { it > 0 }
            scrollState.scrollTo(scrollState.maxValue / 2)
        } else {
            val (target, hourHeight) = snapshotFlow {
                val viewport = scrollState.viewportSize
                val hourHeight = zoom.scale.hourHeight(with(density) { viewport.toDp() })
                val hourPx = with(density) { hourHeight.toPx() }
                startHourScroll(scrollState.maxValue, viewport, startHour, hourPx)?.let { it to hourHeight }
            }.filterNotNull().first()
            scrollState.scrollTo(target)
            zoom.holdFit(hourHeight)
        }
        scroll.noonPending = false
    }
    if (memory != null) {
        LaunchedEffect(scrollState, memory) {
            snapshotFlow { currentTop - scrollState.value }.collect { memory.midnightY = it }
        }
    }
    return scroll
}
