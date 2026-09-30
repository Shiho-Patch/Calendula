package de.jeanlucmakiola.calendula.ui.week

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.plus

/** Pages the week pager spans — a little under four centuries, centred on [PAGE_EPOCH]. */
internal const val WEEK_PAGE_COUNT: Int = 20_000

private const val EPOCH_PAGE = WEEK_PAGE_COUNT / 2

/** The week at [EPOCH_PAGE]; any date works, it only has to stay fixed. */
private val PAGE_EPOCH = LocalDate(2000, 1, 3)

/** First day of the week shown on pager [page], with weeks starting on [firstDay]. */
internal fun weekStartForPage(page: Int, firstDay: DayOfWeek): LocalDate =
    PAGE_EPOCH.startOfWeek(firstDay).plus((page - EPOCH_PAGE) * 7, DateTimeUnit.DAY)

/** The pager page whose week contains [date], with weeks starting on [firstDay]. */
internal fun weekPageFor(date: LocalDate, firstDay: DayOfWeek): Int {
    val days = PAGE_EPOCH.startOfWeek(firstDay).daysUntil(date.startOfWeek(firstDay))
    return (EPOCH_PAGE + days / 7).coerceIn(0, WEEK_PAGE_COUNT - 1)
}

/** How a timeline pager cuts the calendar into pages of [dayCount] days. */
sealed interface PageGrid {
    val dayCount: Int
    val pageCount: Int

    /** First day shown on pager [page]. */
    fun startForPage(page: Int): LocalDate

    /** The pager page showing [date]. */
    fun pageFor(date: LocalDate): Int
}

/** Whole weeks, starting on [firstDay]. */
data class WeekGrid(val firstDay: DayOfWeek) : PageGrid {
    override val dayCount: Int get() = 7
    override val pageCount: Int get() = WEEK_PAGE_COUNT
    override fun startForPage(page: Int): LocalDate = weekStartForPage(page, firstDay)
    override fun pageFor(date: LocalDate): Int = weekPageFor(date, firstDay)
}

/** Pages the multi-day pager spans — over three centuries at the widest page. */
internal const val DAYS_PAGE_COUNT: Int = 40_000

private const val DAYS_EPOCH_PAGE = DAYS_PAGE_COUNT / 2

/** A fixed day the multi-day pages are counted from. */
private val DAYS_EPOCH = LocalDate(2000, 1, 1)

/**
 * Runs of [dayCount] days (#193), offset [phase] days from a fixed epoch so any
 * date can start a page. The phase stays put while paging and only changes when
 * a jump moves another date to the first column.
 */
data class DaysGrid(override val dayCount: Int, val phase: Int) : PageGrid {
    override val pageCount: Int get() = DAYS_PAGE_COUNT

    override fun startForPage(page: Int): LocalDate =
        DAYS_EPOCH.plus(phase + (page - DAYS_EPOCH_PAGE) * dayCount, DateTimeUnit.DAY)

    override fun pageFor(date: LocalDate): Int =
        (DAYS_EPOCH_PAGE + Math.floorDiv(DAYS_EPOCH.daysUntil(date) - phase, dayCount))
            .coerceIn(0, DAYS_PAGE_COUNT - 1)

    companion object {
        /** The grid of [dayCount]-day pages on which [date] is a page's first day. */
        fun startingAt(date: LocalDate, dayCount: Int): DaysGrid =
            DaysGrid(dayCount, Math.floorMod(DAYS_EPOCH.daysUntil(date), dayCount))
    }
}

/** Pages the day pager spans — a little under four centuries, centred on [DAY_EPOCH]. */
internal const val DAY_PAGE_COUNT: Int = 140_000

private const val DAY_EPOCH_PAGE = DAY_PAGE_COUNT / 2

/** The day at [DAY_EPOCH_PAGE]; any date works, it only has to stay fixed. */
private val DAY_EPOCH = LocalDate(2000, 1, 1)

/** Single days: every date starts a page of its own. */
data object DayGrid : PageGrid {
    override val dayCount: Int get() = 1
    override val pageCount: Int get() = DAY_PAGE_COUNT

    override fun startForPage(page: Int): LocalDate =
        DAY_EPOCH.plus(page - DAY_EPOCH_PAGE, DateTimeUnit.DAY)

    override fun pageFor(date: LocalDate): Int =
        (DAY_EPOCH_PAGE + DAY_EPOCH.daysUntil(date)).coerceIn(0, DAY_PAGE_COUNT - 1)
}

/**
 * Where the focused date goes when the pager settles on [page] (#184): kept if
 * the page still shows it, otherwise moved by the pages travelled, so it keeps
 * its place in the page — the same weekday a week on.
 */
internal fun PageGrid.focusOnPage(focus: LocalDate, page: Int): LocalDate {
    val start = startForPage(page)
    val end = start.plus(dayCount - 1, DateTimeUnit.DAY)
    if (focus in start..end) return focus
    return focus.plus((page - pageFor(focus)) * dayCount, DateTimeUnit.DAY).coerceIn(start, end)
}
