package dev.robothanzo.werewolf.game.roles.actions

import dev.robothanzo.werewolf.database.documents.Session
import dev.robothanzo.werewolf.game.model.ActionSubmissionSource
import dev.robothanzo.werewolf.game.model.RoleActionInstance
import dev.robothanzo.werewolf.game.roles.PredefinedRoles
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
        println("RoleActionExecutor: Starting execution of ${pendingActions.size} actions.")
        pendingActions.forEach { println(" - Action: ${it.actionDefinitionId} by Actor ${it.actor} (Targets: ${it.targets})") }

        // Sort actions by priority (lower number = higher priority = executes first)
        val sortedActions = pendingActions.sortedBy { action ->
            roleRegistry.getAction(action.actionDefinitionId)?.priority ?: Int.MAX_VALUE
        }

        // Execute actions one by one, accumulating state
        var result = ActionExecutionResult()

        for (action in sortedActions) {
            try {
                println("RoleActionExecutor: Executing ${action.actionDefinitionId} for Actor ${action.actor}...")
                val previousDeaths = result.deaths.toString()
                result = executeActionInstance(session, action, result)
                println("RoleActionExecutor: Finished ${action.actionDefinitionId}. Deaths changed: $previousDeaths -> ${result.deaths}")
            } catch (e: Exception) {
                // Log error but continue execution of other actions
                println("Error executing action ${action.actionDefinitionId} for player ${action.actor}: ${e.message}")
                e.printStackTrace()
            }
        }

        // Execute death resolution as final step
        val deathResolution = roleRegistry.getAction(PredefinedRoles.DEATH_RESOLUTION)
        if (deathResolution == null) {
            println("RoleActionExecutor: CRITICAL WARNING - DEATH_RESOLUTION action not found! Returning raw accumulated state.")
            return result
        }
        println("RoleActionExecutor: Executing DEATH_RESOLUTION...")
        val dummyAction = RoleActionInstance(
            actor = 0,
            actorRole = "SYSTEM",
            actionDefinitionId = PredefinedRoles.DEATH_RESOLUTION,
            targets = arrayListOf(),
            submittedBy = ActionSubmissionSource.JUDGE,
            status = dev.robothanzo.werewolf.game.model.ActionStatus.SUBMITTED
        )
        result = deathResolution.execute(session, dummyAction, result)
        println("RoleActionExecutor: Final Result - Deaths: ${result.deaths}, Saved: ${result.saved}, Protected: ${result.protectedPlayers}")

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
            println("RoleActionExecutor: CRITICAL - No executor found for actionId '${action.actionDefinitionId}'")
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
