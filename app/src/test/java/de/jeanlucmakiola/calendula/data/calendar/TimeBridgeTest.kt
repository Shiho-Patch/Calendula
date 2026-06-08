package de.jeanlucmakiola.calendula.data.calendar

import com.google.common.truth.Truth.assertThat
import kotlin.time.Instant
import org.junit.jupiter.api.Test

class TimeBridgeTest {

    @Test
    fun `epoch millis round-trips through Instant`() {
        val original = 1_717_840_800_000L // 2024-06-08T10:00:00Z
        val instant = original.toKotlinInstantFromEpochMillis()
        assertThat(instant.toEpochMillis()).isEqualTo(original)
    }

    @Test
    fun `zero millis maps to Instant epoch`() {
        assertThat(0L.toKotlinInstantFromEpochMillis()).isEqualTo(Instant.fromEpochMilliseconds(0L))
    }

    @Test
    fun `negative epoch millis is supported`() {
        val original = -1_000_000L
        assertThat(original.toKotlinInstantFromEpochMillis().toEpochMillis()).isEqualTo(original)
    }
}
