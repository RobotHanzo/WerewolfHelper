package dev.robothanzo.werewolf.game.roles.actions

import dev.robothanzo.werewolf.database.documents.Session
import dev.robothanzo.werewolf.game.model.ActionTiming
import dev.robothanzo.werewolf.game.model.DeathCause
import dev.robothanzo.werewolf.game.model.RoleActionInstance

/**
 * Death resolution action - processes protection and removes saved players from deaths
 * This action must be executed AFTER all other actions to properly calculate final deaths
 */
class DeathResolutionAction : RoleAction {
    override val actionId = "DEATH_RESOLUTION"
    override val roleName = "SYSTEM"
    override val actionName = "結算"
    override val priority = 1000 // Highest priority (executed last)
    override val timing = ActionTiming.NIGHT
    override val targetCount = 0
    override val usageLimit = -1
    override val requiresAliveTarget = false

    override fun execute(
        session: Session,
        action: RoleActionInstance,
        accumulatedState: ActionExecutionResult
    ): ActionExecutionResult {
        val deaths = mutableMapOf<DeathCause, MutableList<Long>>()

        // Convert existing deaths to mutable lists
        for ((cause, deadList) in accumulatedState.deaths) {
            deaths[cause] = deadList.toMutableList()
        }

        // Detect players who were targeted by werewolves and are both saved AND protected
        // These players die due to double protection
        val werewolfTargets = deaths[DeathCause.WEREWOLF]?.toSet() ?: emptySet()
        val doubleProtected = werewolfTargets
            .filter { it in accumulatedState.saved }
            .filter { it in accumulatedState.protectedPlayers }

        // Remove saved players from all death lists
        for (savedId in accumulatedState.saved) {
            deaths.forEach { (_, deathList) ->
                deathList.remove(savedId)
            }
        }

        // Remove protected players from werewolf kills (but not poison)
        for (protectedId in accumulatedState.protectedPlayers) {
            deaths[DeathCause.WEREWOLF]?.remove(protectedId)
            // Poison ignores protection
        }

        // Add double protected players back to deaths with special cause
        if (doubleProtected.isNotEmpty()) {
            deaths[DeathCause.DOUBLE_PROTECTION] = doubleProtected.toMutableList()
        }

        // Clean up empty death lists
        deaths.entries.removeIf { it.value.isEmpty() }

        val metadata = accumulatedState.metadata.toMutableMap()
        if (doubleProtected.isNotEmpty()) {
            metadata["doubleProtectedPlayers"] = doubleProtected
        }

        return accumulatedState.copy(deaths = deaths, metadata = metadata)
    }
}
