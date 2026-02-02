package dev.robothanzo.werewolf.utils

import dev.robothanzo.werewolf.database.documents.AuthSession
import dev.robothanzo.werewolf.database.documents.UserRole
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import java.util.*

@Component
class IdentityUtils {
    fun getCurrentUser(): Optional<AuthSession> {
        val auth = SecurityContextHolder.getContext().authentication
        if (auth != null && auth.principal is AuthSession) {
            return Optional.of(auth.principal as AuthSession)
        }
        return Optional.empty()
    }

    fun hasAccess(guildId: Long): Boolean {
        return getCurrentUser()
            .map { user: AuthSession ->
                (guildId.toString() == user.guildId)
            }
            .orElse(false)
    }

    fun hasRole(role: UserRole): Boolean {
        return getCurrentUser()
            .map { user: AuthSession ->
                (user.role == role)
            }
            .orElse(false)
    }

    val isJudge: Boolean
        get() = getCurrentUser().map { obj: AuthSession -> obj.isJudge }.orElse(false)

    val isSpectator: Boolean
        get() = getCurrentUser().map { obj: AuthSession -> obj.isSpectator }.orElse(false)

    val isPrivileged: Boolean
        get() = getCurrentUser().map { obj: AuthSession -> obj.isPrivileged }.orElse(false)

    fun canManage(guildId: Long): Boolean {
        return hasAccess(guildId) && isJudge
    }

    fun canView(guildId: Long): Boolean {
        return hasAccess(guildId) && isPrivileged
    }
}
