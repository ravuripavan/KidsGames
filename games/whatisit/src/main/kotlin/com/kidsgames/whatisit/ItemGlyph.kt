package com.kidsgames.whatisit

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.scale
import com.kidsgames.designkit.KidPalette
import com.kidsgames.vocab.Sector
import com.kidsgames.vocab.VocabItem
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * No real artwork exists yet for [VocabCatalogue] (see PlaceholderAssets.kt
 * in :core:vocab) -- every [VocabItem.image] is a placeholder integer, not a
 * resolvable drawable. This draws a simple vector stand-in per item instead
 * of leaving a blank box: a broad silhouette per [Sector] (a paw for
 * animals, a car body for vehicles, and so on), coloured from the shared
 * [KidPalette.Swatch], THEN varied per-ITEM using [ItemVariant.forItem] --
 * proportion, mirroring, decoration dots, and an accent mark all derive
 * deterministically from the item's own id. Two items sharing a sector
 * (e.g. dog and bird, both [Sector.ANIMALS]) therefore never render as the
 * same picture: shape sets the sector, the per-item variant is what makes
 * THIS item tellable apart from every other item the child could confuse it
 * with -- see [visualDescriptor] and the uniqueness tests in
 * WhatIsItStateTest and ItemVariantTest.
 */
@Composable
fun ItemGlyph(item: VocabItem, modifier: Modifier = Modifier) {
    val baseColor = KidPalette.Swatch[item.sector.ordinal % KidPalette.Swatch.size]
    val variant = ItemVariant.forItem(item)
    val accentColor = KidPalette.Swatch[variant.swatchIndex % KidPalette.Swatch.size]
    Canvas(modifier = modifier) {
        drawItemVariant(item.sector, variant, baseColor, accentColor)
    }
}

/**
 * Draws the sector's base silhouette with per-item proportion scaling and
 * optional horizontal mirroring, then overlays [ItemVariant.featureCount]
 * decoration dots and one of four [ItemVariant.accentKey] accent marks in
 * [accentColor]. Colour is never the SOLE carrier here -- shape (proportion,
 * mirroring, dot count, accent shape) all vary too.
 */
private fun DrawScope.drawItemVariant(
    sector: Sector,
    variant: ItemVariant,
    baseColor: Color,
    accentColor: Color,
) {
    // 0 squat, 1 neutral, 2 tall -- a visible proportion difference even
    // when two items share every other variant field.
    val (scaleX, scaleY) = when (variant.proportionKey) {
        0 -> 0.85f to 1.12f
        2 -> 1.12f to 0.85f
        else -> 1f to 1f
    }
    val mirrorX = if (variant.mirrored) -1f else 1f
    scale(scaleX = scaleX * mirrorX, scaleY = scaleY, pivot = Offset(size.width / 2f, size.height / 2f)) {
        drawSectorGlyph(sector, baseColor)
    }
    drawFeatureDots(variant.featureCount, accentColor)
    drawAccentMark(variant.accentKey, accentColor)
}

/** 0..3 small dots along the top edge -- a per-item decoration, not a sector marker. */
private fun DrawScope.drawFeatureDots(count: Int, color: Color) {
    if (count <= 0) return
    val w = size.width
    val h = size.height
    val dotRadius = w * 0.045f
    for (i in 0 until count) {
        val x = w * (0.3f + i * 0.2f)
        drawCircle(color = color, radius = dotRadius, center = Offset(x, h * 0.08f))
    }
}

/** One of four small accent shapes in a fixed corner, keyed off the item -- a visible extra differentiator beyond dots and proportion. */
private fun DrawScope.drawAccentMark(key: Int, color: Color) {
    val w = size.width
    val h = size.height
    when (key) {
        0 -> Unit
        1 -> drawLine(
            color = color,
            start = Offset(w * 0.05f, h * 0.95f),
            end = Offset(w * 0.25f, h * 0.95f),
            strokeWidth = w * 0.05f,
        )
        2 -> drawCircle(color = color, radius = w * 0.07f, center = Offset(w * 0.9f, h * 0.1f))
        else -> {
            val path = Path().apply {
                moveTo(w * 0.85f, h * 0.95f)
                lineTo(w * 0.98f, h * 0.95f)
                lineTo(w * 0.98f, h * 0.82f)
                close()
            }
            drawPath(path, color = color)
        }
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
    drawCircle(color = color, radius = radius, center = center, style = Fill)
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
