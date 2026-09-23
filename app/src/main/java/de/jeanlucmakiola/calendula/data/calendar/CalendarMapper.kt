package de.jeanlucmakiola.calendula.data.calendar

import android.provider.CalendarContract
import de.jeanlucmakiola.calendula.domain.CalendarSource

internal fun ColumnReader.toCalendarSource(): CalendarSource {
    val accountType = getString(CalendarProjection.IDX_ACCOUNT_TYPE).orEmpty()
    val isLocal = accountType == CalendarContract.ACCOUNT_TYPE_LOCAL
    return CalendarSource(
        id = getLong(CalendarProjection.IDX_ID),
        // Blank when the provider row has no name: the placeholder is a display
        // string and belongs to the UI, where it can be localised (#329).
        displayName = getString(CalendarProjection.IDX_DISPLAY_NAME).orEmpty(),
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
        // A special-dates mirror calendar, recognised by its durable CAL_SYNC2
        // marker (only meaningful on the local calendars the app owns). This is
        // the source of truth for the editor lock — independent of any stored
        // preference that a backup restore could have wiped.
        isManaged = isLocal &&
            getString(CalendarProjection.IDX_MANAGED_MARKER)
                ?.startsWith(CalendarProjection.MANAGED_MARKER_PREFIX) == true,
        // NULL is treated as syncing — the harmless default.
        syncsEvents = isNull(CalendarProjection.IDX_SYNC_EVENTS) ||
            getInt(CalendarProjection.IDX_SYNC_EVENTS) != 0,
    )
}
