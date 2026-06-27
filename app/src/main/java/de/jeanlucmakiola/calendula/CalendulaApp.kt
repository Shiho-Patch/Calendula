package de.jeanlucmakiola.calendula

import android.app.Application
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.HiltAndroidApp
import de.jeanlucmakiola.calendula.data.backup.BackupScheduler
import de.jeanlucmakiola.calendula.data.backup.BackupWorker
import de.jeanlucmakiola.calendula.data.crash.CrashReporter
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
        // respecting, on-device; the user submits the report by hand).
        CrashReporter.install(this)
        reconcileAutoBackup()
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
}
