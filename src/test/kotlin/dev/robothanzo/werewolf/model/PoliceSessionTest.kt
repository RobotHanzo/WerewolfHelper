package dev.robothanzo.werewolf.model

import dev.robothanzo.werewolf.database.documents.Player
import dev.robothanzo.werewolf.database.documents.Session
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class PoliceSessionTest {
    private lateinit var session: Session
    private lateinit var policeSession: PoliceSession

    @BeforeEach
    fun setup() {
        session = Session(guildId = 123L)
        policeSession = PoliceSession(
            guildId = 123L,
            channelId = 456L,
            session = session
        )
    }

    @Test
    fun testPoliceSessionInitialization() {
        assertEquals(123L, policeSession.guildId)
        assertEquals(456L, policeSession.channelId)
        assertEquals(session, policeSession.session)
        assertEquals(PoliceSession.State.NONE, policeSession.state)
        assertEquals(0L, policeSession.stageEndTime)
        assertTrue(policeSession.candidates.isEmpty())
        assertNull(policeSession.message)
    }

    @Test
    fun testStateEnumCanEnroll() {
        assertTrue(PoliceSession.State.ENROLLMENT.canEnroll())
        assertFalse(PoliceSession.State.SPEECH.canEnroll())
        assertFalse(PoliceSession.State.VOTING.canEnroll())
        assertFalse(PoliceSession.State.NONE.canEnroll())
    }

    @Test
    fun testStateEnumCanQuit() {
        assertTrue(PoliceSession.State.ENROLLMENT.canQuit())
        assertTrue(PoliceSession.State.UNENROLLMENT.canQuit())
        assertTrue(PoliceSession.State.SPEECH.canQuit())
        assertFalse(PoliceSession.State.VOTING.canQuit())
        assertFalse(PoliceSession.State.NONE.canQuit())
    }

    @Test
    fun testStateTransitionToEnrollment() {
        policeSession.state = PoliceSession.State.ENROLLMENT
        assertEquals(PoliceSession.State.ENROLLMENT, policeSession.state)
        assertTrue(policeSession.state.canEnroll())
    }

    @Test
    fun testStateTransitionToSpeech() {
        policeSession.state = PoliceSession.State.SPEECH
        assertEquals(PoliceSession.State.SPEECH, policeSession.state)
        assertFalse(policeSession.state.canEnroll())
    }

    @Test
    fun testStateTransitionToVoting() {
        policeSession.state = PoliceSession.State.VOTING
        assertEquals(PoliceSession.State.VOTING, policeSession.state)
        assertFalse(policeSession.state.canEnroll())
    }

    @Test
    fun testStateTransitionToUnenrollment() {
        policeSession.state = PoliceSession.State.UNENROLLMENT
        assertEquals(PoliceSession.State.UNENROLLMENT, policeSession.state)
        assertTrue(policeSession.state.canQuit())
    }

    @Test
    fun testStateTransitionToFinished() {
        policeSession.state = PoliceSession.State.FINISHED
        assertEquals(PoliceSession.State.FINISHED, policeSession.state)
        assertFalse(policeSession.state.canEnroll())
        assertFalse(policeSession.state.canQuit())
    }

    @Test
    fun testStageEndTimeUpdate() {
        val futureTime = System.currentTimeMillis() + 5000
        policeSession.stageEndTime = futureTime
        assertEquals(futureTime, policeSession.stageEndTime)
    }

    @Test
    fun testAddCandidates() {
        val player1 = Player(id = 1, roleId = 1L, channelId = 1L, userId = 100L)
        val player2 = Player(id = 2, roleId = 2L, channelId = 2L, userId = 200L)

        val candidate1 = Candidate(player = player1)
        val candidate2 = Candidate(player = player2)

        policeSession.candidates[1] = candidate1
        policeSession.candidates[2] = candidate2

        assertEquals(2, policeSession.candidates.size)
        assertEquals(candidate1, policeSession.candidates[1])
        assertEquals(candidate2, policeSession.candidates[2])
    }

    @Test
    fun testRemoveCandidates() {
        val player = Player(id = 1, roleId = 1L, channelId = 1L, userId = 100L)
        val candidate = Candidate(player = player)

        policeSession.candidates[1] = candidate
        assertEquals(1, policeSession.candidates.size)

        policeSession.candidates.remove(1)
        assertEquals(0, policeSession.candidates.size)
    }

    @Test
    fun testMultipleStateTransitions() {
        policeSession.state = PoliceSession.State.ENROLLMENT
        assertEquals(PoliceSession.State.ENROLLMENT, policeSession.state)

        policeSession.state = PoliceSession.State.SPEECH
        assertEquals(PoliceSession.State.SPEECH, policeSession.state)

        policeSession.state = PoliceSession.State.VOTING
        assertEquals(PoliceSession.State.VOTING, policeSession.state)

        policeSession.state = PoliceSession.State.FINISHED
        assertEquals(PoliceSession.State.FINISHED, policeSession.state)
    }
}
