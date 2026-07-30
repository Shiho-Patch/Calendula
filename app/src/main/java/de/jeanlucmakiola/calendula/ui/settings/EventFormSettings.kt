package de.jeanlucmakiola.calendula.ui.settings

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.jeanlucmakiola.calendula.R
import de.jeanlucmakiola.calendula.domain.EventFormField
import de.jeanlucmakiola.calendula.ui.common.eventFormFieldIcon
import de.jeanlucmakiola.calendula.ui.common.eventFormFieldLabel
import de.jeanlucmakiola.floret.components.CollapsingScaffold
import de.jeanlucmakiola.floret.components.GroupedRow
import de.jeanlucmakiola.floret.components.Position
import de.jeanlucmakiola.floret.components.positionOf

/** New event form: which fields it opens with, and how it behaves. */
@Composable
internal fun EventFormScreen(
    state: SettingsUiState,
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
) {
    CollapsingScaffold(
        title = stringResource(R.string.settings_section_event_form),
        onBack = onBack,
        predictiveBack = true,
    ) {
        SettingsHint(stringResource(R.string.settings_form_fields_hint))
        Spacer(Modifier.height(8.dp))
        val fields = EventFormField.entries
        fields.forEachIndexed { index, field ->
            val checked = field in state.defaultFormFields
            GroupedRow(
                title = stringResource(eventFormFieldLabel(field)),
                position = positionOf(index, fields.size),
                // The same icon the field carries in the new-event form.
                leading = {
                    Icon(
                        imageVector = eventFormFieldIcon(field),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                trailing = {
                    Switch(
                        checked = checked,
                        onCheckedChange = { viewModel.setFormFieldDefault(field, it) },
                    )
                },
                onClick = { viewModel.setFormFieldDefault(field, !checked) },
            )
        }

        // Auto-focus the title on a new event (#10), on by default.
        Spacer(Modifier.height(24.dp))
        GroupedRow(
            title = stringResource(R.string.settings_autofocus_title),
            summary = stringResource(R.string.settings_autofocus_title_hint),
            position = Position.Alone,
            leading = {
                Icon(
                    imageVector = Icons.Default.Keyboard,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            trailing = {
                Switch(
                    checked = state.autofocusEventTitle,
                    onCheckedChange = { viewModel.setAutofocusEventTitle(it) },
                )
            },
            onClick = { viewModel.setAutofocusEventTitle(!state.autofocusEventTitle) },
        )

        // Per-event colour on calendars that publish no colour set (some
        // CalDAV); off by default, since it may not survive their next sync.
        Spacer(Modifier.height(24.dp))
        GroupedRow(
            title = stringResource(R.string.settings_color_unsupported),
            summary = stringResource(R.string.settings_color_unsupported_hint),
            position = Position.Alone,
            trailing = {
                Switch(
                    checked = state.allowColorOnUnsupportedCalendars,
                    onCheckedChange = { viewModel.setAllowColorOnUnsupportedCalendars(it) },
                )
            },
            onClick = {
                viewModel.setAllowColorOnUnsupportedCalendars(
                    !state.allowColorOnUnsupportedCalendars,
                )
            },
        )
    }
}
