package dev.robothanzo.werewolf.game.roles.actions

import dev.robothanzo.werewolf.database.documents.Session
import dev.robothanzo.werewolf.game.model.ActionDefinitionId
import dev.robothanzo.werewolf.game.model.ActionTiming
import dev.robothanzo.werewolf.game.model.RoleActionInstance
import dev.robothanzo.werewolf.game.roles.PredefinedRoles
import org.springframework.stereotype.Component

@Component
class DreamWeaverLinkAction : BaseRoleAction(
    actionId = ActionDefinitionId.DREAM_WEAVER_LINK,
    priority = PredefinedRoles.DREAM_WEAVER_PRIORITY,
    timing = ActionTiming.NIGHT,
    isOptional = false
) {
    override fun execute(
        session: Session,
        action: RoleActionInstance,
        accumulatedState: ActionExecutionResult
    ): ActionExecutionResult {
        if (action.targets.isEmpty()) return accumulatedState
        val targetId = action.targets.firstOrNull() ?: return accumulatedState

        // Record the target for this day
        session.stateData.dreamWeaverTargets[session.day] = targetId

        return accumulatedState
    }

    override fun validate(session: Session, actor: Int, targets: List<Int>): String? {
        val baseError = super.validate(session, actor, targets)
        if (baseError != null) return baseError

        val targetId = targets.firstOrNull() ?: return "請選擇一名玩家"
        if (targetId == actor) return "攝夢人不能選擇自己"

        return null
    }

    override fun eligibleTargets(
        session: Session,
        actor: Int,
        alivePlayers: List<Int>,
        accumulatedState: ActionExecutionResult
    ): List<Int> {
        return alivePlayers.filter { it != actor }
    }
}
