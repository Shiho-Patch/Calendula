package de.jeanlucmakiola.calendula.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import de.jeanlucmakiola.calendula.R
import kotlinx.datetime.LocalDate

/**
 * What a calendar screen shows in the top bar, the FAB and the drawer, which the
 * host draws once for all of them (#184): switching screens changes what they
 * say rather than swapping one bar for another.
 *
 * @param titleDate the date the title's picker opens on, or null for a title
 *   that is only a label.
 * @param drawerDate the date the drawer's picker opens on.
 * @param awayFromToday whether "today" is worth offering.
 */
@Immutable
data class ScreenChrome(
    val title: String,
    val shortTitle: String = title,
    val titleDate: LocalDate?,
    val drawerDate: LocalDate,
    val awayFromToday: Boolean,
    val todayText: String,
)

/** What the chrome does for the screen on show; read when used, so not observed. */
class ScreenActions(
    val onToday: () -> Unit,
    val onJumpToDate: (LocalDate) -> Unit,
    val onCreate: () -> Unit,
)

/** The screen on show's [ScreenChrome] and [ScreenActions], for the host to draw. */
@Stable
class ChromeHost {
    var chrome by mutableStateOf<ScreenChrome?>(null)
        private set

    var actions: ScreenActions? = null
        private set

    internal fun publish(chrome: ScreenChrome, actions: ScreenActions) {
        this.chrome = chrome
        this.actions = actions
    }
}

/** The host's chrome, and whether the screen reading it is the one on show rather than one leaving. */
class ChromeSlot(val host: ChromeHost, val current: Boolean)

val LocalChromeSlot = staticCompositionLocalOf<ChromeSlot?> { null }

/** Hands [chrome] and [actions] to the host, while this screen is the one on show. */
@Composable
fun PublishChrome(chrome: ScreenChrome, actions: ScreenActions) {
    val slot = LocalChromeSlot.current ?: return
    if (slot.current) SideEffect { slot.host.publish(chrome, actions) }
}

/** The top bar every calendar screen shares. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarTopBar(
    host: ChromeHost,
    selectedView: CalendarView,
    quickSwitchViews: List<CalendarView>,
    onCycleView: () -> Unit,
    onOpenDrawer: () -> Unit,
    onOpenSearch: () -> Unit,
    showTodayButton: Boolean,
) {
    val chrome = host.chrome
    TopAppBar(
        title = {
            if (chrome != null) {
                CalendarTitleButton(
                    title = chrome.title,
                    currentDate = chrome.titleDate,
                    onJumpToDate = { host.actions?.onJumpToDate?.invoke(it) },
                    shortTitle = chrome.shortTitle,
                )
            }
        },
        navigationIcon = {
            IconButton(onClick = onOpenDrawer) {
                Icon(
                    imageVector = Icons.Default.Menu,
                    contentDescription = stringResource(R.string.month_open_menu),
                )
            }
        },
        actions = {
            TodayAction(show = showTodayButton, onToday = { host.actions?.onToday?.invoke() })
            IconButton(onClick = onOpenSearch) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = stringResource(R.string.search_action),
                )
            }
            ViewSwitcherPill(
                current = selectedView,
                cycle = quickSwitchViews,
                onCycle = onCycleView,
            )
        },
        // Deliberately flat: M3 lifts the bar to mark content scrolling under
        // it, but here the bar meets the header on the same surface and the
        // tint is what makes that seam look like a separate block (#186).
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            scrolledContainerColor = MaterialTheme.colorScheme.surface,
        ),
    )
}

/** The FAB column every calendar screen shares. */
@Composable
fun CalendarFab(host: ChromeHost, todayInToolbar: Boolean) {
    val chrome = host.chrome ?: return
    CalendarFabColumn(
        todayVisible = chrome.awayFromToday && !todayInToolbar,
        todayText = chrome.todayText,
        onToday = { host.actions?.onToday?.invoke() },
        onCreate = { host.actions?.onCreate?.invoke() },
    )
}

/**
 * A calendar screen's body under the shared chrome, on the ground the screen's
 * own Scaffold used to give it; the host's Scaffold has already made room for
 * the bars, so [content] gets no padding of its own.
 */
@Composable
fun ScreenBody(modifier: Modifier = Modifier, content: @Composable (PaddingValues) -> Unit) {
    Box(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        content(PaddingValues())
    }
}
