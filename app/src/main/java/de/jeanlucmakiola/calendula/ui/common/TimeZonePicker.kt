package de.jeanlucmakiola.calendula.ui.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import de.jeanlucmakiola.calendula.R
import de.jeanlucmakiola.calendula.domain.TimeZoneOption
import de.jeanlucmakiola.calendula.domain.filterTimeZones
import de.jeanlucmakiola.calendula.domain.timeZoneOptionOf
import de.jeanlucmakiola.calendula.domain.timeZoneOptions
import de.jeanlucmakiola.calendula.domain.zoneDescriptor
import de.jeanlucmakiola.floret.components.FullScreenPicker
import de.jeanlucmakiola.floret.components.GroupedListInset
import de.jeanlucmakiola.floret.components.GroupedRow
import de.jeanlucmakiola.floret.components.InlineTextField
import de.jeanlucmakiola.floret.components.Position
import de.jeanlucmakiola.floret.components.SelectedCheck
import de.jeanlucmakiola.floret.components.positionOf
import de.jeanlucmakiola.floret.locale.currentLocale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
    val locale = currentLocale()
    // ~600 zones, each resolving a localized name and up to two ICU short names:
    // far too much to run inside composition, so build it on a worker and let the
    // list fill in a frame later. Filtering the built catalogue is cheap (its
    // search keys are pre-normalized), so that stays here.
    val allZones by produceState(initialValue = emptyList<TimeZoneOption>(), locale) {
        value = withContext(Dispatchers.Default) {
            timeZoneOptions(locale, regionOf = ::icuTimeZoneRegion)
        }
    }
    val matches = remember(allZones, query) { filterTimeZones(allZones, query) }
    val recentZones = remember(allZones, recents) {
        recents.mapNotNull { id -> allZones.firstOrNull { it.id == id } }
    }
    // Resolved on its own rather than looked up in the catalogue: it's one zone,
    // so it costs nothing, and the device row can then render complete on the
    // first frame instead of showing a bare id until the catalogue lands.
    val deviceSummary = remember(deviceZoneId, locale) {
        timeZoneOptionOf(deviceZoneId, locale, regionOf = ::icuTimeZoneRegion)
            ?.let { zoneDescriptor(it) }
            ?: deviceZoneId
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
        SearchField(
            query = query,
            onQueryChange = { query = it },
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        LazyColumn(contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp)) {
            if (!searching) {
                // The device row is the "unpinned" choice, so it reads as a mode
                // rather than as a zone among zones — it stays out of the big
                // list and never carries an offset.
                item(key = "device") {
                    GroupedRow(
                        title = stringResource(R.string.event_edit_timezone_device),
                        summary = deviceSummary,
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

            // allZones.isNotEmpty() gates this: until the catalogue lands there is
            // simply nothing to match yet, and claiming "no time zone matches"
            // for that frame would be wrong.
            if (searching && matches.isEmpty() && allZones.isNotEmpty()) {
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

/**
 * The query box: the family's borderless [InlineTextField] over a tonal surface,
 * so it reads like the rest of the app's inputs rather than a boxed Material
 * field. The clear button rides inside the surface (unlike the search screen,
 * which has a top bar to put it in) because the picker's bar is the title.
 */
@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val keyboard = LocalSoftwareKeyboardController.current
    // surfaceContainerHighest — the picker sits on surfaceContainerHigh, so
    // anything lower vanishes into it.
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        shape = RoundedCornerShape(28.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            InlineTextField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = stringResource(R.string.event_edit_timezone_search),
                capitalization = KeyboardCapitalization.None,
                imeAction = ImeAction.Search,
                onImeAction = { keyboard?.hide() },
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp, vertical = 14.dp),
            )
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.search_clear),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
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
        modifier = Modifier.padding(
            start = GroupedListInset,
            end = GroupedListInset,
            top = 16.dp,
            bottom = 4.dp,
        ),
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
        title = zone.label,
        summary = zoneDescriptor(zone),
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
