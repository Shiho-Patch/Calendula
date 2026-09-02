package de.jeanlucmakiola.calendula.ui.imports

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.jeanlucmakiola.calendula.data.calendar.CalendarRepository
import de.jeanlucmakiola.calendula.data.di.IoDispatcher
import de.jeanlucmakiola.calendula.data.ics.IcsImporter
import de.jeanlucmakiola.calendula.domain.CalendarSource
import de.jeanlucmakiola.calendula.domain.EventForm
import de.jeanlucmakiola.calendula.domain.ics.IcsImportSummary
import de.jeanlucmakiola.calendula.domain.ics.IcsParseWarning
import de.jeanlucmakiola.calendula.domain.ics.IcsParser
import de.jeanlucmakiola.calendula.domain.ics.ParsedIcsEvent
import de.jeanlucmakiola.calendula.domain.ics.toEventForm
import de.jeanlucmakiola.calendula.domain.isEventTarget
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.TimeZone
import kotlin.coroutines.cancellation.CancellationException
import javax.inject.Inject

/** What an opened/received `.ics` resolved to. */
sealed interface ImportUiState {
    data object Loading : ImportUiState
    data object Importing : ImportUiState

    /** The file held no importable event (or couldn't be read/parsed as one). */
    data object Empty : ImportUiState
    data object Failed : ImportUiState

    /** Exactly one event → review it in the prefilled create form. */
    data class Single(val form: EventForm, val warnings: Set<IcsParseWarning>) : ImportUiState

    /** Several events → pick a target calendar and bulk-import. */
    data class Many(
        val events: List<ParsedIcsEvent>,
        val warnings: Set<IcsParseWarning>,
        val calendars: List<CalendarSource>,
        /**
         * The calendar the file says its events came from, when every one of
         * them names the same — used to preselect a matching target (see
         * [fileCalendarName]).
         */
        val fileCalendarName: String? = null,
    ) : ImportUiState

    data class Done(val summary: IcsImportSummary) : ImportUiState
}

/**
 * Reads an `.ics` [Uri], parses it (pure [IcsParser]) and routes by event count:
 * one event opens the create form for review, many open the bulk-import picker.
 * The bulk import dedups by UID in the repository.
 */
@HiltViewModel
class ImportViewModel @Inject constructor(
    private val repository: CalendarRepository,
    private val importer: IcsImporter,
    @IoDispatcher private val io: CoroutineDispatcher,
) : ViewModel() {

    private val parser = IcsParser()
    private val _state = MutableStateFlow<ImportUiState>(ImportUiState.Loading)
    val state: StateFlow<ImportUiState> = _state.asStateFlow()
    private var started = false

    /**
     * Read + parse [uri] once; subsequent calls (recomposition) are ignored.
     * When [forceMany] is set (an in-app restore), a single-event file still goes
     * through the bulk picker + summary rather than the prefilled create form —
     * a restore is "bring back a backup", not "add this one event".
     */
    fun load(uri: Uri, forceMany: Boolean = false) {
        if (started) return
        started = true
        viewModelScope.launch {
            val parsed = withContext(io) {
                importer.readText(uri)?.let(parser::parse)
            }
            _state.value = when {
                parsed == null -> ImportUiState.Failed
                parsed.events.isEmpty() -> ImportUiState.Empty
                parsed.events.size == 1 && !forceMany -> ImportUiState.Single(
                    form = parsed.events.single().toEventForm(TimeZone.currentSystemDefault()),
                    warnings = parsed.warnings,
                )
                else -> {
                    // The same targets the event form offers ([isEventTarget]).
                    ImportUiState.Many(
                        events = parsed.events,
                        warnings = parsed.warnings,
                        calendars = repository.calendars().first()
                            .filter { it.isEventTarget },
                        fileCalendarName = fileCalendarName(parsed.events),
                    )
                }
            }
        }
    }

    /** Bulk-import the parsed events into [targetCalendarId]; result → [ImportUiState.Done]. */
    fun import(targetCalendarId: Long) {
        val many = _state.value as? ImportUiState.Many ?: return
        viewModelScope.launch {
            _state.value = ImportUiState.Importing
            _state.value = try {
                ImportUiState.Done(repository.importEvents(targetCalendarId, many.events))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ImportUiState.Failed
            }
        }
    }
}

/**
 * The one calendar [events] all came from, or null.
 *
 * Every event has to agree, unnamed ones included: a foreign file where a single
 * `VEVENT` carries a `CATEGORIES` tag and the rest carry nothing names no
 * calendar — it just has one tagged event, and letting it pick the target would
 * land the whole file wherever that tag happens to match.
 */
internal fun fileCalendarName(events: List<ParsedIcsEvent>): String? =
    events.map { it.calendarName }.distinct().singleOrNull()

/**
 * The target calendar to preselect for a bulk import.
 *
 * A calendar named like the one the file came from wins — restoring "Birthdays"
 * onto the Birthdays calendar is what the user means. Otherwise the first
 * *local* calendar, which is the first row the picker shows ("Your calendars"
 * leads), so the pre-selection lines up with the top of the list; failing that
 * the first calendar of any kind.
 */
internal fun defaultImportTarget(
    calendars: List<CalendarSource>,
    fileCalendarName: String?,
): Long? {
    val named = fileCalendarName?.let { name ->
        calendars.firstOrNull { it.displayName.equals(name, ignoreCase = true) }
    }
    return (named ?: calendars.firstOrNull { it.isLocal } ?: calendars.firstOrNull())?.id
}
