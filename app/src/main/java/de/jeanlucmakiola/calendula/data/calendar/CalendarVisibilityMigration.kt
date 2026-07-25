package de.jeanlucmakiola.calendula.data.calendar

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import de.jeanlucmakiola.calendula.data.di.IoDispatcher
import de.jeanlucmakiola.calendula.data.prefs.CalendarPrefs
import de.jeanlucmakiola.calendula.domain.calendarVisibilityPlan
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * One-shot reconciliation of the two visibility models Calendula used to run in
 * parallel (#75): the app-local "disabled calendars" set is folded into the
 * system's `Calendars.VISIBLE`, which from now on is the only one. See
 * [calendarVisibilityPlan] for the rules — the app's state wins, so what the
 * user sees today is exactly what they keep seeing after the upgrade.
 *
 * Runs at most once. Both permissions are required (the flag is a provider
 * write), so a run that finds them missing changes nothing and leaves the guard
 * unset — the next launch, or the moment the permission screen grants them,
 * tries again. Only once the writes land is the guard set and the retired key
 * dropped.
 */
@Singleton
class CalendarVisibilityMigration @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dataSource: CalendarDataSource,
    private val prefs: CalendarPrefs,
    @IoDispatcher private val io: CoroutineDispatcher,
) {

    suspend fun runIfNeeded() = withContext(io) {
        if (prefs.visibilityMigrationDone.first()) return@withContext
        if (!hasCalendarPermissions()) return@withContext
        try {
            val plan = calendarVisibilityPlan(
                calendars = dataSource.calendars(),
                disabledCalendarIds = prefs.legacyDisabledCalendarIds(),
            )
            // One calendar per write: the provider skips its reminder-alarm
            // reschedule for anything but a single-id update (see
            // [CalendarDataSource.setCalendarVisible]).
            plan.hide.forEach { dataSource.setCalendarVisible(it, false) }
            plan.show.forEach { dataSource.setCalendarVisible(it, true) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // A part-applied plan is safe to redo: every write is idempotent and
            // the source set is still on disk, so leave the guard unset and try
            // again next launch rather than stranding the user half-migrated.
            Log.w(TAG, "Calendar visibility migration failed; will retry", e)
            return@withContext
        }
        prefs.setVisibilityMigrationDone()
        prefs.clearLegacyDisabledCalendarIds()
    }

    private fun hasCalendarPermissions(): Boolean =
        listOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)
            .all {
                ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
            }

    /** Lets non-injectable entry points (the Application) reach the migration. */
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Deps {
        fun calendarVisibilityMigration(): CalendarVisibilityMigration
    }

    private companion object {
        const val TAG = "CalendarVisibilityMigration"
    }
}
