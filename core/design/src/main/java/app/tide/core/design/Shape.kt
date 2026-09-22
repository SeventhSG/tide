package app.tide.core.design

import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.material3.Shapes
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.toRect
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
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
 * Compose's own rounded shapes are circular, so this exists.
 *
 * Extends [CornerBasedShape] rather than plain Shape for two reasons: Material's
 * [Shapes] will not accept anything else, and it brings per-corner sizes, which
 * the bottom sheet needs.
 *
 * @param n superellipse exponent. 2 is exactly a circular corner, higher is
 *   squarer with a longer curvature ramp. 5 is close to Apple's.
 * @param segments arc subdivisions per corner. 24 is indistinguishable from 32
 *   at phone sizes and costs less to build.
 */
class ContinuousCornerShape(
    topStart: CornerSize,
    topEnd: CornerSize,
    bottomEnd: CornerSize,
    bottomStart: CornerSize,
    private val n: Float = 5f,
    private val segments: Int = 24,
) : CornerBasedShape(topStart, topEnd, bottomEnd, bottomStart) {

    override fun createOutline(
        size: Size,
        topStart: Float,
        topEnd: Float,
        bottomEnd: Float,
        bottomStart: Float,
        layoutDirection: LayoutDirection,
    ): Outline {
        if (topStart + topEnd + bottomEnd + bottomStart == 0f) {
            return Outline.Rectangle(size.toRect())
        }
        val ltr = layoutDirection == LayoutDirection.Ltr
        val tl = if (ltr) topStart else topEnd
        val tr = if (ltr) topEnd else topStart
        val br = if (ltr) bottomEnd else bottomStart
        val bl = if (ltr) bottomStart else bottomEnd
        return Outline.Generic(buildPath(size, tl, tr, br, bl))
    }

    private fun buildPath(size: Size, tl: Float, tr: Float, br: Float, bl: Float): Path =
        Path().apply {
            val w = size.width
            val h = size.height
            val half = min(w, h) / 2f
            val e = 2f / n

            // Each corner is swept from its horizontal tangent point to its
            // vertical one, all in the same rotational direction. Sweeping them
            // inconsistently makes the edges cross and the shape renders as a
            // parallelogram, which is a mistake worth only making once.
            fun corner(r0: Float, cx: Float, cy: Float, sx: Float, sy: Float, reversed: Boolean) {
                val r = min(r0, half)
                if (r <= 0f) {
                    val x = cx + sx * 0f
                    val y = cy + sy * 0f
                    if (isEmpty) moveTo(x, y) else lineTo(x, y)
                    return
                }
                for (i in 0..segments) {
                    val step = (if (reversed) segments - i else i) / segments.toFloat()
                    val t = step * (Math.PI.toFloat() / 2f)
                    val x = cx + sx * r * superPow(cos(t), e)
                    val y = cy + sy * r * superPow(sin(t), e)
                    if (isEmpty) moveTo(x, y) else lineTo(x, y)
                }
            }

            corner(tl, min(tl, half), min(tl, half), -1f, -1f, reversed = false)
            corner(tr, w - min(tr, half), min(tr, half), 1f, -1f, reversed = true)
            corner(br, w - min(br, half), h - min(br, half), 1f, 1f, reversed = false)
            corner(bl, min(bl, half), h - min(bl, half), -1f, 1f, reversed = true)
            close()
        }

    /** |v|^e with the sign preserved, so negative cosines survive the power. */
    private fun superPow(v: Float, e: Float): Float =
        abs(v).pow(e) * if (v == 0f) 0f else sign(v)

    override fun copy(
        topStart: CornerSize,
        topEnd: CornerSize,
        bottomEnd: CornerSize,
        bottomStart: CornerSize,
    ) = ContinuousCornerShape(topStart, topEnd, bottomEnd, bottomStart, n, segments)

    override fun equals(other: Any?): Boolean =
        other is ContinuousCornerShape &&
            topStart == other.topStart && topEnd == other.topEnd &&
            bottomEnd == other.bottomEnd && bottomStart == other.bottomStart &&
            n == other.n && segments == other.segments

    override fun hashCode(): Int {
        var result = topStart.hashCode()
        result = 31 * result + topEnd.hashCode()
        result = 31 * result + bottomEnd.hashCode()
        result = 31 * result + bottomStart.hashCode()
        result = 31 * result + n.hashCode()
        return 31 * result + segments
    }

    override fun toString(): String = "ContinuousCornerShape(n=$n, topStart=$topStart)"
}

/** All four corners the same size. The common case. */
fun ContinuousCornerShape(radius: Dp, n: Float = 5f): ContinuousCornerShape =
    ContinuousCornerShape(
        CornerSize(radius), CornerSize(radius), CornerSize(radius), CornerSize(radius), n,
    )

/** Top corners only, for sheets. */
fun ContinuousTopCornerShape(radius: Dp, n: Float = 5f): ContinuousCornerShape =
    ContinuousCornerShape(
        CornerSize(radius), CornerSize(radius), CornerSize(0.dp), CornerSize(0.dp), n,
    )

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

val TideBottomSheetShape = ContinuousTopCornerShape(28.dp)
