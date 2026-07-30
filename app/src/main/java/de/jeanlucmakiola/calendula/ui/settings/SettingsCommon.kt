package de.jeanlucmakiola.calendula.ui.settings

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import de.jeanlucmakiola.calendula.R
import de.jeanlucmakiola.calendula.ui.common.LocalUse24HourFormat
import de.jeanlucmakiola.calendula.ui.common.formatMinuteOfDay
import de.jeanlucmakiola.calendula.ui.common.reminderLeadTimeLabel
import de.jeanlucmakiola.floret.components.GroupedListInset
import de.jeanlucmakiola.floret.locale.currentLocale

/**
 * Pieces shared by the settings hub and its sub-screens (`*Settings.kt`). Each
 * sub-screen owns whatever only it uses; anything two of them need lives here.
 */

/**
 * Token-based accent for a leading icon chip (container / on-container pair).
 * Neutral chips stay grey; accents are drawn from the M3 scheme so they adapt
 * to theme, dark mode and dynamic colour.
 *
 * The chips are a scanning aid, so the rule is **no two rows in one group share
 * an accent** — a group in a single colour carries no more information than no
 * colour at all. [Neutral] is not a fourth colour to rotate through but a
 * deliberate step back, for rows that are reference or last-resort rather than
 * somewhere you routinely go.
 */
internal enum class ChipAccent { Neutral, Primary, Secondary, Tertiary }

/**
 * Leading circular icon chip. Colours come from the M3 scheme via a container /
 * on-container token pair, so each accent stays correctly paired across theme,
 * dark mode and dynamic colour.
 */
@Composable
internal fun CategoryIcon(icon: ImageVector, accent: ChipAccent) {
    val scheme = MaterialTheme.colorScheme
    val (background, iconColor) = when (accent) {
        ChipAccent.Neutral -> scheme.surfaceContainerHighest to scheme.onSurfaceVariant
        ChipAccent.Primary -> scheme.primaryContainer to scheme.onPrimaryContainer
        ChipAccent.Secondary -> scheme.secondaryContainer to scheme.onSecondaryContainer
        ChipAccent.Tertiary -> scheme.tertiaryContainer to scheme.onTertiaryContainer
    }
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(background),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconColor,
            modifier = Modifier.size(22.dp),
        )
    }
}

/**
 * A small primary-coloured group label, matching the Calendars settings screen.
 *
 * Starts on [GroupedListInset] — the cards' own edge — so a header, the hint
 * under it and the group it names all share one left margin.
 */
@Composable
internal fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(
            start = GroupedListInset,
            end = GroupedListInset,
            top = 16.dp,
            bottom = 4.dp,
        ),
    )
}

/** Muted supporting text under a [SectionHeader], matching the form-fields hint. */
@Composable
internal fun SettingsHint(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = GroupedListInset, vertical = 4.dp),
    )
}

internal fun openUrl(context: Context, url: String) {
    val intent = Intent(Intent.ACTION_VIEW, url.toUri())
    runCatching { context.startActivity(intent) }
}

/** Label for a global-default choice: empty → "None", else the lead times joined. */
@Composable
internal fun reminderChoiceLabel(minutes: List<Int>): String {
    if (minutes.isEmpty()) return stringResource(R.string.reminder_none)
    return minutes.map { reminderLeadTimeLabel(it) }.joinToString(", ")
}

/**
 * Lead times offered for the all-day default — day-scale, since a "minutes
 * before midnight" reminder on an all-day event is rarely what's wanted. Shared
 * with the contact special-dates calendars, whose events are all all-day.
 */
internal val ALLDAY_REMINDER_PRESETS = listOf(0, 1_440, 2_880, 10_080)

/**
 * A minute-of-day ("09:00") in the app's *own* 12/24-hour convention — the same
 * [LocalUse24HourFormat] the agenda and event rows read, so a time shown in
 * settings can't disagree with the times shown everywhere else.
 */
@Composable
internal fun settingsTimeOfDay(minutesOfDay: Int): String =
    formatMinuteOfDay(minutesOfDay, LocalUse24HourFormat.current, currentLocale())

/**
 * Second line for an all-day lead-time row: the clock time it fires at.
 *
 * An all-day occurrence has no time of its own, so a reminder's stored offset
 * only decides *which day* it belongs to — the hour always comes from the
 * global "show all-day reminders at" setting (see
 * [de.jeanlucmakiola.calendula.domain.reminders.planReminders]). Naming it on
 * every row is what makes "At time of event" readable on an all-day default at
 * all: it means that day at 09:00, not midnight.
 *
 * Shared by the notification defaults, the per-calendar overrides and the
 * contact special-dates calendars — all three read the same one setting.
 */
@Composable
internal fun allDayFiringTimeSummary(allDayReminderTimeMinutes: Int): @Composable (Int) -> String? {
    val summary = stringResource(
        R.string.settings_allday_reminder_fires_at,
        settingsTimeOfDay(allDayReminderTimeMinutes),
    )
    return { summary }
}
