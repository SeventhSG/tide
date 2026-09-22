package app.tide.core.design

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sin

/**
 * A rounded shape whose corners are a superellipse rather than a circular arc.
 *
 * A circular corner meets the straight edge with a step change in curvature. The
 * eye reads that discontinuity as slightly cheap even when it cannot name it. A
 * superellipse ramps the curvature in, which is most of why iOS surfaces feel
 * more considered than Android ones.
 *
 * Compose's own [RoundedCornerShape] is circular, so this exists.
 *
 * @param radius corner size.
 * @param n superellipse exponent. 2 is exactly a circular corner, higher is
 *   squarer with a longer curvature ramp. 5 is close to Apple's.
 * @param segments arc subdivisions per corner. 24 is imperceptible from 32 at
 *   phone sizes and costs less to build.
 */
class ContinuousCornerShape(
    private val radius: Dp,
    private val n: Float = 5f,
    private val segments: Int = 24,
) : Shape {

    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val r = min(with(density) { radius.toPx() }, min(size.width, size.height) / 2f)
        if (r <= 0f) return Outline.Rectangle(size.toRect())
        return Outline.Generic(buildPath(size, r))
    }

    private fun buildPath(size: Size, r: Float): Path = Path().apply {
        val w = size.width
        val h = size.height
        val e = 2f / n

        // Each corner is swept from its horizontal tangent point to its vertical
        // one, all in the same rotational direction. Sweeping them inconsistently
        // makes the edges cross and the shape comes out as a parallelogram.
        fun corner(cx: Float, cy: Float, sx: Float, sy: Float, reversed: Boolean) {
            for (i in 0..segments) {
                val t = (if (reversed) segments - i else i) / segments.toFloat() * (Math.PI / 2f).toFloat()
                val x = cx + sx * r * superPow(cos(t), e)
                val y = cy + sy * r * superPow(sin(t), e)
                if (isEmpty) moveTo(x, y) else lineTo(x, y)
            }
        }

        corner(r, r, -1f, -1f, reversed = false)          // top left
        corner(w - r, r, 1f, -1f, reversed = true)        // top right
        corner(w - r, h - r, 1f, 1f, reversed = false)    // bottom right
        corner(r, h - r, -1f, 1f, reversed = true)        // bottom left
        close()
    }

    /** |v|^e, sign preserved, so the parameterisation survives negative cosines. */
    private fun superPow(v: Float, e: Float): Float =
        abs(v).pow(e) * if (v == 0f) 0f else sign(v)

    override fun equals(other: Any?): Boolean =
        other is ContinuousCornerShape &&
            radius == other.radius && n == other.n && segments == other.segments

    override fun hashCode(): Int =
        (radius.hashCode() * 31 + n.hashCode()) * 31 + segments

    override fun toString(): String = "ContinuousCornerShape(radius=$radius, n=$n)"
}

/**
 * Continuous corners need a larger radius than circular ones to read as
 * deliberate, which is why these are bigger than the usual Material values.
 *
 * Buttons and chips stay fully round: a pill has no corner to smooth.
 */
val TideShapes = Shapes(
    extraSmall = ContinuousCornerShape(8.dp),
    small = ContinuousCornerShape(12.dp),
    medium = ContinuousCornerShape(20.dp),
    large = ContinuousCornerShape(26.dp),
    extraLarge = ContinuousCornerShape(32.dp),
)

/** Sheets round only their top corners, so they get their own shape. */
val TideBottomSheetShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
