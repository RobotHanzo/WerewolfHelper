package dev.robothanzo.werewolf.game.speech

/** Speaking direction chosen by the police (or random with no police). */
enum class SpeechDirection { UP, DOWN }

/**
 * One day's speech flow. [order] is the resolved seat sequence (alive seats only, wrapping from the
 * starting seat in the chosen direction); [index] is the current speaker. [waiting] means the flow
 * is parked on the direction choice.
 */
data class SpeechFlow(
    val order: List<Int>,
    var index: Int = 0,
    val direction: SpeechDirection,
    var endsAt: Long? = null,
    var waiting: Boolean = false,
    val from: Int,
    val interruptVotes: MutableSet<Int> = linkedSetOf(),
    /** Single-speaker session (e.g. last words) does not advance through a queue. */
    val singleSpeaker: Boolean = false,
)
