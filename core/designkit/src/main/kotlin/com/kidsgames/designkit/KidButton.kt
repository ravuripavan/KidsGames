package com.kidsgames.designkit

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
 * [modifier] and [layoutModifier] are NOT interchangeable: [layoutModifier]
 * is applied to the outer, real-touch-target node — use it for any
 * parent-data or layout modifier (`weight`, `align`, `fillMaxWidth`,
 * `aspectRatio`) that must actually influence how the parent measures and
 * places this button — while [modifier] only styles the inner visual layer
 * and is invisible to the parent's measurement pass.
 *
 * The min-tap-target guarantee is structural, not advisory: [layoutModifier]
 * is placed *before* [defaultMinSize] in the outer chain, so it can supply
 * parent data and alter how the parent sizes this node, but it cannot
 * override the `defaultMinSize` constraint applied afterward in the same
 * chain — there is no modifier a caller can pass in [layoutModifier] or
 * [modifier] that shrinks the touch node below [MinTapTarget].
 *
 * Note for tests: `testTag()` semantics deliberately do not merge upward in
 * Compose, so a `testTag` placed on [modifier] (which lands on the inner
 * visual layer) will not resolve to the outer, real-touch-target node. Use
 * the [testTag] parameter to tag the outer node directly when a test needs
 * to assert on the actual tap target.
 *
 * [onLongClick], when supplied, fires on a press-and-hold instead of adding a
 * second independent gesture detector: it is wired through
 * [androidx.compose.foundation.combinedClickable] on the very same outer
 * node, so a long press can never be interpreted as a tap-then-something-else
 * or fight the tap gesture for the pointer. Left `null` (the default) this
 * behaves exactly as a plain tap target, unchanged from every existing call
 * site.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun KidButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    layoutModifier: Modifier = Modifier,
    testTag: String? = null,
    onLongClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    KidTapHaptics(interactionSource)
    Box(
        // Outer node: owns the tap target guarantee and the click handling.
        // layoutModifier is applied first so a caller's parent-data/layout
        // modifiers (weight, align, fillMaxWidth, aspectRatio) reach the
        // parent's measurement of THIS node — but defaultMinSize is chained
        // after it, so no combination of modifiers passed via layoutModifier
        // (or via modifier, which never reaches this node at all) can defeat
        // the minimum tap target.
        modifier = layoutModifier
            .defaultMinSize(minWidth = MinTapTarget, minHeight = MinTapTarget)
            .background(KidPalette.Surface, RoundedCornerShape(16.dp))
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onLongClick = onLongClick,
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
