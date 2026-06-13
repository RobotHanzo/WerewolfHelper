package dev.robothanzo.werewolf.security

import dev.robothanzo.werewolf.discord.DiscordProperties
import dev.robothanzo.werewolf.domain.DashboardRole
import dev.robothanzo.werewolf.domain.repo.DashboardUserRepository
import dev.robothanzo.werewolf.domain.repo.GameSessionRepository
import org.springframework.stereotype.Service

/**
 * Derives a user's dashboard role on a guild (FEATURES §11.1): explicit overrides win, then trusted
 * server-creators are judges, otherwise spectator. **Active players are locked out** — a logged-in
 * user holding a seat in the running game must not reach the dashboard (they'd see all identities).
 */
@Service
class DashboardRoleService(
    private val sessions: GameSessionRepository,
    private val dashboardUsers: DashboardUserRepository,
    private val discordProperties: DiscordProperties,
) {

    fun roleFor(guildId: Long, userId: Long): DashboardRole {
        dashboardUsers.findByGuildIdAndUserId(guildId, userId)?.let { return it.role }
        if (userId in discordProperties.serverCreatorIds) return DashboardRole.JUDGE
        return DashboardRole.SPECTATOR
    }

    /** Whether [userId] currently holds a seat in this guild's running game. */
    fun isActivePlayer(guildId: Long, userId: Long): Boolean {
        val session = sessions.findById(guildId).orElse(null) ?: return false
        if (!session.assigned) return false
        return session.seats.any { it.memberId == userId }
    }

    fun canManage(guildId: Long, userId: Long): Boolean =
        !isActivePlayer(guildId, userId) && roleFor(guildId, userId) == DashboardRole.JUDGE

    fun canView(guildId: Long, userId: Long): Boolean {
        if (isActivePlayer(guildId, userId)) return false
        return roleFor(guildId, userId) in setOf(DashboardRole.JUDGE, DashboardRole.SPECTATOR)
    }
}
