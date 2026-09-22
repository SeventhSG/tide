package app.tide.core.design

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.semantics.Role
import kotlin.math.abs
import kotlin.math.sin

/**
 * The transition between one part of the app and another.
 *
 * The app is called Tide, so a page hop is water moving rather than a card
 * sliding: the outgoing screen sinks and fades, the incoming one rises into
 * place, and a single crest of light crosses in the direction of travel. One
 * crest, not a wave machine, and it is finished in about a quarter of a second
 * because this is a door rather than a view.
 *
 * **Frequency still governs.** The crest is drawn only for a hop between the
 * app's main sections, a handful of times a session. Moving inside a section
 * gets the rise and fade alone, and under reduced motion both collapse to a
 * plain cut with no movement at all.
 */
@Composable
fun <T> WaveTransition(
    target: T,
    modifier: Modifier = Modifier,
    /** True when this is a hop between main sections, which earns the crest. */
    crest: Boolean = true,
    /** Deeper into the app rises; coming back sinks. Direction only. */
    forward: Boolean = true,
    content: @Composable (T) -> Unit,
) {
    val reduce = rememberReducedMotion()
    val shift = if (forward) 1 else -1

    Box(modifier) {
        AnimatedContent(
            targetState = target,
            transitionSpec = {
                if (reduce) {
                    fadeIn(tween(0)) togetherWith fadeOut(tween(0))
                } else {
                    val enter = fadeIn(tween(Durations.CONTAINER_TRANSFORM, easing = Emphasized)) +
                        slideInVertically(
                            tween(Durations.CONTAINER_TRANSFORM, easing = Emphasized),
                        ) { height -> shift * height / 26 }
                    val exit = fadeOut(tween(Durations.CONTAINER_TRANSFORM / 2, easing = Emphasized)) +
                        slideOutVertically(
                            tween(Durations.CONTAINER_TRANSFORM, easing = Emphasized),
                        ) { height -> -shift * height / 40 }
                    enter togetherWith exit
                }
            },
            label = "screen",
        ) { value -> content(value) }

        if (crest && !reduce) Crest(target, forward)
    }
}

/**
 * A sine edged band of light that crosses once per arrival.
 *
 * Over the content rather than behind it, at a low alpha, so it reads as light
 * moving through water and not as a panel wiping across. It takes no touches:
 * it is gone before a thumb could reach anything.
 */
@Composable
private fun <T> Crest(target: T, forward: Boolean) {
    val progress = remember(target) { Animatable(0f) }
    LaunchedEffect(target) {
        progress.snapTo(0f)
        progress.animateTo(
            1f,
            tween(Durations.CONTAINER_TRANSFORM + 140, easing = LinearEasing),
        )
    }
    val p = progress.value
    if (p <= 0f || p >= 1f) return

    Canvas(Modifier.fillMaxSize()) {
        val travel = if (forward) p else 1f - p
        val y = size.height * (1.3f * travel - 0.15f)
        val amplitude = size.height * 0.03f
        val band = size.height * 0.20f

        val path = Path().apply {
            moveTo(0f, y)
            val steps = 28
            for (i in 0..steps) {
                val x = size.width * i / steps
                lineTo(x, y + sin(i / steps.toFloat() * Math.PI.toFloat() * 2f) * amplitude)
            }
            lineTo(size.width, y - band)
            lineTo(0f, y - band)
            close()
        }

        // Fades up and back down across its own crossing, so neither end of it
        // is ever a visible edge.
        val alpha = sin(p * Math.PI.toFloat()) * 0.22f
        drawPath(
            path,
            Brush.verticalGradient(
                colors = listOf(Color.Transparent, TideColors.Accent.copy(alpha = alpha)),
                startY = y - band,
                endY = y,
            ),
        )
    }
}

/**
 * The press response every tappable surface in the app shares.
 *
 * The scale drops to 98.5 percent over 140ms and comes back. It is feedback
 * rather than decoration, so it survives the frequency gate even on the
 * logger's controls, and under reduced motion it becomes instant rather than
 * absent: the surface still answers, it just does not travel.
 */
@Composable
fun Modifier.tidePress(
    enabled: Boolean = true,
    role: Role = Role.Button,
    onClick: () -> Unit,
): Modifier {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val reduce = rememberReducedMotion()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) 0.985f else 1f,
        animationSpec = tween(if (reduce) 0 else Durations.PRESS, easing = PressEasing),
        label = "tidePress",
    )
    return this
        .scale(if (abs(scale - 1f) < 0.0001f) 1f else scale)
        .clickable(
            interactionSource = interaction,
            indication = null,
            enabled = enabled,
            role = role,
            onClick = onClick,
        )
}
