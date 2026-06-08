package de.jeanlucmakiola.calendula.ui.debug

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.jeanlucmakiola.calendula.data.calendar.CalendarRepository
import de.jeanlucmakiola.calendula.data.di.IoDispatcher
import de.jeanlucmakiola.calendula.domain.FailureReason
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant
import javax.inject.Inject

private const val MAX_DEBUG_EVENTS = 50
private val DEBUG_WINDOW = 30.days

@HiltViewModel
class DebugViewModel @Inject constructor(
    private val repository: CalendarRepository,
    @IoDispatcher private val io: CoroutineDispatcher,
) : ViewModel() {

    val state: StateFlow<DebugUiState> = run {
        val now = Instant.fromEpochMilliseconds(System.currentTimeMillis())
        val range = now..(now + DEBUG_WINDOW)
        combine(
            repository.calendars(),
            repository.instances(range),
        ) { calendars, instances ->
            DebugUiState.Success(
                calendars = calendars,
                nextEvents = instances.take(MAX_DEBUG_EVENTS),
            ) as DebugUiState
        }
            .catch { emit(DebugUiState.Failure(FailureReason.ProviderUnavailable)) }
            .flowOn(io)
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000L),
                initialValue = DebugUiState.Loading,
            )
    }
}
