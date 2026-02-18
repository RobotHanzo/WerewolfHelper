package dev.robothanzo.werewolf.service.impl

import dev.robothanzo.werewolf.WerewolfApplication
import dev.robothanzo.werewolf.database.documents.Player
import dev.robothanzo.werewolf.database.documents.Session
import dev.robothanzo.werewolf.game.GameStep
import dev.robothanzo.werewolf.service.GameSessionService
import net.dv8tion.jda.api.JDA
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*

class GameStateServiceImplTest {

    private val sessionService: GameSessionService = mock()
    private val jda: JDA = mock()
    private val step1: GameStep = mock {
        on { id } doReturn "NIGHT_STEP"
        on { name } doReturn "天黑請閉眼"
        on { getEndTime(any()) } doReturn (System.currentTimeMillis() + 60_000L)
    }
    private val step2: GameStep = mock {
        on { id } doReturn "SHERIFF_ELECTION"
        on { name } doReturn "警長競選"
        on { getEndTime(any()) } doReturn (System.currentTimeMillis() + 30_000L)
    }
    private val dayStep: GameStep = mock {
        on { id } doReturn "DAY_STEP"
        on { name } doReturn "天亮了"
        on { getEndTime(any()) } doReturn (System.currentTimeMillis() + 5_000L)
    }
    private val setupStep: GameStep = mock {
        on { id } doReturn "SETUP"
        on { name } doReturn "遊戲設置"
    }
    private val judgeStep: GameStep = mock {
        on { id } doReturn "JUDGE_DECISION"
        on { name } doReturn "法官裁決"
    }

    private lateinit var gameStateService: GameStateServiceImpl

    @BeforeEach
    fun setUp() {
        WerewolfApplication.gameSessionService = sessionService
        WerewolfApplication.jda = jda
        gameStateService = GameStateServiceImpl(sessionService, listOf(step1, step2, setupStep, dayStep, judgeStep))
        WerewolfApplication.gameStateService = gameStateService

        // Mock withLockedSession(Long, (Session) -> T)
        whenever(sessionService.withLockedSession(any<Long>(), any<(Session) -> Any?>())).thenAnswer { invocation ->
            val block = invocation.getArgument<(Session) -> Any?>(1)
            val guildId = invocation.getArgument<Long>(0)
            val session = Session().apply { this.guildId = guildId }
            block(session)
        }
    }

    private fun addAlivePlayers(session: Session) {
        session.addPlayer(Player(id = 1, roles = mutableListOf("狼人")).apply { this.session = session })
        session.addPlayer(Player(id = 2, roles = mutableListOf("平民")).apply { this.session = session })
        session.addPlayer(Player(id = 3, roles = mutableListOf("女巫")).apply { this.session = session })
    }

    @Test
    fun `registerStep adds step to map`() {
        val newStep: GameStep = mock { on { id } doReturn "NEW_STEP" }
        gameStateService.registerStep(newStep)
        assertEquals(newStep, gameStateService.getStep("NEW_STEP"))
    }

    @Test
    fun `getCurrentStep returns correct step based on session state`() {
        val session = Session().apply { guildId = 123L; currentState = "NIGHT_STEP" }
        assertEquals(step1, gameStateService.getCurrentStep(session))
    }

    @Test
    fun `startStep ends current step and starts new one`() {
        val session = Session().apply { guildId = 123L; currentState = "SETUP" }
        addAlivePlayers(session)

        gameStateService.startStep(session, "NIGHT_STEP")

        verify(setupStep).onEnd(eq(session), any())
        assertEquals("NIGHT_STEP", session.currentState)
        assertTrue(session.currentStepEndTime > System.currentTimeMillis())
        verify(sessionService).saveSession(session)
        verify(step1).onStart(eq(session), any())
    }

    @Test
    fun `nextStep from SETUP moves to NIGHT_STEP`() {
        val session = Session().apply { guildId = 123L; currentState = "SETUP" }
        addAlivePlayers(session) // Ensure game doesn't end immediately

        gameStateService.nextStep(session)

        assertEquals("NIGHT_STEP", session.currentState)
    }

    @Test
    fun `nextStep from NIGHT_STEP moves to DAY_STEP on day 0`() {
        val session = Session().apply {
            guildId = 123L
            currentState = "NIGHT_STEP"
            day = 0
        }
        addAlivePlayers(session)

        gameStateService.nextStep(session)

        assertEquals("DAY_STEP", session.currentState)
    }

    @Test
    fun `nextStep from NIGHT_STEP moves to DAY_STEP on day 1`() {
        val deathStep: GameStep = mock {
            on { id } doReturn "DEATH_ANNOUNCEMENT"
            on { name } doReturn "公布亡者"
        }
        gameStateService.registerStep(deathStep)

        val session = Session().apply {
            guildId = 123L
            currentState = "NIGHT_STEP"
            day = 1
        }
        addAlivePlayers(session)

        gameStateService.nextStep(session)

        assertEquals("DAY_STEP", session.currentState)
    }

    @Test
    fun `handleInput calls current step handleInput and saves session if successful`() {
        val session = Session().apply { guildId = 123L; currentState = "NIGHT_STEP" }
        val input = mapOf("action" to "vote")
        val output = mapOf("success" to true)
        whenever(step1.handleInput(session, input)).thenReturn(output)

        val result = gameStateService.handleInput(session, input)

        assertEquals(output, result)
        verify(sessionService).saveSession(session)
    }

    @Test
    fun `handleInput advances to next step if votingEnded is true`() {
        val session = Session().apply {
            guildId = 123L
            currentState = "NIGHT_STEP"
            day = 0
        }
        addAlivePlayers(session)
        val input = mapOf("action" to "vote")
        val output = mapOf("success" to true, "votingEnded" to true)
        whenever(step1.handleInput(session, input)).thenReturn(output)

        gameStateService.handleInput(session, input)

        assertEquals("DAY_STEP", session.currentState)
    }

    @Test
    fun `nextStep from DAY_STEP moves to SHERIFF_ELECTION on day 0`() {
        val session = Session().apply {
            guildId = 123L
            currentState = "DAY_STEP"
            day = 0
        }
        addAlivePlayers(session)

        gameStateService.nextStep(session)

        assertEquals("SHERIFF_ELECTION", session.currentState)
    }

    @Test
    fun `nextStep from DAY_STEP skips SHERIFF_ELECTION on day 1`() {
        val deathStep: GameStep = mock {
            on { id } doReturn "DEATH_ANNOUNCEMENT"
            on { name } doReturn "公布亡者"
        }
        gameStateService.registerStep(deathStep)

        val session = Session().apply {
            guildId = 123L
            currentState = "DAY_STEP"
            day = 1
        }
        addAlivePlayers(session)

        gameStateService.nextStep(session)

        assertEquals("DEATH_ANNOUNCEMENT", session.currentState)
        assertEquals(2, session.day)
    }
}
