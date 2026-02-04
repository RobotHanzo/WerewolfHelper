package dev.robothanzo.werewolf.game.roles.actions

import dev.robothanzo.werewolf.database.documents.Session
import dev.robothanzo.werewolf.game.model.ActionTiming
import dev.robothanzo.werewolf.game.model.DeathCause
import dev.robothanzo.werewolf.game.model.RoleActionInstance
import dev.robothanzo.werewolf.game.roles.PredefinedRoles

/**
 * Witch antidote action - saves a player from death
 */
class WitchAntidoteAction : RoleAction {
    override val actionId = PredefinedRoles.WITCH_ANTIDOTE
    override val roleName = "女巫"
    override val actionName = "解藥"
    override val priority = PredefinedRoles.WITCH_ANTIDOTE_PRIORITY
    override val timing = ActionTiming.NIGHT
    override val targetCount = 1
    override val usageLimit = 1
    override val requiresAliveTarget = false

    override fun execute(
        session: Session,
        action: RoleActionInstance,
        accumulatedState: ActionExecutionResult
    ): ActionExecutionResult {
        if (action.targets.isEmpty()) {
            return accumulatedState
        }

        val targetId = action.targets[0]

        // Witch can only save players who are currently being killed by werewolves
        val werewolfKillList = accumulatedState.deaths[DeathCause.WEREWOLF] ?: emptyList()
        if (targetId !in werewolfKillList) {
            // Target is not in the werewolf kill list, cannot save
            return accumulatedState
        }

        // Check if witch can save themselves
        @Suppress("UNCHECKED_CAST")
        val settings =
            (session.stateData.getOrDefault("settings", emptyMap<String, Any>()) as? Map<String, Any>) ?: emptyMap()
        val witchCanSaveSelf = settings.getOrDefault("witchCanSaveSelf", true) as? Boolean ?: true

        if (targetId == action.actor && !witchCanSaveSelf) {
            return accumulatedState
        }

        val saved = accumulatedState.saved.toMutableList()
        saved.add(targetId)

        return accumulatedState.copy(saved = saved)
    }

    override fun eligibleTargets(
        session: Session,
        actor: Long,
        alivePlayers: List<Long>,
        accumulatedState: ActionExecutionResult
    ): List<Long> {
        // Witch can only save players who are currently being killed by werewolves
        return accumulatedState.deaths[DeathCause.WEREWOLF] ?: emptyList()
    }
}
