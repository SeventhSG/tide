package app.tide.core.design

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.sin
import kotlin.random.Random

/**
 * The app's ground is deep water, not a flat colour.
 *
 * Three rules govern it, and they are what keep it from being decoration:
 *
 *  1. It is never behind a number. Anything carrying text sits on a scrim, so
 *     contrast is measured against the scrim and not against the water.
 *  2. It is environment, not feedback. It never tells you something changed.
 *  3. It turns off: under reduced motion it stops moving, and the session logger
 *     switches it off entirely.
 *
 * Drawn with Compose rather than an AGSL shader. The shader version was prettier
 * and wrong: it needed API 33, so most of the effect vanished on older devices,
 * and it could not be screenshot-tested at all because the JVM renderer does not
 * execute AGSL. This runs everywhere down to minSdk and renders in tests, which
 * is worth more than the extra fidelity.
 */

private val DepthGradient = Brush.verticalGradient(
    0.00f to Color(0xFF1B5F68),
    0.10f to Color(0xFF12454F),
    0.30f to Color(0xFF0A2A33),
    0.58f to Color(0xFF05171D),
    1.00f to Color(0xFF03090D),
)

enum class OceanIntensity {
    /** Shafts, bubbles, drift. */
    Full,

    /** Depth gradient and vignette only. Battery saver, low RAM. */
    Subtle,

    /** Flat surface colour. The session logger uses this. */
    Off,
}

@Composable
fun OceanBackground(
    modifier: Modifier = Modifier,
    intensity: OceanIntensity = OceanIntensity.Full,
    content: @Composable BoxScope.() -> Unit,
) {
    val reduceMotion = rememberReducedMotion()

    if (intensity == OceanIntensity.Off) {
        Box(modifier.fillMaxSize().background(TideColors.Surface), content = content)
        return
    }

    // One slow clock drives everything. 60s is long enough that the loop is not
    // perceptible, and a single animation is cheaper than one per element.
    val transition = rememberInfiniteTransition(label = "ocean")
    val t by if (reduceMotion) {
        remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    } else {
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(60_000, easing = LinearEasing), RepeatMode.Restart),
            label = "oceanTime",
        )
    }

    val bubbles = remember { generateBubbles() }

    Box(modifier.fillMaxSize().background(DepthGradient)) {
        Canvas(Modifier.fillMaxSize()) {
            drawShafts(t)
            if (intensity == OceanIntensity.Full) {
                drawMotes(t)
                drawBubbles(bubbles, t)
            }
            drawVignette()
        }
        content()
    }
}

/**
 * Light shafts. Slanted parallelograms with a vertical gradient, sliding
 * sideways on a slow sine so they breathe rather than march.
 */
private fun DrawScope.drawShafts(t: Float) {
    data class Shaft(val x: Float, val w: Float, val alpha: Float, val phase: Float)

    val shafts = listOf(
        Shaft(0.06f, 0.11f, 0.20f, 0.0f),
        Shaft(0.27f, 0.05f, 0.14f, 0.6f),
        Shaft(0.46f, 0.16f, 0.26f, 0.25f),
        Shaft(0.70f, 0.07f, 0.16f, 0.85f),
        Shaft(0.88f, 0.04f, 0.11f, 0.45f),
    )

    // Light stops around 46 percent of the way down, as it does in water.
    val depth = size.height * 0.46f
    val slant = size.width * 0.16f

    // Each shaft is drawn as overlapping sub-bands whose alpha follows a bell
    // curve across the width. A single parallelogram gives hard diagonal edges,
    // which is the tell that immediately reads as a shape rather than as light.
    val bands = 11

    shafts.forEach { s ->
        val sway = sin((t + s.phase) * 2f * Math.PI.toFloat()) * size.width * 0.035f
        val left = s.x * size.width + sway
        val w = s.w * size.width
        val bandW = w / bands

        for (i in 0 until bands) {
            val centred = (i + 0.5f) / bands * 2f - 1f          // -1 at edges, 0 mid
            val falloff = kotlin.math.exp(-(centred * centred) * 2.6f)
            val a = s.alpha * falloff
            if (a < 0.004f) continue

            val bx = left + i * bandW
            val path = Path().apply {
                moveTo(bx, 0f)
                lineTo(bx + bandW + 0.75f, 0f)                   // hairline overlap
                lineTo(bx + bandW + 0.75f + slant, depth)
                lineTo(bx + slant, depth)
                close()
            }
            drawPath(
                path,
                Brush.verticalGradient(
                    0.00f to Color(0xFF8AFFE8).copy(alpha = a),
                    0.55f to Color(0xFF8AFFE8).copy(alpha = a * 0.32f),
                    1.00f to Color.Transparent,
                    endY = depth,
                ),
            )
        }
    }
}

private class Bubble(val x: Float, val seed: Float, val r: Float, val speed: Float)

private fun generateBubbles(): List<Bubble> {
    val rng = Random(7)
    return List(18) {
        Bubble(
            x = rng.nextFloat(),
            seed = rng.nextFloat(),
            r = 1.2f + rng.nextFloat() * 4.4f,
            speed = 0.35f + rng.nextFloat() * 0.85f,
        )
    }
}

private fun DrawScope.drawBubbles(bubbles: List<Bubble>, t: Float) {
    bubbles.forEach { b ->
        // Each bubble runs its own loop, offset by its seed, so they never
        // rise in step with one another.
        val p = ((t * b.speed) + b.seed) % 1f
        val y = size.height * (1.05f - p * 1.15f)
        val drift = sin((p + b.seed) * 4f * Math.PI.toFloat()) * size.width * 0.012f
        val x = b.x * size.width + drift

        // Fade in off the bottom and out before the surface, so none of them pop.
        val alpha = (minOf(p / 0.12f, 1f) * (1f - maxOf(0f, (p - 0.80f) / 0.20f))) * 0.42f
        if (alpha <= 0.01f) return@forEach

        drawCircle(
            color = Color(0xFFBAFAF0).copy(alpha = alpha),
            radius = b.r,
            center = Offset(x, y),
            style = Stroke(width = b.r * 0.34f),
        )
        drawCircle(
            color = Color(0xFFD8FFF8).copy(alpha = alpha * 0.8f),
            radius = b.r * 0.28f,
            center = Offset(x - b.r * 0.3f, y - b.r * 0.35f),
        )
    }
}

/** Suspended particulate. Denser with depth, drifting up very slowly. */
private fun DrawScope.drawMotes(t: Float) {
    val rng = Random(21)
    repeat(70) {
        val mx = rng.nextFloat() * size.width
        val base = rng.nextFloat()
        val my = ((base - t * 0.10f) % 1f + 1f) % 1f
        val y = my * size.height
        val depthFade = 0.10f + (y / size.height) * 0.22f
        drawCircle(
            color = Color(0xFFBAFAF0).copy(alpha = depthFade),
            radius = 0.9f,
            center = Offset(mx, y),
        )
    }
}

/** Pulls the corners down so the content sits in a pool of light. */
private fun DrawScope.drawVignette() {
    drawRect(
        Brush.radialGradient(
            0.42f to Color.Transparent,
            1.00f to Color(0xFF02060A).copy(alpha = 0.66f),
            center = Offset(size.width * 0.5f, size.height * 0.30f),
            radius = size.height * 0.78f,
        ),
    )
}

/** The surface every readable thing sits on. */
fun oceanScrimColor(): Color = Color(0xCC0B1D25)
