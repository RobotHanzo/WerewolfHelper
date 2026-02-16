package dev.robothanzo.werewolf.game

import dev.robothanzo.werewolf.WerewolfApplication
import dev.robothanzo.werewolf.database.documents.Player
import dev.robothanzo.werewolf.database.documents.Session
import dev.robothanzo.werewolf.game.model.*
import dev.robothanzo.werewolf.game.roles.actions.ActionExecutionResult
import dev.robothanzo.werewolf.game.roles.actions.MerchantGunAction
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel
import net.dv8tion.jda.api.requests.restaction.MessageCreateAction
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever

class MerchantGunTests {

    @BeforeEach
    fun setup() {
        val mockJda = mock<JDA>()
        val mockChannel = mock<TextChannel>()

        @Suppress("DEPRECATION")
        val mockMessageAction = mock<MessageCreateAction>()
        val mockGameSessionService = mock<dev.robothanzo.werewolf.service.GameSessionService>()

        whenever(mockJda.getUserById(any<Long>())).thenAnswer { invocation ->
            val userId = invocation.arguments[0] as Long
            val user = mock<User>()
            whenever(user.idLong).thenReturn(userId)
            user
        }
        whenever(mockJda.getTextChannelById(any<Long>())).thenReturn(mockChannel)
        whenever(mockChannel.sendMessage(any<String>())).thenReturn(mockMessageAction)

        // Mock withLockedSession
        whenever(mockGameSessionService.withLockedSession<Any>(any(), any())).thenAnswer {
            null
        }

        WerewolfApplication.jda = mockJda
        WerewolfApplication.gameSessionService = mockGameSessionService
    }

    @Test
    fun `Merchant Gun should be usable on normal death`() {
        val session = Session()
        val actor = 1
        val target = 2

        session.players = mutableMapOf(
            actor.toString() to Player(id = actor, roles = mutableListOf("平民")),
            target.toString() to Player(id = target, roles = mutableListOf("狼人"))
        )

        // Grant Merchant Gun
        session.stateData.playerOwnedActions[actor] = mutableMapOf(
            ActionDefinitionId.MERCHANT_GUN.toString() to 1
        )

        val gunAction = MerchantGunAction()

        // Verify timing
        assertEquals(ActionTiming.DEATH_TRIGGER, gunAction.timing)

        // Verify availability
        assertTrue(gunAction.isAvailable(session, actor))

        // Execute
        val actionInstance = RoleActionInstance(
            actor = actor,
            actionDefinitionId = ActionDefinitionId.MERCHANT_GUN,
            targets = mutableListOf(target),
            status = ActionStatus.SUBMITTED,
            actorRole = "平民",
            submittedBy = ActionSubmissionSource.PLAYER
        )
        val result = ActionExecutionResult()
        gunAction.execute(session, actionInstance, result)

        // Verify death
        assertTrue(result.deaths.containsKey(DeathCause.HUNTER_REVENGE))
        assertTrue(result.deaths[DeathCause.HUNTER_REVENGE]!!.contains(target))

        // Verify consumption
        val actions = session.stateData.playerOwnedActions[actor]
        assertTrue(actions == null || !actions.containsKey(ActionDefinitionId.MERCHANT_GUN.toString()))
    }

    @Test
    fun `Merchant Gun should be removed on Poison death`() {
        val session = Session()
        val actor = 1

        session.players = mutableMapOf(
            actor.toString() to Player(id = actor, roles = mutableListOf("平民"))
        )

        // Grant Merchant Gun
        session.stateData.playerOwnedActions[actor] = mutableMapOf(
            ActionDefinitionId.MERCHANT_GUN.toString() to 1
        )

        val gunAction = MerchantGunAction()

        // Trigger onDeath with POISON
        gunAction.onDeath(session, actor, DeathCause.POISON)

        // Verify removal
        val actions = session.stateData.playerOwnedActions[actor]
        assertTrue(actions == null || !actions.containsKey(ActionDefinitionId.MERCHANT_GUN.toString()))
    }

    @Test
    fun `Merchant Gun should NOT be removed on other death causes`() {
        val session = Session()
        val actor = 1

        session.players = mutableMapOf(
            actor.toString() to Player(id = actor, roles = mutableListOf("平民"))
        )

        // Grant Merchant Gun
        session.stateData.playerOwnedActions[actor] = mutableMapOf(
            ActionDefinitionId.MERCHANT_GUN.toString() to 1
        )

        val gunAction = MerchantGunAction()

        // Trigger onDeath with WEREWOLF (normal night death)
        gunAction.onDeath(session, actor, DeathCause.WEREWOLF)

        // Verify it remains available for usage
        val actions = session.stateData.playerOwnedActions[actor]
        assertNotNull(actions)
        assertTrue(actions!!.containsKey(ActionDefinitionId.MERCHANT_GUN.toString()))
    }
}
