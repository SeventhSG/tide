package app.tide.core.design

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp

/**
 * Two families, one rule each.
 *
 * Manrope carries the interface. It is smooth and slightly geometric and holds
 * up at small sizes on a dark ground, where Roboto goes flat. Roboto Flex was the
 * original choice for being the system font; it was dropped because the app is
 * dark-first and Roboto's low-contrast strokes disappear against deep water.
 *
 * Every numeral, unit and data label is IBM Plex Mono with tabular figures, so a
 * column of numbers never shifts as values change. A number rendered in the body
 * font is a bug.
 *
 * Both are fetched through the downloadable-fonts provider rather than bundled,
 * which keeps roughly 400KB out of the APK. The provider needs the certificates
 * in res/values/font_certs.xml and a fallback when the fetch fails.
 */
private val provider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage = "com.google.android.gms",
    certificates = R.array.com_google_android_gms_fonts_certs,
)

private val Manrope = FontFamily(
    Font(GoogleFont("Manrope"), provider, FontWeight.Normal),
    Font(GoogleFont("Manrope"), provider, FontWeight.Medium),
    Font(GoogleFont("Manrope"), provider, FontWeight.SemiBold),
    Font(GoogleFont("Manrope"), provider, FontWeight.Bold),
    Font(GoogleFont("Manrope"), provider, FontWeight.ExtraBold),
)

val PlexMono = FontFamily(
    Font(GoogleFont("IBM Plex Mono"), provider, FontWeight.Normal),
    Font(GoogleFont("IBM Plex Mono"), provider, FontWeight.Medium),
    Font(GoogleFont("IBM Plex Mono"), provider, FontWeight.SemiBold),
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

/** Every numeral on screen. Tabular figures are the whole point. */
val DataStyle = TextStyle(
    fontFamily = PlexMono,
    fontWeight = FontWeight.Medium,
    fontSize = 15.sp,
    lineHeight = 20.sp,
)

/** Small caps-ish section labels. Rationed: see the eyebrow rule in the docs. */
val LabelStyle = TextStyle(
    fontFamily = PlexMono,
    fontWeight = FontWeight.Normal,
    fontSize = 10.sp,
    lineHeight = 14.sp,
    letterSpacing = 2.2.sp,
)
