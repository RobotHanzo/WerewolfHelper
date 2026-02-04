package dev.robothanzo.werewolf.game.roles.actions

import dev.robothanzo.werewolf.database.documents.Session
import dev.robothanzo.werewolf.game.model.ActionTiming
import dev.robothanzo.werewolf.game.model.DeathCause
import dev.robothanzo.werewolf.game.model.RoleActionInstance
import dev.robothanzo.werewolf.game.roles.PredefinedRoles
import org.springframework.stereotype.Component

/**
 * Hunter revenge action - triggered when hunter dies
 * Allows the hunter to take one player with them
 */
@Component
class HunterRevengeAction : RoleAction {
    override val actionId = PredefinedRoles.HUNTER_REVENGE
    override val roleName = "獵人"
    override val actionName = "開槍"
    override val priority = PredefinedRoles.HUNTER_PRIORITY
    override val timing = ActionTiming.DEATH_TRIGGER
    override val targetCount = 1
    override val usageLimit = -1 // Unlimited - triggers each time hunter dies
    override val requiresAliveTarget = true

    override fun execute(
        session: Session,
        action: RoleActionInstance,
        accumulatedState: ActionExecutionResult
    ): ActionExecutionResult {
        val targetUserId = action.targets.firstOrNull() ?: return accumulatedState

        // Add target to kill list
        val newDeaths = accumulatedState.deaths.toMutableMap()
        val currentList = newDeaths.getOrDefault(DeathCause.HUNTER_REVENGE, emptyList())
        newDeaths[DeathCause.HUNTER_REVENGE] = currentList + targetUserId

        // Remove the death trigger available flag
        session.stateData.remove("${actionId}Available")

        return accumulatedState.copy(deaths = newDeaths)
    }

    override fun eligibleTargets(
        session: Session,
        actor: Long,
        alivePlayers: List<Long>,
        accumulatedState: ActionExecutionResult
    ): List<Long> {
        // Check if this actor has this death trigger available
        val availableUserId = session.stateData["${actionId}Available"] as? Long

        if (actor != availableUserId) {
            return emptyList()
        }

        // Can target any alive player
        return alivePlayers
    }
}
