package de.jeanlucmakiola.calendula.ui.theme

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import de.jeanlucmakiola.calendula.R
import de.jeanlucmakiola.calendula.data.fonts.CustomFontStore
import de.jeanlucmakiola.calendula.domain.FontRole
import java.io.File

/** Stored token for "use the device default typeface" — the default for both roles. */
const val FONT_SYSTEM_TOKEN = "system"

/** Stored token for "use the font the user loaded from a file" (per role). */
const val FONT_CUSTOM_TOKEN = "custom"

/**
 * A font checked into the app that a user can pick without providing a file
 * (issue #19). [token] is the stable value persisted in preferences; [family] is
 * the resolved Compose family. Kept deliberately small and readability-focused.
 */
enum class BundledFont(
    val token: String,
    @StringRes val labelRes: Int,
    val family: FontFamily,
) {
    /** Purpose-built for low-vision legibility (Braille Institute). */
    AtkinsonHyperlegible(
        token = "atkinson_hyperlegible",
        labelRes = R.string.font_atkinson_hyperlegible,
        family = FontFamily(
            Font(R.font.atkinson_hyperlegible_regular, FontWeight.Normal),
            Font(R.font.atkinson_hyperlegible_bold, FontWeight.Bold),
        ),
    ),

    /** A serif alternative; shipped as a variable font, weights via its wght axis. */
    Lora(
        token = "lora",
        labelRes = R.string.font_lora,
        family = FontFamily(
            Font(R.font.lora, FontWeight.Normal, variationSettings = weightAxis(400)),
            Font(R.font.lora, FontWeight.Medium, variationSettings = weightAxis(500)),
            Font(R.font.lora, FontWeight.Bold, variationSettings = weightAxis(700)),
        ),
    ),

    /** A monospace option. */
    JetBrainsMono(
        token = "jetbrains_mono",
        labelRes = R.string.font_jetbrains_mono,
        family = FontFamily(
            Font(R.font.jetbrains_mono_regular, FontWeight.Normal),
            Font(R.font.jetbrains_mono_bold, FontWeight.Bold),
        ),
    );

    companion object {
        fun fromToken(token: String?): BundledFont? = entries.firstOrNull { it.token == token }
    }
}

private fun weightAxis(weight: Int) = FontVariation.Settings(FontVariation.weight(weight))

/** The two roles' current font tokens (see [FontRole]); the app-wide typeface choice. */
data class AppFontSettings(
    val brand: String = FONT_SYSTEM_TOKEN,
    val plain: String = FONT_SYSTEM_TOKEN,
)

/**
 * Resolve a stored [token] for [role] to a Compose [FontFamily], or null to mean
 * "leave the Material default" (the system typeface). A custom token whose file
 * is missing or unreadable also resolves to null, so a deleted file degrades to
 * the system font rather than crashing text layout.
 */
fun resolveFontFamily(token: String, role: FontRole, context: Context): FontFamily? = when (token) {
    FONT_SYSTEM_TOKEN -> null
    FONT_CUSTOM_TOKEN -> customFontFamily(CustomFontStore.file(context, role))
    else -> BundledFont.fromToken(token)?.family
}

private fun customFontFamily(file: File): FontFamily? =
    if (file.exists() && file.length() > 0) {
        runCatching { FontFamily(Font(file)) }.getOrNull()
    } else {
        null
    }

/**
 * Build the app typography from the chosen [brand] and [plain] families. A null
 * family leaves that role's Material default (system typeface) in place, so the
 * common "system for both" case returns [CalendulaTypography] untouched. Brand
 * drives display + headline; plain drives title + body + label (M3's two
 * typeface roles).
 */
fun calendulaTypography(brand: FontFamily?, plain: FontFamily?): Typography {
    if (brand == null && plain == null) return CalendulaTypography
    val base = CalendulaTypography
    fun TextStyle.withFamily(family: FontFamily?): TextStyle =
        if (family != null) copy(fontFamily = family) else this
    return base.copy(
        displayLarge = base.displayLarge.withFamily(brand),
        displayMedium = base.displayMedium.withFamily(brand),
        displaySmall = base.displaySmall.withFamily(brand),
        headlineLarge = base.headlineLarge.withFamily(brand),
        headlineMedium = base.headlineMedium.withFamily(brand),
        headlineSmall = base.headlineSmall.withFamily(brand),
        titleLarge = base.titleLarge.withFamily(plain),
        titleMedium = base.titleMedium.withFamily(plain),
        titleSmall = base.titleSmall.withFamily(plain),
        bodyLarge = base.bodyLarge.withFamily(plain),
        bodyMedium = base.bodyMedium.withFamily(plain),
        bodySmall = base.bodySmall.withFamily(plain),
        labelLarge = base.labelLarge.withFamily(plain),
        labelMedium = base.labelMedium.withFamily(plain),
        labelSmall = base.labelSmall.withFamily(plain),
    )
}
