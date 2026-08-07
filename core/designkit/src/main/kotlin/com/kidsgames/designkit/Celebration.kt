package com.kidsgames.designkit

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import android.provider.Settings
import android.content.Context

/**
 * The single shared success moment used by every game, so success feels
 * identical everywhere: a star-burst scale-pop, silent unless [SoundBank] is
 * wired in by the caller. [big] is the distinct, larger celebration reserved
 * for reaching level 5.
 *
 * Honours the system animation scale: when animations are disabled
 * system-wide, the celebration appears instantly rather than not at all,
 * because a child must still get positive feedback.
 */
@Composable
fun Celebration(visible: Boolean, big: Boolean = false) {
    val context = LocalContext.current
    val animationsEnabled = remember(context) { systemAnimationsEnabled(context) }

    val targetScale = if (visible) (if (big) 1.4f else 1f) else 0f
    val scale by animateFloatAsState(
        targetValue = targetScale,
        animationSpec = if (animationsEnabled) tween(durationMillis = if (big) 500 else 300) else tween(durationMillis = 0),
        label = "celebration-scale",
    )

    if (scale > 0f) {
        Box(
            modifier = Modifier
                .size(if (big) 160.dp else 96.dp)
                .scale(scale)
                .background(KidPalette.Yellow, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (big) {
                Row {
                    Star(size = 48.dp)
                    Star(size = 48.dp)
                    Star(size = 48.dp)
                }
            } else {
                Star(size = 56.dp)
            }
        }
    }
}

/**
 * A five-point star drawn on a [Canvas], not typeset. A glyph such as "⭐"
 * renders inconsistently across devices, fonts, and emoji sets — different
 * shape, colour, and even presence depending on what font falls back for it.
 * Drawing the star as a vector path guarantees identical, predictable
 * rendering everywhere.
 */
@Composable
private fun Star(size: androidx.compose.ui.unit.Dp) {
    Canvas(modifier = Modifier.size(size)) {
        drawStar()
    }
}

private fun DrawScope.drawStar() {
    val points = 5
    val outerRadius = min(size.width, size.height) / 2f
    val innerRadius = outerRadius * 0.4f
    val center = Offset(size.width / 2f, size.height / 2f)
    val path = Path()
    val angleStep = PI / points

    for (i in 0 until points * 2) {
        val radius = if (i % 2 == 0) outerRadius else innerRadius
        val angle = -PI / 2 + i * angleStep
        val x = center.x + (radius * cos(angle)).toFloat()
        val y = center.y + (radius * sin(angle)).toFloat()
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()

    drawPath(path, color = KidPalette.OnSurface)
}

private fun systemAnimationsEnabled(context: Context): Boolean {
    val scale = Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
    return scale > 0f
}
