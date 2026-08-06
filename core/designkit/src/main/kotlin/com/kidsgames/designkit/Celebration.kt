package com.kidsgames.designkit

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
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
            Text(if (big) "⭐⭐⭐" else "⭐")
        }
    }
}

private fun systemAnimationsEnabled(context: Context): Boolean {
    val scale = Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
    return scale > 0f
}
