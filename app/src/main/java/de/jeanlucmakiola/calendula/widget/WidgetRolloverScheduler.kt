package de.jeanlucmakiola.calendula.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.core.content.getSystemService
import de.jeanlucmakiola.calendula.widget.agenda.AgendaWidgetReceiver
import de.jeanlucmakiola.calendula.widget.month.MonthWidgetReceiver
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * Holds the app's own wake-up for the next local midnight, so the home-screen
 * widgets roll "today" over on the day boundary (#228).
 *
 * The widgets used to lean on `ACTION_DATE_CHANGED`, but that broadcast is not
 * on the implicit-broadcast exemption list, so a manifest-declared receiver has
 * never been given it since Android 8 — leaving only `updatePeriodMillis`, which
 * the system defers in doze and OEM skins throttle harder still. The result was
 * yesterday staying highlighted (and the agenda's past-event dimming staying
 * anchored to yesterday) until something else forced a redraw.
 *
 * Exactly one alarm exists at a time and every firing re-arms the next one, the
 * same shape as [de.jeanlucmakiola.calendula.data.reminders.ReminderAlarmScheduler].
 * It is deliberately **inexact**: `setAndAllowWhileIdle` needs no permission and
 * survives doze (which plain `set` does not), and a rollover that lands a few
 * minutes late is invisible on a sleeping screen. Exact alarms stay reserved for
 * reminder snooze.
 */
object WidgetRolloverScheduler {

    /**
     * Fire just *after* midnight, never exactly on it. An alarm delivered a few
     * milliseconds early would still read the old date and re-arm for an instant
     * later; the offset makes "the day has changed" unambiguous.
     */
    internal val ROLLOVER_SLACK = 5.seconds

    /**
     * Arm the next rollover, or cancel a pending one when no widget is placed.
     * Idempotent, so every trigger (boot, app start, widget added/removed, the
     * rollover itself, a clock or timezone change) can just call it.
     */
    fun sync(context: Context) {
        val appContext = context.applicationContext
        val alarmManager = appContext.getSystemService<AlarmManager>() ?: return
        val pendingIntent = rolloverPendingIntent(appContext)
        if (!hasPlacedWidgets(appContext)) {
            alarmManager.cancel(pendingIntent)
            return
        }
        val triggerAt = nextRolloverAt(Clock.System.now(), TimeZone.currentSystemDefault())
        alarmManager.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP, triggerAt.toEpochMilliseconds(), pendingIntent,
        )
    }

    /**
     * The instant just after the next local midnight following [now] in [zone].
     *
     * Uses the *actual* start of the day rather than 00:00, so it stays correct
     * where a DST jump means midnight never happens (Havana springs from 00:00 to
     * 01:00) and where a whole local date is skipped by a date-line move (Apia
     * had no 30 December 2011) — the loop then walks on to the next real day.
     *
     * The mirror case, a zone that rewinds *across* midnight so the day starts
     * twice, resolves to the earlier start; the widget would then run an hour
     * ahead of the clock. No entry in the current tz database does that (Brazil,
     * which used to, dropped DST in 2019), and `updatePeriodMillis` covers it,
     * so it is not worth carrying state to detect.
     */
    fun nextRolloverAt(now: Instant, zone: TimeZone): Instant {
        val date = now.toLocalDateTime(zone).date
        var days = 1
        while (days <= MAX_LOOKAHEAD_DAYS) {
            val candidate = date.plus(days, DateTimeUnit.DAY).atStartOfDayIn(zone) + ROLLOVER_SLACK
            if (candidate > now) return candidate
            days++
        }
        // Unreachable for any zone in the tz database. Deliberately an hour and
        // not the slack: a 5-second retry would just hit this branch again and
        // wake the device in a loop.
        return now + 1.hours
    }

    private fun hasPlacedWidgets(context: Context): Boolean {
        val manager = AppWidgetManager.getInstance(context) ?: return false
        return PROVIDERS.any {
            manager.getAppWidgetIds(ComponentName(context, it)).isNotEmpty()
        }
    }

    private fun rolloverPendingIntent(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            ROLLOVER_REQUEST_CODE,
            Intent(context, WidgetUpdateReceiver::class.java)
                .setAction(WidgetUpdateReceiver.ACTION_ROLLOVER),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private val PROVIDERS = listOf(
        MonthWidgetReceiver::class.java,
        AgendaWidgetReceiver::class.java,
    )

    /** Fixed: there is only ever one rollover alarm, and re-arming must replace it. */
    private const val ROLLOVER_REQUEST_CODE = 0x0DA1

    /** A gap of more than a couple of days does not exist in any tz database entry. */
    private const val MAX_LOOKAHEAD_DAYS = 3
}
