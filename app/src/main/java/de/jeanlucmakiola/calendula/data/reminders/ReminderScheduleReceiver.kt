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
 * Every out-of-process reason to re-run a reminder scan: our own [ACTION_SCAN]
 * alarm, boot and package-replaced (both wipe pending alarms), and time or
 * timezone changes (both move reminders relative to the armed alarm). All do the
 * same thing, since [ReminderScanner.scan] is idempotent.
 *
 * Exported for the system broadcasts; an early scan triggered by another app is
 * harmless.
 */
@AndroidEntryPoint
class ReminderScheduleReceiver : BroadcastReceiver() {

    @Inject lateinit var scanner: ReminderScanner

    override fun onReceive(context: Context, intent: Intent) {
        // Checked despite every action doing the same thing: the receiver is
        // exported and the broadcasts it takes are protected, so any other
        // action did not come from where it claims to.
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
