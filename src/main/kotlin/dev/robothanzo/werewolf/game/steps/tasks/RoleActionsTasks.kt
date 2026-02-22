package dev.robothanzo.werewolf.game.steps.tasks

import dev.robothanzo.werewolf.database.documents.LogType
import dev.robothanzo.werewolf.game.model.ActionDefinitionId
import dev.robothanzo.werewolf.game.model.NightPhase
import dev.robothanzo.werewolf.game.model.getAvailableActionsForPlayer
import dev.robothanzo.werewolf.game.steps.NightStep
import dev.robothanzo.werewolf.game.steps.NightTask

internal interface RoleActionsTask : NightTask {
    override val phase: NightPhase get() = NightPhase.ROLE_ACTIONS
}

object RoleActionsStart : RoleActionsTask {
    override suspend fun execute(step: NightStep, guildId: Long): Boolean {
        return step.gameSessionService.withLockedSession(guildId) { lockedSession ->
            lockedSession.stateData.phaseType = NightPhase.ROLE_ACTIONS
            val roleStartTime = System.currentTimeMillis()
            val durationMs = NightPhase.ROLE_ACTIONS.defaultDurationMs
            lockedSession.stateData.phaseStartTime = roleStartTime
            lockedSession.stateData.phaseEndTime = roleStartTime + durationMs
            step.gameSessionService.broadcastSessionUpdate(lockedSession)

            val actors = mutableListOf<Int>()
            val currentFearedId = lockedSession.stateData.nightmareFearTargets[lockedSession.day]

            for (player in lockedSession.alivePlayers().values) {
                val pid = player.id

                // Fetch actions ignoring fear so we can tell if they WOULD have had actions
                var actions =
                    lockedSession.getAvailableActionsForPlayer(pid, step.roleRegistry, ignoreEffect = true)
                if (player.wolf) actions = actions.filter { it.actionId != ActionDefinitionId.WEREWOLF_KILL }
                actions =
                    actions.filter { action -> // we don't want to re-execute actions that were executed already at prior phases (e.g. wolf yb, nightmare and magician...etc)
                        lockedSession.stateData.submittedActions.none {
                            it.actor == pid && (it.actionDefinitionId == action.actionId || it.actionDefinitionId == null) && it.status.executed
                        }
                    }

                if (actions.isNotEmpty()) {
                    if (currentFearedId != null && currentFearedId == pid) {
                        player.channel?.sendMessage(
                            "💤 **你被夢魘侵襲！**\n恐怖的夢境纏繞著你，今晚你無法執行任何行動..."
                        )?.queue()
                        continue
                    }

                    actors.add(pid)
                    step.actionUIService.promptPlayerForAction(
                        guildId,
                        lockedSession,
                        pid,
                        actions,
                        (durationMs / 1000L).toInt()
                    )
                }
            }
            actors.isNotEmpty()
        }
    }
}

object RoleActionsWait : RoleActionsTask {
    override suspend fun execute(step: NightStep, guildId: Long): Boolean {
        val durationSeconds = (NightPhase.ROLE_ACTIONS.defaultDurationMs / 1000L).toInt()
        val finishedEarly = step.waitForCondition(guildId, durationSeconds) {
            step.gameSessionService.withLockedSession(guildId) { session ->
                session.stateData.submittedActions.all {
                    it.status.executed
                }
            }
        }
        return !finishedEarly
    }
}

object RoleActionsCleanup : RoleActionsTask {
    override val isSkippable = false
    override suspend fun execute(step: NightStep, guildId: Long): Boolean {
        step.gameSessionService.withLockedSession(guildId) { session ->
            step.actionUIService.cleanupExpiredPrompts(session)
            session.addLog(LogType.SYSTEM, "角色行動階段結束")
            session.stateData.phaseType = null
        }
        return false
    }
}
