package de.jeanlucmakiola.calendula.data.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Every reason to re-run a reminder scan that arrives from outside the process.
 *
 * All of them do the same thing, because [ReminderScanner.scan] is idempotent
 * and works out what is owed from its watermark rather than from why it was
 * called:
 *
 * - **our own alarm** ([ACTION_SCAN]) — the ordinary case, a reminder is due;
 * - **boot** and **package replaced** — both wipe pending alarms, so the app has
 *   to re-arm or reminders stop silently, which is the failure #75 is about;
 * - **time and timezone changes** — they move every reminder relative to the
 *   armed alarm, and an all-day reminder's fire hour is recomposed in the
 *   current zone, so both need a fresh plan.
 *
 * Exported because the system broadcasts arrive from outside. [ACTION_SCAN] is
 * ours and always sent as an explicit intent; another app triggering a scan
 * early would only make it re-read the provider and re-arm, which is harmless.
 */
@AndroidEntryPoint
class ReminderScheduleReceiver : BroadcastReceiver() {

    @Inject lateinit var scanner: ReminderScanner

    override fun onReceive(context: Context, intent: Intent) {
        // Every action here does the same thing, but the filter still has to be
        // checked: the receiver is exported, and the system broadcasts it takes
        // are protected, so an intent arriving with any other action did not
        // come from where it claims to.
        if (intent.action !in HANDLED_ACTIONS) return
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                scanner.scan()
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_SCAN = "de.jeanlucmakiola.calendula.reminders.SCAN"

        private val HANDLED_ACTIONS = setOf(
            ACTION_SCAN,
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
        )
    }
}
