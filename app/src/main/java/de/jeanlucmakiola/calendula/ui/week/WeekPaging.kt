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
