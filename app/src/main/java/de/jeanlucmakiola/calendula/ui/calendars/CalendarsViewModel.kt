package de.jeanlucmakiola.calendula.ui.calendars

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import de.jeanlucmakiola.calendula.data.backup.BackupScheduler
import de.jeanlucmakiola.calendula.data.calendar.CalendarRepository
import de.jeanlucmakiola.calendula.data.di.IoDispatcher
import de.jeanlucmakiola.calendula.data.ics.IcsExporter
import de.jeanlucmakiola.calendula.data.prefs.BackupStatus
import de.jeanlucmakiola.calendula.data.prefs.SettingsPrefs
import de.jeanlucmakiola.calendula.domain.CalendarSource
import de.jeanlucmakiola.calendula.domain.ics.IcsWriter
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
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
    @ApplicationContext private val context: Context,
    private val repository: CalendarRepository,
    private val icsExporter: IcsExporter,
    private val settingsPrefs: SettingsPrefs,
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

    /** Automatic-backup settings + last-run status, for the Backup section UI. */
    val autoBackup: StateFlow<AutoBackupUiState> = combine(
        settingsPrefs.autoBackupEnabled,
        settingsPrefs.autoBackupIntervalMinutes,
        settingsPrefs.autoBackupFolderUri,
        settingsPrefs.autoBackupStatus,
    ) { enabled, interval, folder, status ->
        AutoBackupUiState(enabled, interval, folder, status)
    }
        .flowOn(io)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = AutoBackupUiState(),
        )

    /**
     * Managed special-dates calendars whose deletion would not stick. While the
     * feature is on, the sync owns every mirror: it recreates a missing one for
     * an enabled type on the next pass and deletes the leftover of a disabled
     * one (`SpecialDatesSyncEngine.reconcileCalendars`), so either way the
     * delete would appear to work and then undo itself. Turning special dates
     * off empties this set, and deleting a leftover mirror is a real delete from
     * then on.
     *
     * Read off each calendar's own durable marker ([CalendarSource.isManaged],
     * the `CAL_SYNC2` one the editor lock already trusts) rather than the stored
     * ids, which are only rewritten on the next sync pass — a preferences loss
     * would otherwise unlock a live mirror until then.
     */
    val deleteLockedCalendarIds: StateFlow<Set<Long>> = combine(
        calendars,
        settingsPrefs.specialDatesEnabled,
    ) { sources, enabled ->
        if (!enabled) emptySet() else sources.filter { it.isManaged }.map { it.id }.toSet()
    }
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
    fun exportBackup(uri: Uri, calendarIds: Set<Long>? = null) {
        viewModelScope.launch {
            _backupResult.value = try {
                val count = withContext(io) {
                    val events = repository.exportEvents(calendarIds)
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
     * Switch a calendar on or off. This is the app's one visibility model: it
     * writes the system's `Calendars.VISIBLE`, so the calendar disappears from
     * every surface *and* the provider stops (or resumes) scheduling its
     * reminders. Nothing is patched by hand — the provider notifies and the
     * observer re-queries.
     *
     * A reminder that came due while the calendar was off stays gone when it is
     * switched back on. Delivery plans from `Instances` and `Reminders` on every
     * scan (#75), but the watermark has already moved past that moment, so no
     * scan returns it again — and on a read-only install the switch never
     * reaches the provider at all, so nothing even triggers one. Deliberate: the
     * reminder was silenced on purpose, and the event itself is back in view.
     */
    fun setCalendarVisible(id: Long, visible: Boolean) = write {
        repository.setCalendarsVisible(listOf(id), visible)
    }

    /**
     * Switch every calendar of one account on or off — the "toggle all"
     * affordance on an account header. Each row is written on its own, in one
     * coroutine so the writes can't race each other.
     */
    fun setAccountVisible(ids: Collection<Long>, visible: Boolean) = write {
        repository.setCalendarsVisible(ids, visible)
    }

    // --- Automatic backup (issue #8) ------------------------------------

    fun setAutoBackupEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsPrefs.setAutoBackupEnabled(enabled)
            reschedule()
            // Give immediate feedback when turning it on with a folder already set.
            if (enabled && settingsPrefs.autoBackupFolderUri.first() != null) {
                BackupScheduler.runNow(context)
            }
        }
    }

    fun setAutoBackupIntervalMinutes(minutes: Long) {
        viewModelScope.launch {
            settingsPrefs.setAutoBackupIntervalMinutes(minutes)
            reschedule()
        }
    }

    /** Persist the chosen destination folder (taking a durable write grant) and run once. */
    fun setAutoBackupFolder(uri: Uri) {
        viewModelScope.launch {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }
            settingsPrefs.setAutoBackupFolderUri(uri.toString())
            reschedule()
            if (settingsPrefs.autoBackupEnabled.first()) BackupScheduler.runNow(context)
        }
    }

    private suspend fun reschedule() {
        BackupScheduler.apply(
            context = context,
            enabled = settingsPrefs.autoBackupEnabled.first(),
            intervalMinutes = settingsPrefs.autoBackupIntervalMinutes.first(),
            hasFolder = settingsPrefs.autoBackupFolderUri.first() != null,
        )
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

/** Automatic-backup settings + last-run status for the Backup section. */
data class AutoBackupUiState(
    val enabled: Boolean = false,
    val intervalMinutes: Long = SettingsPrefs.DEFAULT_BACKUP_INTERVAL,
    val folderUri: String? = null,
    val status: BackupStatus = BackupStatus(lastRun = 0L, lastSuccess = true, consecutiveFailures = 0),
)

/** Outcome of a whole-calendar backup, surfaced once to the screen. */
sealed interface BackupResult {
    data class Success(val eventCount: Int) : BackupResult
    data object Failure : BackupResult
}
