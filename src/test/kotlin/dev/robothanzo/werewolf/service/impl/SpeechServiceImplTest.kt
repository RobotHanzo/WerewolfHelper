package dev.robothanzo.werewolf.service.impl

import dev.robothanzo.werewolf.database.SessionRepository
import dev.robothanzo.werewolf.database.documents.Session
import dev.robothanzo.werewolf.model.SpeechSession
import dev.robothanzo.werewolf.service.DiscordService
import dev.robothanzo.werewolf.service.GameSessionService
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.whenever
import java.util.*

class SpeechServiceImplTest {
    @Mock
    private lateinit var sessionRepository: SessionRepository

    @Mock
    private lateinit var discordService: DiscordService

    @Mock
    private lateinit var gameSessionService: GameSessionService

    private lateinit var speechService: SpeechServiceImpl

    @BeforeEach
    fun setup() {
        MockitoAnnotations.openMocks(this)
        speechService = SpeechServiceImpl(
            sessionRepository,
            discordService,
            gameSessionService
        )
    }

    @Test
    fun testGetSpeechSessionNotFound() {
        val guildId = 123L
        assertNull(speechService.getSpeechSession(guildId))
    }

    @Test
    fun testSpeechServiceInitialization() {
        val guildId = 123L
        val session = Session(guildId = guildId)

        whenever(sessionRepository.findByGuildId(guildId)).thenReturn(Optional.of(session))

        val speechSession = SpeechSession(
            guildId = guildId,
            channelId = 456L,
            session = session
        )

        // Manually add to verify behavior
        assertNull(speechService.getSpeechSession(guildId))
    }

    @Test
    fun testSpeechSessionStorageAndRetrieval() {
        val guildId = 123L
        val session = Session(guildId = guildId)
        val speechSession = SpeechSession(
            guildId = guildId,
            channelId = 456L,
            session = session
        )

        // Using reflection to add to the private map for testing purposes
        val field = speechService.javaClass.getDeclaredField("speechSessions")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val speechSessions = field.get(speechService) as MutableMap<Long, SpeechSession>
        speechSessions[guildId] = speechSession

        val retrieved = speechService.getSpeechSession(guildId)
        assertNotNull(retrieved)
        assertEquals(guildId, retrieved?.guildId)
        assertEquals(456L, retrieved?.channelId)
    }

    @Test
    fun testMultipleSpeechSessions() {
        val session1 = Session(guildId = 100L)
        val session2 = Session(guildId = 200L)

        val speechSession1 = SpeechSession(
            guildId = 100L,
            channelId = 456L,
            session = session1
        )
        val speechSession2 = SpeechSession(
            guildId = 200L,
            channelId = 789L,
            session = session2
        )

        // Using reflection to add to the private map
        val field = speechService.javaClass.getDeclaredField("speechSessions")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val speechSessions = field.get(speechService) as MutableMap<Long, SpeechSession>
        speechSessions[100L] = speechSession1
        speechSessions[200L] = speechSession2

        val retrieved1 = speechService.getSpeechSession(100L)
        val retrieved2 = speechService.getSpeechSession(200L)

        assertNotNull(retrieved1)
        assertNotNull(retrieved2)
        assertEquals(100L, retrieved1?.guildId)
        assertEquals(200L, retrieved2?.guildId)
    }

    @Test
    fun testSpeechSessionRemoval() {
        val guildId = 123L
        val session = Session(guildId = guildId)
        val speechSession = SpeechSession(
            guildId = guildId,
            channelId = 456L,
            session = session
        )

        val field = speechService.javaClass.getDeclaredField("speechSessions")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val speechSessions = field.get(speechService) as MutableMap<Long, SpeechSession>

        speechSessions[guildId] = speechSession
        assertNotNull(speechService.getSpeechSession(guildId))

        speechSessions.remove(guildId)
        assertNull(speechService.getSpeechSession(guildId))
    }

    @Test
    fun testGetSpeechSessionMultipleCalls() {
        val guildId = 123L
        val session = Session(guildId = guildId)
        val speechSession = SpeechSession(
            guildId = guildId,
            channelId = 456L,
            session = session
        )

        val field = speechService.javaClass.getDeclaredField("speechSessions")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val speechSessions = field.get(speechService) as MutableMap<Long, SpeechSession>
        speechSessions[guildId] = speechSession

        val retrieved1 = speechService.getSpeechSession(guildId)
        val retrieved2 = speechService.getSpeechSession(guildId)

        assertNotNull(retrieved1)
        assertNotNull(retrieved2)
        assertEquals(retrieved1, retrieved2)
    }
}
