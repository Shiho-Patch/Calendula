package de.jeanlucmakiola.calendula.ui.onboarding

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import de.jeanlucmakiola.calendula.R
import de.jeanlucmakiola.calendula.domain.CalendarSource
import de.jeanlucmakiola.calendula.domain.hasVisibilitySwitch
import de.jeanlucmakiola.calendula.ui.common.CalendarColorChip
import de.jeanlucmakiola.floret.components.GroupedListInset
import de.jeanlucmakiola.floret.components.GroupedRow
import de.jeanlucmakiola.floret.components.OnboardingScaffold
import de.jeanlucmakiola.floret.components.OnboardingSpace
import de.jeanlucmakiola.floret.components.Position

/**
 * Wizard step for a device whose every calendar is switched off (#287), which
 * would otherwise open the app on a blank grid with nothing saying why.
 *
 * The calendars are listed rather than summarised: which ones exist and whose
 * account they came from is the whole of what there is to decide, and switching
 * them on shows them in every other calendar app too — not a choice to make on
 * the app's word. Each row carries the same switch Settings does; the button
 * above them does all of it at once.
 *
 * Declining leads into the calendar step behind this one, which offers to make a
 * calendar instead — the same offer a device with none at all gets.
 */
@Composable
internal fun HiddenCalendarsStep(
    calendars: List<CalendarSource>,
    onSetVisible: (id: Long, visible: Boolean) -> Unit,
    onSwitchAllOn: () -> Unit,
    onFinished: () -> Unit,
    writeFailed: Boolean,
    modifier: Modifier = Modifier,
    progress: (@Composable () -> Unit)? = null,
    navigationIcon: (@Composable () -> Unit)? = null,
) {
    val anyVisible = calendars.any { it.isVisibleInSystem }
    OnboardingScaffold(
        modifier = modifier,
        progress = progress,
        navigationIcon = navigationIcon,
        topSpacing = OnboardingSpace.sm,
        contentPadding = 0.dp,
        // The list is as long as the device's calendars; pinned actions would
        // eat the room it needs and hide that there is more below.
        scrollingActions = true,
        hero = { IconHero(Icons.Filled.VisibilityOff) },
        actions = {
            Button(
                onClick = if (anyVisible) onFinished else onSwitchAllOn,
                modifier = Modifier
                    .padding(horizontal = GroupedListInset)
                    .fillMaxWidth()
                    .height(56.dp),
            ) {
                Text(
                    text = stringResource(
                        if (anyVisible) {
                            R.string.onboarding_view_continue_button
                        } else {
                            R.string.onboarding_hidden_switch_all_button
                        },
                    ),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            // Only while nothing is on: once something is, the step has been
            // answered and the one button left to press says so.
            if (!anyVisible) {
                TextButton(
                    onClick = onFinished,
                    modifier = Modifier
                        .padding(horizontal = GroupedListInset)
                        .fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.onboarding_hidden_skip_button))
                }
            }
        },
    ) {
        Column(Modifier.padding(horizontal = GroupedListInset)) {
            Text(
                text = stringResource(R.string.onboarding_hidden_title),
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.onboarding_hidden_body),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            if (writeFailed) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.onboarding_hidden_failed),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        Spacer(Modifier.height(OnboardingSpace.md))

        calendars.forEachIndexed { index, calendar ->
            val off = !calendar.isVisibleInSystem
            GroupedRow(
                title = calendar.displayName.ifBlank { stringResource(R.string.calendar_unnamed) },
                summary = calendar.accountName.takeIf { it.isNotBlank() },
                position = when {
                    calendars.size == 1 -> Position.Alone
                    index == 0 -> Position.Top
                    index == calendars.lastIndex -> Position.Bottom
                    else -> Position.Middle
                },
                dimmed = off,
                trailing = if (calendar.hasVisibilitySwitch) {
                    {
                        CalendarSwitch(
                            calendarName = calendar.displayName.ifBlank { stringResource(R.string.calendar_unnamed) },
                            enabled = !off,
                            onToggle = { on -> onSetVisible(calendar.id, on) },
                        )
                    }
                } else {
                    null
                },
                leading = { CalendarColorChip(calendar.color) },
            )
        }

        Spacer(Modifier.height(OnboardingSpace.md))
    }
}

/** The per-row switch, writing the device's own `Calendars.VISIBLE` as Settings does. */
@Composable
private fun CalendarSwitch(
    calendarName: String,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    val label = stringResource(R.string.calendars_visibility_a11y, calendarName)
    Switch(
        checked = enabled,
        onCheckedChange = onToggle,
        modifier = Modifier.semantics { contentDescription = label },
    )
}
