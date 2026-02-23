package dev.robothanzo.werewolf.controller

import dev.robothanzo.werewolf.WerewolfApplication
import dev.robothanzo.werewolf.controller.dto.AuthData
import dev.robothanzo.werewolf.controller.dto.AuthResponse
import dev.robothanzo.werewolf.database.documents.AuthSession
import dev.robothanzo.werewolf.database.documents.UserRole
import jakarta.servlet.http.HttpSession
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.User
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever
import org.springframework.http.HttpStatus
import java.util.*

class AuthControllerTest {

    @InjectMocks
    private lateinit var authController: AuthController

    @Mock
    private lateinit var session: HttpSession

    @Mock
    private lateinit var jda: JDA

    @BeforeEach
    fun setup() {
        MockitoAnnotations.openMocks(this)
        WerewolfApplication.jda = jda
    }

    @Test
    fun testSelectGuild_Success() {
        val user = AuthSession(userId = "user1", guildId = null, role = UserRole.PENDING)
        whenever(session.getAttribute("user")).thenReturn(user)

        val guildId = "123"
        val guildMock = mock(Guild::class.java)
        val memberMock = mock(Member::class.java)
        whenever(jda.getGuildById(123L)).thenReturn(guildMock)
        whenever(guildMock.getMemberById("user1")).thenReturn(memberMock)
        // Assume isAdmin logic - isAdmin is an extension function, mocking it might be hard.
        // It likely checks permissions.
        // If it's an extension function, we cannot easily mock it unless we mock the Member interface completely
        // and isAdmin calls member methods.
        // dev.robothanzo.werewolf.utils.isAdmin checks member.hasPermission(Administrator) or is owner.

        // However, isAdmin is an extension function.
        // Let's assume it returns false for mock unless we configure it.
        // But for this test, we just check if it returns OK.

        val response = authController.selectGuild(guildId, session)

        assertEquals(HttpStatus.OK, response.statusCode)
        val body = response.body as AuthResponse
        assertEquals(guildId, body.data.user.guildId)
        verify(session).setAttribute(eq("user"), any())
    }

    @Test
    fun testSelectGuild_Unauthenticated() {
        whenever(session.getAttribute("user")).thenReturn(null)

        val response = authController.selectGuild("123", session)

        assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
    }

    @Test
    fun testSelectGuild_GuildNotFound() {
        val user = AuthSession(userId = "user1")
        whenever(session.getAttribute("user")).thenReturn(user)
        whenever(jda.getGuildById(123L)).thenReturn(null)

        val response = authController.selectGuild("123", session)

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
    }

    @Test
    fun testMe_Authenticated() {
        val user = AuthSession(userId = "user1", guildId = "123")
        whenever(session.getAttribute("user")).thenReturn(user)

        val discordUser = mock(User::class.java)
        whenever(discordUser.name).thenReturn("TestUser")
        whenever(discordUser.effectiveAvatarUrl).thenReturn("avatar.png")
        whenever(jda.getUserById("user1")).thenReturn(discordUser)

        val response = authController.me(session)

        assertEquals(HttpStatus.OK, response.statusCode)
        val body = response.body as AuthResponse
        assertEquals("user1", body.data.user.userId)
        assertEquals("TestUser", body.data.username)
    }

    @Test
    fun testMe_Unauthenticated() {
        whenever(session.getAttribute("user")).thenReturn(null)

        val response = authController.me(session)

        assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
    }

    @Test
    fun testLogout() {
        val response = authController.logout(session)

        assertEquals(HttpStatus.OK, response.statusCode)
        verify(session).invalidate()
    }
}
