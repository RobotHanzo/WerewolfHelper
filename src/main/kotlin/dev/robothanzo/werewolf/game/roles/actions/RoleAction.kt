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
     * Whether this action should be executed immediately upon submission
     * for immediate feedback (e.g., Seer check results).
     */
    val isImmediate: Boolean
        get() = false

    /**
     * Whether this action can be performed multiple times in the same phase.
     * If false (default), submitting this action will mark the player as 'actionSubmitted',
     * preventing further actions in the same phase.
     * Judges bypass this restriction.
     */
    val allowMultiplePerPhase: Boolean
        get() = false

    /**
     * Number of times this action has been used by the actor in the current session.
     */
    fun getUsageCount(session: Session, actor: Long): Int {
        val userUsage = session.stateData.actionUsage[actor.toString()] ?: return 0
        return userUsage.getOrDefault(actionId, 0)
    }

    /**
     * Whether this action is available for the actor in the current session state.
     */
    fun isAvailable(session: Session, actor: Long): Boolean {
        // Check usage limit
        if (usageLimit != -1 && getUsageCount(session, actor) >= usageLimit) {
            return false
        }
        return true
    }

    /**
     * Validate a potential action submission.
     * 
     * @return null if valid, or an error message if invalid.
     */
    fun validate(session: Session, actor: Long, targets: List<Long>): String? {
        // Basic actor check
        val actorPlayer = session.getPlayer(actor) ?: return "Actor not found"
        if (!actorPlayer.alive) return "Actor not alive"
        if (actorPlayer.roles?.contains(roleName) != true) return "Actor does not have role $roleName"

        // Usage limit check
        if (!isAvailable(session, actor)) return "已達到使用限制"

        // Target count check
        if (targetCount > 0 && targets.size != targetCount) return "無效的目標數量"

        // Target validity check
        for (targetId in targets) {
            val targetPlayer = session.getPlayer(targetId) ?: return "目標玩家不存在"
            if (requiresAliveTarget && !targetPlayer.alive) return "目標必須是生存狀態"
        }

        return null
    }

    /**
     * Hook called when an action is successfully submitted.
     */
    fun onSubmitted(session: Session, actor: Long, targets: List<Long>) {
        // Default: update usage count
        val userUsage = session.stateData.actionUsage.getOrPut(actor.toString()) { mutableMapOf() }
        val currentCount = userUsage.getOrDefault(actionId, 0)
        userUsage[actionId] = currentCount + 1
    }

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

/**
 * Abstract base class for role actions providing common functionality.
 */
abstract class BaseRoleAction(
    override val actionId: String,
    override val roleName: String,
    override val actionName: String,
    override val priority: Int,
    override val timing: ActionTiming,
    override val targetCount: Int = 1,
    override val usageLimit: Int = -1,
    override val requiresAliveTarget: Boolean = true,
    override val isImmediate: Boolean = false,
    override val allowMultiplePerPhase: Boolean = false
) : RoleAction

/**
 * A generic implementation of RoleAction for custom or dynamically defined actions.
 */
data class GenericRoleAction(
    override val actionId: String,
    override val roleName: String,
    override val actionName: String,
    override val priority: Int,
    override val timing: ActionTiming,
    override val targetCount: Int,
    override val usageLimit: Int,
    override val requiresAliveTarget: Boolean = true,
    override val isImmediate: Boolean = false,
    override val allowMultiplePerPhase: Boolean = false
) : RoleAction {
    override fun execute(
        session: Session,
        action: RoleActionInstance,
        accumulatedState: ActionExecutionResult
    ): ActionExecutionResult = accumulatedState
}
