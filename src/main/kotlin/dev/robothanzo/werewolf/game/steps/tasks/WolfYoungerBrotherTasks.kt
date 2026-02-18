package dev.robothanzo.werewolf.game.steps.tasks

import dev.robothanzo.werewolf.database.documents.LogType
import dev.robothanzo.werewolf.database.documents.Session
import dev.robothanzo.werewolf.game.model.ActionDefinitionId
import dev.robothanzo.werewolf.game.model.NightPhase
import dev.robothanzo.werewolf.game.model.getAvailableActionsForPlayer
import dev.robothanzo.werewolf.game.steps.NightStep
import dev.robothanzo.werewolf.game.steps.NightTask

internal interface WolfYoungerBrotherTask : NightTask {
    override val phase: NightPhase get() = NightPhase.WOLF_YOUNGER_BROTHER_ACTION
    override fun shouldExecute(session: Session) =
        session.isCharacterAlive("狼弟") && session.stateData.wolfBrotherDiedDay != null
}

object WolfYoungerBrotherStart : WolfYoungerBrotherTask {
    override suspend fun execute(step: NightStep, guildId: Long): Boolean {
        return step.gameSessionService.withLockedSession(guildId) { lockedSession ->
            val wolfYoungerBrother = lockedSession.players.values.find {
                it.roles.contains("狼弟") && it.alive
            }

            if (wolfYoungerBrother != null) {
                val actions = lockedSession.getAvailableActionsForPlayer(wolfYoungerBrother.id, step.roleRegistry)
                val extraKillAction =
                    actions.find { it.actionId == ActionDefinitionId.WOLF_YOUNGER_BROTHER_EXTRA_KILL }

                if (extraKillAction != null) {
                    lockedSession.stateData.phaseType = NightPhase.WOLF_YOUNGER_BROTHER_ACTION
                    val startTime = System.currentTimeMillis()
                    val durationMs = NightPhase.WOLF_YOUNGER_BROTHER_ACTION.defaultDurationMs
                    lockedSession.stateData.phaseStartTime = startTime
                    lockedSession.stateData.phaseEndTime = startTime + durationMs

                    step.actionUIService.promptPlayerForAction(
                        guildId,
                        lockedSession,
                        wolfYoungerBrother.id,
                        listOf(extraKillAction),
                        (durationMs / 1000L).toInt()
                    )
                    return@withLockedSession true
                }
            }
            false
        }
    }
}

object WolfYoungerBrotherWait : WolfYoungerBrotherTask {
    override suspend fun execute(step: NightStep, guildId: Long): Boolean {
        val durationSeconds = (NightPhase.WOLF_YOUNGER_BROTHER_ACTION.defaultDurationMs / 1000L).toInt()
        val finishedEarly = step.waitForCondition(guildId, durationSeconds) {
            val session = step.gameSessionService.getSession(guildId).orElse(null) ?: return@waitForCondition true
            if (session.stateData.phaseType != NightPhase.WOLF_YOUNGER_BROTHER_ACTION) return@waitForCondition true
            val ybAction = session.stateData.submittedActions.find { it.actorRole == "狼弟" }
            ybAction != null && ybAction.status.executed
        }
        return !finishedEarly
    }
}

object WolfYoungerBrotherCleanup : WolfYoungerBrotherTask {
    override val isSkippable = false
    override suspend fun execute(step: NightStep, guildId: Long): Boolean {
        step.gameSessionService.withLockedSession(guildId) { session ->
            step.actionUIService.cleanupExpiredPrompts(session)
            session.addLog(LogType.SYSTEM, "狼弟行動階段結束")
            session.stateData.phaseType = null
        }
        return false
    }
}
