package de.jeanlucmakiola.calendula.ui.common

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/** The corner radius every event chip keeps on each edge the event doesn't cross. */
val EVENT_CHIP_CORNER = 4.dp

/** An event chip that is cut on no edge — an all-day bar, a floating copy. */
val EventChipShape = RoundedCornerShape(EVENT_CHIP_CORNER)

/**
 * A timed block's corners: square on whichever edge the event runs past, so a
 * cut edge reads as "this carries on" rather than as the event's own end. The
 * vertical counterpart of [monthBarShape].
 */
fun timedBlockShape(continuesBefore: Boolean, continuesAfter: Boolean): RoundedCornerShape =
    RoundedCornerShape(
        topStart = if (continuesBefore) 0.dp else EVENT_CHIP_CORNER,
        topEnd = if (continuesBefore) 0.dp else EVENT_CHIP_CORNER,
        bottomStart = if (continuesAfter) 0.dp else EVENT_CHIP_CORNER,
        bottomEnd = if (continuesAfter) 0.dp else EVENT_CHIP_CORNER,
    )

/**
 * A month/all-day bar's corners: square on whichever side the event runs past
 * the row it is drawn in.
 */
fun monthBarShape(continuesLeft: Boolean, continuesRight: Boolean): RoundedCornerShape =
    RoundedCornerShape(
        topStart = if (continuesLeft) 0.dp else EVENT_CHIP_CORNER,
        bottomStart = if (continuesLeft) 0.dp else EVENT_CHIP_CORNER,
        topEnd = if (continuesRight) 0.dp else EVENT_CHIP_CORNER,
        bottomEnd = if (continuesRight) 0.dp else EVENT_CHIP_CORNER,
    )
