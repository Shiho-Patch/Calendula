package de.jeanlucmakiola.calendula.ui.common

import android.accounts.AccountManager
import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import de.jeanlucmakiola.calendula.R
import de.jeanlucmakiola.calendula.domain.CalendarSource

/**
 * The app's single "which calendar" selection list, shared by the event editor
 * and the .ics import screen. Renders the same grouped-card system as the
 * calendar-manager screen: a category header per source — the device chip for
 * the app's own calendars, the owning app's launcher icon for each synced
 * account — with the calendars beneath it as a connected card, a colour chip on
 * each and a check on the selected one. Emits into the caller's [ColumnScope]
 * (a scrolling column), so the caller owns the surrounding chrome.
 */
@Composable
fun ColumnScope.CalendarPickerGroups(
    calendars: List<CalendarSource>,
    selectedId: Long?,
    onSelect: (Long) -> Unit,
) {
    val local = remember(calendars) { calendars.filter { it.isLocal } }
    val syncedGroups = remember(calendars) {
        calendars.filterNot { it.isLocal }
            .groupBy { it.accountName.ifBlank { it.accountType } }
            .toList()
    }

    if (local.isNotEmpty()) {
        CalendarPickerGroup(
            title = stringResource(R.string.calendars_local_header),
            leading = { LeadingAvatar(Icons.Default.PhoneAndroid) },
            calendars = local,
            selectedId = selectedId,
            onSelect = onSelect,
        )
    }
    syncedGroups.forEachIndexed { index, (account, cals) ->
        if (local.isNotEmpty() || index > 0) Spacer(Modifier.height(16.dp))
        CalendarPickerGroup(
            title = account,
            leading = { SourceLogo(cals.first().accountType) },
            calendars = cals,
            selectedId = selectedId,
            onSelect = onSelect,
        )
    }
}

/** One account's category header (avatar + name) atop its selectable calendars. */
@Composable
private fun CalendarPickerGroup(
    title: String,
    leading: @Composable () -> Unit,
    calendars: List<CalendarSource>,
    selectedId: Long?,
    onSelect: (Long) -> Unit,
) {
    GroupedRow(
        title = title,
        position = Position.Top,
        leading = leading,
    )
    calendars.forEachIndexed { index, calendar ->
        val isSelected = calendar.id == selectedId
        GroupedRow(
            title = calendar.displayName,
            position = if (index == calendars.lastIndex) Position.Bottom else Position.Middle,
            selected = isSelected,
            leading = { CalendarColorChip(calendar.color) },
            trailing = if (isSelected) {
                {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            } else {
                null
            },
            onClick = { onSelect(calendar.id) },
        )
    }
}

/**
 * The source app's launcher icon (Google Calendar, DAVx5, Nextcloud, …) as a
 * round 40dp chip, so each synced account is recognisable at a glance. We load
 * whatever app owns the account from [android.content.pm.PackageManager] rather
 * than bundling brand logos — always accurate, nothing to license. Falls back to
 * a neutral cloud chip when no installed app resolves for the account.
 */
@Composable
fun SourceLogo(accountType: String) {
    val context = LocalContext.current
    val logo = remember(accountType) { sourceAppLogo(context, accountType) }
    if (logo != null) {
        Image(
            bitmap = logo,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape),
        )
    } else {
        LeadingAvatar(Icons.Default.Cloud)
    }
}

/** Neutral circular chip carrying an arbitrary icon (e.g. the local-device mark). */
@Composable
fun LeadingAvatar(icon: ImageVector) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp),
        )
    }
}

/** The launcher icon of the app backing [accountType], preferring the human-facing app. */
private fun sourceAppLogo(context: Context, accountType: String): ImageBitmap? {
    val pm = context.packageManager
    val candidates = buildList {
        curatedSourcePackage(accountType)?.let { add(it) }
        AccountManager.get(context).authenticatorTypes
            .firstOrNull { it.type.equals(accountType, ignoreCase = true) }
            ?.packageName
            ?.let { add(it) }
    }
    for (pkg in candidates) {
        val bitmap = runCatching { pm.getApplicationIcon(pkg).toBitmap() }.getOrNull()
        if (bitmap != null) return bitmap.asImageBitmap()
    }
    return null
}

/** Preferred app for account types whose authenticator isn't the app to open. */
internal fun curatedSourcePackage(accountType: String): String? = when {
    accountType.equals("com.google", ignoreCase = true) -> "com.google.android.calendar"
    else -> null
}
