package dev.robothanzo.werewolf.service

import dev.robothanzo.werewolf.database.documents.Session
import dev.robothanzo.werewolf.game.model.DeathCause
import dev.robothanzo.werewolf.game.model.RoleActionInstance
import dev.robothanzo.werewolf.game.roles.actions.RoleAction

data class NightResolutionResult(
    val deaths: Map<DeathCause, List<Long>>, // cause -> list of user IDs
    val saved: List<Long>,
    val checked: Map<Long, String> // seer userId -> result (werewolf/villager)
)

interface RoleActionService {

    /**
     * Submit a role action from a player or judge
     */
    fun submitAction(
        guildId: Long,
        actionDefinitionId: String,
        actorUserId: Long,
        targetUserIds: List<Long>,
        submittedBy: String // "PLAYER" or "JUDGE"
    ): Map<String, Any>

    /**
     * Get available actions for a specific player based on their roles
     */
    fun getAvailableActionsForPlayer(session: Session, userId: Long): List<RoleAction>

    /**
     * Get all available actions that can be manually cast by judge
     */
    fun getAvailableActionsForJudge(session: Session): Map<Long, List<RoleAction>>

    /**
     * Resolve all pending night actions and return death results
     */
    fun resolveNightActions(session: Session): NightResolutionResult

    /**
     * Check if an action is available for a player (has usage remaining)
     */
    fun isActionAvailable(session: Session, userId: Long, actionDefinitionId: String): Boolean

    /**
     * Get all pending actions for a session
     */
    fun getPendingActions(session: Session): List<RoleActionInstance>

    /**
     * Get usage count for a specific action
     */
    fun getActionUsageCount(session: Session, userId: Long, actionDefinitionId: String): Int

    /**
     * Execute death trigger actions (e.g., Hunter revenge, Wolf King revenge)
     * Returns the list of user IDs killed by death triggers
     */
    fun executeDeathTriggers(session: Session): List<Long>

    /**
     * Check if a player has pending death trigger actions available
     */
    fun hasDeathTriggerAvailable(session: Session, userId: Long): Boolean
}
