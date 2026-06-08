package de.jeanlucmakiola.calendula.data.calendar

import de.jeanlucmakiola.calendula.domain.CalendarSource
import de.jeanlucmakiola.calendula.domain.EventDetail
import de.jeanlucmakiola.calendula.domain.EventInstance

/**
 * Test-only fake. Tunable via the three `var` properties; `tick()` simulates
 * a provider change so the repository re-queries.
 */
internal class FakeCalendarDataSource : CalendarDataSource {

    var calendarsResult: List<CalendarSource> = emptyList()
    var instancesResult: (Long, Long) -> List<EventInstance> = { _, _ -> emptyList() }
    var eventDetailResult: (Long) -> EventDetail? = { null }

    private val listeners = mutableListOf<() -> Unit>()

    override fun calendars(): List<CalendarSource> = calendarsResult
    override fun instances(beginMillis: Long, endMillis: Long): List<EventInstance> =
        instancesResult(beginMillis, endMillis)
    override fun eventDetail(eventId: Long): EventDetail? = eventDetailResult(eventId)
    override fun registerChangeListener(listener: () -> Unit) {
        listeners += listener
    }
    override fun unregisterChangeListener(listener: () -> Unit) {
        listeners -= listener
    }

    fun tick() {
        listeners.forEach { it() }
    }
}
