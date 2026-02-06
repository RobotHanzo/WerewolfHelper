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
    actionName = "擊殺",
    priority = PredefinedRoles.WEREWOLF_PRIORITY,
    timing = ActionTiming.NIGHT
) {
    override fun execute(
        session: Session,
        action: RoleActionInstance,
        accumulatedState: ActionExecutionResult
    ): ActionExecutionResult {
        if (action.targets.isEmpty() || action.targets.first() == dev.robothanzo.werewolf.game.model.SKIP_TARGET_ID) {
            return accumulatedState
        }

        val deaths = accumulatedState.deaths.toMutableMap()
        val killList = (deaths[DeathCause.WEREWOLF] ?: emptyList()).toMutableList()
        killList.add(action.targets[0])
        deaths[DeathCause.WEREWOLF] = killList
        return accumulatedState.copy(deaths = deaths)
    }
}

@Component
class WolfYoungerBrotherExtraKillAction : BaseRoleAction(
    actionId = PredefinedRoles.WOLF_YOUNGER_BROTHER_EXTRA_KILL,
    actionName = "狼弟復仇刀",
    priority = PredefinedRoles.WEREWOLF_PRIORITY + 1,
    timing = ActionTiming.NIGHT,
    isOptional = false
) {
    override fun execute(
        session: Session,
        action: RoleActionInstance,
        accumulatedState: ActionExecutionResult
    ): ActionExecutionResult {
        if (action.targets.isEmpty() || action.targets[0] == -1) return accumulatedState

        val deaths = accumulatedState.deaths.toMutableMap()
        val killList = (deaths[DeathCause.WEREWOLF] ?: emptyList()).toMutableList()
        killList.add(action.targets[0])
        deaths[DeathCause.WEREWOLF] = killList
        return accumulatedState.copy(deaths = deaths)
    }

    override fun isAvailable(session: Session, actor: Int): Boolean {
        if (!super.isAvailable(session, actor)) return false

        // Check if Wolf Brother died in the previous day
        val wolfBrotherDiedDay = session.stateData.roleFlags["WolfBrotherDiedDay"] as? Int ?: return false

        // So if Wolf Brother died on Day X, we are now at Night X+1 (session.day == X).
        // Since session.day is only incremented at the start of DEATH_ANNOUNCEMENT,
        // it remains X during the following night.

        return wolfBrotherDiedDay == session.day
    }
}

@Component
class SeerCheckAction(
    @Transient @param:Lazy private val roleRegistry: dev.robothanzo.werewolf.game.roles.RoleRegistry
) : BaseRoleAction(
    actionId = PredefinedRoles.SEER_CHECK,
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

        val isWolfBrotherAlive = session.alivePlayers().values.any { it.roles?.contains("狼兄") == true }
        val isYoungerBrother = target.roles?.contains("狼弟") == true

        val isWolf = if (isYoungerBrother && isWolfBrotherAlive) {
            false
        } else {
            target.roles?.any { role ->
                (session.hydratedRoles[role] ?: roleRegistry.getRole(role))?.camp == Camp.WEREWOLF
            } ?: false
        }

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

        if (lastProtected == targetId && session.day > 0) return accumulatedState

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
        return if (lastProtected != null && session.day > 0) {
            alivePlayers.filter { it != lastProtected }
        } else {
            alivePlayers
        }
    }
}

@Component
class HunterRevengeAction : BaseRoleAction(
    actionId = PredefinedRoles.HUNTER_REVENGE,
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

        accumulatedState.saved.forEach { savedId ->
            deaths.values.forEach { it.removeIf { id -> id == savedId } }
        }
        val protectedPlayers = accumulatedState.protectedPlayers
        deaths[DeathCause.WEREWOLF]?.removeIf { it in protectedPlayers }

        if (doubleProtected.isNotEmpty()) {
            deaths[DeathCause.DOUBLE_PROTECTION] = doubleProtected.toMutableList()
        }

        deaths.entries.removeIf { it.value.isEmpty() }

        val metadata = accumulatedState.metadata.toMutableMap()
        if (doubleProtected.isNotEmpty()) metadata["doubleProtectedPlayers"] = doubleProtected

        return accumulatedState.copy(deaths = deaths, metadata = metadata)
    }
}

abstract class DarkMerchantTradeAction(
    actionId: String,
    actionName: String,
    private val skillType: String
) : BaseRoleAction(
    actionId = actionId,
    actionName = actionName,
    priority = PredefinedRoles.DARK_MERCHANT_PRIORITY,
    timing = ActionTiming.NIGHT,
    usageLimit = 1
) {
    override fun execute(
        session: Session,
        action: RoleActionInstance,
        accumulatedState: ActionExecutionResult
    ): ActionExecutionResult {
        val targetId = action.targets.firstOrNull() ?: return accumulatedState
        val target = session.getPlayer(targetId) ?: return accumulatedState

        val isWolf = target.wolf
        val actorPlayer = session.getPlayer(action.actor)
        if (isWolf) {
            // Merchant dies
            val deaths = accumulatedState.deaths.toMutableMap()
            val unknownList = (deaths[DeathCause.UNKNOWN] ?: emptyList()).toMutableList()
            unknownList.add(action.actor)
            deaths[DeathCause.UNKNOWN] = unknownList
            session.addLog(dev.robothanzo.werewolf.database.documents.LogType.SYSTEM, "黑市商人與狼人交易，不幸出局")
            actorPlayer?.channel?.sendMessage("🌙 **交易失敗**：你交易的對象是狼人，你不幸出局...")?.queue()
            return accumulatedState.copy(deaths = deaths)
        } else {
            // Trade success, recipient gets a skill next night
            session.stateData.roleFlags["DarkMerchantTradeRecipient"] = targetId
            session.stateData.roleFlags["DarkMerchantGiftedSkill"] = skillType
            session.addLog(
                dev.robothanzo.werewolf.database.documents.LogType.SYSTEM,
                "黑市商人交易成功，將技能 $skillType 贈予了玩家 $targetId"
            )
            actorPlayer?.channel?.sendMessage("🌙 **交易成功**：你已成功贈予技能！")?.queue()
        }
        return accumulatedState
    }
}

@Component
class DarkMerchantTradeSeerAction : DarkMerchantTradeAction(
    PredefinedRoles.DARK_MERCHANT_TRADE_SEER, "交易 (預言家)", "SEER"
)

@Component
class DarkMerchantTradePoisonAction : DarkMerchantTradeAction(
    PredefinedRoles.DARK_MERCHANT_TRADE_POISON, "交易 (女巫)", "POISON"
)

@Component
class DarkMerchantTradeGunAction : DarkMerchantTradeAction(
    PredefinedRoles.DARK_MERCHANT_TRADE_GUN, "交易 (獵人)", "GUN"
)

@Component
class MerchantSeerCheckAction(
    @Transient @param:Lazy private val roleRegistry: dev.robothanzo.werewolf.game.roles.RoleRegistry
) : BaseRoleAction(
    actionId = PredefinedRoles.MERCHANT_SEER_CHECK,
    actionName = "查驗",
    priority = PredefinedRoles.SEER_PRIORITY + 1,
    timing = ActionTiming.NIGHT,
    usageLimit = 1,
    isImmediate = true
) {
    override fun execute(
        session: Session,
        action: RoleActionInstance,
        accumulatedState: ActionExecutionResult
    ): ActionExecutionResult {
        val targetId = action.targets.firstOrNull() ?: return accumulatedState
        val target = session.getPlayer(targetId) ?: return accumulatedState

        val isWolf = target.roles?.any { role ->
            (session.hydratedRoles[role] ?: roleRegistry.getRole(role))?.camp == Camp.WEREWOLF
        } ?: false

        if (!action.processed) {
            val actorPlayer = session.getPlayer(action.actor)
            val resultText = if (isWolf) "狼人" else "好人"
            actorPlayer?.channel?.sendMessage("🔮 **查驗結果 (商人贈予)**：${target.nickname} 是 **$resultText**")
                ?.queue()
            action.processed = true
        }
        return accumulatedState
    }
}

@Component
class MerchantPoisonAction : BaseRoleAction(
    actionId = PredefinedRoles.MERCHANT_POISON,
    actionName = "毒藥",
    priority = PredefinedRoles.WITCH_POISON_PRIORITY + 1,
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
class MerchantGunAction : BaseRoleAction(
    actionId = PredefinedRoles.MERCHANT_GUN,
    actionName = "獵槍",
    priority = PredefinedRoles.HUNTER_PRIORITY + 1,
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
        val currentList = deaths.getOrDefault(DeathCause.HUNTER_REVENGE, emptyList())
        deaths[DeathCause.HUNTER_REVENGE] = currentList + targetId
        return accumulatedState.copy(deaths = deaths)
    }
}
