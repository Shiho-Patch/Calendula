package de.jeanlucmakiola.calendula.ui.filter

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.jeanlucmakiola.calendula.data.calendar.CalendarRepository
import de.jeanlucmakiola.calendula.data.di.IoDispatcher
import de.jeanlucmakiola.calendula.data.prefs.CalendarPrefs
import de.jeanlucmakiola.calendula.domain.CalendarSource
import de.jeanlucmakiola.calendula.domain.FailureReason
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
            prefs.disabledCalendarIds,
        ) { calendars, hidden, disabled ->
            // Disabled calendars are gone from the app entirely — they don't
            // belong in the drawer's hide/show list (you can't hide what's
            // already disabled). They live only in Settings → Calendars.
            val enabled = calendars.filterNot { it.id in disabled }
            if (enabled.isEmpty()) {
                FilterUiState.Failure(FailureReason.NoCalendarsConfigured)
            } else {
                FilterUiState.Success(groupByAccount(enabled, hidden))
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
 * Group calendars under their owning account, preserving the provider's order
 * within each group and ordering groups by first appearance. A calendar is
 * "visible" when its id is *not* in [hidden].
 */
internal fun groupByAccount(
    calendars: List<CalendarSource>,
    hidden: Set<Long>,
): List<AccountGroup> =
    calendars
        .groupBy { it.accountLabel() }
        .map { (account, cals) ->
            AccountGroup(
                account = account,
                calendars = cals.map { c ->
                    CalendarRow(
                        id = c.id,
                        displayName = c.displayName,
                        color = c.color,
                        visible = c.id !in hidden,
                    )
                },
            )
        }

/** Account header text: the account name, falling back to its type. */
private fun CalendarSource.accountLabel(): String =
    accountName.takeIf { it.isNotBlank() } ?: accountType.takeIf { it.isNotBlank() } ?: displayName
