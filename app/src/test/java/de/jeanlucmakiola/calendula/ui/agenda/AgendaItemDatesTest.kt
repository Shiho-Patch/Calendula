package de.jeanlucmakiola.calendula.ui.agenda

import com.google.common.truth.Truth.assertThat
import de.jeanlucmakiola.calendula.domain.EventInstance
import kotlinx.datetime.LocalDate
import kotlin.time.Instant
import org.junit.jupiter.api.Test

/** The day each agenda list item belongs to, for the focus it hands back (#184). */
class AgendaItemDatesTest {

    private val monday = LocalDate(2026, 9, 28)
    private val tuesday = LocalDate(2026, 9, 29)

    private fun event(id: Long) = EventInstance(
        instanceId = id,
        eventId = id,
        calendarId = 1L,
        title = "Event",
        start = Instant.fromEpochMilliseconds(0),
        end = Instant.fromEpochMilliseconds(0),
        isAllDay = false,
        color = 0xFF000000.toInt(),
        location = null,
    )

    @Test
    fun `each day counts its header, events and gap`() {
        val days = listOf(AgendaDay(monday, listOf(event(1), event(2))), AgendaDay(tuesday, listOf(event(3))))
        assertThat(agendaItemDates(days)).containsExactly(
            monday, monday, monday, monday,
            tuesday, tuesday, tuesday,
        ).inOrder()
    }

    @Test
    fun `an empty day counts its placeholder`() {
        assertThat(agendaItemDates(listOf(AgendaDay(monday, emptyList())))).hasSize(3)
    }
}
