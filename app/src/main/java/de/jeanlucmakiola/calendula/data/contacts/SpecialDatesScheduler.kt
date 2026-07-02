package de.jeanlucmakiola.calendula.data.contacts

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import de.jeanlucmakiola.calendula.data.prefs.SettingsPrefs
import de.jeanlucmakiola.calendula.data.prefs.SpecialDatesStalledReason
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/**
 * Schedules the contact special-dates mirror. Birthdays change rarely, so this
 * is deliberately cheap: a **daily** periodic reconcile, plus an immediate run
 * on enable / "Sync now" and a debounced one when the app is foregrounded. No
 * ContentObserver — it would need the process alive and buys almost nothing for
 * once-a-year events. Everything stays offline (local contacts → local calendar).
 */
object SpecialDatesScheduler {

    private const val WORK_NAME = "special-dates-sync"
    private const val WORK_NAME_NOW = "special-dates-sync-now"
    private const val WORK_NAME_FOREGROUND = "special-dates-sync-foreground"
    private const val KEY_FOREGROUND = "foreground"

    /** Enqueue (or cancel) the daily periodic reconcile to match [enabled]. */
    fun apply(context: Context, enabled: Boolean) {
        val workManager = WorkManager.getInstance(context)
        if (!enabled) {
            workManager.cancelUniqueWork(WORK_NAME)
            workManager.cancelUniqueWork(WORK_NAME_NOW)
            workManager.cancelUniqueWork(WORK_NAME_FOREGROUND)
            return
        }
        val request = PeriodicWorkRequestBuilder<SpecialDatesSyncWorker>(1, TimeUnit.DAYS)
            // Delay the first periodic run so it never overlaps an immediate run.
            .setInitialDelay(1, TimeUnit.DAYS)
            .build()
        workManager.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    /**
     * Run one reconcile immediately. [foreground] runs (the app resuming) are
     * debounced inside the worker and use their own work name, so a frequent
     * foreground resync can never REPLACE — and swallow — a pending enable /
     * "Sync now" run, which always syncs. The engine serializes the two if they
     * overlap.
     */
    fun runNow(context: Context, foreground: Boolean = false) {
        val request = OneTimeWorkRequestBuilder<SpecialDatesSyncWorker>()
            .setInputData(Data.Builder().putBoolean(KEY_FOREGROUND, foreground).build())
            .build()
        val name = if (foreground) WORK_NAME_FOREGROUND else WORK_NAME_NOW
        WorkManager.getInstance(context)
            .enqueueUniqueWork(name, ExistingWorkPolicy.REPLACE, request)
    }

    internal const val INPUT_FOREGROUND = KEY_FOREGROUND
}

/**
 * Runs the [SpecialDatesSyncEngine]. Pulls its collaborators through a Hilt
 * [EntryPoint] so it works under WorkManager's default worker factory. Records
 * the run for the settings status line; a missing permission parks the feature
 * in a stalled state (surfaced in settings) rather than retrying forever.
 */
class SpecialDatesSyncWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Deps {
        fun settingsPrefs(): SettingsPrefs
        fun syncEngine(): SpecialDatesSyncEngine
    }

    override suspend fun doWork(): Result {
        val deps = EntryPointAccessors.fromApplication(applicationContext, Deps::class.java)
        val prefs = deps.settingsPrefs()

        val now = System.currentTimeMillis()
        // A foreground resume can fire often — skip if we synced recently.
        val foreground = inputData.getBoolean(SpecialDatesScheduler.INPUT_FOREGROUND, false)
        if (foreground && now - prefs.specialDatesLastForegroundSync.first() < FOREGROUND_DEBOUNCE_MILLIS) {
            return Result.success()
        }

        return try {
            when (val result = deps.syncEngine().sync()) {
                // The engine re-checks the toggle, so already-queued work never
                // revives a disabled feature; don't record a run either.
                SpecialDatesSyncResult.Disabled -> return Result.success()
                else -> {
                    val stalled = if (result == SpecialDatesSyncResult.PermissionMissing) {
                        SpecialDatesStalledReason.PermissionRevoked
                    } else {
                        null
                    }
                    prefs.recordSpecialDatesRun(now, stalled)
                }
            }
            if (foreground) prefs.setSpecialDatesLastForegroundSync(now)
            Result.success()
        } catch (e: SecurityException) {
            // A revoked calendar/contacts permission won't fix itself on retry —
            // park the feature in a stalled state (surfaced in settings) instead
            // of retrying with backoff forever.
            Log.w(TAG, "Special-dates sync lacks a required permission", e)
            prefs.recordSpecialDatesRun(now, SpecialDatesStalledReason.PermissionRevoked)
            Result.success()
        } catch (e: Exception) {
            Log.w(TAG, "Special-dates sync failed", e)
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "SpecialDatesSync"

        /** Skip a foreground-triggered sync if one ran within this window (4h). */
        private const val FOREGROUND_DEBOUNCE_MILLIS = 4L * 60 * 60 * 1000
    }
}
