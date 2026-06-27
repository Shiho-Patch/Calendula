package de.jeanlucmakiola.calendula.data.backup

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.work.CoroutineWorker
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
import de.jeanlucmakiola.calendula.R
import de.jeanlucmakiola.calendula.data.calendar.CalendarRepository
import de.jeanlucmakiola.calendula.data.ics.IcsExporter
import de.jeanlucmakiola.calendula.data.prefs.SettingsPrefs
import de.jeanlucmakiola.calendula.domain.ics.IcsWriter
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit
import kotlin.time.Clock

/**
 * Schedules and runs the automatic periodic `.ics` backup of local calendars
 * (issue #8). One-way export only — it mirrors the manual "Back up" into a
 * user-chosen folder on an interval; it is not a sync. Stays INTERNET-free:
 * everything is local provider reads + a local file write.
 */
object BackupScheduler {

    private const val WORK_NAME = "auto-backup"
    private const val WORK_NAME_NOW = "auto-backup-now"

    /**
     * Reconcile the scheduled work with the current settings: enqueue a unique
     * periodic request when backup is on and a folder is set, otherwise cancel
     * it. WorkManager persists the request across reboots on its own.
     *
     * The first run is delayed by one interval so it never overlaps an immediate
     * [runNow] (two simultaneous writes would race and leave a "(1)" duplicate).
     */
    fun apply(context: Context, enabled: Boolean, intervalMinutes: Long, hasFolder: Boolean) {
        val workManager = WorkManager.getInstance(context)
        if (!enabled || !hasFolder) {
            workManager.cancelUniqueWork(WORK_NAME)
            return
        }
        // WorkManager's own floor is 15 min; the UI floors the user choice at 30.
        val interval = intervalMinutes.coerceAtLeast(15L)
        val request = PeriodicWorkRequestBuilder<BackupWorker>(interval, TimeUnit.MINUTES)
            .setInitialDelay(interval, TimeUnit.MINUTES)
            .build()
        workManager.enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    /**
     * Run one export immediately (e.g. right after enabling or changing the
     * folder) for instant feedback. Unique + REPLACE so rapid taps coalesce into
     * a single run rather than racing each other.
     */
    fun runNow(context: Context) {
        WorkManager.getInstance(context).enqueueUniqueWork(
            WORK_NAME_NOW,
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<BackupWorker>().build(),
        )
    }
}

/**
 * Exports the local calendars to `calendula-backup.ics` in the configured folder,
 * overwriting the previous file. Pulls its collaborators through a Hilt
 * [EntryPoint] so it works under WorkManager's default (no-arg) worker factory —
 * no custom factory / Application wiring needed. Records the outcome for the
 * settings status line, and notifies after repeated failures.
 */
class BackupWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Deps {
        fun settingsPrefs(): SettingsPrefs
        fun repository(): CalendarRepository
        fun exporter(): IcsExporter
    }

    override suspend fun doWork(): Result {
        val deps = EntryPointAccessors.fromApplication(applicationContext, Deps::class.java)
        val prefs = deps.settingsPrefs()
        val folder = prefs.autoBackupFolderUri.first()
            ?: return Result.failure() // nothing to write to — leave scheduling to settings
        val now = System.currentTimeMillis()
        return try {
            val events = deps.repository().exportEvents()
            val content = IcsWriter().writeCalendar(events, Clock.System.now())
            deps.exporter().writeToFolder(folder.toUri(), BACKUP_FILE_NAME, content)
            prefs.recordAutoBackupRun(success = true, atMillis = now)
            Result.success()
        } catch (e: Exception) {
            prefs.recordAutoBackupRun(success = false, atMillis = now)
            if (prefs.autoBackupStatus.first().consecutiveFailures >= FAILURE_NOTIFY_THRESHOLD) {
                notifyFailure(applicationContext)
            }
            Log.w(TAG, "Automatic backup failed", e)
            Result.retry()
        }
    }

    private fun notifyFailure(context: Context) {
        val canPost = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!canPost) return
        val manager = NotificationManagerCompat.from(context)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.backup_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply { description = context.getString(R.string.backup_channel_description) },
        )
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
        val tap = launch?.let {
            PendingIntent.getActivity(
                context, 0, it,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.backup_failed_title))
            .setContentText(context.getString(R.string.backup_failed_text))
            .setCategory(NotificationCompat.CATEGORY_ERROR)
            .setAutoCancel(true)
            .apply { tap?.let(::setContentIntent) }
            .build()
        try {
            manager.notify(NOTIFICATION_ID, notification)
        } catch (e: SecurityException) {
            Log.w(TAG, "Could not post backup-failure notification", e)
        }
    }

    companion object {
        const val BACKUP_FILE_NAME = "calendula-backup.ics"
        private const val FAILURE_NOTIFY_THRESHOLD = 2
        private const val CHANNEL_ID = "backup"
        private const val NOTIFICATION_ID = 2
        private const val TAG = "BackupWorker"
    }
}
