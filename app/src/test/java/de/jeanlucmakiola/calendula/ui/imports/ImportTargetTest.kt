package de.jeanlucmakiola.calendula.ui.imports

import com.google.common.truth.Truth.assertThat
import de.jeanlucmakiola.calendula.domain.CalendarSource
import org.junit.jupiter.api.Test

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
}
