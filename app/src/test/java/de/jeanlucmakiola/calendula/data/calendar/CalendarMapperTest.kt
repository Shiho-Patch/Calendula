package de.jeanlucmakiola.calendula.data.calendar

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class CalendarMapperTest {

    private fun reader(
        id: Long = 1L,
        displayName: String? = "Cal",
        accountName: String? = "x@y",
        accountType: String? = "LOCAL",
        color: Int = 0,
        visible: Int = 1,
    ): MapColumnReader = MapColumnReader(
        CalendarProjection.IDX_ID to id,
        CalendarProjection.IDX_DISPLAY_NAME to displayName,
        CalendarProjection.IDX_ACCOUNT_NAME to accountName,
        CalendarProjection.IDX_ACCOUNT_TYPE to accountType,
        CalendarProjection.IDX_COLOR to color,
        CalendarProjection.IDX_VISIBLE to visible,
    )

    @Test
    fun `happy path maps all six columns`() {
        val src = reader(
            id = 42L,
            displayName = "Work",
            accountName = "x@y",
            accountType = "com.google",
            color = 0xFF112233.toInt(),
            visible = 1,
        ).toCalendarSource()
        assertThat(src).isEqualTo(
            de.jeanlucmakiola.calendula.domain.CalendarSource(
                id = 42L,
                displayName = "Work",
                accountName = "x@y",
                accountType = "com.google",
                color = 0xFF112233.toInt(),
                isVisibleInSystem = true,
            )
        )
    }

    @Test
    fun `null displayName falls back to placeholder`() {
        val src = reader(displayName = null).toCalendarSource()
        assertThat(src.displayName).isEqualTo(Fallbacks.UNNAMED_CALENDAR)
    }

    @Test
    fun `visible flag 0 maps to false`() {
        assertThat(reader(visible = 0).toCalendarSource().isVisibleInSystem).isFalse()
    }

    @Test
    fun `visible flag 1 maps to true`() {
        assertThat(reader(visible = 1).toCalendarSource().isVisibleInSystem).isTrue()
    }

    @Test
    fun `null accountName and accountType coerce to empty string`() {
        val src = reader(accountName = null, accountType = null).toCalendarSource()
        assertThat(src.accountName).isEqualTo("")
        assertThat(src.accountType).isEqualTo("")
    }
}
