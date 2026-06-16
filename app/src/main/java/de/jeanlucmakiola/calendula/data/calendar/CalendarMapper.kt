package de.jeanlucmakiola.calendula.data.calendar

import android.provider.CalendarContract
import de.jeanlucmakiola.calendula.domain.CalendarSource

internal fun ColumnReader.toCalendarSource(): CalendarSource {
    val accountType = getString(CalendarProjection.IDX_ACCOUNT_TYPE).orEmpty()
    val isLocal = accountType == CalendarContract.ACCOUNT_TYPE_LOCAL
    return CalendarSource(
        id = getLong(CalendarProjection.IDX_ID),
        displayName = getString(CalendarProjection.IDX_DISPLAY_NAME)
            ?: Fallbacks.UNNAMED_CALENDAR,
        accountName = getString(CalendarProjection.IDX_ACCOUNT_NAME).orEmpty(),
        accountType = accountType,
        color = getInt(CalendarProjection.IDX_COLOR),
        isVisibleInSystem = getInt(CalendarProjection.IDX_VISIBLE) != 0,
        canModifyContents = getInt(CalendarProjection.IDX_ACCESS_LEVEL) >=
            CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR,
        isLocal = isLocal,
        // CAL_SYNC1 holds the sync token for synced rows, so only treat it as a
        // user description on the local calendars the app owns.
        description = if (isLocal) {
            getString(CalendarProjection.IDX_DESCRIPTION)?.takeIf { it.isNotBlank() }
        } else {
            null
        },
    )
}
