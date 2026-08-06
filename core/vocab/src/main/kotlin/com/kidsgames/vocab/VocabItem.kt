package com.kidsgames.vocab

/**
 * One picture-word-recording triple shared by :games:whatisit and
 * :games:talktime. One catalogue, one voice, one format — see
 * VocabCatalogue.kt for why this lives here instead of in either game.
 *
 * [image] and [audio] are bundled resource ids. In this checkout they are
 * placeholder ids (see PlaceholderAssets.kt) because no artwork or recorded
 * audio exists yet; the shape of the data is real, the assets are not.
 */
data class VocabItem(
    val id: String,
    val image: Int,
    val audio: Int,
    val word: String,
    val sector: Sector,
    val tier: Int,
)

enum class Sector {
    ANIMALS, FRUITS, VEGETABLES, VEHICLES, BODY, CLOTHES,
    HOUSEHOLD, FOOD, NATURE, JOBS, INSTRUMENTS, SPORTS
}

/**
 * A daily sentence, spoken aloud alongside a picture, for :games:talktime.
 */
data class Sentence(
    val text: String,
    val audio: Int,
    val image: Int,
)
