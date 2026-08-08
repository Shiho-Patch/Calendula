package de.jeanlucmakiola.calendula.ui.search

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.jeanlucmakiola.calendula.R
import de.jeanlucmakiola.calendula.domain.EventInstance
import de.jeanlucmakiola.calendula.domain.RecurringWriteScope
import de.jeanlucmakiola.floret.identity.animateItemMotion
import de.jeanlucmakiola.floret.identity.fadeThrough
import de.jeanlucmakiola.floret.identity.predictiveBack
import de.jeanlucmakiola.floret.components.GroupedRow
import de.jeanlucmakiola.floret.components.InlineTextField
import de.jeanlucmakiola.floret.components.Position
import de.jeanlucmakiola.floret.components.SnackChip
import de.jeanlucmakiola.floret.components.SnackChipHeight
import de.jeanlucmakiola.floret.components.SnackChipMargin
import de.jeanlucmakiola.floret.locale.currentLocale
import de.jeanlucmakiola.calendula.ui.common.LocalSoftenColors
import de.jeanlucmakiola.calendula.ui.common.RecurringScopeDialog
import de.jeanlucmakiola.calendula.ui.common.eventAccent
import de.jeanlucmakiola.calendula.ui.common.eventFill
import de.jeanlucmakiola.calendula.domain.spanFirstDay
import de.jeanlucmakiola.calendula.ui.common.LocalUse24HourFormat
import de.jeanlucmakiola.calendula.ui.common.timeOfDayFormatter
import de.jeanlucmakiola.floret.components.positionOf
import kotlinx.coroutines.delay
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toJavaLocalDate
import java.time.Instant as JavaInstant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** How long the delete confirmation chip stays up, matching a short snackbar. */
private const val CHIP_MILLIS = 4_000L

/**
 * Full-text event search (top-bar entry). Type a query → matching events
 * (title / location / description) across the whole calendar, newest-relevant
 * first; tap a result to open its detail. Long-press a result to enter selection
 * mode and delete several at once (#80). A full-screen overlay hosted by
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
    val selection by viewModel.selection.collectAsStateWithLifecycle()
    val deleteState by viewModel.deleteState.collectAsStateWithLifecycle()
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val context = LocalContext.current
    val inSelection = selection.isNotEmpty()
    var showDeleteDialog by remember { mutableStateOf(false) }

    // Each fresh open starts blank and straight into typing. The ViewModel is
    // activity-scoped so it outlives the overlay; clearing on (re)enter is what
    // resets a previous search. Peeking a result doesn't re-run this (the screen
    // stays composed under the detail), so backing out keeps the query.
    LaunchedEffect(Unit) {
        viewModel.setQuery("")
        focusRequester.requestFocus()
        keyboard?.show()
    }

    // Same in-place WRITE_CALENDAR upgrade the detail screen does: a v1.0 install
    // holds only READ_CALENDAR, and granting continues into the held delete.
    var pendingWrite by remember { mutableStateOf<(() -> Unit)?>(null) }
    val writePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) pendingWrite?.invoke()
        pendingWrite = null
    }
    val requireWrite: (() -> Unit) -> Unit = { action ->
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.WRITE_CALENDAR,
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            action()
        } else {
            pendingWrite = action
            writePermissionLauncher.launch(Manifest.permission.WRITE_CALENDAR)
        }
    }

    // Back leaves selection mode before it leaves the screen — and without the
    // predictive-back scale-out, which would read as closing search only to have
    // it snap back. The two handlers are mutually exclusive on [inSelection].
    BackHandler(enabled = inSelection) { viewModel.clearSelection() }

    Scaffold(
        modifier = modifier.predictiveBack(onBack = onBack, enabled = !inSelection),
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            SearchTopBar(
                inSelection = inSelection,
                query = query,
                selectedCount = selection.size,
                focusRequester = focusRequester,
                onQueryChange = viewModel::setQuery,
                onImeAction = { keyboard?.hide() },
                onBack = onBack,
                onCloseSelection = viewModel::clearSelection,
                onSelectAll = viewModel::selectAll,
                onDelete = { requireWrite { showDeleteDialog = true } },
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
                    results = s,
                    selection = selection,
                    inSelection = inSelection,
                    onEventClick = onEventClick,
                    onToggle = viewModel::toggleSelection,
                )
            }
            DeleteOutcomeChip(
                deleteState = deleteState,
                onConsume = viewModel::consumeDeleteResult,
                modifier = Modifier.align(Alignment.BottomStart),
            )
        }
    }

    if (showDeleteDialog) {
        val count = selection.size
        // One decision for the whole batch: the scope question is only asked when
        // a recurring event is in it, and one-offs ignore whatever comes back.
        if (viewModel.selectionHasRecurring()) {
            RecurringScopeDialog(
                title = stringResource(R.string.event_delete_recurring_title),
                onSelect = { scope ->
                    showDeleteDialog = false
                    viewModel.deleteSelected(scope)
                },
                onDismiss = { showDeleteDialog = false },
            )
        } else {
            AlertDialog(
                onDismissRequest = { showDeleteDialog = false },
                title = {
                    Text(pluralStringResource(R.plurals.search_delete_title, count, count))
                },
                text = { Text(stringResource(R.string.event_delete_body)) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showDeleteDialog = false
                            viewModel.deleteSelected(RecurringWriteScope.AllEvents)
                        },
                    ) {
                        Text(
                            text = stringResource(R.string.event_detail_delete),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteDialog = false }) {
                        Text(stringResource(R.string.dialog_cancel))
                    }
                },
            )
        }
    }
}

/**
 * The search field and the contextual selection bar as one bar: entering or
 * leaving selection mode keeps the bar itself in place, eases its container
 * colour and fades its slots through, rather than cutting between two bars.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SearchTopBar(
    inSelection: Boolean,
    query: String,
    selectedCount: Int,
    focusRequester: FocusRequester,
    onQueryChange: (String) -> Unit,
    onImeAction: () -> Unit,
    onBack: () -> Unit,
    onCloseSelection: () -> Unit,
    onSelectAll: () -> Unit,
    onDelete: () -> Unit,
) {
    val container by animateColorAsState(
        targetValue = if (inSelection) {
            MaterialTheme.colorScheme.surfaceContainerHigh
        } else {
            MaterialTheme.colorScheme.surface
        },
        animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
        label = "search-top-bar-color",
    )
    // Held past the selection emptying so the count has something to draw while
    // it fades out — by then the set is already at zero.
    var lastCount by remember { mutableIntStateOf(0) }
    if (selectedCount > 0) lastCount = selectedCount

    // Read here so the non-composable transitionSpec lambdas can capture it.
    val slotSwap = fadeThrough()

    TopAppBar(
        title = {
            AnimatedContent(
                targetState = inSelection,
                transitionSpec = { slotSwap },
                label = "search-top-bar-title",
            ) { selecting ->
                if (selecting) {
                    Text(
                        pluralStringResource(
                            R.plurals.search_selected_count,
                            lastCount,
                            lastCount,
                        ),
                    )
                } else {
                    InlineTextField(
                        value = query,
                        onValueChange = onQueryChange,
                        placeholder = stringResource(R.string.search_hint),
                        capitalization = KeyboardCapitalization.None,
                        imeAction = ImeAction.Search,
                        onImeAction = onImeAction,
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester),
                    )
                }
            }
        },
        navigationIcon = {
            AnimatedContent(
                targetState = inSelection,
                transitionSpec = { slotSwap },
                label = "search-top-bar-navigation",
            ) { selecting ->
                if (selecting) {
                    IconButton(onClick = onCloseSelection) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.search_selection_close),
                        )
                    }
                } else {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.search_back),
                        )
                    }
                }
            }
        },
        actions = {
            AnimatedContent(
                targetState = inSelection,
                transitionSpec = { slotSwap },
                label = "search-top-bar-actions",
            ) { selecting ->
                Row {
                    if (selecting) {
                        IconButton(onClick = onSelectAll) {
                            Icon(
                                imageVector = Icons.Default.SelectAll,
                                contentDescription = stringResource(R.string.search_select_all),
                            )
                        }
                        IconButton(onClick = onDelete) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = stringResource(
                                    R.string.search_delete_selected,
                                ),
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                    } else if (query.isNotEmpty()) {
                        IconButton(onClick = { onQueryChange("") }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = stringResource(R.string.search_clear),
                            )
                        }
                    }
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = container),
    )
}

@Composable
private fun SearchResults(
    results: SearchUiState.Results,
    selection: Set<Long>,
    inSelection: Boolean,
    onEventClick: (EventInstance) -> Unit,
    onToggle: (Long) -> Unit,
) {
    val hits = results.hits
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        // No horizontal inset here: GroupedRow already insets itself, and adding
        // another 16dp made the results narrower than every other list.
        contentPadding = PaddingValues(top = 8.dp, bottom = 96.dp),
    ) {
        itemsIndexed(
            items = hits,
            key = { _, hit -> hit.event.eventId },
        ) { index, hit ->
            val event = hit.event
            val deletable = results.isDeletable(event)
            SearchResultRow(
                event = event,
                position = positionOf(index, hits.size),
                modifier = animateItemMotion(),
                selected = event.eventId in selection,
                // A read-only calendar's row can't join a batch, so in selection
                // mode it goes quiet rather than offering a checkbox that fails.
                inSelection = inSelection,
                selectable = deletable,
                onClick = when {
                    inSelection && deletable -> ({ onToggle(event.eventId) })
                    inSelection -> null
                    else -> ({ onEventClick(event) })
                },
                onLongClick = { onToggle(event.eventId) }.takeIf { deletable && !inSelection },
            )
        }
    }
}

@Composable
private fun SearchResultRow(
    event: EventInstance,
    position: Position,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    inSelection: Boolean = false,
    selectable: Boolean = true,
    onClick: (() -> Unit)?,
    onLongClick: (() -> Unit)? = null,
) {
    val dark = isSystemInDarkTheme()
    val soften = LocalSoftenColors.current
    GroupedRow(
        modifier = modifier,
        title = event.title,
        summary = searchSummary(event),
        position = position,
        minHeight = 64.dp,
        selected = selected,
        dimmed = inSelection && !selectable,
        leading = {
            Box(
                modifier = Modifier
                    .size(width = 6.dp, height = 36.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(eventAccent(event.color, dark, soften)),
            )
        },
        trailing = if (inSelection && selectable) {
            {
                Checkbox(
                    checked = selected,
                    onCheckedChange = null,
                )
            }
        } else {
            null
        },
        onClick = onClick,
        onLongClick = onLongClick,
    )
}

/**
 * The batch's receipt: how many events went, and whether any refused. Sits on
 * the FAB band so the shortened list stays visible behind it. Deletes aren't
 * reversible through the provider, so this carries no Undo.
 */
@Composable
private fun DeleteOutcomeChip(
    deleteState: BulkDeleteUiState,
    onConsume: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val message = when (deleteState) {
        is BulkDeleteUiState.Done -> when {
            deleteState.failed > 0 -> stringResource(
                R.string.search_delete_partial,
                deleteState.deleted,
                deleteState.failed,
            )
            else -> pluralStringResource(
                R.plurals.search_delete_done,
                deleteState.deleted,
                deleteState.deleted,
            )
        }
        BulkDeleteUiState.NeedsPermission -> stringResource(R.string.event_delete_write_denied)
        else -> null
    }
    // Held past the state being consumed so the chip has something to draw while
    // it springs back out.
    val shown = remember { mutableStateOf("") }
    if (message != null && shown.value != message) shown.value = message

    LaunchedEffect(deleteState) {
        if (message == null) return@LaunchedEffect
        delay(CHIP_MILLIS)
        onConsume()
    }
    Box(
        modifier = modifier
            .navigationBarsPadding()
            .padding(start = SnackChipMargin, bottom = SnackChipMargin)
            .height(SnackChipHeight),
        contentAlignment = Alignment.CenterStart,
    ) {
        SnackChip(visible = message != null, message = shown.value)
    }
}

/** "Wed, 17 Jun 2026 · 09:00 · Office" — date, then time (or All day), then location. */
@Composable
private fun searchSummary(event: EventInstance): String {
    val locale = currentLocale()
    val zone = remember { ZoneId.systemDefault() }
    val start = remember(event.start, zone) {
        JavaInstant.ofEpochMilli(event.start.toEpochMilliseconds()).atZone(zone)
    }
    // From the shared span rule, not [start]: an all-day event sits at UTC
    // midnight and would name the day before west of UTC (#82). The clock time
    // below stays device-zone — it is only rendered for timed events.
    val dateText = remember(event.start, event.end, event.isAllDay, locale) {
        DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)
            .format(event.spanFirstDay(TimeZone.currentSystemDefault()).toJavaLocalDate())
    }
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
