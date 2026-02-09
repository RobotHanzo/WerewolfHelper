package dev.robothanzo.werewolf.service.impl

import dev.robothanzo.werewolf.database.documents.Session
import dev.robothanzo.werewolf.model.Candidate
import dev.robothanzo.werewolf.model.ExpelPoll
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock

class ExpelServiceImplTest {

    private lateinit var expelService: ExpelServiceImpl

    @BeforeEach
    fun setUp() {
        expelService = ExpelServiceImpl()
    }

    @Test
    fun `startExpelPoll adds session to map`() {
        val session = Session().apply { guildId = 123456789L }
        expelService.startExpelPoll(session, 60)

        val expelSession = expelService.getExpelSession(123456789L)
        assertNotNull(expelSession)
        assertEquals(123456789L, expelSession?.guildId)
        val es = expelSession!!
        assertTrue(es.endTime > es.startTime)
    }

    @Test
    fun `endExpelPoll removes session and poll`() {
        val guildId = 123456789L
        val session = Session().apply { this.guildId = guildId }
        expelService.startExpelPoll(session, 60)

        // Add a poll manually
        val poll: ExpelPoll = mock()
        expelService.polls[guildId] = poll

        expelService.endExpelPoll(guildId)

        assertNull(expelService.getExpelSession(guildId))
        assertNull(expelService.getPoll(guildId))
    }

    @Test
    fun `setPollCandidates updates or creates poll`() {
        val guildId = 123456789L
        val candidates = mutableMapOf(1 to Candidate(mock()))

        expelService.setPollCandidates(guildId, candidates)

        val poll = expelService.getPoll(guildId)
        assertNotNull(poll)
        assertEquals(1, poll?.candidates?.size)
    }

    @Test
    fun `removePoll removes poll only`() {
        val guildId = 123456789L
        val session = Session().apply { this.guildId = guildId }
        expelService.startExpelPoll(session, 60)

        val poll: ExpelPoll = mock()
        expelService.polls[guildId] = poll

        expelService.removePoll(guildId)

        assertNull(expelService.getPoll(guildId))
        assertNotNull(expelService.getExpelSession(guildId))
    }

    @Test
    fun `getExpelStatus returns null when no session or poll`() {
        val status = expelService.getExpelStatus(999999L)
        assertNull(status)
    }

    @Test
    fun `getExpelStatus returns session-based status with empty voters`() {
        val guildId = 222222L
        val session = Session().apply { this.guildId = guildId }
        // Create expel session record with candidates
        val expelSession = dev.robothanzo.werewolf.model.ExpelSession(
            guildId = guildId,
            startTime = System.currentTimeMillis(),
            endTime = System.currentTimeMillis() + 30000
        )
        expelSession.candidates[1] = Candidate(player = session.addedPlayerForTest(1))
        expelSession.candidates[2] = Candidate(player = session.addedPlayerForTest(2))
        expelService.sessions[guildId] = expelSession

        val status = expelService.getExpelStatus(guildId)
        assertNotNull(status)
        assertTrue(status!!.voting)
        assertNotNull(status.endTime)
        assertEquals(2, status.candidates.size)
        assertTrue(status.candidates.all { it.voters.isEmpty() })
    }

    @Test
    fun `getExpelStatus returns poll-based status with elector counts`() {
        val guildId = 333333L
        val session = Session().apply { this.guildId = guildId }
        // Create poll and add electors
        val poll = ExpelPoll(guildId, 0L, session, null)
        val c1 = Candidate(player = session.addedPlayerForTest(1))
        c1.electors.add(111111111L)
        c1.electors.add(222222222L)
        poll.candidates[c1.player.id] = c1
        expelService.polls[guildId] = poll

        val status = expelService.getExpelStatus(guildId)
        assertNotNull(status)
        assertEquals(1, status!!.candidates.size)
        assertEquals(2, status.candidates.first().voters.size)
    }
}

// Test helpers: add players to a session for candidate creation
private fun Session.addedPlayerForTest(id: Int): dev.robothanzo.werewolf.database.documents.Player {
    val p = dev.robothanzo.werewolf.database.documents.Player().apply { this.id = id }
    this.addPlayer(p)
    return p
}
