package de.jeanlucmakiola.calendula.domain

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * The one-shot reconciliation of the retired app-local "disabled calendars" set
 * into the system's `Calendars.VISIBLE` (#75), across every
 * (sync_events, visible) combination.
 */
class CalendarVisibilityPlanTest {

    private fun cal(
        id: Long,
        visible: Boolean = true,
        syncsEvents: Boolean = true,
    ): CalendarSource = CalendarSource(
        id = id,
        displayName = "Cal $id",
        accountName = "acc@local",
        accountType = "LOCAL",
        color = 0,
        isVisibleInSystem = visible,
        syncsEvents = syncsEvents,
    )

    @Test
    fun `a calendar disabled in-app is switched off at system level`() {
        val plan = calendarVisibilityPlan(listOf(cal(1L, visible = true)), setOf(1L))
        assertThat(plan.hide).containsExactly(1L)
        assertThat(plan.show).isEmpty()
    }

    @Test
    fun `a calendar enabled in-app but hidden at system level is switched on`() {
        val plan = calendarVisibilityPlan(listOf(cal(1L, visible = false)), emptySet())
        assertThat(plan.show).containsExactly(1L)
        assertThat(plan.hide).isEmpty()
    }

    @Test
    fun `a not-synced calendar is never switched on`() {
        // sync_events = 0 means the provider holds none of its events locally,
        // so making it visible could only produce an enabled-yet-empty row.
        val plan = calendarVisibilityPlan(
            listOf(cal(1L, visible = false, syncsEvents = false)),
            emptySet(),
        )
        assertThat(plan.isEmpty).isTrue()
    }

    @Test
    fun `a not-synced calendar disabled in-app is still switched off`() {
        val plan = calendarVisibilityPlan(
            listOf(cal(1L, visible = true, syncsEvents = false)),
            setOf(1L),
        )
        assertThat(plan.hide).containsExactly(1L)
    }

    @Test
    fun `calendars that already agree are left untouched`() {
        val plan = calendarVisibilityPlan(
            listOf(cal(1L, visible = true), cal(2L, visible = false)),
            disabledCalendarIds = setOf(2L),
        )
        assertThat(plan.isEmpty).isTrue()
    }

    @Test
    fun `a disabled id for a calendar that no longer exists writes nothing`() {
        val plan = calendarVisibilityPlan(listOf(cal(1L)), setOf(99L))
        assertThat(plan.isEmpty).isTrue()
    }

    @Test
    fun `a mixed device is reconciled in both directions at once`() {
        val plan = calendarVisibilityPlan(
            listOf(
                cal(1L, visible = true), // enabled, already visible → untouched
                cal(2L, visible = false), // enabled but hidden → show
                cal(3L, visible = true), // disabled in-app → hide
                cal(4L, visible = false, syncsEvents = false), // not synced → untouched
            ),
            disabledCalendarIds = setOf(3L),
        )
        assertThat(plan.show).containsExactly(2L)
        assertThat(plan.hide).containsExactly(3L)
    }

    @Test
    fun `nothing disabled and everything visible is a no-op`() {
        val plan = calendarVisibilityPlan(listOf(cal(1L), cal(2L)), emptySet())
        assertThat(plan.isEmpty).isTrue()
    }
}
