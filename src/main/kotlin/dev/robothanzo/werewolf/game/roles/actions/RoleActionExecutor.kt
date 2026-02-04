package dev.robothanzo.werewolf.game.roles.actions

import dev.robothanzo.werewolf.database.documents.Session
import dev.robothanzo.werewolf.game.model.ActionSubmissionSource
import dev.robothanzo.werewolf.game.model.RoleActionInstance
import org.springframework.stereotype.Component

/**
 * Executes role actions in priority order and accumulates their results
 */
@Component
class RoleActionExecutor {

    private val actionRegistry = mapOf(
        "WEREWOLF_KILL" to WerewolfKillAction(),
        "WITCH_ANTIDOTE" to WitchAntidoteAction(),
        "WITCH_POISON" to WitchPoisonAction(),
        "SEER_CHECK" to SeerCheckAction(),
        "GUARD_PROTECT" to GuardProtectAction(),
        "DEATH_RESOLUTION" to DeathResolutionAction(),
        "HUNTER_REVENGE" to HunterRevengeAction(),
        "WOLF_KING_REVENGE" to WolfKingRevengeAction()
    )

    /**
     * Execute all pending actions in priority order
     * 
     * @param session The current game session
     * @param pendingActions The list of pending actions to execute
     * @return The accumulated result of all action executions
     */
    fun executeActions(
        session: Session,
        pendingActions: List<RoleActionInstance>
    ): ActionExecutionResult {
        // Sort actions by priority (lower number = higher priority = executes first)
        val sortedActions = pendingActions.sortedBy { action ->
            actionRegistry[action.actionDefinitionId]?.priority ?: Int.MAX_VALUE
        }

        // Execute actions one by one, accumulating state
        var result = ActionExecutionResult()

        for (action in sortedActions) {
            val executor = actionRegistry[action.actionDefinitionId]
            if (executor != null) {
                result = executor.execute(session, action, result)
            }
        }

        // Execute death resolution as final step
        val deathResolution = DeathResolutionAction()
        val dummyAction = RoleActionInstance(
            actor = 0L,
            actionDefinitionId = "DEATH_RESOLUTION",
            targets = emptyList(),
            submittedBy = ActionSubmissionSource.JUDGE
        )
        result = deathResolution.execute(session, dummyAction, result)

        return result
    }

    /**
     * Register a custom action executor
     */
    fun registerAction(actionId: String, executor: RoleAction) {
        (actionRegistry as MutableMap)[actionId] = executor
    }

    /**
     * Get a registered action executor
     */
    fun getAction(actionId: String): RoleAction? = actionRegistry[actionId]

    /**
     * Get an action by ID (alias for getAction)
     */
    fun getActionById(actionId: String): RoleAction? = actionRegistry[actionId]
}
