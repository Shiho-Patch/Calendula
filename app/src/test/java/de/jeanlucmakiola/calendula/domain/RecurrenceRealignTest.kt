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
    fun `a day-of-month rule is refused, because the anchor moves by days not dates`() {
        // BYMONTHDAY=28 with a January anchor, occurrence Feb 28 dragged to Mar 1:
        // the rebuilt rule would say the 1st while the anchor became Jan 29 — a
        // DTSTART that is not an instance of its own rule. Weekday arithmetic is
        // uniform mod 7 and survives the same shift; day-of-month is not.
        assertThat(realignRecurrence("FREQ=MONTHLY;BYMONTHDAY=8", monday, wednesday)).isNull()
        assertThat(
            realignRecurrence("FREQ=YEARLY;BYMONTH=6;BYMONTHDAY=8", monday, LocalDate(2026, 7, 20)),
        ).isNull()
    }

    @Test
    fun `BYDAY on a non-weekly rule is refused`() {
        // "every Monday of the month" is a shape this has not been reasoned about,
        // and parseSimpleRecurrence can't read it either — so the UNTIL guard
        // downstream would be blind to it.
        assertThat(realignRecurrence("FREQ=MONTHLY;BYDAY=MO", monday, wednesday)).isNull()
    }

    @Test
    fun `a rule with no FREQ is refused`() {
        assertThat(realignRecurrence("INTERVAL=2;BYDAY=MO", monday, wednesday)).isNull()
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
        assertThat(realignRecurrence("FREQ=WEEKLY;BYDAY=MO;BYSETPOS=1", monday, wednesday))
            .isNull()
        assertThat(realignRecurrence("FREQ=YEARLY;BYYEARDAY=159", monday, wednesday)).isNull()
    }

    @Test
    fun `everything realignable is also a rule the UNTIL guard can read`() {
        // problems() checks UNTIL through parseSimpleRecurrence; a rule this
        // realigns but that parser rejects would move a series past its own end
        // unchecked. Accepting only weekly BYDAY keeps the two in step.
        val realignable = listOf(
            "FREQ=WEEKLY;BYDAY=MO",
            "FREQ=WEEKLY;INTERVAL=2;BYDAY=MO;UNTIL=20261231T225959Z",
            "FREQ=DAILY;COUNT=5",
        )
        realignable.forEach { rule ->
            assertThat(realignRecurrence(rule, monday, wednesday)).isNotNull()
            assertThat(parseSimpleRecurrence(realignRecurrence(rule, monday, wednesday)!!))
                .isNotNull()
        }
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
