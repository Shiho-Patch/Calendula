package de.jeanlucmakiola.calendula.ui.month

import androidx.compose.ui.unit.dp

// The month grid's chip geometry, together in one file: these derive from each
// other, and a top-level val reading one across a file boundary would resolve
// against whichever class the JVM happened to initialise first.

/** Gap between a day cell and its neighbours. */
internal val CELL_GAP = 2.dp

/** Padding between a month chip's edge and its text. */
internal val MONTH_CHIP_TEXT_PADDING = 3.dp

/**
 * A chip's own inset inside its day cell. The cell's gap and no more: a day
 * column on a phone is around fifty dp, and the chip was spending a quarter of
 * it on chrome before a single glyph. The cells keep their full separation from
 * each other — what the grid reads as breathing room — and only the chip inside
 * one takes the width back (#212).
 */
internal val MONTH_CHIP_INSET = CELL_GAP

/** Horizontal space a chip spends on chrome rather than on text, both sides. */
internal val MONTH_CHIP_CHROME = (MONTH_CHIP_INSET + MONTH_CHIP_TEXT_PADDING) * 2f
