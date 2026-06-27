package de.jeanlucmakiola.calendula.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.jeanlucmakiola.calendula.R
import de.jeanlucmakiola.calendula.domain.EventInstance
import de.jeanlucmakiola.calendula.ui.common.calendarAnimateItem
import de.jeanlucmakiola.calendula.ui.common.predictiveBack
import de.jeanlucmakiola.calendula.ui.common.GroupedRow
import de.jeanlucmakiola.calendula.ui.common.InlineTextField
import de.jeanlucmakiola.calendula.ui.common.Position
import de.jeanlucmakiola.calendula.ui.common.currentLocale
import de.jeanlucmakiola.calendula.ui.common.LocalUse24HourFormat
import de.jeanlucmakiola.calendula.ui.common.timeOfDayFormatter
import de.jeanlucmakiola.calendula.ui.common.pastelize
import de.jeanlucmakiola.calendula.ui.common.positionOf
import java.time.Instant as JavaInstant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * Full-text event search (top-bar entry). Type a query → matching events
 * (title / location / description) across the whole calendar, newest-relevant
 * first; tap a result to open its detail. A full-screen overlay hosted by
 * [de.jeanlucmakiola.calendula.ui.CalendarHost].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onBack: () -> Unit,
    onEventClick: (EventInstance) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val query by viewModel.query.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    // Each fresh open starts blank and straight into typing. The ViewModel is
    // activity-scoped so it outlives the overlay; clearing on (re)enter is what
    // resets a previous search. Peeking a result doesn't re-run this (the screen
    // stays composed under the detail), so backing out keeps the query.
    LaunchedEffect(Unit) {
        viewModel.setQuery("")
        focusRequester.requestFocus()
        keyboard?.show()
    }
    Scaffold(
        modifier = modifier.predictiveBack(onBack = onBack),
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = {
                    InlineTextField(
                        value = query,
                        onValueChange = viewModel::setQuery,
                        placeholder = stringResource(R.string.search_hint),
                        imeAction = ImeAction.Search,
                        onImeAction = { keyboard?.hide() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.search_back),
                        )
                    }
                },
                actions = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { viewModel.setQuery("") }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = stringResource(R.string.search_clear),
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { padding ->
        // imePadding shrinks the content by the keyboard, so the centered
        // idle/empty message re-centres in the space above it (and the results
        // list lifts clear of the keyboard too).
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding(),
        ) {
            when (val s = state) {
                SearchUiState.Idle -> SearchMessage(
                    icon = null,
                    text = stringResource(R.string.search_idle_hint),
                )
                is SearchUiState.Empty -> SearchMessage(
                    icon = Icons.Default.SearchOff,
                    text = stringResource(R.string.search_empty, s.query),
                )
                is SearchUiState.Results -> SearchResults(
                    events = s.events,
                    onEventClick = onEventClick,
                )
            }
        }
    }
}

@Composable
private fun SearchResults(
    events: List<EventInstance>,
    onEventClick: (EventInstance) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
    ) {
        itemsIndexed(
            items = events,
            key = { _, event -> event.eventId },
        ) { index, event ->
            SearchResultRow(
                event = event,
                position = positionOf(index, events.size),
                modifier = calendarAnimateItem(),
                onClick = { onEventClick(event) },
            )
        }
    }
}

@Composable
private fun SearchResultRow(
    event: EventInstance,
    position: Position,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val dark = isSystemInDarkTheme()
    GroupedRow(
        modifier = modifier,
        title = event.title,
        summary = searchSummary(event),
        position = position,
        minHeight = 64.dp,
        leading = {
            Box(
                modifier = Modifier
                    .size(width = 6.dp, height = 36.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(pastelize(event.color, dark)),
            )
        },
        onClick = onClick,
    )
}

/** "Wed, 17 Jun 2026 · 09:00 · Office" — date, then time (or All day), then location. */
@Composable
private fun searchSummary(event: EventInstance): String {
    val locale = currentLocale()
    val zone = remember { ZoneId.systemDefault() }
    val start = remember(event.start, zone) {
        JavaInstant.ofEpochMilli(event.start.toEpochMilliseconds()).atZone(zone)
    }
    val dateText = remember(locale) {
        DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)
    }.format(start)
    val use24Hour = LocalUse24HourFormat.current
    val timeText = if (event.isAllDay) {
        stringResource(R.string.event_detail_all_day)
    } else {
        remember(locale, use24Hour) { timeOfDayFormatter(use24Hour, locale) }.format(start)
    }
    val base = "$dateText · $timeText"
    return event.location?.takeIf { it.isNotBlank() }?.let { "$base · $it" } ?: base
}

@Composable
private fun SearchMessage(
    icon: ImageVector?,
    text: String,
) {
    Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(48.dp),
                )
                Spacer(Modifier.height(16.dp))
            }
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}
