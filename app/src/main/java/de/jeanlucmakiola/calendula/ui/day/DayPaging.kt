package de.jeanlucmakiola.calendula.ui.day

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.plus

/** Pages the day pager spans — a little under four centuries, centred on [PAGE_EPOCH]. */
internal const val DAY_PAGE_COUNT: Int = 140_000

private const val EPOCH_PAGE = DAY_PAGE_COUNT / 2

/** The day at [EPOCH_PAGE]; any date works, it only has to stay fixed. */
private val PAGE_EPOCH = LocalDate(2000, 1, 1)

/** The day shown on pager [page]. */
internal fun dayForPage(page: Int): LocalDate =
    PAGE_EPOCH.plus(page - EPOCH_PAGE, DateTimeUnit.DAY)

/** The pager page showing [date]. */
internal fun dayPageFor(date: LocalDate): Int =
    (EPOCH_PAGE + PAGE_EPOCH.daysUntil(date)).coerceIn(0, DAY_PAGE_COUNT - 1)
