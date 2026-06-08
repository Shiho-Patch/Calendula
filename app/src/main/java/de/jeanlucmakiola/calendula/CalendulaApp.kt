package de.jeanlucmakiola.calendula

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * Application entry point. Registered as android:name=".CalendulaApp"
 * in AndroidManifest.xml. Hilt initializes its component graph here.
 */
@HiltAndroidApp
class CalendulaApp : Application()
