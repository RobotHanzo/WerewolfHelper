package dev.robothanzo.werewolf.game.steps.tasks

import dev.robothanzo.werewolf.database.documents.LogType
import dev.robothanzo.werewolf.game.model.ActionDefinitionId
import dev.robothanzo.werewolf.game.model.NightPhase
import dev.robothanzo.werewolf.game.model.getAvailableActionsForPlayer
import dev.robothanzo.werewolf.game.steps.NightStep
import dev.robothanzo.werewolf.game.steps.NightTask

// 0.5. Magician (Runs after Nightmare/Brother, before Dream Weaver/Wolves)
// Actually Nightmare is 0. Magician should be early.
// Let's check NightSequence in NightStep.kt.
// Nightmare -> WolfBrother -> WerewolfVoting -> RoleActions
// Magician should be before WerewolfVoting.
// And Magician might affect Dream Weaver? Dream Weaver is in RoleActions.
// Magician swap affects "actions targeting".
// Dream Weaver links 2 players. If Magician swaps A and B. Dream Weaver links A.
// Does it link B? Yes.
// So Magician should happen before any action that targets.
// Nightmare targets? "Nightmare Fear".
// If Magician swaps A and B. Nightmare fears A.
// Should B be feared?
// Description says: "Magician exchanges... logic... when Wolf kills 1... Witch sees 1... Seer checks 1..."
// It doesn't explicitly mention Nightmare. But generally swap affects all target-based actions.
// HOWEVER, Magician acts "every night".
// Nightmare acts at start of night.
// If Magician acts simultaneously or after?
// Usually Magician acts very early.
// Let's place Magician tasks here.

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
                    lockedSession.stateData.phaseStartTime = startTime
                    lockedSession.stateData.phaseEndTime = startTime + 60_000 // 60s

                    step.actionUIService.promptPlayerForAction(
                        guildId,
                        lockedSession,
                        magician.id,
                        listOf(swapAction),
                        60
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
        val finishedEarly = step.waitForCondition(guildId, 60) {
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
