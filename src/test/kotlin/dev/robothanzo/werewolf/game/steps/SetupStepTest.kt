package dev.robothanzo.werewolf.game.steps

import dev.robothanzo.werewolf.WerewolfApplication
import dev.robothanzo.werewolf.database.documents.Session
import dev.robothanzo.werewolf.service.*
import net.dv8tion.jda.api.JDA
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*

class SetupStepTest {

    private val speechService: SpeechService = mock()
    private val policeService: PoliceService = mock()
    private val roleService: RoleService = mock()
    private val gameActionService: GameActionService = mock()
    private val gameStateService: GameStateService = mock()
    private val expelService: ExpelService = mock()
    private val gameSessionService: GameSessionService = mock()
    private val jda: JDA = mock()

    @BeforeEach
    fun setUp() {
        WerewolfApplication.policeService = policeService
        WerewolfApplication.expelService = expelService
        WerewolfApplication.speechService = speechService
        WerewolfApplication.gameStateService = gameStateService
        WerewolfApplication.gameSessionService = gameSessionService
        WerewolfApplication.jda = jda
    }

    @Test
    fun `onStart should reset services and mute all`() {
        val step = SetupStep(
            speechService,
            policeService,
            roleService,
            gameActionService,
            gameStateService,
            expelService
        )
        val guildId = 123L
        val session = Session().apply {
            this.guildId = guildId
            this.currentState = step.id
        }

        // Mock withLockedSession for addLog
        whenever(gameSessionService.withLockedSession(eq(guildId), any<(Session) -> Any?>())).thenAnswer {
            val block = it.getArgument<(Session) -> Any?>(1)
            block(session)
        }

        step.onStart(session, gameStateService)

        verify(speechService).interruptSession(guildId)
        verify(policeService).interrupt(guildId)
        verify(expelService).removePoll(guildId)
        verify(speechService).setAllMute(guildId, false)
    }

    @Test
    fun `handleInput start_game should set day and advance step`() {
        val step = SetupStep(
            speechService,
            policeService,
            roleService,
            gameActionService,
            gameStateService,
            expelService
        )
        val guildId = 123L
        val session = Session().apply {
            this.guildId = guildId
            this.day = 1 // Start with some day
        }

        val input = mapOf("action" to "start_game")
        val result = step.handleInput(session, input)

        assertTrue(result["success"] as Boolean)
        assertEquals(0, session.day)
        verify(gameStateService).startStep(session, "NIGHT_STEP")
    }
}
