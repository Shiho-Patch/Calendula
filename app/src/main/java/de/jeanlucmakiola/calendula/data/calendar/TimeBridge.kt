package de.jeanlucmakiola.calendula.data.calendar

import kotlin.time.Instant

fun Long.toKotlinInstantFromEpochMillis(): Instant = Instant.fromEpochMilliseconds(this)

fun Instant.toEpochMillis(): Long = toEpochMilliseconds()
