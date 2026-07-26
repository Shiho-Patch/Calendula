package de.jeanlucmakiola.calendula.data.reminders

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit

/**
 * The backstop under the alarm: a daily scan that runs whether or not the alarm
 * survived.
 *
 * The scan alarm re-arms itself at most a day out, so in the steady state this
 * finds nothing to do. It exists for the case the whole feature is about — a
 * device that quietly drops the alarm without a reboot to announce it. The
 * scheduling half of reminder delivery must not have a single point of failure,
 * which is exactly what the provider's broadcast turned out to be.
 */
object ReminderMaintenanceScheduler {

    private const val WORK_NAME = "reminder-scan-maintenance"

    /** Enqueue the daily backstop; idempotent, so every launch may call it. */
    fun apply(context: Context) {
        val request = PeriodicWorkRequestBuilder<ReminderMaintenanceWorker>(1, TimeUnit.DAYS)
            // The launch scan covers now; let the first periodic run wait.
            .setInitialDelay(1, TimeUnit.DAYS)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
    }
}

class ReminderMaintenanceWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Deps {
        fun reminderScanner(): ReminderScanner
    }

    override suspend fun doWork(): Result = try {
        EntryPointAccessors.fromApplication(applicationContext, Deps::class.java)
            .reminderScanner()
            .scan()
        Result.success()
    } catch (e: Exception) {
        // The scan swallows its own failures; anything reaching here is the
        // entry point itself, which a retry will not mend. Never fail the chain.
        Log.w(TAG, "Reminder maintenance scan failed", e)
        Result.success()
    }

    private companion object {
        const val TAG = "ReminderMaintenance"
    }
}
