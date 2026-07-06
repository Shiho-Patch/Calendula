package de.jeanlucmakiola.calendula.data.calendar

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class SearchMapperTest {

    private fun searchReader(
        id: Long = 1L,
        calendarId: Long = 7L,
        title: String? = "Birthday",
        dtstart: Long? = 1_000_000_000L,
        dtend: Long? = null,
        duration: String? = "P1D",
        allDay: Int = 1,
        eventColor: Any? = null,
        calendarColor: Int = 0xFFAABBCC.toInt(),
        location: String? = null,
    ): MapColumnReader = MapColumnReader(
        SearchProjection.IDX_ID to id,
        SearchProjection.IDX_CALENDAR_ID to calendarId,
        SearchProjection.IDX_TITLE to title,
        SearchProjection.IDX_DTSTART to dtstart,
        SearchProjection.IDX_DTEND to dtend,
        SearchProjection.IDX_DURATION to duration,
        SearchProjection.IDX_ALL_DAY to allDay,
        SearchProjection.IDX_EVENT_COLOR to eventColor,
        SearchProjection.IDX_CALENDAR_COLOR to calendarColor,
        SearchProjection.IDX_LOCATION to location,
    )

    @Test
    fun `pre-1970 negative dtstart still surfaces in search (issue #34)`() {
        val begin = -157_766_400_000L // 1965-01-01T00:00:00Z
        val result = searchReader(dtstart = begin, dtend = null, duration = "P1D").toSearchResult()
        assertThat(result).isNotNull()
        assertThat(result!!.start.toEpochMilliseconds()).isEqualTo(begin)
        assertThat(result.title).isEqualTo("Birthday")
    }

    @Test
    fun `absent dtstart drops the search hit`() {
        assertThat(searchReader(dtstart = null).toSearchResult()).isNull()
    }
}
