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
        val rawTargetId = action.targets.firstOrNull() ?: return accumulatedState
        if (rawTargetId == SKIP_TARGET_ID) return accumulatedState

        val targetId = session.stateData.getRealTarget(rawTargetId)

        // Nightmare Check: If any alive wolf is feared, the kill fails
        val fearedId = session.stateData.nightmareFearTargets[session.day]
        if (fearedId != null) {
            val fearedPlayer = session.getPlayer(fearedId)
            if (fearedPlayer?.wolf == true) {
                session.addLog(LogType.SYSTEM, "狼人陣營今晚無法行兇，因為隊友 ${fearedPlayer.nickname} 處於恐懼狀態")
                return accumulatedState
            }
        }

        accumulatedState.deaths.getOrPut(DeathCause.WEREWOLF) { mutableListOf() }.add(targetId)
        return accumulatedState
    }

    override fun eligibleTargets(
        session: Session,
        actor: Int,
        alivePlayers: List<Int>,
        accumulatedState: ActionExecutionResult
    ): List<Int> {
        val baseTargets = super.eligibleTargets(session, actor, alivePlayers, accumulatedState)

        return baseTargets.filter { targetId ->
            val targetPlayer = session.getPlayer(targetId) ?: return@filter false

            // Ghost Rider and Nightmare can NEVER be targeted by Wolf Kill
            if (targetPlayer.roles.contains("惡靈騎士") || targetPlayer.roles.contains("夢魘")) {
                return@filter false
            }

            // Other wolves depend on the setting
            if (!session.settings.allowWolfSelfKill && targetPlayer.wolf) {
                return@filter false
            }

            true
        }
    }

    override fun validate(session: Session, actor: Int, targets: List<Int>): String? {
        val baseError = super.validate(session, actor, targets)
        if (baseError != null) return baseError

        for (targetId in targets) {
            val targetPlayer = session.getPlayer(targetId) ?: continue
            // Ghost Rider cannot be targeted by Wolf Kill (cannot self-kill)
            if (targetPlayer.roles.contains("惡靈騎士")) {
                return "惡靈騎士不能自刀"
            }
            if (targetPlayer.roles.contains("夢魘")) {
                return "夢魘不能自刀"
            }

            if (!session.settings.allowWolfSelfKill && targetPlayer.wolf) {
                return "不允許狼人自殺"
            }
        }
        return null
    }
}

@Component
class WolfYoungerBrotherExtraKillAction : BaseRoleAction(
    actionId = ActionDefinitionId.WOLF_YOUNGER_BROTHER_EXTRA_KILL,
    priority = PredefinedRoles.WEREWOLF_PRIORITY + 1,
    timing = ActionTiming.NIGHT,
    usageLimit = 1,
    isOptional = false,
    allowMultiplePerPhase = true
) {
    override fun execute(
        session: Session,
        action: RoleActionInstance,
        accumulatedState: ActionExecutionResult
    ): ActionExecutionResult {
        if (action.targets.isEmpty() || action.targets[0] == -1) return accumulatedState

        val rawTargetId = action.targets[0]
        val targetId = session.stateData.getRealTarget(rawTargetId)
        accumulatedState.deaths.getOrPut(DeathCause.WEREWOLF) { mutableListOf() }.add(targetId)

        // Clear flag after execution (though it will also be cleared at end of night)
        session.stateData.wolfBrotherAwakenedPlayerId = null

        return accumulatedState
    }

    override fun isAvailable(session: Session, actor: Int): Boolean {
        if (!super.isAvailable(session, actor)) return false

        // Available if Wolf Brother died TODAY (this night is the revenge night)
        return session.stateData.wolfBrotherDiedDay == session.day
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

        val rawTargetId = action.targets[0]
        val targetId = session.stateData.getRealTarget(rawTargetId) // for magician
        val target = session.getPlayer(targetId) ?: return accumulatedState

        // Use the player originally selected for the display message
        val displayTarget = session.getPlayer(rawTargetId) ?: target

        val isWolfBrotherAlive = session.alivePlayers().values.any { it.roles.contains("狼兄") }
        val isYoungerBrother = target.roles.contains("狼弟")

        val isWolf: Boolean
        if (target.roles.contains("機械狼")) {
            val learned = session.wolfMechanicLearnedRole[targetId]
            // If inherited, exposed as wolf anyway
            if (session.isWolfMechanicInherited()) {
                isWolf = true
            } else if (learned != null) {
                // Determine if learned role is wolf faction
                val learnedRoleDef = session.hydratedRoles[learned] ?: roleRegistry.getRole(learned)
                isWolf = learnedRoleDef?.camp == Camp.WEREWOLF
            } else {
                // "沒學習他人被驗則是機械狼"
                isWolf = true
            }
        } else if (isYoungerBrother && isWolfBrotherAlive) {
            isWolf = false
        } else {
            isWolf = target.roles.any { role ->
                (session.hydratedRoles[role] ?: roleRegistry.getRole(role))?.camp == Camp.WEREWOLF
            }
        }

        val resultText = if (isWolf) "狼人" else "好人"
        val seerPlayer = session.getPlayer(action.actor)
        seerPlayer?.channel?.sendMessage("🔮 **查驗結果**：${displayTarget.nickname} 是 **$resultText**")?.queue()

        action.status = ActionStatus.PROCESSED
        return accumulatedState
    }
}

@Component
class PsychicCheckAction : BaseRoleAction(
    actionId = ActionDefinitionId.PSYCHIC_CHECK,
    priority = PredefinedRoles.PSYCHIC_PRIORITY,
    timing = ActionTiming.NIGHT,
    isImmediate = true
) {
    override fun execute(
        session: Session,
        action: RoleActionInstance,
        accumulatedState: ActionExecutionResult
    ): ActionExecutionResult {
        if (action.targets.isEmpty()) return accumulatedState

        val rawTargetId = action.targets[0]
        val targetId = session.stateData.getRealTarget(rawTargetId) // Handle Magician swap
        val target = session.getPlayer(targetId) ?: return accumulatedState

        val displayTarget = session.getPlayer(rawTargetId) ?: target

        val resultRoleName: String
        if (target.roles.contains("機械狼")) {
            val learned = session.wolfMechanicLearnedRole[targetId]
            resultRoleName = learned ?: "機械狼"
        } else {
            resultRoleName =
                target.roles.firstOrNull { it !in target.deadRoles } ?: target.roles.firstOrNull() ?: "平民"
        }

        val psychicPlayer = session.getPlayer(action.actor)
        psychicPlayer?.channel?.sendMessage("🔮 **查驗結果**：${displayTarget.nickname} 的具體身分是 **$resultRoleName**")
            ?.queue()

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
        val rawTargetId = action.targets.firstOrNull() ?: return accumulatedState
        val targetId = session.stateData.getRealTarget(rawTargetId)

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
        val rawTargetId = action.targets.firstOrNull() ?: return accumulatedState
        val targetId = session.stateData.getRealTarget(rawTargetId)
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
    private fun getLastProtectedId(session: Session): Int? {
        if (session.day <= 1) return null
        val lastDayActions = session.stateData.executedActions[session.day - 1] ?: return null
        return lastDayActions.find {
            it.actionDefinitionId == ActionDefinitionId.GUARD_PROTECT
        }?.targets?.firstOrNull()?.takeIf { it != SKIP_TARGET_ID }
    }

    override fun execute(
        session: Session,
        action: RoleActionInstance,
        accumulatedState: ActionExecutionResult
    ): ActionExecutionResult {
        val rawTargetId = action.targets.firstOrNull() ?: return accumulatedState
        val targetId = session.stateData.getRealTarget(rawTargetId)

        // Note: Guard "cannot guard same person" rule check logic needs to consider if lastProtected was raw or real.
        // Currently getLastProtectedId returns the RAW target from executedActions.
        // The validate function uses the raw target check.
        // For consistency, we might need to store the REAL target in executedActions or resolve it here.
        // However, standard werewolf rules often say "cannot choose the same NUMBER".
        // If the rule is "same NUMBER", then `getLastProtectedId` (raw) == `rawTargetId` check in validate is correct.
        // But the PROTECTION should land on the REAL target.

        val lastProtected = getLastProtectedId(session)
        if (lastProtected == rawTargetId) return accumulatedState

        accumulatedState.protectedPlayers.add(targetId)
        return accumulatedState
    }

    override fun eligibleTargets(
        session: Session,
        actor: Int,
        alivePlayers: List<Int>,
        accumulatedState: ActionExecutionResult
    ): List<Int> {
        val lastProtected = getLastProtectedId(session)
        return if (lastProtected != null) {
            alivePlayers.filter { it != lastProtected }
        } else {
            alivePlayers
        }
    }

    override fun validate(session: Session, actor: Int, targets: List<Int>): String? {
        val baseError = super.validate(session, actor, targets)
        if (baseError != null) return baseError

        val targetId = targets.firstOrNull()
        val lastProtected = getLastProtectedId(session)

        if (targetId != null && targetId == lastProtected) {
            return "不能連續兩晚守護同一個人"
        }
        return null
    }
}

abstract class AbstractRevengeAction(
    actionId: ActionDefinitionId,
    priority: Int,
    private val deathCause: DeathCause,
) : BaseRoleAction(
    actionId = actionId,
    priority = priority,
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
        val rawTargetId = action.targets.firstOrNull() ?: return accumulatedState
        val targetId = session.stateData.getRealTarget(rawTargetId)
        accumulatedState.deaths.getOrPut(deathCause) { mutableListOf() }.add(targetId)

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
        if (!cause.preventsRevenge) {
            session.stateData.playerOwnedActions.getOrPut(actor) { mutableMapOf() }[actionId.toString()] = 1
        } else {
            session.getPlayer(actor)?.channel?.sendMessage("⚠️ **你${cause.logMessage}**！你感到身體虛弱，無法發動技能。")
                ?.queue()
        }
    }
}

@Component
class HunterRevengeAction : AbstractRevengeAction(
    actionId = ActionDefinitionId.HUNTER_REVENGE,
    priority = PredefinedRoles.HUNTER_PRIORITY,
    deathCause = DeathCause.HUNTER_REVENGE,
)

@Component
class WolfKingRevengeAction : AbstractRevengeAction(
    actionId = ActionDefinitionId.WOLF_KING_REVENGE,
    priority = PredefinedRoles.HUNTER_PRIORITY,
    deathCause = DeathCause.WOLF_KING_REVENGE,
)

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

        // Dream Weaver Logic
        val currentSleepwalkerId = session.stateData.dreamWeaverTargets[session.day]
        val prevSleepwalkerId = session.stateData.dreamWeaverTargets[session.day - 1]
        val dreamWeaverId = session.alivePlayers().values.find { it.roles.contains("攝夢人") }?.id

        // 1. Dream Weaver Immunity: Sleepwalker is immune to night damage (except Dream Weaver's own effects)
        if (currentSleepwalkerId != null) {
            deaths.values.forEach { it.removeIf { id -> id == currentSleepwalkerId } }
        }

        // 2. Dream Weaver Consecutive Death
        if (currentSleepwalkerId != null && currentSleepwalkerId == prevSleepwalkerId) {
            deaths.getOrPut(DeathCause.DREAM_WEAVER) { mutableListOf() }.add(currentSleepwalkerId)
        }

        // 3. Dream Weaver Linked Death
        // Check if Dream Weaver is dying tonight
        if (dreamWeaverId != null && currentSleepwalkerId != null) {
            val isDreamWeaverDying = deaths.values.flatten().contains(dreamWeaverId)
            if (isDreamWeaverDying) {
                deaths.getOrPut(DeathCause.DREAM_WEAVER) { mutableListOf() }.add(currentSleepwalkerId)
            }
        }

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

abstract class AbstractMerchantTradeAction(
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
        val rawTargetId = action.targets.firstOrNull() ?: return accumulatedState
        val targetId = session.stateData.getRealTarget(rawTargetId)
        val target = session.getPlayer(targetId) ?: return accumulatedState

        val isWolf = target.wolf
        val actorPlayer = session.getPlayer(action.actor)
        // Simple heuristic to get role name, or default to "黑市商人"
        val roleName = if (actorPlayer?.roles?.contains("奇蹟商人") == true) "奇蹟商人" else "黑市商人"

        if (isWolf) {
            accumulatedState.deaths.getOrPut(DeathCause.TRADED_WITH_WOLF) { mutableListOf() }.add(action.actor)
            session.addLog(LogType.SYSTEM, "${roleName}與狼人交易，不幸出局")
            return accumulatedState
        } else {
            skillType.let { id ->
                val playerActions = session.stateData.playerOwnedActions.getOrPut(targetId) { mutableMapOf() }
                playerActions[id.toString()] = 1
            }

            target.channel?.sendMessage("🎁 **你收到了${roleName}的禮物**！\n你獲得了技能：**${skillType.actionName}**\n你可以在**下一晚**開始使用它。")
                ?.queue()

            session.addLog(LogType.SYSTEM, "${roleName}交易成功，將技能 $skillType 贈予了玩家 $targetId")
        }
        return accumulatedState
    }

    override fun eligibleTargets(
        session: Session,
        actor: Int,
        alivePlayers: List<Int>,
        accumulatedState: ActionExecutionResult
    ): List<Int> {
        return alivePlayers.filter { it != actor }
    }

    override fun isAvailable(session: Session, actor: Int): Boolean {
        // 1. Check if ANY Merchant action was EXECUTED in the past.
        val executedActions = session.stateData.executedActions.values.flatten()
        val hasTraded = executedActions.any {
            it.actor == actor && (
                it.actionDefinitionId.toString().startsWith("DARK_MERCHANT_TRADE_") ||
                    it.actionDefinitionId.toString().startsWith("MIRACLE_MERCHANT_TRADE_")
                )
        }
        if (hasTraded) return false

        // 2. Check if ANOTHER Merchant action is currently SUBMITTED.
        val otherSubmitted = session.stateData.submittedActions.any {
            it.actor == actor &&
                it.actionDefinitionId != this.actionId &&
                (it.actionDefinitionId.toString().startsWith("DARK_MERCHANT_TRADE_") ||
                    it.actionDefinitionId.toString().startsWith("MIRACLE_MERCHANT_TRADE_")) &&
                (it.status == ActionStatus.SUBMITTED || it.status == ActionStatus.PROCESSED)
        }

        if (otherSubmitted) return false

        return super.isAvailable(session, actor)
    }
}

abstract class DarkMerchantTradeAction(
    actionId: ActionDefinitionId,
    skillType: ActionDefinitionId
) : AbstractMerchantTradeAction(actionId, skillType)

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
class MiracleMerchantTradeGuardAction : AbstractMerchantTradeAction(
    ActionDefinitionId.MIRACLE_MERCHANT_TRADE_GUARD, ActionDefinitionId.MERCHANT_GUARD_PROTECT
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
        val rawTargetId = action.targets.firstOrNull() ?: return accumulatedState
        val targetId = session.stateData.getRealTarget(rawTargetId)
        val target = session.getPlayer(targetId) ?: return accumulatedState

        // Use the player originally selected for the display message
        val displayTarget = session.getPlayer(rawTargetId) ?: target

        val isWolf = target.roles.any { role ->
            (session.hydratedRoles[role] ?: roleRegistry.getRole(role))?.camp == Camp.WEREWOLF
        }

        val seerPlayer = session.getPlayer(action.actor)
        val resultText = if (isWolf) "狼人" else "好人"
        seerPlayer?.channel?.sendMessage("🔮 **查驗結果**：${displayTarget.nickname} 是 **$resultText**")?.queue()

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
        val rawTargetId = action.targets.firstOrNull() ?: return accumulatedState
        val targetId = session.stateData.getRealTarget(rawTargetId)
        accumulatedState.deaths.getOrPut(DeathCause.POISON) { mutableListOf() }.add(targetId)
        return accumulatedState
    }
}

@Component
class MerchantGunAction : BaseRoleAction(
    actionId = ActionDefinitionId.MERCHANT_GUN,
    priority = PredefinedRoles.HUNTER_PRIORITY + 1,
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
        val rawTargetId = action.targets.firstOrNull() ?: return accumulatedState
        val targetId = session.stateData.getRealTarget(rawTargetId)
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
        if (cause == DeathCause.POISON) {
            // If poisoned, lose the gun (remove from owned actions) and notify
            session.stateData.playerOwnedActions[actor]?.remove(actionId.toString())
            session.getPlayer(actor)?.channel?.sendMessage("🧪 **你被女巫毒死了**！你感到身體虛弱，無法使用商人贈予的獵槍。")
                ?.queue()
        }
    }
}

@Component
class MerchantGuardProtectAction : BaseRoleAction(
    actionId = ActionDefinitionId.MERCHANT_GUARD_PROTECT,
    priority = PredefinedRoles.GUARD_PRIORITY + 1,
    timing = ActionTiming.NIGHT,
    usageLimit = 1
) {
    override fun execute(
        session: Session,
        action: RoleActionInstance,
        accumulatedState: ActionExecutionResult
    ): ActionExecutionResult {
        val rawTargetId = action.targets.firstOrNull() ?: return accumulatedState
        val targetId = session.stateData.getRealTarget(rawTargetId)
        accumulatedState.protectedPlayers.add(targetId)
        return accumulatedState
    }
}
