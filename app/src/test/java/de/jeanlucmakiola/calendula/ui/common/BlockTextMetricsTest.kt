package de.jeanlucmakiola.calendula.ui.common

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class BlockTextMetricsTest {

    /** One trimmed labelMedium line at font scale 1: a 12sp glyph, leading off. */
    private val titleLine = 14.dp

    /** A block with room to spare, for the line arithmetic. */
    private fun metrics(height: Dp = 100.dp) = BlockTextMetrics(
        inset = blockTextInset(height, titleLine),
        available = height - blockTextInset(height, titleLine) * 2,
        titleLine = titleLine,
        titleLeading = 16.dp,
        timeLine = 13.dp,
    )

    @Test
    fun `a block with room keeps the full inset`() {
        assertThat(blockTextInset(height = 60.dp, titleLine = titleLine))
            .isEqualTo(BLOCK_TEXT_INSET)
    }

    @Test
    fun `the inset tapers instead of snapping as the block shrinks`() {
        // Half the inset left over is half the inset kept, so a pinch closes the
        // gap frame by frame rather than dropping it in one.
        assertThat(blockTextInset(height = titleLine + 2.dp, titleLine = titleLine))
            .isEqualTo(1.dp)
    }

    @Test
    fun `a block exactly one title tall spends nothing on padding`() {
        assertThat(blockTextInset(height = titleLine, titleLine = titleLine)).isEqualTo(0.dp)
    }

    @Test
    fun `a block shorter than a line never insets negatively`() {
        assertThat(blockTextInset(height = 4.dp, titleLine = titleLine)).isEqualTo(0.dp)
    }

    @Test
    fun `the title survives a block that used to be too short for it`() {
        // 18dp is under the old floor — a title line plus 2dp of inset at each
        // edge — and over the new one, which is the line on its own (#289).
        val m = metrics(height = 18.dp)
        assertThat(m.fitsTitle).isTrue()
    }

    @Test
    fun `a block under one line still drops the title`() {
        assertThat(metrics(height = 10.dp).fitsTitle).isFalse()
    }

    @Test
    fun `every title line after the first costs a whole line box`() {
        // The trim reaches the outer edges only, so the leading between two
        // lines is still there to pay for.
        val m = metrics()
        assertThat(m.titleHeight(1)).isEqualTo(14.dp)
        assertThat(m.titleHeight(2)).isEqualTo(30.dp)
        assertThat(m.titleHeight(3)).isEqualTo(46.dp)
        assertThat(m.titleHeight(0)).isEqualTo(0.dp)
    }

    @Test
    fun `the budget is what the block can actually draw, not what divides into it`() {
        val m = metrics()
        // Two lines cost 30dp: 29 buys one, 30 buys the second.
        assertThat(m.titleBudget(29.dp)).isEqualTo(1)
        assertThat(m.titleBudget(30.dp)).isEqualTo(2)
        assertThat(m.titleBudget(13.dp)).isEqualTo(0)
    }

    @Test
    fun `a budget line is always one the block can pay for`() {
        val m = metrics()
        (0..80).forEach { dp ->
            val within = dp.dp
            val budget = m.titleBudget(within)
            assertThat(m.titleHeight(budget)).isAtMost(within)
        }
    }
}
