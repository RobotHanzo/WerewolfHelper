package dev.robothanzo.werewolf.controller

import dev.robothanzo.werewolf.WerewolfApplication
import dev.robothanzo.werewolf.controller.dto.GameRequests
import dev.robothanzo.werewolf.database.documents.Session
import dev.robothanzo.werewolf.service.GameSessionService
import dev.robothanzo.werewolf.service.PoliceService
import dev.robothanzo.werewolf.service.SpeechService
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.Mockito.mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.http.HttpStatus
import java.util.*

class SpeechControllerTest {

    private lateinit var speechController: SpeechController

    @Mock
    private lateinit var gameSessionService: GameSessionService

    @Mock
    private lateinit var speechService: SpeechService

    @Mock
    private lateinit var policeService: PoliceService

    @Mock
    private lateinit var jda: JDA

    @BeforeEach
    fun setup() {
        MockitoAnnotations.openMocks(this)
        WerewolfApplication.jda = jda
        speechController = SpeechController(gameSessionService, speechService, policeService)
    }

    @Test
    fun testStartAutoSpeech() {
        val guildId = 123L
        val session = Session(guildId = guildId)
        session.discordIDs.courtTextChannelId = 456L

        whenever(gameSessionService.getSession(guildId)).thenReturn(Optional.of(session))
        val guildMock = mock(Guild::class.java)
        whenever(jda.getGuildById(guildId)).thenReturn(guildMock)
        val textChannelMock = mock(TextChannel::class.java)
        whenever(guildMock.getTextChannelById(456L)).thenReturn(textChannelMock)
        whenever(textChannelMock.idLong).thenReturn(456L)

        val response = speechController.startAutoSpeech(guildId.toString())

        assertEquals(HttpStatus.OK, response.statusCode)
        verify(speechService).startAutoSpeechFlow(eq(session), eq(456L), isNull())
        verify(gameSessionService).broadcastUpdate(guildId)
    }

    @Test
    fun testSkipSpeech() {
        val guildId = 123L
        val response = speechController.skipSpeech(guildId.toString())

        assertEquals(HttpStatus.OK, response.statusCode)
        verify(speechService).skipToNext(guildId)
        verify(gameSessionService).broadcastUpdate(guildId)
    }

    @Test
    fun testInterruptSpeech() {
        val guildId = 123L
        val response = speechController.interruptSpeech(guildId.toString())

        assertEquals(HttpStatus.OK, response.statusCode)
        verify(speechService).interruptSession(guildId)
        verify(gameSessionService).broadcastUpdate(guildId)
    }

    @Test
    fun testMuteAll() {
        val guildId = 123L
        val response = speechController.muteAll(guildId.toString())

        assertEquals(HttpStatus.OK, response.statusCode)
        verify(speechService).setAllMute(guildId, true)
        verify(gameSessionService).broadcastUpdate(guildId)
    }

    @Test
    fun testUnmuteAll() {
        val guildId = 123L
        val response = speechController.unmuteAll(guildId.toString())

        assertEquals(HttpStatus.OK, response.statusCode)
        verify(speechService).setAllMute(guildId, false)
        verify(gameSessionService).broadcastUpdate(guildId)
    }
}
