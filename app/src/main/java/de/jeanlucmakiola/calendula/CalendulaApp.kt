package de.jeanlucmakiola.calendula

import android.app.Application
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.HiltAndroidApp
import de.jeanlucmakiola.calendula.data.backup.BackupScheduler
import de.jeanlucmakiola.calendula.data.backup.BackupWorker
import de.jeanlucmakiola.calendula.data.contacts.SpecialDatesScheduler
import de.jeanlucmakiola.calendula.data.contacts.SpecialDatesSyncWorker
import de.jeanlucmakiola.floret.crash.CrashConfig
import de.jeanlucmakiola.floret.crash.CrashReporter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Application entry point. Registered as android:name=".CalendulaApp"
 * in AndroidManifest.xml. Hilt initializes its component graph here.
 */
@HiltAndroidApp
class CalendulaApp : Application() {

    override fun onCreate() {
        super.onCreate()
        // Install first thing so startup crashes are captured too (privacy-
        // respecting, on-device; the user submits the report by hand). The
        // capture/loop-detection/report machinery lives in floret-kit's
        // core-crash; only the app label + issue-tracker URLs are app-specific.
        CrashReporter.install(
            this,
            CrashConfig(
                appLabel = getString(R.string.app_name),
                newIssueUrl = getString(R.string.report_issue_url),
                chooseIssueUrl = getString(R.string.report_issue_choose_url),
                issueTitle = getString(R.string.crash_report_issue_title),
            ),
        )
        reconcileAutoBackup()
        reconcileSpecialDates()
    }

    /**
     * Bring the scheduled auto-backup work back in line with the saved settings
     * on every launch — re-arms it after a reinstall and, crucially, cancels any
     * orphaned work once backup has been turned off.
     */
    private fun reconcileAutoBackup() {
        val deps = EntryPointAccessors.fromApplication(this, BackupWorker.Deps::class.java)
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            val prefs = deps.settingsPrefs()
            BackupScheduler.apply(
                context = this@CalendulaApp,
                enabled = prefs.autoBackupEnabled.first(),
                intervalMinutes = prefs.autoBackupIntervalMinutes.first(),
                hasFolder = prefs.autoBackupFolderUri.first() != null,
            )
        }
    }

    /**
     * Re-arm (or cancel) the daily special-dates reconcile from the saved
     * settings on every launch — like [reconcileAutoBackup], this cancels
     * orphaned work after the feature is turned off and re-schedules after a
     * reinstall. The foreground trigger (RootScreen ON_RESUME) does the on-open
     * refresh, so no immediate run is needed here.
     */
    private fun reconcileSpecialDates() {
        val deps = EntryPointAccessors.fromApplication(this, SpecialDatesSyncWorker.Deps::class.java)
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            SpecialDatesScheduler.apply(
                context = this@CalendulaApp,
                enabled = deps.settingsPrefs().specialDatesEnabled.first(),
            )
        }
    }
}
