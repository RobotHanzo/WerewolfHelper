package dev.robothanzo.werewolf.game.vote

import org.springframework.stereotype.Service

/**
 * Provides the per-seat facts the engine needs, without coupling it to the session document — so
 * the voting rules stay pure and testable.
 */
interface PollContext {
    /** Poll weight: 1.5 for the police, 1.0 otherwise (FEATURES §7). */
    fun weightOf(seat: Int): Double

    fun isAlive(seat: Int): Boolean

    /** A dead 白癡 stays revealed in play but loses its vote (FEATURES §8). */
    fun isDeadIdiot(seat: Int): Boolean
}

/**
 * The one voting state machine behind both the police election and the expel poll. Enrollment and
 * withdrawal are **stage-gated** (a past bug let players slip in/out after voting opened); votes use
 * switch/retract semantics; ties give exactly one PK runoff.
 */
@Service
class PollEngine {

    /** Toggle enrollment — only while the enrollment stage is open. Returns true if the state changed. */
    fun enroll(poll: Poll, seat: Int): Boolean {
        if (poll.stage != PollStage.ENROLL) return false
        return if (seat in poll.candidates) poll.candidates.remove(seat) else poll.candidates.add(seat)
    }

    /** Withdraw a candidate — only while the withdrawal stage is open. */
    fun withdraw(poll: Poll, seat: Int): Boolean {
        if (poll.stage != PollStage.WITHDRAW) return false
        if (seat !in poll.candidates) return false
        return poll.withdrawn.add(seat)
    }

    /** Whether [seat] may cast a vote in this poll right now. */
    fun canVote(poll: Poll, ctx: PollContext, seat: Int): Boolean {
        if (poll.stage != PollStage.VOTING) return false
        if (!ctx.isAlive(seat) || ctx.isDeadIdiot(seat)) return false
        // In a police election the candidates themselves do not vote.
        if (poll.kind == PollKind.POLICE && seat in poll.activeCandidates()) return false
        return true
    }

    /**
     * Cast, switch, or retract a vote. Clicking a different candidate switches; clicking the same
     * candidate again retracts. Returns true if accepted.
     */
    fun castVote(poll: Poll, ctx: PollContext, voter: Int, candidate: Int): Boolean {
        if (!canVote(poll, ctx, voter)) return false
        if (candidate !in poll.activeCandidates()) return false
        if (poll.votes[voter] == candidate) {
            poll.votes.remove(voter) // retract
        } else {
            poll.votes[voter] = candidate
        }
        return true
    }

    /** Weighted tally per active candidate. */
    fun tally(poll: Poll, ctx: PollContext): Map<Int, Double> {
        val result = poll.activeCandidates().associateWith { 0.0 }.toMutableMap()
        for ((voter, candidate) in poll.votes) {
            if (candidate in result) result[candidate] = result.getValue(candidate) + ctx.weightOf(voter)
        }
        return result
    }

    /** Voter seat numbers backing each candidate (for the dashboard's voter chips). */
    fun voters(poll: Poll): Map<Int, List<Int>> =
        poll.votes.entries.groupBy({ it.value }, { it.key })

    /** Resolve the enrollment stage of a police election (FEATURES §7 resolution row). */
    fun resolveEnrollment(poll: Poll): PollResolution {
        val active = poll.activeCandidates()
        return when (active.size) {
            0 -> PollResolution(PollOutcome.BADGE_DESTROYED)
            1 -> PollResolution(PollOutcome.ELECTED, winner = active.first())
            else -> PollResolution(PollOutcome.PK_NEEDED, tied = active) // proceed to campaign/vote
        }
    }

    /** Resolve the voting stage: a winner, a tie needing one runoff, or no result. */
    fun resolveVotes(poll: Poll, ctx: PollContext): PollResolution {
        val tally = tally(poll, ctx)
        val max = tally.values.maxOrNull() ?: 0.0
        if (max <= 0.0) {
            // Nobody voted. A police election with no decision destroys the badge; an expel poll passes.
            return if (poll.kind == PollKind.POLICE) {
                PollResolution(PollOutcome.BADGE_DESTROYED, tally = tally)
            } else {
                PollResolution(PollOutcome.NO_RESULT, tally = tally)
            }
        }
        val leaders = tally.filterValues { it == max }.keys
        if (leaders.size == 1) {
            val outcome = if (poll.kind == PollKind.POLICE) PollOutcome.ELECTED else PollOutcome.EXPELLED
            return PollResolution(outcome, winner = leaders.first(), tally = tally)
        }
        // Tie. Exactly one runoff is allowed; a second tie ends without result.
        return if (!poll.pkRound) {
            PollResolution(PollOutcome.PK_NEEDED, tied = leaders, tally = tally)
        } else {
            PollResolution(PollOutcome.NO_RESULT, tied = leaders, tally = tally)
        }
    }

    /** Enter the single PK runoff among [tied], clearing prior votes. */
    fun startPkRound(poll: Poll, tied: Set<Int>) {
        poll.pkRound = true
        poll.pkCandidates = tied
        poll.votes.clear()
        poll.stage = PollStage.VOTING
    }
}
