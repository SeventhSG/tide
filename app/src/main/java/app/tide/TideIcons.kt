package app.tide

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.unit.dp

/**
 * The navigation icons.
 *
 * Hand-rolling icon paths is normally a mistake: there are good libraries and
 * a bespoke set drifts out of alignment. These five are the exception. They are
 * drawn on one grid at one stroke weight with one cap style, and the Today icon
 * is the app's own tide curve, which no library has.
 *
 * Anything beyond navigation should come from a real icon set rather than be
 * added here.
 */
object TideIcons {

    private fun icon(name: String, block: PathBuilder.() -> Unit): ImageVector =
        ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            addPath(
                pathData = androidx.compose.ui.graphics.vector.PathData(block),
                stroke = SolidColor(Color.White),
                strokeLineWidth = 1.7f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }.build()

    /** Today: the tide curve, one full cycle. */
    val Waves: ImageVector by lazy {
        icon("Waves") {
            moveTo(2f, 12f)
            curveTo(5f, 5f, 8f, 5f, 12f, 12f)
            curveTo(16f, 19f, 19f, 19f, 22f, 12f)
        }
    }

    val Barbell: ImageVector by lazy {
        icon("Barbell") {
            moveTo(6.5f, 6.5f); verticalLineTo(17.5f)
            moveTo(17.5f, 6.5f); verticalLineTo(17.5f)
            moveTo(3f, 9.5f); verticalLineTo(14.5f)
            moveTo(21f, 9.5f); verticalLineTo(14.5f)
            moveTo(6.5f, 12f); horizontalLineTo(17.5f)
        }
    }

    val Pulse: ImageVector by lazy {
        icon("Pulse") {
            moveTo(2f, 12f); horizontalLineTo(6f)
            lineTo(8.5f, 6.5f); lineTo(12f, 17.5f); lineTo(14.5f, 11f)
            lineTo(16f, 14f); horizontalLineTo(22f)
        }
    }

    val Card: ImageVector by lazy {
        icon("Card") {
            moveTo(6f, 6f); horizontalLineTo(18f)
            curveTo(19.7f, 6f, 21f, 7.3f, 21f, 9f)
            verticalLineTo(16f)
            curveTo(21f, 17.7f, 19.7f, 19f, 18f, 19f)
            horizontalLineTo(6f)
            curveTo(4.3f, 19f, 3f, 17.7f, 3f, 16f)
            verticalLineTo(9f)
            curveTo(3f, 7.3f, 4.3f, 6f, 6f, 6f)
            close()
            moveTo(3f, 10.5f); horizontalLineTo(21f)
        }
    }

    val Chat: ImageVector by lazy {
        icon("Chat") {
            moveTo(20f, 15f)
            curveTo(20f, 16.7f, 18.7f, 18f, 17f, 18f)
            horizontalLineTo(8f)
            lineTo(4f, 21f)
            verticalLineTo(7f)
            curveTo(4f, 5.3f, 5.3f, 4f, 7f, 4f)
            horizontalLineTo(17f)
            curveTo(18.7f, 4f, 20f, 5.3f, 20f, 7f)
            close()
        }
    }

    val Inbox: ImageVector by lazy {
        icon("Inbox") {
            moveTo(4f, 7f); horizontalLineTo(20f)
            verticalLineTo(18f)
            curveTo(20f, 19.1f, 19.1f, 20f, 18f, 20f)
            horizontalLineTo(6f)
            curveTo(4.9f, 20f, 4f, 19.1f, 4f, 18f)
            close()
            moveTo(4f, 8f); lineTo(12f, 14f); lineTo(20f, 8f)
        }
    }
}
