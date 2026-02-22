package dev.robothanzo.werewolf.game.steps.tasks

import dev.robothanzo.werewolf.database.documents.LogType
import dev.robothanzo.werewolf.database.documents.Session
import dev.robothanzo.werewolf.game.model.*
import dev.robothanzo.werewolf.game.roles.PredefinedRoles
import dev.robothanzo.werewolf.game.steps.NightStep
import dev.robothanzo.werewolf.game.steps.NightTask

internal interface WerewolfVotingTask : NightTask {
    override val phase: NightPhase get() = NightPhase.WEREWOLF_VOTING
    override fun shouldExecute(session: Session): Boolean {
        val fearedId = session.stateData.nightmareFearTargets[session.day]
        val isAnyWolfFeared = fearedId != null && session.getPlayer(fearedId)?.wolf == true
        if (isAnyWolfFeared) return false

        return session.players.values.any { p ->
            p.alive && p.wolf && !(p.roles.contains("狼弟") && session.isCharacterAlive("狼兄"))
        }
    }
}

object WerewolfVotingStart : WerewolfVotingTask {
    override suspend fun execute(step: NightStep, guildId: Long): Boolean {
        val session = step.gameSessionService.getSession(guildId).orElseThrow()

        // Nightmare Check
        val fearedId = session.stateData.nightmareFearTargets[session.day]
        val isAnyWolfFeared = fearedId != null && session.getPlayer(fearedId)?.wolf == true

        if (isAnyWolfFeared) {
            val msg = "⚠️ **警告**：由於一名狼人被夢魘恐懼，今晚全體狼人無法行動。"
            session.players.values.filter { it.alive && it.wolf }.forEach {
                it.channel?.sendMessage(msg)?.queue()
            }
            session.addLog(LogType.SYSTEM, "由於一名狼人被恐懼，狼人陣營今晚無法行兇")
            step.gameSessionService.saveSession(session)
            return false
        }

        val werewolves = session.players.values.filter { p ->
            if (!p.alive || !p.wolf) return@filter false
            if (p.roles.contains("狼弟") && session.isCharacterAlive("狼兄")) return@filter false
            true
        }.map { it.id }.sorted()

        if (werewolves.isNotEmpty()) {
            step.gameSessionService.withLockedSession(guildId) { lockedSession ->
                lockedSession.stateData.phaseType = NightPhase.WEREWOLF_VOTING
                lockedSession.stateData.phaseStartTime = System.currentTimeMillis()
                val durationMs = NightPhase.WEREWOLF_VOTING.defaultDurationMs
                // Total time 90s usually (60s wait + 30s force) but UI prompt set to 90
                step.actionUIService.promptGroupForAction(
                    guildId,
                    lockedSession,
                    PredefinedRoles.WEREWOLF_KILL,
                    werewolves,
                    (durationMs / 1000L).toInt()
                )
                lockedSession.addLog(LogType.SYSTEM, "狼人進行討論投票，時限${(durationMs / 1000L).toInt()}秒")
                lockedSession.stateData.phaseEndTime = System.currentTimeMillis() + durationMs
            }
            return true
        }
        return false
    }
}

object WerewolfVotingWait : WerewolfVotingTask {
    override suspend fun execute(step: NightStep, guildId: Long): Boolean {
        // Use full duration (usually 90s)
        val durationSeconds = (NightPhase.WEREWOLF_VOTING.defaultDurationMs / 1000L).toInt()
        val finishedEarly = step.waitForCondition(guildId, durationSeconds) {
            val session = step.gameSessionService.getSession(guildId).orElse(null) ?: return@waitForCondition true
            val groupState = step.actionUIService.getGroupState(session, PredefinedRoles.WEREWOLF_KILL)
                ?: return@waitForCondition true
            groupState.electorates.all { electorateId ->
                groupState.votes.any { it.voterId == electorateId && it.targetId != null }
            }
        }
        return !finishedEarly
    }
}

object WerewolfVotingCleanup : WerewolfVotingTask {
    override val isSkippable = false
    override suspend fun execute(step: NightStep, guildId: Long): Boolean {
        step.gameSessionService.withLockedSession(guildId) { session ->
            val groupState = session.stateData.wolfStates[PredefinedRoles.WEREWOLF_KILL] ?: return@withLockedSession
            val electorates = groupState.electorates
            electorates.forEach { pid ->
                if (groupState.votes.none { it.voterId == pid }) {
                    groupState.votes.add(WolfVote(voterId = pid, targetId = SKIP_TARGET_ID))
                }
            }

            val chosenTarget = step.actionUIService.resolveGroupVote(session, groupState)
            val resultText = if (chosenTarget == null || chosenTarget == SKIP_TARGET_ID) {
                "✓ **投票結果**：全體選擇 **跳過**，本夜無人被擊殺"
            } else {
                val targetPlayer = session.getPlayer(chosenTarget)
                "✓ **投票結果**：擊殺 **${targetPlayer?.nickname ?: "未知"}**"
            }

            val msg = buildString {
                appendLine("🐺 **狼人行動階段結束**")
                appendLine(resultText)
            }

            session.players.values.filter { electorates.contains(it.id) }
                .forEach { it.channel?.sendMessage(msg)?.queue() }
            session.judgeTextChannel?.sendMessage(resultText)?.queue()
            session.addLog(LogType.SYSTEM, resultText.replace("**", "").replace("✓ ", "").replace("：", " → "))

            if (chosenTarget != null) {
                val actorId = groupState.electorates.firstOrNull { pid ->
                    session.alivePlayers().containsKey(pid.toString())
                } ?: groupState.electorates.firstOrNull() ?: 0

                session.validateAndSubmitAction(
                    ActionDefinitionId.WEREWOLF_KILL,
                    actorId,
                    arrayListOf(chosenTarget),
                    "SYSTEM",
                    step.roleRegistry,
                    step.roleActionExecutor
                )
            }
            step.actionUIService.cleanupExpiredPrompts(session)
            session.stateData.phaseType = null
        }
        return false
    }
}
