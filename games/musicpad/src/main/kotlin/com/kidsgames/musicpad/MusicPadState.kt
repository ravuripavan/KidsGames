package com.kidsgames.musicpad

/**
 * One tap of one pad on one instrument, captured for the level-5
 * record-and-replay tool. [elapsedMillis] is measured from the instant
 * [MusicPadState.startRecording] was called, using a clock the caller
 * supplies -- this class is plain Kotlin and knows nothing about
 * `System.currentTimeMillis()` or Android at all.
 */
data class TapEvent(
    val padId: Int,
    val instrumentIndex: Int,
    val elapsedMillis: Long,
)

/**
 * Plain-Kotlin, unit-testable state machine for the music pad sandbox. No
 * Android imports anywhere in this file.
 *
 * This is a SANDBOX, not a goal-directed game: there is no correct note, no
 * wrong tap, and no fail state of any kind. Every level ADDS a tool (more
 * pads, a second instrument, record-and-replay) -- levels never make
 * anything harder to get right, because nothing here is ever wrong to begin
 * with.
 *
 * - L1: 4 pads, 1 instrument.
 * - L2: 6 pads, 1 instrument.
 * - L3: 8 pads, 1 instrument.
 * - L4: 8 pads, 2 instruments (an instrument switcher becomes available).
 * - L5: 8 pads, 2 instruments, PLUS record-and-replay.
 *
 * Every mutating function ([tapPad], [selectInstrument], [startRecording],
 * [stopRecording], [startReplay], [stopReplay], [replayTap]) returns a NEW
 * [MusicPadState]; none of them mutate the receiver. Callers hold the result
 * in a single `mutableStateOf` and write the return value back, exactly like
 * every other game module in the suite.
 *
 * Recording is always available wherever it is offered, replay always works
 * (including replaying an empty recording, which is simply silent), and
 * there is no way to "lose" or "break" a recording -- starting a new
 * recording simply replaces the previous one, which is the same forgiving
 * shape as every other tool in this game.
 */
data class MusicPadState(
    val level: Int,
    val padCount: Int = padCountFor(level),
    val instrumentCount: Int = instrumentCountFor(level),
    val recordingSupported: Boolean = recordingSupportedFor(level),
    val selectedInstrument: Int = 0,
    /** (instrumentIndex, padId) -> number of times that pad has ever sounded, purely to key one-shot animations. */
    val tapTriggers: Map<Pair<Int, Int>, Int> = emptyMap(),
    /** The most recent tap, for a Composable that only cares about the latest one-shot event. */
    val lastTap: TapEvent? = null,
    val isRecording: Boolean = false,
    val isReplaying: Boolean = false,
    val recordingStartMillis: Long = 0L,
    val recordedEvents: List<TapEvent> = emptyList(),
    /**
     * True right after [startRecording] until the first tap of the new
     * recording arrives. While true, [recordedEvents] still holds the
     * PREVIOUS recording untouched -- an accidental press of record is
     * completely recoverable, because nothing is erased until the child
     * actually starts playing a new tune. The old recording is only
     * discarded by the first [tapPad] call made while this is true.
     */
    val pendingRecordingClear: Boolean = false,
    /**
     * [selectedInstrument] captured the instant [startReplay] was called, so
     * [stopReplay] can put the child back on the instrument they had chosen
     * before pressing play -- a mixed-instrument tune still ends with the
     * child exactly where they left off, not wherever the last replayed tap
     * happened to leave the badge.
     */
    val preReplayInstrument: Int? = null,
) {

    /**
     * A pad on the currently selected instrument sounds. Always succeeds,
     * always animates, and -- if a recording is in progress -- is appended
     * to [recordedEvents]. [atMillis] is an absolute clock reading supplied
     * by the caller; the elapsed offset stored on the event is derived from
     * [recordingStartMillis]. Callers should supply a real monotonic clock
     * reading (not a coarse, once-a-second counter) so that taps made
     * fractions of a second apart -- entirely normal for a young child --
     * are recorded with the gaps they actually had, not rounded together.
     */
    fun tapPad(padId: Int, atMillis: Long = 0L): MusicPadState {
        if (padId !in 0 until padCount) return this
        val instrument = selectedInstrument
        val key = instrument to padId
        val event = TapEvent(padId = padId, instrumentIndex = instrument, elapsedMillis = (atMillis - recordingStartMillis).coerceAtLeast(0L))
        val newRecordedEvents = when {
            !isRecording -> recordedEvents
            pendingRecordingClear -> listOf(event)
            else -> recordedEvents + event
        }
        return copy(
            tapTriggers = tapTriggers + (key to ((tapTriggers[key] ?: 0) + 1)),
            lastTap = event,
            recordedEvents = newRecordedEvents,
            pendingRecordingClear = if (isRecording) false else pendingRecordingClear,
        )
    }

    /**
     * Replays a previously recorded tap: sounds the pad without appending it
     * to any recording. [selectedInstrument] is switched to the event's
     * instrument so the child sees the same instrument that made the sound
     * during recording, even if they left a different instrument selected
     * before pressing play.
     */
    fun replayTap(event: TapEvent): MusicPadState {
        val key = event.instrumentIndex to event.padId
        return copy(
            selectedInstrument = event.instrumentIndex,
            tapTriggers = tapTriggers + (key to ((tapTriggers[key] ?: 0) + 1)),
            lastTap = event,
        )
    }

    /** Switches the active instrument. Out-of-range indices clamp harmlessly; there is nothing to get wrong. */
    fun selectInstrument(index: Int): MusicPadState {
        if (instrumentCount <= 1) return this
        return copy(selectedInstrument = index.coerceIn(0, instrumentCount - 1))
    }

    /**
     * Begins a fresh recording, discarding any previous one. Recording is
     * always available on a level where [recordingSupported] is true --
     * there is no limit on how many times it can be started, and starting
     * one never fails.
     */
    fun startRecording(atMillis: Long = 0L): MusicPadState {
        if (!recordingSupported) return this
        return copy(
            isRecording = true,
            isReplaying = false,
            recordingStartMillis = atMillis,
            pendingRecordingClear = true,
        )
    }

    fun stopRecording(): MusicPadState = if (isRecording) copy(isRecording = false) else this

    /**
     * Begins replay of [recordedEvents]. Works identically whether the
     * recording holds many taps or none at all -- an empty recording simply
     * replays as silence, which is a completely valid, unremarkable outcome.
     */
    fun startReplay(): MusicPadState {
        if (!recordingSupported) return this
        return copy(isReplaying = true, isRecording = false, preReplayInstrument = selectedInstrument)
    }

    /**
     * Ends replay -- whether because every recorded tap finished playing or
     * because the child pressed the replay button again mid-playback -- and
     * restores whichever instrument was selected before [startReplay] was
     * called.
     */
    fun stopReplay(): MusicPadState = if (isReplaying) {
        copy(isReplaying = false, selectedInstrument = preReplayInstrument ?: selectedInstrument, preReplayInstrument = null)
    } else {
        this
    }

    companion object {
        fun padCountFor(level: Int): Int = when {
            level <= 1 -> 4
            level == 2 -> 6
            else -> 8
        }

        fun instrumentCountFor(level: Int): Int = if (level >= 4) 2 else 1

        fun recordingSupportedFor(level: Int): Boolean = level >= 5

        fun initial(level: Int): MusicPadState = MusicPadState(level = level)
    }
}
