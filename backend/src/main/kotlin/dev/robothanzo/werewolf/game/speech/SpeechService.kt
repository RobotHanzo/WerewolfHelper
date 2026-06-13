package dev.robothanzo.werewolf.game.speech

import org.springframework.stereotype.Service

/**
 * Pure speech-ordering logic: build the seat queue, advance speakers, and decide when an interrupt
 * vote has carried. Timing/audio live in the scheduler; this class owns only the sequencing rules.
 */
@Service
class SpeechService {

    /**
     * Build the speaking order: alive seats only, starting at [from], wrapping around in [direction]
     * (DOWN = ascending seat numbers, UP = descending), exactly one pass.
     */
    fun buildOrder(aliveSeats: Collection<Int>, from: Int, direction: SpeechDirection): List<Int> {
        val alive = aliveSeats.distinct().sorted()
        if (alive.isEmpty()) return emptyList()
        val startIdx = alive.indexOf(from).let { if (it < 0) 0 else it }
        val n = alive.size
        return (0 until n).map { k ->
            val idx = when (direction) {
                SpeechDirection.DOWN -> (startIdx + k) % n
                SpeechDirection.UP -> ((startIdx - k) % n + n) % n
            }
            alive[idx]
        }
    }

    fun current(flow: SpeechFlow): Int? = flow.order.getOrNull(flow.index)

    /** Advance to the next speaker; returns the new speaker, or null if the flow is complete. */
    fun advance(flow: SpeechFlow): Int? {
        flow.interruptVotes.clear()
        flow.index += 1
        return current(flow)
    }

    fun isComplete(flow: SpeechFlow): Boolean = flow.index >= flow.order.size

    /**
     * Register an interrupt vote against the current speaker. The turn ends immediately once a
     * **majority of alive players** have voted to cut the speaker off (the speaker doesn't count).
     */
    fun registerInterrupt(flow: SpeechFlow, voter: Int, aliveCount: Int): Boolean {
        val speaker = current(flow)
        if (voter == speaker) return false
        flow.interruptVotes.add(voter)
        return flow.interruptVotes.size >= majorityThreshold(aliveCount)
    }

    /** Strict majority: more than half of the alive players. */
    fun majorityThreshold(aliveCount: Int): Int = aliveCount / 2 + 1
}
