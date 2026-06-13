package dev.robothanzo.werewolf.domain

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document

/** Dashboard access role, derived from Discord permissions on the selected guild (FEATURES §11.1). */
enum class DashboardRole {
    /** Admin/manager on the guild → full control. */
    JUDGE,

    /** Everyone else with view access. */
    SPECTATOR,

    /** Logged in but hasn't confirmed a guild. */
    PENDING,

    /** Explicitly denied. */
    BLOCKED,
}

/**
 * Persisted per-(user, guild) dashboard role override. Absent rows fall back to the derived role;
 * judges can promote/demote others, which writes a row here.
 */
@Document(collection = "dashboard_users")
data class DashboardUser(
    @Id
    val id: String, // "${guildId}:${userId}"
    val guildId: Long,
    val userId: Long,
    var role: DashboardRole,
)
