package de.jeanlucmakiola.calendula.domain.contacts

import kotlinx.datetime.LocalDate

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
 * Render a title [template] for a contact, substituting `{name}` and `{year}`
 * (the source year — a birthday's birth year or an anniversary's start year;
 * empty when [year] is null). Unlike an age, the year is static and correct on
 * every occurrence of the yearly event. Collapses the whitespace an empty
 * `{year}` may leave behind, so "{name}'s birthday ({year})" degrades cleanly to
 * "Jane's birthday" when no year is known.
 */
fun renderSpecialDateTitle(template: String, name: String, year: Int?): String =
    template
        .replace("{name}", name)
        .replace("{year}", year?.toString().orEmpty())
        // Drop an empty "()" left by an unresolved {year}, then tidy spacing.
        .replace(Regex("""\(\s*\)"""), "")
        .replace(Regex("""\s+"""), " ")
        .trim()
