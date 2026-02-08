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
}
