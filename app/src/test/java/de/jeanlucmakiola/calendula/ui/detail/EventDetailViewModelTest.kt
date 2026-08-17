package de.jeanlucmakiola.calendula.ui.detail

import android.content.ContextWrapper
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.google.common.truth.Truth.assertThat
import de.jeanlucmakiola.calendula.data.calendar.CalendarRepositoryImpl
import de.jeanlucmakiola.calendula.data.calendar.FakeCalendarDataSource
import de.jeanlucmakiola.calendula.data.ics.IcsExporter
import de.jeanlucmakiola.calendula.data.prefs.CalendarPrefs
import de.jeanlucmakiola.calendula.data.prefs.SettingsPrefs
import de.jeanlucmakiola.calendula.domain.CalendarSource
import de.jeanlucmakiola.calendula.domain.EventDetail
import de.jeanlucmakiola.calendula.domain.EventInstance
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.time.Instant

/**
 * Re-opening an occurrence must re-read it (#196): the view model outlives the
 * sheet, so an edit that changed no time would otherwise show the pre-save row.
 * The re-read stays silent — the loaded content must not blink back to the
 * skeleton on the way.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EventDetailViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()

    @BeforeEach fun setUp() = Dispatchers.setMain(dispatcher)
    @AfterEach fun tearDown() = Dispatchers.resetMain()

    private val beginMillis = 1_781_164_800_000L
    private val endMillis = beginMillis + 3_600_000L

    private fun detail(description: String?) = EventDetail(
        instance = EventInstance(
            instanceId = 42L, eventId = 42L, calendarId = 1L, title = "Standup",
            start = Instant.fromEpochMilliseconds(beginMillis),
            end = Instant.fromEpochMilliseconds(endMillis),
            isAllDay = false, color = 0xFF000000.toInt(), location = null,
        ),
        description = description, organizer = null, attendees = emptyList(), rrule = null,
    )

    private fun viewModel(tempDir: Path, fake: FakeCalendarDataSource): EventDetailViewModel {
        val prefs = CalendarPrefs(
            PreferenceDataStoreFactory.create(
                scope = CoroutineScope(dispatcher),
                produceFile = { tempDir.resolve("detail_prefs.preferences_pb").toFile() },
            ),
        )
        val settings = SettingsPrefs(
            PreferenceDataStoreFactory.create(
                scope = CoroutineScope(dispatcher),
                produceFile = { tempDir.resolve("detail_settings.preferences_pb").toFile() },
            ),
        )
        val repo = CalendarRepositoryImpl(fake, prefs, settings, dispatcher as CoroutineDispatcher)
        // Only `shareUri()` touches the exporter, and nothing here shares.
        return EventDetailViewModel(repo, IcsExporter(ContextWrapper(null)), dispatcher)
    }

    private fun fakeSource(description: () -> String?) = FakeCalendarDataSource().apply {
        calendarsResult = listOf(
            CalendarSource(
                id = 1L, displayName = "Cal", accountName = "acc@local", accountType = "LOCAL",
                color = 0xFF112233.toInt(), isVisibleInSystem = true, canModifyContents = true,
            ),
        )
        eventDetailResult = { detail(description()) }
    }

    @Test
    fun `re-opening the same occurrence re-reads it`(@TempDir tempDir: Path) = runTest(dispatcher) {
        var stored: String? = null
        val vm = viewModel(tempDir, fakeSource { stored })
        val collector = launch(Job()) { vm.state.collect {} }

        vm.open(42L, beginMillis, endMillis)
        advanceUntilIdle()
        assertThat((vm.state.value as EventDetailUiState.Success).detail.description).isNull()

        // The edit screen saved a description; the tapped occurrence is unchanged.
        stored = "Bring the roadmap"
        vm.open(42L, beginMillis, endMillis)
        advanceUntilIdle()
        assertThat((vm.state.value as EventDetailUiState.Success).detail.description)
            .isEqualTo("Bring the roadmap")

        collector.cancel()
    }

    @Test
    fun `the re-read does not fall back to the skeleton`(@TempDir tempDir: Path) = runTest(dispatcher) {
        val vm = viewModel(tempDir, fakeSource { null })
        val seen = mutableListOf<EventDetailUiState>()
        val collector = launch(Job()) { vm.state.collect { seen += it } }

        vm.open(42L, beginMillis, endMillis)
        advanceUntilIdle()
        assertThat(vm.state.value).isInstanceOf(EventDetailUiState.Success::class.java)

        seen.clear()
        vm.open(42L, beginMillis, endMillis)
        advanceUntilIdle()
        assertThat(seen).doesNotContain(EventDetailUiState.Loading)

        collector.cancel()
    }
}
