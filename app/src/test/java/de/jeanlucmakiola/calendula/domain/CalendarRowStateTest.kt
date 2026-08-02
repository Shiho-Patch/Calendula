package de.jeanlucmakiola.calendula.domain

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class CalendarRowStateTest {

    private fun cal(
        id: Long = 1L,
        name: String = "Cal $id",
        writable: Boolean = true,
        syncsEvents: Boolean = true,
        local: Boolean = false,
        managed: Boolean = false,
    ) = CalendarSource(
        id = id,
        displayName = name,
        accountName = "account",
        accountType = if (local) "LOCAL" else "com.google",
        color = 0,
        isVisibleInSystem = true,
        canModifyContents = writable,
        isLocal = local,
        syncsEvents = syncsEvents,
        isManaged = managed,
    )

    @Test
    fun `a plain writable calendar carries no state labels`() {
        assertThat(cal().stateLabels()).isEmpty()
        assertThat(cal().hasVisibilitySwitch).isTrue()
    }

    @Test
    fun `a read-only calendar is labelled`() {
        assertThat(cal(writable = false).stateLabels())
            .containsExactly(CalendarStateLabel.READ_ONLY)
    }

    @Test
    fun `a non-syncing account calendar is labelled and loses its switch`() {
        val calendar = cal(syncsEvents = false)
        assertThat(calendar.stateLabels()).containsExactly(CalendarStateLabel.NOT_SYNCED)
        assertThat(calendar.hasVisibilitySwitch).isFalse()
    }

    @Test
    fun `both states can hold at once, read-only first`() {
        assertThat(cal(writable = false, syncsEvents = false).stateLabels())
            .containsExactly(CalendarStateLabel.READ_ONLY, CalendarStateLabel.NOT_SYNCED)
            .inOrder()
    }

    @Test
    fun `a managed special-dates mirror is labelled although it is writable`() {
        // Writable, visible, syncing — nothing else on the row would hint at why
        // it can't be picked as an event target.
        val calendar = cal(local = true, managed = true)
        assertThat(calendar.stateLabels()).containsExactly(CalendarStateLabel.MANAGED)
        assertThat(calendar.hasVisibilitySwitch).isTrue()
    }

    @Test
    fun `a local calendar is never called not-synced`() {
        // Nothing syncs a device-local calendar, so sync_events says nothing
        // about it — and another app's local calendar can hold real events at 0.
        val calendar = cal(syncsEvents = false, local = true)
        assertThat(calendar.isNotSynced).isFalse()
        assertThat(calendar.stateLabels()).isEmpty()
        assertThat(calendar.hasVisibilitySwitch).isTrue()
    }

    @Test
    fun `every named state keeps a calendar out of the pickers`() {
        // The labels and the picker exclusion are the same set, stated twice —
        // a labelled row the pickers still offered would make the footer's
        // "manage your calendars to see why" a lie (#76).
        assertThat(cal().isEventTarget).isTrue()
        listOf(
            cal(writable = false),
            cal(syncsEvents = false),
            cal(local = true, managed = true),
        ).forEach { calendar ->
            assertThat(calendar.stateLabels()).isNotEmpty()
            assertThat(calendar.isEventTarget).isFalse()
        }
    }

    @Test
    fun `a switched-off calendar is no target although it carries no label`() {
        // The switch is right there on the row, so the state speaks for itself.
        val calendar = cal().copy(isVisibleInSystem = false)
        assertThat(calendar.stateLabels()).isEmpty()
        assertThat(calendar.isEventTarget).isFalse()
    }

    @Test
    fun `a managed calendar's events are editable but never movable`() {
        // Their date is owned by the contacts sync, so a drag would be undone.
        val managed = cal().copy(isManaged = true)
        assertThat(managed.allowsEventMove).isFalse()
    }

    @Test
    fun `a read-only calendar's events are not movable`() {
        assertThat(cal().copy(canModifyContents = false).allowsEventMove).isFalse()
    }

    @Test
    fun `a switched-off calendar's events stay movable, unlike a new-event target`() {
        // The predicate is deliberately not isEventTarget.
        val hidden = cal().copy(isVisibleInSystem = false)
        assertThat(hidden.isEventTarget).isFalse()
        assertThat(hidden.allowsEventMove).isTrue()
    }

    @Test
    fun `manager order puts non-syncing calendars last and is otherwise stable`() {
        val ordered = listOf(
            cal(id = 1L, name = "Anna", syncsEvents = false),
            cal(id = 2L, name = "Bert"),
            cal(id = 3L, name = "Cleo", syncsEvents = false),
            cal(id = 4L, name = "Dana"),
        ).orderedForManager()

        assertThat(ordered.map { it.displayName })
            .containsExactly("Bert", "Dana", "Anna", "Cleo")
            .inOrder()
    }
}
