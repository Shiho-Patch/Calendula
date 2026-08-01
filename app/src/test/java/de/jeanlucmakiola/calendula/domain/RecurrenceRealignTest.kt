package de.jeanlucmakiola.calendula.domain

import com.google.common.truth.Truth.assertThat
import kotlinx.datetime.LocalDate
import org.junit.jupiter.api.Test

class RecurrenceRealignTest {

    private val monday = LocalDate(2026, 6, 8)
    private val wednesday = LocalDate(2026, 6, 10)

    @Test
    fun `a weekly BYDAY rule follows the occurrence to its new weekday`() {
        assertThat(realignRecurrence("FREQ=WEEKLY;BYDAY=MO", monday, wednesday))
            .isEqualTo("FREQ=WEEKLY;BYDAY=WE")
    }

    @Test
    fun `unrelated parts survive the rewrite`() {
        assertThat(
            realignRecurrence("FREQ=WEEKLY;INTERVAL=2;BYDAY=MO;COUNT=10", monday, wednesday),
        ).isEqualTo("FREQ=WEEKLY;INTERVAL=2;BYDAY=WE;COUNT=10")
    }

    @Test
    fun `a monthly BYMONTHDAY rule follows the day of the month`() {
        assertThat(
            realignRecurrence("FREQ=MONTHLY;BYMONTHDAY=8", monday, wednesday),
        ).isEqualTo("FREQ=MONTHLY;BYMONTHDAY=10")
    }

    @Test
    fun `a yearly rule realigns both month and day`() {
        assertThat(
            realignRecurrence(
                "FREQ=YEARLY;BYMONTH=6;BYMONTHDAY=8",
                monday,
                LocalDate(2026, 7, 20),
            ),
        ).isEqualTo("FREQ=YEARLY;BYMONTH=7;BYMONTHDAY=20")
    }

    @Test
    fun `a rule with no day-selecting part needs no rewrite`() {
        assertThat(realignRecurrence("FREQ=DAILY;INTERVAL=3", monday, wednesday))
            .isEqualTo("FREQ=DAILY;INTERVAL=3")
        assertThat(realignRecurrence("FREQ=WEEKLY", monday, wednesday)).isEqualTo("FREQ=WEEKLY")
    }

    @Test
    fun `a rule the move does not disturb comes back verbatim`() {
        val rule = "FREQ=WEEKLY;BYDAY=MO,WE,FR"
        assertThat(realignRecurrence(rule, monday, monday)).isEqualTo(rule)
    }

    @Test
    fun `a multi-day BYDAY cannot be resolved from one moved occurrence`() {
        assertThat(realignRecurrence("FREQ=WEEKLY;BYDAY=MO,WE", monday, wednesday)).isNull()
    }

    @Test
    fun `an ordinal BYDAY cannot be resolved`() {
        assertThat(realignRecurrence("FREQ=MONTHLY;BYDAY=2MO", monday, wednesday)).isNull()
    }

    @Test
    fun `a BYDAY that does not name the occurrence's own weekday is refused`() {
        // The rule and DTSTART already disagree; guessing would make it worse.
        assertThat(realignRecurrence("FREQ=WEEKLY;BYDAY=TU", monday, wednesday)).isNull()
    }

    @Test
    fun `parts we cannot reason about are refused rather than guessed`() {
        assertThat(realignRecurrence("FREQ=MONTHLY;BYDAY=MO;BYSETPOS=1", monday, wednesday))
            .isNull()
        assertThat(realignRecurrence("FREQ=YEARLY;BYYEARDAY=159", monday, wednesday)).isNull()
    }

    @Test
    fun `a leading RRULE prefix is preserved`() {
        assertThat(realignRecurrence("RRULE:FREQ=WEEKLY;BYDAY=MO", monday, wednesday))
            .isEqualTo("RRULE:FREQ=WEEKLY;BYDAY=WE")
    }

    @Test
    fun `a malformed rule is refused`() {
        assertThat(realignRecurrence("FREQ=WEEKLY;GARBAGE", monday, wednesday)).isNull()
        assertThat(realignRecurrence("", monday, wednesday)).isNull()
    }
}
