package dev.robothanzo.werewolf.service.impl

import dev.robothanzo.werewolf.WerewolfApplication
import dev.robothanzo.werewolf.database.documents.Session
import dev.robothanzo.werewolf.database.documents.UserRole
import dev.robothanzo.werewolf.game.GameStep
import dev.robothanzo.werewolf.security.GlobalWebSocketHandler
import dev.robothanzo.werewolf.security.SessionRepository
import dev.robothanzo.werewolf.service.DiscordService
import dev.robothanzo.werewolf.service.SpeechService
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mock
import org.mockito.Mockito.mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.atLeastOnce
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.*

class GameSessionServiceImplTest {
    @Mock
    private lateinit var sessionRepository: SessionRepository

    @Mock
    private lateinit var discordService: DiscordService

    @Mock
    private lateinit var webSocketHandler: GlobalWebSocketHandler

    @Mock
    private lateinit var speechService: SpeechService

    private lateinit var gameSessionService: GameSessionServiceImpl
    private val emptyStepList: List<GameStep> = emptyList()

    @BeforeEach
    fun setup() {
        MockitoAnnotations.openMocks(this)
        gameSessionService = GameSessionServiceImpl(
            sessionRepository,
            discordService,
            webSocketHandler,
            speechService,
            emptyStepList
        )
    }

    @Test
    fun testGetAllSessions() {
        val sessions = listOf(
            Session(guildId = 123L),
            Session(guildId = 456L)
        )

        whenever(sessionRepository.findAll()).thenReturn(sessions)

        val result = gameSessionService.getAllSessions()

        assertEquals(2, result.size)
        assertEquals(123L, result[0].guildId)
        assertEquals(456L, result[1].guildId)
        verify(sessionRepository).findAll()
    }

    @Test
    fun testGetSessionFound() {
        val guildId = 123L
        val session = Session(guildId = guildId)

        whenever(sessionRepository.findByGuildId(guildId)).thenReturn(Optional.of(session))

        val result = gameSessionService.getSession(guildId)

        assertTrue(result.isPresent)
        assertEquals(session, result.get())
    }

    @Test
    fun testGetSessionNotFound() {
        val guildId = 123L

        whenever(sessionRepository.findByGuildId(guildId)).thenReturn(Optional.empty())

        val result = gameSessionService.getSession(guildId)

        assertTrue(result.isEmpty)
    }

    @Test
    fun testCreateSession() {
        val guildId = 123L
        val session = Session(guildId = guildId)

        whenever(sessionRepository.save(any())).thenReturn(session)

        val result = gameSessionService.createSession(guildId)

        assertEquals(guildId, result.guildId)
        verify(sessionRepository).save(any())
    }

    @Test
    fun testSaveSession() {
        val session = Session(guildId = 123L)

        whenever(sessionRepository.save(session)).thenReturn(session)

        val result = gameSessionService.saveSession(session)

        assertEquals(session, result)
        verify(sessionRepository).save(session)
    }

    @Test
    fun testDeleteSession() {
        val guildId = 123L

        gameSessionService.deleteSession(guildId)

        verify(sessionRepository).deleteByGuildId(guildId)
    }

    @Test
    fun testSessionToJSON() {
        // Mock WerewolfApplication.policeService to avoid uninitialized property exception
        val mockPoliceService = mock(PoliceServiceImpl::class.java)
        try {
            WerewolfApplication::class.java.getDeclaredField("policeService").let {
                it.isAccessible = true
                it.set(null, mockPoliceService)
            }
        } catch (e: Exception) {
            // Skip if reflection fails
        }

        val session = Session(guildId = 123L)
        session.doubleIdentities = true
        session.day = 2
        session.roles = mutableListOf("狼人", "平民")

        val result = gameSessionService.sessionToJSON(session)

        assertEquals("123", result["guildId"])
        assertEquals(true, result["doubleIdentities"])
        assertEquals(2, result["day"])
        assertNotNull(result["players"])
    }

    @Test
    fun testPlayersToJSON() {
        val session = Session(guildId = 123L)
        val player1 = Session.Player(id = 1, roleId = 100L, channelId = 200L)
        val player2 = Session.Player(id = 2, roleId = 101L, channelId = 201L)
        session.players["1"] = player1
        session.players["2"] = player2

        val result = gameSessionService.playersToJSON(session)

        assertEquals(2, result.size)
    }

    @Test
    fun testSessionToSummaryJSON() {
        val session = Session(guildId = 123L)
        session.day = 3

        val result = gameSessionService.sessionToSummaryJSON(session)

        assertNotNull(result)
        assertEquals("123", result["guildId"])
    }

    @Test
    fun testGetGuildMembersSessionNotFound() {
        val guildId = 123L

        whenever(sessionRepository.findByGuildId(guildId)).thenReturn(Optional.empty())

        assertThrows<Exception> {
            gameSessionService.getGuildMembers(guildId)
        }
    }

    @Test
    fun testUpdateUserRoleSessionNotFound() {
        val guildId = 123L

        whenever(sessionRepository.findByGuildId(guildId)).thenReturn(Optional.empty())

        assertThrows<Exception> {
            gameSessionService.updateUserRole(guildId, 100L, UserRole.JUDGE)
        }
    }

    @Test
    fun testUpdateSessionSettingsSuccess() {
        val guildId = 123L
        val session = Session(guildId = guildId)

        whenever(sessionRepository.findByGuildId(guildId)).thenReturn(Optional.of(session))
        whenever(sessionRepository.save(any())).thenReturn(session)

        val settings = mapOf(
            "muteAfterSpeech" to true,
            "doubleIdentities" to false
        )
        gameSessionService.updateSettings(guildId, settings)

        assertEquals(true, session.muteAfterSpeech)
        assertEquals(false, session.doubleIdentities)
        // Verify save was called (may be called multiple times due to addLog)
        verify(sessionRepository, atLeastOnce()).save(any())
    }
}
