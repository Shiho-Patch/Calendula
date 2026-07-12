package de.jeanlucmakiola.calendula.domain

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cbrt
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Curates an account's published event palette for the colour picker.
 *
 * Sync adapters differ wildly in what they publish: Google exposes a
 * hand-picked two-dozen set, while CalDAV adapters (DAVx5) dump all ~147 CSS3
 * named colours — including exact-value aliases (aqua/cyan, the gray/grey
 * spelling pairs) and dozens of visually indistinguishable whites and grays
 * (#22).
 *
 * Crucially, curation runs against the colour the picker actually *paints*, not
 * the raw provider value. The picker softens every swatch through [pastelArgb]:
 * it pins lightness to a constant and caps saturation, so the raw palette's
 * lightness axis is invisible on screen. Two raw colours that look different —
 * a navy and a mid blue — paint as one swatch, and every neutral (black, the
 * grays, white) paints as the same pale tint. Judging distinctness in raw
 * space, as before, left near-identical painted swatches and stranded the
 * neutrals as a run of look-alike "pinks" at the end of the grid.
 *
 * Three steps, all in painted space:
 *  1. Collapse swatches that paint identically to one (alphabetically-first key
 *     wins, deterministically) — this folds aliases, dark/light shades of a
 *     hue, and all the neutrals together.
 *  2. Oversized palettes (> [CURATION_TRIGGER_SIZE]) drop the washed-out
 *     neutral-origin tints (painted chroma < [PASTEL_CHROMA_FLOOR]) and are
 *     then thinned to visually distinct colours: most vivid first, a colour is
 *     kept only when at least [MIN_DELTA_E] (CIE76, painted Lab) from every
 *     colour already kept. Small palettes are already curated by their adapter
 *     and pass through whole.
 *  3. The survivors are ordered like a rainbow — continuously by painted hue —
 *     with the wheel cut at its single widest empty gap so the one unavoidable
 *     seam lands in dead space and no hue family is torn across both ends.
 *
 * Every surviving option keeps its provider [EventColorOption.key], so a pick
 * still round-trips through sync.
 */
fun List<EventColorOption>.curatedForPicker(): List<EventColorOption> {
    val painted = sortedBy { it.key }
        .distinctBy { pastelArgb(it.argb) }
        .map { it to Lab.of(pastelArgb(it.argb)) }
    val kept = if (painted.size <= CURATION_TRIGGER_SIZE) {
        painted
    } else {
        thin(painted.filter { (_, lab) -> lab.chroma >= PASTEL_CHROMA_FLOOR })
    }
    return orderAroundWheel(kept).map { (option, _) -> option }
}

/**
 * Orders swatches continuously around the (painted) hue wheel, then cuts the
 * circle at its widest angular gap so the single seam lands in empty space
 * instead of mid-family. Saturation breaks ties, vivid first.
 */
private fun orderAroundWheel(
    swatches: List<Pair<EventColorOption, Lab>>,
): List<Pair<EventColorOption, Lab>> {
    if (swatches.size < 2) return swatches
    val byHue = swatches.sortedWith(
        compareBy({ (_, lab) -> lab.hue }, { (_, lab) -> -lab.chroma }),
    )
    // Split the wheel after the largest empty arc between neighbouring hues;
    // the default is the wrap gap (last hue back round to the first), i.e. the
    // familiar 0→360 order, and we only rotate away from it for a wider void.
    var cutAfter = byHue.lastIndex
    var widestGap = 360.0 - byHue.last().second.hue + byHue.first().second.hue
    for (i in 0 until byHue.lastIndex) {
        val gap = byHue[i + 1].second.hue - byHue[i].second.hue
        if (gap > widestGap) {
            widestGap = gap
            cutAfter = i
        }
    }
    return byHue.subList(cutAfter + 1, byHue.size) + byHue.subList(0, cutAfter + 1)
}

/** Greedy max-distance filter: vivid colours stake out clusters first. */
private fun thin(
    swatches: List<Pair<EventColorOption, Lab>>,
): List<Pair<EventColorOption, Lab>> {
    val byVividness = swatches
        .sortedWith(compareByDescending<Pair<EventColorOption, Lab>> { it.second.chroma }.thenBy { it.first.key })
    val kept = mutableListOf<Pair<EventColorOption, Lab>>()
    for (candidate in byVividness) {
        if (kept.none { it.second.deltaE(candidate.second) < MIN_DELTA_E }) kept += candidate
    }
    return kept
}

/**
 * The softening the colour picker paints over every swatch: keep the hue, scale
 * and clamp saturation into a gentle band, and pin value to a constant so
 * nothing screams and everything reads on the surface. Value is fixed here so
 * curation is theme-independent — only hue and saturation distinguish painted
 * swatches.
 *
 * This is a self-contained mirror of floret-kit's `pastelize` hue/saturation
 * shaping (`de.jeanlucmakiola.floret.components.pastelize`), with value pinned
 * rather than theme-picked. Curation must reason about the colour the picker
 * paints, so the two shapings have to agree: if floret's saturation band or
 * curve changes, update this in step.
 */
fun pastelArgb(rawArgb: Int): Int {
    val r = ((rawArgb shr 16) and 0xFF) / 255f
    val g = ((rawArgb shr 8) and 0xFF) / 255f
    val b = (rawArgb and 0xFF) / 255f
    val max = maxOf(r, g, b)
    val min = minOf(r, g, b)
    val delta = max - min
    val hue = when {
        delta == 0f -> 0f
        max == r -> 60f * (((g - b) / delta) % 6f)
        max == g -> 60f * (((b - r) / delta) + 2f)
        else -> 60f * (((r - g) / delta) + 4f)
    }.let { if (it < 0f) it + 360f else it }
    val sat = (if (max == 0f) 0f else delta / max) * 0.6f
    val s = sat.coerceIn(0.25f, 0.65f)
    val v = PASTEL_VALUE
    val c = v * s
    val x = c * (1f - abs((hue / 60f) % 2f - 1f))
    val m = v - c
    val (rr, gg, bb) = when {
        hue < 60f -> Triple(c, x, 0f)
        hue < 120f -> Triple(x, c, 0f)
        hue < 180f -> Triple(0f, c, x)
        hue < 240f -> Triple(0f, x, c)
        hue < 300f -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }
    fun channel(value: Float) = ((value + m) * 255f).roundToInt().coerceIn(0, 255)
    return (0xFF shl 24) or (channel(rr) shl 16) or (channel(gg) shl 8) or channel(bb)
}

/** Reference lightness for curation; the picker paints at this on dark surfaces. */
private const val PASTEL_VALUE = 0.82f

/** Palettes at most this big skip the thinning (Google's ~26 pass through). */
private const val CURATION_TRIGGER_SIZE = 36

/** Minimum CIE76 ΔE between surviving painted swatches. */
private const val MIN_DELTA_E = 13.0

/**
 * Painted-chroma floor for oversized palettes: below this a swatch is a washed-
 * out tint — the neutrals and near-whites the saturation clamp muddies — so it
 * is dropped rather than shown as pale filler.
 */
private const val PASTEL_CHROMA_FLOOR = 22.0

/** CIE Lab (D65) — the space where Euclidean distance ≈ perceived difference. */
private class Lab(val l: Double, val a: Double, val b: Double) {
    val chroma: Double get() = hypot(a, b)

    /** Hue angle in degrees, 0–360, around the Lab a-b plane. */
    val hue: Double get() = (Math.toDegrees(atan2(b, a)) + 360.0) % 360.0

    fun deltaE(other: Lab): Double =
        sqrt((l - other.l).pow(2) + (a - other.a).pow(2) + (b - other.b).pow(2))

    companion object {
        fun of(argb: Int): Lab {
            fun linear(shift: Int): Double {
                val c = ((argb shr shift) and 0xFF) / 255.0
                return if (c <= 0.04045) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
            }
            val r = linear(16)
            val g = linear(8)
            val b = linear(0)
            val x = (0.4124 * r + 0.3576 * g + 0.1805 * b) / 0.95047
            val y = 0.2126 * r + 0.7152 * g + 0.0722 * b
            val z = (0.0193 * r + 0.1192 * g + 0.9505 * b) / 1.08883
            fun f(t: Double) = if (t > 0.008856) cbrt(t) else 7.787 * t + 16.0 / 116.0
            val fy = f(y)
            return Lab(116 * fy - 16, 500 * (f(x) - fy), 200 * (fy - f(z)))
        }
    }
}
