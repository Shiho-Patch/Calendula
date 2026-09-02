package de.jeanlucmakiola.calendula.data.calendar

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * The provider's "use the account default" sentinel is translated out here, so
 * the pure planner only ever sees offsets it can resolve (#75).
 */
class ProviderReminderMinutesTest {

    @Test
    fun `the account-default sentinel is dropped`() {
        // Taken as an offset it armed an alarm a minute *after* the event began,
        // while the detail screen said "Default reminder" for the same row.
        assertThat(mapOf(1L to listOf(-1)).withoutProviderDefaults())
            .containsExactly(1L, emptyList<Int>())
    }

    @Test
    fun `a real reminder beside a default one survives`() {
        assertThat(mapOf(1L to listOf(-1, 10)).withoutProviderDefaults())
            .containsExactly(1L, listOf(10))
    }

    @Test
    fun `an all-day encoding that is negative is not mistaken for the sentinel`() {
        // An all-day offset carries the firing time of day, so it is routinely
        // negative; a `< 0` test would silence reminders that do fire.
        assertThat(mapOf(1L to listOf(-420, -2_000)).withoutProviderDefaults())
            .containsExactly(1L, listOf(-420, -2_000))
    }
}
