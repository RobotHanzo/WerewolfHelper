package dev.robothanzo.werewolf.game.steps

import dev.robothanzo.werewolf.WerewolfApplication
import dev.robothanzo.werewolf.database.documents.Session
import dev.robothanzo.werewolf.model.ExpelSession
import dev.robothanzo.werewolf.service.ExpelService
import dev.robothanzo.werewolf.service.GameStateService
import dev.robothanzo.werewolf.service.SpeechService
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*

class VotingStepTest {

    private val expelService: ExpelService = mock()
    private val speechService: SpeechService = mock()
    private val gameStateService: GameStateService = mock()
    private val jda: JDA = mock()

    @BeforeEach
    fun setUp() {
        WerewolfApplication.expelService = expelService
        WerewolfApplication.speechService = speechService
        WerewolfApplication.gameStateService = gameStateService
        WerewolfApplication.jda = jda
    }

    @Test
    fun `onStart should start expel poll`() {
        val step = VotingStep(expelService)
        val guildId = 123L
        val guild = mock<Guild>()
        val textChannel = mock<TextChannel>()

        val session = Session().apply {
            this.guildId = guildId
            this.discordIDs.courtTextChannelId = 456L
        }

        whenever(jda.getGuildById(guildId)).thenReturn(guild)
        whenever(guild.getTextChannelById(456L)).thenReturn(textChannel)

        step.onStart(session, gameStateService)

        verify(expelService).startExpelPoll(eq(session), eq(30))
    }

    @Test
    fun `getEndTime should calculate from expel session`() {
        val step = VotingStep(expelService)
        val guildId = 123L
        val session = Session().apply {
            this.guildId = guildId
        }

        val expelSession = mock<ExpelSession> {
            on { endTime } doReturn 135792468L
        }

        whenever(expelService.getExpelSession(guildId)).thenReturn(expelSession)

        val result = step.getEndTime(session)

        assertEquals(135792468L, result)
    }
}
