package de.jeanlucmakiola.calendula.ui.month

import androidx.compose.ui.unit.dp

// The month grid's chip geometry, together in one file: these derive from each
// other, and a top-level val reading one across a file boundary would resolve
// against whichever class the JVM happened to initialise first.

/** Gap between a day cell and its neighbours. */
internal val CELL_GAP = 2.dp

/** Padding between a month chip's edge and its text. */
internal val MONTH_CHIP_TEXT_PADDING = 4.dp

/** A chip's own inset inside its day cell, on top of the cell's gap. */
internal val MONTH_CHIP_INSET = CELL_GAP + 1.dp

/** Horizontal space a chip spends on chrome rather than on text, both sides. */
internal val MONTH_CHIP_CHROME = (MONTH_CHIP_INSET + MONTH_CHIP_TEXT_PADDING) * 2f
