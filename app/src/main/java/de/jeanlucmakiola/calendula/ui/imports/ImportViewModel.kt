package de.jeanlucmakiola.calendula.ui.imports

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.jeanlucmakiola.calendula.data.calendar.CalendarRepository
import de.jeanlucmakiola.calendula.data.di.IoDispatcher
import de.jeanlucmakiola.calendula.data.ics.IcsImporter
import de.jeanlucmakiola.calendula.data.prefs.CalendarPrefs
import de.jeanlucmakiola.calendula.domain.CalendarSource
import de.jeanlucmakiola.calendula.domain.EventForm
import de.jeanlucmakiola.calendula.domain.ics.IcsImportSummary
import de.jeanlucmakiola.calendula.domain.ics.IcsParseWarning
import de.jeanlucmakiola.calendula.domain.ics.IcsParser
import de.jeanlucmakiola.calendula.domain.ics.ParsedIcsEvent
import de.jeanlucmakiola.calendula.domain.ics.toEventForm
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
    private val prefs: CalendarPrefs,
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
                    // A disabled calendar is removed from the app, so it can't be
                    // an import target — exclude it alongside the read-only ones.
                    // Managed special-dates calendars are contact-derived and
                    // editor-locked, so they're not a valid destination either.
                    val disabled = prefs.disabledCalendarIds.first()
                    ImportUiState.Many(
                        events = parsed.events,
                        warnings = parsed.warnings,
                        calendars = repository.calendars().first()
                            .filter { it.canModifyContents && !it.isManaged && it.id !in disabled },
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
