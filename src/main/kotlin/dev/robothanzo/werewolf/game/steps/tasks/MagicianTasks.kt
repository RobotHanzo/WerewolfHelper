package dev.robothanzo.werewolf.game.steps.tasks

import dev.robothanzo.werewolf.database.documents.LogType
import dev.robothanzo.werewolf.game.model.ActionDefinitionId
import dev.robothanzo.werewolf.game.model.NightPhase
import dev.robothanzo.werewolf.game.model.getAvailableActionsForPlayer
import dev.robothanzo.werewolf.game.steps.NightStep
import dev.robothanzo.werewolf.game.steps.NightTask

object MagicianStart : NightTask {
    override val phase = NightPhase.MAGICIAN_ACTION
    override suspend fun execute(step: NightStep, guildId: Long): Boolean {
        return step.gameSessionService.withLockedSession(guildId) { lockedSession ->
            val magician = lockedSession.players.values.find {
                it.roles.contains("魔術師") && it.alive
            }

            if (magician != null) {
                val actions = lockedSession.getAvailableActionsForPlayer(magician.id, step.roleRegistry)
                val swapAction = actions.find { it.actionId == ActionDefinitionId.MAGICIAN_SWAP }

                if (swapAction != null) {
                    lockedSession.stateData.phaseType = NightPhase.MAGICIAN_ACTION
                    val startTime = System.currentTimeMillis()
                    val durationMs = NightPhase.MAGICIAN_ACTION.defaultDurationMs
                    lockedSession.stateData.phaseStartTime = startTime
                    lockedSession.stateData.phaseEndTime = startTime + durationMs

                    step.actionUIService.promptPlayerForAction(
                        guildId,
                        lockedSession,
                        magician.id,
                        listOf(swapAction),
                        (durationMs / 1000L).toInt()
                    )
                    return@withLockedSession true
                }
            }
            false
        }
    }
}

object MagicianWait : NightTask {
    override val phase = NightPhase.MAGICIAN_ACTION
    override suspend fun execute(step: NightStep, guildId: Long): Boolean {
        val durationSeconds = (NightPhase.MAGICIAN_ACTION.defaultDurationMs / 1000L).toInt()
        val finishedEarly = step.waitForCondition(guildId, durationSeconds) {
            val session = step.gameSessionService.getSession(guildId).orElse(null) ?: return@waitForCondition true
            if (session.stateData.phaseType != NightPhase.MAGICIAN_ACTION) return@waitForCondition true
            val swapAction = session.stateData.submittedActions.find { it.actorRole == "魔術師" }
            swapAction != null && swapAction.status.executed
        }
        return !finishedEarly
    }
}

object MagicianCleanup : NightTask {
    override val phase = NightPhase.MAGICIAN_ACTION
    override val isSkippable = false
    override suspend fun execute(step: NightStep, guildId: Long): Boolean {
        step.gameSessionService.withLockedSession(guildId) { session ->
            step.actionUIService.cleanupExpiredPrompts(session)
            session.addLog(LogType.SYSTEM, "魔術師行動階段結束")
            session.stateData.phaseType = null
        }
        return false
    }
}
