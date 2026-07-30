package de.jeanlucmakiola.calendula.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import de.jeanlucmakiola.calendula.R
import de.jeanlucmakiola.calendula.domain.CalendarSource

/**
 * One account's calendars, as every surface that lists calendars by account
 * shows them.
 *
 * An account is identified by name **and** type (#77). A Google account and a
 * DAVx5 account can carry the same address and still be two separate accounts,
 * from separate apps: merging them mixed their calendars into one group whose
 * header — source logo, "manage in app", toggle-all, collapsed state — was
 * derived from whichever calendar happened to sort first.
 */
data class CalendarAccountGroup(
    /** Stable identity: what makes two calendars belong to the same account. */
    val key: AccountKey,
    /** The account's own name, as shown when it is unambiguous. */
    val label: String,
    /** True when another group shows the same [label] under a different type. */
    val ambiguous: Boolean,
    val calendars: List<CalendarSource>,
) {
    val accountType: String get() = key.type
}

/** The pair a group is keyed on. */
data class AccountKey(val name: String, val type: String)

/**
 * Group [calendars] under their owning account, preserving the provider's order
 * within each group and ordering groups by first appearance.
 *
 * The label falls back through name → type → the first calendar's own name, so
 * a calendar with no account still lands somewhere sensible.
 */
fun List<CalendarSource>.groupByAccount(): List<CalendarAccountGroup> {
    val grouped = groupBy { AccountKey(it.accountName, it.accountType) }
    val labels = grouped.mapValues { (key, cals) ->
        key.name.ifBlank { key.type }.ifBlank { cals.first().displayName }
    }
    val shared = labels.values.groupingBy { it }.eachCount()
    return grouped.map { (key, cals) ->
        val label = labels.getValue(key)
        CalendarAccountGroup(
            key = key,
            label = label,
            ambiguous = shared.getValue(label) > 1,
            calendars = cals,
        )
    }
}

/**
 * What to write above a group: its account name, qualified with the app the
 * account comes from when another account shares the name (#77).
 */
@Composable
fun accountGroupTitle(group: CalendarAccountGroup): String =
    if (!group.ambiguous) {
        group.label
    } else {
        stringResource(R.string.calendars_account_from_source, group.label, sourceAppName(group.accountType))
    }

/**
 * The human name of the app backing [accountType] — the same app whose icon
 * [SourceLogo] draws. Falls back to the raw account type, which is at least
 * unique, when no installed app resolves for it.
 */
@Composable
fun sourceAppName(accountType: String): String {
    val context = LocalContext.current
    return remember(accountType) {
        val pm = context.packageManager
        val packages = sourceAppPackages(context, accountType)
        packages.firstNotNullOfOrNull { pkg ->
            runCatching { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() }.getOrNull()
        } ?: accountType
    }
}
