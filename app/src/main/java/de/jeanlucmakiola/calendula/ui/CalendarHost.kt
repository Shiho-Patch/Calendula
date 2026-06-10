package de.jeanlucmakiola.calendula.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import de.jeanlucmakiola.calendula.domain.EventInstance
import de.jeanlucmakiola.calendula.ui.common.CalendarView
import de.jeanlucmakiola.calendula.ui.common.rememberCalendarSlideSpec
import de.jeanlucmakiola.calendula.ui.day.DayScreen
import de.jeanlucmakiola.calendula.ui.detail.EventDetailScreen
import de.jeanlucmakiola.calendula.ui.month.MonthScreen
import de.jeanlucmakiola.calendula.ui.settings.SettingsScreen
import de.jeanlucmakiola.calendula.ui.week.WeekScreen
import kotlinx.datetime.LocalDate

/**
 * Holds the active top-level view (spec M1) and swaps between the calendar
 * screens. Each screen owns its own ViewModel and date anchor; the view-switcher
 * pill in their top bars writes back here via [onSelectView].
 */
@Composable
fun CalendarHost(modifier: Modifier = Modifier) {
    var view by rememberSaveable { mutableStateOf(CalendarView.Week) }
    val onSelectView: (CalendarView) -> Unit = { view = it }

    // Tapping a day in the month grid opens the day view anchored to that date.
    var pendingDayIso by rememberSaveable { mutableStateOf<String?>(null) }
    val onOpenDay: (LocalDate) -> Unit = { date ->
        pendingDayIso = date.toString()
        view = CalendarView.Day
    }

    // The event-detail screen (S4) is a full-screen destination hoisted here so
    // it overlays whichever calendar view is active. We forward the tapped
    // occurrence's own times (eventId + begin + end, packed as a saveable
    // long[]) so recurring events show the correct date, not the series start.
    // [heldKey] keeps the last shown key alive through the slide-out (when
    // [detailKey] is cleared); it is set in the tap callback — never a [0,0,0]
    // placeholder — so the destination never loads a bogus id=0 on first frame.
    var detailKey by rememberSaveable { mutableStateOf<LongArray?>(null) }
    var heldKey by remember { mutableStateOf<LongArray?>(null) }
    val onEventClick: (EventInstance) -> Unit = { event ->
        val key = longArrayOf(
            event.eventId,
            event.start.toEpochMilliseconds(),
            event.end.toEpochMilliseconds(),
        )
        heldKey = key
        detailKey = key
    }

    // Settings (M4) is hoisted here so it overlays whichever calendar view is
    // active and survives view switches. (The calendar filter now lives inline
    // in the navigation drawer, so no overlay state is needed for it.)
    var showSettings by rememberSaveable { mutableStateOf(false) }
    val onOpenSettings = { showSettings = true }

    val slideSpec = rememberCalendarSlideSpec()

    Box(modifier = modifier.fillMaxSize()) {
        when (view) {
            CalendarView.Week -> WeekScreen(
                selectedView = view,
                onSelectView = onSelectView,
                onEventClick = onEventClick,
                onOpenSettings = onOpenSettings,
            )
            CalendarView.Day -> DayScreen(
                selectedView = view,
                onSelectView = onSelectView,
                onEventClick = onEventClick,
                onOpenSettings = onOpenSettings,
                initialDateIso = pendingDayIso,
            )
            CalendarView.Month -> MonthScreen(
                selectedView = view,
                onSelectView = onSelectView,
                onOpenDay = onOpenDay,
                onOpenSettings = onOpenSettings,
            )
        }

        // Prefer the live key; fall back to the held one only while sliding out.
        val activeKey = detailKey ?: heldKey
        AnimatedVisibility(
            visible = detailKey != null,
            enter = slideInHorizontally(slideSpec) { it } + fadeIn(),
            exit = slideOutHorizontally(slideSpec) { it } + fadeOut(),
        ) {
            activeKey?.let { key ->
                EventDetailScreen(
                    eventId = key[0],
                    beginMillis = key[1],
                    endMillis = key[2],
                    onBack = { detailKey = null },
                )
            }
        }

        // Settings (M4) — full-screen destination, slides over the calendar.
        AnimatedVisibility(
            visible = showSettings,
            enter = slideInHorizontally(slideSpec) { it } + fadeIn(),
            exit = slideOutHorizontally(slideSpec) { it } + fadeOut(),
        ) {
            SettingsScreen(onBack = { showSettings = false })
        }
    }
}
