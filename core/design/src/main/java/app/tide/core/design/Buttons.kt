package app.tide.core.design

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

/**
 * Buttons.
 *
 * Material's Button is a flat fill. What reads as a physical surface instead of
 * a coloured rectangle is a one-stop vertical gradient plus a bright inset line
 * along the top edge, which is the highlight you would get from light above.
 * That is the entire trick and it costs nothing.
 *
 * Press drops the scale to 98.5 percent over 140ms. That is the only motion a
 * button has, and it is feedback, so it survives the frequency gate even on a
 * control pressed a hundred times in a session. It still collapses under
 * reduced motion, where the press becomes instant rather than absent.
 *
 * Minimum height is 52dp, above the 48dp floor, because the primary action on
 * Today is pressed with a thumb while standing up.
 */
private val MinTouch = 52.dp

@Composable
fun TideButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val reduce = rememberReducedMotion()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.985f else 1f,
        animationSpec = tween(if (reduce) 0 else Durations.PRESS, easing = PressEasing),
        label = "press",
    )

    Box(
        modifier
            .scale(scale)
            .defaultMinSize(minHeight = MinTouch)
            .clip(CircleShape)
            .background(
                if (enabled) {
                    Brush.verticalGradient(
                        listOf(TideColors.Accent, TideColors.AccentPressed),
                    )
                } else {
                    Brush.verticalGradient(
                        listOf(TideColors.SurfaceRaised, TideColors.SurfaceRaised),
                    )
                },
            )
            // The highlight. One hairline of light along the top edge.
            .border(
                BorderStroke(
                    1.dp,
                    Brush.verticalGradient(
                        0.0f to Color.White.copy(alpha = if (enabled) 0.34f else 0.04f),
                        0.5f to Color.Transparent,
                    ),
                ),
                CircleShape,
            )
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = 26.dp, vertical = 15.dp),
        contentAlignment = Alignment.Center,
    ) { content() }
}

/** Secondary. Quiet enough that it never competes with the primary. */
@Composable
fun TideGhostButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val reduce = rememberReducedMotion()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.985f else 1f,
        animationSpec = tween(if (reduce) 0 else Durations.PRESS, easing = PressEasing),
        label = "pressGhost",
    )

    Box(
        modifier
            .scale(scale)
            .defaultMinSize(minHeight = MinTouch)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = if (pressed) 0.10f else 0.06f))
            .border(1.dp, TideColors.Hairline, CircleShape)
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = 22.dp, vertical = 15.dp),
        contentAlignment = Alignment.Center,
    ) { content() }
}
