package de.jeanlucmakiola.calendula.domain.ics

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class IcsRecurrenceTest {

    @Test
    fun `a well-formed rule is unchanged`() {
        val result = sanitizeRrule("FREQ=WEEKLY;INTERVAL=2;BYDAY=MO,WE")

        assertThat(result.rule).isEqualTo("FREQ=WEEKLY;INTERVAL=2;BYDAY=MO,WE")
        assertThat(result.repaired).isFalse()
    }

    @Test
    fun `the RRULE prefix is stripped`() {
        assertThat(sanitizeRrule("RRULE:FREQ=DAILY").rule).isEqualTo("FREQ=DAILY")
    }

    @Test
    fun `an empty list part is dropped`() {
        val bareByDay = sanitizeRrule("FREQ=WEEKLY;INTERVAL=1;BYDAY=")
        assertThat(bareByDay.rule).isEqualTo("FREQ=WEEKLY;INTERVAL=1")
        assertThat(bareByDay.repaired).isTrue()

        assertThat(sanitizeRrule("FREQ=WEEKLY;BYDAY=MO,,WE;BYMONTH=").rule)
            .isEqualTo("FREQ=WEEKLY;BYDAY=MO,WE")
    }

    @Test
    fun `a nonsensical INTERVAL or COUNT is dropped, not fatal`() {
        assertThat(sanitizeRrule("FREQ=DAILY;INTERVAL=0").rule).isEqualTo("FREQ=DAILY")
        assertThat(sanitizeRrule("FREQ=DAILY;INTERVAL=every").rule).isEqualTo("FREQ=DAILY")
        assertThat(sanitizeRrule("FREQ=DAILY;COUNT=0").rule).isEqualTo("FREQ=DAILY")
    }

    @Test
    fun `a rule without a usable FREQ is unsalvageable`() {
        assertThat(sanitizeRrule("INTERVAL=1;BYDAY=MO").rule).isNull()
        assertThat(sanitizeRrule("FREQ=FORTNIGHTLY;INTERVAL=1").rule).isNull()
        assertThat(sanitizeRrule("FREQ=;INTERVAL=1").rule).isNull()
        assertThat(sanitizeRrule("").rule).isNull()
        assertThat(sanitizeRrule(null).rule).isNull()
    }

    @Test
    fun `no rule at all is not a repair`() {
        assertThat(sanitizeRrule(null).repaired).isFalse()
        assertThat(sanitizeRrule("").repaired).isFalse()
        assertThat(sanitizeRrule("FREQ=FORTNIGHTLY").repaired).isTrue()
    }

    @Test
    fun `normalising is not repairing`() {
        // Case and a trailing separator change the text but lose nothing, and
        // telling the user their file was faulty over either is crying wolf.
        val lowercase = sanitizeRrule("freq=daily;count=3")
        assertThat(lowercase.rule).isEqualTo("FREQ=DAILY;COUNT=3")
        assertThat(lowercase.repaired).isFalse()

        val trailing = sanitizeRrule("FREQ=DAILY;")
        assertThat(trailing.rule).isEqualTo("FREQ=DAILY")
        assertThat(trailing.repaired).isFalse()
    }

    @Test
    fun `parts the provider's parser has no entry for are dropped`() {
        // EventRecurrence.parse throws on any part name outside its own table,
        // so passing one on would cost the whole event (Codeberg #225).
        val result = sanitizeRrule("FREQ=MONTHLY;RSCALE=GREGORIAN;BYMONTHDAY=15")

        assertThat(result.rule).isEqualTo("FREQ=MONTHLY;BYMONTHDAY=15")
        assertThat(result.repaired).isTrue()
        assertThat(sanitizeRrule("FREQ=YEARLY;X-THING=7").rule).isEqualTo("FREQ=YEARLY")
    }

    @Test
    fun `WKST survives, a nonsense weekday does not`() {
        assertThat(sanitizeRrule("FREQ=WEEKLY;WKST=SU").rule).isEqualTo("FREQ=WEEKLY;WKST=SU")
        assertThat(sanitizeRrule("FREQ=WEEKLY;WKST=XX").rule).isEqualTo("FREQ=WEEKLY")
    }

    @Test
    fun `a BYDAY item that isn't a weekday is dropped`() {
        assertThat(sanitizeRrule("FREQ=MONTHLY;BYDAY=-1FR,1,MO").rule)
            .isEqualTo("FREQ=MONTHLY;BYDAY=-1FR,MO")
        assertThat(sanitizeRrule("FREQ=WEEKLY;BYDAY=mo,tu").rule)
            .isEqualTo("FREQ=WEEKLY;BYDAY=MO,TU")
    }

    @Test
    fun `a zero is dropped from the by-position parts only`() {
        assertThat(sanitizeRrule("FREQ=MONTHLY;BYMONTHDAY=0,15").rule)
            .isEqualTo("FREQ=MONTHLY;BYMONTHDAY=15")
        assertThat(sanitizeRrule("FREQ=DAILY;BYHOUR=0,9").rule)
            .isEqualTo("FREQ=DAILY;BYHOUR=0,9")
    }

    @Test
    fun `an out-of-range numeric item is dropped`() {
        // EventRecurrence.parseNumberList range-checks every one of these and
        // throws out of insert, so being a number isn't enough (Codeberg #225).
        assertThat(sanitizeRrule("FREQ=MONTHLY;BYMONTHDAY=15,32").rule)
            .isEqualTo("FREQ=MONTHLY;BYMONTHDAY=15")
        assertThat(sanitizeRrule("FREQ=DAILY;BYHOUR=25").rule).isEqualTo("FREQ=DAILY")
        assertThat(sanitizeRrule("FREQ=YEARLY;BYMONTH=13").rule).isEqualTo("FREQ=YEARLY")
        assertThat(sanitizeRrule("FREQ=YEARLY;BYWEEKNO=60").rule).isEqualTo("FREQ=YEARLY")
        assertThat(sanitizeRrule("FREQ=YEARLY;BYYEARDAY=400").rule).isEqualTo("FREQ=YEARLY")
        assertThat(sanitizeRrule("FREQ=DAILY;BYSECOND=60").rule).isEqualTo("FREQ=DAILY")
        assertThat(sanitizeRrule("FREQ=DAILY;BYHOUR=25").repaired).isTrue()
    }

    @Test
    fun `the edges of each range survive`() {
        assertThat(sanitizeRrule("FREQ=MONTHLY;BYMONTHDAY=-31,31").repaired).isFalse()
        assertThat(sanitizeRrule("FREQ=YEARLY;BYYEARDAY=-366,366;BYWEEKNO=-53,53").repaired)
            .isFalse()
        assertThat(sanitizeRrule("FREQ=DAILY;BYHOUR=23;BYMINUTE=59;BYSECOND=59").repaired)
            .isFalse()
        // BYSETPOS is the one part the provider leaves unbounded.
        assertThat(sanitizeRrule("FREQ=MONTHLY;BYDAY=MO;BYSETPOS=-1,400").rule)
            .isEqualTo("FREQ=MONTHLY;BYDAY=MO;BYSETPOS=-1,400")
    }

    @Test
    fun `an UNTIL that isn't a timestamp is dropped`() {
        assertThat(sanitizeRrule("FREQ=DAILY;UNTIL=20260819T235959Z").rule)
            .isEqualTo("FREQ=DAILY;UNTIL=20260819T235959Z")
        assertThat(sanitizeRrule("FREQ=DAILY;UNTIL=2026-08-19").rule).isEqualTo("FREQ=DAILY")
    }
}
