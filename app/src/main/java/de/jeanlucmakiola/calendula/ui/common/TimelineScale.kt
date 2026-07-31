package de.jeanlucmakiola.calendula.ui.common

import androidx.annotation.StringRes
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
 *
 * [Custom] is what a pinch on the timeline leaves behind. The presets are the
 * settings-screen vocabulary; a pinch is a direct manipulation and should be
 * able to land anywhere between them, so it gets its own case rather than
 * snapping to the nearest named step.
 */
sealed interface TimelineScale {

    /** Whole day in one screen: the hour height follows the viewport. */
    data object FitDay : TimelineScale

    /** Denser than the default, still a fixed height. */
    data object Compact : TimelineScale

    /** The historical 56dp scale. */
    data object Regular : TimelineScale

    /** Roomier blocks, more scrolling. */
    data object Comfortable : TimelineScale

    /** A height the user pinched to. Build it through [custom], which clamps. */
    data class Custom(val hourHeight: Dp) : TimelineScale

    companion object {
        /** The named steps the settings picker offers, coarse to roomy. */
        val presets: List<TimelineScale> = listOf(FitDay, Compact, Regular, Comfortable)

        /**
         * A pinched hour height. The floor a pinch actually stops at depends on
         * the viewport and is applied when the height is resolved (see
         * [hourHeight]); this only holds a stored value to something sane.
         */
        fun custom(hourHeight: Dp): Custom =
            Custom(hourHeight.coerceIn(MIN_STORED_HOUR_HEIGHT, MAX_PINCH_HOUR_HEIGHT))
    }
}

/**
 * Hour height for this scale. [viewportHeight] is the visible height of the
 * scrolling timeline and is only consulted by [TimelineScale.FitDay].
 *
 * The fit-day result is clamped: below [FIT_DAY_MIN] the 24 gutter labels stop
 * being legible, and above [FIT_DAY_MAX] a short landscape day would stretch its
 * blocks absurdly. On a screen too short for the whole day the clamp wins and
 * the timeline still scrolls a little — honest, rather than unreadable.
 *
 * A pinched height is held to [fillHourHeight] here and not only in the gesture,
 * because the viewport can change under a height that was already stored: pinch
 * all the way out in landscape and the same value would leave portrait with dead
 * space under midnight.
 */
fun TimelineScale.hourHeight(viewportHeight: Dp): Dp = when (this) {
    TimelineScale.FitDay -> (viewportHeight / 24f).coerceIn(FIT_DAY_MIN, FIT_DAY_MAX)
    TimelineScale.Compact -> 40.dp
    TimelineScale.Regular -> 56.dp
    TimelineScale.Comfortable -> 80.dp
    is TimelineScale.Custom -> this.hourHeight
        .coerceAtMost(MAX_PINCH_HOUR_HEIGHT)
        .coerceAtLeast(fillHourHeight(viewportHeight))
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

/**
 * The hour height at which all 24 hours exactly fill [viewportHeight] — how far
 * out a pinch can zoom.
 *
 * Below it there is no more day left to uncover, so the only thing shrinking
 * further buys is empty space under midnight. Zooming out means "show me more of
 * the day", and once the whole day is on screen that request is answered.
 */
fun fillHourHeight(viewportHeight: Dp): Dp = viewportHeight / 24f

/**
 * Ceiling for a pinch, deliberately far above [TimelineScale.Comfortable]: a
 * pinch is a deliberate act, so someone zooming in on a single busy afternoon
 * should be allowed to go further than any preset offers.
 */
val MAX_PINCH_HOUR_HEIGHT = 240.dp

/**
 * Floor for a *stored* pinched height, which only has to stay sane enough to be
 * re-clamped against whatever viewport it is later shown in. The floor a pinch
 * stops at is [fillHourHeight].
 */
private val MIN_STORED_HOUR_HEIGHT = 1.dp

@get:StringRes
val TimelineScale.labelRes: Int
    get() = when (this) {
        TimelineScale.FitDay -> R.string.timeline_scale_fit_day
        TimelineScale.Compact -> R.string.timeline_scale_compact
        TimelineScale.Regular -> R.string.timeline_scale_regular
        TimelineScale.Comfortable -> R.string.timeline_scale_comfortable
        is TimelineScale.Custom -> R.string.timeline_scale_custom
    }

@get:StringRes
val TimelineScale.descriptionRes: Int
    get() = when (this) {
        TimelineScale.FitDay -> R.string.timeline_scale_fit_day_summary
        TimelineScale.Compact -> R.string.timeline_scale_compact_summary
        TimelineScale.Regular -> R.string.timeline_scale_regular_summary
        TimelineScale.Comfortable -> R.string.timeline_scale_comfortable_summary
        is TimelineScale.Custom -> R.string.timeline_scale_custom_summary
    }

/** Marks a stored custom height; the rest of the value is its dp. */
private const val CUSTOM_PREFIX = "custom:"

/**
 * Stored names for the presets. Spelled out rather than taken from `toString()`
 * so R8 can't rename them out from under an existing install — and they match
 * the enum names this used to be, so a value written before the pinch existed
 * still reads back as the same preset.
 */
private val PRESET_NAMES: Map<TimelineScale, String> = mapOf(
    TimelineScale.FitDay to "FitDay",
    TimelineScale.Compact to "Compact",
    TimelineScale.Regular to "Regular",
    TimelineScale.Comfortable to "Comfortable",
)

/**
 * Serialise for the `timeline_scale` preference; see [parseTimelineScale].
 *
 * Spelled out rather than an `else` into [PRESET_NAMES], so a preset added later
 * without a stored name is a compile error here instead of a crash the first
 * time someone picks it.
 */
fun TimelineScale.storageValue(): String = when (this) {
    is TimelineScale.Custom -> CUSTOM_PREFIX + hourHeight.value
    TimelineScale.FitDay,
    TimelineScale.Compact,
    TimelineScale.Regular,
    TimelineScale.Comfortable,
    -> PRESET_NAMES.getValue(this)
}

/**
 * Parse a stored scale. Anything unrecognised — a null key, a name from a
 * future release, a truncated custom height — falls back to
 * [TimelineScale.Regular] rather than throwing, like the other enum prefs.
 */
fun parseTimelineScale(stored: String?): TimelineScale = when {
    stored == null -> TimelineScale.Regular
    stored.startsWith(CUSTOM_PREFIX) -> stored.removePrefix(CUSTOM_PREFIX).toFloatOrNull()
        ?.let { TimelineScale.custom(it.dp) }
        ?: TimelineScale.Regular
    else -> PRESET_NAMES.entries.firstOrNull { it.value == stored }?.key ?: TimelineScale.Regular
}
