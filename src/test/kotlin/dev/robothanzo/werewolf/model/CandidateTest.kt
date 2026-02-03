package dev.robothanzo.werewolf.model

import dev.robothanzo.werewolf.database.documents.Session
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class CandidateTest {
    private lateinit var player: Session.Player
    private lateinit var candidate: Candidate

    @BeforeEach
    fun setup() {
        player = Session.Player(
            id = 1,
            roleId = 123L,
            channelId = 456L,
            userId = 789L
        )
        candidate = Candidate(player = player)
    }

    @Test
    fun testCandidateInitialization() {
        assertEquals(player, candidate.player)
        assertFalse(candidate.expelPK)
        assertTrue(candidate.electors.isEmpty())
        assertFalse(candidate.quit)
    }

    @Test
    fun testAddElector() {
        candidate.electors.add(100L)
        candidate.electors.add(200L)
        assertEquals(2, candidate.electors.size)
        assertTrue(candidate.electors.contains(100L))
        assertTrue(candidate.electors.contains(200L))
    }

    @Test
    fun testGetElectorsAsMention() {
        candidate.electors.add(100L)
        candidate.electors.add(200L)
        val mentions = candidate.getElectorsAsMention()
        assertEquals(2, mentions.size)
        assertTrue(mentions.contains("<@!100>"))
        assertTrue(mentions.contains("<@!200>"))
    }

    @Test
    fun testGetVotesWithoutPolice() {
        candidate.electors.add(100L)
        candidate.electors.add(200L)
        val votes = candidate.getVotes(null)
        assertEquals(2f, votes)
    }

    @Test
    fun testGetVotesWithPoliceVoting() {
        val police = Session.Player(id = 2, roleId = 124L, channelId = 457L, userId = 790L)
        candidate.electors.add(100L)
        candidate.electors.add(790L) // Police voted
        val votes = candidate.getVotes(police)
        assertEquals(2.5f, votes)
    }

    @Test
    fun testGetVotesWithPoliceNotVoting() {
        val police = Session.Player(id = 2, roleId = 124L, channelId = 457L, userId = 790L)
        candidate.electors.add(100L)
        candidate.electors.add(200L)
        val votes = candidate.getVotes(police)
        assertEquals(2f, votes)
    }

    @Test
    fun testGetVotesWhenQuit() {
        candidate.electors.add(100L)
        candidate.electors.add(200L)
        candidate.quit = true
        val votes = candidate.getVotes(null)
        assertEquals(0f, votes)
    }

    @Test
    fun testGetComparator() {
        val candidate1 = Candidate(player = Session.Player(id = 1, roleId = 1L, channelId = 1L))
        val candidate2 = Candidate(player = Session.Player(id = 2, roleId = 2L, channelId = 2L))
        val candidate3 = Candidate(player = Session.Player(id = 3, roleId = 3L, channelId = 3L))

        val comparator = Candidate.getComparator()
        val sorted = listOf(candidate3, candidate1, candidate2).sortedWith(comparator)

        assertEquals(1, sorted[0].player.id)
        assertEquals(2, sorted[1].player.id)
        assertEquals(3, sorted[2].player.id)
    }

    @Test
    fun testGetWinnerSingleCandidate() {
        candidate.electors.add(100L)
        candidate.electors.add(200L)

        val candidates = listOf(candidate)
        val winners = Candidate.getWinner(candidates, null)

        assertEquals(1, winners.size)
        assertEquals(candidate, winners[0])
    }

    @Test
    fun testGetWinnerMultipleCandidatesWithClear() {
        val candidate1 = Candidate(player = Session.Player(id = 1, roleId = 1L, channelId = 1L))
        val candidate2 = Candidate(player = Session.Player(id = 2, roleId = 2L, channelId = 2L))

        candidate1.electors.add(100L)
        candidate1.electors.add(200L)
        candidate2.electors.add(300L)

        val candidates = listOf(candidate1, candidate2)
        val winners = Candidate.getWinner(candidates, null)

        assertEquals(1, winners.size)
        assertEquals(candidate1, winners[0])
    }

    @Test
    fun testGetWinnerTie() {
        val candidate1 = Candidate(player = Session.Player(id = 1, roleId = 1L, channelId = 1L))
        val candidate2 = Candidate(player = Session.Player(id = 2, roleId = 2L, channelId = 2L))

        candidate1.electors.add(100L)
        candidate1.electors.add(200L)
        candidate2.electors.add(300L)
        candidate2.electors.add(400L)

        val candidates = listOf(candidate1, candidate2)
        val winners = Candidate.getWinner(candidates, null)

        assertEquals(2, winners.size)
        assertTrue(winners.contains(candidate1))
        assertTrue(winners.contains(candidate2))
    }

    @Test
    fun testGetWinnerWithZeroVotes() {
        val candidate1 = Candidate(player = Session.Player(id = 1, roleId = 1L, channelId = 1L))
        val candidate2 = Candidate(player = Session.Player(id = 2, roleId = 2L, channelId = 2L))

        candidate1.quit = true
        candidate2.electors.add(300L)

        val candidates = listOf(candidate1, candidate2)
        val winners = Candidate.getWinner(candidates, null)

        assertEquals(1, winners.size)
        assertEquals(candidate2, winners[0])
    }

    @Test
    fun testGetWinnerAllQuit() {
        candidate.quit = true

        val candidates = listOf(candidate)
        val winners = Candidate.getWinner(candidates, null)

        assertEquals(0, winners.size)
    }
}
