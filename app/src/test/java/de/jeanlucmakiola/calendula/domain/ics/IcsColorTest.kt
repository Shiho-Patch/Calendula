package de.jeanlucmakiola.calendula.domain.ics

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class IcsColorTest {

    @Test
    fun `a raw Android colour int round-trips`() {
        assertThat(parseIcsColorValue("-2818048")).isEqualTo(-2818048)
    }

    @Test
    fun `a CSS3 name resolves opaque`() {
        assertThat(parseIcsColorValue("tomato")).isEqualTo(0xFFFF6347.toInt())
        assertThat(parseIcsColorValue("REBECCAPURPLE")).isEqualTo(0xFF663399.toInt())
    }

    @Test
    fun `hex forms are accepted with and without alpha`() {
        assertThat(parseIcsColorValue("#ff6347")).isEqualTo(0xFFFF6347.toInt())
        assertThat(parseIcsColorValue("#80ff6347")).isEqualTo(0x80FF6347.toInt())
    }

    @Test
    fun `a colour with no alpha byte is made opaque`() {
        // 0x00FF6347 as a decimal int — an RGB triple that lost its alpha.
        assertThat(parseIcsColorValue("16737095")).isEqualTo(0xFFFF6347.toInt())
    }

    @Test
    fun `unusable values resolve to null`() {
        assertThat(parseIcsColorValue(null)).isNull()
        assertThat(parseIcsColorValue("")).isNull()
        // Fossify interpolates a nullable calendar straight into the property.
        assertThat(parseIcsColorValue("null")).isNull()
        assertThat(parseIcsColorValue("chartreusey")).isNull()
        assertThat(parseIcsColorValue("#abc")).isNull()
    }
}
