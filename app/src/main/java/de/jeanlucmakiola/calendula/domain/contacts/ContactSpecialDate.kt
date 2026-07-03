package de.jeanlucmakiola.calendula.domain.contacts

import java.time.LocalDate

/**
 * The kinds of contact "special date" Calendula mirrors into local calendars.
 * Each kind gets its own local calendar, so it inherits per-calendar colour,
 * visibility and reminder defaults for free. See
 * docs/design/contact-special-dates.md.
 */
enum class SpecialDateType {
    Birthday,
    Anniversary,

    /** Everything else — a contact "Event" that is neither birthday nor anniversary. */
    Custom,
}

/**
 * One dated event read from a device contact (a `ContactsContract` `Event`
 * row). The [lookupKey] is the stable contact identity used to reconcile the
 * mirror without duplicating; [year] is null when the contact stored the date
 * without one (`--MM-dd`), in which case age can't be shown.
 */
data class ContactSpecialDate(
    val lookupKey: String,
    val displayName: String,
    val type: SpecialDateType,
    val month: Int,
    val day: Int,
    val year: Int?,
    /** The contact's custom label for a [SpecialDateType.Custom] date, if any. */
    val label: String? = null,
)

/** The parsed month/day (+ optional year) of a contact date. */
data class ContactDateParts(val year: Int?, val month: Int, val day: Int)

/**
 * A fixed leap year to validate and anchor year-less dates against, so that a
 * `--02-29` birthday is representable (and, once anchored, only recurs in leap
 * years — matching how the calendar provider expands a yearly Feb-29 series).
 */
const val YEARLESS_ANCHOR_YEAR = 1972

/**
 * Parse a `ContactsContract.CommonDataKinds.Event.START_DATE` value into its
 * calendar parts, or null if it isn't a usable date. Handles the three shapes
 * seen in the wild:
 * - full `yyyy-MM-dd` (year known),
 * - year-less `--MM-dd` (year null),
 * - compact `yyyyMMdd`.
 *
 * A date that names an impossible day (e.g. `1999-02-29`) is rejected. Pure, so
 * it's unit-tested without a device.
 */
fun parseContactEventDate(raw: String?): ContactDateParts? {
    val s = raw?.trim().orEmpty()
    if (s.isEmpty()) return null

    // Year-less: "--MM-dd" or "--MMdd".
    if (s.startsWith("--")) {
        val (m, d) = parseMonthDay(s.substring(2)) ?: return null
        return validated(null, m, d)
    }

    if (s.contains('-')) {
        val parts = s.split('-').filter { it.isNotEmpty() }
        return when (parts.size) {
            // "yyyy-MM-dd" — but a leading '-' would have dropped the empty
            // first part, so require the first token to look like a year.
            3 -> if (s.startsWith('-')) null else validated(
                year = parts[0].toIntOrNull() ?: return null,
                month = parts[1].toIntOrNull() ?: return null,
                day = parts[2].toIntOrNull() ?: return null,
            )
            // Year-less "MM-dd" without the "--" prefix.
            2 -> validated(
                year = null,
                month = parts[0].toIntOrNull() ?: return null,
                day = parts[1].toIntOrNull() ?: return null,
            )
            else -> null
        }
    }

    // Compact "yyyyMMdd".
    if (s.length == 8 && s.all { it.isDigit() }) {
        return validated(
            year = s.substring(0, 4).toInt(),
            month = s.substring(4, 6).toInt(),
            day = s.substring(6, 8).toInt(),
        )
    }

    return null
}

/** "MM-dd" or compact "MMdd". */
private fun parseMonthDay(rest: String): Pair<Int, Int>? {
    if (rest.contains('-')) {
        val p = rest.split('-').filter { it.isNotEmpty() }
        if (p.size != 2) return null
        return (p[0].toIntOrNull() ?: return null) to (p[1].toIntOrNull() ?: return null)
    }
    if (rest.length == 4 && rest.all { it.isDigit() }) {
        return rest.substring(0, 2).toInt() to rest.substring(2, 4).toInt()
    }
    return null
}

/**
 * Confirm month/day form a real calendar date (validated against the actual
 * year when known, otherwise the leap anchor so `02-29` survives).
 */
private fun validated(year: Int?, month: Int, day: Int): ContactDateParts? {
    if (month !in 1..12 || day !in 1..31) return null
    val checkYear = year ?: YEARLESS_ANCHOR_YEAR
    return runCatching { LocalDate.of(checkYear, month, day) }
        .map { ContactDateParts(year, month, day) }
        .getOrNull()
}
