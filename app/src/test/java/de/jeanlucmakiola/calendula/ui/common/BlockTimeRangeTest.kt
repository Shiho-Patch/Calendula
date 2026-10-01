package de.jeanlucmakiola.calendula.ui.common

import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class BlockTimeRangeTest {

    /** Fits whatever is at most [chars] characters wide. */
    private fun upTo(chars: Int): (String) -> Boolean = { it.length <= chars }

    @Test
    fun `a range that fits stays on one line`() {
        assertThat(blockTimeRange("6:00 PM", "7:00 PM", upTo(20)))
            .isEqualTo("6:00\u00A0PM–7:00\u00A0PM")
    }

    @Test
    fun `a narrow column hangs the dash off the start time`() {
        assertThat(blockTimeRange("6:00 PM", "7:00 PM", upTo(8)))
            .isEqualTo("6:00\u00A0PM–\n7:00\u00A0PM")
    }

    @Test
    fun `the dash leads the end time when only that side has room`() {
        assertThat(blockTimeRange("10:00 PM", "7:00 PM", upTo(8)))
            .isEqualTo("10:00\u00A0PM\n–7:00\u00A0PM")
    }

    @Test
    fun `with room for neither dash the times just stack`() {
        assertThat(blockTimeRange("6:00 PM", "7:00 PM", upTo(7)))
            .isEqualTo("6:00\u00A0PM\n7:00\u00A0PM")
    }

    @Test
    fun `a time too wide for the column keeps its space to wrap at`() {
        assertThat(blockTimeRange("6:00 PM", "7:00 PM", upTo(5)))
            .isEqualTo("6:00 PM–\u200B7:00 PM")
    }

    @Test
    fun `the label takes every whole line the spare height holds`() {
        assertThat(timeLinesWithin(spare = 0.dp, timeLine = 13.dp)).isEqualTo(1)
        assertThat(timeLinesWithin(spare = 13.dp, timeLine = 13.dp)).isEqualTo(2)
        assertThat(timeLinesWithin(spare = 40.dp, timeLine = 13.dp)).isEqualTo(4)
    }
}
