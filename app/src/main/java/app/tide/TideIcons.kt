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

    /** Settings: a dial with a mark on it, rather than the usual cog. */
    val Settings: ImageVector by lazy {
        icon("Settings") {
            moveTo(12f, 4f)
            curveTo(16.4f, 4f, 20f, 7.6f, 20f, 12f)
            curveTo(20f, 16.4f, 16.4f, 20f, 12f, 20f)
            curveTo(7.6f, 20f, 4f, 16.4f, 4f, 12f)
            curveTo(4f, 7.6f, 7.6f, 4f, 12f, 4f)
            close()
            moveTo(12f, 7.5f); verticalLineTo(12f)
            moveTo(12f, 12f); lineTo(15f, 14f)
        }
    }

    /** Body: a drop, for weight and the measurements that come with it. */
    val Drop: ImageVector by lazy {
        icon("Drop") {
            moveTo(12f, 3.5f)
            curveTo(12f, 3.5f, 18f, 10f, 18f, 14f)
            curveTo(18f, 17.3f, 15.3f, 20f, 12f, 20f)
            curveTo(8.7f, 20f, 6f, 17.3f, 6f, 14f)
            curveTo(6f, 10f, 12f, 3.5f, 12f, 3.5f)
            close()
        }
    }

    /** A speaker with one arc. Sound, at the volume this app uses it. */
    val Sound: ImageVector by lazy {
        icon("Sound") {
            moveTo(4f, 9.5f); horizontalLineTo(7.5f); lineTo(12f, 5.5f)
            verticalLineTo(18.5f); lineTo(7.5f, 14.5f); horizontalLineTo(4f)
            close()
            moveTo(15.5f, 9f)
            curveTo(17f, 10.5f, 17f, 13.5f, 15.5f, 15f)
        }
    }

    /** Move a row up or down in a plan. */
    val ChevronUp: ImageVector by lazy {
        icon("ChevronUp") {
            moveTo(6f, 14.5f); lineTo(12f, 8.5f); lineTo(18f, 14.5f)
        }
    }

    val ChevronDown: ImageVector by lazy {
        icon("ChevronDown") {
            moveTo(6f, 9.5f); lineTo(12f, 15.5f); lineTo(18f, 9.5f)
        }
    }

    /** Remove a row. A plain cross, never a bin: this deletes a plan line. */
    val Cross: ImageVector by lazy {
        icon("Cross") {
            moveTo(7f, 7f); lineTo(17f, 17f)
            moveTo(17f, 7f); lineTo(7f, 17f)
        }
    }

    val Calendar: ImageVector by lazy {
        icon("Calendar") {
            moveTo(5f, 6f); horizontalLineTo(19f)
            curveTo(20f, 6f, 20.5f, 6.9f, 20.5f, 8f)
            verticalLineTo(18f)
            curveTo(20.5f, 19.1f, 20f, 20f, 19f, 20f)
            horizontalLineTo(5f)
            curveTo(4f, 20f, 3.5f, 19.1f, 3.5f, 18f)
            verticalLineTo(8f)
            curveTo(3.5f, 6.9f, 4f, 6f, 5f, 6f)
            close()
            moveTo(3.5f, 10f); horizontalLineTo(20.5f)
            moveTo(8f, 4f); verticalLineTo(7.5f)
            moveTo(16f, 4f); verticalLineTo(7.5f)
        }
    }
}
