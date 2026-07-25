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
        description: String? = null,
        syncEvents: Int? = 1,
    ): MapColumnReader = MapColumnReader(
        CalendarProjection.IDX_ID to id,
        CalendarProjection.IDX_DISPLAY_NAME to displayName,
        CalendarProjection.IDX_ACCOUNT_NAME to accountName,
        CalendarProjection.IDX_ACCOUNT_TYPE to accountType,
        CalendarProjection.IDX_COLOR to color,
        CalendarProjection.IDX_VISIBLE to visible,
        CalendarProjection.IDX_ACCESS_LEVEL to accessLevel,
        CalendarProjection.IDX_DESCRIPTION to description,
        CalendarProjection.IDX_SYNC_EVENTS to syncEvents,
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
    fun `sync_events 0 marks the calendar as not syncing its events`() {
        assertThat(reader(syncEvents = 0).toCalendarSource().syncsEvents).isFalse()
    }

    @Test
    fun `a NULL sync_events column is treated as syncing`() {
        // The harmless default: it only ever holds the visibility migration back
        // from switching a calendar on.
        assertThat(reader(syncEvents = null).toCalendarSource().syncsEvents).isTrue()
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

    @Test
    fun `local account type marks the calendar as app-owned`() {
        val src = reader(accountType = CalendarContract.ACCOUNT_TYPE_LOCAL).toCalendarSource()
        assertThat(src.isLocal).isTrue()
    }

    @Test
    fun `synced account type is not local`() {
        val src = reader(accountType = "com.google").toCalendarSource()
        assertThat(src.isLocal).isFalse()
    }

    @Test
    fun `local calendar exposes its CAL_SYNC1 description`() {
        val src = reader(
            accountType = CalendarContract.ACCOUNT_TYPE_LOCAL,
            description = "House stuff",
        ).toCalendarSource()
        assertThat(src.description).isEqualTo("House stuff")
    }

    @Test
    fun `synced calendar never exposes CAL_SYNC1 as a description`() {
        // CAL_SYNC1 holds the sync token for synced rows — it must not leak as a note.
        val src = reader(
            accountType = "com.google",
            description = """{"type":"SYNC_TOKEN","value":"…"}""",
        ).toCalendarSource()
        assertThat(src.description).isNull()
    }
}
