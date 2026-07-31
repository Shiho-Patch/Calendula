package de.jeanlucmakiola.calendula.domain

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class EventColorPaletteTest {

    @Test
    fun `empty palette stays empty`() {
        assertThat(emptyList<EventColorOption>().curatedForPicker()).isEmpty()
    }

    @Test
    fun `exact duplicate values collapse to the alphabetically first key`() {
        val curated = listOf(
            EventColorOption("cyan", 0xFF00FFFF.toInt()),
            EventColorOption("aqua", 0xFF00FFFF.toInt()),
            EventColorOption("red", 0xFFFF0000.toInt()),
        ).curatedForPicker()

        assertThat(curated.map { it.key }).containsExactly("aqua", "red")
    }

    @Test
    fun `small palettes pass through whole, so Google's curated set is untouched`() {
        // A Google-like palette: two dozen distinct hand-picked colours.
        val palette = (0 until 24).map {
            val hue = it * 15
            EventColorOption("$it", hsvArgb(hue.toFloat()))
        }

        val curated = palette.curatedForPicker()

        assertThat(curated).containsExactlyElementsIn(palette)
    }

    @Test
    fun `oversized CSS3 palette thins to a pickable number of distinct swatches`() {
        val curated = css3Palette().curatedForPicker()

        // The whole point of #22: ~147 published colours become a single
        // manageable grid instead of a full screen.
        assertThat(curated.size).isAtLeast(30)
        assertThat(curated.size).isAtMost(60)
    }

    @Test
    fun `curation never invents colours or drops keys`() {
        val source = css3Palette()
        val curated = source.curatedForPicker()

        assertThat(source).containsAtLeastElementsIn(curated)
        assertThat(curated.map { it.argb }).containsNoDuplicates()
    }

    @Test
    fun `spelling-alias pairs never both survive`() {
        val keys = css3Palette().curatedForPicker().map { it.key }.toSet()

        val aliasPairs = listOf(
            "aqua" to "cyan",
            "fuchsia" to "magenta",
            "gray" to "grey",
            "darkgray" to "darkgrey",
            "dimgray" to "dimgrey",
            "lightgray" to "lightgrey",
            "slategray" to "slategrey",
            "lightslategray" to "lightslategrey",
            "darkslategray" to "darkslategrey",
        )
        aliasPairs.forEach { (a, b) ->
            assertThat(keys.contains(a) && keys.contains(b)).isFalse()
        }
    }

    @Test
    fun `neutrals collapse instead of forming a run of look-alikes`() {
        // Neutrals lose their hue entirely when painted, so they collapse onto
        // the one or two greys the poles offer — a dark and a pale — instead of
        // the old stranded run of look-alike "pinks" at the end of the grid (#22).
        val curated = listOf(
            EventColorOption("black", 0xFF000000.toInt()),
            EventColorOption("gray", 0xFF808080.toInt()),
            EventColorOption("darkgray", 0xFFA9A9A9.toInt()),
            EventColorOption("blue", 0xFF0000FF.toInt()),
            EventColorOption("red", 0xFFFF0000.toInt()),
        ).curatedForPicker().map { it.key }

        assertThat(curated).doesNotContain("gray") // paints as black's grey
        assertThat(curated).containsAtLeast("black", "red", "blue")
        // darkgray is light enough to keep a pale character, so the three
        // neutrals still fold down to at most two swatches, never a run.
        assertThat(curated.count { it in listOf("black", "gray", "darkgray") }).isAtMost(2)
    }

    @Test
    fun `a dark and a light shade of one hue collapse to a single swatch`() {
        // The picker paints every swatch at one fixed lightness, so navy and a
        // mid blue are indistinguishable once painted — keep just one.
        val curated = listOf(
            EventColorOption("navy", 0xFF000080.toInt()),
            EventColorOption("blue", 0xFF0000FF.toInt()),
        ).curatedForPicker()

        assertThat(curated).hasSize(1)
    }

    @Test
    fun `the wheel is cut once, keeping each hue family contiguous`() {
        // Twelve pure hues, deliberately shuffled; a small palette passes the
        // thinning stage untouched so only the ordering is under test.
        val shuffledHues = listOf(0, 300, 60, 180, 120, 240, 30, 330, 90, 210, 150, 270)
        val curated = shuffledHues
            .map { EventColorOption("$it", hsvArgb(it.toFloat())) }
            .curatedForPicker()
            .map { it.key.toInt() }

        // A proper single-seam sweep around the wheel descends exactly once
        // (at the seam). The old bucketed sort could scatter a family across
        // both ends, producing extra descents.
        val descents = curated.indices.count { i ->
            curated[(i + 1) % curated.size] < curated[i]
        }
        assertThat(descents).isEqualTo(1)
    }

    @Test
    fun `CSS3 survivors span the whole rainbow`() {
        val keys = css3Palette().curatedForPicker().map { it.key }
        fun has(vararg families: String) = keys.any { k -> families.any { k.contains(it) } }

        // Which exact name represents a hue family depends on the vivid-first
        // thinning, so assert each family survives, not a specific key.
        assertThat(has("red", "crimson", "firebrick", "tomato", "maroon", "brown")).isTrue()
        assertThat(has("orange", "gold", "goldenrod", "peru", "sienna", "salmon")).isTrue()
        assertThat(has("green", "olive", "lime", "chartreuse", "forest", "sea")).isTrue()
        assertThat(has("blue", "navy", "dodger", "steel", "royal", "sky", "aqua")).isTrue()
        assertThat(has("violet", "purple", "magenta", "orchid", "fuchsia", "indigo", "plum")).isTrue()
    }

    private fun hsvArgb(hue: Float): Int {
        val h = hue / 60f
        val sector = h.toInt() % 6
        val f = h - h.toInt()
        val q = ((1 - f) * 255).toInt()
        val t = (f * 255).toInt()
        return when (sector) {
            0 -> argb(255, t, 0)
            1 -> argb(q, 255, 0)
            2 -> argb(0, 255, t)
            3 -> argb(0, q, 255)
            4 -> argb(t, 0, 255)
            else -> argb(255, 0, q)
        }
    }

    private fun argb(r: Int, g: Int, b: Int): Int =
        (0xFF shl 24) or (r shl 16) or (g shl 8) or b

    /** The exact set ical4android/DAVx5 publishes: CSS3's 147 named colours. */
    private fun css3Palette(): List<EventColorOption> = CSS3.map { (name, rgb) ->
        EventColorOption(name, 0xFF000000.toInt() or rgb)
    }

    private val CSS3 = mapOf(
        "aliceblue" to 0xF0F8FF, "antiquewhite" to 0xFAEBD7, "aqua" to 0x00FFFF,
        "aquamarine" to 0x7FFFD4, "azure" to 0xF0FFFF, "beige" to 0xF5F5DC,
        "bisque" to 0xFFE4C4, "black" to 0x000000, "blanchedalmond" to 0xFFEBCD,
        "blue" to 0x0000FF, "blueviolet" to 0x8A2BE2, "brown" to 0xA52A2A,
        "burlywood" to 0xDEB887, "cadetblue" to 0x5F9EA0, "chartreuse" to 0x7FFF00,
        "chocolate" to 0xD2691E, "coral" to 0xFF7F50, "cornflowerblue" to 0x6495ED,
        "cornsilk" to 0xFFF8DC, "crimson" to 0xDC143C, "cyan" to 0x00FFFF,
        "darkblue" to 0x00008B, "darkcyan" to 0x008B8B, "darkgoldenrod" to 0xB8860B,
        "darkgray" to 0xA9A9A9, "darkgreen" to 0x006400, "darkgrey" to 0xA9A9A9,
        "darkkhaki" to 0xBDB76B, "darkmagenta" to 0x8B008B, "darkolivegreen" to 0x556B2F,
        "darkorange" to 0xFF8C00, "darkorchid" to 0x9932CC, "darkred" to 0x8B0000,
        "darksalmon" to 0xE9967A, "darkseagreen" to 0x8FBC8F, "darkslateblue" to 0x483D8B,
        "darkslategray" to 0x2F4F4F, "darkslategrey" to 0x2F4F4F, "darkturquoise" to 0x00CED1,
        "darkviolet" to 0x9400D3, "deeppink" to 0xFF1493, "deepskyblue" to 0x00BFFF,
        "dimgray" to 0x696969, "dimgrey" to 0x696969, "dodgerblue" to 0x1E90FF,
        "firebrick" to 0xB22222, "floralwhite" to 0xFFFAF0, "forestgreen" to 0x228B22,
        "fuchsia" to 0xFF00FF, "gainsboro" to 0xDCDCDC, "ghostwhite" to 0xF8F8FF,
        "gold" to 0xFFD700, "goldenrod" to 0xDAA520, "gray" to 0x808080,
        "green" to 0x008000, "greenyellow" to 0xADFF2F, "grey" to 0x808080,
        "honeydew" to 0xF0FFF0, "hotpink" to 0xFF69B4, "indianred" to 0xCD5C5C,
        "indigo" to 0x4B0082, "ivory" to 0xFFFFF0, "khaki" to 0xF0E68C,
        "lavender" to 0xE6E6FA, "lavenderblush" to 0xFFF0F5, "lawngreen" to 0x7CFC00,
        "lemonchiffon" to 0xFFFACD, "lightblue" to 0xADD8E6, "lightcoral" to 0xF08080,
        "lightcyan" to 0xE0FFFF, "lightgoldenrodyellow" to 0xFAFAD2, "lightgray" to 0xD3D3D3,
        "lightgreen" to 0x90EE90, "lightgrey" to 0xD3D3D3, "lightpink" to 0xFFB6C1,
        "lightsalmon" to 0xFFA07A, "lightseagreen" to 0x20B2AA, "lightskyblue" to 0x87CEFA,
        "lightslategray" to 0x778899, "lightslategrey" to 0x778899, "lightsteelblue" to 0xB0C4DE,
        "lightyellow" to 0xFFFFE0, "lime" to 0x00FF00, "limegreen" to 0x32CD32,
        "linen" to 0xFAF0E6, "magenta" to 0xFF00FF, "maroon" to 0x800000,
        "mediumaquamarine" to 0x66CDAA, "mediumblue" to 0x0000CD, "mediumorchid" to 0xBA55D3,
        "mediumpurple" to 0x9370DB, "mediumseagreen" to 0x3CB371, "mediumslateblue" to 0x7B68EE,
        "mediumspringgreen" to 0x00FA9A, "mediumturquoise" to 0x48D1CC,
        "mediumvioletred" to 0xC71585, "midnightblue" to 0x191970, "mintcream" to 0xF5FFFA,
        "mistyrose" to 0xFFE4E1, "moccasin" to 0xFFE4B5, "navajowhite" to 0xFFDEAD,
        "navy" to 0x000080, "oldlace" to 0xFDF5E6, "olive" to 0x808000,
        "olivedrab" to 0x6B8E23, "orange" to 0xFFA500, "orangered" to 0xFF4500,
        "orchid" to 0xDA70D6, "palegoldenrod" to 0xEEE8AA, "palegreen" to 0x98FB98,
        "paleturquoise" to 0xAFEEEE, "palevioletred" to 0xDB7093, "papayawhip" to 0xFFEFD5,
        "peachpuff" to 0xFFDAB9, "peru" to 0xCD853F, "pink" to 0xFFC0CB,
        "plum" to 0xDDA0DD, "powderblue" to 0xB0E0E6, "purple" to 0x800080,
        "red" to 0xFF0000, "rosybrown" to 0xBC8F8F, "royalblue" to 0x4169E1,
        "saddlebrown" to 0x8B4513, "salmon" to 0xFA8072, "sandybrown" to 0xF4A460,
        "seagreen" to 0x2E8B57, "seashell" to 0xFFF5EE, "sienna" to 0xA0522D,
        "silver" to 0xC0C0C0, "skyblue" to 0x87CEEB, "slateblue" to 0x6A5ACD,
        "slategray" to 0x708090, "slategrey" to 0x708090, "snow" to 0xFFFAFA,
        "springgreen" to 0x00FF7F, "steelblue" to 0x4682B4, "tan" to 0xD2B48C,
        "teal" to 0x008080, "thistle" to 0xD8BFD8, "tomato" to 0xFF6347,
        "turquoise" to 0x40E0D0, "violet" to 0xEE82EE, "wheat" to 0xF5DEB3,
        "white" to 0xFFFFFF, "whitesmoke" to 0xF5F5F5, "yellow" to 0xFFFF00,
        "yellowgreen" to 0x9ACD32,
    )
}
