package de.jeanlucmakiola.calendula.ui.settings

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import de.jeanlucmakiola.calendula.R
import de.jeanlucmakiola.calendula.data.prefs.PastEventDisplay
import de.jeanlucmakiola.calendula.domain.EventInstance
import de.jeanlucmakiola.calendula.ui.agenda.AgendaDayHeader
import de.jeanlucmakiola.calendula.ui.agenda.AgendaEventRow
import de.jeanlucmakiola.calendula.ui.common.PickerDescription
import de.jeanlucmakiola.floret.components.FullScreenPicker
import de.jeanlucmakiola.floret.components.GroupedRow
import de.jeanlucmakiola.floret.components.SelectedCheck
import de.jeanlucmakiola.floret.components.positionOf
import de.jeanlucmakiola.floret.identity.rememberReduceMotion
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

/**
 * The "past events" chooser for the Agenda (#69 follow-up). A stand-in agenda
 * day sits above the options and re-renders as you pick one, using the Agenda's
 * own rows and filtering. A preview picker, so it stays open on selection.
 */
@Composable
internal fun PastEventsPicker(
    selected: PastEventDisplay,
    onSelect: (PastEventDisplay) -> Unit,
    onDismiss: () -> Unit,
) {
    val options = PastEventDisplay.entries
    val reduceMotion = rememberReduceMotion()
    FullScreenPicker(
        title = stringResource(R.string.settings_past_events),
        onDismiss = onDismiss,
        predictiveBack = true,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .height(PREVIEW_HEIGHT),
            contentAlignment = Alignment.TopCenter,
        ) {
            Crossfade(
                targetState = selected,
                animationSpec = if (reduceMotion) snap() else tween(durationMillis = 250),
                label = "past-events-preview",
            ) { shown ->
                Box(
                    modifier = Modifier
                        .clip(PREVIEW_SHAPE)
                        .background(MaterialTheme.colorScheme.surface)
                        .clipToBounds(),
                ) {
                    PastEventsPreview(mode = shown)
                }
            }
        }
        PickerDescription(stringResource(R.string.settings_past_events_hint))
        options.forEachIndexed { index, mode ->
            val isSelected = mode == selected
            GroupedRow(
                title = stringResource(pastEventDisplayLabelRes(mode)),
                position = positionOf(index, options.size),
                selected = isSelected,
                trailing = if (isSelected) {
                    { SelectedCheck() }
                } else {
                    null
                },
                onClick = { onSelect(mode) },
            )
        }
    }
}

/**
 * A stand-in agenda day in a given [mode]: three events on today, the first two
 * finished. Times are fixed rather than clock-derived, so it reads the same at
 * any hour.
 */
@Composable
private fun PastEventsPreview(mode: PastEventDisplay, modifier: Modifier = Modifier) {
    val zone = remember { TimeZone.currentSystemDefault() }
    val today = remember(zone) { Clock.System.now().toLocalDateTime(zone).date }
    val titles = listOf(
        stringResource(R.string.settings_past_events_sample_morning),
        stringResource(R.string.settings_past_events_sample_midday),
        stringResource(R.string.settings_past_events_sample_evening),
    )
    val sample = remember(today, zone, titles) { samplePastDay(today, zone, titles) }
    // The same three-way split AgendaContent makes.
    val visible = if (mode == PastEventDisplay.HIDE) sample.filterNot { it.hasPassed } else sample

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clipToBounds()
            // A picture, not a control: take the taps the fake rows would
            // otherwise handle, and keep it out of the reading order. Taps only —
            // consuming drags would stop the picker scrolling from here.
            .pointerInput(Unit) { detectTapGestures { } }
            .clearAndSetSemantics { },
    ) {
        AgendaDayHeader(date = today, today = today, onOpenDay = {})
        visible.forEachIndexed { index, row ->
            AgendaEventRow(
                event = row.event,
                day = today,
                zone = zone,
                position = positionOf(index, visible.size),
                dimmed = mode == PastEventDisplay.DIM && row.hasPassed,
                onClick = {},
            )
        }
    }
}

/** One stand-in row plus whether it counts as finished. */
private class SampleRow(val event: EventInstance, val hasPassed: Boolean)

/**
 * Today's stand-in events, [titles] in order: two finished and one to come.
 * Colours are raw ARGB, as the provider hands them out.
 */
private fun samplePastDay(today: LocalDate, zone: TimeZone, titles: List<String>): List<SampleRow> =
    listOf(
        sampleRow(1, titles[0], today, zone, 9, 10, 0xFF3F7BD4.toInt(), hasPassed = true),
        sampleRow(2, titles[1], today, zone, 12, 13, 0xFFCE5B4C.toInt(), hasPassed = true),
        sampleRow(3, titles[2], today, zone, 18, 19, 0xFF4E9A6A.toInt(), hasPassed = false),
    )

private fun sampleRow(
    id: Long,
    title: String,
    day: LocalDate,
    zone: TimeZone,
    startHour: Int,
    endHour: Int,
    color: Int,
    hasPassed: Boolean,
): SampleRow = SampleRow(
    event = EventInstance(
        instanceId = id,
        eventId = id,
        calendarId = 1L,
        title = title,
        start = day.atTime(startHour, 0).toInstant(zone),
        end = day.atTime(endHour, 0).toInstant(zone),
        isAllDay = false,
        color = color,
        location = null,
    ),
    hasPassed = hasPassed,
)

/** Room for the day header plus its three two-line rows, so hiding shortens the
 *  list inside a box that keeps its height instead of the page jumping a row. */
private val PREVIEW_HEIGHT = 288.dp
private val PREVIEW_SHAPE = RoundedCornerShape(12.dp)
