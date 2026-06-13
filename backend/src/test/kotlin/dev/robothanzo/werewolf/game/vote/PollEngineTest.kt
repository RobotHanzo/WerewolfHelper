package dev.robothanzo.werewolf.game.vote

import dev.robothanzo.werewolf.game.GameConstants.POLICE_VOTE_WEIGHT
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PollEngineTest {

    private val engine = PollEngine()

    /** Test context: [police] votes 1.5; everyone alive; [deadIdiots] excluded from voting. */
    private fun ctx(police: Int? = null, deadIdiots: Set<Int> = emptySet(), dead: Set<Int> = emptySet()) =
        object : PollContext {
            override fun weightOf(seat: Int) = if (seat == police) POLICE_VOTE_WEIGHT else 1.0
            override fun isAlive(seat: Int) = seat !in dead
            override fun isDeadIdiot(seat: Int) = seat in deadIdiots
        }

    private fun votingPoll(kind: PollKind, vararg candidates: Int) =
        Poll(kind = kind, stage = PollStage.VOTING, candidates = candidates.toSortedSet())

    @Test
    fun `enrollment is stage-gated`() {
        val poll = Poll(PollKind.POLICE, PollStage.ENROLL)
        assertTrue(engine.enroll(poll, 2))
        assertTrue(2 in poll.candidates)
        // toggling again withdraws the enrollment
        assertTrue(engine.enroll(poll, 2))
        assertFalse(2 in poll.candidates)

        poll.stage = PollStage.VOTING
        assertFalse(engine.enroll(poll, 5)) // cannot slip in once voting opens
    }

    @Test
    fun `withdrawal only allowed in the withdraw stage`() {
        val poll = Poll(PollKind.POLICE, PollStage.CAMPAIGN, candidates = sortedSetOf(2, 5))
        assertFalse(engine.withdraw(poll, 2))
        poll.stage = PollStage.WITHDRAW
        assertTrue(engine.withdraw(poll, 2))
        assertTrue(2 in poll.withdrawn)
        assertFalse(2 in poll.activeCandidates())
    }

    @Test
    fun `police vote counts 1_5`() {
        val poll = votingPoll(PollKind.EXPEL, 3, 7)
        val ctx = ctx(police = 1)
        engine.castVote(poll, ctx, voter = 1, candidate = 3) // police
        engine.castVote(poll, ctx, voter = 2, candidate = 3) // normal
        val tally = engine.tally(poll, ctx)
        assertEquals(2.5, tally[3])
        assertEquals(0.0, tally[7])
    }

    @Test
    fun `switching and retracting votes`() {
        val poll = votingPoll(PollKind.EXPEL, 3, 7)
        val ctx = ctx()
        engine.castVote(poll, ctx, voter = 2, candidate = 3)
        assertEquals(3, poll.votes[2])
        // switch
        engine.castVote(poll, ctx, voter = 2, candidate = 7)
        assertEquals(7, poll.votes[2])
        // retract (same candidate again)
        engine.castVote(poll, ctx, voter = 2, candidate = 7)
        assertFalse(poll.votes.containsKey(2))
    }

    @Test
    fun `candidates do not vote in their own police election`() {
        val poll = votingPoll(PollKind.POLICE, 3, 7)
        val ctx = ctx()
        assertFalse(engine.canVote(poll, ctx, 3)) // 3 is a candidate
        assertTrue(engine.canVote(poll, ctx, 5))
    }

    @Test
    fun `dead idiot cannot vote in an expel poll`() {
        val poll = votingPoll(PollKind.EXPEL, 3, 7)
        val ctx = ctx(deadIdiots = setOf(9))
        assertFalse(engine.canVote(poll, ctx, 9))
    }

    @Test
    fun `enrollment resolution - none, one, many`() {
        assertEquals(PollOutcome.BADGE_DESTROYED, engine.resolveEnrollment(Poll(PollKind.POLICE, PollStage.ENROLL)).outcome)

        val one = Poll(PollKind.POLICE, PollStage.ENROLL, candidates = sortedSetOf(4))
        val r1 = engine.resolveEnrollment(one)
        assertEquals(PollOutcome.ELECTED, r1.outcome)
        assertEquals(4, r1.winner)

        val many = Poll(PollKind.POLICE, PollStage.ENROLL, candidates = sortedSetOf(2, 5))
        assertEquals(PollOutcome.PK_NEEDED, engine.resolveEnrollment(many).outcome)
    }

    @Test
    fun `clear winner is elected or expelled`() {
        val poll = votingPoll(PollKind.EXPEL, 3, 7)
        val ctx = ctx()
        engine.castVote(poll, ctx, 1, 3)
        engine.castVote(poll, ctx, 2, 3)
        engine.castVote(poll, ctx, 4, 7)
        val r = engine.resolveVotes(poll, ctx)
        assertEquals(PollOutcome.EXPELLED, r.outcome)
        assertEquals(3, r.winner)
    }

    @Test
    fun `tie triggers exactly one PK runoff`() {
        val poll = votingPoll(PollKind.EXPEL, 3, 7)
        val ctx = ctx()
        engine.castVote(poll, ctx, 1, 3)
        engine.castVote(poll, ctx, 2, 7)
        val first = engine.resolveVotes(poll, ctx)
        assertEquals(PollOutcome.PK_NEEDED, first.outcome)
        assertEquals(setOf(3, 7), first.tied)

        engine.startPkRound(poll, first.tied)
        // tie again in the runoff
        engine.castVote(poll, ctx, 1, 3)
        engine.castVote(poll, ctx, 2, 7)
        val second = engine.resolveVotes(poll, ctx)
        assertEquals(PollOutcome.NO_RESULT, second.outcome)
    }

    @Test
    fun `voters are grouped per candidate`() {
        val poll = votingPoll(PollKind.EXPEL, 3, 7)
        val ctx = ctx()
        engine.castVote(poll, ctx, 1, 3)
        engine.castVote(poll, ctx, 2, 3)
        engine.castVote(poll, ctx, 4, 7)
        val voters = engine.voters(poll)
        assertEquals(listOf(1, 2), voters[3])
        assertEquals(listOf(4), voters[7])
    }
}
