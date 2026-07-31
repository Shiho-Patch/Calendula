package de.jeanlucmakiola.calendula.ui.common

import androidx.annotation.StringRes
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import de.jeanlucmakiola.calendula.R

/**
 * How tall one hour is drawn in the week and day timelines (#56).
 *
 * One shared setting for both views: they are the same grid at different widths,
 * and a scale that only applied to one of them would read as a bug.
 *
 * [FitDay] is the answer to the actual complaint behind the issue — on a tall
 * phone the default scale shows about half a day, so a whole week can hide
 * appointments below the fold. It derives the hour height from the timeline's
 * own viewport instead of a fixed value.
 */
enum class TimelineScale {
    /** Whole day in one screen: the hour height follows the viewport. */
    FitDay,

    /** Denser than the default, still a fixed height. */
    Compact,

    /** The historical 56dp scale. */
    Regular,

    /** Roomier blocks, more scrolling. */
    Comfortable,
}

/** The scale the timelines draw at, from the `timelineScale` preference. */
val LocalTimelineScale = staticCompositionLocalOf { TimelineScale.Regular }

/**
 * Hour height for this scale. [viewportHeight] is the visible height of the
 * scrolling timeline and is only consulted by [TimelineScale.FitDay].
 *
 * The fit-day result is clamped: below [FIT_DAY_MIN] the 24 gutter labels stop
 * being legible, and above [FIT_DAY_MAX] a short landscape day would stretch its
 * blocks absurdly. On a screen too short for the whole day the clamp wins and
 * the timeline still scrolls a little — honest, rather than unreadable.
 */
fun TimelineScale.hourHeight(viewportHeight: Dp): Dp = when (this) {
    TimelineScale.FitDay -> (viewportHeight / 24f).coerceIn(FIT_DAY_MIN, FIT_DAY_MAX)
    TimelineScale.Compact -> 40.dp
    TimelineScale.Regular -> 56.dp
    TimelineScale.Comfortable -> 80.dp
}

/**
 * Shortest an event block may render, as a fraction of an hour. Blocks keep a
 * floor so a 15-minute event stays tappable, but the floor scales with the hour
 * height — a fixed 24dp would swallow half an hour once zoomed out and make
 * short events overlap their neighbours.
 */
const val MIN_EVENT_FRACTION = 26f / 60f

/**
 * Narrowest an event block may be and still wrap its title over several lines.
 *
 * Wrapping is driven by the block's height, so a tall block on a lane-split
 * column would otherwise stack two or three characters per line — "Da/ily",
 * "Fa/rmer/s…" — which reads worse than one ellipsised line. A full week column
 * clears this on any phone; a split one never does, while the day view's much
 * wider columns keep wrapping even several lanes deep.
 */
val MIN_TITLE_WRAP_WIDTH = 36.dp

/** Smallest hour height [TimelineScale.FitDay] will resolve to. */
val FIT_DAY_MIN = 24.dp

/** Largest hour height [TimelineScale.FitDay] will resolve to. */
val FIT_DAY_MAX = 96.dp

@get:StringRes
val TimelineScale.labelRes: Int
    get() = when (this) {
        TimelineScale.FitDay -> R.string.timeline_scale_fit_day
        TimelineScale.Compact -> R.string.timeline_scale_compact
        TimelineScale.Regular -> R.string.timeline_scale_regular
        TimelineScale.Comfortable -> R.string.timeline_scale_comfortable
    }

@get:StringRes
val TimelineScale.descriptionRes: Int
    get() = when (this) {
        TimelineScale.FitDay -> R.string.timeline_scale_fit_day_summary
        TimelineScale.Compact -> R.string.timeline_scale_compact_summary
        TimelineScale.Regular -> R.string.timeline_scale_regular_summary
        TimelineScale.Comfortable -> R.string.timeline_scale_comfortable_summary
    }
