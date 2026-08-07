package com.kidsgames.designkit

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

/** The minimum tap target every interactive element in the suite must meet. */
val MinTapTarget = 64.dp

/**
 * The one tappable-thing composable used everywhere in the suite. Enforces
 * the 64dp minimum tap target internally so no game has to remember to.
 *
 * The guarantee is structural, not advisory: the outer node that carries
 * [defaultMinSize] and `clickable` is owned entirely by this composable and
 * never receives the caller's [modifier]. The caller's modifier is applied
 * only to an inner visual layer, so a caller passing e.g.
 * `Modifier.size(40.dp).padding(4.dp)` can shrink how the button *looks* but
 * can never shrink the touch node below [MinTapTarget] — there is no
 * modifier chain a caller can construct that reaches the outer node's
 * constraints.
 *
 * Note for tests: `testTag()` semantics deliberately do not merge upward in
 * Compose, so a `testTag` placed on [modifier] (which lands on the inner
 * visual layer) will not resolve to the outer, real-touch-target node. Use
 * the [testTag] parameter to tag the outer node directly when a test needs
 * to assert on the actual tap target.
 */
@Composable
fun KidButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    testTag: String? = null,
    content: @Composable () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    KidTapHaptics(interactionSource)
    Box(
        // Outer node: owns the tap target guarantee and the click handling.
        // Deliberately does NOT incorporate the caller's modifier, so no
        // combination of size/padding modifiers passed in can defeat
        // defaultMinSize by fixing this node's constraints upstream.
        modifier = Modifier
            .defaultMinSize(minWidth = MinTapTarget, minHeight = MinTapTarget)
            .background(KidPalette.Surface, RoundedCornerShape(16.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .let { if (testTag != null) it.testTag(testTag) else it },
        contentAlignment = Alignment.Center,
    ) {
        // Inner node: purely visual. The caller's modifier lands here, where
        // it can affect appearance and layout within the button but cannot
        // reach the outer node's constraints or its clickable.
        Box(
            modifier = modifier,
            contentAlignment = Alignment.Center,
        ) {
            content()
        }
    }
}
