package app.tide.core.design

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

/**
 * One theme, applied once at the root. Feature modules never override it, and
 * there is no user-facing accent picker: an app whose every install looks
 * different has no identity.
 */
private val TideColorScheme = darkColorScheme(
    primary = TideColors.Accent,
    onPrimary = TideColors.OnAccent,
    secondary = TideColors.TextMuted,
    onSecondary = TideColors.Surface,
    background = TideColors.Surface,
    onBackground = TideColors.Text,
    surface = TideColors.Surface,
    onSurface = TideColors.Text,
    surfaceVariant = TideColors.SurfaceRaised,
    onSurfaceVariant = TideColors.TextMuted,
    outline = TideColors.Hairline,
    error = TideColors.Critical,
    onError = TideColors.Surface,
)

@Composable
fun TideTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = TideColorScheme,
        typography = TideTypography,
        shapes = TideShapes,
        content = content,
    )
}
