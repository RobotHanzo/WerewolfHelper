package dev.robothanzo.werewolf.game.steps

import dev.robothanzo.werewolf.WerewolfApplication
import dev.robothanzo.werewolf.database.ReplayRepository
import dev.robothanzo.werewolf.database.documents.Session
import dev.robothanzo.werewolf.service.GameStateService
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.components.actionrow.ActionRow
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.MessageEmbed
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel
import net.dv8tion.jda.api.requests.restaction.MessageCreateAction
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class JudgeDecisionStepTest {

    private val gameStateService: GameStateService = mock()
    private val jda: JDA = mock()
    private val replayRepository: ReplayRepository = mock()

    @BeforeEach
    fun setUp() {
        WerewolfApplication.gameStateService = gameStateService
        WerewolfApplication.jda = jda
        WerewolfApplication.replayRepository = replayRepository
    }

    @Test
    fun `onStart should send decision embed`() {
        val step = JudgeDecisionStep()
        val guildId = 123L
        val guild = mock<Guild>()
        val textChannel = mock<TextChannel>()
        val messageAction = mock<MessageCreateAction>()

        val session = Session().apply {
            this.guildId = guildId
            this.discordIDs.judgeTextChannelId = 456L
        }

        whenever(jda.getGuildById(guildId)).thenReturn(guild)
        whenever(guild.getTextChannelById(456L)).thenReturn(textChannel)
        whenever(textChannel.sendMessageEmbeds(any<MessageEmbed>())).thenReturn(messageAction)
        whenever(messageAction.setComponents(any<ActionRow>())).thenReturn(messageAction)

        step.onStart(session, gameStateService)

        verify(textChannel).sendMessageEmbeds(any<MessageEmbed>())
        verify(messageAction).setComponents(any<ActionRow>())
        verify(messageAction).queue()
    }

    @Test
    fun `handleInput continue_game should advance step`() {
        val step = JudgeDecisionStep()
        val session = Session()
        val input = mapOf("action" to "continue_game")

        val result = step.handleInput(session, input)

        assertTrue(result["success"] as Boolean)
        verify(gameStateService).nextStep(session)
    }
}
