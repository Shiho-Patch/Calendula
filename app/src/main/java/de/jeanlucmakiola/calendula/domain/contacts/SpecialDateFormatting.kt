package de.jeanlucmakiola.calendula.domain.contacts

import kotlinx.datetime.LocalDate

/**
 * Prefix of every managed-event `UID_2445`. Distinguishes mirror events from
 * user-created ones (which carry a random `<uuid>@calendula` UID), so the sync
 * only ever reconciles — and never deletes — events it actually owns.
 */
const val MANAGED_UID_PREFIX = "contact-"

/**
 * The deterministic `Events.UID_2445` that ties a mirrored event to its source
 * contact date. Stable across syncs (the reconciliation key), namespaced by
 * type so the same contact's birthday and anniversary never collide, and — when
 * a [discriminator] is given — by it too, so two Custom dates on one contact
 * (e.g. "Wedding" and "Graduation") get distinct events instead of clobbering
 * each other.
 */
fun managedEventUid(type: SpecialDateType, lookupKey: String, discriminator: String? = null): String {
    val disc = discriminator?.takeIf { it.isNotBlank() }?.let { ":$it" }.orEmpty()
    return "$MANAGED_UID_PREFIX${type.name.lowercase()}:$lookupKey$disc@calendula"
}

/**
 * The reconciliation key for this date's mirrored event. Birthdays/anniversaries
 * are one-per-contact, so they key on the contact alone; a [SpecialDateType.Custom]
 * date adds a discriminator (its label, else its month-day) so distinct custom
 * dates on one contact don't collapse into a single event.
 */
fun ContactSpecialDate.managedUid(): String =
    managedEventUid(type, lookupKey, customDiscriminator())

private fun ContactSpecialDate.customDiscriminator(): String? =
    if (type == SpecialDateType.Custom) {
        label?.trim()?.lowercase()?.ifBlank { null } ?: "%02d-%02d".format(month, day)
    } else {
        null
    }

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
