package de.jeanlucmakiola.calendula.ui.common

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/** Which screen draws each view: the timeline views share one, so switching among them resizes it (#184). */
class ViewScreenTest {

    @Test
    fun `the day, multi-day and week views share the timeline`() {
        assertThat(listOf(CalendarView.Day, CalendarView.MultiDay, CalendarView.Week).map { it.screen }.toSet())
            .containsExactly(ViewScreen.Timeline)
    }

    @Test
    fun `month and agenda each have their own screen`() {
        assertThat(CalendarView.Month.screen).isEqualTo(ViewScreen.Month)
        assertThat(CalendarView.Agenda.screen).isEqualTo(ViewScreen.Agenda)
    }
}
