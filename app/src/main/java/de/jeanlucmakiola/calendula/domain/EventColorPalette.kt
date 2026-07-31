package de.jeanlucmakiola.calendula.domain

import de.jeanlucmakiola.calendula.domain.color.Oklch
import de.jeanlucmakiola.calendula.domain.color.eventTone
import de.jeanlucmakiola.calendula.domain.color.oklchOf

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
 * the raw provider value — and it gets that colour from the same [eventTone]
 * the picker calls, rather than from a copy of its shaping kept in step by hand.
 * Because a harmonised container pins lightness, the raw palette's lightness
 * axis is invisible on screen: two raw colours that look different — a navy and
 * a mid blue — paint as one swatch, and every neutral (black, the grays, white)
 * paints as the same grey. Judging distinctness in raw space, as before, left
 * near-identical painted swatches and stranded the neutrals as a run of
 * look-alike tints at the end of the grid.
 *
 * Three steps, all in painted space:
 *  1. Collapse swatches that paint identically to one (alphabetically-first key
 *     wins, deterministically) — this folds aliases, dark/light shades of a
 *     hue, and all the neutrals together.
 *  2. Oversized palettes (> [CURATION_TRIGGER_SIZE]) drop the neutral-origin
 *     swatches — with lightness pinned, a grey source paints as plain grey, so
 *     "has no hue left" is simply zero chroma — and are then thinned to visually
 *     distinct colours: most vivid first, a colour is kept only when at least
 *     [MIN_DISTANCE] away in Oklab from every colour already kept. Small
 *     palettes are already curated by their adapter and pass through whole.
 *  3. The survivors are ordered like a rainbow — continuously by painted hue —
 *     with the wheel cut at its single widest empty gap so the one unavoidable
 *     seam lands in dead space and no hue family is torn across both ends.
 *
 * Every surviving option keeps its provider [EventColorOption.key], so a pick
 * still round-trips through sync.
 */
fun List<EventColorOption>.curatedForPicker(): List<EventColorOption> {
    val painted = sortedBy { it.key }
        .distinctBy { paintedArgb(it.argb) }
        .map { it to oklchOf(paintedArgb(it.argb)) }
    val kept = if (painted.size <= CURATION_TRIGGER_SIZE) {
        painted
    } else {
        thin(painted.filter { (_, painted) -> painted.chroma > 0f })
    }
    return orderAroundWheel(kept).map { (option, _) -> option }
}

/**
 * Orders swatches continuously around the (painted) hue wheel, then cuts the
 * circle at its widest angular gap so the single seam lands in empty space
 * instead of mid-family. Saturation breaks ties, vivid first.
 */
private fun orderAroundWheel(
    swatches: List<Pair<EventColorOption, Oklch>>,
): List<Pair<EventColorOption, Oklch>> {
    if (swatches.size < 2) return swatches
    val byHue = swatches.sortedWith(
        compareBy({ (_, painted) -> painted.hue }, { (_, painted) -> -painted.chroma }),
    )
    // Split the wheel after the largest empty arc between neighbouring hues;
    // the default is the wrap gap (last hue back round to the first), i.e. the
    // familiar 0→360 order, and we only rotate away from it for a wider void.
    var cutAfter = byHue.lastIndex
    var widestGap = 360f - byHue.last().second.hue + byHue.first().second.hue
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
    swatches: List<Pair<EventColorOption, Oklch>>,
): List<Pair<EventColorOption, Oklch>> {
    val byVividness = swatches
        .sortedWith(
            compareByDescending<Pair<EventColorOption, Oklch>> { it.second.chroma }
                .thenBy { it.first.key },
        )
    val kept = mutableListOf<Pair<EventColorOption, Oklch>>()
    for (candidate in byVividness) {
        if (kept.none { it.second.distanceTo(candidate.second) < MIN_DISTANCE }) kept += candidate
    }
    return kept
}

/** The colour the picker paints for [argb]; the light theme stands in as the
 *  reference, since a harmonised container differs only in lightness by theme
 *  and curation compares hue and chroma. */
private fun paintedArgb(argb: Int): Int =
    eventTone(argb, dark = false, harmonise = true).container

/** Palettes at most this big skip the thinning (Google's ~26 pass through). */
private const val CURATION_TRIGGER_SIZE = 36

/**
 * Minimum Oklab distance between surviving painted swatches. Painted colours all
 * share one lightness, so this is really a hue/chroma separation — far enough
 * apart that two swatches never read as the same colour in the grid.
 */
private const val MIN_DISTANCE = 0.025f
