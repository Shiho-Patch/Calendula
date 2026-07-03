package de.jeanlucmakiola.calendula.ui.common

import com.google.common.truth.Truth.assertThat
import de.jeanlucmakiola.calendula.data.prefs.CalendarReminderOverride
import org.junit.jupiter.api.Test

/**
 * The pure emit rule behind [ReminderDefaultPicker]: a non-empty selection is
 * [CalendarReminderOverride.Minutes] (normalised), while clearing the last time
 * reverts to [CalendarReminderOverride.Inherit] on a per-calendar picker (so an
 * accidental toggle-undo can't silently wipe the calendar's default) and to
 * explicit [CalendarReminderOverride.None] on the global default.
 */
class ReminderOverrideForMinutesTest {

    @Test
    fun `empty selection on a per-calendar picker reverts to inherit`() {
        assertThat(reminderOverrideForMinutes(emptyList(), allowInherit = true))
            .isEqualTo(CalendarReminderOverride.Inherit)
    }

    @Test
    fun `empty selection on the global default is explicit none`() {
        assertThat(reminderOverrideForMinutes(emptyList(), allowInherit = false))
            .isEqualTo(CalendarReminderOverride.None)
    }

    @Test
    fun `a non-empty selection is normalised minutes regardless of scope`() {
        assertThat(reminderOverrideForMinutes(listOf(30, 10, 10, 60), allowInherit = true))
            .isEqualTo(CalendarReminderOverride.Minutes(listOf(10, 30, 60)))
        assertThat(reminderOverrideForMinutes(listOf(30, 10, 10, 60), allowInherit = false))
            .isEqualTo(CalendarReminderOverride.Minutes(listOf(10, 30, 60)))
    }
}
