package dev.robothanzo.werewolf.utils

import dev.robothanzo.werewolf.database.documents.AuthSession
import dev.robothanzo.werewolf.database.documents.UserRole
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.whenever
import org.springframework.security.core.Authentication
import org.springframework.security.core.context.SecurityContext
import org.springframework.security.core.context.SecurityContextHolder

class IdentityUtilsTest {
    @Mock
    private lateinit var securityContext: SecurityContext

    @Mock
    private lateinit var authentication: Authentication

    private lateinit var identityUtils: IdentityUtils

    @BeforeEach
    fun setup() {
        MockitoAnnotations.openMocks(this)
        identityUtils = IdentityUtils()
    }

    @Test
    fun testGetCurrentUserWhenNoAuthentication() {
        SecurityContextHolder.setContext(securityContext)
        whenever(securityContext.authentication).thenReturn(null)

        val result = identityUtils.getCurrentUser()

        assertFalse(result.isPresent)
    }

    @Test
    fun testGetCurrentUserWhenAuthenticationExists() {
        val authSession = AuthSession(guildId = "123", role = UserRole.JUDGE)

        SecurityContextHolder.setContext(securityContext)
        whenever(securityContext.authentication).thenReturn(authentication)
        whenever(authentication.principal).thenReturn(authSession)

        val result = identityUtils.getCurrentUser()

        assertTrue(result.isPresent)
        assertEquals(authSession, result.get())
        assertEquals("123", result.get().guildId)
        assertEquals(UserRole.JUDGE, result.get().role)
    }

    @Test
    fun testGetCurrentUserWhenPrincipalIsNotAuthSession() {
        SecurityContextHolder.setContext(securityContext)
        whenever(securityContext.authentication).thenReturn(authentication)
        whenever(authentication.principal).thenReturn("not-an-auth-session")

        val result = identityUtils.getCurrentUser()

        assertFalse(result.isPresent)
    }

    @Test
    fun testHasAccessWithMatchingGuild() {
        val authSession = AuthSession(guildId = "123", role = UserRole.JUDGE)

        SecurityContextHolder.setContext(securityContext)
        whenever(securityContext.authentication).thenReturn(authentication)
        whenever(authentication.principal).thenReturn(authSession)

        val result = identityUtils.hasAccess(123L)

        assertTrue(result)
    }

    @Test
    fun testHasAccessWithNonMatchingGuild() {
        val authSession = AuthSession(guildId = "123", role = UserRole.JUDGE)

        SecurityContextHolder.setContext(securityContext)
        whenever(securityContext.authentication).thenReturn(authentication)
        whenever(authentication.principal).thenReturn(authSession)

        val result = identityUtils.hasAccess(456L)

        assertFalse(result)
    }

    @Test
    fun testHasAccessWithNoUser() {
        SecurityContextHolder.setContext(securityContext)
        whenever(securityContext.authentication).thenReturn(null)

        val result = identityUtils.hasAccess(123L)

        assertFalse(result)
    }

    @Test
    fun testHasRoleWithMatchingRole() {
        val authSession = AuthSession(guildId = "123", role = UserRole.JUDGE)

        SecurityContextHolder.setContext(securityContext)
        whenever(securityContext.authentication).thenReturn(authentication)
        whenever(authentication.principal).thenReturn(authSession)

        val result = identityUtils.hasRole(UserRole.JUDGE)

        assertTrue(result)
    }

    @Test
    fun testHasRoleWithNonMatchingRole() {
        val authSession = AuthSession(guildId = "123", role = UserRole.JUDGE)

        SecurityContextHolder.setContext(securityContext)
        whenever(securityContext.authentication).thenReturn(authentication)
        whenever(authentication.principal).thenReturn(authSession)

        val result = identityUtils.hasRole(UserRole.SPECTATOR)

        assertFalse(result)
    }

    @Test
    fun testIsJudgeWhenUserIsJudge() {
        val authSession = AuthSession(guildId = "123", role = UserRole.JUDGE)

        SecurityContextHolder.setContext(securityContext)
        whenever(securityContext.authentication).thenReturn(authentication)
        whenever(authentication.principal).thenReturn(authSession)

        assertTrue(identityUtils.isJudge)
    }

    @Test
    fun testIsJudgeWhenUserIsNotJudge() {
        val authSession = AuthSession(guildId = "123", role = UserRole.SPECTATOR)

        SecurityContextHolder.setContext(securityContext)
        whenever(securityContext.authentication).thenReturn(authentication)
        whenever(authentication.principal).thenReturn(authSession)

        assertFalse(identityUtils.isJudge)
    }

    @Test
    fun testIsSpectatorWhenUserIsSpectator() {
        val authSession = AuthSession(guildId = "123", role = UserRole.SPECTATOR)

        SecurityContextHolder.setContext(securityContext)
        whenever(securityContext.authentication).thenReturn(authentication)
        whenever(authentication.principal).thenReturn(authSession)

        assertTrue(identityUtils.isSpectator)
    }

    @Test
    fun testCanManageWithJudgeInCorrectGuild() {
        val authSession = AuthSession(guildId = "123", role = UserRole.JUDGE)

        SecurityContextHolder.setContext(securityContext)
        whenever(securityContext.authentication).thenReturn(authentication)
        whenever(authentication.principal).thenReturn(authSession)

        assertTrue(identityUtils.canManage(123L))
    }

    @Test
    fun testCanManageWithJudgeInWrongGuild() {
        val authSession = AuthSession(guildId = "123", role = UserRole.JUDGE)

        SecurityContextHolder.setContext(securityContext)
        whenever(securityContext.authentication).thenReturn(authentication)
        whenever(authentication.principal).thenReturn(authSession)

        assertFalse(identityUtils.canManage(456L))
    }

    @Test
    fun testCanManageWithNonJudge() {
        val authSession = AuthSession(guildId = "123", role = UserRole.SPECTATOR)

        SecurityContextHolder.setContext(securityContext)
        whenever(securityContext.authentication).thenReturn(authentication)
        whenever(authentication.principal).thenReturn(authSession)

        assertFalse(identityUtils.canManage(123L))
    }

    @Test
    fun testCanViewWithPrivilegedUserInCorrectGuild() {
        val authSession = AuthSession(guildId = "123", role = UserRole.JUDGE)

        SecurityContextHolder.setContext(securityContext)
        whenever(securityContext.authentication).thenReturn(authentication)
        whenever(authentication.principal).thenReturn(authSession)

        assertTrue(identityUtils.canView(123L))
    }
}
