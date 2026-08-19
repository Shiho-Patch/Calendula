package de.jeanlucmakiola.calendula.domain.ics

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class IcsRecurrenceTest {

    @Test
    fun `a well-formed rule is unchanged`() {
        assertThat(sanitizeRrule("FREQ=WEEKLY;INTERVAL=2;BYDAY=MO,WE"))
            .isEqualTo("FREQ=WEEKLY;INTERVAL=2;BYDAY=MO,WE")
    }

    @Test
    fun `the RRULE prefix is stripped`() {
        assertThat(sanitizeRrule("RRULE:FREQ=DAILY")).isEqualTo("FREQ=DAILY")
    }

    @Test
    fun `an empty list part is dropped`() {
        assertThat(sanitizeRrule("FREQ=WEEKLY;INTERVAL=1;BYDAY="))
            .isEqualTo("FREQ=WEEKLY;INTERVAL=1")
        assertThat(sanitizeRrule("FREQ=WEEKLY;BYDAY=MO,,WE;BYMONTH="))
            .isEqualTo("FREQ=WEEKLY;BYDAY=MO,WE")
    }

    @Test
    fun `a nonsensical INTERVAL is dropped, not fatal`() {
        assertThat(sanitizeRrule("FREQ=DAILY;INTERVAL=0")).isEqualTo("FREQ=DAILY")
        assertThat(sanitizeRrule("FREQ=DAILY;INTERVAL=every")).isEqualTo("FREQ=DAILY")
    }

    @Test
    fun `a rule without a usable FREQ is unsalvageable`() {
        assertThat(sanitizeRrule("INTERVAL=1;BYDAY=MO")).isNull()
        assertThat(sanitizeRrule("FREQ=FORTNIGHTLY;INTERVAL=1")).isNull()
        assertThat(sanitizeRrule("FREQ=;INTERVAL=1")).isNull()
        assertThat(sanitizeRrule("")).isNull()
        assertThat(sanitizeRrule(null)).isNull()
    }

    @Test
    fun `FREQ is normalised to upper case`() {
        assertThat(sanitizeRrule("freq=daily;count=3")).isEqualTo("FREQ=DAILY;COUNT=3")
    }

    @Test
    fun `unknown parts pass through untouched`() {
        assertThat(sanitizeRrule("FREQ=YEARLY;WKST=SU;X-THING=7"))
            .isEqualTo("FREQ=YEARLY;WKST=SU;X-THING=7")
    }
}
