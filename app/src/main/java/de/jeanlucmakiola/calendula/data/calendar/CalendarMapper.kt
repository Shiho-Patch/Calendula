package de.jeanlucmakiola.calendula.data.calendar

import android.provider.CalendarContract
import de.jeanlucmakiola.calendula.domain.CalendarSource

internal fun ColumnReader.toCalendarSource(): CalendarSource = CalendarSource(
    id = getLong(CalendarProjection.IDX_ID),
    displayName = getString(CalendarProjection.IDX_DISPLAY_NAME)
        ?: Fallbacks.UNNAMED_CALENDAR,
    accountName = getString(CalendarProjection.IDX_ACCOUNT_NAME).orEmpty(),
    accountType = getString(CalendarProjection.IDX_ACCOUNT_TYPE).orEmpty(),
    color = getInt(CalendarProjection.IDX_COLOR),
    isVisibleInSystem = getInt(CalendarProjection.IDX_VISIBLE) != 0,
    canModifyContents = getInt(CalendarProjection.IDX_ACCESS_LEVEL) >=
        CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR,
)
