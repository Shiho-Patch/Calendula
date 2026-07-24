package de.jeanlucmakiola.calendula.data.calendar

import android.Manifest
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import com.google.common.truth.Truth.assertThat
import de.jeanlucmakiola.calendula.data.prefs.CalendarPrefs
import de.jeanlucmakiola.calendula.data.prefs.SettingsPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.time.Instant

@RunWith(AndroidJUnit4::class)
class CalendarRepositorySmokeTest {

    @get:Rule
    val permissionRule: GrantPermissionRule =
        GrantPermissionRule.grant(Manifest.permission.READ_CALENDAR)

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun newRepo(): CalendarRepositoryImpl {
        val dataSource = AndroidCalendarDataSource(context)
        val store: DataStore<Preferences> = PreferenceDataStoreFactory.create(
            produceFile = { context.cacheDir.resolve("smoke_test_prefs.preferences_pb") },
        )
        return CalendarRepositoryImpl(
            dataSource,
            CalendarPrefs(store),
            SettingsPrefs(store),
            Dispatchers.IO,
        )
    }

    @Test
    fun calendars_returnsListWithoutCrashing() = runBlocking {
        val repo = newRepo()
        val first = repo.calendars().first()
        assertThat(first).isNotNull()
    }

    @Test
    fun instances_returnsListWithoutCrashing() = runBlocking {
        val repo = newRepo()
        val now = Instant.fromEpochMilliseconds(System.currentTimeMillis())
        val oneDayLater = Instant.fromEpochMilliseconds(System.currentTimeMillis() + 86_400_000L)
        val first = repo.instances(now..oneDayLater).first()
        assertThat(first).isNotNull()
    }
}
