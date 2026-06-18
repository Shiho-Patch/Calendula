package de.jeanlucmakiola.calendula.domain.ics

import de.jeanlucmakiola.calendula.domain.Availability
import de.jeanlucmakiola.calendula.domain.EventStatus
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toInstant
import kotlin.time.Instant

/**
 * A `VEVENT` parsed from an `.ics` file — the read-side mirror of [IcsEvent],
 * but [uid] is nullable (an incoming event may carry none; the insert layer
 * then assigns one). Times are absolute instants; [isAllDay]/[zoneId] mirror
 * how the writer encoded them.
 */
data class ParsedIcsEvent(
    val uid: String?,
    val summary: String,
    val start: Instant,
    val end: Instant,
    val isAllDay: Boolean,
    val zoneId: String,
    val recurrenceRule: String? = null,
    val location: String? = null,
    val description: String? = null,
    val reminderMinutes: List<Int> = emptyList(),
    val status: EventStatus = EventStatus.Confirmed,
    val availability: Availability = Availability.Busy,
    val calendarName: String? = null,
)

/** Things the parser dropped rather than failing — surfaced in the import report. */
enum class IcsParseWarning {
    /** A `RECURRENCE-ID` override occurrence (not modelled; only masters import). */
    ModifiedOccurrenceSkipped,

    /** A `VEVENT` with no parseable `DTSTART`. */
    EventWithoutStartSkipped,

    /** `ATTENDEE` rows were present; Calendula doesn't import attendees. */
    AttendeesIgnored,

    /** A `TZID` couldn't be resolved against the device tz database (used local zone). */
    UnknownTimezone,
}

data class IcsParseResult(
    val events: List<ParsedIcsEvent>,
    val warnings: Set<IcsParseWarning>,
)

/** Outcome of a bulk `.ics` import into one calendar. */
data class IcsImportSummary(val imported: Int, val skippedDuplicate: Int)

/**
 * Hand-rolled RFC 5545 reader, the inverse of [IcsWriter]. Pure and
 * JVM-testable. Liberal-in/strict-out: unknown properties are ignored, a single
 * malformed `VEVENT` is skipped (not fatal), and unsupported constructs
 * (`RECURRENCE-ID`, attendees, unresolved `TZID`) are reported as [warnings]
 * rather than silently dropped. `VTIMEZONE` blocks are skipped — a `TZID` is
 * resolved against the OS tz database instead ([deviceZone] is the fallback).
 */
class IcsParser(private val deviceZone: TimeZone = TimeZone.currentSystemDefault()) {

    fun parse(text: String): IcsParseResult {
        val lines = unfoldLines(text)
        val events = mutableListOf<ParsedIcsEvent>()
        val warnings = mutableSetOf<IcsParseWarning>()
        var calendarName: String? = null

        var i = 0
        while (i < lines.size) {
            val line = parseContentLine(lines[i])
            if (line == null) { i++; continue }
            when {
                line.isBegin("VEVENT") -> {
                    val end = indexOfEnd(lines, i + 1, "VEVENT")
                    parseVevent(lines.subList(i + 1, end), calendarName, warnings)
                        ?.let(events::add)
                    i = end + 1
                }
                line.isBegin("VTIMEZONE") -> {
                    // Skipped wholesale; TZIDs resolve against the OS tz database.
                    i = indexOfEnd(lines, i + 1, "VTIMEZONE") + 1
                }
                line.name == "X-WR-CALNAME" -> {
                    calendarName = unescapeText(line.value).trim().ifEmpty { null }
                    i++
                }
                else -> i++
            }
        }
        return IcsParseResult(events, warnings)
    }

    private fun parseVevent(
        body: List<String>,
        fileCalendarName: String?,
        warnings: MutableSet<IcsParseWarning>,
    ): ParsedIcsEvent? {
        var uid: String? = null
        var summary = ""
        var dtStart: IcsDateTime? = null
        var dtEnd: IcsDateTime? = null
        var duration: String? = null
        var rrule: String? = null
        var location: String? = null
        var description: String? = null
        var status = EventStatus.Confirmed
        var availability = Availability.Busy
        var calendarName = fileCalendarName
        val reminders = mutableListOf<Int>()
        var skipAsOverride = false

        var i = 0
        while (i < body.size) {
            val line = parseContentLine(body[i])
            if (line == null) { i++; continue }
            when (line.name) {
                "BEGIN" -> if (line.value.trim().equals("VALARM", true)) {
                    val end = indexOfEnd(body, i + 1, "VALARM")
                    parseAlarmMinutes(body.subList(i + 1, end))?.let(reminders::add)
                    i = end + 1
                    continue
                }
                "UID" -> uid = line.value.trim().ifEmpty { null }
                "SUMMARY" -> summary = unescapeText(line.value)
                "DTSTART" -> dtStart = parseIcsDateTime(line, warnings)
                "DTEND" -> dtEnd = parseIcsDateTime(line, warnings)
                "DURATION" -> duration = line.value.trim()
                "RRULE" -> rrule = line.value.trim().ifEmpty { null }
                "LOCATION" -> location = unescapeText(line.value).ifEmpty { null }
                "DESCRIPTION" -> description = unescapeText(line.value).ifEmpty { null }
                "STATUS" -> status = mapIcsStatus(line.value)
                "TRANSP" -> availability =
                    if (line.value.trim().equals("TRANSPARENT", true)) Availability.Free
                    else Availability.Busy
                "RECURRENCE-ID" -> skipAsOverride = true
                "ATTENDEE" -> warnings.add(IcsParseWarning.AttendeesIgnored)
                "X-CALENDULA-CALENDAR" ->
                    calendarName = unescapeText(line.value).trim().ifEmpty { calendarName }
            }
            i++
        }

        if (skipAsOverride) {
            warnings.add(IcsParseWarning.ModifiedOccurrenceSkipped)
            return null
        }
        val start = dtStart ?: run {
            warnings.add(IcsParseWarning.EventWithoutStartSkipped)
            return null
        }
        val end = dtEnd
            ?: duration?.let {
                start.copy(
                    instant = Instant.fromEpochMilliseconds(
                        start.instant.toEpochMilliseconds() + parseRfc2445DurationMillis(it),
                    ),
                )
            }
            ?: start
        return ParsedIcsEvent(
            uid = uid,
            summary = summary,
            start = start.instant,
            end = end.instant,
            isAllDay = start.isAllDay,
            zoneId = start.zoneId,
            recurrenceRule = rrule,
            location = location,
            description = description,
            reminderMinutes = reminders.distinct(),
            status = status,
            availability = availability,
            calendarName = calendarName,
        )
    }

    /** A VALARM's lead time in minutes before start, or null if not a usable relative trigger. */
    private fun parseAlarmMinutes(body: List<String>): Int? {
        val trigger = body.asSequence()
            .mapNotNull { parseContentLine(it) }
            .firstOrNull { it.name == "TRIGGER" }
            ?: return null
        // Absolute (DATE-TIME) triggers can't be expressed as a lead time.
        if (trigger.params["VALUE"].equals("DATE-TIME", true)) return null
        val millis = parseRfc2445DurationMillis(trigger.value)
        // Negative = before start (the normal case) → positive lead minutes.
        return (-millis / 60_000L).toInt().coerceAtLeast(0)
    }

    private fun parseIcsDateTime(line: IcsContentLine, warnings: MutableSet<IcsParseWarning>): IcsDateTime? {
        val raw = line.value.trim()
        val isDate = line.params["VALUE"].equals("DATE", true) ||
            (raw.length == 8 && !raw.contains('T'))
        if (isDate) {
            val date = parseBasicDate(raw) ?: return null
            return IcsDateTime(date.atStartOfDayIn(TimeZone.UTC), isAllDay = true, zoneId = "UTC")
        }
        val isUtc = raw.endsWith("Z")
        val ldt = parseBasicDateTime(raw.removeSuffix("Z")) ?: return null
        if (isUtc) return IcsDateTime(ldt.toInstant(TimeZone.UTC), isAllDay = false, zoneId = "UTC")

        val tzid = line.params["TZID"]
        val resolved = tzid?.let { id -> runCatching { TimeZone.of(id) }.getOrNull()?.let { id to it } }
        if (tzid != null && resolved == null) warnings.add(IcsParseWarning.UnknownTimezone)
        val (zoneId, zone) = resolved ?: (deviceZone.id to deviceZone)
        return IcsDateTime(ldt.toInstant(zone), isAllDay = false, zoneId = zoneId)
    }

    private data class IcsDateTime(val instant: Instant, val isAllDay: Boolean, val zoneId: String)

    private companion object {
        fun IcsContentLine.isBegin(component: String) =
            name == "BEGIN" && value.trim().equals(component, true)

        /** Index of the matching `END:<component>` at/after [from], or list end. */
        fun indexOfEnd(lines: List<String>, from: Int, component: String): Int {
            var i = from
            while (i < lines.size) {
                val line = parseContentLine(lines[i])
                if (line != null && line.name == "END" &&
                    line.value.trim().equals(component, true)
                ) {
                    return i
                }
                i++
            }
            return lines.size
        }

        fun mapIcsStatus(value: String): EventStatus = when (value.trim().uppercase()) {
            "TENTATIVE" -> EventStatus.Tentative
            "CANCELLED" -> EventStatus.Cancelled
            else -> EventStatus.Confirmed
        }

        fun parseBasicDate(s: String): LocalDate? = runCatching {
            LocalDate(s.substring(0, 4).toInt(), s.substring(4, 6).toInt(), s.substring(6, 8).toInt())
        }.getOrNull()

        fun parseBasicDateTime(s: String): LocalDateTime? = runCatching {
            val date = LocalDate(
                s.substring(0, 4).toInt(), s.substring(4, 6).toInt(), s.substring(6, 8).toInt(),
            )
            // Format is YYYYMMDD 'T' HHMMSS; seconds optional.
            val time = LocalTime(
                s.substring(9, 11).toInt(),
                s.substring(11, 13).toInt(),
                if (s.length >= 15) s.substring(13, 15).toInt() else 0,
            )
            LocalDateTime(date, time)
        }.getOrNull()
    }
}
