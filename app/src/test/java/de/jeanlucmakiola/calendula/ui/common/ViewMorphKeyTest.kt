package de.jeanlucmakiola.calendula.ui.common

import com.google.common.truth.Truth.assertThat
import de.jeanlucmakiola.calendula.domain.EventInstance
import kotlinx.datetime.LocalDate
import org.junit.jupiter.api.Test
import kotlin.time.Instant

class ViewMorphKeyTest {

    private fun instance(instanceId: Long, eventId: Long = 7, startMillis: Long = 1_000L) = EventInstance(
        instanceId = instanceId,
        eventId = eventId,
        calendarId = 1,
        title = "Standup",
        start = Instant.fromEpochMilliseconds(startMillis),
        end = Instant.fromEpochMilliseconds(startMillis + 3_600_000L),
        isAllDay = false,
        color = 0,
        location = null,
    )

    private val day = LocalDate(2026, 9, 25)

    @Test
    fun `the same occurrence on the same day matches across views`() {
        // Each view reads its own instances query, so the row id is no anchor.
        assertThat(ViewMorphKey.event(instance(instanceId = 1), day))
            .isEqualTo(ViewMorphKey.event(instance(instanceId = 99), day))
    }

    @Test
    fun `a multi-day event keys each day it is drawn from apart`() {
        assertThat(ViewMorphKey.event(instance(1), day))
            .isNotEqualTo(ViewMorphKey.event(instance(1), LocalDate(2026, 9, 26)))
    }

    @Test
    fun `two occurrences of one series stay apart`() {
        assertThat(ViewMorphKey.event(instance(1, startMillis = 1_000L), day))
            .isNotEqualTo(ViewMorphKey.event(instance(2, startMillis = 2_000L), day))
    }

    @Test
    fun `only the settled page morphs`() {
        assertThat(morphPageActive(page = 5, settledPage = 5)).isTrue()
        assertThat(morphPageActive(page = 4, settledPage = 5)).isFalse()
        assertThat(morphPageActive(page = 6, settledPage = 5)).isFalse()
    }
}
