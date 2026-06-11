package de.jeanlucmakiola.calendula

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import de.jeanlucmakiola.calendula.data.prefs.ThemeMode
import de.jeanlucmakiola.calendula.ui.RootScreen
import de.jeanlucmakiola.calendula.ui.settings.SettingsViewModel
import de.jeanlucmakiola.calendula.ui.theme.CalendulaTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    // The occurrence a reminder notification was tapped for (eventId, begin,
    // end — the detail screen's key shape). singleTop + onNewIntent route a
    // tap into the running activity; CalendarHost consumes and clears it.
    private var requestedDetailKey by mutableStateOf<LongArray?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestedDetailKey = intent.detailKeyOrNull()
        setContent {
            // One activity-scoped SettingsViewModel drives both the theme here
            // and the Settings screen, so a theme change applies app-wide at once.
            val settingsViewModel: SettingsViewModel = hiltViewModel()
            val settings by settingsViewModel.state.collectAsStateWithLifecycle()
            val darkTheme = when (settings.themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            CalendulaTheme(
                darkTheme = darkTheme,
                dynamicColor = settings.dynamicColor,
            ) {
                RootScreen(
                    modifier = Modifier.fillMaxSize(),
                    requestedDetailKey = requestedDetailKey,
                    onDetailKeyConsumed = { requestedDetailKey = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.detailKeyOrNull()?.let { requestedDetailKey = it }
    }

    private fun Intent.detailKeyOrNull(): LongArray? {
        val eventId = getLongExtra(EXTRA_EVENT_ID, -1L)
        if (eventId == -1L) return null
        return longArrayOf(
            eventId,
            getLongExtra(EXTRA_BEGIN_MILLIS, 0L),
            getLongExtra(EXTRA_END_MILLIS, 0L),
        )
    }

    companion object {
        private const val EXTRA_EVENT_ID = "de.jeanlucmakiola.calendula.extra.EVENT_ID"
        private const val EXTRA_BEGIN_MILLIS = "de.jeanlucmakiola.calendula.extra.BEGIN"
        private const val EXTRA_END_MILLIS = "de.jeanlucmakiola.calendula.extra.END"

        /**
         * Intent opening the detail screen of one occurrence (reminder
         * notifications). The synthetic data URI keys the intent so
         * PendingIntents for different occurrences never collapse into one.
         */
        fun eventDetailIntent(
            context: Context,
            eventId: Long,
            beginMillis: Long,
            endMillis: Long,
        ): Intent = Intent(context, MainActivity::class.java).apply {
            data = "calendula://event/$eventId/$beginMillis".toUri()
            putExtra(EXTRA_EVENT_ID, eventId)
            putExtra(EXTRA_BEGIN_MILLIS, beginMillis)
            putExtra(EXTRA_END_MILLIS, endMillis)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }
}
