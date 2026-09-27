package de.jeanlucmakiola.calendula.ui.common

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Whether the week/day timeline seats each hour in its own cell, from the
 * `show_hour_lines` preference. Provided once at the app root (like
 * [LocalUse24HourFormat]) so the timeline reads it without ViewModel plumbing.
 * Defaults to off — the historical flat column.
 */
val LocalShowHourGrid = staticCompositionLocalOf { false }

/** Gap between two hour cells, matching the month grid's gap between day cells. */
val HOUR_CELL_GAP = 2.dp

/** Half a gap: what a cell — and a block seated in it — gives up at each edge. */
val HOUR_CELL_INSET = HOUR_CELL_GAP / 2

/**
 * The half-gap a block seated in the grid gives up at one of its ends, so an
 * on-the-hour event fills its cell instead of overhanging the seam into its
 * neighbours'. Zero with the grid off, and zero at an end [cut] at midnight:
 * that edge is squared off against the column's own, and stopping a half-gap
 * short of it would leave the block floating there rather than running off.
 */
fun hourCellBlockInset(show: Boolean, cut: Boolean): Dp =
    if (show && !cut) HOUR_CELL_INSET else 0.dp

/**
 * Corner radius of an hour cell: the event chip's own, so the grid never rounds
 * harder than the blocks it seats. The month grid's 12dp belongs to a cell many
 * times the size — on an hour cell it out-rounds its own content.
 */
private val HOUR_CELL_CORNER = EVENT_CHIP_CORNER

/**
 * Radius an hour cell of [cellHeight] by [cellWidth] pixels may round to. The
 * hour pitch runs from a fit-the-day sliver up to [MAX_PINCH_HOUR_HEIGHT] and a
 * week column is a seventh of the screen, so the radius is held to half the
 * shorter side — the point past which the corners would meet and the cell turn
 * into a lozenge.
 */
internal fun hourCellRadiusPx(cellHeight: Float, cellWidth: Float, maxRadius: Float): Float =
    minOf(maxRadius, cellHeight / 2f, cellWidth / 2f).coerceAtLeast(0f)

/**
 * Seat each of the day's 24 hours in its own rounded cell when [show] is true,
 * so the hour boundary reads as a seam between two surfaces rather than a line
 * drawn across one. Applied to a day column's content, so the cells sit over the
 * column background but beneath the event blocks — blocks stay in a continuous
 * coordinate space and keep spanning cells. [hourHeightPx] is one hour's pixel
 * height; [color] is resolved by the caller from the theme. [offsetPx] shifts
 * the cells up, for a fixed window over a scrolled column.
 */
fun Modifier.hourGridCells(
    show: Boolean,
    hourHeightPx: Float,
    color: Color,
    offsetPx: () -> Float = { 0f },
): Modifier =
    if (!show) {
        this
    } else {
        drawBehind {
            val inset = HOUR_CELL_INSET.toPx()
            val cellHeight = hourHeightPx - inset * 2f
            if (cellHeight <= 0f) return@drawBehind
            val radius = CornerRadius(
                hourCellRadiusPx(cellHeight, size.width, HOUR_CELL_CORNER.toPx()),
            )
            val cellSize = Size(size.width, cellHeight)
            val top = inset - offsetPx()
            for (hour in 0 until 24) {
                drawRoundRect(
                    color = color,
                    topLeft = Offset(0f, hour * hourHeightPx + top),
                    size = cellSize,
                    cornerRadius = radius,
                )
            }
        }
    }
