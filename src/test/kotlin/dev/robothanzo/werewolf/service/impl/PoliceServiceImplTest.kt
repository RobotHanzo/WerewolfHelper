package dev.robothanzo.werewolf.service.impl

import dev.robothanzo.werewolf.database.SessionRepository
import dev.robothanzo.werewolf.database.documents.Session
import dev.robothanzo.werewolf.model.PoliceSession
import dev.robothanzo.werewolf.service.DiscordService
import dev.robothanzo.werewolf.service.GameSessionService
import dev.robothanzo.werewolf.service.SpeechService
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations

class PoliceServiceImplTest {
    @Mock
    private lateinit var sessionRepository: SessionRepository

    @Mock
    private lateinit var discordService: DiscordService

    @Mock
    private lateinit var gameSessionService: GameSessionService

    @Mock
    private lateinit var speechService: SpeechService

    private lateinit var policeService: PoliceServiceImpl

    @BeforeEach
    fun setup() {
        MockitoAnnotations.openMocks(this)
        policeService = PoliceServiceImpl(
            gameSessionService,
            speechService
        )
    }

    @Test
    fun testPoliceServiceSessionsMapInitialized() {
        assertNotNull(policeService.sessions)
        assertTrue(policeService.sessions.isEmpty())
    }

    @Test
    fun testPoliceServiceSessionsMapCanStoreItems() {
        val guildId = 123L
        val session = Session(guildId = guildId)
        val policeSession = PoliceSession(
            guildId = guildId,
            channelId = 456L,
            session = session
        )

        policeService.sessions[guildId] = policeSession

        assertEquals(1, policeService.sessions.size)
        assertNotNull(policeService.sessions[guildId])
        assertEquals(guildId, policeService.sessions[guildId]?.guildId)
    }

    @Test
    fun testPoliceServiceSessionsMapMultipleItems() {
        val session1 = Session(guildId = 100L)
        val session2 = Session(guildId = 200L)

        val policeSession1 = PoliceSession(
            guildId = 100L,
            channelId = 456L,
            session = session1
        )
        val policeSession2 = PoliceSession(
            guildId = 200L,
            channelId = 789L,
            session = session2
        )

        policeService.sessions[100L] = policeSession1
        policeService.sessions[200L] = policeSession2

        assertEquals(2, policeService.sessions.size)
        assertEquals(100L, policeService.sessions[100L]?.guildId)
        assertEquals(200L, policeService.sessions[200L]?.guildId)
    }

    @Test
    fun testPoliceServiceSessionsMapRemoveItem() {
        val guildId = 123L
        val session = Session(guildId = guildId)
        val policeSession = PoliceSession(
            guildId = guildId,
            channelId = 456L,
            session = session
        )

        policeService.sessions[guildId] = policeSession
        assertEquals(1, policeService.sessions.size)

        policeService.sessions.remove(guildId)
        assertEquals(0, policeService.sessions.size)
    }

    @Test
    fun testPoliceServiceSessionsConcurrentHashMap() {
        // Test that the sessions map is thread-safe (ConcurrentHashMap)
        val threads = mutableListOf<Thread>()

        repeat(5) { i ->
            val thread = Thread {
                val guildId = (100L + i)
                val session = Session(guildId = guildId)
                val policeSession = PoliceSession(
                    guildId = guildId,
                    channelId = (456L + i),
                    session = session
                )
                policeService.sessions[guildId] = policeSession
            }
            threads.add(thread)
            thread.start()
        }

        threads.forEach { it.join() }
        assertEquals(5, policeService.sessions.size)
    }
}
