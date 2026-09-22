package app.tide.core.design

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Two families, one rule each.
 *
 * Manrope carries the interface. It is smooth and slightly geometric and holds
 * up at small sizes on a dark ground, where Roboto goes flat. Roboto Flex was the
 * original pick for being the system font; it was dropped because the app is
 * dark-first and Roboto's low-contrast strokes disappear against deep water.
 *
 * Every numeral, unit and data label is IBM Plex Mono, so a column of numbers
 * never shifts as values change. A number rendered in the body font is a bug.
 *
 * **Bundled, not downloaded.** The obvious route is the Play Services
 * downloadable-fonts provider, which keeps about 500KB out of the APK. It is the
 * wrong call here: it needs Play Services present, it needs a network fetch, and
 * it fails silently to the system font when either is missing. An app whose
 * entire premise is that it works with no network should not have its typography
 * depend on one. 500KB is a fair price for determinism.
 *
 * Manrope ships as a single variable font, so all five weights come from one
 * file via [FontVariation]. Plex Mono is three static cuts.
 */
@OptIn(ExperimentalTextApi::class)
private val Manrope = FontFamily(
    Font(R.font.manrope_variable, FontWeight.Normal, variationSettings = weight(400)),
    Font(R.font.manrope_variable, FontWeight.Medium, variationSettings = weight(500)),
    Font(R.font.manrope_variable, FontWeight.SemiBold, variationSettings = weight(600)),
    Font(R.font.manrope_variable, FontWeight.Bold, variationSettings = weight(700)),
    Font(R.font.manrope_variable, FontWeight.ExtraBold, variationSettings = weight(800)),
)

private fun weight(w: Int) = FontVariation.Settings(FontVariation.weight(w))

val PlexMono = FontFamily(
    Font(R.font.plex_mono_regular, FontWeight.Normal),
    Font(R.font.plex_mono_medium, FontWeight.Medium),
    Font(R.font.plex_mono_semibold, FontWeight.SemiBold),
)

val TideTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = Manrope, fontWeight = FontWeight.ExtraBold,
        fontSize = 31.sp, lineHeight = 36.sp, letterSpacing = (-0.74).sp,
    ),
    titleLarge = TextStyle(
        fontFamily = Manrope, fontWeight = FontWeight.Bold,
        fontSize = 20.sp, lineHeight = 26.sp, letterSpacing = (-0.3).sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = Manrope, fontWeight = FontWeight.Normal,
        fontSize = 15.sp, lineHeight = 22.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = Manrope, fontWeight = FontWeight.Normal,
        fontSize = 14.sp, lineHeight = 20.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = Manrope, fontWeight = FontWeight.Bold,
        fontSize = 15.sp, lineHeight = 20.sp, letterSpacing = 0.15.sp,
    ),
)

/** Every numeral on screen. */
val DataStyle = TextStyle(
    fontFamily = PlexMono,
    fontWeight = FontWeight.Medium,
    fontSize = 15.sp,
    lineHeight = 20.sp,
)

/** Small section labels. Rationed: see the eyebrow rule in docs/design-system.md. */
val LabelStyle = TextStyle(
    fontFamily = PlexMono,
    fontWeight = FontWeight.Normal,
    fontSize = 10.sp,
    lineHeight = 14.sp,
    letterSpacing = 2.2.sp,
)
