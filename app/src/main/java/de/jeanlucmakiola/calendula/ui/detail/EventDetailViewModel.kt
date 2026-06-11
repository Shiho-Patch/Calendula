package de.jeanlucmakiola.calendula.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.jeanlucmakiola.calendula.data.calendar.CalendarRepository
import de.jeanlucmakiola.calendula.data.calendar.NoSuchEventException
import de.jeanlucmakiola.calendula.data.di.IoDispatcher
import de.jeanlucmakiola.calendula.domain.FailureReason
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Instant
import javax.inject.Inject

/**
 * Loads a single event's detail on demand for the bottom sheet (spec S4).
 * The event id is set via [open]; the sheet observes [state].
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class EventDetailViewModel @Inject constructor(
    private val repository: CalendarRepository,
    @IoDispatcher private val io: CoroutineDispatcher,
) : ViewModel() {

    private val _target = MutableStateFlow<Target?>(null)
    // Bumped by retry() to re-run the load for the same target.
    private val _reload = MutableStateFlow(0)

    private val _deleteState = MutableStateFlow<DeleteUiState>(DeleteUiState.Idle)
    val deleteState: StateFlow<DeleteUiState> = _deleteState.asStateFlow()

    val state: StateFlow<EventDetailUiState> =
        combine(_target, _reload) { target, _ -> target }
            .flatMapLatest { target ->
                if (target == null) {
                    flowOf<EventDetailUiState>(EventDetailUiState.Loading)
                } else {
                    flow {
                        emit(EventDetailUiState.Loading)
                        emit(loadDetail(target))
                    }
                }
            }
            .flowOn(io)
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000L),
                initialValue = EventDetailUiState.Loading,
            )

    /**
     * Open (or switch) to the tapped occurrence. [beginMillis]/[endMillis] are
     * the occurrence's own times (from `CalendarContract.Instances`); they
     * override the series DTSTART/DTEND so recurring events show the correct
     * date instead of the first occurrence.
     */
    fun open(eventId: Long, beginMillis: Long, endMillis: Long) {
        _target.value = Target(eventId, beginMillis, endMillis)
    }

    /** Re-run the current load after a failure. */
    fun retry() {
        _reload.value += 1
    }

    /**
     * Delete the open event. [wholeSeries] is meaningful only for recurring
     * events: false cancels just the tapped occurrence. Result lands in
     * [deleteState]; the screen consumes it via [consumeDeleteResult].
     */
    fun delete(wholeSeries: Boolean) {
        val target = _target.value ?: return
        if (_deleteState.value == DeleteUiState.Deleting) return
        viewModelScope.launch {
            _deleteState.value = DeleteUiState.Deleting
            _deleteState.value = try {
                if (wholeSeries) {
                    repository.deleteEvent(target.eventId)
                } else {
                    repository.deleteOccurrence(target.eventId, target.beginMillis)
                }
                DeleteUiState.Deleted
            } catch (e: CancellationException) {
                throw e
            } catch (e: SecurityException) {
                DeleteUiState.NeedsPermission
            } catch (e: Exception) {
                DeleteUiState.Failed
            }
        }
    }

    /** Reset [deleteState] after the screen handled a terminal result. */
    fun consumeDeleteResult() {
        _deleteState.value = DeleteUiState.Idle
    }

    private suspend fun loadDetail(target: Target): EventDetailUiState = try {
        val detail = repository.eventDetail(target.eventId)
        // The Events row holds the series start; replace it with this
        // occurrence's time so recurring events render correctly.
        val corrected = detail.copy(
            instance = detail.instance.copy(
                start = Instant.fromEpochMilliseconds(target.beginMillis),
                end = Instant.fromEpochMilliseconds(target.endMillis),
            ),
        )
        val calendar = repository.calendars().first()
            .firstOrNull { it.id == corrected.instance.calendarId }
        EventDetailUiState.Success(
            detail = corrected,
            calendarName = calendar?.displayName,
            canModify = calendar?.canModifyContents == true,
        )
    } catch (e: CancellationException) {
        throw e
    } catch (e: NoSuchEventException) {
        EventDetailUiState.Failure(FailureReason.EventNotFound)
    } catch (e: SecurityException) {
        EventDetailUiState.Failure(FailureReason.PermissionRevoked)
    } catch (e: Exception) {
        EventDetailUiState.Failure(FailureReason.ProviderUnavailable)
    }

    /** A tapped occurrence: the series [eventId] plus this occurrence's own times. */
    private data class Target(val eventId: Long, val beginMillis: Long, val endMillis: Long)
}
