package dev.robothanzo.werewolf.game.steps

import dev.robothanzo.werewolf.WerewolfApplication
import dev.robothanzo.werewolf.database.documents.Session
import dev.robothanzo.werewolf.service.GameSessionService
import dev.robothanzo.werewolf.service.GameStateService
import dev.robothanzo.werewolf.service.SpeechService
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel
import net.dv8tion.jda.api.requests.restaction.MessageCreateAction
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class DayStepTest {

    private val speechService: SpeechService = mock()
    private val gameSessionService: GameSessionService = mock()
    private val gameStateService: GameStateService = mock()
    private val jda: JDA = mock()

    @BeforeEach
    fun setUp() {
        WerewolfApplication.speechService = speechService
        WerewolfApplication.gameSessionService = gameSessionService
        WerewolfApplication.gameStateService = gameStateService
        WerewolfApplication.jda = jda
    }

    @Test
    fun `onStart should mute all and send message`() {
        val dayStep = DayStep(speechService, gameSessionService)
        val guildId = 123L
        val guild = mock<Guild>()
        val textChannel = mock<TextChannel>()
        val voiceChannel = mock<VoiceChannel>()
        val messageAction = mock<MessageCreateAction>()

        val session = Session().apply {
            this.guildId = guildId
            this.discordIDs.courtTextChannelId = 456L
            this.discordIDs.courtVoiceChannelId = 789L
        }

        whenever(jda.getGuildById(guildId)).thenReturn(guild)
        whenever(guild.getTextChannelById(456L)).thenReturn(textChannel)
        whenever(guild.getVoiceChannelById(789L)).thenReturn(voiceChannel)
        whenever(textChannel.sendMessage(any<CharSequence>())).thenReturn(messageAction)

        dayStep.onStart(session, gameStateService)

        verify(speechService).setAllMute(guildId, true)
        verify(textChannel).sendMessage("# **:sunny: 天亮了**")
        verify(messageAction).queue()
    }
}
