package dev.robothanzo.werewolf.game.roles.actions

import dev.robothanzo.werewolf.database.documents.LogType
import dev.robothanzo.werewolf.database.documents.Session
import dev.robothanzo.werewolf.game.model.*
import dev.robothanzo.werewolf.game.roles.PredefinedRoles
import dev.robothanzo.werewolf.game.roles.RoleRegistry
import org.springframework.context.annotation.Lazy
import org.springframework.data.annotation.Transient
import org.springframework.stereotype.Component

@Component
class WerewolfKillAction : BaseRoleAction(
    actionId = ActionDefinitionId.WEREWOLF_KILL,
    priority = PredefinedRoles.WEREWOLF_PRIORITY,
    timing = ActionTiming.NIGHT
) {
    override fun execute(
        session: Session,
        action: RoleActionInstance,
        accumulatedState: ActionExecutionResult
    ): ActionExecutionResult {
        if (action.targets.isEmpty()) return accumulatedState
        val targetId = action.targets.firstOrNull() ?: return accumulatedState
        if (targetId == SKIP_TARGET_ID) return accumulatedState

        accumulatedState.deaths.getOrPut(DeathCause.WEREWOLF) { mutableListOf() }.add(targetId)
        return accumulatedState
    }
}

@Component
class WolfYoungerBrotherExtraKillAction : BaseRoleAction(
    actionId = ActionDefinitionId.WOLF_YOUNGER_BROTHER_EXTRA_KILL,
    priority = PredefinedRoles.WEREWOLF_PRIORITY + 1,
    timing = ActionTiming.NIGHT,
    usageLimit = 1,
    isOptional = false
) {
    override fun execute(
        session: Session,
        action: RoleActionInstance,
        accumulatedState: ActionExecutionResult
    ): ActionExecutionResult {
        if (action.targets.isEmpty() || action.targets[0] == -1) return accumulatedState

        accumulatedState.deaths.getOrPut(DeathCause.WEREWOLF) { mutableListOf() }.add(action.targets[0])

        // Clear flag after execution (though it will also be cleared at end of night)
        session.stateData.wolfBrotherAwakenedPlayerId = null

        return accumulatedState
    }

    override fun isAvailable(session: Session, actor: Int): Boolean {
        if (!super.isAvailable(session, actor)) return false

        // Available if this specific player was flagged for revenge after Wolf Brother's death
        return session.stateData.wolfBrotherAwakenedPlayerId == actor
    }
}

@Component
class SeerCheckAction(
    @Transient @param:Lazy private val roleRegistry: RoleRegistry
) : BaseRoleAction(
    actionId = ActionDefinitionId.SEER_CHECK,
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

        val isWolfBrotherAlive = session.alivePlayers().values.any { it.roles.contains("狼兄") }
        val isYoungerBrother = target.roles.contains("狼弟")

        val isWolf = if (isYoungerBrother && isWolfBrotherAlive) {
            false
        } else {
            target.roles.any { role ->
                (session.hydratedRoles[role] ?: roleRegistry.getRole(role))?.camp == Camp.WEREWOLF
            }
        }

        val seerPlayer = session.getPlayer(action.actor)
        val resultText = if (isWolf) "狼人" else "好人"
        seerPlayer?.channel?.sendMessage("🔮 **查驗結果**：${target.nickname} 是 **$resultText**")?.queue()

        action.status = ActionStatus.PROCESSED
        return accumulatedState
    }
}

@Component
class WitchAntidoteAction : BaseRoleAction(
    actionId = ActionDefinitionId.WITCH_ANTIDOTE,
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

        accumulatedState.saved.add(targetId)
        return accumulatedState
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
    actionId = ActionDefinitionId.WITCH_POISON,
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
        accumulatedState.deaths.getOrPut(DeathCause.POISON) { mutableListOf() }.add(targetId)
        return accumulatedState
    }
}

@Component
class GuardProtectAction : BaseRoleAction(
    actionId = ActionDefinitionId.GUARD_PROTECT,
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

        accumulatedState.protectedPlayers.add(targetId)
        return accumulatedState
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
    actionId = ActionDefinitionId.HUNTER_REVENGE,
    priority = PredefinedRoles.HUNTER_PRIORITY,
    timing = ActionTiming.DEATH_TRIGGER,
    usageLimit = 1
) {
    override val isImmediate: Boolean
        get() = true

    override fun execute(
        session: Session,
        action: RoleActionInstance,
        accumulatedState: ActionExecutionResult
    ): ActionExecutionResult {
        val targetId = action.targets.firstOrNull() ?: return accumulatedState
        accumulatedState.deaths.getOrPut(DeathCause.HUNTER_REVENGE) { mutableListOf() }.add(targetId)

        // Consume the granted action
        session.stateData.playerOwnedActions[action.actor]?.remove(actionId.toString())

        return accumulatedState
    }

    override fun eligibleTargets(
        session: Session,
        actor: Int,
        alivePlayers: List<Int>,
        accumulatedState: ActionExecutionResult
    ): List<Int> {
        return if (isAvailable(session, actor)) alivePlayers else emptyList()
    }

    override fun onDeath(session: Session, actor: Int, cause: DeathCause) {
        if (cause != DeathCause.POISON) {
            session.stateData.playerOwnedActions.getOrPut(actor) { mutableMapOf() }[actionId.toString()] = 1
        } else {
            session.getPlayer(actor)?.channel?.sendMessage("🧪 **你被女巫毒死了**！你感到身體虛弱，無法帶走任何玩家。")
                ?.queue()
        }
    }
}

@Component
class WolfKingRevengeAction : BaseRoleAction(
    actionId = ActionDefinitionId.WOLF_KING_REVENGE,
    priority = PredefinedRoles.HUNTER_PRIORITY,
    timing = ActionTiming.DEATH_TRIGGER,
    usageLimit = 1
) {
    override val isImmediate: Boolean
        get() = true

    override fun execute(
        session: Session,
        action: RoleActionInstance,
        accumulatedState: ActionExecutionResult
    ): ActionExecutionResult {
        val targetId = action.targets.firstOrNull() ?: return accumulatedState
        accumulatedState.deaths.getOrPut(DeathCause.WOLF_KING_REVENGE) { mutableListOf() }.add(targetId)

        // Consume the granted action
        session.stateData.playerOwnedActions[action.actor]?.remove(actionId.toString())

        return accumulatedState
    }

    override fun eligibleTargets(
        session: Session,
        actor: Int,
        alivePlayers: List<Int>,
        accumulatedState: ActionExecutionResult
    ): List<Int> {
        return if (isAvailable(session, actor)) alivePlayers else emptyList()
    }

    override fun onDeath(session: Session, actor: Int, cause: DeathCause) {
        if (cause != DeathCause.POISON) {
            session.stateData.playerOwnedActions.getOrPut(actor) { mutableMapOf() }[actionId.toString()] = 1
        } else {
            session.getPlayer(actor)?.channel?.sendMessage("🧪 **你被女巫毒死了**！你感到身體虛弱，無法帶走任何玩家。")
                ?.queue()
        }
    }
}

@Component
class DeathResolutionAction : BaseRoleAction(
    actionId = ActionDefinitionId.DEATH_RESOLUTION,
    priority = 1000,
    timing = ActionTiming.NIGHT,
    targetCount = 0
) {
    override fun execute(
        session: Session,
        action: RoleActionInstance,
        accumulatedState: ActionExecutionResult
    ): ActionExecutionResult {
        val deaths = accumulatedState.deaths
        val werewolfTargets = deaths[DeathCause.WEREWOLF]?.toSet() ?: emptySet()
        val doubleProtected = werewolfTargets
            .filter { it in accumulatedState.saved }
            .filter { it in accumulatedState.protectedPlayers }

        accumulatedState.saved.forEach { savedId ->
            if (deaths.values.any { it.contains(savedId) }) {
                deaths.values.forEach { it.removeIf { id -> id == savedId } }
            }
        }
        val protectedPlayers = accumulatedState.protectedPlayers
        if (protectedPlayers.isNotEmpty()) {
            deaths[DeathCause.WEREWOLF]?.removeIf { it in protectedPlayers }
        }

        if (doubleProtected.isNotEmpty()) {
            deaths[DeathCause.DOUBLE_PROTECTION] = doubleProtected.toMutableList()
        }

        val wolfKillAction =
            session.stateData.submittedActions.find { it.actionDefinitionId == ActionDefinitionId.WEREWOLF_KILL }
        val ybExtraKillAction =
            session.stateData.submittedActions.find { it.actionDefinitionId == ActionDefinitionId.WOLF_YOUNGER_BROTHER_EXTRA_KILL }

        if (wolfKillAction != null && ybExtraKillAction != null) {
            val wolfTarget = wolfKillAction.targets.firstOrNull()
            val ybTarget = ybExtraKillAction.targets.firstOrNull()

            if (wolfTarget != null && ybTarget != null && wolfTarget == ybTarget && wolfTarget != SKIP_TARGET_ID) {
                if (deaths[DeathCause.WEREWOLF]?.contains(wolfTarget) != true) {
                    deaths.getOrPut(DeathCause.WEREWOLF) { mutableListOf() }.add(wolfTarget)
                }
            }
        }

        deaths.entries.removeIf { it.value.isEmpty() }
        return accumulatedState
    }
}

abstract class DarkMerchantTradeAction(
    actionId: ActionDefinitionId,
    private val skillType: ActionDefinitionId
) : BaseRoleAction(
    actionId = actionId,
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
        if (isWolf) {
            accumulatedState.deaths.getOrPut(DeathCause.TRADED_WITH_WOLF) { mutableListOf() }.add(action.actor)
            session.addLog(LogType.SYSTEM, "黑市商人與狼人交易，不幸出局")
            return accumulatedState
        } else {
            skillType.let { id ->
                val playerActions = session.stateData.playerOwnedActions.getOrPut(targetId) { mutableMapOf() }
                playerActions[id.toString()] = 1
            }

            target.channel?.sendMessage("🎁 **你收到了黑市商人的禮物**！\n你獲得了技能：**${skillType.actionName}**\n你可以在**下一晚**開始使用它。")
                ?.queue()

            session.addLog(LogType.SYSTEM, "黑市商人交易成功，將技能 $skillType 贈予了玩家 $targetId")
        }
        return accumulatedState
    }
}

@Component
class DarkMerchantTradeSeerAction : DarkMerchantTradeAction(
    ActionDefinitionId.DARK_MERCHANT_TRADE_SEER, ActionDefinitionId.MERCHANT_SEER_CHECK
)

@Component
class DarkMerchantTradePoisonAction : DarkMerchantTradeAction(
    ActionDefinitionId.DARK_MERCHANT_TRADE_POISON, ActionDefinitionId.MERCHANT_POISON
)

@Component
class DarkMerchantTradeGunAction : DarkMerchantTradeAction(
    ActionDefinitionId.DARK_MERCHANT_TRADE_GUN, ActionDefinitionId.MERCHANT_GUN
)

@Component
class MerchantSeerCheckAction(
    @Transient @param:Lazy private val roleRegistry: RoleRegistry
) : BaseRoleAction(
    actionId = ActionDefinitionId.MERCHANT_SEER_CHECK,
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

        val isWolf = target.roles.any { role ->
            (session.hydratedRoles[role] ?: roleRegistry.getRole(role))?.camp == Camp.WEREWOLF
        }

        val seerPlayer = session.getPlayer(action.actor)
        val resultText = if (isWolf) "狼人" else "好人"
        seerPlayer?.channel?.sendMessage("🔮 **查驗結果**：${target.nickname} 是 **$resultText**")?.queue()

        return accumulatedState
    }
}

@Component
class MerchantPoisonAction : BaseRoleAction(
    actionId = ActionDefinitionId.MERCHANT_POISON,
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
        accumulatedState.deaths.getOrPut(DeathCause.POISON) { mutableListOf() }.add(targetId)
        return accumulatedState
    }
}

@Component
class MerchantGunAction : BaseRoleAction(
    actionId = ActionDefinitionId.MERCHANT_GUN,
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
        accumulatedState.deaths.getOrPut(DeathCause.HUNTER_REVENGE) { mutableListOf() }.add(targetId)
        return accumulatedState
    }
}
