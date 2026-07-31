package de.jeanlucmakiola.calendula.domain.color

import kotlin.math.atan2
import kotlin.math.cbrt
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * A colour in Oklch — Oklab's cylindrical form: perceptual [lightness], [chroma]
 * (colourfulness) and [hue] in degrees.
 *
 * The point of using it over HSV is that its lightness axis matches what the eye
 * calls brightness. HSV's "value" does not: at a single pinned value the hues
 * spread across relative luminance 0.10 (indigo) to 0.45 (yellow), which is why
 * pinning value produced fills that needed different ink per hue. Pin Oklch
 * lightness instead and every hue lands at the same apparent brightness, so one
 * ink serves all of them.
 *
 * [lightness] runs 0 (black) to 1 (white); [chroma] is 0 (grey) to about 0.32 at
 * the sRGB limit.
 */
data class Oklch(val lightness: Float, val chroma: Float, val hue: Float) {

    /**
     * Perceptual distance to [other] — plain Euclidean in Oklab, which is what
     * Oklab is built for (unlike CIE Lab, where CIE76 is known to misjudge
     * saturated blues).
     */
    fun distanceTo(other: Oklch): Float {
        val (a1, b1) = chroma * cosDeg(hue) to chroma * sinDeg(hue)
        val (a2, b2) = other.chroma * cosDeg(other.hue) to other.chroma * sinDeg(other.hue)
        val dl = (lightness - other.lightness).toDouble()
        return sqrt(dl * dl + (a1 - a2).pow(2) + (b1 - b2).pow(2)).toFloat()
    }
}

/** Read [argb]'s opaque colour as Oklch. */
fun oklchOf(argb: Int): Oklch {
    val r = toLinear(((argb shr 16) and 0xFF) / 255.0)
    val g = toLinear(((argb shr 8) and 0xFF) / 255.0)
    val b = toLinear((argb and 0xFF) / 255.0)

    val l = cbrt(0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b)
    val m = cbrt(0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b)
    val s = cbrt(0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b)

    val lightness = 0.2104542553 * l + 0.7936177850 * m - 0.0040720468 * s
    val aAxis = 1.9779984951 * l - 2.4285922050 * m + 0.4505937099 * s
    val bAxis = 0.0259040371 * l + 0.7827717662 * m - 0.8086757660 * s

    val hue = (Math.toDegrees(atan2(bAxis, aAxis)) + 360.0) % 360.0
    return Oklch(lightness.toFloat(), hypot(aAxis, bAxis).toFloat(), hue.toFloat())
}

/**
 * The opaque sRGB colour for this Oklch, gamut-mapped: most of the Oklch cylinder
 * falls outside sRGB, so a colour that does not fit keeps its lightness and hue
 * and gives up chroma until it does. Holding lightness is what matters here —
 * it's the axis the contrast guarantees rest on.
 */
fun Oklch.toArgb(): Int {
    val fitted = if (inSrgb(lightness, chroma, hue)) {
        chroma
    } else {
        var low = 0f
        var high = chroma
        repeat(GAMUT_STEPS) {
            val mid = (low + high) / 2f
            if (inSrgb(lightness, mid, hue)) low = mid else high = mid
        }
        low
    }
    val (r, g, b) = linearSrgbOf(lightness, fitted, hue)
    return 0xFF shl 24 or
        (channel(r) shl 16) or
        (channel(g) shl 8) or
        channel(b)
}

private fun linearSrgbOf(lightness: Float, chroma: Float, hue: Float): Triple<Double, Double, Double> {
    val a = chroma * cosDeg(hue)
    val b = chroma * sinDeg(hue)
    val l = (lightness + 0.3963377774 * a + 0.2158037573 * b).pow(3)
    val m = (lightness - 0.1055613458 * a - 0.0638541728 * b).pow(3)
    val s = (lightness - 0.0894841775 * a - 1.2914855480 * b).pow(3)
    return Triple(
        4.0767416621 * l - 3.3077115913 * m + 0.2309699292 * s,
        -1.2684380046 * l + 2.6097574011 * m - 0.3413193965 * s,
        -0.0041960863 * l - 0.7034186147 * m + 1.7076147010 * s,
    )
}

private fun inSrgb(lightness: Float, chroma: Float, hue: Float): Boolean {
    val (r, g, b) = linearSrgbOf(lightness, chroma, hue)
    return r in -GAMUT_EPSILON..(1.0 + GAMUT_EPSILON) &&
        g in -GAMUT_EPSILON..(1.0 + GAMUT_EPSILON) &&
        b in -GAMUT_EPSILON..(1.0 + GAMUT_EPSILON)
}

private fun channel(linear: Double): Int =
    (toSrgb(linear.coerceIn(0.0, 1.0)) * 255.0).toInt().coerceIn(0, 255)

private fun toLinear(c: Double): Double =
    if (c <= 0.04045) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)

private fun toSrgb(c: Double): Double =
    if (c <= 0.0031308) 12.92 * c else 1.055 * c.pow(1.0 / 2.4) - 0.055

private fun cosDeg(deg: Float) = cos(Math.toRadians(deg.toDouble()))
private fun sinDeg(deg: Float) = sin(Math.toRadians(deg.toDouble()))

/** Bisection steps when pulling an out-of-gamut colour back into sRGB. */
private const val GAMUT_STEPS = 24

/** Slack for the gamut test, so rounding at the boundary doesn't reject a fit. */
private const val GAMUT_EPSILON = 1e-4
