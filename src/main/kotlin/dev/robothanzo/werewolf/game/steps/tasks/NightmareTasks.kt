package dev.robothanzo.werewolf.game.steps.tasks

import dev.robothanzo.werewolf.database.documents.LogType
import dev.robothanzo.werewolf.database.documents.Session
import dev.robothanzo.werewolf.game.model.ActionDefinitionId
import dev.robothanzo.werewolf.game.model.NightPhase
import dev.robothanzo.werewolf.game.model.getAvailableActionsForPlayer
import dev.robothanzo.werewolf.game.steps.NightStep
import dev.robothanzo.werewolf.game.steps.NightTask

internal interface NightmareTask : NightTask {
    override val phase: NightPhase get() = NightPhase.NIGHTMARE_ACTION
    override fun shouldExecute(session: Session) = session.isCharacterAlive("夢魘")
}

object NightmareStart : NightmareTask {
    override suspend fun execute(step: NightStep, guildId: Long): Boolean {
        return step.gameSessionService.withLockedSession(guildId) { lockedSession ->
            val nightmare = lockedSession.players.values.find {
                it.roles.contains("夢魘") && it.alive
            }

            if (nightmare != null) {
                val actions = lockedSession.getAvailableActionsForPlayer(nightmare.id, step.roleRegistry)
                val fearAction = actions.find { it.actionId == ActionDefinitionId.NIGHTMARE_FEAR }

                if (fearAction != null) {
                    lockedSession.stateData.phaseType = NightPhase.NIGHTMARE_ACTION
                    val startTime = System.currentTimeMillis()
                    val durationMs = NightPhase.NIGHTMARE_ACTION.defaultDurationMs
                    lockedSession.stateData.phaseStartTime = startTime
                    lockedSession.stateData.phaseEndTime = startTime + durationMs

                    step.actionUIService.promptPlayerForAction(
                        guildId,
                        lockedSession,
                        nightmare.id,
                        listOf(fearAction),
                        (durationMs / 1000L).toInt()
                    )
                    return@withLockedSession true
                }
            }
            false
        }
    }
}

object NightmareWait : NightmareTask {
    override suspend fun execute(step: NightStep, guildId: Long): Boolean {
        val durationSeconds = (NightPhase.NIGHTMARE_ACTION.defaultDurationMs / 1000L).toInt()
        val finishedEarly = step.waitForCondition(guildId, durationSeconds) {
            val session = step.gameSessionService.getSession(guildId).orElse(null) ?: return@waitForCondition true
            if (session.stateData.phaseType != NightPhase.NIGHTMARE_ACTION) return@waitForCondition true
            val nmAction = session.stateData.submittedActions.find { it.actorRole == "夢魘" }
            nmAction != null && nmAction.status.executed
        }
        return !finishedEarly
    }
}

object NightmareCleanup : NightmareTask {
    override val isSkippable = false
    override suspend fun execute(step: NightStep, guildId: Long): Boolean {
        step.gameSessionService.withLockedSession(guildId) { session ->
            step.actionUIService.cleanupExpiredPrompts(session)
            session.addLog(LogType.SYSTEM, "夢魘行動階段結束")
            session.stateData.phaseType = null
        }
        return false
    }
}
