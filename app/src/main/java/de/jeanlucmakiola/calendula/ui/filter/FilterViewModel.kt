package de.jeanlucmakiola.calendula.ui.filter

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.jeanlucmakiola.calendula.data.calendar.CalendarRepository
import de.jeanlucmakiola.calendula.data.di.IoDispatcher
import de.jeanlucmakiola.calendula.data.prefs.CalendarPrefs
import de.jeanlucmakiola.calendula.domain.CalendarSource
import de.jeanlucmakiola.calendula.domain.FailureReason
import de.jeanlucmakiola.calendula.ui.common.groupByAccount
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class FilterViewModel @Inject constructor(
    private val repository: CalendarRepository,
    private val prefs: CalendarPrefs,
    @IoDispatcher private val io: CoroutineDispatcher,
) : ViewModel() {

    val state: StateFlow<FilterUiState> =
        combine(
            repository.calendars(),
            prefs.hiddenCalendarIds,
        ) { calendars, hidden ->
            // Calendars switched off device-wide don't belong in the drawer's
            // hide/show list; they live only in Settings → Calendars.
            val enabled = calendars.filter { it.isVisibleInSystem }
            if (enabled.isEmpty()) {
                FilterUiState.Failure(FailureReason.NoCalendarsConfigured)
            } else {
                FilterUiState.Success(groupCalendarsForFilter(enabled, hidden))
            }
        }
            .catch { emit(FilterUiState.Failure(FailureReason.ProviderUnavailable)) }
            .flowOn(io)
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000L),
                initialValue = FilterUiState.Loading,
            )

    /** Show or hide a single calendar; persists the new hidden set. */
    fun setVisible(calendarId: Long, visible: Boolean) {
        viewModelScope.launch {
            val current = prefs.hiddenCalendarIds.first()
            val next = if (visible) current - calendarId else current + calendarId
            if (next != current) prefs.setHiddenCalendarIds(next)
        }
    }
}

/**
 * Group calendars under their owning account — by name *and* type, so two
 * accounts that merely share a name stay apart (#77) — preserving the
 * provider's order within each group and ordering groups by first appearance.
 * A calendar is "visible" when its id is *not* in [hidden].
 */
internal fun groupCalendarsForFilter(
    calendars: List<CalendarSource>,
    hidden: Set<Long>,
): List<AccountGroup> =
    calendars.groupByAccount().map { group ->
        AccountGroup(
            account = group.label,
            accountType = group.accountType,
            ambiguous = group.ambiguous,
            calendars = group.calendars.map { c ->
                CalendarRow(
                    id = c.id,
                    displayName = c.displayName,
                    color = c.color,
                    visible = c.id !in hidden,
                )
            },
        )
    }
