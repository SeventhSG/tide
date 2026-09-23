package app.tide.core.design

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.sin
import kotlin.random.Random

data class NavItem(val label: String, val icon: ImageVector)

/**
 * The bottom bar, as a tank of water rather than a strip of buttons.
 *
 * It floats, inset from all three edges, so the ocean runs behind and around it
 * and it reads as a glass case sitting in the water rather than a panel bolted
 * to the bottom of the screen.
 *
 * Four things together make the glass, and dropping any one of them collapses
 * the effect into a grey rectangle:
 *
 *  1. A vertical fill that is lighter at the top, because the light is above.
 *  2. One hairline of near-white along the top edge, and a fainter one along the
 *     bottom. That specular line is the single strongest cue that a surface is
 *     glass rather than paint.
 *  3. A selection pill that slides rather than cuts, on a spring, so the
 *     highlight behaves like it has mass.
 *  4. Bubbles rising inside the bar, clipped to it.
 *
 * On the motion budget: the sliding pill is a state transition, so it passes the
 * frequency gate on a control used constantly. The bubbles are ambient and do
 * not, so they are slow enough to sit below notice and stop under reduced
 * motion. This is the one place in the app that gets an ambient loop.
 *
 * Note this is not Apple's Liquid Glass, which is an Apple-platform material.
 * It is an approximation built from gradients and borders, and it does not
 * refract what is behind it: Compose has no backdrop blur that works below
 * API 31, and a nav bar that looks solid on half the install base is not worth
 * the API split.
 */
@Composable
fun LiquidGlassNav(
    items: List<NavItem>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = ContinuousCornerShape(30.dp)
    val reduce = rememberReducedMotion()

    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .height(66.dp),
    ) {
        val itemWidth: Dp = maxWidth / items.size
        val indicatorX by animateDpAsState(
            targetValue = itemWidth * selected,
            // Critically damped either way: the spring still gives the
            // highlight weight as it settles, but it never overshoots and
            // rebounds past the target.
            animationSpec = spring(
                dampingRatio = 1f,
                stiffness = if (reduce) Spring.StiffnessHigh else Spring.StiffnessMediumLow,
            ),
            label = "navIndicator",
        )

        Box(
            Modifier
                .fillMaxSize()
                .clip(shape)
                .background(
                    Brush.verticalGradient(
                        0.0f to Color(0xFF12323D).copy(alpha = 0.82f),
                        0.5f to Color(0xFF0A1F27).copy(alpha = 0.88f),
                        1.0f to Color(0xFF071820).copy(alpha = 0.92f),
                    ),
                ),
        ) {
            if (!reduce) NavBubbles()

            // The selection pill, under the icons.
            Box(
                Modifier
                    .offset(x = indicatorX)
                    .width(itemWidth)
                    .fillMaxSize()
                    .padding(horizontal = 6.dp, vertical = 7.dp)
                    .clip(ContinuousCornerShape(22.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                TideColors.Accent.copy(alpha = 0.22f),
                                TideColors.Accent.copy(alpha = 0.07f),
                            ),
                        ),
                    )
                    .border(
                        BorderStroke(
                            1.dp,
                            Brush.verticalGradient(
                                0.0f to Color.White.copy(alpha = 0.22f),
                                0.6f to Color.Transparent,
                            ),
                        ),
                        ContinuousCornerShape(22.dp),
                    ),
            )

            Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                items.forEachIndexed { i, item ->
                    val active = i == selected
                    val tint = if (active) TideColors.Accent else TideColors.TextFaint
                    Column(
                        Modifier
                            .width(itemWidth)
                            .fillMaxSize()
                            .clip(ContinuousCornerShape(22.dp))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                role = Role.Tab,
                            ) { onSelect(i) },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Icon(item.icon, item.label, Modifier.size(21.dp), tint = tint)
                        Spacer(Modifier.height(4.dp))
                        Text(item.label, style = LabelStyle, color = tint)
                    }
                }
            }
        }

        // The specular edge. Drawn last so nothing sits over it.
        Box(
            Modifier
                .fillMaxSize()
                .border(
                    BorderStroke(
                        1.dp,
                        Brush.verticalGradient(
                            0.00f to Color.White.copy(alpha = 0.30f),
                            0.35f to Color.White.copy(alpha = 0.05f),
                            1.00f to Color.White.copy(alpha = 0.10f),
                        ),
                    ),
                    shape,
                ),
        )
    }
}

/** Bubbles inside the tank. Clipped by the parent, so they never escape it. */
@Composable
private fun NavBubbles() {
    val transition = rememberInfiniteTransition(label = "navBubbles")
    val t by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(14_000, easing = LinearEasing), RepeatMode.Restart),
        label = "navBubbleTime",
    )
    val seeds = remember {
        List(9) { Triple(Random(it * 31).nextFloat(), Random(it * 17).nextFloat(), 0.9f + Random(it * 13).nextFloat() * 2.1f) }
    }

    Canvas(Modifier.fillMaxSize()) {
        seeds.forEach { (x, seed, r) ->
            val p = ((t + seed) % 1f)
            val y = size.height * (1.05f - p * 1.2f)
            val drift = sin((p + seed) * 3f * Math.PI.toFloat()) * 3f
            val alpha = (minOf(p / 0.18f, 1f) * (1f - maxOf(0f, (p - 0.7f) / 0.3f))) * 0.34f
            if (alpha <= 0.01f) return@forEach
            drawCircle(
                color = Color(0xFFBAFAF0).copy(alpha = alpha),
                radius = r,
                center = Offset(x * size.width + drift, y),
                style = Stroke(width = r * 0.4f),
            )
        }
    }
}
