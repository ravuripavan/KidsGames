package com.kidsgames.vocab

/**
 * Deterministic daily rotation for :games:talktime, indexed by day-of-year.
 * Never random, never networked: the same day always yields the same word
 * and sentence, so the activity is stable across restarts within a day and
 * changes only when the day changes.
 */
object DailyPicker {

    fun wordOfDay(dayOfYear: Int): VocabItem {
        val items = VocabCatalogue.items
        val index = ((dayOfYear - 1) % items.size + items.size) % items.size
        return items[index]
    }

    fun sentenceOfDay(dayOfYear: Int): Sentence {
        val pool = SentencePool.sentences
        val index = ((dayOfYear - 1) % pool.size + pool.size) % pool.size
        return pool[index]
    }
}

/**
 * The bundled sentence pool: a full year (365) of ordinary daily-use
 * language with no repeats, over-weighted toward travel phrasing since
 * that is talktime's context of use. Real sentences, real everyday
 * vocabulary. Audio and image ids are placeholders — see
 * PlaceholderAssets.kt — pending real recordings.
 */
internal object SentencePool {

    /** Hand-written phrases: greetings, needs, courtesies, travel talk. */
    private val core: List<String> = listOf(
        "hello",
        "goodbye",
        "please",
        "thank you",
        "you are welcome",
        "good morning",
        "good night",
        "I love you",
        "I am hungry",
        "I am thirsty",
        "I am tired",
        "I am happy",
        "I am sleepy",
        "can I have more",
        "can I have water",
        "can I have a snack",
        "where are we going",
        "are we there yet",
        "I need the bathroom",
        "I want to go home",
        "look out the window",
        "I see a cloud",
        "I see a bird",
        "can we stop please",
        "I want my toy",
        "hold my hand",
        "I did it",
        "help me please",
        "I am okay",
        "that was fun",
        "one more time",
        "my turn now",
        "I am ready",
        "wait for me",
        "watch this",
        "I found it",
        "let us go",
        "come here please",
        "sit down please",
        "buckle your seatbelt",
        "we are almost there",
        "I packed my bag",
        "the sun is up",
        "the moon is out",
        "it is raining",
        "it is sunny today",
        "I like this song",
        "sing with me",
        "can we play a game",
        "I am not scared",
        "good job",
    )

    /**
     * Sentence templates filled with real catalogue words, giving natural
     * variety while staying inside ordinary daily language a 4-6 year old
     * hears and uses.
     */
    private val templates: List<(String) -> String> = listOf(
        { word -> "I see a $word" },
        { word -> "look at the $word" },
        { word -> "I like the $word" },
        { word -> "where is my $word" },
        { word -> "can I have the $word" },
        { word -> "point to the $word" },
        { word -> "that is a big $word" },
        { word -> "I want a $word" },
        { word -> "we have a $word" },
        { word -> "the $word is soft" },
    )

    val sentences: List<Sentence> = buildList {
        core.forEach { text -> add(text) }
        val words = VocabCatalogue.items.map { it.word }
        for (template in templates) {
            for (word in words) {
                add(template(word))
            }
        }
    }
        .distinct()
        .let { texts ->
            // Guarantee 365 unique sentences even if templating produced
            // fewer distinct strings than needed (it does not, but this
            // keeps the invariant explicit rather than accidental).
            require(texts.size >= 365) {
                "sentence pool has ${texts.size} unique entries, need at least 365"
            }
            texts
        }
        .map { text ->
            Sentence(
                text = text,
                audio = PlaceholderAssets.nextAudio(),
                image = PlaceholderAssets.nextImage(),
            )
        }
}
