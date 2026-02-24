package dev.robothanzo.werewolf.database.documents

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AuthSessionTest {

    @Test
    fun testAuthSessionRoles() {
        val judgeSession = AuthSession(role = UserRole.JUDGE)
        assertTrue(judgeSession.isJudge)
        assertFalse(judgeSession.isSpectator)
        assertTrue(judgeSession.isPrivileged)
        assertFalse(judgeSession.isBlocked)
        assertFalse(judgeSession.isPending)

        val spectatorSession = AuthSession(role = UserRole.SPECTATOR)
        assertFalse(spectatorSession.isJudge)
        assertTrue(spectatorSession.isSpectator)
        assertTrue(spectatorSession.isPrivileged)
        assertFalse(spectatorSession.isBlocked)
        assertFalse(spectatorSession.isPending)

        val pendingSession = AuthSession(role = UserRole.PENDING)
        assertFalse(pendingSession.isPrivileged)
        assertTrue(pendingSession.isPending)

        val blockedSession = AuthSession(role = UserRole.BLOCKED)
        assertFalse(blockedSession.isPrivileged)
        assertTrue(blockedSession.isBlocked)

        val nullRoleSession = AuthSession(role = null)
        assertFalse(nullRoleSession.isPrivileged)
        assertTrue(nullRoleSession.isPending)
    }

    @Test
    fun testUserRoleFromString() {
        assertTrue(UserRole.fromString("JUDGE") == UserRole.JUDGE)
        assertTrue(UserRole.fromString("judge") == UserRole.JUDGE)
        assertTrue(UserRole.fromString("SPECTATOR") == UserRole.SPECTATOR)
        assertTrue(UserRole.fromString("unknown") == UserRole.PENDING)
        assertTrue(UserRole.fromString(null) == UserRole.PENDING)
    }
}
