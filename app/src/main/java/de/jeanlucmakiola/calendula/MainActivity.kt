package de.jeanlucmakiola.calendula

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.IntentCompat
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import de.jeanlucmakiola.calendula.data.crash.CrashReporter
import de.jeanlucmakiola.calendula.data.prefs.ThemeMode
import de.jeanlucmakiola.calendula.ui.RootScreen
import de.jeanlucmakiola.calendula.ui.WidgetNavRequest
import de.jeanlucmakiola.calendula.ui.crash.CrashReportActivity
import de.jeanlucmakiola.calendula.ui.crash.CrashReportDialog
import de.jeanlucmakiola.calendula.ui.crash.submitCrashReport
import de.jeanlucmakiola.calendula.ui.settings.SettingsViewModel
import de.jeanlucmakiola.calendula.ui.theme.CalendulaTheme
import kotlinx.datetime.LocalDate

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    // The occurrence a reminder notification was tapped for (eventId, begin,
    // end — the detail screen's key shape). singleTop + onNewIntent route a
    // tap into the running activity; CalendarHost consumes and clears it.
    private var requestedDetailKey by mutableStateOf<LongArray?>(null)

    // A navigation a home-screen widget asked for (open a date / start a
    // create). Consumed once by CalendarHost, same pattern as the detail key.
    private var requestedNav by mutableStateOf<WidgetNavRequest?>(null)

    // An .ics file opened/shared into the app (ACTION_VIEW/SEND). Consumed once
    // by CalendarHost's import flow.
    private var requestedImportUri by mutableStateOf<Uri?>(null)

    // A captured crash report awaiting the user's decision, surfaced as a dialog
    // over the calendar on the next launch (the single-crash path). A startup
    // crash-loop is handled out of band, before setContent — see below.
    private var pendingCrashReport by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // If the app keeps crashing as it starts, the main UI can't be trusted
        // to come up — route to the standalone report screen instead of
        // re-entering the crashing graph.
        if (CrashReporter.isCrashLoop(this)) {
            startActivity(
                Intent(this, CrashReportActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
            )
            finish()
            return
        }

        enableEdgeToEdge()
        requestedDetailKey = intent.detailKeyOrNull()
        requestedNav = intent.navRequestOrNull()
        requestedImportUri = intent.importUriOrNull()
        if (CrashReporter.shouldPrompt(this)) pendingCrashReport = CrashReporter.pendingReport(this)
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
                    widgetNavRequest = requestedNav,
                    onWidgetNavConsumed = { requestedNav = null },
                    requestedImportUri = requestedImportUri,
                    onImportConsumed = { requestedImportUri = null },
                )
                pendingCrashReport?.let { report ->
                    CrashReportDialog(
                        report = report,
                        onSend = {
                            submitCrashReport(this@MainActivity, report)
                            CrashReporter.clearReport(this@MainActivity)
                            pendingCrashReport = null
                        },
                        onDismiss = {
                            // Keep the report (Settings can still reach it); just
                            // stop it popping on every launch.
                            CrashReporter.dismissPrompt(this@MainActivity)
                            pendingCrashReport = null
                        },
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Reaching a running UI means startup succeeded; reset the loop trail.
        CrashReporter.markHealthy(this)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.detailKeyOrNull()?.let { requestedDetailKey = it }
        intent.navRequestOrNull()?.let { requestedNav = it }
        intent.importUriOrNull()?.let { requestedImportUri = it }
    }

    /**
     * The `.ics` Uri an external app asked us to open (file manager `ACTION_VIEW`)
     * or share into us (`ACTION_SEND`). Restricted to content/file schemes so the
     * app's own `calendula://` deep-links never match.
     */
    private fun Intent.importUriOrNull(): Uri? {
        val uri = when (action) {
            Intent.ACTION_VIEW -> data
            Intent.ACTION_SEND -> IntentCompat.getParcelableExtra(this, Intent.EXTRA_STREAM, Uri::class.java)
            else -> null
        } ?: return null
        return uri.takeIf { it.scheme == "content" || it.scheme == "file" }
    }

    private fun Intent.navRequestOrNull(): WidgetNavRequest? = when {
        // Launcher long-press "New event" shortcut. Static shortcut intents
        // can't carry typed extras, so the action alone signals create-on-today.
        action == ACTION_NEW_EVENT -> WidgetNavRequest.Create(null)
        getBooleanExtra(EXTRA_CREATE, false) ->
            WidgetNavRequest.Create(getStringExtra(EXTRA_DATE_ISO))
        getStringExtra(EXTRA_DATE_ISO) != null ->
            WidgetNavRequest.OpenDate(getStringExtra(EXTRA_DATE_ISO)!!)
        else -> null
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
        private const val EXTRA_DATE_ISO = "de.jeanlucmakiola.calendula.extra.DATE_ISO"
        private const val EXTRA_CREATE = "de.jeanlucmakiola.calendula.extra.CREATE"

        // Fired by the launcher long-press "New event" shortcut (res/xml/
        // shortcuts.xml hardcodes this string — keep the two in sync).
        const val ACTION_NEW_EVENT = "de.jeanlucmakiola.calendula.action.NEW_EVENT"

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

        /** Open the day view anchored on [date] (home-screen widgets). */
        fun openDateIntent(context: Context, date: LocalDate): Intent =
            Intent(context, MainActivity::class.java).apply {
                data = "calendula://date/$date".toUri()
                putExtra(EXTRA_DATE_ISO, date.toString())
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

        /** Open the create-event form prefilled for [date] (home-screen widgets). */
        fun openCreateIntent(context: Context, date: LocalDate): Intent =
            Intent(context, MainActivity::class.java).apply {
                data = "calendula://create/$date".toUri()
                putExtra(EXTRA_CREATE, true)
                putExtra(EXTRA_DATE_ISO, date.toString())
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
    }
}
