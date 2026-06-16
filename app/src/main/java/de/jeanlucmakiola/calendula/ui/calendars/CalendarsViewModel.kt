package de.jeanlucmakiola.calendula.ui.calendars

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.jeanlucmakiola.calendula.data.calendar.CalendarRepository
import de.jeanlucmakiola.calendula.data.di.IoDispatcher
import de.jeanlucmakiola.calendula.domain.CalendarSource
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException
import javax.inject.Inject

/**
 * Backs the calendar manager: lists every calendar (the screen splits them into
 * the app's own local calendars and read-only/synced ones) and creates,
 * renames, recolors or deletes the local calendars the app owns. Write failures
 * flip [error] so the screen can surface a one-shot message.
 */
@HiltViewModel
class CalendarsViewModel @Inject constructor(
    private val repository: CalendarRepository,
    @IoDispatcher private val io: CoroutineDispatcher,
) : ViewModel() {

    val calendars: StateFlow<List<CalendarSource>> =
        repository.calendars()
            .catch { emit(emptyList()) }
            .flowOn(io)
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000L),
                initialValue = emptyList(),
            )

    private val _error = MutableStateFlow(false)
    val error: StateFlow<Boolean> = _error.asStateFlow()

    fun consumeError() { _error.value = false }

    fun createCalendar(displayName: String, color: Int, description: String?) = write {
        repository.createLocalCalendar(displayName, color, description)
    }

    fun updateCalendar(id: Long, displayName: String, color: Int, description: String?) = write {
        repository.updateCalendar(id, displayName, color, description)
    }

    fun deleteCalendar(id: Long) = write {
        repository.deleteCalendar(id)
    }

    private inline fun write(crossinline block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _error.value = true
            }
        }
    }
}
