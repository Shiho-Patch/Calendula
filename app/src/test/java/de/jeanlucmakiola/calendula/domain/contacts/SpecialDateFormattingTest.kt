package de.jeanlucmakiola.calendula.domain.contacts

import com.google.common.truth.Truth.assertThat
import kotlinx.datetime.LocalDate
import org.junit.jupiter.api.Test

class SpecialDateFormattingTest {

    private fun birthday(month: Int, day: Int, year: Int?) = ContactSpecialDate(
        lookupKey = "k",
        displayName = "Jane",
        type = SpecialDateType.Birthday,
        month = month,
        day = day,
        year = year,
    )

    @Test
    fun `uid is deterministic and namespaced by type`() {
        assertThat(managedEventUid(SpecialDateType.Birthday, "abc"))
            .isEqualTo("contact-birthday:abc@calendula")
        assertThat(managedEventUid(SpecialDateType.Anniversary, "abc"))
            .isEqualTo("contact-anniversary:abc@calendula")
    }

    @Test
    fun `anchor date uses the known year`() {
        assertThat(birthday(5, 14, 1990).anchorDate()).isEqualTo(LocalDate(1990, 5, 14))
    }

    @Test
    fun `anchor date falls back to a leap year when year-less`() {
        assertThat(birthday(2, 29, null).anchorDate()).isEqualTo(LocalDate(YEARLESS_ANCHOR_YEAR, 2, 29))
    }

    @Test
    fun `age is null when the year is unknown`() {
        assertThat(birthday(5, 14, null).ageAtNextOccurrence(LocalDate(2026, 1, 1))).isNull()
    }

    @Test
    fun `age counts the upcoming birthday when it is still ahead this year`() {
        // Born 1990-05-14; today 2026-03-01 → next birthday 2026 → turns 36.
        assertThat(birthday(5, 14, 1990).ageAtNextOccurrence(LocalDate(2026, 3, 1))).isEqualTo(36)
    }

    @Test
    fun `age rolls to next year once the birthday has passed`() {
        // Born 1990-05-14; today 2026-06-01 → next birthday 2027 → turns 37.
        assertThat(birthday(5, 14, 1990).ageAtNextOccurrence(LocalDate(2026, 6, 1))).isEqualTo(37)
    }

    @Test
    fun `age counts today's birthday as this year`() {
        assertThat(birthday(5, 14, 1990).ageAtNextOccurrence(LocalDate(2026, 5, 14))).isEqualTo(36)
    }

    @Test
    fun `title substitutes name and age`() {
        assertThat(renderSpecialDateTitle("{name}'s birthday ({age})", "Jane", 30))
            .isEqualTo("Jane's birthday (30)")
    }

    @Test
    fun `title drops an empty age parenthesis and tidies spacing`() {
        assertThat(renderSpecialDateTitle("{name}'s birthday ({age})", "Jane", null))
            .isEqualTo("Jane's birthday")
    }
}
