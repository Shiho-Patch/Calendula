package de.jeanlucmakiola.calendula.data.calendar

import android.provider.CalendarContract
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
        accessLevel: Int = CalendarContract.Calendars.CAL_ACCESS_OWNER,
    ): MapColumnReader = MapColumnReader(
        CalendarProjection.IDX_ID to id,
        CalendarProjection.IDX_DISPLAY_NAME to displayName,
        CalendarProjection.IDX_ACCOUNT_NAME to accountName,
        CalendarProjection.IDX_ACCOUNT_TYPE to accountType,
        CalendarProjection.IDX_COLOR to color,
        CalendarProjection.IDX_VISIBLE to visible,
        CalendarProjection.IDX_ACCESS_LEVEL to accessLevel,
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
                canModifyContents = true,
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

    @Test
    fun `contributor access and above can modify contents`() {
        val contributor = reader(accessLevel = CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR)
        val owner = reader(accessLevel = CalendarContract.Calendars.CAL_ACCESS_OWNER)
        assertThat(contributor.toCalendarSource().canModifyContents).isTrue()
        assertThat(owner.toCalendarSource().canModifyContents).isTrue()
    }

    @Test
    fun `read access cannot modify contents`() {
        val src = reader(accessLevel = CalendarContract.Calendars.CAL_ACCESS_READ)
        assertThat(src.toCalendarSource().canModifyContents).isFalse()
    }

    @Test
    fun `missing access level defaults to read-only`() {
        // WebCal subscriptions on some providers report level 0 (CAL_ACCESS_NONE).
        val src = reader(accessLevel = CalendarContract.Calendars.CAL_ACCESS_NONE)
        assertThat(src.toCalendarSource().canModifyContents).isFalse()
    }
}
