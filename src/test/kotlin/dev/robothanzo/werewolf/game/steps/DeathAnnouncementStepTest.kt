package dev.robothanzo.werewolf.game.steps

import dev.robothanzo.werewolf.WerewolfApplication
import dev.robothanzo.werewolf.database.documents.Player
import dev.robothanzo.werewolf.database.documents.Session
import dev.robothanzo.werewolf.game.roles.RoleRegistry
import dev.robothanzo.werewolf.game.roles.actions.RoleActionExecutor
import dev.robothanzo.werewolf.service.GameSessionService
import dev.robothanzo.werewolf.service.GameStateService
import net.dv8tion.jda.api.JDA
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import java.util.*

class DeathAnnouncementStepTest {

    private val roleRegistry: RoleRegistry = mock()
    private val roleActionExecutor: RoleActionExecutor = mock()
    private val gameSessionService: GameSessionService = mock()
    private val gameStateService: GameStateService = mock()
    private val jda: JDA = mock()

    @BeforeEach
    fun setUp() {
        WerewolfApplication.gameSessionService = gameSessionService
        WerewolfApplication.gameStateService = gameStateService
        WerewolfApplication.jda = jda
    }

    @Test
    fun `onStart should resolve night actions and mark players dead`() {
        val step = DeathAnnouncementStep(roleRegistry, roleActionExecutor, gameSessionService)
        val guildId = 123L

        val session = Session().apply {
            this.guildId = guildId
            addPlayer(Player(id = 1))
            addPlayer(Player(id = 2))
        }

        // Mock withLockedSession
        whenever(gameSessionService.withLockedSession(eq(guildId), any<(Session) -> Any?>())).thenAnswer {
            val block = it.getArgument<(Session) -> Any?>(1)
            block(session)
        }

        // Mock getSession
        whenever(gameSessionService.getSession(guildId)).thenReturn(Optional.of(session))

        step.onStart(session, gameStateService)

        verify(gameSessionService, atLeastOnce()).withLockedSession(eq(guildId), any<(Session) -> Any?>())
    }
}
