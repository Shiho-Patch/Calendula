package de.jeanlucmakiola.calendula.ui.common

import de.jeanlucmakiola.calendula.domain.EventInstance
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * Stand-in events for the view previews. A preview has no business querying the
 * provider for a thumbnail, so every chooser renders the same invented week.
 *
 * Colours are raw ARGB on purpose: that is what the provider hands out for an
 * event, so a theme token here would misrepresent what the views actually draw.
 */
private val SAMPLE_COLORS = listOf(
    0xFF3F7BD4.toInt(),
    0xFFCE5B4C.toInt(),
    0xFF4E9A6A.toInt(),
    0xFF8A63C7.toInt(),
)

private val SAMPLE_TITLES = listOf(
    "Standup",
    "Lunch",
    "Review",
    "Gym",
    "Call",
    "Workshop",
    "Dentist",
    "Trip",
)

/** Hands out [EventInstance]s with running ids and cycling sample titles/colours. */
private class SampleEvents(private val zone: TimeZone) {
    private var id = 0L

    fun timed(
        day: LocalDate,
        hour: Int,
        minute: Int = 0,
        lengthMinutes: Int,
        colorIndex: Int,
    ): EventInstance {
        val start = day.atTime(hour, minute).toInstant(zone)
        return build(
            start = start,
            end = start + lengthMinutes.minutes,
            isAllDay = false,
            colorIndex = colorIndex,
        )
    }

    fun allDay(from: LocalDate, days: Int, colorIndex: Int): EventInstance = build(
        // All-day events sit at UTC midnights with an exclusive end.
        start = from.atTime(0, 0).toInstant(TimeZone.UTC),
        end = from.plus(days, DateTimeUnit.DAY).atTime(0, 0).toInstant(TimeZone.UTC),
        isAllDay = true,
        colorIndex = colorIndex,
    )

    private fun build(
        start: Instant,
        end: Instant,
        isAllDay: Boolean,
        colorIndex: Int,
    ): EventInstance {
        val next = ++id
        return EventInstance(
            instanceId = next,
            eventId = next,
            calendarId = 1L,
            title = SAMPLE_TITLES[(next.toInt() - 1) % SAMPLE_TITLES.size],
            start = start,
            end = end,
            isAllDay = isAllDay,
            color = SAMPLE_COLORS[colorIndex % SAMPLE_COLORS.size],
            location = null,
        )
    }
}

/**
 * A month's worth of stand-ins, spread so most weeks carry something, with one
 * multi-day bar bridging cells and a busy today for the split pane.
 */
internal fun sampleMonthEvents(
    firstOfMonth: LocalDate,
    today: LocalDate,
    zone: TimeZone,
): List<EventInstance> {
    val sample = SampleEvents(zone)
    return buildList {
        add(sample.allDay(firstOfMonth.plus(9, DateTimeUnit.DAY), days = 3, colorIndex = 2))
        add(sample.timed(firstOfMonth.plus(1, DateTimeUnit.DAY), 9, lengthMinutes = 60, colorIndex = 0))
        add(sample.timed(firstOfMonth.plus(4, DateTimeUnit.DAY), 14, lengthMinutes = 120, colorIndex = 1))
        add(sample.timed(firstOfMonth.plus(7, DateTimeUnit.DAY), 11, lengthMinutes = 60, colorIndex = 3))
        add(sample.timed(firstOfMonth.plus(15, DateTimeUnit.DAY), 10, lengthMinutes = 60, colorIndex = 0))
        add(sample.timed(firstOfMonth.plus(18, DateTimeUnit.DAY), 16, lengthMinutes = 60, colorIndex = 2))
        add(sample.timed(firstOfMonth.plus(22, DateTimeUnit.DAY), 8, lengthMinutes = 120, colorIndex = 1))
        add(sample.timed(firstOfMonth.plus(25, DateTimeUnit.DAY), 13, lengthMinutes = 60, colorIndex = 3))
        // Today, so the split pane's list and the grid's dots both have content.
        add(sample.timed(today, 9, lengthMinutes = 60, colorIndex = 0))
        add(sample.timed(today, 12, lengthMinutes = 60, colorIndex = 1))
        add(sample.timed(today, 15, lengthMinutes = 120, colorIndex = 3))
    }
}

/**
 * Stand-ins for a timeline preview over [days]. [anchor] — the day the preview
 * is centred on — gets the busy set, including one overlapping pair so the
 * side-by-side lane layout shows; the rest of the week is sparser. A multi-day
 * all-day bar is only added when there is more than one column to span.
 */
internal fun sampleTimelineEvents(
    days: List<LocalDate>,
    anchor: LocalDate,
    zone: TimeZone,
): List<EventInstance> {
    val sample = SampleEvents(zone)
    return buildList {
        if (days.size > 1) {
            add(sample.allDay(days[days.size / 2], days = 2, colorIndex = 2))
        } else {
            add(sample.allDay(anchor, days = 1, colorIndex = 2))
        }
        // The anchor day: enough to fill the column, with 09:30–11:00 running
        // under 09:00–10:00 so the overlap resolves into two lanes.
        add(sample.timed(anchor, 9, lengthMinutes = 60, colorIndex = 0))
        add(sample.timed(anchor, 9, minute = 30, lengthMinutes = 90, colorIndex = 1))
        add(sample.timed(anchor, 13, lengthMinutes = 120, colorIndex = 3))
        days.filterNot { it == anchor }.forEachIndexed { index, day ->
            // Skip every third day so the week doesn't read as a solid block.
            if (index % 3 == 2) return@forEachIndexed
            add(
                sample.timed(
                    day = day,
                    hour = 8 + (index * 3) % 9,
                    lengthMinutes = if (index % 2 == 0) 60 else 90,
                    colorIndex = index,
                ),
            )
        }
    }
}

/**
 * Stand-ins for the agenda preview: a forward-looking fortnight where the first
 * days are busy and later ones thin out, so the grouped-by-day list shows both
 * a full day and the gaps between days.
 */
internal fun sampleAgendaEvents(today: LocalDate, zone: TimeZone): List<EventInstance> {
    val sample = SampleEvents(zone)
    return buildList {
        add(sample.timed(today, 9, lengthMinutes = 60, colorIndex = 0))
        add(sample.timed(today, 12, minute = 30, lengthMinutes = 60, colorIndex = 1))
        add(sample.timed(today, 16, lengthMinutes = 90, colorIndex = 3))
        val tomorrow = today.plus(1, DateTimeUnit.DAY)
        add(sample.allDay(tomorrow, days = 2, colorIndex = 2))
        add(sample.timed(tomorrow, 10, lengthMinutes = 120, colorIndex = 1))
        add(sample.timed(today.plus(3, DateTimeUnit.DAY), 14, lengthMinutes = 60, colorIndex = 0))
        add(sample.timed(today.plus(6, DateTimeUnit.DAY), 11, lengthMinutes = 60, colorIndex = 3))
        add(sample.timed(today.plus(10, DateTimeUnit.DAY), 18, lengthMinutes = 120, colorIndex = 2))
    }
}
