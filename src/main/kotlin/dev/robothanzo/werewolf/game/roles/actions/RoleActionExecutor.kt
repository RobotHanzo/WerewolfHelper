package dev.robothanzo.werewolf.game.roles.actions

import dev.robothanzo.werewolf.database.documents.Session
import dev.robothanzo.werewolf.game.model.ActionDefinitionId
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
        val deathResolution = roleRegistry.getAction(ActionDefinitionId.DEATH_RESOLUTION)
        if (deathResolution == null) {
            println("RoleActionExecutor: CRITICAL WARNING - DEATH_RESOLUTION action not found! Returning raw accumulated state.")
            return result
        }
        println("RoleActionExecutor: Executing DEATH_RESOLUTION...")
        val dummyAction = RoleActionInstance(
            actor = 0,
            actorRole = "SYSTEM",
            actionDefinitionId = ActionDefinitionId.DEATH_RESOLUTION,
            targets = arrayListOf(),
            submittedBy = ActionSubmissionSource.JUDGE,
            status = dev.robothanzo.werewolf.game.model.ActionStatus.SUBMITTED
        )
        result = deathResolution.execute(session, dummyAction, result)

        // --- Ghost Rider Immunity Logic (Post-processing) ---
        val ghostRiderPlayerIds = session.players.values.filter { it.roles.contains("惡靈騎士") }.map { it.id }.toSet()
        // "Ghost Rider cannot die at night"
        // Remove Ghost Rider from deaths caused by: WEREWOLF, POISON, HUNTER_REVENGE, WOLF_KING_REVENGE
        // (He is NOT immune to REFLECT? Well he can't be reflected by himself.
        //  He IS immune to Witch poison, Hunter shot.
        //  Is he immune to DREAM_WEAVER? "Good camp players use skills... will die".
        //  If Dream Weaver links him, usually Dream Weaver dies. Does Ghost Rider die?
        //  Rule says "Immune to skills". So probably immune to Dream death too if it's considered night death.
        //  Let's stick to explicit "Cannot die at night".

        if (ghostRiderPlayerIds.isNotEmpty()) {
            result.deaths.forEach { (cause, playerList) ->
                // Check if this cause is a night death cause (usually all except EXILE/SUICIDE?)
                // Explicitly: Wolf, Poison, Hunter, Wolf King, Scheme/Dream?
                if (cause == dev.robothanzo.werewolf.game.model.DeathCause.WEREWOLF ||
                    cause == dev.robothanzo.werewolf.game.model.DeathCause.POISON ||
                    cause == dev.robothanzo.werewolf.game.model.DeathCause.HUNTER_REVENGE ||
                    cause == dev.robothanzo.werewolf.game.model.DeathCause.WOLF_KING_REVENGE ||
                    cause == dev.robothanzo.werewolf.game.model.DeathCause.DREAM_WEAVER
                ) { // Assuming Dream death is night death

                    playerList.removeAll(ghostRiderPlayerIds)
                }
            }
            // Clean up empty lists
            result.deaths.entries.removeIf { it.value.isEmpty() }
        }
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
        // --- Ghost Rider Reflection Logic ---
        val ghostRiderPlayerIds = session.players.values.filter { it.roles.contains("惡靈騎士") }.map { it.id }.toSet()

        if (ghostRiderPlayerIds.isNotEmpty() && !session.stateData.ghostRiderReflected) {
            val targetsGhostRider = action.targets.any { it in ghostRiderPlayerIds }

            if (targetsGhostRider) {
                // Check Actor Camp
                val actorRoleName = action.actorRole
                val actorRole = roleRegistry.getRole(actorRoleName)
                val isGoodCamp = actorRole?.camp == dev.robothanzo.werewolf.game.model.Camp.GOD ||
                    actorRole?.camp == dev.robothanzo.werewolf.game.model.Camp.VILLAGER
                // Exclude Guard (Guard Protect)
                val isGuard = action.actionDefinitionId == ActionDefinitionId.GUARD_PROTECT

                if (isGoodCamp && !isGuard) {
                    val victimId = action.actor
                    println("RoleActionExecutor: Ghost Rider Reflection Triggered (Singular)! ${action.actionDefinitionId} from Actor $victimId reflected.")

                    // Record reflection action to history
                    val ghostRiderActorId = ghostRiderPlayerIds.first()
                    val reflectAction = RoleActionInstance(
                        actor = ghostRiderActorId,
                        actorRole = "惡靈騎士",
                        actionDefinitionId = ActionDefinitionId.GHOST_RIDER_REFLECT,
                        targets = mutableListOf(victimId),
                        submittedBy = ActionSubmissionSource.SYSTEM,
                        status = dev.robothanzo.werewolf.game.model.ActionStatus.PROCESSED
                    )
                    session.stateData.executedActions.getOrPut(session.day) { mutableListOf() }.add(reflectAction)

                    // Kill the actor
                    accumulatedState.deaths.getOrPut(dev.robothanzo.werewolf.game.model.DeathCause.REFLECT) { mutableListOf() }
                        .add(victimId)

                    // Skip original action
                    return accumulatedState
                }
            }
        }

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
    fun getAction(actionId: ActionDefinitionId): RoleAction? = roleRegistry.getAction(actionId)
}
