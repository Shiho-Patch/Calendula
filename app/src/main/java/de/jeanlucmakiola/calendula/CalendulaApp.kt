package de.jeanlucmakiola.calendula

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import de.jeanlucmakiola.calendula.data.crash.CrashReporter

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
    }
}
