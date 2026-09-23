package app.tide.core.design

import android.provider.Settings
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * True when the device asks for less movement, by either of the two signals
 * Android exposes. Checking only one misses a large share of users: the
 * animator scale is what the developer options and most "reduce animations"
 * toggles actually set, while some OEM accessibility settings only move the
 * transition scale.
 *
 * Every animation above a press response is gated on this. Wrapped here once so
 * no feature module has to remember.
 */
@Composable
fun rememberReducedMotion(): Boolean {
    val resolver = LocalContext.current.contentResolver
    return remember(resolver) {
        val animator = Settings.Global.getFloat(
            resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f,
        )
        val transition = Settings.Global.getFloat(
            resolver, Settings.Global.TRANSITION_ANIMATION_SCALE, 1f,
        )
        animator == 0f || transition == 0f
    }
}

/** Material's emphasized easing. Used for anything that moves between states. */
val Emphasized = CubicBezierEasing(0.2f, 0f, 0f, 1f)

/** Press response. Short enough to feel like contact rather than animation. */
val PressEasing = CubicBezierEasing(0.2f, 0.8f, 0.3f, 1f)

object Durations {
    /** Navigating into a module. Many times a day, so it stays brisk. */
    const val CONTAINER_TRANSFORM = 250

    /**
     * A hop between main sections, crest and content swap together. A
     * handful of times a session, which is what earns it the longer of the
     * two: it is the whole transition now, not a decoration riding on top
     * of a faster one.
     */
    const val WAVE = 480

    /** Sheets and dialogs. Monthly, so it can breathe. */
    const val SHEET = 300

    /** Press feedback. */
    const val PRESS = 140
}
