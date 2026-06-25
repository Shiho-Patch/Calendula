package de.jeanlucmakiola.calendula.ui.calendars

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.jeanlucmakiola.calendula.data.calendar.CalendarRepository
import de.jeanlucmakiola.calendula.data.di.IoDispatcher
import de.jeanlucmakiola.calendula.data.ics.IcsExporter
import de.jeanlucmakiola.calendula.data.prefs.CalendarPrefs
import de.jeanlucmakiola.calendula.domain.CalendarSource
import de.jeanlucmakiola.calendula.domain.ics.IcsWriter
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Clock
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
    private val icsExporter: IcsExporter,
    private val prefs: CalendarPrefs,
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

    /**
     * Calendars the user has disabled in the app. This screen is the only
     * surface that lists them, so it both reads the set (to dim the rows) and
     * toggles it. Every other surface simply excludes these ids.
     */
    val disabledCalendarIds: StateFlow<Set<Long>> =
        prefs.disabledCalendarIds
            .flowOn(io)
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000L),
                initialValue = emptySet(),
            )

    private val _error = MutableStateFlow(false)
    val error: StateFlow<Boolean> = _error.asStateFlow()

    fun consumeError() { _error.value = false }

    private val _backupResult = MutableStateFlow<BackupResult?>(null)
    val backupResult: StateFlow<BackupResult?> = _backupResult.asStateFlow()

    fun consumeBackupResult() { _backupResult.value = null }

    /**
     * Serialise every event of the writable local calendars into the chosen SAF
     * document [uri] as one `VCALENDAR`. Result (event count, or failure) lands
     * in [backupResult] for a one-shot message.
     */
    fun exportBackup(uri: Uri) {
        viewModelScope.launch {
            _backupResult.value = try {
                val count = withContext(io) {
                    val events = repository.exportEvents()
                    icsExporter.writeDocument(
                        uri = uri,
                        content = IcsWriter().writeCalendar(events, Clock.System.now()),
                    )
                    events.size
                }
                BackupResult.Success(count)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                BackupResult.Failure
            }
        }
    }

    fun createCalendar(displayName: String, color: Int, description: String?) = write {
        repository.createLocalCalendar(displayName, color, description)
    }

    fun updateCalendar(id: Long, displayName: String, color: Int, description: String?) = write {
        repository.updateCalendar(id, displayName, color, description)
    }

    fun deleteCalendar(id: Long) = write {
        repository.deleteCalendar(id)
    }

    /**
     * Enable or disable a calendar app-side. Disabling removes it from every
     * surface but Settings → Calendars (and hides its events) without touching
     * provider data — purely a reversible Calendula-local view choice.
     */
    fun setDisabled(id: Long, disabled: Boolean) {
        viewModelScope.launch {
            val current = prefs.disabledCalendarIds.first()
            val next = if (disabled) current + id else current - id
            if (next != current) prefs.setDisabledCalendarIds(next)
        }
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

/** Outcome of a whole-calendar backup, surfaced once to the screen. */
sealed interface BackupResult {
    data class Success(val eventCount: Int) : BackupResult
    data object Failure : BackupResult
}
