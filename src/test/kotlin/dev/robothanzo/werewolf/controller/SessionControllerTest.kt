package dev.robothanzo.werewolf.controller

import dev.robothanzo.werewolf.WerewolfApplication
import dev.robothanzo.werewolf.controller.dto.SessionResponse
import dev.robothanzo.werewolf.controller.dto.SessionSummaryResponse
import dev.robothanzo.werewolf.database.documents.AuthSession
import dev.robothanzo.werewolf.database.documents.Session
import dev.robothanzo.werewolf.database.documents.UserRole
import dev.robothanzo.werewolf.service.GameSessionService
import dev.robothanzo.werewolf.utils.IdentityUtils
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Member
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.http.HttpStatus
import java.util.*

class SessionControllerTest {

    @Mock
    private lateinit var gameSessionService: GameSessionService

    @Mock
    private lateinit var identityUtils: IdentityUtils

    private lateinit var sessionController: SessionController

    @BeforeEach
    fun setup() {
        MockitoAnnotations.openMocks(this)
        sessionController = SessionController(gameSessionService, identityUtils)

        val mockJda = mock<JDA>()
        WerewolfApplication.jda = mockJda
    }

    @Test
    fun testGetAllSessions_Unauthenticated() {
        whenever(identityUtils.getCurrentUser()).thenReturn(Optional.empty())

        val response = sessionController.getAllSessions()

        assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
    }

    @Test
    fun testGetAllSessions_Success() {
        val user = AuthSession(userId = "user1", guildId = "123", role = UserRole.SPECTATOR)
        whenever(identityUtils.getCurrentUser()).thenReturn(Optional.of(user))

        val guildMock = mock<Guild>()
        whenever(guildMock.name).thenReturn("Test Guild")
        whenever(guildMock.iconUrl).thenReturn("icon.png")
        whenever(guildMock.getMemberById("user1")).thenReturn(mock<Member>())

        val session = Session(guildId = 123L)
        // We need to mock WerewolfApplication.jda.getGuildById(123L) to return guildMock
        whenever(WerewolfApplication.jda.getGuildById(123L)).thenReturn(guildMock)

        whenever(gameSessionService.getAllSessions()).thenReturn(listOf(session))

        val response = sessionController.getAllSessions()

        assertEquals(HttpStatus.OK, response.statusCode)
        val body = response.body as SessionSummaryResponse
        assertEquals(1, body.data.size)
        assertEquals("123", body.data[0].guildId)
    }

    @Test
    fun testGetSession_Found() {
        val guildId = 123L
        val session = Session(guildId = guildId)
        whenever(gameSessionService.getSession(guildId)).thenReturn(Optional.of(session))

        val response = sessionController.getSession(guildId.toString())

        assertEquals(HttpStatus.OK, response.statusCode)
        val body = response.body as SessionResponse
        assertEquals(guildId, body.data.guildId)
    }

    @Test
    fun testGetSession_NotFound() {
        val guildId = 123L
        whenever(gameSessionService.getSession(guildId)).thenReturn(Optional.empty())

        val response = sessionController.getSession(guildId.toString())

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
    }
}
