package dev.robothanzo.werewolf.game.steps

import dev.robothanzo.werewolf.WerewolfApplication
import dev.robothanzo.werewolf.database.documents.Session
import dev.robothanzo.werewolf.model.SpeechSession
import dev.robothanzo.werewolf.service.GameStateService
import dev.robothanzo.werewolf.service.SpeechService
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*

class SpeechStepTest {

    private val speechService: SpeechService = mock()
    private val gameStateService: GameStateService = mock()
    private val jda: JDA = mock()

    @BeforeEach
    fun setUp() {
        WerewolfApplication.speechService = speechService
        WerewolfApplication.gameStateService = gameStateService
        WerewolfApplication.jda = jda
    }

    @Test
    fun `onStart should start auto speech flow`() {
        val step = SpeechStep(speechService)
        val guildId = 123L
        val guild = mock<Guild>()
        val textChannel = mock<TextChannel> {
            on { idLong } doReturn 456L
        }
        val session = Session().apply {
            this.guildId = guildId
            this.discordIDs.courtTextChannelId = 456L
        }

        whenever(jda.getGuildById(guildId)).thenReturn(guild)
        whenever(guild.getTextChannelById(456L)).thenReturn(textChannel)

        step.onStart(session, gameStateService)

        verify(speechService).startAutoSpeechFlow(eq(session), eq(456L), any())
    }

    @Test
    fun `onEnd should interrupt speech session`() {
        val step = SpeechStep(speechService)
        val guildId = 123L
        val session = Session().apply {
            this.guildId = guildId
        }

        step.onEnd(session, gameStateService)

        verify(speechService).interruptSession(guildId)
    }

    @Test
    fun `getEndTime should calculate from speech session`() {
        val step = SpeechStep(speechService)
        val guildId = 123L
        val session = Session().apply {
            this.guildId = guildId
        }

        val now = System.currentTimeMillis()
        val speechSession = mock<SpeechSession> {
            on { currentSpeechEndTime } doReturn now + 10000L // 10s left for current
            on { order } doReturn mutableListOf() // No more speakers
        }

        whenever(speechService.getSpeechSession(guildId)).thenReturn(speechSession)

        val result = step.getEndTime(session)

        // Result should be approximately now + 10s.
        assertTrue(result >= now + 10000L)
    }

    @Test
    fun `getEndTime should use pauseStartTime when paused`() {
        val step = SpeechStep(speechService)
        val guildId = 123L
        val pauseTime = 1000000L
        val session = Session().apply {
            this.guildId = guildId
            stateData.paused = true
            stateData.pauseStartTime = pauseTime
        }

        val speechSession = mock<SpeechSession> {
            on { currentSpeechEndTime } doReturn pauseTime + 5000L // 5s left
            on { order } doReturn mutableListOf()
        }

        whenever(speechService.getSpeechSession(guildId)).thenReturn(speechSession)

        val result = step.getEndTime(session)
        assertEquals(pauseTime + 5000L, result)
    }
}
