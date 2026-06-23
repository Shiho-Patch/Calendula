package de.jeanlucmakiola.calendula.qs

import android.app.PendingIntent
import android.os.Build
import android.service.quicksettings.TileService
import de.jeanlucmakiola.calendula.MainActivity
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

/**
 * Quick Settings tile: tapping it opens the create-event form on today — the
 * same action as the launcher "New event" shortcut and the agenda widget's "+".
 * A stateless action tile, so there is no on/off state to keep in sync.
 */
class NewEventTileService : TileService() {

    override fun onClick() {
        super.onClick()
        val today = Clock.System.now()
            .toLocalDateTime(TimeZone.currentSystemDefault()).date
        val intent = MainActivity.openCreateIntent(this, today)
        // Launch only once the device is unlocked: creating an event behind the
        // keyguard makes no sense, and the shade can't start an activity over a
        // locked screen anyway.
        unlockAndRun {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                val pending = PendingIntent.getActivity(
                    this,
                    0,
                    intent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
                startActivityAndCollapse(pending)
            } else {
                @Suppress("DEPRECATION")
                startActivityAndCollapse(intent)
            }
        }
    }
}
