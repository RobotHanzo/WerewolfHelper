package dev.robothanzo.werewolf.game.roles.actions

import dev.robothanzo.werewolf.database.documents.LogType
import dev.robothanzo.werewolf.database.documents.Player
import dev.robothanzo.werewolf.database.documents.Session
import dev.robothanzo.werewolf.game.model.*
import dev.robothanzo.werewolf.game.roles.PredefinedRoles
import dev.robothanzo.werewolf.game.roles.RoleRegistry
import org.springframework.context.annotation.Lazy
import org.springframework.data.annotation.Transient
import org.springframework.stereotype.Component

@Component
class WolfMechanicLearnAction(
    @Transient @param:Lazy private val roleRegistry: RoleRegistry
) : BaseRoleAction(
    actionId = ActionDefinitionId.WOLF_MECHANIC_LEARN,
    priority = PredefinedRoles.WOLF_MECHANIC_PRIORITY,
    timing = ActionTiming.NIGHT,
    usageLimit = 1,
    isImmediate = true
) {
    override fun execute(
        session: Session,
        action: RoleActionInstance,
        accumulatedState: ActionExecutionResult
    ): ActionExecutionResult {
        if (action.targets.isEmpty()) return accumulatedState

        val rawTargetId = action.targets[0]
        val targetId = session.stateData.getRealTarget(rawTargetId)
        val targetPlayer = session.getPlayer(targetId) ?: return accumulatedState
        val actorPlayer = session.getPlayer(action.actor) ?: return accumulatedState

        // Determine learned role. First role that is not dead.
        val targetRoleName =
            targetPlayer.roles.firstOrNull { it !in targetPlayer.deadRoles } ?: targetPlayer.roles.firstOrNull()
            ?: "平民"

        // Grant action based on learned role
        val playerActions = session.stateData.playerOwnedActions.getOrPut(action.actor) { mutableMapOf() }
        when (targetRoleName) {
            "通靈師", "預言家" -> {
                playerActions[ActionDefinitionId.WOLF_MECHANIC_SEER_CHECK.toString()] = -1 // unlimited
                actorPlayer.channel?.sendMessage("⚙️ **學習成功**：你學習了 ${targetPlayer.nickname}，得知其身分為 **$targetRoleName**。\n你獲得了查驗技能，將在下一晚開始可以使用。")
                    ?.queue()
            }

            "女巫" -> {
                playerActions[ActionDefinitionId.WOLF_MECHANIC_POISON.toString()] = 1
                actorPlayer.channel?.sendMessage("⚙️ **學習成功**：你學習了 ${targetPlayer.nickname}，得知其身分為 **女巫**。\n你獲得了毒藥技能，將在下一晚開始可以使用。")
                    ?.queue()
            }

            "守衛" -> {
                playerActions[ActionDefinitionId.WOLF_MECHANIC_GUARD_PROTECT.toString()] = 1
                actorPlayer.channel?.sendMessage("⚙️ **學習成功**：你學習了 ${targetPlayer.nickname}，得知其身分為 **守衛**。\n你獲得了守衛技能，將在下一晚開始可以使用。")
                    ?.queue()
            }

            "獵人" -> {
                playerActions[ActionDefinitionId.WOLF_MECHANIC_HUNTER_REVENGE.toString()] = 1
                actorPlayer.channel?.sendMessage("⚙️ **學習成功**：你學習了 ${targetPlayer.nickname}，得知其身分為 **獵人**。\n你獲得了開槍技能 (如果今晚被淘汰，也適用)。")
                    ?.queue()
            }

            else -> {
                if (Player.isWolf(targetRoleName)) {
                    actorPlayer.channel?.sendMessage("⚙️ **學習成功**：你學習了 ${targetPlayer.nickname}，得知其身分為 **$targetRoleName**。\n繼承狼隊殺人權限後，你在機械狼階段可以額外刀一人。")
                        ?.queue()
                } else {
                    actorPlayer.channel?.sendMessage("⚙️ **學習成功**：你學習了 ${targetPlayer.nickname}，得知其身分為 **$targetRoleName**。\n你沒有獲得任何技能。")
                        ?.queue()
                }
            }
        }

        session.addLog(
            LogType.SYSTEM,
            "機械狼 ${actorPlayer.nickname} 學習了 ${targetPlayer.nickname} ($targetRoleName)"
        )
        action.status = ActionStatus.PROCESSED
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
}

@Component
class WolfMechanicSeerCheckAction(
    @Transient @param:Lazy private val roleRegistry: RoleRegistry
) : BaseRoleAction(
    actionId = ActionDefinitionId.WOLF_MECHANIC_SEER_CHECK,
    priority = PredefinedRoles.SEER_PRIORITY,
    timing = ActionTiming.NIGHT,
    isImmediate = true
) {
    override fun isAvailable(session: Session, actor: Int): Boolean {
        if (!super.isAvailable(session, actor)) return false
        val owned =
            session.stateData.playerOwnedActions[actor]?.get(ActionDefinitionId.WOLF_MECHANIC_SEER_CHECK.toString())
                ?: 0
        if (owned == 0) return false

        val learnDay = session.stateData.wolfMechanicLearnDay[actor] ?: return false
        return session.day > learnDay // Applicable from NEXT night
    }

    override fun execute(
        session: Session,
        action: RoleActionInstance,
        accumulatedState: ActionExecutionResult
    ): ActionExecutionResult {
        if (action.targets.isEmpty()) return accumulatedState

        val rawTargetId = action.targets[0]
        val targetId = session.stateData.getRealTarget(rawTargetId)
        val target = session.getPlayer(targetId) ?: return accumulatedState
        val displayTarget = session.getPlayer(rawTargetId) ?: target

        val learnedRoleName = session.wolfMechanicLearnedRole[action.actor]

        val resultText = if (learnedRoleName == "通靈師") {
            val roleName: String
            if (target.roles.contains("機械狼")) {
                val targetLearned = session.wolfMechanicLearnedRole[targetId]
                roleName = targetLearned ?: "機械狼"
            } else {
                roleName = target.roles.firstOrNull { it !in target.deadRoles } ?: target.roles.firstOrNull() ?: "平民"
            }
            roleName
        } else {
            val isWolfBrotherAlive = session.alivePlayers().values.any { it.roles.contains("狼兄") }
            val isYoungerBrother = target.roles.contains("狼弟")

            val isWolf = if (isYoungerBrother && isWolfBrotherAlive) {
                false
            } else {
                target.roles.any { role ->
                    (session.hydratedRoles[role] ?: roleRegistry.getRole(role))?.camp == Camp.WEREWOLF
                }
            }
            if (isWolf) "狼人" else "好人"
        }

        val actorPlayer = session.getPlayer(action.actor)
        actorPlayer?.channel?.sendMessage("🔮 **查驗結果 (機械狼)**：${displayTarget.nickname} 是 **$resultText**")
            ?.queue()

        action.status = ActionStatus.PROCESSED
        return accumulatedState
    }
}

@Component
class WolfMechanicPoisonAction : BaseRoleAction(
    actionId = ActionDefinitionId.WOLF_MECHANIC_POISON,
    priority = PredefinedRoles.WITCH_POISON_PRIORITY,
    timing = ActionTiming.NIGHT,
    usageLimit = 1
) {
    override fun isAvailable(session: Session, actor: Int): Boolean {
        if (!super.isAvailable(session, actor)) return false
        val owned =
            session.stateData.playerOwnedActions[actor]?.get(ActionDefinitionId.WOLF_MECHANIC_POISON.toString()) ?: 0
        if (owned == 0) return false

        val learnDay = session.stateData.wolfMechanicLearnDay[actor] ?: return false
        return session.day > learnDay // Applicable from NEXT night
    }

    override fun execute(
        session: Session,
        action: RoleActionInstance,
        accumulatedState: ActionExecutionResult
    ): ActionExecutionResult {
        val rawTargetId = action.targets.firstOrNull() ?: return accumulatedState
        val targetId = session.stateData.getRealTarget(rawTargetId)
        accumulatedState.deaths.getOrPut(DeathCause.POISON) { mutableListOf() }.add(targetId)

        // Consume the action
        session.stateData.playerOwnedActions[action.actor]?.remove(ActionDefinitionId.WOLF_MECHANIC_POISON.toString())
        return accumulatedState
    }
}

@Component
class WolfMechanicGuardProtectAction : BaseRoleAction(
    actionId = ActionDefinitionId.WOLF_MECHANIC_GUARD_PROTECT,
    priority = PredefinedRoles.GUARD_PRIORITY,
    timing = ActionTiming.NIGHT,
    usageLimit = 1
) {
    override fun isAvailable(session: Session, actor: Int): Boolean {
        if (!super.isAvailable(session, actor)) return false
        val owned =
            session.stateData.playerOwnedActions[actor]?.get(ActionDefinitionId.WOLF_MECHANIC_GUARD_PROTECT.toString())
                ?: 0
        if (owned == 0) return false

        val learnDay = session.stateData.wolfMechanicLearnDay[actor] ?: return false
        return session.day > learnDay // Applicable from NEXT night
    }

    override fun execute(
        session: Session,
        action: RoleActionInstance,
        accumulatedState: ActionExecutionResult
    ): ActionExecutionResult {
        val rawTargetId = action.targets.firstOrNull() ?: return accumulatedState
        val targetId = session.stateData.getRealTarget(rawTargetId)
        accumulatedState.protectedPlayers.add(targetId)

        // Consume the action
        session.stateData.playerOwnedActions[action.actor]?.remove(ActionDefinitionId.WOLF_MECHANIC_GUARD_PROTECT.toString())
        return accumulatedState
    }
}

@Component
class WolfMechanicHunterRevengeAction : AbstractRevengeAction(
    actionId = ActionDefinitionId.WOLF_MECHANIC_HUNTER_REVENGE,
    priority = PredefinedRoles.HUNTER_PRIORITY,
    deathCause = DeathCause.HUNTER_REVENGE
) {
    // Note: Available IMMEDIATELY, even on the same night they learned it
}

@Component
class WolfMechanicExtraKillAction : BaseRoleAction(
    actionId = ActionDefinitionId.WOLF_MECHANIC_EXTRA_KILL,
    priority = PredefinedRoles.WEREWOLF_PRIORITY + 1,
    timing = ActionTiming.NIGHT,
    isOptional = false,
    allowMultiplePerPhase = true
) {
    override fun isAvailable(session: Session, actor: Int): Boolean {
        if (!super.isAvailable(session, actor)) return false
        // Must have inherited the kill right
        if (!session.isWolfMechanicInherited()) return false

        // Must have learned a wolf role
        val learnedTargetId = session.stateData.wolfMechanicLearnedPlayerId[actor] ?: return false
        val learnedTarget = session.getPlayer(learnedTargetId) ?: return false
        if (!learnedTarget.wolf) return false

        return true
    }

    override fun execute(
        session: Session,
        action: RoleActionInstance,
        accumulatedState: ActionExecutionResult
    ): ActionExecutionResult {
        if (action.targets.isEmpty() || action.targets[0] == SKIP_TARGET_ID) return accumulatedState

        val rawTargetId = action.targets[0]
        val targetId = session.stateData.getRealTarget(rawTargetId)
        accumulatedState.deaths.getOrPut(DeathCause.WEREWOLF) { mutableListOf() }.add(targetId)
        return accumulatedState
    }
}
