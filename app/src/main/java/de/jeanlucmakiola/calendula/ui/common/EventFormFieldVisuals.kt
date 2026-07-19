package de.jeanlucmakiola.calendula.ui.common

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.ui.graphics.vector.ImageVector
import de.jeanlucmakiola.calendula.R
import de.jeanlucmakiola.calendula.domain.EventFormField

/**
 * The shared label and icon for each optional [EventFormField]. Both the event
 * editor (which shows these fields) and the settings screen (which toggles their
 * defaults) draw from this one mapping, so a field reads the same wherever it
 * appears.
 */
@StringRes
fun eventFormFieldLabel(field: EventFormField): Int = when (field) {
    EventFormField.Location -> R.string.event_detail_location
    EventFormField.Description -> R.string.event_detail_description
    EventFormField.Timezone -> R.string.event_detail_timezone
    EventFormField.Reminders -> R.string.event_detail_reminders
    EventFormField.Recurrence -> R.string.event_detail_recurrence
    EventFormField.Availability -> R.string.event_edit_availability
    EventFormField.Visibility -> R.string.event_edit_visibility
    EventFormField.Color -> R.string.event_edit_color
    EventFormField.Attendees -> R.string.event_edit_attendees
}

fun eventFormFieldIcon(field: EventFormField): ImageVector = when (field) {
    EventFormField.Location -> Icons.Default.Place
    EventFormField.Description -> Icons.AutoMirrored.Filled.Notes
    EventFormField.Timezone -> Icons.Default.Public
    EventFormField.Reminders -> Icons.Default.Notifications
    EventFormField.Recurrence -> Icons.Default.Repeat
    EventFormField.Availability -> Icons.Default.EventAvailable
    EventFormField.Visibility -> Icons.Default.Lock
    EventFormField.Color -> Icons.Default.Palette
    EventFormField.Attendees -> Icons.Default.People
}
