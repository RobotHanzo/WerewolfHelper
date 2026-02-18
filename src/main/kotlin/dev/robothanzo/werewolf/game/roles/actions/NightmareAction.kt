package dev.robothanzo.werewolf.game.roles.actions

import dev.robothanzo.werewolf.database.documents.Session
import dev.robothanzo.werewolf.game.model.ActionDefinitionId
import dev.robothanzo.werewolf.game.model.ActionTiming
import dev.robothanzo.werewolf.game.model.RoleActionInstance
import dev.robothanzo.werewolf.game.roles.PredefinedRoles
import org.springframework.stereotype.Component

@Component
class NightmareFearAction : BaseRoleAction(
    actionId = ActionDefinitionId.NIGHTMARE_FEAR,
    priority = PredefinedRoles.NIGHTMARE_PRIORITY,
    timing = ActionTiming.NIGHT,
    isImmediate = true
) {
    override fun execute(
        session: Session,
        action: RoleActionInstance,
        accumulatedState: ActionExecutionResult
    ): ActionExecutionResult {
        if (action.targets.isEmpty()) return accumulatedState
        return accumulatedState
    }

    override fun validate(session: Session, actor: Int, targets: List<Int>): String? {
        val baseError = super.validate(session, actor, targets)
        if (baseError != null) return baseError

        val targetId = targets.firstOrNull() ?: return "請選擇一名玩家"

        // Cannot fear self
        if (targetId == actor) {
            return "無法恐懼自己"
        }

        // Check consistency: Cannot fear the same player two nights in a row
        val lastDayTarget = session.stateData.nightmareFearTargets[session.day - 1]
        if (lastDayTarget == targetId) {
            return "不能連續兩晚恐懼同一名玩家"
        }

        return null
    }

    override fun eligibleTargets(
        session: Session,
        actor: Int,
        alivePlayers: List<Int>,
        accumulatedState: ActionExecutionResult
    ): List<Int> {
        // Eligible targets are all alive players except self and the previous night's target
        val lastDayTarget = session.stateData.nightmareFearTargets[session.day - 1]
        return alivePlayers.filter { it != actor && it != lastDayTarget }
    }
}
