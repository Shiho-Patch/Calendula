package de.jeanlucmakiola.calendula.data.calendar

import android.provider.CalendarContract
import de.jeanlucmakiola.calendula.domain.AccessLevel
import de.jeanlucmakiola.calendula.domain.Availability
import de.jeanlucmakiola.calendula.domain.EventForm
import kotlinx.datetime.toJavaLocalDate
import kotlinx.datetime.toJavaLocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

/** Provider-ready DTSTART / DTEND / EVENT_TIMEZONE for an event write. */
internal data class EventWriteTimes(
    val dtStartMillis: Long,
    val dtEndMillis: Long,
    val timezone: String,
)

/**
 * All-day events live at UTC midnights with an exclusive DTEND (the
 * CalendarContract convention — a one-day event ends at the next midnight);
 * timed events resolve their wall-clock values in [zone].
 */
internal fun EventForm.toWriteTimes(zone: ZoneId): EventWriteTimes = if (isAllDay) {
    EventWriteTimes(
        dtStartMillis = start.date.toJavaLocalDate()
            .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        dtEndMillis = end.date.toJavaLocalDate().plusDays(1)
            .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        timezone = "UTC",
    )
} else {
    EventWriteTimes(
        dtStartMillis = start.toJavaLocalDateTime().atZone(zone).toInstant().toEpochMilli(),
        dtEndMillis = end.toJavaLocalDateTime().atZone(zone).toInstant().toEpochMilli(),
        timezone = zone.id,
    )
}

internal fun Availability.toProviderValue(): Int = when (this) {
    Availability.Busy -> CalendarContract.Events.AVAILABILITY_BUSY
    Availability.Free -> CalendarContract.Events.AVAILABILITY_FREE
    Availability.Tentative -> CalendarContract.Events.AVAILABILITY_TENTATIVE
}

internal fun AccessLevel.toProviderValue(): Int = when (this) {
    AccessLevel.Default -> CalendarContract.Events.ACCESS_DEFAULT
    AccessLevel.Confidential -> CalendarContract.Events.ACCESS_CONFIDENTIAL
    AccessLevel.Private -> CalendarContract.Events.ACCESS_PRIVATE
    AccessLevel.Public -> CalendarContract.Events.ACCESS_PUBLIC
}
