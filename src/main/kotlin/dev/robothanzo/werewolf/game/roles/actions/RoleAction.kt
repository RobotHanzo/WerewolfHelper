package dev.robothanzo.werewolf.game.roles.actions

import dev.robothanzo.werewolf.database.documents.Session
import dev.robothanzo.werewolf.game.model.ActionTiming
import dev.robothanzo.werewolf.game.model.DeathCause
import dev.robothanzo.werewolf.game.model.RoleActionInstance

/**
 * Represents the result of executing a role action
 */
data class ActionExecutionResult(
    val deaths: Map<DeathCause, List<Long>> = emptyMap(), // cause -> list of user IDs
    val saved: List<Long> = emptyList(),
    val protectedPlayers: Set<Long> = emptySet(),
    val metadata: Map<String, Any> = emptyMap() // For storing additional action-specific data like seer checks
)

/**
 * Base interface for all role actions.
 * Each action is responsible for modifying the session state and returning execution results.
 */
interface RoleAction {
    /**
     * The unique identifier of this action
     */
    val actionId: String

    /**
     * The role name this action belongs to
     */
    val roleName: String

    /**
     * The display name of this action (e.g., "查驗", "擊殺", "毒殺")
     */
    val actionName: String

    /**
     * The priority order for this action during resolution.
     * Lower numbers execute first.
     */
    val priority: Int

    /**
     * When this action can be performed (NIGHT, DAY, or ANYTIME)
     */
    val timing: ActionTiming

    /**
     * Number of targets required for this action
     */
    val targetCount: Int

    /**
     * Maximum number of times this action can be used (-1 for unlimited)
     */
    val usageLimit: Int

    /**
     * Whether this action requires the target to be alive
     */
    val requiresAliveTarget: Boolean
        get() = true

    /**
     * Execute this action with the given context.
     * 
     * @param session The current game session
     * @param action The action instance to execute
     * @param accumulatedState The accumulated state from previously executed actions
     * @return The result of executing this action
     */
    fun execute(
        session: Session,
        action: RoleActionInstance,
        accumulatedState: ActionExecutionResult
    ): ActionExecutionResult

    /**
     * Filter the list of alive players to determine which ones are eligible targets for this action.
     * 
     * @param session The current game session
     * @param actor The player performing the action
     * @param alivePlayers List of all alive players
     * @param accumulatedState The accumulated state from previously executed actions (for actions that depend on prior actions)
     * @return Filtered list of eligible target player IDs
     */
    fun eligibleTargets(
        session: Session,
        actor: Long,
        alivePlayers: List<Long>,
        accumulatedState: ActionExecutionResult = ActionExecutionResult()
    ): List<Long> {
        // Default implementation: all alive players are eligible
        return alivePlayers
    }
}
