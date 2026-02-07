package dev.robothanzo.werewolf.service

import dev.robothanzo.werewolf.database.documents.Session
import dev.robothanzo.werewolf.game.model.ActionStatus
import dev.robothanzo.werewolf.game.model.DeathCause
import dev.robothanzo.werewolf.game.model.RoleActionInstance
import dev.robothanzo.werewolf.game.roles.actions.RoleAction

data class NightResolutionResult(
    val deaths: Map<DeathCause, List<Int>>, // cause -> list of player IDs (Int)
    val saved: List<Int>, // player IDs (Int)
)

interface RoleActionService {

    /**
     * Submit a role action from a player or judge
     */
    fun submitAction(
        guildId: Long,
        actionDefinitionId: String,
        actorPlayerId: Int,
        targetPlayerIds: MutableList<Int>,
        submittedBy: String, // "PLAYER" or "JUDGE"
        metadata: Map<String, Any> = emptyMap()
    ): Map<String, Any>

    /**
     * Get available actions for a specific player based on their roles
     */
    fun getAvailableActionsForPlayer(session: Session, playerId: Int): List<RoleAction>

    /**
     * Resolve all pending night actions and return death results
     */
    fun resolveNightActions(session: Session): NightResolutionResult

    /**
     * Check if an action is available for a player (has usage remaining)
     */
    fun isActionAvailable(session: Session, playerId: Int, actionDefinitionId: String): Boolean

    /**
     * Get all pending actions for a session
     */
    fun getPendingActions(session: Session): List<RoleActionInstance>

    /**
     * Get usage count for a specific action
     */
    fun getActionUsageCount(session: Session, playerId: Int, actionDefinitionId: String): Int

    /**
     * Execute death trigger actions (e.g., Hunter revenge, Wolf King revenge)
     * Returns the list of player IDs killed by death triggers
     */
    fun executeDeathTriggers(session: Session): List<Int>

    /**
     * Check if a player has pending death trigger actions available
     */
    fun hasDeathTriggerAvailable(session: Session, playerId: Int): Boolean

    /**
     * Update the UI status for a role action (e.g., set to ACTING or SUBMITTED).
     * Provide session to avoid duplicate fetches and potential race conditions.
     */
    fun updateActionStatus(
        guildId: Long,
        actorPlayerId: Int,
        status: ActionStatus,
        actionId: String? = null,
        targetPlayerIds: List<Int> = emptyList(),
        session: Session? = null
    )
}
