package dev.robothanzo.werewolf.game.roles.actions

import dev.robothanzo.werewolf.database.documents.Session
import dev.robothanzo.werewolf.game.model.ActionTiming
import dev.robothanzo.werewolf.game.model.DeathCause
import dev.robothanzo.werewolf.game.model.RoleActionInstance
import dev.robothanzo.werewolf.game.roles.PredefinedRoles

/**
 * Werewolf kill action - group voted action for werewolves to select their target
 * All werewolves discuss for 90 seconds and vote on a single target
 */
class WerewolfKillAction : RoleAction {
    override val actionId = PredefinedRoles.WEREWOLF_KILL
    override val roleName = "狼人"
    override val actionName = "擊殺"
    override val priority = PredefinedRoles.WEREWOLF_PRIORITY
    override val timing = ActionTiming.NIGHT
    override val targetCount = 1
    override val usageLimit = -1
    override val requiresAliveTarget = true

    override fun execute(
        session: Session,
        action: RoleActionInstance,
        accumulatedState: ActionExecutionResult
    ): ActionExecutionResult {
        // Only add the selected target (should be resolved from group voting)
        // If no target was selected during the 90s discussion window, no kill happens
        if (action.targets.isEmpty()) {
            // Group failed to select a target within 90 seconds - forfeit kill
            return accumulatedState
        }

        val deaths = accumulatedState.deaths.toMutableMap()
        val killList = (deaths[DeathCause.WEREWOLF] ?: emptyList()).toMutableList()

        // Add only the first (and typically only) target from group voting
        killList.add(action.targets[0])

        deaths[DeathCause.WEREWOLF] = killList
        return accumulatedState.copy(deaths = deaths)
    }
}
