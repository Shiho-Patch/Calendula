package de.jeanlucmakiola.calendula.ui.common

import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class HourGridTest {

    /** The hour cell's 4dp corner at a 3x density. */
    private val maxRadius = 12f

    @Test
    fun `a tall cell rounds to the full radius`() {
        // A Regular 56dp hour on a 3x screen, in a phone-width day column.
        assertThat(hourCellRadiusPx(cellHeight = 162f, cellWidth = 900f, maxRadius = maxRadius))
            .isEqualTo(maxRadius)
    }

    @Test
    fun `a short cell rounds to half its height, never past a lozenge`() {
        // Fit-the-day on a small viewport: the hour is shorter than the radius.
        val radius = hourCellRadiusPx(cellHeight = 18f, cellWidth = 900f, maxRadius = maxRadius)
        assertThat(radius).isEqualTo(9f)
    }

    @Test
    fun `a narrow cell rounds to half its width`() {
        // Seven columns on a small phone, so the cell is narrower than it is tall.
        assertThat(hourCellRadiusPx(cellHeight = 162f, cellWidth = 18f, maxRadius = maxRadius))
            .isEqualTo(9f)
    }

    @Test
    fun `a degenerate cell asks for no radius at all`() {
        assertThat(hourCellRadiusPx(cellHeight = -4f, cellWidth = 900f, maxRadius = maxRadius))
            .isEqualTo(0f)
    }

    @Test
    fun `a seated block gives up the half-gap at an end of its own`() {
        assertThat(hourCellBlockInset(show = true, cut = false)).isEqualTo(HOUR_CELL_INSET)
    }

    @Test
    fun `an end cut at midnight stays against the column edge`() {
        assertThat(hourCellBlockInset(show = true, cut = true)).isEqualTo(0.dp)
    }

    @Test
    fun `with the grid off nothing is given up`() {
        assertThat(hourCellBlockInset(show = false, cut = false)).isEqualTo(0.dp)
    }
}
