package de.jeanlucmakiola.calendula.ui.settings

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.ui.input.pointer.PointerEventPass
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
 * The "past events" chooser for the Agenda (#69 follow-up).
 *
 * "Show / dim / hide" is the case where the word cannot carry the choice —
 * *how far* does dimmed fade, and what is left behind when finished events are
 * hidden? So a stand-in agenda day sits above the options and re-renders as you
 * pick one, using the very rows the Agenda draws: dimming is
 * [AgendaEventRow]'s own `dimmed` flag, and hiding drops the row exactly as
 * [de.jeanlucmakiola.calendula.ui.agenda.AgendaScreen] filters it — so the
 * preview cannot claim an effect the screen doesn't have.
 *
 * Like the Month style and App name pickers, selecting applies immediately and
 * leaves the picker open: closing would hide the thing the screen is for. Back
 * exits.
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
                // Applies straight away; the preview above is the confirmation.
                onClick = { onSelect(mode) },
            )
        }
    }
}

/**
 * A stand-in agenda day in a given [mode]: three events on today, the first two
 * already finished. The times are fixed rather than derived from the clock, so
 * the preview reads the same at any hour — what varies is only what [mode] does
 * to the two finished rows.
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
    // Hiding drops the finished rows outright; showing and dimming keep them,
    // and only dimming fades them — the same three-way split AgendaContent makes.
    val visible = if (mode == PastEventDisplay.HIDE) sample.filterNot { it.hasPassed } else sample

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clipToBounds()
            // A preview is a picture, not a control: swallow touches before the
            // rows' own clickables see them, and keep it out of the reading order —
            // the options below already say what is selected.
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                    }
                }
            }
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
 * Today's stand-in events, [titles] in order: two finished (morning, midday) and
 * one still to come.
 *
 * Colours are raw ARGB on purpose — that is what the provider hands out for an
 * event, so a theme token here would misrepresent what the agenda renders.
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
