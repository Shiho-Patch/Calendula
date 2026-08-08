package de.jeanlucmakiola.calendula.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.jeanlucmakiola.calendula.data.calendar.CalendarRepository
import de.jeanlucmakiola.calendula.data.di.IoDispatcher
import de.jeanlucmakiola.calendula.domain.EventInstance
import de.jeanlucmakiola.calendula.domain.EventSearch
import de.jeanlucmakiola.calendula.domain.RecurringWriteScope
import de.jeanlucmakiola.calendula.domain.SearchResults
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import javax.inject.Inject

/** Shortest query that triggers a search; one character matches almost everything. */
private const val MIN_QUERY_LENGTH = 2

sealed interface SearchUiState {
    /** No query yet, or one too short to search — show the prompt. */
    data object Idle : SearchUiState

    /** A query ran but matched nothing. */
    data class Empty(val query: String) : SearchUiState

    /** Matches by month — see [de.jeanlucmakiola.calendula.domain.EventSearch]. */
    data class Results(
        val results: SearchResults,
        /**
         * Calendars among the results that can't be written to (WebCal, birthday
         * mirrors, …). Their rows are excluded from selection rather than failing
         * at delete time.
         */
        val readOnlyCalendarIds: Set<Long> = emptySet(),
    ) : SearchUiState {
        val events: List<EventInstance> get() = results.allHits.map { it.event }

        fun isDeletable(event: EventInstance): Boolean =
            event.calendarId !in readOnlyCalendarIds
    }
}

/** Outcome of deleting the selected results (#80). */
sealed interface BulkDeleteUiState {
    data object Idle : BulkDeleteUiState

    data object Deleting : BulkDeleteUiState

    /** Terminal: [deleted] events went, [failed] wouldn't. */
    data class Done(val deleted: Int, val failed: Int) : BulkDeleteUiState

    /** WRITE_CALENDAR was revoked mid-flight; nothing further was attempted. */
    data object NeedsPermission : BulkDeleteUiState
}

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val repository: CalendarRepository,
    @IoDispatcher private val io: CoroutineDispatcher,
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    /** Bumped after a delete so the same query re-runs against the changed provider. */
    private val _reload = MutableStateFlow(0)

    private val _selection = MutableStateFlow<Set<Long>>(emptySet())

    /** Event ids picked in selection mode; empty means the mode is off. */
    val selection: StateFlow<Set<Long>> = _selection.asStateFlow()

    private val _deleteState = MutableStateFlow<BulkDeleteUiState>(BulkDeleteUiState.Idle)
    val deleteState: StateFlow<BulkDeleteUiState> = _deleteState.asStateFlow()

    val state: StateFlow<SearchUiState> = combine(
        _query.debounce(250L).map { it.trim() }.distinctUntilChanged(),
        _reload,
    ) { q, _ -> q }
        .mapLatest { q ->
            if (q.length < MIN_QUERY_LENGTH) {
                SearchUiState.Idle
            } else {
                val now = Clock.System.now()
                val zone = TimeZone.currentSystemDefault()
                val results = EventSearch.search(
                    candidates = repository.searchEvents(q),
                    query = q,
                    now = now,
                    todayStart = now.toLocalDateTime(zone).date.atStartOfDayIn(zone),
                    zone = zone,
                )
                if (results.isEmpty) {
                    SearchUiState.Empty(q)
                } else {
                    SearchUiState.Results(
                        results = results,
                        readOnlyCalendarIds = readOnlyCalendarIds(),
                    )
                }
            }
        }
        .catch { emit(SearchUiState.Idle) }
        .flowOn(io)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), SearchUiState.Idle)

    fun setQuery(value: String) {
        if (value != _query.value) _selection.value = emptySet()
        _query.value = value
    }

    /** Toggle one result; the first pick is what turns selection mode on. */
    fun toggleSelection(eventId: Long) {
        val current = _selection.value
        _selection.value = if (eventId in current) current - eventId else current + eventId
    }

    /** Pick every result that lives in a writable calendar. */
    fun selectAll() {
        val results = state.value as? SearchUiState.Results ?: return
        _selection.value = results.events.filter(results::isDeletable).map { it.eventId }.toSet()
    }

    /** Leave selection mode. */
    fun clearSelection() {
        _selection.value = emptySet()
    }

    /**
     * Whether the current selection contains a recurring event, i.e. whether the
     * batch needs a [RecurringWriteScope] decision before it can run.
     */
    fun selectionHasRecurring(): Boolean {
        val results = state.value as? SearchUiState.Results ?: return false
        val picked = _selection.value
        return results.events.any { it.eventId in picked && it.isRecurring }
    }

    /**
     * Delete every selected event. [scope] applies to the recurring ones only —
     * a one-off is always removed whole, whatever the batch decision was. Runs
     * one event at a time so a single failure doesn't take the rest with it;
     * the tally lands in [deleteState].
     */
    fun deleteSelected(scope: RecurringWriteScope) {
        if (_deleteState.value == BulkDeleteUiState.Deleting) return
        val results = state.value as? SearchUiState.Results ?: return
        val picked = _selection.value
        val targets = results.events.filter { it.eventId in picked && results.isDeletable(it) }
        if (targets.isEmpty()) return

        viewModelScope.launch {
            _deleteState.value = BulkDeleteUiState.Deleting
            var deleted = 0
            var failed = 0
            var denied = false
            for (event in targets) {
                try {
                    withContext(io) { deleteOne(event, scope) }
                    deleted++
                } catch (e: CancellationException) {
                    throw e
                } catch (e: SecurityException) {
                    denied = true
                    break
                } catch (e: Exception) {
                    failed++
                }
            }
            _selection.value = emptySet()
            _reload.value += 1
            _deleteState.value = if (denied) {
                BulkDeleteUiState.NeedsPermission
            } else {
                BulkDeleteUiState.Done(deleted = deleted, failed = failed)
            }
        }
    }

    /** Reset [deleteState] after the screen showed the outcome. */
    fun consumeDeleteResult() {
        _deleteState.value = BulkDeleteUiState.Idle
    }

    private suspend fun deleteOne(event: EventInstance, scope: RecurringWriteScope) {
        val begin = event.start.toEpochMilliseconds()
        when {
            !event.isRecurring -> repository.deleteEvent(event.eventId)
            scope == RecurringWriteScope.ThisEvent ->
                repository.deleteOccurrence(event.eventId, begin)
            scope == RecurringWriteScope.ThisAndFollowing ->
                repository.deleteEventFromOccurrence(event.eventId, begin)
            else -> repository.deleteEvent(event.eventId)
        }
    }

    private suspend fun readOnlyCalendarIds(): Set<Long> =
        repository.calendars().first()
            .filterNot { it.canModifyContents }
            .map { it.id }
            .toSet()
}
