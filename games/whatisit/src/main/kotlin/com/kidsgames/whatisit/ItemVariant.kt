package com.kidsgames.whatisit

import com.kidsgames.vocab.VocabItem

/**
 * No real artwork exists for [VocabItem.image] (see PlaceholderAssets.kt in
 * :core:vocab) -- every value is a placeholder integer, not a resolvable
 * drawable. [ItemGlyph] therefore draws a procedural stand-in per item, and
 * this file is the plain-Kotlin (no Android imports, unit-testable) part of
 * that: a deterministic set of visual-variation knobs derived from the
 * item's OWN identity ([VocabItem.id]), not merely its [VocabItem.sector].
 *
 * Two items in the same sector share a base silhouette (drawn in
 * [ItemGlyph]) but get different [ItemVariant]s, so they render as visibly
 * different pictures -- a paw with two spots and a mirrored tail accent
 * reads differently from a plain unmirrored paw with no spots. This is what
 * makes the "what is it?" question answerable: every item the child can be
 * asked about looks different from every other item they could confuse it
 * with.
 */
data class ItemVariant(
    /** 0..2: scales the silhouette's width/height ratio (squat / neutral / tall). */
    val proportionKey: Int,
    /** 0..3: how many small decoration marks (spots/dots) are drawn on the shape. */
    val featureCount: Int,
    /** 0..3: which accent shape (tail/ear/stripe/none) is added, and where. */
    val accentKey: Int,
    /** Whether the whole glyph is horizontally mirrored. */
    val mirrored: Boolean,
    /** Index into [com.kidsgames.designkit.KidPalette.Swatch] for the accent colour only -- never the sole carrier of meaning, since shape already varies. */
    val swatchIndex: Int,
) {
    companion object {

        /**
         * Derives a stable [ItemVariant] from [item].id alone, so the same
         * item always renders the same way across recompositions and
         * sessions, but different items -- even in the same sector --
         * reliably diverge. Uses a handful of large odd multipliers to
         * spread bits from a short id string across independent fields
         * rather than reusing the same low bits for every field.
         */
        fun forItem(item: VocabItem): ItemVariant {
            val h = stableHash(item.id)
            return ItemVariant(
                proportionKey = ((h ushr 2) and 0x3).let { it % 3 },
                featureCount = ((h ushr 5) and 0x3),
                accentKey = ((h ushr 9) and 0x3),
                mirrored = ((h ushr 13) and 0x1) == 1,
                swatchIndex = ((h ushr 15) and 0xFF),
            )
        }

        /**
         * A deterministic string hash independent of [String.hashCode]'s
         * platform-defined (though currently stable) behaviour, so this
         * module never depends on JVM/Android hashCode implementation
         * details for something that must never silently change.
         */
        private fun stableHash(s: String): Int {
            var h = 0x811C9DC5.toInt() // FNV-1a offset basis
            for (c in s) {
                h = h xor c.code
                h *= 0x01000193 // FNV-1a prime
            }
            return h and 0x7FFFFFFF
        }
    }
}

/**
 * A visible-difference fingerprint for [item]: its sector (base silhouette)
 * combined with the tuple of variation knobs that actually change what gets
 * drawn. Two items with the same [visualDescriptor] would render as the
 * same picture and are therefore indistinguishable to the child -- see the
 * uniqueness tests in WhatIsItStateTest and ItemVariantTest.
 */
fun visualDescriptor(item: VocabItem): List<Any> {
    val v = ItemVariant.forItem(item)
    return listOf(item.sector, v.proportionKey, v.featureCount, v.accentKey, v.mirrored, v.swatchIndex)
}
