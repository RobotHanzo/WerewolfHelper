package dev.robothanzo.werewolf.controller

import dev.robothanzo.werewolf.WerewolfApplication
import dev.robothanzo.werewolf.controller.dto.GameRequests
import dev.robothanzo.werewolf.database.documents.Session
import dev.robothanzo.werewolf.game.model.GameSettings
import dev.robothanzo.werewolf.service.*
import net.dv8tion.jda.api.JDA
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.Mockito.mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.*
import java.util.*

class GameControllerTest {

    @Mock
    private lateinit var playerService: PlayerService

    @Mock
    private lateinit var roleService: RoleService

    @Mock
    private lateinit var gameActionService: GameActionService

    @Mock
    private lateinit var gameSessionService: GameSessionService

    @Mock
    private lateinit var gameStateService: GameStateService

    private lateinit var gameController: GameController

    @BeforeEach
    fun setup() {
        MockitoAnnotations.openMocks(this)
        gameController = GameController(
            playerService,
            roleService,
            gameActionService,
            gameSessionService,
            gameStateService
        )

        // Mock JDA to prevent NPE in Session
        val mockJda = mock<JDA>()
        WerewolfApplication.jda = mockJda
        WerewolfApplication.gameSessionService = gameSessionService
        WerewolfApplication.gameStateService = gameStateService
    }

    @Test
    fun testNextState() {
        val guildId = 123L
        val session = Session(guildId = guildId)

        whenever(gameSessionService.withLockedSession<Any>(eq(guildId), any())).thenAnswer { invocation ->
            val block = invocation.getArgument<(Session) -> Any>(1)
            block.invoke(session)
        }

        val response = gameController.nextState(guildId.toString())

        assertEquals(200, response.statusCode.value())
        verify(gameStateService).nextStep(session)
    }

    @Test
    fun testSetState() {
        val guildId = 123L
        val session = Session(guildId = guildId)
        val stepId = "NIGHT_STEP"
        val request = GameRequests.StateSetRequest(stepId)

        whenever(gameSessionService.withLockedSession<Any>(eq(guildId), any())).thenAnswer { invocation ->
            val block = invocation.getArgument<(Session) -> Any>(1)
            block.invoke(session)
        }

        val response = gameController.setState(guildId.toString(), request)

        assertEquals(200, response.statusCode.value())
        verify(gameStateService).startStep(session, stepId)
    }

    @Test
    fun testPauseState() {
        val guildId = 123L
        val session = Session(guildId = guildId)

        whenever(gameSessionService.withLockedSession<Any>(eq(guildId), any())).thenAnswer { invocation ->
            val block = invocation.getArgument<(Session) -> Any>(1)
            block.invoke(session)
        }

        val response = gameController.pauseState(guildId.toString())

        assertEquals(200, response.statusCode.value())
        verify(gameStateService).pauseStep(eq(session), any())
    }

    @Test
    fun testResumeState() {
        val guildId = 123L
        val session = Session(guildId = guildId)

        whenever(gameSessionService.withLockedSession<Any>(eq(guildId), any())).thenAnswer { invocation ->
            val block = invocation.getArgument<(Session) -> Any>(1)
            block.invoke(session)
        }

        val response = gameController.resumeState(guildId.toString())

        assertEquals(200, response.statusCode.value())
        verify(gameStateService).resumeStep(eq(session), any())
    }

    @Test
    fun testStateAction() {
        val guildId = 123L
        val session = Session(guildId = guildId)
        val action = "vote"
        val request = GameRequests.StateActionRequest(action, mapOf("target" to "1"))

        whenever(gameSessionService.withLockedSession<Any>(eq(guildId), any())).thenAnswer { invocation ->
            val block = invocation.getArgument<(Session) -> Any>(1)
            block.invoke(session)
        }
        whenever(gameStateService.handleInput(eq(session), any())).thenReturn(mapOf("success" to true))

        val response = gameController.stateAction(guildId.toString(), request)

        assertEquals(200, response.statusCode.value())
        // Argument capture or specialized matcher can be used here
        // verifying that handleInput was called is sufficient for now
        verify(gameStateService).handleInput(eq(session), any())
        verify(gameSessionService).broadcastSessionUpdate(session)
    }

    @Test
    fun testAssignRoles() {
        val guildId = 123L
        val session = Session(guildId = guildId)

        whenever(gameSessionService.getSession(guildId)).thenReturn(Optional.of(session))

        val response = gameController.assignRoles(guildId.toString())

        assertEquals(200, response.statusCode.value())
        verify(roleService).assignRoles(eq(session), any(), any())
    }

    @Test
    fun testStartGame() {
        val guildId = 123L
        val session = Session(guildId = guildId)

        whenever(gameSessionService.getSession(guildId)).thenReturn(Optional.of(session))

        val response = gameController.startGame(guildId.toString())

        assertEquals(200, response.statusCode.value())
        verify(gameSessionService).saveSession(session)
        verify(gameStateService).startStep(session, "NIGHT_STEP")
        verify(gameSessionService).broadcastSessionUpdate(session)
    }

    @Test
    fun testResetGame() {
        val guildId = 123L
        val session = Session(guildId = guildId)

        whenever(gameSessionService.getSession(guildId)).thenReturn(Optional.of(session))

        val response = gameController.resetGame(guildId.toString())

        assertEquals(200, response.statusCode.value())
        verify(gameActionService).resetGame(eq(session), any(), any())
    }

    @Test
    fun testUpdateSettings() {
        val guildId = 123L
        val session = Session(guildId = guildId, settings = GameSettings())
        val settings = mapOf("doubleIdentities" to true, "allowWolfSelfKill" to true)

        whenever(gameSessionService.withLockedSession<Any>(eq(guildId), any())).thenAnswer { invocation ->
            val block = invocation.getArgument<(Session) -> Any>(1)
            block.invoke(session)
        }

        val response = gameController.updateSettings(guildId.toString(), settings)

        assertEquals(200, response.statusCode.value())
        assertEquals(true, session.doubleIdentities)
        assertEquals(true, session.settings.allowWolfSelfKill)
    }
}
