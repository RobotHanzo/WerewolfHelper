package dev.robothanzo.werewolf.game.roles.actions

import dev.robothanzo.werewolf.database.documents.Session
import dev.robothanzo.werewolf.game.model.ActionDefinitionId
import dev.robothanzo.werewolf.game.model.ActionTiming
import dev.robothanzo.werewolf.game.model.DeathCause
import dev.robothanzo.werewolf.game.model.RoleActionInstance

/**
 * Represents the result of executing a role action
 */
data class ActionExecutionResult(
    var deaths: MutableMap<DeathCause, MutableList<Int>> = mutableMapOf(), // cause -> list of player IDs (Int)
    var saved: MutableList<Int> = mutableListOf(), // player IDs (Int)
    var protectedPlayers: MutableSet<Int> = mutableSetOf() // player IDs (Int)
)

/**
 * Base interface for all role actions.
 * Each action is responsible for modifying the session state and returning execution results.
 */
interface RoleAction {
    /**
     * The unique identifier of this action
     */
    val actionId: ActionDefinitionId

    /**
     * The display name of this action (e.g., "查驗", "擊殺", "毒殺")
     */
    val actionName: String
        get() = actionId.actionName

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
     * Whether this action is optional. If false, the player MUST select a target.
     * Skipping or timing out will result in a random selection (or other mandatory behavior).
     */
    val isOptional: Boolean
        get() = true

    fun getUsageCount(session: Session, actor: Int): Int {
        val executedCount = session.stateData.executedActions.values.flatten()
            .count { it.actor == actor && it.actionDefinitionId == actionId }
        return executedCount
    }

    /**
     * Whether this action is available for the actor in the current session state.
     */
    fun isAvailable(session: Session, actor: Int): Boolean {
        // Check usage limit
        if (usageLimit != -1 && getUsageCount(session, actor) >= usageLimit) return false

        // For death triggers, require explicit grant in playerOwnedActions
        if (timing == ActionTiming.DEATH_TRIGGER) {
            return (session.stateData.playerOwnedActions[actor]?.get(actionId.toString()) ?: 0) > 0
        }

        return true
    }

    /**
     * Hook called when a player dies, if this action is part of the player's role
     * or is owned by the player.
     */
    fun onDeath(session: Session, actor: Int, cause: DeathCause) {}

    /**
     * Validate a potential action submission.
     * 
     * @return null if valid, or an error message if invalid.
     */
    fun validate(session: Session, actor: Int, targets: List<Int>): String? {
        // Basic actor check
        val actorPlayer = session.getPlayer(actor.toString()) ?: return "Actor not found"
        if (!actorPlayer.alive && timing != ActionTiming.DEATH_TRIGGER) return "你已死亡，無法執行此動作"

        // Usage limit check
        if (!isAvailable(session, actor)) return "已達到使用限制"

        // Target count check
        if (targetCount > 0 && targets.size != targetCount) return "無效的目標數量"

        // Target validity check
        for (targetId in targets) {
            val targetPlayer = session.getPlayer(targetId.toString()) ?: return "目標玩家不存在"
            if (requiresAliveTarget && !targetPlayer.alive) return "目標必須是生存狀態"
        }

        return null
    }

    fun onSubmitted(session: Session, actor: Int, targets: List<Int>) {
        // Usage count is now derived from history, no explicit increment needed
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
        actor: Int,
        alivePlayers: List<Int>,
        accumulatedState: ActionExecutionResult = ActionExecutionResult()
    ): List<Int> {
        // Default implementation: all alive players are eligible
        return alivePlayers
    }
}

/**
 * Abstract base class for role actions providing common functionality.
 */
abstract class BaseRoleAction(
    override val actionId: ActionDefinitionId,
    override val priority: Int,
    override val timing: ActionTiming,
    override val targetCount: Int = 1,
    override val usageLimit: Int = -1,
    override val requiresAliveTarget: Boolean = true,
    override val isImmediate: Boolean = false,
    override val allowMultiplePerPhase: Boolean = false,
    override val isOptional: Boolean = true
) : RoleAction
