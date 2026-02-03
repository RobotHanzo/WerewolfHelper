package dev.robothanzo.werewolf.model

import dev.robothanzo.werewolf.database.documents.Session
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class SpeechSessionTest {
    private lateinit var session: Session
    private lateinit var speechSession: SpeechSession

    @BeforeEach
    fun setup() {
        session = Session(guildId = 123L)
        speechSession = SpeechSession(
            guildId = 123L,
            channelId = 456L,
            session = session
        )
    }

    @Test
    fun testSpeechSessionInitialization() {
        assertEquals(123L, speechSession.guildId)
        assertEquals(456L, speechSession.channelId)
        assertEquals(session, speechSession.session)
        assertTrue(speechSession.interruptVotes.isEmpty())
        assertTrue(speechSession.order.isEmpty())
        assertNull(speechSession.speakingThread)
        assertNull(speechSession.lastSpeaker)
        assertNull(speechSession.finishedCallback)
        assertEquals(0L, speechSession.currentSpeechEndTime)
        assertEquals(0, speechSession.totalSpeechTime)
        assertTrue(!speechSession.shouldStopCurrentSpeaker)
    }

    @Test
    fun testAddInterruptVotes() {
        speechSession.interruptVotes.add(100L)
        speechSession.interruptVotes.add(200L)
        speechSession.interruptVotes.add(300L)

        assertEquals(3, speechSession.interruptVotes.size)
        assertTrue(speechSession.interruptVotes.contains(100L))
        assertTrue(speechSession.interruptVotes.contains(200L))
        assertTrue(speechSession.interruptVotes.contains(300L))
    }

    @Test
    fun testRemoveInterruptVotes() {
        speechSession.interruptVotes.add(100L)
        speechSession.interruptVotes.add(200L)

        speechSession.interruptVotes.remove(100L)

        assertEquals(1, speechSession.interruptVotes.size)
        assertTrue(speechSession.interruptVotes.contains(200L))
    }

    @Test
    fun testAddPlayersToSpeechOrder() {
        val player1 = Session.Player(id = 1, roleId = 1L, channelId = 1L, userId = 100L)
        val player2 = Session.Player(id = 2, roleId = 2L, channelId = 2L, userId = 200L)
        val player3 = Session.Player(id = 3, roleId = 3L, channelId = 3L, userId = 300L)

        speechSession.order.add(player1)
        speechSession.order.add(player2)
        speechSession.order.add(player3)

        assertEquals(3, speechSession.order.size)
        assertEquals(player1, speechSession.order[0])
        assertEquals(player2, speechSession.order[1])
        assertEquals(player3, speechSession.order[2])
    }

    @Test
    fun testSetLastSpeaker() {
        speechSession.lastSpeaker = 100L
        assertEquals(100L, speechSession.lastSpeaker)

        speechSession.lastSpeaker = 200L
        assertEquals(200L, speechSession.lastSpeaker)
    }

    @Test
    fun testSetCurrentSpeechEndTime() {
        val endTime = System.currentTimeMillis() + 30000
        speechSession.currentSpeechEndTime = endTime
        assertEquals(endTime, speechSession.currentSpeechEndTime)
    }

    @Test
    fun testSetTotalSpeechTime() {
        speechSession.totalSpeechTime = 60
        assertEquals(60, speechSession.totalSpeechTime)

        speechSession.totalSpeechTime = 120
        assertEquals(120, speechSession.totalSpeechTime)
    }

    @Test
    fun testShouldStopCurrentSpeaker() {
        speechSession.shouldStopCurrentSpeaker = false
        assertTrue(!speechSession.shouldStopCurrentSpeaker)

        speechSession.shouldStopCurrentSpeaker = true
        assertTrue(speechSession.shouldStopCurrentSpeaker)
    }

    @Test
    fun testSetSpeakingThread() {
        val thread = Thread { }
        speechSession.speakingThread = thread
        assertEquals(thread, speechSession.speakingThread)
    }

    @Test
    fun testSetFinishedCallback() {
        var callbackExecuted = false
        val callback = { callbackExecuted = true }
        speechSession.finishedCallback = callback

        speechSession.finishedCallback?.invoke()
        assertTrue(callbackExecuted)
    }

    @Test
    fun testComplexSpeechSessionScenario() {
        val player1 = Session.Player(id = 1, roleId = 1L, channelId = 1L, userId = 100L)
        val player2 = Session.Player(id = 2, roleId = 2L, channelId = 2L, userId = 200L)

        speechSession.order.add(player1)
        speechSession.order.add(player2)
        speechSession.lastSpeaker = 100L
        speechSession.totalSpeechTime = 30
        speechSession.currentSpeechEndTime = System.currentTimeMillis() + 30000
        speechSession.interruptVotes.add(200L)
        speechSession.interruptVotes.add(300L)

        assertEquals(2, speechSession.order.size)
        assertEquals(100L, speechSession.lastSpeaker)
        assertEquals(30, speechSession.totalSpeechTime)
        assertEquals(2, speechSession.interruptVotes.size)
    }

    @Test
    fun testClearSpeechOrder() {
        val player1 = Session.Player(id = 1, roleId = 1L, channelId = 1L, userId = 100L)
        val player2 = Session.Player(id = 2, roleId = 2L, channelId = 2L, userId = 200L)

        speechSession.order.add(player1)
        speechSession.order.add(player2)
        assertEquals(2, speechSession.order.size)

        speechSession.order.clear()
        assertTrue(speechSession.order.isEmpty())
    }

    @Test
    fun testClearInterruptVotes() {
        speechSession.interruptVotes.add(100L)
        speechSession.interruptVotes.add(200L)
        assertEquals(2, speechSession.interruptVotes.size)

        speechSession.interruptVotes.clear()
        assertTrue(speechSession.interruptVotes.isEmpty())
    }
}
