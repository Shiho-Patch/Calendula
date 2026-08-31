package de.jeanlucmakiola.calendula.ui.imports

import com.google.common.truth.Truth.assertThat
import de.jeanlucmakiola.calendula.domain.CalendarSource
import de.jeanlucmakiola.calendula.domain.ics.ParsedIcsEvent
import org.junit.jupiter.api.Test
import kotlin.time.Instant

/** Which calendar a bulk import lands on before the user touches the picker. */
class ImportTargetTest {

    private fun cal(id: Long, name: String, local: Boolean = false) = CalendarSource(
        id = id,
        displayName = name,
        accountName = "acc@local",
        accountType = if (local) "LOCAL" else "com.google",
        color = 0,
        isVisibleInSystem = true,
        isLocal = local,
    )

    private val calendars = listOf(
        cal(1L, "Work"),
        cal(2L, "Personal", local = true),
        cal(3L, "Birthdays", local = true),
    )

    @Test
    fun `a calendar named like the file's wins`() {
        assertThat(defaultImportTarget(calendars, "Birthdays")).isEqualTo(3L)
    }

    @Test
    fun `the name match ignores case`() {
        assertThat(defaultImportTarget(calendars, "birthdays")).isEqualTo(3L)
    }

    @Test
    fun `an unmatched name falls back to the first local calendar`() {
        assertThat(defaultImportTarget(calendars, "Holidays")).isEqualTo(2L)
    }

    @Test
    fun `no name at all falls back to the first local calendar`() {
        assertThat(defaultImportTarget(calendars, null)).isEqualTo(2L)
    }

    @Test
    fun `with no local calendars the first of any kind is taken`() {
        assertThat(defaultImportTarget(listOf(cal(1L, "Work")), null)).isEqualTo(1L)
    }

    @Test
    fun `an empty list preselects nothing`() {
        assertThat(defaultImportTarget(emptyList(), "Birthdays")).isNull()
    }

    private fun event(calendarName: String?) = ParsedIcsEvent(
        uid = "u@x",
        summary = "E",
        start = Instant.fromEpochMilliseconds(0L),
        end = Instant.fromEpochMilliseconds(3_600_000L),
        isAllDay = false,
        zoneId = "UTC",
        calendarName = calendarName,
    )

    @Test
    fun `a file whose events all name one calendar names it`() {
        assertThat(fileCalendarName(List(3) { event("Birthdays") })).isEqualTo("Birthdays")
    }

    @Test
    fun `disagreeing events name none`() {
        assertThat(fileCalendarName(listOf(event("Birthdays"), event("Anniversaries")))).isNull()
    }

    @Test
    fun `one tagged event among unnamed ones names none`() {
        // CATEGORIES is a tag list everywhere but the Fossify family, so a lone
        // tagged event must not decide where the whole file lands.
        assertThat(fileCalendarName(listOf(event("Holiday")) + List(9) { event(null) })).isNull()
    }

    @Test
    fun `a file that names nothing names none`() {
        assertThat(fileCalendarName(List(3) { event(null) })).isNull()
    }
}
