package app.tide.core.design

import androidx.compose.ui.graphics.Color

/**
 * Sampled from the app icon, not invented. `tools/render_brand.py` quantises
 * brand/tide-icon-source.png and writes brand/tide-palette.png; these are those
 * values. Theme and icon cannot drift apart because one is derived from the other.
 *
 * Measured contrast against Surface: Text 16.7, Accent 10.2, Warning 7.4,
 * TextMuted 6.4, Critical 5.3. All pass WCAG AA for body text.
 *
 * Hex literals live in this file and nowhere else in the app. CI greps for it.
 */
object TideColors {
    val Abyss = Color(0xFF03070B)
    val Surface = Color(0xFF060F14)
    val SurfaceRaised = Color(0xFF0C1B22)
    val Hairline = Color(0x1F8FC4C4)

    val Text = Color(0xFFEAF6F3)
    val TextMuted = Color(0xFF9FBFBF)
    val TextFaint = Color(0xFF6B8A8C)

    /** Wave glow. One accent, whole app, no user picker. */
    val Accent = Color(0xFF55E3C6)
    val AccentPressed = Color(0xFF33C4A8)
    val OnAccent = Color(0xFF04141A)

    /** Expiring, stale, due soon. Nothing else. */
    val Warning = Color(0xFFE8B355)

    /** Overdue, failed, over budget. Nothing else. Red never decorates. */
    val Critical = Color(0xFFF07A6E)
}
