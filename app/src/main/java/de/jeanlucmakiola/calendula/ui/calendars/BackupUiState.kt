package de.jeanlucmakiola.calendula.ui.calendars

import de.jeanlucmakiola.calendula.domain.CalendarSource
import de.jeanlucmakiola.calendula.domain.FailureReason
import de.jeanlucmakiola.calendula.domain.calendarListFailure
import de.jeanlucmakiola.calendula.domain.isEventTarget

/**
 * State of the Backup & restore screen (#69). Three states, because the calendar
 * list it is derived from has all three: it arrives empty, it can throw, and
 * "loaded but nothing to offer" is a real outcome the screen used to render as a
 * blank page (#304).
 */
sealed interface BackupUiState {
    data object Loading : BackupUiState
    data class Failure(val reason: FailureReason) : BackupUiState

    /**
     * At least one half of the screen works. [exportable] may be empty (restore
     * only) and [canImport] may be false (export only), but never both — that is
     * a [Failure].
     */
    data class Ready(
        val exportable: List<CalendarSource>,
        val canImport: Boolean,
    ) : BackupUiState
}

/**
 * What the screen can offer for this calendar list.
 *
 * Export covers the app's own local calendars; managed special-dates mirrors are
 * rebuilt from contacts, so they are excluded. Restore can target anything the
 * import picker offers ([isEventTarget]).
 *
 * A failure is raised only when *neither* is possible — deliberately not
 * [calendarListFailure] on its own, which would call an all-hidden device a
 * failure while its local calendars are still perfectly exportable. When both
 * halves are dead the list itself usually says why (no calendars, everything
 * switched off); [FailureReason.NoImportTarget] covers the remaining case, where
 * calendars exist and are visible but every one of them is read-only, managed or
 * not synced to the device.
 */
fun backupUiState(calendars: List<CalendarSource>): BackupUiState {
    val exportable = calendars.filter { it.isLocal && it.canModifyContents && !it.isManaged }
    val canImport = calendars.any { it.isEventTarget }
    if (exportable.isEmpty() && !canImport) {
        return BackupUiState.Failure(
            calendarListFailure(calendars) ?: FailureReason.NoImportTarget,
        )
    }
    return BackupUiState.Ready(exportable = exportable, canImport = canImport)
}
