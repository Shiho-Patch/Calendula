package de.jeanlucmakiola.calendula.data.reminders

/**
 * Still relevant while the event has not ended: a reminder for an event that is
 * already over is pointless to re-surface. Falls back to the begin time when the
 * end is unknown (0L).
 */
internal fun ReminderAlert.isRelevantAt(nowMillis: Long): Boolean =
    (endMillis.takeIf { it > 0L } ?: beginMillis) >= nowMillis

/**
 * The alerts [EventReminderReceiver] may mark handled (`STATE_FIRED`): the ones
 * it posted, plus the ones it silenced whose event is already over.
 *
 * A silenced alert for an event still ahead is deliberately left
 * `STATE_SCHEDULED`. Silencing is not handling — the calendar is switched off in
 * Calendula while the provider still holds `VISIBLE = 1` (a read-only install,
 * or an upgrade whose flush hasn't landed), so switching it back on before the
 * event must still be able to surface the reminder. [ReminderAlertStore.dueAlerts]
 * only ever returns scheduled rows, so marking them here would lose them for
 * good; leaving them makes the provider's own table the stash
 * ([ReminderRecovery]).
 */
internal fun handledAlertIds(
    due: List<ReminderAlert>,
    postedIds: Set<Long>,
    nowMillis: Long,
): List<Long> = due
    .filter { it.alertId in postedIds || !it.isRelevantAt(nowMillis) }
    .map { it.alertId }
