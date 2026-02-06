package dev.robothanzo.werewolf.model

import dev.robothanzo.werewolf.database.documents.Player
import dev.robothanzo.werewolf.database.documents.Session
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class SpeechSessionTest {
    private lateinit var session: Session
    private lateinit var speechSession: SpeechSession

    @BeforeEach
    fun setup() {
        session = Session(guildId = 123)
        speechSession = SpeechSession(
            guildId = 123,
            channelId = 456,
            session = session
        )
    }

    @Test
    fun testSpeechSessionInitialization() {
        assertEquals(123, speechSession.guildId)
        assertEquals(456, speechSession.channelId)
        assertEquals(session, speechSession.session)
        assertTrue(speechSession.interruptVotes.isEmpty())
        assertTrue(speechSession.order.isEmpty())
        assertNull(speechSession.speakingThread)
        assertNull(speechSession.lastSpeaker)
        assertNull(speechSession.finishedCallback)
        assertEquals(0, speechSession.currentSpeechEndTime)
        assertEquals(0, speechSession.totalSpeechTime)
        assertTrue(!speechSession.shouldStopCurrentSpeaker)
    }

    @Test
    fun testAddInterruptVotes() {
        speechSession.interruptVotes.add(100)
        speechSession.interruptVotes.add(200)
        speechSession.interruptVotes.add(300)

        assertEquals(3, speechSession.interruptVotes.size)
        assertTrue(speechSession.interruptVotes.contains(100))
        assertTrue(speechSession.interruptVotes.contains(200))
        assertTrue(speechSession.interruptVotes.contains(300))
    }

    @Test
    fun testRemoveInterruptVotes() {
        speechSession.interruptVotes.add(100)
        speechSession.interruptVotes.add(200)

        speechSession.interruptVotes.remove(100)

        assertEquals(1, speechSession.interruptVotes.size)
        assertTrue(speechSession.interruptVotes.contains(200))
    }

    @Test
    fun testAddPlayersToSpeechOrder() {
        val player1 = Player(id = 1, roleId = 1, channelId = 1, userId = 100)
        val player2 = Player(id = 2, roleId = 2, channelId = 2, userId = 200)
        val player3 = Player(id = 3, roleId = 3, channelId = 3, userId = 300)

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
        speechSession.lastSpeaker = 100
        assertEquals(100, speechSession.lastSpeaker)

        speechSession.lastSpeaker = 200
        assertEquals(200, speechSession.lastSpeaker)
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
        val player1 = Player(id = 1, roleId = 1, channelId = 1, userId = 100)
        val player2 = Player(id = 2, roleId = 2, channelId = 2, userId = 200)

        speechSession.order.add(player1)
        speechSession.order.add(player2)
        speechSession.lastSpeaker = 100
        speechSession.totalSpeechTime = 30
        speechSession.currentSpeechEndTime = System.currentTimeMillis() + 30000
        speechSession.interruptVotes.add(200)
        speechSession.interruptVotes.add(300)

        assertEquals(2, speechSession.order.size)
        assertEquals(100, speechSession.lastSpeaker)
        assertEquals(30, speechSession.totalSpeechTime)
        assertEquals(2, speechSession.interruptVotes.size)
    }

    @Test
    fun testClearSpeechOrder() {
        val player1 = Player(id = 1, roleId = 1, channelId = 1, userId = 100)
        val player2 = Player(id = 2, roleId = 2, channelId = 2, userId = 200)

        speechSession.order.add(player1)
        speechSession.order.add(player2)
        assertEquals(2, speechSession.order.size)

        speechSession.order.clear()
        assertTrue(speechSession.order.isEmpty())
    }

    @Test
    fun testClearInterruptVotes() {
        speechSession.interruptVotes.add(100)
        speechSession.interruptVotes.add(200)
        assertEquals(2, speechSession.interruptVotes.size)

        speechSession.interruptVotes.clear()
        assertTrue(speechSession.interruptVotes.isEmpty())
    }
}
