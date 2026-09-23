package de.jeanlucmakiola.calendula.ui.month

import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import de.jeanlucmakiola.calendula.domain.EventInstance
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import kotlinx.datetime.TimeZone
import kotlinx.datetime.YearMonth
import kotlinx.datetime.atTime
import kotlinx.datetime.toInstant
import org.junit.jupiter.api.Test

/**
 * How many chip lanes a week row seats at a given height (#190) — the cap that
 * used to be a flat three whatever the device had.
 */
class MonthLaneCapTest {

    private val zone = TimeZone.UTC
    private val jul26 = YearMonth(2026, Month.JULY)
    private val monday = LocalDate(2026, 7, 6)

    /** Cell chrome above the band: 6 + 22 + 4. */
    private val header = 32.dp

    private fun timed(day: LocalDate, hour: Int, id: Long) = EventInstance(
        instanceId = id,
        eventId = id,
        calendarId = 1L,
        title = "T$id",
        start = day.atTime(hour, 0).toInstant(zone),
        end = day.atTime(hour + 1, 0).toInstant(zone),
        isAllDay = false,
        color = 0xFFF44336.toInt(),
        location = null,
    )

    /** Jul 6-12, wholly inside July 2026. */
    private fun week(eventsOnMonday: Int) = layoutMonthWeeks(
        jul26,
        DayOfWeek.MONDAY,
        (1..eventsOnMonday).map { timed(monday, hour = it, id = it.toLong()) },
        zone,
    )[1]

    private fun rowFor(bandHeight: Int) = header + bandHeight.dp

    /** A labelSmall line at font scale 1 — what the overflow row measures to. */
    private val overflowRow = 16.dp

    private fun MonthWeek.capAt(rowHeight: androidx.compose.ui.unit.Dp) =
        laneCapFor(rowHeight, overflowRow)

    @Test
    fun `a week that fits spends the overflow row on another lane`() {
        // 80dp of band is four 20dp lanes. Nothing overflows at four, so no
        // room is set aside for a marker that would never be drawn.
        assertThat(week(eventsOnMonday = 4).capAt(rowFor(80))).isEqualTo(4)
    }

    @Test
    fun `a week that overflows pays for the dots out of its own lanes`() {
        // The same 80dp, but a fifth event means the dots have to be drawn, and
        // their 14dp comes off the band before it is divided.
        assertThat(week(eventsOnMonday = 5).capAt(rowFor(80))).isEqualTo(3)
    }

    @Test
    fun `a taller row seats more, with no ceiling`() {
        assertThat(week(eventsOnMonday = 20).capAt(rowFor(120))).isEqualTo(5)
        assertThat(week(eventsOnMonday = 20).capAt(rowFor(220))).isEqualTo(10)
    }

    @Test
    fun `a cramped row seats fewer rather than drawing past its band`() {
        // 58dp was three clipped lanes and dots nobody could see. It is two
        // lanes and a visible marker.
        assertThat(week(eventsOnMonday = 6).capAt(rowFor(58))).isEqualTo(2)
    }

    @Test
    fun `a row with no height to give still seats one lane`() {
        assertThat(week(eventsOnMonday = 6).capAt(rowFor(0))).isEqualTo(1)
        assertThat(week(eventsOnMonday = 6).capAt(10.dp)).isEqualTo(1)
    }

    @Test
    fun `an empty week never charges itself for dots`() {
        assertThat(week(eventsOnMonday = 0).capAt(rowFor(80))).isEqualTo(4)
    }
}
