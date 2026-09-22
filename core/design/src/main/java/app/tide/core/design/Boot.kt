package app.tide.core.design

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import kotlin.math.sin
import kotlin.random.Random

/**
 * What you see for the first second of a cold start.
 *
 * The one place in this app where motion is the point rather than a cost. It is
 * seen once per launch, it covers work that is happening anyway (opening the
 * database, reading the day), and it is the app introducing itself: the surface
 * rises, bubbles climb through it, and the water hands over to the screen
 * underneath.
 *
 * **It never delays anything.** [onFinished] fires when the animation ends, and
 * the caller is free to fire it sooner. Under reduced motion it is skipped
 * entirely rather than shortened, because a splash that cannot move has no
 * reason to exist.
 */
@Composable
fun BootWave(
    modifier: Modifier = Modifier,
    durationMs: Int = 1_400,
    onFinished: () -> Unit,
) {
    val reduce = rememberReducedMotion()
    val progress = remember { Animatable(0f) }
    val bubbles = remember { bootBubbles() }
    val measurer = rememberTextMeasurer()

    LaunchedEffect(reduce) {
        if (reduce) {
            onFinished()
            return@LaunchedEffect
        }
        progress.animateTo(1f, tween(durationMs, easing = LinearEasing))
        onFinished()
    }

    if (reduce) return

    val p = progress.value
    Box(modifier.fillMaxSize().background(TideColors.Surface)) {
        Canvas(Modifier.fillMaxSize()) {
            // The water rises to fill the screen over the first two thirds, then
            // holds while the name settles.
            val fill = (p / 0.66f).coerceIn(0f, 1f)
            val surfaceY = size.height * (1f - eased(fill))

            drawRect(
                brush = Brush.verticalGradient(
                    listOf(
                        TideColors.Accent.copy(alpha = 0.16f),
                        TideColors.Surface,
                    ),
                    startY = surfaceY,
                    endY = size.height,
                ),
                topLeft = Offset(0f, surfaceY),
                size = androidx.compose.ui.geometry.Size(size.width, size.height - surfaceY),
            )
            drawSurfaceLine(surfaceY, p)
            drawBootBubbles(bubbles, p, surfaceY)

            // The name fades in from under the surface, the way something rises
            // into the light rather than being switched on.
            val nameAlpha = ((p - 0.35f) / 0.4f).coerceIn(0f, 1f) *
                (1f - ((p - 0.85f) / 0.15f).coerceIn(0f, 1f))
            if (nameAlpha > 0f) {
                val text = "TIDE"
                val style = TextStyle(
                    fontFamily = PlexMono,
                    fontSize = LabelStyle.fontSize * 2.2f,
                    letterSpacing = LabelStyle.letterSpacing * 2f,
                    color = TideColors.Text.copy(alpha = nameAlpha),
                )
                val layout = measurer.measure(text, style)
                drawText(
                    layout,
                    topLeft = Offset(
                        (size.width - layout.size.width) / 2f,
                        (size.height - layout.size.height) / 2f,
                    ),
                )
            }
        }
    }
}

/** Ease out, so the water arrives rather than stopping. */
private fun eased(t: Float): Float = 1f - (1f - t) * (1f - t)

private data class BootBubble(val x: Float, val size: Float, val speed: Float, val phase: Float)

private fun bootBubbles(): List<BootBubble> {
    // Seeded, so the launch looks the same every time rather than random enough
    // to read as a glitch.
    val random = Random(7)
    return List(18) {
        BootBubble(
            x = random.nextFloat(),
            size = 1.5f + random.nextFloat() * 4f,
            speed = 0.6f + random.nextFloat() * 0.8f,
            phase = random.nextFloat(),
        )
    }
}

private fun DrawScope.drawSurfaceLine(y: Float, p: Float) {
    val amplitude = size.height * 0.012f
    val path = Path().apply {
        moveTo(0f, y)
        val steps = 40
        for (i in 0..steps) {
            val x = size.width * i / steps
            val wave = sin((i / steps.toFloat() * 3f + p * 4f) * Math.PI.toFloat()) * amplitude
            lineTo(x, y + wave)
        }
    }
    drawPath(path, TideColors.Accent.copy(alpha = 0.55f), style = Stroke(width = 2f))
}

private fun DrawScope.drawBootBubbles(bubbles: List<BootBubble>, p: Float, surfaceY: Float) {
    bubbles.forEach { b ->
        val travelled = ((p * b.speed) + b.phase) % 1f
        val y = size.height - travelled * (size.height - surfaceY + size.height * 0.1f)
        if (y < surfaceY) return@forEach
        val drift = sin((travelled * 3f + b.phase) * Math.PI.toFloat()) * size.width * 0.01f
        drawCircle(
            color = Color.White.copy(alpha = 0.16f * (1f - travelled)),
            radius = b.size,
            center = Offset(b.x * size.width + drift, y),
        )
    }
}
