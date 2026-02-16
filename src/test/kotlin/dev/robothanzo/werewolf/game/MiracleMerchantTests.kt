package dev.robothanzo.werewolf.game

import dev.robothanzo.werewolf.WerewolfApplication
import dev.robothanzo.werewolf.database.documents.Player
import dev.robothanzo.werewolf.database.documents.Session
import dev.robothanzo.werewolf.game.model.*
import dev.robothanzo.werewolf.game.roles.MiracleMerchant
import dev.robothanzo.werewolf.game.roles.actions.ActionExecutionResult
import dev.robothanzo.werewolf.game.roles.actions.MerchantGuardProtectAction
import dev.robothanzo.werewolf.game.roles.actions.MiracleMerchantTradeGuardAction
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

class MiracleMerchantTests {

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

        // Mock withLockedSession to just execute the block
        whenever(mockGameSessionService.withLockedSession<Any>(any(), any())).thenAnswer { invocation ->
            val block = invocation.arguments[1] as (Session) -> Any
            // We don't have the session instance easily available here to pass to the block if it requires *the* session
            // But wait, addLog calls it with `this.guildId`. The block receives a session.
            // If we are strictly unit testing, we can just execute the block with a dummy session or capture it.
            // However, addLog logic: `it.logs.add(...)`. `it` is the session passed to block.
            // Since we instantiate Session in tests, we can't easily pass THAT session here inside setup().
            // BUT, `addLog` is an extension method on Session? No, it's member method.
            // `WerewolfApplication.gameSessionService.withLockedSession(this.guildId) { it.logs.add(...) }`.
            // The `it` inside the block is the session retrieved by `withLockedSession`.
            // For the test, we can just *not* execute the block if we don't care about logs, OR we can try to return null.
            // Or better: the test creates `session`. `addLog` is called on that session.
            // We can't easily make `withLockedSession` use THAT session because `setup` runs before test.
            // So we'll just mock it to do nothing/return null, effectively disabling logging side-effects in tests.
            null
        }

        WerewolfApplication.jda = mockJda
        WerewolfApplication.gameSessionService = mockGameSessionService
    }

    @Test
    fun `Miracle Merchant should trade Guard skill correctly`() {
        val session = Session()
        val actor = 1
        val target = 2

        // Setup session with Miracle Merchant and a target Villager
        val merchant = mock(MiracleMerchant::class.java)
        val tradeGuardAction = MiracleMerchantTradeGuardAction()

        session.players = mutableMapOf(
            actor.toString() to Player(id = actor, roles = mutableListOf("奇蹟商人")),
            target.toString() to Player(id = target, roles = mutableListOf("平民"))
        )

        // Mock eligible targets to include the villager
        val alivePlayers = listOf(actor, target)
        val accumulatedState = ActionExecutionResult()

        // Submit action
        val actionInstance = RoleActionInstance(
            actor = actor,
            actionDefinitionId = ActionDefinitionId.MIRACLE_MERCHANT_TRADE_GUARD,
            targets = mutableListOf(target),
            status = ActionStatus.SUBMITTED,
            actorRole = "奇蹟商人",
            submittedBy = ActionSubmissionSource.PLAYER
        )

        // Execute action
        tradeGuardAction.execute(session, actionInstance, accumulatedState)

        // Verify target received the skill
        val targetActions = session.stateData.playerOwnedActions[target]
        assertNotNull(targetActions)
        assertTrue(targetActions!!.containsKey(ActionDefinitionId.MERCHANT_GUARD_PROTECT.toString()))
        assertEquals(1, targetActions[ActionDefinitionId.MERCHANT_GUARD_PROTECT.toString()])
    }

    @Test
    fun `Miracle Merchant cannot trade with Wolf`() {
        val session = Session()
        val actor = 1
        val target = 2

        // Setup session with Miracle Merchant and a target *Werewolf*
        val tradeGuardAction = MiracleMerchantTradeGuardAction()

        session.players = mutableMapOf(
            actor.toString() to Player(id = actor, roles = mutableListOf("奇蹟商人")),
            target.toString() to Player(
                id = target,
                roles = mutableListOf("狼人")
            )
        )

        val accumulatedState = ActionExecutionResult()

        // Submit action
        val actionInstance = RoleActionInstance(
            actor = actor,
            actionDefinitionId = ActionDefinitionId.MIRACLE_MERCHANT_TRADE_GUARD,
            targets = mutableListOf(target),
            status = ActionStatus.SUBMITTED,
            actorRole = "奇蹟商人",
            submittedBy = ActionSubmissionSource.PLAYER
        )

        // Execute
        tradeGuardAction.execute(session, actionInstance, accumulatedState)

        // Verify merchant died
        assertTrue(accumulatedState.deaths.containsKey(DeathCause.TRADED_WITH_WOLF))
        assertTrue(accumulatedState.deaths[DeathCause.TRADED_WITH_WOLF]!!.contains(actor))

        // Verify target did NOT receive skill
        val targetActions = session.stateData.playerOwnedActions[target]
        assertNull(targetActions)
    }

    @Test
    fun `Recipient can use the traded Guard skill`() {
        val session = Session()
        val actor = 2 // The villager who received the skill
        val protectedTarget = 1 // Protecting the merchant back

        // Setup session
        session.players = mutableMapOf(
            actor.toString() to Player(id = actor, roles = mutableListOf("平民"))
        )

        // Simulate skill already traded
        session.stateData.playerOwnedActions[actor] = mutableMapOf(
            ActionDefinitionId.MERCHANT_GUARD_PROTECT.toString() to 1
        )

        val guardAction = MerchantGuardProtectAction()
        val accumulatedState = ActionExecutionResult()

        val actionInstance = RoleActionInstance(
            actor = actor,
            actionDefinitionId = ActionDefinitionId.MERCHANT_GUARD_PROTECT,
            targets = mutableListOf(protectedTarget),
            status = ActionStatus.SUBMITTED,
            actorRole = "平民",
            submittedBy = ActionSubmissionSource.PLAYER
        )

        guardAction.execute(session, actionInstance, accumulatedState)

        // Verify protection
        assertTrue(accumulatedState.protectedPlayers.contains(protectedTarget))
    }
}
