package de.jeanlucmakiola.calendula.ui.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import de.jeanlucmakiola.calendula.R
import de.jeanlucmakiola.calendula.domain.TimeZoneOption
import de.jeanlucmakiola.calendula.domain.filterTimeZones
import de.jeanlucmakiola.calendula.domain.formatGmtOffset
import de.jeanlucmakiola.calendula.domain.timeZoneOptions
import de.jeanlucmakiola.floret.components.FullScreenPicker
import de.jeanlucmakiola.floret.components.GroupedRow
import de.jeanlucmakiola.floret.components.Position
import de.jeanlucmakiola.floret.components.positionOf
import de.jeanlucmakiola.floret.locale.currentLocale

/**
 * Full-screen zone picker: a query field over every zone the JVM knows, with the
 * device zone and recently-picked ones pinned on top so the common case needs no
 * typing. [selected] is the pinned zone id, or null when the event follows the
 * device — picking the device row reports null back, which is what keeps an
 * unpinned event unpinned rather than freezing it to today's zone.
 *
 * Unlike the kit's [de.jeanlucmakiola.floret.components.OptionPicker], this
 * drives its own [LazyColumn] (hence `scrollable = false`): ~600 options is far
 * past what an eagerly composed column can carry.
 */
@Composable
fun TimeZonePickerDialog(
    selected: String?,
    deviceZoneId: String,
    recents: List<String>,
    onSelect: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    // ~600 zones, each resolving a localized name and a DST-aware offset: build
    // once per open, then filter the result per keystroke.
    val locale = currentLocale()
    val allZones = remember(locale) { timeZoneOptions(locale) }
    val matches = remember(allZones, query) { filterTimeZones(allZones, query) }
    val recentZones = remember(allZones, recents) {
        recents.mapNotNull { id -> allZones.firstOrNull { it.id == id } }
    }
    val searching = query.isNotBlank()

    fun choose(zoneId: String?) {
        onSelect(zoneId)
        onDismiss()
    }

    FullScreenPicker(
        title = stringResource(R.string.event_detail_timezone),
        onDismiss = onDismiss,
        scrollable = false,
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            placeholder = { Text(stringResource(R.string.event_edit_timezone_search)) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        )
        LazyColumn(contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp)) {
            if (!searching) {
                // The device row is the "unpinned" choice, so it reads as a mode
                // rather than as a zone among zones — it stays out of the big
                // list and never carries an offset.
                item(key = "device") {
                    GroupedRow(
                        title = stringResource(R.string.event_edit_timezone_device),
                        summary = zoneSummary(deviceZoneId, allZones),
                        position = Position.Alone,
                        selected = selected == null,
                        leading = { Icon(Icons.Default.Public, contentDescription = null) },
                        trailing = if (selected == null) {
                            { SelectedCheck() }
                        } else {
                            null
                        },
                        onClick = { choose(null) },
                    )
                }
                if (recentZones.isNotEmpty()) {
                    item(key = "recent_header") {
                        SectionHeader(stringResource(R.string.event_edit_timezone_recent))
                    }
                    itemsIndexed(
                        items = recentZones,
                        key = { _, zone -> "recent_${zone.id}" },
                    ) { index, zone ->
                        ZoneRow(
                            zone = zone,
                            position = positionOf(index, recentZones.size),
                            selected = zone.id == selected,
                            onClick = { choose(zone.id) },
                        )
                    }
                }
                item(key = "all_header") {
                    SectionHeader(stringResource(R.string.event_edit_timezone_all))
                }
            }

            if (searching && matches.isEmpty()) {
                item(key = "empty") {
                    Text(
                        text = stringResource(R.string.event_edit_timezone_none, query),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 32.dp, vertical = 48.dp),
                    )
                }
            }

            itemsIndexed(
                items = matches,
                key = { _, zone -> "zone_${zone.id}" },
            ) { index, zone ->
                ZoneRow(
                    zone = zone,
                    position = positionOf(index, matches.size),
                    selected = zone.id == selected,
                    onClick = { choose(zone.id) },
                )
            }
        }
    }
}

/** A small primary-coloured group label, matching the settings screens. */
@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun SelectedCheck() {
    Icon(
        imageVector = Icons.Default.Check,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun ZoneRow(
    zone: TimeZoneOption,
    position: Position,
    selected: Boolean,
    onClick: () -> Unit,
) {
    GroupedRow(
        title = zone.city,
        summary = "${zone.displayName} · ${formatGmtOffset(zone.offsetMinutes)}",
        position = position,
        selected = selected,
        trailing = if (selected) {
            { SelectedCheck() }
        } else {
            null
        },
        onClick = onClick,
    )
}

/** "Central European Time · GMT+02:00" for [zoneId], or the bare id if unknown. */
private fun zoneSummary(zoneId: String, allZones: List<TimeZoneOption>): String =
    allZones.firstOrNull { it.id == zoneId }
        ?.let { "${it.displayName} · ${formatGmtOffset(it.offsetMinutes)}" }
        ?: zoneId
