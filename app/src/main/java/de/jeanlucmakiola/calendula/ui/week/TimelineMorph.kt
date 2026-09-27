package de.jeanlucmakiola.calendula.ui.week

import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.Stable
import kotlinx.coroutines.flow.StateFlow
import kotlinx.datetime.LocalDate

/**
 * A switch between two timeline views in progress (#184): the page on screen
 * when it started, the page it ends on, and how far between the two it is.
 */
@Stable
internal class TimelineMorph(
    val fromKind: TimelineKind,
    val toKind: TimelineKind,
    val from: DaySpan,
    val to: DaySpan,
    val fromState: StateFlow<WeekUiState>,
    val toState: StateFlow<WeekUiState>,
) {
    val progress = Animatable(0f)

    /** Where each day's events come from: the page it was on, else the page it goes to. */
    fun sourceOf(date: LocalDate): Side = if (date in from.start..from.end) Side.From else Side.To

    enum class Side { From, To }
}
