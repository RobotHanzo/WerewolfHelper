package dev.robothanzo.werewolf.game.flow

import dev.robothanzo.werewolf.domain.Phase
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class GameFlowServiceTest {

    private val flow = GameFlowService()

    @Test
    fun `starting the game enters the first night`() {
        assertEquals(PhaseTransition(Phase.NIGHT, 1), flow.start())
    }

    @Test
    fun `day one runs the police election before speeches`() {
        // NIGHT(1) → DAWN(1) → POLICE_ELECTION(1) → SPEECHES(1) → EXPEL_VOTE(1) → NIGHT(2)
        assertEquals(PhaseTransition(Phase.DAWN, 1), flow.next(Phase.NIGHT, 1))
        assertEquals(PhaseTransition(Phase.POLICE_ELECTION, 1), flow.next(Phase.DAWN, 1))
        assertEquals(PhaseTransition(Phase.SPEECHES, 1), flow.next(Phase.POLICE_ELECTION, 1))
        assertEquals(PhaseTransition(Phase.EXPEL_VOTE, 1), flow.next(Phase.SPEECHES, 1))
        assertEquals(PhaseTransition(Phase.NIGHT, 2), flow.next(Phase.EXPEL_VOTE, 1))
    }

    @Test
    fun `later days skip the police election`() {
        // DAWN on day 2 goes straight to speeches
        assertEquals(PhaseTransition(Phase.SPEECHES, 2), flow.next(Phase.DAWN, 2))
    }

    @Test
    fun `game over is terminal`() {
        assertEquals(PhaseTransition(Phase.OVER, 3), flow.next(Phase.OVER, 3))
    }
}
