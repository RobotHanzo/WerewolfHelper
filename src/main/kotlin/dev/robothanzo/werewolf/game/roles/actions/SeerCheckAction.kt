package dev.robothanzo.werewolf.game.roles.actions

import dev.robothanzo.werewolf.database.documents.Session
import dev.robothanzo.werewolf.game.model.ActionTiming
import dev.robothanzo.werewolf.game.model.Camp
import dev.robothanzo.werewolf.game.model.RoleActionInstance
import dev.robothanzo.werewolf.game.roles.PredefinedRoles

/**
 * Seer check action - identifies if a target is a werewolf or villager
 */
class SeerCheckAction : RoleAction {
    override val actionId = PredefinedRoles.SEER_CHECK
    override val roleName = "預言家"
    override val actionName = "查驗"
    override val priority = PredefinedRoles.SEER_PRIORITY
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
        val target = session.players.values.find { it.userId == targetId }

        if (target == null || target.roles == null) {
            return accumulatedState
        }

        val isWolf = target.roles?.any { role ->
            PredefinedRoles.getRoleDefinition(role)?.camp == Camp.WEREWOLF
        } ?: false

        // Store seer check results in metadata (doesn't affect other actions)
        val metadata = accumulatedState.metadata.toMutableMap()

        @Suppress("UNCHECKED_CAST")
        val seerChecks = (metadata.getOrPut("seerChecks") { mutableMapOf<Long, String>() } as MutableMap<Long, String>)
        seerChecks[action.actor] = if (isWolf) "werewolf" else "villager"

        return accumulatedState.copy(metadata = metadata)
    }
}
