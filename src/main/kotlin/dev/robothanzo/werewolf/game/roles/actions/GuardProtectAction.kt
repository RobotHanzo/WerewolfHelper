package dev.robothanzo.werewolf.game.roles.actions

import dev.robothanzo.werewolf.database.documents.Session
import dev.robothanzo.werewolf.game.model.ActionTiming
import dev.robothanzo.werewolf.game.model.RoleActionInstance
import dev.robothanzo.werewolf.game.roles.PredefinedRoles

/**
 * Guard protect action - protects a player from werewolf kills
 */
class GuardProtectAction : RoleAction {
    override val actionId = PredefinedRoles.GUARD_PROTECT
    override val roleName = "守衛"
    override val actionName = "守護"
    override val priority = PredefinedRoles.GUARD_PRIORITY
    override val timing = ActionTiming.NIGHT
    override val targetCount = 1
    override val usageLimit = -1
    override val requiresAliveTarget = true

    override fun execute(
        session: Session,
        action: RoleActionInstance,
        accumulatedState: ActionExecutionResult
    ): ActionExecutionResult {
        if (action.targets.isEmpty()) {
            return accumulatedState
        }

        val targetId = action.targets[0]

        // Guard cannot protect the same player on consecutive nights
        @Suppress("UNCHECKED_CAST")
        val lastProtected = session.stateData["lastGuardProtected"] as? Long
        if (lastProtected == targetId && session.day > 1) {
            // Cannot protect the same player consecutively (except on day 1)
            return accumulatedState
        }

        // Store the protected target for next night's check
        session.stateData["lastGuardProtected"] = targetId

        val protectedPlayers = accumulatedState.protectedPlayers.toMutableSet()
        protectedPlayers.add(targetId)

        return accumulatedState.copy(protectedPlayers = protectedPlayers)
    }

    override fun eligibleTargets(
        session: Session,
        actor: Long,
        alivePlayers: List<Long>,
        accumulatedState: ActionExecutionResult
    ): List<Long> {
        // Guard cannot protect the same player on consecutive nights
        @Suppress("UNCHECKED_CAST")
        val lastProtected = session.stateData["lastGuardProtected"] as? Long

        return if (lastProtected != null && session.day > 1) {
            // Exclude the player protected last night (except on day 1)
            alivePlayers.filter { it != lastProtected }
        } else {
            // Day 1 or no previous protection - all alive players are eligible
            alivePlayers
        }
    }
}
