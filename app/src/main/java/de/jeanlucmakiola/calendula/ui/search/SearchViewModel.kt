package de.jeanlucmakiola.calendula.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.jeanlucmakiola.calendula.data.calendar.CalendarRepository
import de.jeanlucmakiola.calendula.data.di.IoDispatcher
import de.jeanlucmakiola.calendula.domain.EventInstance
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlin.time.Clock
import javax.inject.Inject

/** Shortest query that triggers a search; one character matches almost everything. */
private const val MIN_QUERY_LENGTH = 2

sealed interface SearchUiState {
    /** No query yet, or one too short to search — show the prompt. */
    data object Idle : SearchUiState

    /** A query ran but matched nothing. */
    data class Empty(val query: String) : SearchUiState

    /** Matches, ordered nearest-to-today first (upcoming ascending, then past descending). */
    data class Results(val events: List<EventInstance>) : SearchUiState
}

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val repository: CalendarRepository,
    @IoDispatcher private val io: CoroutineDispatcher,
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    val state: StateFlow<SearchUiState> = _query
        .debounce(250L)
        .map { it.trim() }
        .distinctUntilChanged()
        .mapLatest { q ->
            if (q.length < MIN_QUERY_LENGTH) {
                SearchUiState.Idle
            } else {
                val results = repository.searchEvents(q)
                if (results.isEmpty()) SearchUiState.Empty(q)
                else SearchUiState.Results(sortNearestFirst(results))
            }
        }
        .catch { emit(SearchUiState.Idle) }
        .flowOn(io)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), SearchUiState.Idle)

    fun setQuery(value: String) {
        _query.value = value
    }

    /** Soonest upcoming (and ongoing) first, then the most recent past. */
    private fun sortNearestFirst(events: List<EventInstance>): List<EventInstance> {
        val now = Clock.System.now()
        val (upcoming, past) = events.partition { it.end >= now }
        return upcoming.sortedBy { it.start } + past.sortedByDescending { it.start }
    }
}
