package de.jeanlucmakiola.calendula.ui.debug

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.jeanlucmakiola.calendula.R
import de.jeanlucmakiola.calendula.domain.CalendarSource
import de.jeanlucmakiola.calendula.domain.EventInstance
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

@Composable
fun DebugScreen(
    modifier: Modifier = Modifier,
    viewModel: DebugViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Column(modifier = modifier.fillMaxSize()) {
        DebugBanner()
        when (val s = state) {
            DebugUiState.Loading -> LoadingContent()
            is DebugUiState.Failure -> FailureContent()
            is DebugUiState.Success -> SuccessContent(s)
        }
    }
}

@Composable
private fun DebugBanner() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.tertiaryContainer)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(
            text = stringResource(R.string.debug_banner),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
        )
    }
}

@Composable
private fun LoadingContent() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun FailureContent() {
    Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(
            text = stringResource(R.string.state_failure_provider),
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@Composable
private fun SuccessContent(state: DebugUiState.Success) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        item { SectionHeader(stringResource(R.string.debug_calendars_header)) }
        if (state.calendars.isEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.debug_no_calendars),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        } else {
            items(state.calendars, key = { "cal-${it.id}" }) { CalendarRow(it) }
        }

        item { Spacer(Modifier.height(16.dp)) }
        item { SectionHeader(stringResource(R.string.debug_events_header)) }

        if (state.nextEvents.isEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.debug_no_events),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        } else {
            items(
                state.nextEvents,
                // Recurring events share Instances._ID across occurrences, so
                // include the start instant to keep the LazyColumn key unique.
                key = { "evt-${it.instanceId}-${it.start.toEpochMilliseconds()}" },
            ) { EventRow(it) }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Text(text = text, style = MaterialTheme.typography.titleMedium)
        HorizontalDivider()
    }
}

@Composable
private fun CalendarRow(cal: CalendarSource) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .background(Color(cal.color), CircleShape),
        )
        Text(
            text = "  ${cal.displayName}  (${cal.accountName})",
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun EventRow(event: EventInstance) {
    val zone = TimeZone.currentSystemDefault()
    val start = event.start.toLocalDateTime(zone)
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(text = event.title, style = MaterialTheme.typography.bodyMedium)
        val date = "%04d-%02d-%02d".format(start.year, start.month.ordinal + 1, start.day)
        val time = "%02d:%02d".format(start.hour, start.minute)
        Text(
            text = "$date  $time",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
