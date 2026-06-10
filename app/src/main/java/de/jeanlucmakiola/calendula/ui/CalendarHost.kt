package de.jeanlucmakiola.calendula.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import de.jeanlucmakiola.calendula.ui.common.CalendarView
import de.jeanlucmakiola.calendula.ui.month.MonthScreen
import de.jeanlucmakiola.calendula.ui.week.WeekScreen

/**
 * Holds the active top-level view (spec M1) and swaps between the calendar
 * screens. Each screen owns its own ViewModel and date anchor; the view-switcher
 * pill in their top bars writes back here via [onSelectView].
 */
@Composable
fun CalendarHost(modifier: Modifier = Modifier) {
    var view by rememberSaveable { mutableStateOf(CalendarView.Month) }
    val onSelectView: (CalendarView) -> Unit = { view = it }

    when (view) {
        CalendarView.Week -> WeekScreen(
            selectedView = view,
            onSelectView = onSelectView,
            modifier = modifier,
        )
        // Month, plus Day as a fallback until the day view lands (v0.5).
        else -> MonthScreen(
            selectedView = view,
            onSelectView = onSelectView,
            modifier = modifier,
        )
    }
}
