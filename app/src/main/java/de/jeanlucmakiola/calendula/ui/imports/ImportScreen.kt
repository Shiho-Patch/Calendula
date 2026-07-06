package de.jeanlucmakiola.calendula.ui.imports

import android.net.Uri
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.jeanlucmakiola.calendula.R
import de.jeanlucmakiola.calendula.domain.EventForm
import de.jeanlucmakiola.calendula.domain.ics.IcsParseWarning
import de.jeanlucmakiola.calendula.ui.common.CalendarPickerGroups
import de.jeanlucmakiola.calendula.ui.common.predictiveBack

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
            .padding(vertical = 8.dp),
    ) {
        Text(
            pluralStringResource(R.plurals.import_event_count, state.events.size, state.events.size),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 12.dp),
        )
        CalendarPickerGroups(
            calendars = state.calendars,
            selectedId = selected,
            onSelect = { selected = it },
        )
        if (state.warnings.isNotEmpty()) {
            Column(
                Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                state.warnings.forEach { WarningText(it) }
            }
        }
        Button(
            onClick = { onImport(selected) },
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp),
        ) {
            Text(pluralStringResource(R.plurals.import_action, state.events.size, state.events.size))
        }
    }
}

@Composable
private fun DoneContent(state: ImportUiState.Done, onClose: () -> Unit) {
    // A little expressive pop on the success badge — springs in on first show.
    val badgeScale = remember { Animatable(0.7f) }
    LaunchedEffect(Unit) {
        badgeScale.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow,
            ),
        )
    }

    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1f))
        Box(
            Modifier
                .size(112.dp)
                .graphicsLayer {
                    scaleX = badgeScale.value
                    scaleY = badgeScale.value
                }
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Default.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(56.dp),
            )
        }
        Spacer(Modifier.height(24.dp))
        Text(
            stringResource(R.string.import_done_title),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(24.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ImportStatCard(
                count = state.summary.imported,
                label = stringResource(R.string.import_done_added_label),
                contentDescription = pluralStringResource(
                    R.plurals.import_done_imported,
                    state.summary.imported,
                    state.summary.imported,
                ),
                container = MaterialTheme.colorScheme.secondaryContainer,
                onContainer = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            if (state.summary.skippedDuplicate > 0) {
                ImportStatCard(
                    count = state.summary.skippedDuplicate,
                    label = stringResource(R.string.import_done_skipped_label),
                    contentDescription = pluralStringResource(
                        R.plurals.import_done_skipped,
                        state.summary.skippedDuplicate,
                        state.summary.skippedDuplicate,
                    ),
                    container = MaterialTheme.colorScheme.surfaceContainerHighest,
                    onContainer = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.weight(1f))
        Button(
            onClick = onClose,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.import_close))
        }
    }
}

/** A big-number tonal tile summarising one import outcome (added / skipped). */
@Composable
private fun RowScope.ImportStatCard(
    count: Int,
    label: String,
    contentDescription: String,
    container: Color,
    onContainer: Color,
) {
    Surface(
        modifier = Modifier
            .weight(1f)
            .clearAndSetSemantics { this.contentDescription = contentDescription },
        shape = RoundedCornerShape(24.dp),
        color = container,
    ) {
        Column(
            Modifier.padding(vertical = 20.dp, horizontal = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                count.toString(),
                style = MaterialTheme.typography.displaySmall,
                color = onContainer,
            )
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                color = onContainer.copy(alpha = 0.85f),
            )
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
