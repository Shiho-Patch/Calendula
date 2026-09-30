package de.jeanlucmakiola.calendula.domain

import kotlinx.datetime.TimeZone

/** Lower values appear first in lists and get the upper visible lanes in grids. */
fun EventInstance.displayPriority(zone: TimeZone): Int = when {
    isDeclined -> 3
    spansMultipleDays(zone) -> 0
    isAllDay -> 1
    else -> 2
}

/** Shared order for agenda and split day lists. Grid bars add their lane geometry. */
fun eventDisplayComparator(zone: TimeZone): Comparator<EventInstance> =
    compareBy<EventInstance> { it.displayPriority(zone) }
        .thenBy { it.start }
        .thenBy { it.title }
        .thenBy { it.instanceId }
