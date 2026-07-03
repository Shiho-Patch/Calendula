package de.jeanlucmakiola.calendula.domain.contacts

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class ContactSpecialDateTest {

    @Test
    fun `parses a full yyyy-MM-dd date with year`() {
        val parts = parseContactEventDate("1990-05-14")
        assertThat(parts).isEqualTo(ContactDateParts(year = 1990, month = 5, day = 14))
    }

    @Test
    fun `parses a year-less --MM-dd date`() {
        val parts = parseContactEventDate("--05-14")
        assertThat(parts).isEqualTo(ContactDateParts(year = null, month = 5, day = 14))
    }

    @Test
    fun `parses a compact yyyyMMdd date`() {
        val parts = parseContactEventDate("19900514")
        assertThat(parts).isEqualTo(ContactDateParts(year = 1990, month = 5, day = 14))
    }

    @Test
    fun `parses a year-less MM-dd date without the double-dash prefix`() {
        val parts = parseContactEventDate("12-25")
        assertThat(parts).isEqualTo(ContactDateParts(year = null, month = 12, day = 25))
    }

    @Test
    fun `keeps a year-less Feb 29 by validating against a leap anchor`() {
        val parts = parseContactEventDate("--02-29")
        assertThat(parts).isEqualTo(ContactDateParts(year = null, month = 2, day = 29))
    }

    @Test
    fun `keeps Feb 29 in a real leap year`() {
        assertThat(parseContactEventDate("2000-02-29"))
            .isEqualTo(ContactDateParts(year = 2000, month = 2, day = 29))
    }

    @Test
    fun `rejects Feb 29 in a non-leap year`() {
        assertThat(parseContactEventDate("1999-02-29")).isNull()
    }

    @Test
    fun `rejects an impossible month or day`() {
        assertThat(parseContactEventDate("2020-13-01")).isNull()
        assertThat(parseContactEventDate("2020-06-31")).isNull()
        assertThat(parseContactEventDate("--00-10")).isNull()
    }

    @Test
    fun `rejects blank, null and garbage input`() {
        assertThat(parseContactEventDate(null)).isNull()
        assertThat(parseContactEventDate("")).isNull()
        assertThat(parseContactEventDate("   ")).isNull()
        assertThat(parseContactEventDate("not-a-date")).isNull()
        assertThat(parseContactEventDate("2020")).isNull()
    }
}
