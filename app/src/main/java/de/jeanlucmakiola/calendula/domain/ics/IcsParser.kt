package de.jeanlucmakiola.calendula.domain.ics

import de.jeanlucmakiola.calendula.domain.Availability
import de.jeanlucmakiola.calendula.domain.EventStatus
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.number
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.math.ceil
import kotlin.time.Instant

/** Milliseconds in a calendar day; all-day times are UTC midnights, so this is exact. */
private const val DAY_MILLIS = 86_400_000L
private const val MINUTES_PER_DAY = 1_440

/**
 * A `VEVENT` parsed from an `.ics` file — the read-side mirror of [IcsEvent],
 * but [uid] is nullable (an incoming event may carry none; the insert layer
 * then assigns one). Times are absolute instants; [isAllDay]/[zoneId] mirror
 * how the writer encoded them.
 *
 * [reminderMinutes] holds raw lead times as the file's `VALARM`s expressed
 * them — minutes *before* start, so a negative entry means "after start".
 * [semanticReminderMinutes] turns those into the lead times Calendula models.
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
    /** Opaque ARGB the source file gave this event, if any (see [parseIcsColorValue]). */
    val color: Int? = null,
    /** Excluded occurrences, already in the provider's `EXDATE` shape. */
    val exDates: List<String> = emptyList(),
    /** True when this came from a `VTODO` rather than a `VEVENT`. */
    val isTask: Boolean = false,
)

/**
 * [reminderMinutes] mapped onto Calendula's model: a lead time in minutes before
 * the event, never negative.
 *
 * For a timed event the raw lead time already is that. All-day reminders are
 * whole days before the event, fired at the user's configured time of day (see
 * `AllDayReminderEncoding`), so a raw offset counts the whole days its trigger
 * lands *earlier than* the event's UTC midnight — i.e. it rounds **up**.
 *
 * Rounding to the nearest day instead would lose a day for every producer whose
 * all-day notifications fire after noon: a file's raw offset is
 * `days × 1440 − timeOfDay`, so "1 day before at 18:00" arrives as
 * `TRIGGER:-PT6H` (raw 360) and would read as "on the day". Rounding up also
 * lands Fossify's encoding correctly — it writes "on the day at 09:00" as a
 * *positive* `TRIGGER:P0DT9H0M0S`, nine hours after the UTC midnight, which
 * ceils to zero days before.
 */
fun ParsedIcsEvent.semanticReminderMinutes(): List<Int> = reminderMinutes
    .map { raw ->
        if (isAllDay) {
            ceil(raw.toDouble() / MINUTES_PER_DAY).toInt().coerceAtLeast(0) * MINUTES_PER_DAY
        } else {
            raw.coerceAtLeast(0)
        }
    }
    .distinct()
    .sorted()

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

    /** `VTODO` components were imported as events — Calendula has no task model. */
    TasksImportedAsEvents,

    /** A malformed `RRULE` was repaired or dropped (see [sanitizeRrule]). */
    RecurrenceRuleRepaired,
}

data class IcsParseResult(
    val events: List<ParsedIcsEvent>,
    val warnings: Set<IcsParseWarning>,
)

/**
 * Outcome of a bulk `.ics` import into one calendar. [failed] counts events the
 * provider rejected — the import continues past them, so a single unusable
 * event can't cost the user the rest of the file.
 */
data class IcsImportSummary(
    val imported: Int,
    val skippedDuplicate: Int,
    val failed: Int = 0,
)

/**
 * Hand-rolled RFC 5545 reader, the inverse of [IcsWriter]. Pure and
 * JVM-testable. Liberal-in/strict-out: unknown properties are ignored, a single
 * malformed `VEVENT` is skipped (not fatal), and unsupported constructs
 * (`RECURRENCE-ID`, attendees, unresolved `TZID`) are reported as [warnings]
 * rather than silently dropped. `VTIMEZONE` blocks are skipped — a `TZID` is
 * resolved against the OS tz database instead ([deviceZone] is the fallback).
 *
 * `VTODO` components are read as all-day/timed events: Calendula models no
 * tasks, and dropping them would silently lose half of some exports.
 */
class IcsParser(private val deviceZone: TimeZone = TimeZone.currentSystemDefault()) {

    fun parse(text: String): IcsParseResult {
        val lines = unfoldLines(text)
        val events = mutableListOf<ParsedIcsEvent>()
        val warnings = mutableSetOf<IcsParseWarning>()
        // Scanned up front rather than in document order: an X-WR-CALNAME after
        // the first VEVENT still names the calendar every event came from.
        val calendarName = lines.asSequence()
            .mapNotNull(::parseContentLine)
            .firstOrNull { it.name == "X-WR-CALNAME" }
            ?.let { unescapeText(it.value).trim().ifEmpty { null } }

        var i = 0
        while (i < lines.size) {
            val line = parseContentLine(lines[i])
            if (line == null) { i++; continue }
            val component = when {
                line.isBegin("VEVENT") -> "VEVENT"
                line.isBegin("VTODO") -> "VTODO"
                else -> null
            }
            when {
                component != null -> {
                    val end = indexOfEnd(lines, i + 1, component)
                    parseComponent(
                        body = lines.subList(i + 1, end),
                        fileCalendarName = calendarName,
                        warnings = warnings,
                        isTask = component == "VTODO",
                    )?.let(events::add)
                    i = end + 1
                }
                line.isBegin("VTIMEZONE") -> {
                    // Skipped wholesale; TZIDs resolve against the OS tz database.
                    i = indexOfEnd(lines, i + 1, "VTIMEZONE") + 1
                }
                else -> i++
            }
        }
        if (events.any { it.isTask }) warnings.add(IcsParseWarning.TasksImportedAsEvents)
        return IcsParseResult(events, warnings)
    }

    private fun parseComponent(
        body: List<String>,
        fileCalendarName: String?,
        warnings: MutableSet<IcsParseWarning>,
        isTask: Boolean,
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
        var eventColor: Int? = null
        var calendarColor: Int? = null
        val reminders = mutableListOf<Int>()
        val exDateLines = mutableListOf<IcsContentLine>()
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
                // A VTODO has no DTSTART of its own in most producers' output.
                "DUE" -> if (isTask && dtStart == null) dtStart = parseIcsDateTime(line, warnings)
                "DTEND" -> dtEnd = parseIcsDateTime(line, warnings)
                "DURATION" -> duration = line.value.trim()
                "RRULE" -> rrule = line.value.trim().ifEmpty { null }
                "EXDATE" -> exDateLines.add(line)
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
                // Fossify names the source calendar per event; it never writes
                // an X-WR-CALNAME, so this is the only calendar label its files carry.
                "CATEGORIES" -> calendarName = unescapeText(line.value).trim()
                    .takeIf { it.isNotEmpty() && !it.equals("null", true) }
                    ?: calendarName
                // Per-event colour, most specific encoding first (see parseIcsColorValue).
                "X-FOSSIFY-EVENT-COLOR", "X-SMT-EVENT-COLOR" ->
                    eventColor = parseIcsColorValue(line.value) ?: eventColor
                "COLOR" -> eventColor = eventColor ?: parseIcsColorValue(line.value)
                // The *calendar's* colour. Kept as a fallback so a migration that
                // folds several source calendars into one keeps them apart visually.
                "X-FOSSIFY-CATEGORY-COLOR", "X-SMT-CATEGORY-COLOR", "CATEGORY_COLOR" ->
                    calendarColor = parseIcsColorValue(line.value) ?: calendarColor
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
        val cleanRrule = sanitizeRrule(rrule)
        if (rrule != null && cleanRrule != rrule.removePrefix("RRULE:").trim()) {
            warnings.add(IcsParseWarning.RecurrenceRuleRepaired)
        }
        val end = resolveEnd(start, dtEnd, duration)
        return ParsedIcsEvent(
            uid = uid,
            summary = summary,
            start = start.instant,
            end = end,
            isAllDay = start.isAllDay,
            zoneId = start.zoneId,
            recurrenceRule = cleanRrule,
            location = location,
            description = description,
            reminderMinutes = reminders.distinct(),
            status = status,
            availability = availability,
            calendarName = calendarName,
            color = eventColor ?: calendarColor,
            exDates = if (cleanRrule == null) emptyList() else normalizeExDates(exDateLines, start),
            isTask = isTask,
        )
    }

    /**
     * The event's end instant.
     *
     * All-day events get two RFC 5545 §3.6.1 rules applied that a literal read
     * would miss: a `DATE`-valued `DTSTART` with no `DTEND` lasts one day, and —
     * since the provider expands a zero-length all-day series into no instances
     * at all — an all-day event may never end at or before it starts.
     */
    private fun resolveEnd(
        start: IcsDateTime,
        dtEnd: IcsDateTime?,
        duration: String?,
    ): Instant {
        val explicit = dtEnd?.instant ?: duration?.let {
            Instant.fromEpochMilliseconds(
                start.instant.toEpochMilliseconds() + parseRfc2445DurationMillis(it),
            )
        }
        if (!start.isAllDay) return explicit ?: start.instant
        return explicit?.takeIf { it > start.instant } ?: start.instant.plusDays(1)
    }

    /**
     * `EXDATE` values in the shape `Events.EXDATE` wants: `yyyyMMdd` for an
     * all-day series, a UTC `yyyyMMddTHHmmssZ` stamp otherwise.
     *
     * Fossify stores its excluded occurrences as bare day codes and writes them
     * out that way even for timed series, where the property is then ambiguous.
     * Such a value is resolved against the series' own time of day, which is
     * what it meant — its repetitions never move within the day.
     */
    private fun normalizeExDates(lines: List<IcsContentLine>, start: IcsDateTime): List<String> {
        if (lines.isEmpty()) return emptyList()
        val startZone = runCatching { TimeZone.of(start.zoneId) }.getOrNull() ?: deviceZone
        val startTime = start.instant.toLocalDateTime(startZone).time
        return lines
            .flatMap { line -> line.value.split(',').map { line to it.trim() } }
            .mapNotNull { (line, token) ->
                if (token.isEmpty()) return@mapNotNull null
                if (token.contains('T')) {
                    val instant = parseExDateTime(token, line, startZone) ?: return@mapNotNull null
                    if (start.isAllDay) utcDayCode(instant) else utcStamp(instant)
                } else {
                    val date = parseBasicDate(token) ?: return@mapNotNull null
                    if (start.isAllDay) {
                        utcDayCode(date.atStartOfDayIn(TimeZone.UTC))
                    } else {
                        utcStamp(LocalDateTime(date, startTime).toInstant(startZone))
                    }
                }
            }
            .distinct()
    }

    /**
     * [startZone] is the series' own zone, which a floating value (no `Z`, no
     * `TZID`) has to be read in — RFC 5545 ties `EXDATE` to `DTSTART`'s form,
     * and reading it in the device's zone instead would put the exclusion on an
     * instant no occurrence has.
     */
    private fun parseExDateTime(
        token: String,
        line: IcsContentLine,
        startZone: TimeZone,
    ): Instant? {
        val ldt = parseBasicDateTime(token.removeSuffix("Z")) ?: return null
        val zone = when {
            token.endsWith("Z") -> TimeZone.UTC
            else -> line.params["TZID"]?.let { runCatching { TimeZone.of(it) }.getOrNull() }
                ?: startZone
        }
        return ldt.toInstant(zone)
    }

    /**
     * A `VALARM`'s offset from the event start, in minutes *before* it — so a
     * trigger that fires after the start (which is how the Fossify family
     * encodes "on the day at 09:00") comes back negative. Null when the trigger
     * isn't a usable relative offset.
     */
    private fun parseAlarmMinutes(body: List<String>): Int? {
        val trigger = body.asSequence()
            .mapNotNull { parseContentLine(it) }
            .firstOrNull { it.name == "TRIGGER" }
            ?: return null
        // Absolute (DATE-TIME) triggers can't be expressed as a lead time.
        if (trigger.params["VALUE"].equals("DATE-TIME", true)) return null
        val millis = parseRfc2445DurationMillis(trigger.value)
        return (-millis / 60_000L).toInt()
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

        fun Instant.plusDays(days: Int): Instant =
            Instant.fromEpochMilliseconds(toEpochMilliseconds() + days * DAY_MILLIS)

        fun utcDayCode(instant: Instant): String = with(instant.toLocalDateTime(TimeZone.UTC)) {
            "%04d%02d%02d".format(year, month.number, day)
        }

        fun utcStamp(instant: Instant): String = with(instant.toLocalDateTime(TimeZone.UTC)) {
            "%04d%02d%02dT%02d%02d%02dZ".format(year, month.number, day, hour, minute, second)
        }

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
