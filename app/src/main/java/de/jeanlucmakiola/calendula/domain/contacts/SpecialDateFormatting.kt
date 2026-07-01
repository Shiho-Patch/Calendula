package de.jeanlucmakiola.calendula.domain.contacts

import kotlinx.datetime.LocalDate
import kotlinx.datetime.number

/**
 * The deterministic `Events.UID_2445` that ties a mirrored event to its source
 * contact date. Stable across syncs (the reconciliation key), and namespaced by
 * type so the same contact's birthday and anniversary never collide.
 */
fun managedEventUid(type: SpecialDateType, lookupKey: String): String =
    "contact-${type.name.lowercase()}:$lookupKey@calendula"

/**
 * The all-day date the recurring `FREQ=YEARLY` series is anchored at: the real
 * date when the year is known (so age can be derived), otherwise the month/day
 * on a fixed leap anchor year so a `--02-29` date stays representable and the
 * anchor never drifts between syncs.
 */
fun ContactSpecialDate.anchorDate(): LocalDate =
    LocalDate(year ?: YEARLESS_ANCHOR_YEAR, month, day)

/**
 * The age the contact reaches on the *upcoming* occurrence of this date,
 * relative to [today], or null when the year is unknown. Because a
 * `FREQ=YEARLY` event carries a single static title, this is a snapshot taken
 * at sync time (refreshed on the next sync), not a per-occurrence value.
 */
fun ContactSpecialDate.ageAtNextOccurrence(today: LocalDate): Int? {
    val birthYear = year ?: return null
    val monthDayPassedThisYear = month < today.month.number ||
        (month == today.month.number && day < today.day)
    val nextYear = if (monthDayPassedThisYear) today.year + 1 else today.year
    return nextYear - birthYear
}

/**
 * Render a title [template] for a contact, substituting `{name}` and `{age}`
 * (the latter empty when [age] is null). Collapses the whitespace the empty
 * `{age}` may leave behind, so "{name}'s birthday ({age})" degrades cleanly to
 * "Jane's birthday" when no year is known.
 */
fun renderSpecialDateTitle(template: String, name: String, age: Int?): String =
    template
        .replace("{name}", name)
        .replace("{age}", age?.toString().orEmpty())
        // Drop an empty "()" left by an unresolved {age}, then tidy spacing.
        .replace(Regex("""\(\s*\)"""), "")
        .replace(Regex("""\s+"""), " ")
        .trim()
