package de.jeanlucmakiola.calendula.data.reminders

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * What the reminder receiver may mark handled. The silenced-but-still-ahead case
 * is the one that matters: those rows are the only copy of the reminder (#75).
 */
class AlertHandlingTest {

    private val now = 1_700_000_000_000L

    private fun alert(
        id: Long,
        calendarId: Long = 1L,
        beginMillis: Long = now + 60_000L,
        endMillis: Long = now + 3_600_000L,
    ) = ReminderAlert(
        alertId = id,
        eventId = id * 10,
        calendarId = calendarId,
        beginMillis = beginMillis,
        endMillis = endMillis,
        title = "E $id",
        location = null,
        isAllDay = false,
    )

    @Test
    fun `posted alerts are handled`() {
        val due = listOf(alert(1L), alert(2L))

        assertThat(handledAlertIds(due, postedIds = setOf(1L, 2L), nowMillis = now))
            .containsExactly(1L, 2L)
    }

    @Test
    fun `a silenced alert whose event is still ahead stays unhandled`() {
        // Switching its calendar back on before the event has to bring it back,
        // and dueAlerts only ever returns STATE_SCHEDULED rows.
        val due = listOf(alert(1L), alert(2L))

        assertThat(handledAlertIds(due, postedIds = setOf(1L), nowMillis = now))
            .containsExactly(1L)
    }

    @Test
    fun `a silenced alert whose event is over is handled`() {
        // Nothing left to re-surface, so it must not linger as scheduled.
        val over = alert(2L, beginMillis = now - 7_200_000L, endMillis = now - 3_600_000L)
        val due = listOf(alert(1L), over)

        assertThat(handledAlertIds(due, postedIds = setOf(1L), nowMillis = now))
            .containsExactly(1L, 2L)
    }

    @Test
    fun `an unknown end time falls back to the begin time`() {
        val started = alert(1L, beginMillis = now - 1L, endMillis = 0L)
        val notYet = alert(2L, beginMillis = now + 1L, endMillis = 0L)

        assertThat(handledAlertIds(listOf(started, notYet), postedIds = emptySet(), nowMillis = now))
            .containsExactly(1L)
    }
}
