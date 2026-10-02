package de.jeanlucmakiola.calendula.ui.detail

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class WebLinksTest {

    @Test
    fun `a plain address has no links`() {
        assertThat(findWebLinks("Office B, 12 Main St.")).isEmpty()
    }

    @Test
    fun `a bare meeting link spans the whole text`() {
        val text = "https://meet.google.com/abc-defg-hij"
        assertThat(findWebLinks(text)).containsExactly(WebLink(0, text.length, text))
    }

    @Test
    fun `a capitalised scheme is matched and lower-cased`() {
        val links = findWebLinks("Https://zoom.us/j/123")
        assertThat(links.single().url).isEqualTo("https://zoom.us/j/123")
        assertThat(links.single().end).isEqualTo(21)
    }

    @Test
    fun `angle brackets around a link stay outside it`() {
        val text = "Microsoft Teams Meeting <https://teams.microsoft.com/l/meetup-join/x>"
        val link = findWebLinks(text).single()
        assertThat(link.url).isEqualTo("https://teams.microsoft.com/l/meetup-join/x")
        assertThat(text.substring(link.start, link.end)).isEqualTo(link.url)
    }

    @Test
    fun `trailing punctuation is left out of the link`() {
        val link = findWebLinks("Office B (https://office.example.com).").single()
        assertThat(link.url).isEqualTo("https://office.example.com")
    }

    @Test
    fun `every link in the text is found`() {
        val links = findWebLinks("Room 3 / https://zoom.us/j/1 / https://meet.google.com/x")
        assertThat(links.map { it.url })
            .containsExactly("https://zoom.us/j/1", "https://meet.google.com/x")
            .inOrder()
    }

    @Test
    fun `other schemes and a bare scheme are not links`() {
        assertThat(findWebLinks("ftp://example.com mailto:a@b.c https://")).isEmpty()
    }
}
