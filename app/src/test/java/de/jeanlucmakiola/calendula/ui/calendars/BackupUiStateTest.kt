package de.jeanlucmakiola.calendula.ui.calendars

import com.google.common.truth.Truth.assertThat
import de.jeanlucmakiola.calendula.domain.CalendarSource
import de.jeanlucmakiola.calendula.domain.FailureReason
import org.junit.jupiter.api.Test

/** What the Backup & restore screen offers for a given calendar list (#304). */
class BackupUiStateTest {

    private fun cal(
        id: Long,
        local: Boolean = false,
        writable: Boolean = true,
        visible: Boolean = true,
        managed: Boolean = false,
        syncs: Boolean = true,
    ) = CalendarSource(
        id = id,
        displayName = "Calendar $id",
        accountName = "acc@example.com",
        accountType = if (local) "LOCAL" else "com.google",
        color = 0,
        isVisibleInSystem = visible,
        canModifyContents = writable,
        isLocal = local,
        isManaged = managed,
        syncsEvents = syncs,
    )

    private fun failure(state: BackupUiState) = (state as BackupUiState.Failure).reason

    private fun ready(state: BackupUiState) = state as BackupUiState.Ready

    @Test
    fun `no calendars at all reports the empty device`() {
        assertThat(failure(backupUiState(emptyList())))
            .isEqualTo(FailureReason.NoCalendarsConfigured)
    }

    @Test
    fun `a synced writable calendar can receive a restore`() {
        val state = ready(backupUiState(listOf(cal(1L))))
        assertThat(state.canImport).isTrue()
        assertThat(state.exportable).isEmpty()
    }

    @Test
    fun `the reported case - only a calendar that cannot take events`() {
        // #304: a Google account with calendar sync switched off leaves nothing
        // exportable and nothing importable, which used to render a blank screen.
        assertThat(failure(backupUiState(listOf(cal(1L, syncs = false)))))
            .isEqualTo(FailureReason.NoImportTarget)
    }

    @Test
    fun `a read-only subscription is no import target either`() {
        assertThat(failure(backupUiState(listOf(cal(1L, writable = false)))))
            .isEqualTo(FailureReason.NoImportTarget)
    }

    @Test
    fun `everything switched off is reported as hidden, not as a missing target`() {
        // The remedy differs: a visibility switch fixes this one.
        assertThat(failure(backupUiState(listOf(cal(1L, visible = false)))))
            .isEqualTo(FailureReason.AllCalendarsHidden)
    }

    @Test
    fun `a hidden local calendar still exports`() {
        // Export reads the provider directly, so system visibility is irrelevant
        // to it — calling this a failure would hide a working action.
        val state = ready(backupUiState(listOf(cal(1L, local = true, visible = false))))
        assertThat(state.exportable).hasSize(1)
        assertThat(state.canImport).isFalse()
    }

    @Test
    fun `a managed special-dates mirror is neither exportable nor a target`() {
        assertThat(failure(backupUiState(listOf(cal(1L, local = true, managed = true)))))
            .isEqualTo(FailureReason.NoImportTarget)
    }

    @Test
    fun `a local calendar alongside a synced one offers both halves`() {
        val state = ready(backupUiState(listOf(cal(1L, local = true), cal(2L))))
        assertThat(state.exportable.map { it.id }).containsExactly(1L)
        assertThat(state.canImport).isTrue()
    }
}
