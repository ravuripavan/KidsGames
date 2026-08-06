package com.kidsgames.designkit

import androidx.compose.ui.graphics.Color

/**
 * High-saturation, high-contrast palette. Colour is never the sole carrier of
 * meaning anywhere in the suite, since colour vision deficiency is
 * undiagnosed at ages 4-6.
 */
object KidPalette {
    val Red = Color(0xFFE53935)
    val Orange = Color(0xFFFB8C00)
    val Yellow = Color(0xFFFDD835)
    val Green = Color(0xFF43A047)
    val Blue = Color(0xFF1E88E5)
    val Purple = Color(0xFF8E24AA)
    val Pink = Color(0xFFEC407A)

    val Background = Color(0xFFFFFDF7)
    val Surface = Color(0xFFFFFFFF)
    val OnSurface = Color(0xFF2A2A2A)

    /** Ordered set used wherever a game needs several distinguishable hues. */
    val Swatch = listOf(Red, Orange, Yellow, Green, Blue, Purple, Pink)
}
