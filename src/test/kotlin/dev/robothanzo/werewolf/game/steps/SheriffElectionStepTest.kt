package dev.robothanzo.werewolf.game.steps

import dev.robothanzo.werewolf.WerewolfApplication
import dev.robothanzo.werewolf.database.documents.Session
import dev.robothanzo.werewolf.model.PoliceSession
import dev.robothanzo.werewolf.service.GameStateService
import dev.robothanzo.werewolf.service.PoliceService
import dev.robothanzo.werewolf.service.SpeechService
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*

class SheriffElectionStepTest {

    private val policeService: PoliceService = mock()
    private val speechService: SpeechService = mock()
    private val gameStateService: GameStateService = mock()
    private val jda: JDA = mock()

    @BeforeEach
    fun setUp() {
        WerewolfApplication.policeService = policeService
        WerewolfApplication.speechService = speechService
        WerewolfApplication.gameStateService = gameStateService
        WerewolfApplication.jda = jda
    }

    @Test
    fun `onStart should start enrollment`() {
        val step = SheriffElectionStep(policeService, speechService)
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

        verify(policeService).startEnrollment(eq(session), eq(textChannel), isNull(), any())
    }

    @Test
    fun `getEndTime should use stageEndTime from police session`() {
        val step = SheriffElectionStep(policeService, speechService)
        val guildId = 123L
        val session = Session().apply {
            this.guildId = guildId
        }

        val policeSession = mock<PoliceSession> {
            on { state } doReturn PoliceSession.State.VOTING
            on { stageEndTime } doReturn 123456789L
        }

        whenever(policeService.sessions).thenReturn(mapOf(guildId to policeSession))

        val result = step.getEndTime(session)

        assertEquals(123456789L, result)
    }
}
