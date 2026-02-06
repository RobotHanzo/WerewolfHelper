package dev.robothanzo.werewolf.game.roles.actions

import dev.robothanzo.werewolf.database.documents.Session
import dev.robothanzo.werewolf.game.model.ActionSubmissionSource
import dev.robothanzo.werewolf.game.model.RoleActionInstance
import dev.robothanzo.werewolf.game.roles.RoleRegistry
import org.springframework.stereotype.Component

/**
 * Executes role actions in priority order and accumulates their results
 */
@Component
class RoleActionExecutor(private val roleRegistry: RoleRegistry) {

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
            roleRegistry.getAction(action.actionDefinitionId)?.priority ?: Int.MAX_VALUE
        }

        // Execute actions one by one, accumulating state
        var result = ActionExecutionResult()

        for (action in sortedActions) {
            result = executeActionInstance(session, action, result)
        }

        // Execute death resolution as final step
        val deathResolution = roleRegistry.getAction("DEATH_RESOLUTION") ?: return result
        val dummyAction = RoleActionInstance(
            actor = 0,
            actionDefinitionId = "DEATH_RESOLUTION",
            targets = emptyList(),
            submittedBy = ActionSubmissionSource.JUDGE
        )
        result = deathResolution.execute(session, dummyAction, result)

        return result
    }

    /**
     * Execute a single action instance
     */
    fun executeActionInstance(
        session: Session,
        action: RoleActionInstance,
        accumulatedState: ActionExecutionResult = ActionExecutionResult()
    ): ActionExecutionResult {
        val executor = roleRegistry.getAction(action.actionDefinitionId)
        return if (executor != null) {
            executor.execute(session, action, accumulatedState)
        } else {
            accumulatedState
        }
    }

    /**
     * Get a registered action executor
     */
    fun getAction(actionId: String): RoleAction? = roleRegistry.getAction(actionId)

    /**
     * Get an action by ID (alias for getAction)
     */
    fun getActionById(actionId: String): RoleAction? = roleRegistry.getAction(actionId)
}
