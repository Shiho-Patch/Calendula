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
    fun `title substitutes name and year`() {
        assertThat(renderSpecialDateTitle("{name}'s birthday ({year})", "Jane", 1990))
            .isEqualTo("Jane's birthday (1990)")
    }

    @Test
    fun `title drops an empty year parenthesis and tidies spacing`() {
        assertThat(renderSpecialDateTitle("{name}'s birthday ({year})", "Jane", null))
            .isEqualTo("Jane's birthday")
    }
}
