package dev.robothanzo.werewolf.game.model

import dev.robothanzo.werewolf.database.documents.Session
import dev.robothanzo.werewolf.game.roles.actions.RoleAction

/**
 * Context provided to role even listeners
 */
data class RoleEventContext(
    val session: Session,
    val eventType: RoleEventType,
    val actorUserId: Long,
    val metadata: Map<String, Any> = emptyMap()
)

/**
 * Interface representing a game role and its behaviors.
 */
interface Role {
    /**
     * Unique identifier for this role (e.g., "SEER", "WEREWOLF")
     */
    val roleName: String

    /**
     * The camp this role belongs to
     */
    val camp: Camp

    /**
     * Get the list of actions this role can perform
     */
    fun getActions(): List<RoleAction>

    /**
     * Hook called when a player with this role dies
     */
    fun onDeath(context: RoleEventContext) {}

    /**
     * Hook called at the start of each night phase
     */
    fun onNightStart(session: Session, actorUserId: Long) {}

    /**
     * Hook called when a player is expelled (voted out)
     */
    fun onPlayerExpelled(context: RoleEventContext) {}
}

/**
 * Base class for standard roles to reduce boilerplate
 */
abstract class BaseRole(
    override val roleName: String,
    override val camp: Camp
) : Role {
    override fun getActions(): List<RoleAction> = emptyList()
}
