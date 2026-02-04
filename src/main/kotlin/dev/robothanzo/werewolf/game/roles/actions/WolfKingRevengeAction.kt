package dev.robothanzo.werewolf.game.roles.actions

import dev.robothanzo.werewolf.database.documents.Session
import dev.robothanzo.werewolf.game.model.ActionTiming
import dev.robothanzo.werewolf.game.model.DeathCause
import dev.robothanzo.werewolf.game.model.RoleActionInstance
import dev.robothanzo.werewolf.game.roles.PredefinedRoles
import org.springframework.stereotype.Component

/**
 * Wolf King revenge action - triggered when wolf king dies
 * Allows the wolf king to take one player with them
 */
@Component
class WolfKingRevengeAction : RoleAction {
    override val actionId = "WOLF_KING_REVENGE"
    override val roleName = "狼王"
    override val actionName = "復仇"
    override val priority = PredefinedRoles.HUNTER_PRIORITY // Same priority as hunter
    override val timing = ActionTiming.DEATH_TRIGGER
    override val targetCount = 1
    override val usageLimit = -1 // Unlimited - triggers each time wolf king dies
    override val requiresAliveTarget = true

    override fun execute(
        session: Session,
        action: RoleActionInstance,
        accumulatedState: ActionExecutionResult
    ): ActionExecutionResult {
        val targetUserId = action.targets.firstOrNull() ?: return accumulatedState

        // Add target to kill list
        val newDeaths = accumulatedState.deaths.toMutableMap()
        val currentList = newDeaths.getOrDefault(DeathCause.WOLF_KING_REVENGE, emptyList())
        newDeaths[DeathCause.WOLF_KING_REVENGE] = currentList + targetUserId

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
