package de.jeanlucmakiola.calendula.ui.month

import androidx.annotation.StringRes
import de.jeanlucmakiola.calendula.R

/**
 * How the Month view lays itself out (#38, #53).
 *
 * One setting rather than two independent toggles: "vertical scrolling" and
 * "month plus agenda" would otherwise multiply into four combinations to build
 * and test, most of which nobody asked for.
 *
 * [Split] does **not** replace or disable the Agenda view — that stays a forward
 * multi-day window with its own range model, while the split pane lists a single
 * selected day.
 */
enum class MonthViewStyle {
    /** Month pages, swiped left/right. Full event bars and pills per day. */
    Paged,

    /**
     * One uninterrupted vertical stream of weeks. Each date appears exactly once,
     * which is the point — paging repeats a boundary week at both ends (#38).
     */
    Continuous,

    /** Compact dots-only grid over a list of the selected day's events (#53). */
    Split,
}

@get:StringRes
val MonthViewStyle.labelRes: Int
    get() = when (this) {
        MonthViewStyle.Paged -> R.string.month_style_paged
        MonthViewStyle.Continuous -> R.string.month_style_continuous
        MonthViewStyle.Split -> R.string.month_style_split
    }

@get:StringRes
val MonthViewStyle.descriptionRes: Int
    get() = when (this) {
        MonthViewStyle.Paged -> R.string.month_style_paged_summary
        MonthViewStyle.Continuous -> R.string.month_style_continuous_summary
        MonthViewStyle.Split -> R.string.month_style_split_summary
    }
