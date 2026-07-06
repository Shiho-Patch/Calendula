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
import de.jeanlucmakiola.calendula.data.prefs.CalendarPrefs
import de.jeanlucmakiola.calendula.data.prefs.SettingsPrefs
import de.jeanlucmakiola.calendula.data.reminders.ReminderNotifier
import de.jeanlucmakiola.calendula.data.reminders.SuppressedReminderStore
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
    private val prefs: CalendarPrefs,
    private val settingsPrefs: SettingsPrefs,
    private val suppressedStore: SuppressedReminderStore,
    private val notifier: ReminderNotifier,
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
     * Enable or disable a calendar app-side. Disabling removes it from every
     * surface but Settings → Calendars (and hides its events) without touching
     * provider data — purely a reversible Calendula-local view choice.
     */
    fun setDisabled(id: Long, disabled: Boolean) {
        viewModelScope.launch {
            val current = prefs.disabledCalendarIds.first()
            val next = if (disabled) current + id else current - id
            if (next != current) {
                prefs.setDisabledCalendarIds(next)
                if (!disabled) recoverReminders(setOf(id))
            }
        }
    }

    /**
     * Enable or disable every calendar of one account in a single write — the
     * "toggle all" affordance on an account header. Done as one set update so the
     * per-calendar [setDisabled] calls can't race each other.
     */
    fun setAccountDisabled(ids: Collection<Long>, disabled: Boolean) {
        viewModelScope.launch {
            val current = prefs.disabledCalendarIds.first()
            val next = if (disabled) current + ids else current - ids.toSet()
            if (next != current) {
                prefs.setDisabledCalendarIds(next)
                if (!disabled) recoverReminders(current intersect ids.toSet())
            }
        }
    }

    /**
     * Re-post the reminders that fired while [reEnabledIds] were disabled and are
     * still relevant (event not yet over), then drop them from the stash. Runs
     * after the disabled set is written, so the notifier's own disabled gate lets
     * them through. Best-effort at re-enable time: it mirrors the receiver gates
     * (reminders on + postable), and there is no later re-scan, so alerts left
     * unposted because those gates are closed are simply released.
     */
    private suspend fun recoverReminders(reEnabledIds: Set<Long>) {
        if (reEnabledIds.isEmpty()) return
        val recovered = suppressedStore.recoverFor(reEnabledIds, System.currentTimeMillis())
        if (recovered.isNotEmpty() &&
            settingsPrefs.remindersEnabled.first() &&
            notifier.canPost()
        ) {
            recovered.forEach { notifier.post(it) }
        }
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
