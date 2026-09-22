package app.tide.core.design

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * An image for every exercise, drawn here rather than licensed from anyone.
 *
 * **Why these are drawings and not photographs.** A photo or animation library
 * needs a licence that permits redistribution in an Apache-2.0 app, checked in
 * the source's own licence file. No source has cleared that check: openGym's
 * notice records its animation rights as unresolved, and the datasets sharing
 * the "ExerciseDB" name do not share a licence. Rather than ship media the app
 * has no right to, or leave every row blank, the app draws its own: a mark for
 * the equipment on a tile tinted by the muscle it trains.
 *
 * That makes them honest and useful at a glance, and it makes them ours. When a
 * licensed library does clear the check, these are what it replaces.
 *
 * The tint is derived from the muscle rather than picked per exercise, so the
 * one accent rule holds: every tile is the same accent at a different depth,
 * never a second colour.
 */
enum class ArtEquipment { Barbell, Dumbbell, Machine, Cable, Bodyweight, Kettlebell, Band, Other }

enum class ArtRegion { Push, Pull, Legs, Core, Other }

@Composable
fun ExerciseArt(
    equipment: ArtEquipment,
    region: ArtRegion,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
) {
    val shape = ContinuousCornerShape(size / 3)
    val depth = when (region) {
        ArtRegion.Push -> 0.22f
        ArtRegion.Pull -> 0.16f
        ArtRegion.Legs -> 0.28f
        ArtRegion.Core -> 0.12f
        ArtRegion.Other -> 0.10f
    }

    Box(
        modifier
            .size(size)
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    listOf(
                        TideColors.Accent.copy(alpha = depth),
                        TideColors.Accent.copy(alpha = depth / 2.4f),
                    ),
                ),
            )
            .border(1.dp, TideColors.Hairline, shape),
    ) {
        Canvas(Modifier.size(size)) {
            val stroke = Stroke(width = this.size.minDimension * 0.055f, cap = StrokeCap.Round)
            val tint = TideColors.Text.copy(alpha = 0.82f)
            when (equipment) {
                ArtEquipment.Barbell -> drawBarbell(tint, stroke)
                ArtEquipment.Dumbbell -> drawDumbbell(tint, stroke)
                ArtEquipment.Machine -> drawMachine(tint, stroke)
                ArtEquipment.Cable -> drawCable(tint, stroke)
                ArtEquipment.Bodyweight -> drawBodyweight(tint, stroke)
                ArtEquipment.Kettlebell -> drawKettlebell(tint, stroke)
                ArtEquipment.Band -> drawBand(tint, stroke)
                ArtEquipment.Other -> drawOther(tint, stroke)
            }
        }
    }
}

private fun DrawScope.drawBarbell(tint: Color, stroke: Stroke) {
    val y = size.height / 2
    drawLine(tint, Offset(size.width * 0.22f, y), Offset(size.width * 0.78f, y), stroke.width, stroke.cap)
    listOf(0.26f, 0.74f).forEach { x ->
        drawLine(
            tint,
            Offset(size.width * x, size.height * 0.3f),
            Offset(size.width * x, size.height * 0.7f),
            stroke.width, stroke.cap,
        )
    }
    listOf(0.16f, 0.84f).forEach { x ->
        drawLine(
            tint,
            Offset(size.width * x, size.height * 0.38f),
            Offset(size.width * x, size.height * 0.62f),
            stroke.width, stroke.cap,
        )
    }
}

private fun DrawScope.drawDumbbell(tint: Color, stroke: Stroke) {
    val y = size.height / 2
    drawLine(tint, Offset(size.width * 0.36f, y), Offset(size.width * 0.64f, y), stroke.width, stroke.cap)
    listOf(0.3f, 0.7f).forEach { x ->
        drawLine(
            tint,
            Offset(size.width * x, size.height * 0.28f),
            Offset(size.width * x, size.height * 0.72f),
            stroke.width * 1.4f, stroke.cap,
        )
    }
}

private fun DrawScope.drawMachine(tint: Color, stroke: Stroke) {
    // A weight stack: three plates on a rail.
    val x = size.width * 0.5f
    drawLine(
        tint,
        Offset(x, size.height * 0.2f),
        Offset(x, size.height * 0.78f),
        stroke.width, stroke.cap,
    )
    (0..2).forEach { i ->
        val top = size.height * (0.42f + i * 0.14f)
        drawRoundRect(
            color = tint,
            topLeft = Offset(size.width * 0.28f, top),
            size = Size(size.width * 0.44f, size.height * 0.09f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.width * 0.03f),
            style = stroke,
        )
    }
}

private fun DrawScope.drawCable(tint: Color, stroke: Stroke) {
    // A pulley with a line hanging off it.
    drawCircle(
        tint,
        radius = size.minDimension * 0.13f,
        center = Offset(size.width * 0.5f, size.height * 0.3f),
        style = stroke,
    )
    drawLine(
        tint,
        Offset(size.width * 0.5f, size.height * 0.43f),
        Offset(size.width * 0.5f, size.height * 0.66f),
        stroke.width, stroke.cap,
    )
    drawLine(
        tint,
        Offset(size.width * 0.34f, size.height * 0.72f),
        Offset(size.width * 0.66f, size.height * 0.72f),
        stroke.width * 1.3f, stroke.cap,
    )
}

private fun DrawScope.drawBodyweight(tint: Color, stroke: Stroke) {
    // A bar with a hanging figure, which is what bodyweight work looks like.
    drawLine(
        tint,
        Offset(size.width * 0.2f, size.height * 0.28f),
        Offset(size.width * 0.8f, size.height * 0.28f),
        stroke.width, stroke.cap,
    )
    drawCircle(
        tint,
        radius = size.minDimension * 0.075f,
        center = Offset(size.width * 0.5f, size.height * 0.48f),
        style = stroke,
    )
    drawLine(
        tint,
        Offset(size.width * 0.5f, size.height * 0.56f),
        Offset(size.width * 0.5f, size.height * 0.76f),
        stroke.width, stroke.cap,
    )
}

private fun DrawScope.drawKettlebell(tint: Color, stroke: Stroke) {
    drawCircle(
        tint,
        radius = size.minDimension * 0.2f,
        center = Offset(size.width * 0.5f, size.height * 0.6f),
        style = stroke,
    )
    drawArc(
        color = tint,
        startAngle = 200f,
        sweepAngle = 140f,
        useCenter = false,
        topLeft = Offset(size.width * 0.34f, size.height * 0.2f),
        size = Size(size.width * 0.32f, size.height * 0.3f),
        style = stroke,
    )
}

private fun DrawScope.drawBand(tint: Color, stroke: Stroke) {
    drawArc(
        color = tint,
        startAngle = 140f,
        sweepAngle = 260f,
        useCenter = false,
        topLeft = Offset(size.width * 0.26f, size.height * 0.26f),
        size = Size(size.width * 0.48f, size.height * 0.48f),
        style = stroke,
    )
}

private fun DrawScope.drawOther(tint: Color, stroke: Stroke) {
    drawCircle(
        tint,
        radius = size.minDimension * 0.18f,
        center = Offset(size.width * 0.5f, size.height * 0.5f),
        style = stroke,
    )
}
