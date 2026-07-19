package de.jeanlucmakiola.calendula.domain

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.Locale

class TimeZoneCatalogTest {

    // A fixed instant so DST-dependent offsets can't drift with the wall clock:
    // 2026-06-11 is northern summer, i.e. Berlin on CEST and New York on EDT.
    private val summer: Instant = Instant.parse("2026-06-11T12:00:00Z")
    private val zones = timeZoneOptions(Locale.ENGLISH, summer)

    private fun filter(query: String) = filterTimeZones(zones, query)

    @Test
    fun `catalogue holds the real zones and drops the legacy aliases`() {
        assertThat(zones.map { it.id }).containsAtLeast("Europe/Berlin", "America/New_York")
        // Bare aliases the tz database keeps for compatibility would double up
        // the real zones in the picker.
        assertThat(zones.map { it.id }).containsNoneOf("EST", "CST6CDT", "UTC")
        assertThat(zones.none { it.id.startsWith("SystemV/") }).isTrue()
    }

    @Test
    fun `option exposes city and region split from the id`() {
        val ny = zones.first { it.id == "America/New_York" }
        assertThat(ny.city).isEqualTo("New York")
        assertThat(ny.region).isEqualTo("America")
    }

    @Test
    fun `label is the id with underscores undone`() {
        assertThat(zones.first { it.id == "America/New_York" }.label)
            .isEqualTo("America/New York")
        assertThat(zones.first { it.id == "Europe/Berlin" }.label).isEqualTo("Europe/Berlin")
    }

    @Test
    fun `resolved abbreviation is compact, not the long name`() {
        // Whatever CLDR gives ("CET"/"CEST" or a "GMT+.." fallback), it must be
        // short and space-free — that's the whole point of showing it instead of
        // "Central European Time".
        val berlin = zones.first { it.id == "Europe/Berlin" }
        assertThat(berlin.shortName).doesNotContain(" ")
        assertThat(berlin.shortName.length).isLessThan(berlin.displayName.length)
    }

    @Test
    fun `descriptor pairs a named abbreviation with the offset`() {
        val option = TimeZoneOption(
            id = "Europe/Berlin",
            displayName = "Central European Time",
            shortName = "CET",
            offsetMinutes = 60,
        )
        assertThat(zoneDescriptor(option)).isEqualTo("CET · GMT+01:00")
    }

    @Test
    fun `descriptor drops the offset when the abbreviation already is one`() {
        // Repeating the offset after an offset-shaped abbreviation would just say
        // the same thing twice.
        fun descriptorFor(shortName: String, offset: Int) = zoneDescriptor(
            TimeZoneOption(id = "X/Y", displayName = "", shortName = shortName, offsetMinutes = offset),
        )
        assertThat(descriptorFor("UTC", 0)).isEqualTo("UTC")
        assertThat(descriptorFor("GMT+05:30", 330)).isEqualTo("GMT+05:30")
    }

    @Test
    fun `offset is resolved at the given instant, not the current one`() {
        val berlin = zones.first { it.id == "Europe/Berlin" }
        // CEST in June, so +02:00 — a fixed +01:00 would mean we ignored DST.
        assertThat(berlin.offsetMinutes).isEqualTo(120)

        val winter = timeZoneOptions(Locale.ENGLISH, Instant.parse("2026-01-11T12:00:00Z"))
        assertThat(winter.first { it.id == "Europe/Berlin" }.offsetMinutes).isEqualTo(60)
    }

    @Test
    fun `blank query returns everything unchanged`() {
        assertThat(filter("")).isEqualTo(zones)
        assertThat(filter("   ")).isEqualTo(zones)
    }

    @Test
    fun `query matches the city, ignoring case and underscores`() {
        assertThat(filter("new york").map { it.id }).contains("America/New_York")
        assertThat(filter("NEW YORK").map { it.id }).contains("America/New_York")
        assertThat(filter("new_york").map { it.id }).contains("America/New_York")
    }

    @Test
    fun `query matches accented cities typed plainly`() {
        // Sao_Paulo has no accent in the id, but its localized name does — the
        // point is that a user typing plain ASCII still finds it.
        assertThat(filter("sao paulo").map { it.id }).contains("America/Sao_Paulo")
        assertThat(filter("zurich").map { it.id }).contains("Europe/Zurich")
    }

    @Test
    fun `query matches the full IANA id`() {
        assertThat(filter("europe/berlin").map { it.id }).contains("Europe/Berlin")
    }

    @Test
    fun `a city starting with the query outranks one merely containing it`() {
        val ids = filter("york").map { it.id }
        // "New York" contains "york"; nothing starts with it, so it should still
        // surface rather than being buried.
        assertThat(ids).contains("America/New_York")

        // "col" starts Colombo but only appears mid-string elsewhere.
        val col = filter("col").map { it.id }
        assertThat(col.first()).isEqualTo("Asia/Colombo")
    }

    @Test
    fun `no match yields an empty list rather than everything`() {
        assertThat(filter("zzzznotazone")).isEmpty()
    }

    @Test
    fun `single zone resolves the same way the catalogue does`() {
        val fromCatalogue = zones.first { it.id == "Europe/Berlin" }
        assertThat(timeZoneOptionOf("Europe/Berlin", Locale.ENGLISH, summer))
            .isEqualTo(fromCatalogue)
    }

    @Test
    fun `an unknown zone id resolves to null`() {
        assertThat(timeZoneOptionOf("Mars/Olympus_Mons")).isNull()
    }

    @Test
    fun `gmt offsets format with a sign and padding`() {
        assertThat(formatGmtOffset(0)).isEqualTo("GMT")
        assertThat(formatGmtOffset(120)).isEqualTo("GMT+02:00")
        assertThat(formatGmtOffset(-300)).isEqualTo("GMT-05:00")
        // India is +05:30 — a whole-hour assumption would render this wrong.
        assertThat(formatGmtOffset(330)).isEqualTo("GMT+05:30")
        assertThat(formatGmtOffset(-210)).isEqualTo("GMT-03:30")
    }
}
