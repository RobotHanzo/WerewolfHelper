package dev.robothanzo.werewolf.game.roles.actions

import dev.robothanzo.werewolf.database.documents.Session
import dev.robothanzo.werewolf.game.model.ActionTiming
import dev.robothanzo.werewolf.game.model.Camp
import dev.robothanzo.werewolf.game.model.DeathCause
import dev.robothanzo.werewolf.game.model.RoleActionInstance
import dev.robothanzo.werewolf.game.roles.PredefinedRoles
import org.springframework.context.annotation.Lazy
import org.springframework.data.annotation.Transient
import org.springframework.stereotype.Component

@Component
class WerewolfKillAction : BaseRoleAction(
    actionId = PredefinedRoles.WEREWOLF_KILL,
    roleName = "狼人",
    actionName = "擊殺",
    priority = PredefinedRoles.WEREWOLF_PRIORITY,
    timing = ActionTiming.NIGHT
) {
    override fun execute(
        session: Session,
        action: RoleActionInstance,
        accumulatedState: ActionExecutionResult
    ): ActionExecutionResult {
        if (action.targets.isEmpty()) return accumulatedState

        val deaths = accumulatedState.deaths.toMutableMap()
        val killList = (deaths[DeathCause.WEREWOLF] ?: emptyList()).toMutableList()
        killList.add(action.targets[0])
        deaths[DeathCause.WEREWOLF] = killList
        return accumulatedState.copy(deaths = deaths)
    }
}

@Component
class SeerCheckAction(
    @Transient @param:Lazy private val roleRegistry: dev.robothanzo.werewolf.game.roles.RoleRegistry
) : BaseRoleAction(
    actionId = PredefinedRoles.SEER_CHECK,
    roleName = "預言家",
    actionName = "查驗",
    priority = PredefinedRoles.SEER_PRIORITY,
    timing = ActionTiming.NIGHT,
    isImmediate = true
) {
    override fun execute(
        session: Session,
        action: RoleActionInstance,
        accumulatedState: ActionExecutionResult
    ): ActionExecutionResult {
        if (action.targets.isEmpty()) return accumulatedState

        val targetId = action.targets[0]
        val target = session.getPlayer(targetId) ?: return accumulatedState

        val isWolf = target.roles?.any { role ->
            (session.hydratedRoles[role] ?: roleRegistry.getRole(role))?.camp == Camp.WEREWOLF
        } ?: false

        if (!action.processed) {
            val seerPlayer = session.getPlayer(action.actor)
            val resultText = if (isWolf) "狼人" else "好人"
            seerPlayer?.channel?.sendMessage("🔮 **查驗結果**：${target.nickname} 是 **$resultText**")?.queue()
            action.processed = true
        }
        return accumulatedState
    }
}

@Component
class WitchAntidoteAction : BaseRoleAction(
    actionId = PredefinedRoles.WITCH_ANTIDOTE,
    roleName = "女巫",
    actionName = "解藥",
    priority = PredefinedRoles.WITCH_ANTIDOTE_PRIORITY,
    timing = ActionTiming.NIGHT,
    usageLimit = 1,
    requiresAliveTarget = false
) {
    override fun execute(
        session: Session,
        action: RoleActionInstance,
        accumulatedState: ActionExecutionResult
    ): ActionExecutionResult {
        val targetId = action.targets.firstOrNull() ?: return accumulatedState
        val werewolfKillList = accumulatedState.deaths[DeathCause.WEREWOLF] ?: emptyList()

        if (targetId !in werewolfKillList) return accumulatedState
        if (targetId == action.actor && !session.settings.witchCanSaveSelf) return accumulatedState

        return accumulatedState.copy(saved = accumulatedState.saved + targetId)
    }

    override fun eligibleTargets(
        session: Session,
        actor: Int,
        alivePlayers: List<Int>,
        accumulatedState: ActionExecutionResult
    ): List<Int> {
        val targetId = session.stateData.nightWolfKillTargetId ?: return emptyList()
        if (targetId == actor && !session.settings.witchCanSaveSelf) return emptyList()
        return listOf(targetId)
    }
}

@Component
class WitchPoisonAction : BaseRoleAction(
    actionId = PredefinedRoles.WITCH_POISON,
    roleName = "女巫",
    actionName = "毒藥",
    priority = PredefinedRoles.WITCH_POISON_PRIORITY,
    timing = ActionTiming.NIGHT,
    usageLimit = 1
) {
    override fun execute(
        session: Session,
        action: RoleActionInstance,
        accumulatedState: ActionExecutionResult
    ): ActionExecutionResult {
        val targetId = action.targets.firstOrNull() ?: return accumulatedState
        val deaths = accumulatedState.deaths.toMutableMap()
        val poisonList = (deaths[DeathCause.POISON] ?: emptyList()).toMutableList()
        poisonList.add(targetId)
        deaths[DeathCause.POISON] = poisonList
        return accumulatedState.copy(deaths = deaths)
    }
}

@Component
class GuardProtectAction : BaseRoleAction(
    actionId = PredefinedRoles.GUARD_PROTECT,
    roleName = "守衛",
    actionName = "守護",
    priority = PredefinedRoles.GUARD_PRIORITY,
    timing = ActionTiming.NIGHT
) {
    override fun execute(
        session: Session,
        action: RoleActionInstance,
        accumulatedState: ActionExecutionResult
    ): ActionExecutionResult {
        val targetId = action.targets.firstOrNull() ?: return accumulatedState
        val lastProtected = session.stateData.lastGuardProtectedId

        if (lastProtected == targetId && session.day > 1) return accumulatedState

        session.stateData.lastGuardProtectedId = targetId
        return accumulatedState.copy(protectedPlayers = accumulatedState.protectedPlayers + targetId)
    }

    override fun eligibleTargets(
        session: Session,
        actor: Int,
        alivePlayers: List<Int>,
        accumulatedState: ActionExecutionResult
    ): List<Int> {
        val lastProtected = session.stateData.lastGuardProtectedId
        return if (lastProtected != null && session.day > 1) {
            alivePlayers.filter { it != lastProtected }
        } else {
            alivePlayers
        }
    }
}

@Component
class HunterRevengeAction : BaseRoleAction(
    actionId = PredefinedRoles.HUNTER_REVENGE,
    roleName = "獵人",
    actionName = "開槍",
    priority = PredefinedRoles.HUNTER_PRIORITY,
    timing = ActionTiming.DEATH_TRIGGER
) {
    override fun execute(
        session: Session,
        action: RoleActionInstance,
        accumulatedState: ActionExecutionResult
    ): ActionExecutionResult {
        val targetId = action.targets.firstOrNull() ?: return accumulatedState
        val deaths = accumulatedState.deaths.toMutableMap()
        val currentList = deaths.getOrDefault(DeathCause.HUNTER_REVENGE, emptyList())
        deaths[DeathCause.HUNTER_REVENGE] = currentList + targetId
        session.stateData.deathTriggerAvailableMap.remove(actionId)
        return accumulatedState.copy(deaths = deaths)
    }

    override fun eligibleTargets(
        session: Session,
        actor: Int,
        alivePlayers: List<Int>,
        accumulatedState: ActionExecutionResult
    ): List<Int> {
        return if (session.stateData.deathTriggerAvailableMap[actionId] == actor) alivePlayers else emptyList()
    }
}

@Component
class WolfKingRevengeAction : BaseRoleAction(
    actionId = "WOLF_KING_REVENGE",
    roleName = "狼王",
    actionName = "復仇",
    priority = PredefinedRoles.HUNTER_PRIORITY,
    timing = ActionTiming.DEATH_TRIGGER
) {
    override fun execute(
        session: Session,
        action: RoleActionInstance,
        accumulatedState: ActionExecutionResult
    ): ActionExecutionResult {
        val targetId = action.targets.firstOrNull() ?: return accumulatedState
        val deaths = accumulatedState.deaths.toMutableMap()
        val currentList = deaths.getOrDefault(DeathCause.WOLF_KING_REVENGE, emptyList())
        deaths[DeathCause.WOLF_KING_REVENGE] = currentList + targetId
        session.stateData.deathTriggerAvailableMap.remove(actionId)
        return accumulatedState.copy(deaths = deaths)
    }

    override fun eligibleTargets(
        session: Session,
        actor: Int,
        alivePlayers: List<Int>,
        accumulatedState: ActionExecutionResult
    ): List<Int> {
        return if (session.stateData.deathTriggerAvailableMap[actionId] == actor) alivePlayers else emptyList()
    }
}

@Component
class DeathResolutionAction : BaseRoleAction(
    actionId = "DEATH_RESOLUTION",
    roleName = "SYSTEM",
    actionName = "結算",
    priority = 1000,
    timing = ActionTiming.NIGHT,
    targetCount = 0
) {
    override fun execute(
        session: Session,
        action: RoleActionInstance,
        accumulatedState: ActionExecutionResult
    ): ActionExecutionResult {
        val deaths = accumulatedState.deaths.mapValues { it.value.toMutableList() }.toMutableMap()

        val werewolfTargets = deaths[DeathCause.WEREWOLF]?.toSet() ?: emptySet()
        val doubleProtected = werewolfTargets
            .filter { it in accumulatedState.saved }
            .filter { it in accumulatedState.protectedPlayers }

        accumulatedState.saved.forEach { savedId -> deaths.values.forEach { it.remove(savedId) } }
        accumulatedState.protectedPlayers.forEach { protectedId -> deaths[DeathCause.WEREWOLF]?.remove(protectedId) }

        if (doubleProtected.isNotEmpty()) {
            deaths[DeathCause.DOUBLE_PROTECTION] = doubleProtected.toMutableList()
        }

        deaths.entries.removeIf { it.value.isEmpty() }

        val metadata = accumulatedState.metadata.toMutableMap()
        if (doubleProtected.isNotEmpty()) metadata["doubleProtectedPlayers"] = doubleProtected

        return accumulatedState.copy(deaths = deaths, metadata = metadata)
    }
}
