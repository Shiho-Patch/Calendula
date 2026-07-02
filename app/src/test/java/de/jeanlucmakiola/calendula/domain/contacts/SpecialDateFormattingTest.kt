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

    private fun custom(label: String?, month: Int, day: Int) = ContactSpecialDate(
        lookupKey = "k",
        displayName = "Jane",
        type = SpecialDateType.Custom,
        month = month,
        day = day,
        year = null,
        label = label,
    )

    @Test
    fun `birthday and anniversary uids ignore the discriminator`() {
        assertThat(birthday(5, 14, 1990).managedUid()).isEqualTo("contact-birthday:k@calendula")
    }

    @Test
    fun `two custom dates on one contact get distinct uids by label`() {
        assertThat(custom("Wedding", 6, 10).managedUid())
            .isEqualTo("contact-custom:k:wedding@calendula")
        assertThat(custom("Graduation", 9, 1).managedUid())
            .isNotEqualTo(custom("Wedding", 6, 10).managedUid())
    }

    @Test
    fun `label-less custom dates fall back to month-day so distinct dates survive`() {
        assertThat(custom(null, 6, 10).managedUid())
            .isEqualTo("contact-custom:k:06-10@calendula")
        assertThat(custom(null, 9, 1).managedUid())
            .isNotEqualTo(custom(null, 6, 10).managedUid())
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
