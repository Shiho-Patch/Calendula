package de.jeanlucmakiola.calendula.ui.filter

import com.google.common.truth.Truth.assertThat
import de.jeanlucmakiola.calendula.domain.CalendarSource
import org.junit.jupiter.api.Test

class FilterGroupingTest {

    private fun cal(
        id: Long,
        name: String,
        account: String,
        type: String = "com.example",
    ) = CalendarSource(
        id = id,
        displayName = name,
        accountName = account,
        accountType = type,
        color = 0xFF336699.toInt(),
        isVisibleInSystem = true,
    )

    @Test
    fun `groups calendars under their account, preserving order`() {
        val calendars = listOf(
            cal(1, "Personal", "alice@dav"),
            cal(2, "Work", "alice@dav"),
            cal(3, "Shared", "team@dav"),
        )

        val groups = groupCalendarsForFilter(calendars, hidden = emptySet())

        assertThat(groups.map { it.account }).containsExactly("alice@dav", "team@dav").inOrder()
        assertThat(groups[0].calendars.map { it.displayName })
            .containsExactly("Personal", "Work").inOrder()
        assertThat(groups[1].calendars.map { it.id }).containsExactly(3L)
    }

    @Test
    fun `hidden ids mark calendars not visible`() {
        val calendars = listOf(
            cal(1, "Personal", "alice@dav"),
            cal(2, "Work", "alice@dav"),
        )

        val groups = groupCalendarsForFilter(calendars, hidden = setOf(2L))
        val rows = groups.single().calendars.associateBy { it.id }

        assertThat(rows.getValue(1L).visible).isTrue()
        assertThat(rows.getValue(2L).visible).isFalse()
    }

    @Test
    fun `blank account name falls back to type`() {
        val groups = groupCalendarsForFilter(
            listOf(cal(1, "Birthdays", account = "", type = "LOCAL")),
            hidden = emptySet(),
        )
        assertThat(groups.single().account).isEqualTo("LOCAL")
    }

    /** #77: same address, two apps — two accounts, and they must read as two. */
    @Test
    fun `one name under two account types stays two groups`() {
        val calendars = listOf(
            cal(1, "Personal", "me@example.com", type = "com.google"),
            cal(2, "Shared", "me@example.com", type = "bitfire.at.davdroid"),
        )

        val groups = groupCalendarsForFilter(calendars, hidden = emptySet())

        assertThat(groups).hasSize(2)
        assertThat(groups.map { it.accountType })
            .containsExactly("com.google", "bitfire.at.davdroid").inOrder()
        assertThat(groups.map { it.calendars.single().id }).containsExactly(1L, 2L).inOrder()
        assertThat(groups.all { it.ambiguous }).isTrue()
    }

    @Test
    fun `one name under one type is not ambiguous`() {
        val groups = groupCalendarsForFilter(
            listOf(cal(1, "Personal", "me@example.com")),
            hidden = emptySet(),
        )

        assertThat(groups.single().ambiguous).isFalse()
    }

    /** Two different names from the same app are still two plain groups. */
    @Test
    fun `different names under one type are not ambiguous`() {
        val groups = groupCalendarsForFilter(
            listOf(
                cal(1, "Personal", "alice@example.com"),
                cal(2, "Shared", "bob@example.com"),
            ),
            hidden = emptySet(),
        )

        assertThat(groups).hasSize(2)
        assertThat(groups.none { it.ambiguous }).isTrue()
    }
}
