package de.jeanlucmakiola.calendula.ui.common

import androidx.compose.runtime.compositionLocalOf
import kotlin.time.Instant

/**
 * The cut-off instant before which a calendar event counts as "completed" and is
 * drawn dimmed in the month/week grids — i.e. the current wall-clock minute when
 * the "dim completed events" setting is on, or `null` when it is off (nothing
 * dims). Provided per grid screen so only the event chips that read it recompose
 * as the minute ticks, mirroring [LocalShowHourLines] / [LocalUse24HourFormat].
 */
val LocalDimCutoff = compositionLocalOf<Instant?> { null }

/** Opacity applied to a completed/past event chip when it is dimmed. */
const val EventDimAlpha = 0.4f
