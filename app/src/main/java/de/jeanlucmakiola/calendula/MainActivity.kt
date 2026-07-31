package de.jeanlucmakiola.calendula

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.CalendarContract
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.IntentCompat
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import de.jeanlucmakiola.calendula.data.prefs.ThemeMode
import de.jeanlucmakiola.calendula.data.prefs.is24Hour
import de.jeanlucmakiola.calendula.domain.EventForm
import de.jeanlucmakiola.calendula.domain.buildInsertEventForm
import de.jeanlucmakiola.calendula.ui.RootScreen
import de.jeanlucmakiola.calendula.ui.common.LocalShowHourLines
import de.jeanlucmakiola.calendula.ui.common.LocalTimelineZoom
import de.jeanlucmakiola.calendula.ui.common.rememberTimelineZoom
import de.jeanlucmakiola.calendula.ui.common.LocalSoftenColors
import de.jeanlucmakiola.calendula.ui.common.LocalUse24HourFormat
import de.jeanlucmakiola.calendula.ui.WidgetNavRequest
import de.jeanlucmakiola.calendula.ui.common.CalendarView
import de.jeanlucmakiola.calendula.ui.detail.EventDetailViewModel.Companion.NO_OCCURRENCE_TIME
import de.jeanlucmakiola.floret.components.DebugRibbon
import de.jeanlucmakiola.calendula.ui.crash.CrashReportActivity
import de.jeanlucmakiola.calendula.domain.FontRole
import de.jeanlucmakiola.calendula.ui.settings.SettingsViewModel
import de.jeanlucmakiola.floret.crash.CrashReportDialog
import de.jeanlucmakiola.floret.crash.CrashReporter
import de.jeanlucmakiola.floret.crash.submitCrashReport
import de.jeanlucmakiola.calendula.ui.theme.CalendulaTheme
import de.jeanlucmakiola.calendula.ui.theme.calendulaTypography
import de.jeanlucmakiola.calendula.ui.theme.resolveFontFamily
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Instant

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

    // A prefilled new-event form from an external ACTION_INSERT launch (another
    // app/widget asking us to create an event, issue #30). Consumed once by
    // CalendarHost, which opens it in the create form for review.
    private var requestedInsertForm by mutableStateOf<EventForm?>(null)

    // An external "edit this event" (ACTION_EDIT on content://.../events/<id>):
    // opens the occurrence in the edit form. Same occurrence-key shape as the
    // detail channel; consumed once by CalendarHost.
    private var requestedEditKey by mutableStateOf<LongArray?>(null)

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
        requestedDetailKey = intent.detailKeyOrNull() ?: intent.viewEventKeyOrNull()
        requestedNav = intent.navRequestOrNull()
        requestedImportUri = intent.importUriOrNull()
        requestedInsertForm = intent.insertFormOrNull()
        requestedEditKey = intent.editEventKeyOrNull()
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
            // The app-wide clock convention: the time-format preference resolved
            // against the device's 24-hour system setting, provided once here so
            // every time label reads it via LocalUse24HourFormat.
            val context = LocalContext.current
            val use24Hour = remember(settings.timeFormat, context) {
                settings.timeFormat.is24Hour(android.text.format.DateFormat.is24HourFormat(context))
            }
            // The user's custom-font choice, resolved to a Material typography
            // (issue #19). Recomputed only when a token — or the custom-font
            // re-import stamp AppFontSettings carries, so replacing the file
            // behind an active "custom" token still refreshes — changes;
            // "system for both" returns the default scale untouched.
            // The timeline scale plus the pinch in flight over it (#56). Held
            // here, above the calendar views, so a zoom survives paging between
            // weeks and switching between the week and day view.
            val timelineZoom = rememberTimelineZoom(
                stored = settings.timelineScale,
                onPersist = settingsViewModel::setTimelineScale,
            )
            val fonts by settingsViewModel.fontState.collectAsStateWithLifecycle()
            val typography = remember(fonts, context) {
                calendulaTypography(
                    brand = resolveFontFamily(fonts.brand, FontRole.BRAND, context),
                    plain = resolveFontFamily(fonts.plain, FontRole.PLAIN, context),
                )
            }
            CalendulaTheme(
                darkTheme = darkTheme,
                dynamicColor = settings.dynamicColor,
                typography = typography,
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    CompositionLocalProvider(
                        LocalUse24HourFormat provides use24Hour,
                        LocalShowHourLines provides settings.showHourLines,
                        LocalTimelineZoom provides timelineZoom,
                        LocalSoftenColors provides settings.softenColors,
                    ) {
                        RootScreen(
                            modifier = Modifier.fillMaxSize(),
                            requestedDetailKey = requestedDetailKey,
                            onDetailKeyConsumed = { requestedDetailKey = null },
                            widgetNavRequest = requestedNav,
                            onWidgetNavConsumed = { requestedNav = null },
                            requestedImportUri = requestedImportUri,
                            onImportConsumed = { requestedImportUri = null },
                            requestedInsertForm = requestedInsertForm,
                            onInsertConsumed = { requestedInsertForm = null },
                            requestedEditKey = requestedEditKey,
                            onEditKeyConsumed = { requestedEditKey = null },
                        )
                    }
                    // A persistent corner marker so a debug build is never
                    // mistaken for the production app; compiled out of release.
                    if (BuildConfig.DEBUG) DebugRibbon()
                }
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
        (intent.detailKeyOrNull() ?: intent.viewEventKeyOrNull())?.let { requestedDetailKey = it }
        intent.navRequestOrNull()?.let { requestedNav = it }
        intent.importUriOrNull()?.let { requestedImportUri = it }
        intent.insertFormOrNull()?.let { requestedInsertForm = it }
        intent.editEventKeyOrNull()?.let { requestedEditKey = it }
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
        // The calendar "view time" Uri (a date tap) is also ACTION_VIEW/content;
        // it's a navigation, not a file to import, so [navRequestOrNull] owns it.
        if (uri.host == CALENDAR_PROVIDER_HOST) return null
        return uri.takeIf { it.scheme == "content" || it.scheme == "file" }
    }

    /**
     * A prefilled new-event form from an external launch asking us to create an
     * event — another app or widget (e.g. Todo Agenda) firing `ACTION_INSERT`
     * (issue #30), or `ACTION_EDIT` with no concrete event id (AOSP's "edit a new
     * event", i.e. create). The new event's fields ride as CalendarContract
     * extras; anything omitted falls back to the in-app "new event" defaults in
     * [buildInsertEventForm].
     */
    private fun Intent.insertFormOrNull(): EventForm? {
        // ACTION_EDIT / ACTION_INSERT_OR_EDIT on an existing event route to the
        // edit form instead ([editEventKeyOrNull]); an id-less one is a create,
        // as is any plain ACTION_INSERT.
        val isCreate = action == Intent.ACTION_INSERT ||
            ((action == Intent.ACTION_EDIT || action == Intent.ACTION_INSERT_OR_EDIT) &&
                editEventKeyOrNull() == null)
        if (!isCreate) return null
        return buildInsertEventForm(
            beginMillis = longExtraOrNull(CalendarContract.EXTRA_EVENT_BEGIN_TIME),
            endMillis = longExtraOrNull(CalendarContract.EXTRA_EVENT_END_TIME),
            isAllDay = getBooleanExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, false),
            title = getStringExtra(CalendarContract.Events.TITLE),
            description = getStringExtra(CalendarContract.Events.DESCRIPTION),
            location = getStringExtra(CalendarContract.Events.EVENT_LOCATION),
            rrule = getStringExtra(CalendarContract.Events.RRULE),
            zone = TimeZone.currentSystemDefault(),
            now = Clock.System.now(),
        )
    }

    /** A Long extra's value, or null when the extra is absent. */
    private fun Intent.longExtraOrNull(key: String): Long? =
        if (hasExtra(key)) getLongExtra(key, 0L) else null

    /**
     * The date a launcher/clock date tap points at, parsed from the AOSP calendar
     * "view time" intent: ACTION_VIEW on `content://com.android.calendar/time/
     * <epochMillis>`. Null for any other intent. The matching manifest filter is
     * what lets users pick Calendula from the system calendar chooser (issue #9).
     */
    private fun Intent.calendarTimeDateOrNull(): LocalDate? {
        if (action != Intent.ACTION_VIEW) return null
        val uri = data ?: return null
        if (uri.host != CALENDAR_PROVIDER_HOST) return null
        val segments = uri.pathSegments
        if (segments.firstOrNull() != "time") return null
        val millis = segments.getOrNull(1)?.toLongOrNull() ?: return null
        return Instant.fromEpochMilliseconds(millis)
            .toLocalDateTime(TimeZone.currentSystemDefault()).date
    }

    private fun Intent.navRequestOrNull(): WidgetNavRequest? {
        // An external date tap (launcher/clock) has no widget source, so it opens
        // the day view rooted over the default home view (OpenDate source = null).
        calendarTimeDateOrNull()?.let { return WidgetNavRequest.OpenDate(it.toString(), source = null) }
        // A widget header tap: open a top-level view with no date drill-in. The
        // empty string carried by [openViewIntent] means "the default home view".
        if (hasExtra(EXTRA_OPEN_VIEW)) {
            val name = getStringExtra(EXTRA_OPEN_VIEW).orEmpty()
            return WidgetNavRequest.OpenView(CalendarView.entries.firstOrNull { it.name == name })
        }
        val source = sourceViewOrNull()
        val eventId = getLongExtra(EXTRA_EVENT_ID, -1L)
        return when {
            // Launcher long-press "New event" shortcut. Static shortcut intents
            // can't carry typed extras, so the action alone signals create-on-today.
            action == ACTION_NEW_EVENT -> WidgetNavRequest.Create(null)
            getBooleanExtra(EXTRA_CREATE, false) ->
                WidgetNavRequest.Create(getStringExtra(EXTRA_DATE_ISO))
            // A widget event tap carries both the occurrence and its source view;
            // reminders (no source) fall through to [detailKeyOrNull] instead.
            source != null && eventId != -1L -> WidgetNavRequest.OpenEvent(
                eventId = eventId,
                beginMillis = getLongExtra(EXTRA_BEGIN_MILLIS, 0L),
                endMillis = getLongExtra(EXTRA_END_MILLIS, 0L),
                source = source,
            )
            source != null && getStringExtra(EXTRA_DATE_ISO) != null ->
                WidgetNavRequest.OpenDate(getStringExtra(EXTRA_DATE_ISO)!!, source)
            else -> null
        }
    }

    /** The view the launching widget represents, if any (see [EXTRA_SOURCE_VIEW]). */
    private fun Intent.sourceViewOrNull(): CalendarView? =
        getStringExtra(EXTRA_SOURCE_VIEW)
            ?.let { name -> CalendarView.entries.firstOrNull { it.name == name } }

    private fun Intent.detailKeyOrNull(): LongArray? {
        // A widget event tap (source present) is routed through [navRequestOrNull]
        // so it can also set the base view; only sourceless reminder taps land here.
        if (sourceViewOrNull() != null) return null
        val eventId = getLongExtra(EXTRA_EVENT_ID, -1L)
        if (eventId == -1L) return null
        return longArrayOf(
            eventId,
            getLongExtra(EXTRA_BEGIN_MILLIS, 0L),
            getLongExtra(EXTRA_END_MILLIS, 0L),
        )
    }

    /**
     * The detail key for an external "open this event" — ACTION_VIEW on
     * `content://com.android.calendar/events/<id>`, the way AOSP fires it (e.g.
     * tapping an existing event in the Todo Agenda widget, issue #48). Reuses the
     * same occurrence-key channel as reminder taps. The launcher passes the
     * occurrence's times as `EXTRA_EVENT_BEGIN_TIME` / `EXTRA_EVENT_END_TIME` when
     * it has them; a bare URI omits them, so we carry [NO_OCCURRENCE_TIME] and
     * [EventDetailViewModel] falls back to the event row's own DTSTART/DTEND
     * rather than rendering at the epoch.
     */
    private fun Intent.viewEventKeyOrNull(): LongArray? {
        if (action != Intent.ACTION_VIEW) return null
        val uri = data ?: return null
        if (uri.host != CALENDAR_PROVIDER_HOST) return null
        val segments = uri.pathSegments
        if (segments.firstOrNull() != "events") return null
        val eventId = segments.getOrNull(1)?.toLongOrNull() ?: return null
        return longArrayOf(
            eventId,
            longExtraOrNull(CalendarContract.EXTRA_EVENT_BEGIN_TIME) ?: NO_OCCURRENCE_TIME,
            longExtraOrNull(CalendarContract.EXTRA_EVENT_END_TIME) ?: NO_OCCURRENCE_TIME,
        )
    }

    /**
     * The occurrence key for an external "edit this event" — `ACTION_EDIT` on
     * `content://com.android.calendar/events/<id>`, the way AOSP fires it (e.g.
     * an assistant, task app, or widget that wants to open the event for editing
     * rather than viewing). Opens it in the edit form. Reuses the same
     * occurrence-key channel as reminder/view taps; the caller passes the
     * occurrence's times as `EXTRA_EVENT_BEGIN_TIME` / `EXTRA_EVENT_END_TIME`
     * when it has them, otherwise we carry [NO_OCCURRENCE_TIME] and
     * [EventEditViewModel.openForEdit] falls back to the event row's own
     * DTSTART/DTEND. An id-less `ACTION_EDIT` — or `ACTION_INSERT_OR_EDIT`, which
     * some apps fire — is a create instead ([insertFormOrNull]).
     */
    private fun Intent.editEventKeyOrNull(): LongArray? {
        if (action != Intent.ACTION_EDIT && action != Intent.ACTION_INSERT_OR_EDIT) return null
        val uri = data ?: return null
        if (uri.host != CALENDAR_PROVIDER_HOST) return null
        val segments = uri.pathSegments
        if (segments.firstOrNull() != "events") return null
        val eventId = segments.getOrNull(1)?.toLongOrNull() ?: return null
        return longArrayOf(
            eventId,
            longExtraOrNull(CalendarContract.EXTRA_EVENT_BEGIN_TIME) ?: NO_OCCURRENCE_TIME,
            longExtraOrNull(CalendarContract.EXTRA_EVENT_END_TIME) ?: NO_OCCURRENCE_TIME,
        )
    }

    companion object {
        // The calendar provider's authority/host. A date tap arrives as
        // ACTION_VIEW on content://com.android.calendar/time/<epochMillis>.
        private const val CALENDAR_PROVIDER_HOST = "com.android.calendar"

        private const val EXTRA_EVENT_ID = "de.jeanlucmakiola.calendula.extra.EVENT_ID"
        private const val EXTRA_BEGIN_MILLIS = "de.jeanlucmakiola.calendula.extra.BEGIN"
        private const val EXTRA_END_MILLIS = "de.jeanlucmakiola.calendula.extra.END"
        private const val EXTRA_DATE_ISO = "de.jeanlucmakiola.calendula.extra.DATE_ISO"
        private const val EXTRA_CREATE = "de.jeanlucmakiola.calendula.extra.CREATE"

        // A widget header tap asking to open a top-level view (no date drill-in).
        // Its value is the target [CalendarView] name, or "" for the default view.
        private const val EXTRA_OPEN_VIEW = "de.jeanlucmakiola.calendula.extra.OPEN_VIEW"

        // The [CalendarView] (by name) of the widget a launch came from. Roots the
        // in-app back stack in that view; absent for non-widget launches (reminders).
        private const val EXTRA_SOURCE_VIEW = "de.jeanlucmakiola.calendula.extra.SOURCE_VIEW"

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

        /**
         * Open the day view anchored on [date], drilled in over the launching
         * widget's [source] view (home-screen widgets). Backing out of the day
         * returns to [source], then to the default home view.
         */
        fun openDateIntent(context: Context, date: LocalDate, source: CalendarView): Intent =
            Intent(context, MainActivity::class.java).apply {
                data = "calendula://date/$date".toUri()
                putExtra(EXTRA_DATE_ISO, date.toString())
                putExtra(EXTRA_SOURCE_VIEW, source.name)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

        /**
         * Open one occurrence's detail from a widget event tap, rooting the back
         * stack in the widget's [source] view. Same occurrence-key shape as
         * [eventDetailIntent]; the [source] extra is what distinguishes a widget
         * tap (sets the base view) from a reminder tap (leaves it untouched).
         */
        fun openEventIntent(
            context: Context,
            eventId: Long,
            beginMillis: Long,
            endMillis: Long,
            source: CalendarView,
        ): Intent = eventDetailIntent(context, eventId, beginMillis, endMillis).apply {
            putExtra(EXTRA_SOURCE_VIEW, source.name)
        }

        /** Open the create-event form prefilled for [date] (home-screen widgets). */
        fun openCreateIntent(context: Context, date: LocalDate): Intent =
            Intent(context, MainActivity::class.java).apply {
                data = "calendula://create/$date".toUri()
                putExtra(EXTRA_CREATE, true)
                putExtra(EXTRA_DATE_ISO, date.toString())
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

        /**
         * Open the app on a top-level [view] with no date drill-in — a widget
         * header tap. A null [view] opens the user's default home view (the agenda
         * widget's "Upcoming" title); a concrete view roots there over the default
         * home (the month widget's month/year title → [CalendarView.Month]). The
         * per-view data URI keeps distinct headers' PendingIntents from collapsing.
         */
        fun openViewIntent(context: Context, view: CalendarView?): Intent =
            Intent(context, MainActivity::class.java).apply {
                data = "calendula://view/${view?.name ?: "default"}".toUri()
                putExtra(EXTRA_OPEN_VIEW, view?.name ?: "")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
    }
}
