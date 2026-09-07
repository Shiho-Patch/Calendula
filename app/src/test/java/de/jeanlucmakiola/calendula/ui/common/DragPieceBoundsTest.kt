package de.jeanlucmakiola.calendula.ui.common

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * The floating copy is the size of the block it lifted off, hour grid or not —
 * a copy a half-gap taller at each end has room for a time label the block
 * itself had to drop, so the label appears on long-press and goes on drop
 * (#267).
 */
class DragPieceBoundsTest {

    /** A Regular 56dp hour on a 3x screen. */
    private val hourPx = 168f

    /** The grid's half-gap, 1dp at 3x. */
    private val insetPx = 3f

    private fun slice(
        startMin: Int,
        spanMin: Int,
        continuesBefore: Boolean = false,
        continuesAfter: Boolean = false,
    ) = DragSlice(
        dayOffset = 0,
        startMin = startMin,
        spanMin = spanMin,
        continuesBefore = continuesBefore,
        continuesAfter = continuesAfter,
    )

    @Test
    fun `with the grid off a piece keeps its raw placement`() {
        val bounds = dragPieceBounds(slice(startMin = 540, spanMin = 60), hourPx, insetPx = 0f)
        assertThat(bounds.top).isEqualTo(9 * hourPx)
        assertThat(bounds.height).isEqualTo(hourPx)
    }

    @Test
    fun `with the grid on a piece gives up a half-gap at each end`() {
        val bounds = dragPieceBounds(slice(startMin = 540, spanMin = 60), hourPx, insetPx)
        assertThat(bounds.top).isEqualTo(9 * hourPx + insetPx)
        assertThat(bounds.height).isEqualTo(hourPx - insetPx * 2)
    }

    @Test
    fun `an end cut at midnight keeps none of the inset`() {
        val head = dragPieceBounds(slice(startMin = 1200, spanMin = 240, continuesAfter = true), hourPx, insetPx)
        assertThat(head.top).isEqualTo(20 * hourPx + insetPx)
        assertThat(head.height).isEqualTo(4 * hourPx - insetPx)

        val tail = dragPieceBounds(slice(startMin = 0, spanMin = 480, continuesBefore = true), hourPx, insetPx)
        assertThat(tail.top).isEqualTo(0f)
        assertThat(tail.height).isEqualTo(8 * hourPx - insetPx)
    }

    @Test
    fun `a whole day cut at both ends fills its column`() {
        val bounds = dragPieceBounds(
            slice(startMin = 0, spanMin = 1440, continuesBefore = true, continuesAfter = true),
            hourPx,
            insetPx,
        )
        assertThat(bounds.top).isEqualTo(0f)
        assertThat(bounds.height).isEqualTo(24 * hourPx)
    }

    @Test
    fun `a very short piece is floored before the inset comes off`() {
        val bounds = dragPieceBounds(slice(startMin = 540, spanMin = 5), hourPx, insetPx)
        assertThat(bounds.height).isEqualTo(MIN_EVENT_FRACTION * hourPx - insetPx * 2)
    }

    @Test
    fun `an inset taller than the piece leaves no height at all`() {
        val bounds = dragPieceBounds(slice(startMin = 0, spanMin = 60), hourPx = 4f, insetPx = insetPx)
        assertThat(bounds.height).isEqualTo(0f)
    }
}
