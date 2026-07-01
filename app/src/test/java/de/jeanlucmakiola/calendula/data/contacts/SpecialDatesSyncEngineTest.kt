package de.jeanlucmakiola.calendula.data.contacts

import android.provider.CalendarContract
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.google.common.truth.Truth.assertThat
import de.jeanlucmakiola.calendula.data.calendar.FakeCalendarDataSource
import de.jeanlucmakiola.calendula.data.calendar.ManagedEventRow
import de.jeanlucmakiola.calendula.data.prefs.SettingsPrefs
import de.jeanlucmakiola.calendula.domain.contacts.ContactSpecialDate
import de.jeanlucmakiola.calendula.domain.contacts.SpecialDateType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class SpecialDatesSyncEngineTest {

    private val today = LocalDate(2026, 3, 1)

    private class FakeContacts(
        var permission: Boolean = true,
        var dates: List<ContactSpecialDate> = emptyList(),
    ) : ContactSpecialDatesDataSource {
        override fun hasPermission() = permission
        override fun readSpecialDates() = dates
    }

    private class FakeSpec : SpecialDatesCalendarSpec {
        override fun displayName(type: SpecialDateType) = type.name
        override fun color(type: SpecialDateType) = 0xFF112233.toInt()
        override fun defaultTitleTemplate(type: SpecialDateType) = when (type) {
            SpecialDateType.Birthday -> "{name}'s birthday ({age})"
            SpecialDateType.Anniversary -> "{name}'s anniversary"
            SpecialDateType.Custom -> "{name}"
        }
    }

    private fun prefs(tempDir: Path): SettingsPrefs {
        val store: DataStore<Preferences> = PreferenceDataStoreFactory.create(
            produceFile = { tempDir.resolve("settings_test.preferences_pb").toFile() },
        )
        return SettingsPrefs(store)
    }

    private fun birthday(key: String, name: String, month: Int, day: Int, year: Int?) =
        ContactSpecialDate(key, name, SpecialDateType.Birthday, month, day, year)

    private suspend fun engineWith(
        tempDir: Path,
        contacts: FakeContacts,
        calendars: FakeCalendarDataSource = FakeCalendarDataSource(),
        enabled: Boolean = true,
    ): Pair<SpecialDatesSyncEngine, SettingsPrefs> {
        val settings = prefs(tempDir)
        if (enabled) settings.setSpecialDatesEnabled(true)
        return SpecialDatesSyncEngine(contacts, calendars, settings, FakeSpec()) to settings
    }

    @Test
    fun `disabled feature is a no-op`(@TempDir tempDir: Path) = runTest {
        val calendars = FakeCalendarDataSource()
        val (engine, _) = engineWith(tempDir, FakeContacts(), calendars, enabled = false)
        assertThat(engine.sync(today)).isEqualTo(SpecialDatesSyncResult.Disabled)
        assertThat(calendars.createdManagedCalendars).isEmpty()
    }

    @Test
    fun `missing permission reports stalled without touching calendars`(@TempDir tempDir: Path) = runTest {
        val calendars = FakeCalendarDataSource()
        val (engine, _) = engineWith(tempDir, FakeContacts(permission = false), calendars)
        assertThat(engine.sync(today)).isEqualTo(SpecialDatesSyncResult.PermissionMissing)
        assertThat(calendars.createdManagedCalendars).isEmpty()
    }

    @Test
    fun `first sync creates calendars and inserts one event per contact`(@TempDir tempDir: Path) = runTest {
        val calendars = FakeCalendarDataSource()
        val contacts = FakeContacts(
            dates = listOf(
                birthday("a", "Jane", 5, 14, 1990),
                birthday("b", "Bob", 12, 25, null),
            ),
        )
        val (engine, settings) = engineWith(tempDir, contacts, calendars)

        assertThat(engine.sync(today)).isEqualTo(SpecialDatesSyncResult.Success)
        // All three type calendars exist; the birthday one holds both contacts.
        assertThat(calendars.createdManagedCalendars.map { it.type })
            .containsExactlyElementsIn(SpecialDateType.entries)
        assertThat(calendars.insertedManagedEvents).hasSize(2)
        assertThat(calendars.insertedManagedEvents.map { it.second })
            .containsExactly("contact-birthday:a@calendula", "contact-birthday:b@calendula")
        // Year-less contact drops the age; year-known keeps it.
        val titles = calendars.insertedManagedEvents.map { it.first.title }
        assertThat(titles).contains("Jane's birthday (36)")
        assertThat(titles).contains("Bob's birthday")
        // Calendar ids were persisted for the editor / next sync.
        assertThat(settings.specialDatesCalendars.first()).containsKey(SpecialDateType.Birthday)
    }

    @Test
    fun `a second sync with no changes writes nothing`(@TempDir tempDir: Path) = runTest {
        val calendars = FakeCalendarDataSource()
        val contacts = FakeContacts(dates = listOf(birthday("a", "Jane", 5, 14, 1990)))
        val (engine, _) = engineWith(tempDir, contacts, calendars)

        engine.sync(today)
        val insertsAfterFirst = calendars.insertedManagedEvents.size
        val calendarsAfterFirst = calendars.createdManagedCalendars.size
        engine.sync(today)

        assertThat(calendars.insertedManagedEvents).hasSize(insertsAfterFirst)
        assertThat(calendars.createdManagedCalendars).hasSize(calendarsAfterFirst)
        assertThat(calendars.updatedManagedFields).isEmpty()
        assertThat(calendars.deletedEventIds).isEmpty()
    }

    @Test
    fun `a renamed contact triggers a title-only managed update`(@TempDir tempDir: Path) = runTest {
        val calendars = FakeCalendarDataSource()
        val contacts = FakeContacts(dates = listOf(birthday("a", "Jane", 5, 14, 1990)))
        val (engine, _) = engineWith(tempDir, contacts, calendars)
        engine.sync(today)

        contacts.dates = listOf(birthday("a", "Janet", 5, 14, 1990))
        engine.sync(today)

        assertThat(calendars.updatedManagedFields).hasSize(1)
        val (_, columns) = calendars.updatedManagedFields.single()
        // Only managed columns are ever written — never reminders/location.
        assertThat(columns.keys).containsExactly(CalendarContract.Events.TITLE)
        assertThat(columns[CalendarContract.Events.TITLE]).isEqualTo("Janet's birthday (36)")
    }

    @Test
    fun `a removed contact deletes its event`(@TempDir tempDir: Path) = runTest {
        val calendars = FakeCalendarDataSource()
        val contacts = FakeContacts(dates = listOf(birthday("a", "Jane", 5, 14, 1990)))
        val (engine, _) = engineWith(tempDir, contacts, calendars)
        engine.sync(today)

        contacts.dates = emptyList()
        engine.sync(today)

        assertThat(calendars.deletedEventIds).hasSize(1)
    }

    @Test
    fun `disabling a type deletes its calendar on the next sync`(@TempDir tempDir: Path) = runTest {
        val calendars = FakeCalendarDataSource()
        val (engine, settings) = engineWith(tempDir, FakeContacts(), calendars)
        engine.sync(today)
        val anniversaryId = settings.specialDatesCalendars.first().getValue(SpecialDateType.Anniversary)

        settings.setSpecialDateTypeEnabled(SpecialDateType.Anniversary, false)
        engine.sync(today)

        assertThat(calendars.deletedCalendarIds).contains(anniversaryId)
        assertThat(settings.specialDatesCalendars.first()).doesNotContainKey(SpecialDateType.Anniversary)
    }

    // --- pure diff ---

    private fun desired(uid: String, title: String, dt: Long, rrule: String? = "FREQ=YEARLY") =
        ManagedEventDesired(uid, title, dt, rrule)

    private fun row(id: Long, uid: String, title: String, dt: Long, rrule: String? = "FREQ=YEARLY") =
        ManagedEventRow(id, uid, title, dt, rrule)

    @Test
    fun `diff inserts new, deletes gone, and leaves unchanged alone`() {
        val diff = diffManagedEvents(
            desired = listOf(desired("keep", "Jane", 100), desired("new", "Ada", 200)),
            existing = listOf(row(1, "keep", "Jane", 100), row(2, "gone", "Old", 300)),
        )
        assertThat(diff.insertUids).containsExactly("new")
        assertThat(diff.deleteEventIds).containsExactly(2L)
        assertThat(diff.updates).isEmpty()
    }

    @Test
    fun `diff emits only the changed managed columns`() {
        val diff = diffManagedEvents(
            desired = listOf(desired("a", "Janet", 150)),
            existing = listOf(row(9, "a", "Jane", 100)),
        )
        val update = diff.updates.single()
        assertThat(update.eventId).isEqualTo(9L)
        assertThat(update.columns.keys)
            .containsExactly(CalendarContract.Events.TITLE, CalendarContract.Events.DTSTART)
    }
}
