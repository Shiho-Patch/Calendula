package de.jeanlucmakiola.calendula.domain.color

/**
 * The colours one calendar's identity resolves to in one theme.
 *
 * The app paints a calendar's colour in two structurally different places, and
 * they want opposite things:
 *
 *  - a **[container]** sits behind text (week and day blocks, month bars, widget
 *    rows, picker swatches), so it has to contrast with its own ink;
 *  - an **[accent]** is a mark on an ordinary app surface (day dots, agenda and
 *    search stripes, calendar icon tints, the detail header), so it has to
 *    contrast with the *surface* instead.
 *
 * Painting both from one colour is what made this hard to get right: deep enough
 * to carry white text is too dark to see as a dot on a dark surface, and light
 * enough to show up there is too pale to carry white text. Splitting them lets
 * each be pinned to the lightness its job needs, while hue and chroma — the parts
 * that actually say *which calendar this is* — stay shared, so the two roles
 * still read as the same colour.
 */
data class EventTone(
    val container: Int,
    val onContainer: Int,
    val accent: Int,
)

/**
 * Resolve [rawArgb] for the current theme.
 *
 * With [harmonise] on, hue and chroma are kept and lightness is re-pinned per
 * role, which is what makes the ink predictable: every container lands at the
 * same perceptual lightness, so one ink colour serves every hue at ≥ 6:1. With
 * it off the provider's colour is painted verbatim — the sync source's own look,
 * as DAVx5/CalDAV users expect — and the ink is then chosen per colour, since
 * nothing constrains what the provider sends.
 */
fun eventTone(rawArgb: Int, dark: Boolean, harmonise: Boolean): EventTone {
    val opaque = rawArgb or 0xFF000000.toInt()
    val raw = oklchOf(opaque)
    if (!harmonise) {
        return EventTone(
            container = opaque,
            onContainer = inkFor(raw.lightness),
            accent = opaque,
        )
    }
    // A near-grey source has no hue worth keeping, so it stays grey rather than
    // being pushed to an arbitrary one; everything else is held inside a band
    // that keeps calendars apart without going neon.
    val chroma = if (raw.chroma < GREY_CHROMA) 0f else raw.chroma.coerceIn(MIN_CHROMA, MAX_CHROMA)
    // Two poles rather than one. Forcing every hue deep is what turned the warm,
    // naturally light ones muddy — a dark orange is brown and a dark yellow is
    // olive, which is a fact about those hues, not a tuning miss. So a colour is
    // sent to whichever pole it already sits nearer: most land deep and carry
    // white ink, while genuinely light sources (yellows, creams, pale tints)
    // stay light and carry dark ink, keeping the character that made them
    // recognisable. Either way the colour is pulled clear of the middle, where
    // neither ink reads well — which was the original fault.
    val staysLight = raw.lightness >= LIGHT_POLE_THRESHOLD
    val containerLightness = when {
        staysLight && dark -> LIGHT_CONTAINER_LIGHTNESS_DARK
        staysLight -> LIGHT_CONTAINER_LIGHTNESS_LIGHT
        dark -> CONTAINER_LIGHTNESS_DARK
        else -> CONTAINER_LIGHTNESS_LIGHT
    }
    // The accent takes no pole: it is a mark on the app's surface, so it has to
    // contrast with that surface whichever way its container went.
    val accentLightness = if (dark) ACCENT_LIGHTNESS_DARK else ACCENT_LIGHTNESS_LIGHT
    return EventTone(
        container = Oklch(containerLightness, chroma, raw.hue).toArgb(),
        onContainer = inkFor(containerLightness),
        accent = Oklch(accentLightness, chroma, raw.hue).toArgb(),
    )
}

/**
 * White or black ink for a background of the given perceptual [lightness].
 *
 * Derived rather than hardcoded so it stays right for raw provider colours,
 * where lightness is whatever the sync source sent. For harmonised containers it
 * is constant by construction — that is the point of pinning the lightness.
 */
fun inkFor(lightness: Float): Int =
    if (lightness < INK_FLIP_LIGHTNESS) 0xFFFFFFFF.toInt() else 0xFF000000.toInt()

/**
 * Lightness at which white ink overtakes black. Sits above the midpoint because
 * lightness is perceptual: a colour has to be distinctly light before black wins.
 */
const val INK_FLIP_LIGHTNESS = 0.62f

/**
 * Raw lightness at or above which a colour keeps its light character instead of
 * being pushed deep. Set high enough that oranges and warm reds still go deep —
 * a burnt orange reads as orange, where a dark yellow does not read as yellow —
 * so only the genuinely pale sources take the light pole.
 */
const val LIGHT_POLE_THRESHOLD = 0.72f

/** Container lightness: deep enough that white ink clears 5.5:1 on every hue. */
const val CONTAINER_LIGHTNESS_LIGHT = 0.45f

/** Same in dark mode, a shade lighter so a block separates from the surface. */
const val CONTAINER_LIGHTNESS_DARK = 0.48f

/** Light-pole container: pale enough that dark ink clears 11:1 on every hue. */
const val LIGHT_CONTAINER_LIGHTNESS_LIGHT = 0.88f

/** Same in dark mode, held down a little so a pale block doesn't glare. */
const val LIGHT_CONTAINER_LIGHTNESS_DARK = 0.84f

/** Accent lightness: dark enough to read as a mark on a pale surface. */
const val ACCENT_LIGHTNESS_LIGHT = 0.55f

/** Same against a dark surface, where the mark has to be the light one. */
const val ACCENT_LIGHTNESS_DARK = 0.78f

/** Below this chroma a colour counts as grey and keeps no hue. */
const val GREY_CHROMA = 0.02f

/** Chroma band for harmonised colours: distinct, but never electric. */
const val MIN_CHROMA = 0.07f
const val MAX_CHROMA = 0.16f
