package de.jeanlucmakiola.calendula.ui.common

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * The pure back-stack rules behind [CalendarHost]'s top-level navigation:
 * the home view is always the bottom, a lateral move keeps the stack one deep,
 * and a date tap drills the day view on top. Verifies the back sequences the
 * widget-UX fix promises (e.g. agenda widget → day → back → agenda → back → home).
 */
class ViewBackStackTest {

    @Test
    fun `a widget whose view is the home view resets to just the home`() {
        assertThat(viewBaseStack(CalendarView.Week, CalendarView.Week))
            .containsExactly(CalendarView.Week)
    }

    @Test
    fun `a widget launch resets to its view over the home`() {
        assertThat(viewBaseStack(CalendarView.Week, CalendarView.Month))
            .containsExactly(CalendarView.Week, CalendarView.Month)
            .inOrder()
    }

    @Test
    fun `pill switches build a visit history that back retraces`() {
        // Agenda → Day → Week → Month, each a new view, stacks the full history.
        val stack = listOf(CalendarView.Agenda)
            .selectView(CalendarView.Day)
            .selectView(CalendarView.Week)
            .selectView(CalendarView.Month)
        assertThat(stack).containsExactly(
            CalendarView.Agenda, CalendarView.Day, CalendarView.Week, CalendarView.Month,
        ).inOrder()
        // Back walks the views in reverse, not straight to the home view.
        assertThat(stack.dropLast(1).last()).isEqualTo(CalendarView.Week)
    }

    @Test
    fun `selecting an already-visited view collapses the loop back to it`() {
        val stack = listOf(CalendarView.Week, CalendarView.Agenda, CalendarView.Day)
            .selectView(CalendarView.Agenda)
        assertThat(stack).containsExactly(CalendarView.Week, CalendarView.Agenda).inOrder()
    }

    @Test
    fun `selecting the home view collapses to just the home`() {
        val stack = listOf(CalendarView.Week, CalendarView.Agenda, CalendarView.Day)
            .selectView(CalendarView.Week)
        assertThat(stack).containsExactly(CalendarView.Week)
    }

    @Test
    fun `drilling pushes the day view on top`() {
        assertThat(listOf(CalendarView.Week, CalendarView.Month).drillToDay())
            .containsExactly(CalendarView.Week, CalendarView.Month, CalendarView.Day)
            .inOrder()
    }

    @Test
    fun `drilling is a no-op when the day view is already current`() {
        val stack = listOf(CalendarView.Week, CalendarView.Day)
        assertThat(stack.drillToDay()).isEqualTo(stack)
    }

    @Test
    fun `agenda widget date tap backs out agenda then home`() {
        // openDate(source = Agenda): root in Agenda over the Week home, then drill.
        val stack = viewBaseStack(CalendarView.Week, CalendarView.Agenda).drillToDay()
        assertThat(stack)
            .containsExactly(CalendarView.Week, CalendarView.Agenda, CalendarView.Day)
            .inOrder()
        // Pressing back pops one level at a time down to the home view.
        assertThat(stack.dropLast(1)).containsExactly(CalendarView.Week, CalendarView.Agenda).inOrder()
        assertThat(stack.dropLast(2)).containsExactly(CalendarView.Week)
    }

    @Test
    fun `month widget date tap backs out month then home`() {
        val stack = viewBaseStack(CalendarView.Week, CalendarView.Month).drillToDay()
        assertThat(stack.last()).isEqualTo(CalendarView.Day)
        assertThat(stack.dropLast(1).last()).isEqualTo(CalendarView.Month)
        assertThat(stack.first()).isEqualTo(CalendarView.Week)
    }

    @Test
    fun `a widget whose view is the home view does not duplicate the home`() {
        // Default = Agenda, agenda widget event tap → just the home, no extra layer.
        assertThat(viewBaseStack(CalendarView.Agenda, CalendarView.Agenda))
            .containsExactly(CalendarView.Agenda)
    }
}
