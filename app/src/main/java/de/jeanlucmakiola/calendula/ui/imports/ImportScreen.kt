package de.jeanlucmakiola.calendula.ui.imports

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.jeanlucmakiola.calendula.R
import de.jeanlucmakiola.calendula.domain.EventForm
import de.jeanlucmakiola.calendula.domain.ics.IcsParseWarning
import de.jeanlucmakiola.calendula.ui.common.predictiveBack
import de.jeanlucmakiola.calendula.ui.common.OptionCard

/**
 * Handles an opened/received `.ics` file. A single event is handed straight to
 * the prefilled create form via [onOpenSingle]; several events show a target-
 * calendar picker and import in bulk (dedup by UID), then a result summary.
 * Empty/failed files show a short message and close.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportScreen(
    uri: Uri,
    onClose: () -> Unit,
    onOpenSingle: (EventForm) -> Unit,
    viewModel: ImportViewModel = hiltViewModel(),
) {
    LaunchedEffect(uri) { viewModel.load(uri) }
    val state by viewModel.state.collectAsStateWithLifecycle()

    // A single event isn't shown here — it opens the create form for review.
    LaunchedEffect(state) {
        (state as? ImportUiState.Single)?.let { onOpenSingle(it.form); onClose() }
    }

    Scaffold(
        modifier = Modifier
            .predictiveBack(onBack = onClose)
            .fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.import_title)) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.event_edit_close))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (val s = state) {
                ImportUiState.Loading,
                ImportUiState.Importing,
                is ImportUiState.Single,
                -> CircularProgressIndicator(Modifier.align(Alignment.Center))

                ImportUiState.Empty -> CenteredMessage(stringResource(R.string.import_empty), onClose)
                ImportUiState.Failed -> CenteredMessage(stringResource(R.string.import_failed), onClose)
                is ImportUiState.Many -> ManyContent(s, onImport = viewModel::import)
                is ImportUiState.Done -> DoneContent(s, onClose)
            }
        }
    }
}

@Composable
private fun ManyContent(state: ImportUiState.Many, onImport: (Long) -> Unit) {
    // No writable calendar to import into — tell the user honestly.
    if (state.calendars.isEmpty()) {
        CenteredMessage(stringResource(R.string.import_no_calendar), onClose = null)
        return
    }
    var selected by rememberSaveable { mutableStateOf(state.calendars.first().id) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            pluralStringResource(R.plurals.import_event_count, state.events.size, state.events.size),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(vertical = 8.dp),
        )
        Text(
            stringResource(R.string.import_target_header),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        state.calendars.forEach { calendar ->
            OptionCard(
                label = calendar.displayName,
                onClick = { selected = calendar.id },
                selected = calendar.id == selected,
                icon = null,
            )
        }
        state.warnings.forEach { WarningText(it) }
        Button(
            onClick = { onImport(selected) },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        ) {
            Text(pluralStringResource(R.plurals.import_action, state.events.size, state.events.size))
        }
    }
}

@Composable
private fun DoneContent(state: ImportUiState.Done, onClose: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            stringResource(R.string.import_done_title),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(top = 24.dp),
        )
        Text(
            pluralStringResource(
                R.plurals.import_done_imported,
                state.summary.imported,
                state.summary.imported,
            ),
            style = MaterialTheme.typography.bodyLarge,
        )
        if (state.summary.skippedDuplicate > 0) {
            Text(
                pluralStringResource(
                    R.plurals.import_done_skipped,
                    state.summary.skippedDuplicate,
                    state.summary.skippedDuplicate,
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Button(onClick = onClose, modifier = Modifier.padding(top = 12.dp)) {
            Text(stringResource(R.string.import_close))
        }
    }
}

@Composable
private fun WarningText(warning: IcsParseWarning) {
    val text = when (warning) {
        IcsParseWarning.ModifiedOccurrenceSkipped -> stringResource(R.string.import_warning_recurrence)
        IcsParseWarning.EventWithoutStartSkipped -> stringResource(R.string.import_warning_no_start)
        IcsParseWarning.AttendeesIgnored -> stringResource(R.string.import_warning_attendees)
        IcsParseWarning.UnknownTimezone -> stringResource(R.string.import_warning_timezone)
    }
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun CenteredMessage(message: String, onClose: (() -> Unit)?) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(message, style = MaterialTheme.typography.bodyLarge)
            if (onClose != null) {
                Button(onClick = onClose) { Text(stringResource(R.string.import_close)) }
            }
        }
    }
}
