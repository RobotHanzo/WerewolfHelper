package dev.robothanzo.werewolf.game.roles

import dev.robothanzo.werewolf.WerewolfApplication
import dev.robothanzo.werewolf.database.documents.Player
import dev.robothanzo.werewolf.database.documents.Session
import dev.robothanzo.werewolf.game.model.ActionDefinitionId
import dev.robothanzo.werewolf.game.model.DeathCause
import dev.robothanzo.werewolf.game.model.RoleEventContext
import dev.robothanzo.werewolf.game.model.RoleEventType
import dev.robothanzo.werewolf.game.roles.actions.HunterRevengeAction
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel
import net.dv8tion.jda.api.requests.restaction.MessageCreateAction
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*

@DisplayName("Hunter Role Tests")
class HunterTests {

    private lateinit var session: Session
    private lateinit var hunter: Hunter
    private lateinit var revengeAction: HunterRevengeAction
    private lateinit var mockChannel: TextChannel
    private lateinit var mockMessageAction: MessageCreateAction

    @BeforeEach
    fun setup() {
        val mockJda = mock<JDA>()
        WerewolfApplication.jda = mockJda

        revengeAction = HunterRevengeAction() // Use real action to test its integration with onDeath
        hunter = Hunter(revengeAction)

        session = Session(guildId = 123L)

        mockChannel = mock()
        mockMessageAction = mock()
        whenever(mockChannel.sendMessage(any<String>())).thenReturn(mockMessageAction)
    }

    private fun createHunterPlayer(id: Int): Player {
        val player = Player(
            id = id,
            roleId = id.toLong(),
            channelId = id.toLong() * 100,
            userId = id.toLong()
        ).apply {
            roles = mutableListOf("獵人")
            this.session = this@HunterTests.session
        }
        session.players[id.toString()] = player

        whenever(WerewolfApplication.jda.getTextChannelById(player.channelId)).thenReturn(mockChannel)

        return player
    }

    @Test
    @DisplayName("Hunter should have revenge available when killed by werewolf")
    fun testHunterKilledByWerewolf() {
        val hunterPlayer = createHunterPlayer(1)
        val context = RoleEventContext(
            session = session,
            eventType = RoleEventType.ON_DEATH,
            actorPlayerId = hunterPlayer.id,
            metadata = mapOf("deathCause" to DeathCause.WEREWOLF)
        )

        hunter.onDeath(context)

        assertTrue(revengeAction.isAvailable(session, hunterPlayer.id))
        assertTrue(session.stateData.playerOwnedActions[hunterPlayer.id]?.containsKey(ActionDefinitionId.HUNTER_REVENGE.toString()) == true)
        verify(mockChannel, never()).sendMessage(any<String>())
    }

    @Test
    @DisplayName("Hunter should NOT have revenge available when killed by poison and should be notified")
    fun testHunterKilledByPoison() {
        val hunterPlayer = createHunterPlayer(1)
        val context = RoleEventContext(
            session = session,
            eventType = RoleEventType.ON_DEATH,
            actorPlayerId = hunterPlayer.id,
            metadata = mapOf("deathCause" to DeathCause.POISON)
        )

        hunter.onDeath(context)

        assertFalse(revengeAction.isAvailable(session, hunterPlayer.id))
        assertFalse(session.stateData.playerOwnedActions[hunterPlayer.id]?.containsKey(ActionDefinitionId.HUNTER_REVENGE.toString()) == true)
        verify(mockChannel).sendMessage(check<String> { content ->
            assertTrue(content.contains("毒死"))
            assertTrue(content.contains("無法發動技能"))
        })
    }

    @Test
    @DisplayName("Hunter should have revenge available when expelled")
    fun testHunterExpelled() {
        val hunterPlayer = createHunterPlayer(1)
        val context = RoleEventContext(
            session = session,
            eventType = RoleEventType.ON_DEATH,
            actorPlayerId = hunterPlayer.id,
            metadata = mapOf("deathCause" to DeathCause.EXPEL)
        )

        hunter.onDeath(context)

        assertTrue(revengeAction.isAvailable(session, hunterPlayer.id))
    }
}
