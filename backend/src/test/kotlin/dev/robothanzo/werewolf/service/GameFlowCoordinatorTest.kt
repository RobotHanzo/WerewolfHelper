package dev.robothanzo.werewolf.service

import dev.robothanzo.werewolf.domain.GameSession
import dev.robothanzo.werewolf.domain.Phase
import dev.robothanzo.werewolf.game.flow.GameFlowService
import dev.robothanzo.werewolf.support.TestFixtures
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * Unit-tests the coarse phase walk: [GameFlowCoordinator.advance] runs [GameFlowService.next] and
 * dispatches to the orchestrator that owns the phase we land on. The orchestrators are mocked (their
 * side effects are tested elsewhere) and [GameSessionService] runs the block on an in-memory session.
 */
class GameFlowCoordinatorTest {

    private lateinit var session: GameSession
    private lateinit var sessionService: GameSessionService
    private val night: NightOrchestrator = mock()
    private val day: DayOrchestrator = mock()
    private lateinit var coordinator: GameFlowCoordinator

    @BeforeEach
    fun setup() {
        session = TestFixtures.session(playerCount = 5, seats = emptyList()) { assigned = true }
        sessionService = mock()
        whenever(sessionService.find(any())).thenAnswer { session }
        whenever(sessionService.mutate(any(), any<(GameSession) -> Any?>())).thenAnswer { inv ->
            inv.getArgument<(GameSession) -> Any?>(1).invoke(session)
        }
        coordinator = GameFlowCoordinator(sessionService, GameFlowService(), night, day)
    }

    @Test
    fun `start enters the first night`() {
        coordinator.start(1L)
        assertEquals(Phase.NIGHT, session.phase)
        assertEquals(1, session.day)
        verify(night).startNight(1L)
    }

    @Test
    fun `day-1 dawn advances into the police election`() {
        session.phase = Phase.DAWN; session.day = 1
        coordinator.advance(1L)
        assertEquals(Phase.POLICE_ELECTION, session.phase)
        verify(day).startPoliceElection(1L)
    }

    @Test
    fun `police election advances into speeches`() {
        session.phase = Phase.POLICE_ELECTION; session.day = 1
        coordinator.advance(1L)
        assertEquals(Phase.SPEECHES, session.phase)
        verify(day).startSpeeches(1L)
    }

    @Test
    fun `speeches advance into the expel vote`() {
        session.phase = Phase.SPEECHES; session.day = 1
        coordinator.advance(1L)
        assertEquals(Phase.EXPEL_VOTE, session.phase)
        verify(day).startExpelVote(1L)
    }

    @Test
    fun `the expel vote advances into the next night`() {
        session.phase = Phase.EXPEL_VOTE; session.day = 1
        coordinator.advance(1L)
        assertEquals(Phase.NIGHT, session.phase)
        assertEquals(2, session.day)
        verify(night).startNight(1L)
    }

    @Test
    fun `later days skip the police election`() {
        session.phase = Phase.DAWN; session.day = 2
        coordinator.advance(1L)
        assertEquals(Phase.SPEECHES, session.phase)
        verify(day).startSpeeches(1L)
    }

    @Test
    fun `game over is terminal and dispatches nothing`() {
        session.phase = Phase.OVER; session.day = 3
        coordinator.advance(1L)
        assertEquals(Phase.OVER, session.phase)
        verify(night, never()).startNight(any())
        verify(day, never()).enterDawn(any())
    }
}
