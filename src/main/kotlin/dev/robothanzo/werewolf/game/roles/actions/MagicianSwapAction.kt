package dev.robothanzo.werewolf.game.roles.actions

import dev.robothanzo.werewolf.database.documents.Session
import dev.robothanzo.werewolf.game.model.ActionDefinitionId

import dev.robothanzo.werewolf.game.model.ActionTiming
import dev.robothanzo.werewolf.game.model.RoleActionInstance
import dev.robothanzo.werewolf.game.roles.PredefinedRoles
import org.springframework.stereotype.Component

@Component
class MagicianSwapAction : BaseRoleAction(
    actionId = ActionDefinitionId.MAGICIAN_SWAP,
    priority = PredefinedRoles.MAGICIAN_PRIORITY,
    timing = ActionTiming.NIGHT,
    targetCount = 2,
    isOptional = true
) {
    override fun execute(
        session: Session,
        action: RoleActionInstance,
        accumulatedState: ActionExecutionResult
    ): ActionExecutionResult {
        // The swap effect is handled via GameStateData.nightlySwap computed property
        // This execution just marks the action as done and adds it to history (via standard flow)
        return accumulatedState
    }

    override fun eligibleTargets(
        session: Session,
        actor: Int,
        alivePlayers: List<Int>,
        accumulatedState: ActionExecutionResult
    ): List<Int> {
        // Can swap any two alive players.
        // Constraint: "Each number can only be exchanged once."
        // We filter out players whose numbers have been swapped in previous nights.
        val usedTargets = session.stateData.magicianSwapTargets
        return alivePlayers.filter { it !in usedTargets }
    }

    override fun validate(session: Session, actor: Int, targets: List<Int>): String? {
        val baseError = super.validate(session, actor, targets)
        if (baseError != null) return baseError

        if (targets.size != 2) return "必須選擇兩名玩家"

        val usedTargets = session.stateData.magicianSwapTargets
        for (t in targets) {
            if (t in usedTargets) {
                return "玩家 $t 的號碼已經被交換過，不能再次交換"
            }
        }
        return null
    }
}
