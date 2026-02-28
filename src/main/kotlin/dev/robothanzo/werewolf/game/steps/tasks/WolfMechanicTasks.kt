package dev.robothanzo.werewolf.game.steps.tasks

import dev.robothanzo.werewolf.database.documents.LogType
import dev.robothanzo.werewolf.database.documents.Session
import dev.robothanzo.werewolf.game.model.ActionDefinitionId
import dev.robothanzo.werewolf.game.model.NightPhase
import dev.robothanzo.werewolf.game.steps.NightStep
import dev.robothanzo.werewolf.game.steps.NightTask

internal interface WolfMechanicTask : NightTask {
    override val phase: NightPhase get() = NightPhase.WOLF_MECHANIC_ACTION
    override fun shouldExecute(session: Session): Boolean {
        // Run only if any alive player is a Wolf Mechanic
        val mechanicId = session.alivePlayers().values.firstOrNull { it.roles.contains("機械狼") }?.id
        return mechanicId != null
    }
}

object WolfMechanicStart : WolfMechanicTask {
    override suspend fun execute(step: NightStep, guildId: Long): Boolean {
        val session = step.gameSessionService.getSession(guildId).orElseThrow()
        val mechanicId = session.alivePlayers().values.firstOrNull { it.roles.contains("機械狼") }?.id ?: return false

        // Nightmare check
        val fearedId = session.stateData.nightmareFearTargets[session.day]
        if (fearedId == mechanicId) {
            session.getPlayer(mechanicId)?.channel?.sendMessage("💤 **你被夢魘侵襲！**\n恐怖的夢境纏繞著你，今晚無法行動...")
                ?.queue()
            session.addLog(LogType.SYSTEM, "機械狼被夢魘恐懼，今晚無法行動")
            return false
        }

        var actions = listOf<dev.robothanzo.werewolf.game.roles.actions.RoleAction>()

        val learnedRole = session.wolfMechanicLearnedRole[mechanicId]
        if (learnedRole == null) {
            // Has not learned a role yet
            actions = listOf(step.roleRegistry.getAction(ActionDefinitionId.WOLF_MECHANIC_LEARN)!!)
        } else {
            // Already learned a role. Check if any skill actions are available.
            actions = listOfNotNull(
                step.roleRegistry.getAction(ActionDefinitionId.WOLF_MECHANIC_SEER_CHECK),
                step.roleRegistry.getAction(ActionDefinitionId.WOLF_MECHANIC_POISON),
                step.roleRegistry.getAction(ActionDefinitionId.WOLF_MECHANIC_GUARD_PROTECT),
                // Hunter revenge is a death trigger, shouldn't appear in night menu normally unless they also have a direct night skill?
                // Extra kill is handled in another phase or here? Wait, the extra kill is WOLF_MECHANIC_EXTRA_KILL night action.
                step.roleRegistry.getAction(ActionDefinitionId.WOLF_MECHANIC_EXTRA_KILL)
            ).filter { it.isAvailable(session, mechanicId) }
        }

        if (actions.isNotEmpty()) {
            step.gameSessionService.withLockedSession(guildId) { lockedSession ->
                lockedSession.stateData.phaseType = NightPhase.WOLF_MECHANIC_ACTION
                lockedSession.stateData.phaseStartTime = System.currentTimeMillis()
                val durationMs = NightPhase.WOLF_MECHANIC_ACTION.defaultDurationMs
                lockedSession.stateData.phaseEndTime = System.currentTimeMillis() + durationMs

                step.actionUIService.promptPlayerForAction(
                    guildId,
                    lockedSession,
                    mechanicId,
                    actions,
                    (durationMs / 1000L).toInt()
                )
            }
            return true
        }

        return false
    }
}

object WolfMechanicWait : WolfMechanicTask {
    override suspend fun execute(step: NightStep, guildId: Long): Boolean {
        val durationSeconds = (NightPhase.WOLF_MECHANIC_ACTION.defaultDurationMs / 1000L).toInt()
        val finishedEarly = step.waitForCondition(guildId, durationSeconds) {
            step.gameSessionService.withLockedSession(guildId) { session ->
                // Look for any PENDING or ACTING actions in the current phase with WOLF_MECHANIC_ prefixes
                session.stateData.submittedActions.none { action ->
                    action.actionDefinitionId?.toString()?.startsWith("WOLF_MECHANIC_") == true
                        && !action.status.executed
                }
            }
        }
        return !finishedEarly
    }
}

object WolfMechanicCleanup : WolfMechanicTask {
    override val isSkippable = false
    override suspend fun execute(step: NightStep, guildId: Long): Boolean {
        step.gameSessionService.withLockedSession(guildId) { session ->
            step.actionUIService.cleanupExpiredPrompts(session)
            session.addLog(LogType.SYSTEM, "機械狼單獨行動階段結束")
            session.stateData.phaseType = null
        }
        return false
    }
}
