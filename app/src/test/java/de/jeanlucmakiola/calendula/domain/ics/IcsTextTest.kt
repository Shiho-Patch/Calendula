package de.jeanlucmakiola.calendula.domain.ics

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class IcsTextTest {

    @Test
    fun `escapes backslash semicolon comma and newline`() {
        assertThat(escapeText("a\\b;c,d\ne"))
            .isEqualTo("a\\\\b\\;c\\,d\\ne")
    }

    @Test
    fun `backslash is escaped before its escape markers, not after`() {
        // A single backslash must become exactly one escaped backslash, not
        // accidentally combine with a following separator.
        assertThat(escapeText("\\;")).isEqualTo("\\\\\\;")
    }

    @Test
    fun `short line is returned unfolded`() {
        val line = "SUMMARY:short"
        assertThat(foldLine(line)).isEqualTo(line)
    }

    @Test
    fun `long line folds into physical lines of at most 75 octets`() {
        val line = "DESCRIPTION:" + "x".repeat(300)
        val folded = foldLine(line)

        val physical = folded.split(ICS_CRLF)
        assertThat(physical.size).isGreaterThan(1)
        physical.forEach { assertThat(it.toByteArray(Charsets.UTF_8).size).isAtMost(75) }
        // Every continuation line begins with the single folding space.
        physical.drop(1).forEach { assertThat(it).startsWith(" ") }
    }

    @Test
    fun `unfolding a folded line restores the original`() {
        val line = "DESCRIPTION:" + "abcdefg ".repeat(40).trim()
        val unfolded = foldLine(line).replace(ICS_CRLF + " ", "")
        assertThat(unfolded).isEqualTo(line)
    }

    @Test
    fun `folding never splits a multi-byte character`() {
        // 100 emoji (4 UTF-8 octets each) — a naive byte split would corrupt one.
        val line = "X-NOTE:" + "😀".repeat(100)
        val folded = foldLine(line)
        // The reassembled content must still decode to the same string.
        assertThat(folded.replace(ICS_CRLF + " ", "")).isEqualTo(line)
        folded.split(ICS_CRLF).forEach {
            assertThat(it.toByteArray(Charsets.UTF_8).size).isAtMost(75)
        }
    }
}
