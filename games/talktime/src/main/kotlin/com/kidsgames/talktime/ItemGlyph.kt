package com.kidsgames.talktime

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.kidsgames.designkit.KidPalette
import com.kidsgames.vocab.Sector
import com.kidsgames.vocab.VocabItem
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * No real artwork exists yet for `:core:vocab`'s catalogue (see
 * PlaceholderAssets.kt there) -- every [VocabItem.image] is a placeholder
 * integer, not a resolvable drawable. This draws a simple vector stand-in
 * per item instead of leaving a blank box: one broad silhouette per
 * [Sector], coloured from the shared [KidPalette.Swatch] so sectors read as
 * visually distinct without relying on colour alone.
 *
 * This is deliberately its own small copy inside `:games:talktime` rather
 * than a shared component: `:games:*` modules never depend on each other,
 * so `:games:whatisit`'s own (separately-owned) glyph renderer is not
 * reachable from here, and a stand-in this simple does not belong in
 * `:core:designkit` either.
 */
@Composable
fun ItemGlyph(item: VocabItem, modifier: Modifier = Modifier) {
    val color = KidPalette.Swatch[item.sector.ordinal % KidPalette.Swatch.size]
    Canvas(modifier = modifier) {
        drawSectorGlyph(item.sector, color)
    }
}

private fun DrawScope.drawSectorGlyph(sector: Sector, color: Color) {
    when (sector) {
        Sector.ANIMALS -> drawPaw(color)
        Sector.FRUITS -> drawRoundFruit(color)
        Sector.VEGETABLES -> drawRootVegetable(color)
        Sector.VEHICLES -> drawVehicle(color)
        Sector.BODY -> drawFigure(color)
        Sector.CLOTHES -> drawShirt(color)
        Sector.HOUSEHOLD -> drawMug(color)
        Sector.FOOD -> drawLoaf(color)
        Sector.NATURE -> drawSun(color)
        Sector.JOBS -> drawCap(color)
        Sector.INSTRUMENTS -> drawDrum(color)
        Sector.SPORTS -> drawBall(color)
    }
}

private fun DrawScope.drawPaw(color: Color) {
    val w = size.width
    val h = size.height
    drawCircle(color = color, radius = w * 0.28f, center = Offset(w * 0.5f, h * 0.62f))
    val padRadius = w * 0.13f
    drawCircle(color = color, radius = padRadius, center = Offset(w * 0.28f, h * 0.3f))
    drawCircle(color = color, radius = padRadius, center = Offset(w * 0.5f, h * 0.18f))
    drawCircle(color = color, radius = padRadius, center = Offset(w * 0.72f, h * 0.3f))
}

private fun DrawScope.drawRoundFruit(color: Color) {
    val w = size.width
    val h = size.height
    drawCircle(color = color, radius = min(w, h) * 0.4f, center = Offset(w * 0.5f, h * 0.56f))
    drawLine(color = KidPalette.Green, start = Offset(w * 0.5f, h * 0.16f), end = Offset(w * 0.5f, h * 0.3f), strokeWidth = w * 0.06f)
}

private fun DrawScope.drawRootVegetable(color: Color) {
    val w = size.width
    val h = size.height
    val path = Path().apply {
        moveTo(w * 0.5f, h * 0.9f)
        lineTo(w * 0.3f, h * 0.25f)
        lineTo(w * 0.7f, h * 0.25f)
        close()
    }
    drawPath(path, color = color)
    drawLine(color = KidPalette.Green, start = Offset(w * 0.5f, h * 0.1f), end = Offset(w * 0.5f, h * 0.28f), strokeWidth = w * 0.06f)
}

private fun DrawScope.drawVehicle(color: Color) {
    val w = size.width
    val h = size.height
    drawRoundRect(
        color = color,
        topLeft = Offset(w * 0.1f, h * 0.38f),
        size = Size(w * 0.8f, h * 0.3f),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.1f),
    )
    drawCircle(color = KidPalette.OnSurface, radius = w * 0.12f, center = Offset(w * 0.3f, h * 0.72f))
    drawCircle(color = KidPalette.OnSurface, radius = w * 0.12f, center = Offset(w * 0.7f, h * 0.72f))
}

private fun DrawScope.drawFigure(color: Color) {
    val w = size.width
    val h = size.height
    drawCircle(color = color, radius = w * 0.18f, center = Offset(w * 0.5f, h * 0.24f))
    val body = Path().apply {
        moveTo(w * 0.32f, h * 0.9f)
        lineTo(w * 0.4f, h * 0.42f)
        lineTo(w * 0.6f, h * 0.42f)
        lineTo(w * 0.68f, h * 0.9f)
        close()
    }
    drawPath(body, color = color)
}

private fun DrawScope.drawShirt(color: Color) {
    val w = size.width
    val h = size.height
    val path = Path().apply {
        moveTo(w * 0.35f, h * 0.2f)
        lineTo(w * 0.15f, h * 0.35f)
        lineTo(w * 0.28f, h * 0.5f)
        lineTo(w * 0.32f, h * 0.42f)
        lineTo(w * 0.32f, h * 0.85f)
        lineTo(w * 0.68f, h * 0.85f)
        lineTo(w * 0.68f, h * 0.42f)
        lineTo(w * 0.72f, h * 0.5f)
        lineTo(w * 0.85f, h * 0.35f)
        lineTo(w * 0.65f, h * 0.2f)
        lineTo(w * 0.5f, h * 0.3f)
        close()
    }
    drawPath(path, color = color)
}

private fun DrawScope.drawMug(color: Color) {
    val w = size.width
    val h = size.height
    drawRoundRect(
        color = color,
        topLeft = Offset(w * 0.2f, h * 0.25f),
        size = Size(w * 0.45f, h * 0.55f),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.06f),
    )
    drawArc(
        color = color,
        startAngle = -90f,
        sweepAngle = 180f,
        useCenter = false,
        topLeft = Offset(w * 0.6f, h * 0.34f),
        size = Size(w * 0.22f, h * 0.32f),
        style = androidx.compose.ui.graphics.drawscope.Stroke(width = w * 0.07f),
    )
}

private fun DrawScope.drawLoaf(color: Color) {
    val w = size.width
    val h = size.height
    val path = Path().apply {
        moveTo(w * 0.15f, h * 0.75f)
        lineTo(w * 0.15f, h * 0.5f)
        quadraticBezierTo(w * 0.5f, h * 0.1f, w * 0.85f, h * 0.5f)
        lineTo(w * 0.85f, h * 0.75f)
        close()
    }
    drawPath(path, color = color)
}

private fun DrawScope.drawSun(color: Color) {
    val w = size.width
    val h = size.height
    val center = Offset(w * 0.5f, h * 0.5f)
    val radius = min(w, h) * 0.22f
    drawCircle(color = color, radius = radius, center = center)
    for (i in 0 until 8) {
        val angle = i * (PI / 4)
        val inner = radius * 1.3f
        val outer = radius * 1.9f
        drawLine(
            color = color,
            start = Offset(center.x + (cos(angle) * inner).toFloat(), center.y + (sin(angle) * inner).toFloat()),
            end = Offset(center.x + (cos(angle) * outer).toFloat(), center.y + (sin(angle) * outer).toFloat()),
            strokeWidth = w * 0.05f,
        )
    }
}

private fun DrawScope.drawCap(color: Color) {
    val w = size.width
    val h = size.height
    drawArc(
        color = color,
        startAngle = 180f,
        sweepAngle = 180f,
        useCenter = true,
        topLeft = Offset(w * 0.15f, h * 0.2f),
        size = Size(w * 0.7f, h * 0.5f),
    )
    drawRoundRect(
        color = color,
        topLeft = Offset(w * 0.55f, h * 0.55f),
        size = Size(w * 0.4f, h * 0.1f),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.05f),
    )
}

private fun DrawScope.drawDrum(color: Color) {
    val w = size.width
    val h = size.height
    drawRoundRect(
        color = color,
        topLeft = Offset(w * 0.2f, h * 0.25f),
        size = Size(w * 0.6f, h * 0.5f),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.08f),
    )
    drawOval(color = color, topLeft = Offset(w * 0.2f, h * 0.15f), size = Size(w * 0.6f, h * 0.2f))
}

private fun DrawScope.drawBall(color: Color) {
    val w = size.width
    val h = size.height
    val center = Offset(w * 0.5f, h * 0.5f)
    val radius = min(w, h) * 0.36f
    drawCircle(color = color, radius = radius, center = center)
    drawArc(
        color = KidPalette.OnSurface.copy(alpha = 0.5f),
        startAngle = 20f,
        sweepAngle = 140f,
        useCenter = false,
        topLeft = Offset(center.x - radius, center.y - radius),
        size = Size(radius * 2f, radius * 2f),
        style = androidx.compose.ui.graphics.drawscope.Stroke(width = w * 0.03f),
    )
}
