package de.jeanlucmakiola.calendula.domain

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * Draining the app's pending "switched off" set into the system's
 * `Calendars.VISIBLE` (#75) — including the set an upgrade inherits from the
 * retired app-local visibility model.
 */
class CalendarVisibilityPlanTest {

    private fun cal(
        id: Long,
        visible: Boolean = true,
        local: Boolean = false,
        syncsEvents: Boolean = true,
    ): CalendarSource = CalendarSource(
        id = id,
        displayName = "Cal $id",
        accountName = "acc@local",
        accountType = if (local) "LOCAL" else "com.google",
        color = 0,
        isVisibleInSystem = visible,
        isLocal = local,
        syncsEvents = syncsEvents,
    )

    @Test
    fun `a pending calendar is switched off at system level`() {
        val plan = calendarVisibilityPlan(listOf(cal(1L, visible = true)), setOf(1L))
        assertThat(plan.hide).containsExactly(1L)
        assertThat(plan.settled).isEmpty()
    }

    @Test
    fun `a calendar hidden at system level is never switched on`() {
        // The plan only hides: switching it on would un-hide the calendar in
        // every other calendar app and start firing its reminders.
        val plan = calendarVisibilityPlan(listOf(cal(1L, visible = false)), emptySet())
        assertThat(plan.isEmpty).isTrue()
    }

    @Test
    fun `a not-synced calendar is switched off like any other`() {
        val plan = calendarVisibilityPlan(
            listOf(cal(1L, visible = true, syncsEvents = false)),
            setOf(1L),
        )
        assertThat(plan.hide).containsExactly(1L)
    }

    @Test
    fun `a pending calendar already switched off is settled without a write`() {
        val plan = calendarVisibilityPlan(listOf(cal(1L, visible = false)), setOf(1L))
        assertThat(plan.hide).isEmpty()
        assertThat(plan.settled).containsExactly(1L)
    }

    @Test
    fun `a pending id for a calendar that no longer exists is settled`() {
        val plan = calendarVisibilityPlan(listOf(cal(1L)), setOf(99L))
        assertThat(plan.hide).isEmpty()
        assertThat(plan.settled).containsExactly(99L)
    }

    @Test
    fun `an empty pending set writes nothing`() {
        val plan = calendarVisibilityPlan(listOf(cal(1L), cal(2L, visible = false)), emptySet())
        assertThat(plan.isEmpty).isTrue()
    }

    @Test
    fun `a mixed device splits into writes and settled ids`() {
        val plan = calendarVisibilityPlan(
            listOf(
                cal(1L, visible = true), // not pending → untouched
                cal(2L, visible = false), // hidden elsewhere → untouched
                cal(3L, visible = true), // pending → hide
                cal(4L, visible = false), // pending, already off → settled
            ),
            pendingDisabledIds = setOf(3L, 4L, 77L),
        )
        assertThat(plan.hide).containsExactly(3L)
        assertThat(plan.settled).containsExactly(4L, 77L)
    }

    @Test
    fun `a calendar hidden outside the app arms the notice`() {
        assertThat(
            hasSystemHiddenCalendars(listOf(cal(1L), cal(2L, visible = false)), emptySet()),
        ).isTrue()
    }

    @Test
    fun `a calendar we are about to hide ourselves does not arm the notice`() {
        // It is off because the user switched it off here — nothing to explain.
        assertThat(
            hasSystemHiddenCalendars(listOf(cal(1L, visible = false)), setOf(1L)),
        ).isFalse()
    }

    @Test
    fun `an all-visible device does not arm the notice`() {
        assertThat(hasSystemHiddenCalendars(listOf(cal(1L), cal(2L)), emptySet())).isFalse()
    }

    @Test
    fun `a device-local calendar is treated like any other`() {
        // Its sync_events flag says nothing about whether it holds events, so
        // neither the plan nor the notice may reason about it.
        val plan = calendarVisibilityPlan(
            listOf(cal(1L, visible = true, local = true, syncsEvents = false)),
            setOf(1L),
        )
        assertThat(plan.hide).containsExactly(1L)
        assertThat(
            hasSystemHiddenCalendars(
                listOf(cal(2L, visible = false, local = true, syncsEvents = false)),
                emptySet(),
            ),
        ).isTrue()
    }

    @Test
    fun `no calendars on the device is its own failure`() {
        assertThat(calendarListFailure(emptyList()))
            .isEqualTo(FailureReason.NoCalendarsConfigured)
    }

    @Test
    fun `calendars that are all switched off read as hidden, not missing`() {
        // The remedy differs: one needs a calendar made, the other needs one
        // switched back on (#239).
        assertThat(calendarListFailure(listOf(cal(1L, visible = false), cal(2L, visible = false))))
            .isEqualTo(FailureReason.AllCalendarsHidden)
    }

    @Test
    fun `one visible calendar is enough to show something`() {
        assertThat(calendarListFailure(listOf(cal(1L, visible = false), cal(2L, visible = true))))
            .isNull()
    }
}
