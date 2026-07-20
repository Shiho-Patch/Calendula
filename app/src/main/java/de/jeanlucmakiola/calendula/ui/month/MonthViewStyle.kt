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
     * Months stacked into one vertical scroll, each a self-contained block under
     * its own sticky header: a block shows only its own days, and whitespace
     * separates it from the next (#38).
     */
    Continuous,

    /**
     * [Continuous] with the seams taken out: one uninterrupted stream of weeks
     * where months flow into each other. Each date appears exactly once — paging
     * repeats a boundary week at both ends — and the 1st names its month, the
     * only marker of where one ends.
     */
    Dense,

    /** Compact dots-only grid over a list of the selected day's events (#53). */
    Split,
}

/** Both vertically scrolling styles, which share a data window and a list state. */
val MonthViewStyle.isScrolling: Boolean
    get() = this == MonthViewStyle.Continuous || this == MonthViewStyle.Dense

@get:StringRes
val MonthViewStyle.labelRes: Int
    get() = when (this) {
        MonthViewStyle.Paged -> R.string.month_style_paged
        MonthViewStyle.Continuous -> R.string.month_style_continuous
        MonthViewStyle.Dense -> R.string.month_style_dense
        MonthViewStyle.Split -> R.string.month_style_split
    }

@get:StringRes
val MonthViewStyle.descriptionRes: Int
    get() = when (this) {
        MonthViewStyle.Paged -> R.string.month_style_paged_summary
        MonthViewStyle.Continuous -> R.string.month_style_continuous_summary
        MonthViewStyle.Dense -> R.string.month_style_dense_summary
        MonthViewStyle.Split -> R.string.month_style_split_summary
    }
