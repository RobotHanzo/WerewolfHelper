package dev.robothanzo.werewolf.game.roles.actions

import dev.robothanzo.werewolf.database.documents.Session
import dev.robothanzo.werewolf.game.model.ActionTiming
import dev.robothanzo.werewolf.game.model.DeathCause
import dev.robothanzo.werewolf.game.model.RoleActionInstance
import dev.robothanzo.werewolf.game.roles.PredefinedRoles

/**
 * Witch poison action - adds target to poison death list
 */
class WitchPoisonAction : RoleAction {
    override val actionId = PredefinedRoles.WITCH_POISON
    override val roleName = "女巫"
    override val actionName = "毒藥"
    override val priority = PredefinedRoles.WITCH_POISON_PRIORITY
    override val timing = ActionTiming.NIGHT
    override val targetCount = 1
    override val usageLimit = 1
    override val requiresAliveTarget = true

    override fun execute(
        session: Session,
        action: RoleActionInstance,
        accumulatedState: ActionExecutionResult
    ): ActionExecutionResult {
        if (action.targets.isEmpty()) {
            return accumulatedState
        }

        val deaths = accumulatedState.deaths.toMutableMap()
        val poisonList = (deaths[DeathCause.POISON] ?: emptyList()).toMutableList()

        poisonList.add(action.targets[0])
        deaths[DeathCause.POISON] = poisonList

        return accumulatedState.copy(deaths = deaths)
    }
}
