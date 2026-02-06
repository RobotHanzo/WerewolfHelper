package dev.robothanzo.werewolf.service.impl

import dev.robothanzo.werewolf.database.documents.Session
import dev.robothanzo.werewolf.game.model.ActionSubmissionStatus
import dev.robothanzo.werewolf.game.model.NightStatus
import dev.robothanzo.werewolf.service.GameSessionService
import dev.robothanzo.werewolf.service.NightManager
import kotlinx.coroutines.flow.MutableSharedFlow
import org.springframework.context.annotation.Lazy
import org.springframework.stereotype.Service

@Service
class NightManagerImpl(
    @param:Lazy
    private val gameSessionService: GameSessionService
) : NightManager {
    private val phaseUpdateFlow = MutableSharedFlow<Long>(extraBufferCapacity = 1)

    override fun buildNightStatus(session: Session): NightStatus {
        val now = System.currentTimeMillis()
        val endTime =
            if (session.stateData.phaseEndTime > 0) session.stateData.phaseEndTime else session.currentStepEndTime

        // werewolfMessages
        val werewolfMessages = session.stateData.werewolfMessages

        // werewolfVotes (from GroupActionState)
        val groupState = session.stateData.groupStates[dev.robothanzo.werewolf.game.roles.PredefinedRoles.WEREWOLF_KILL]
        val werewolfVotes = groupState?.votes ?: emptyList()

        // actionStatuses
        val actionStatuses = session.stateData.actionData.values.map { data ->
            ActionSubmissionStatus(
                playerId = data.playerId,
                role = data.role,
                status = data.status,
                actionType = data.selectedAction?.actionName,
                targetId = data.selectedTargets.firstOrNull()?.toString(),
                submittedAt = data.submittedAt
            )
        }

        val phaseType = session.stateData.phaseType ?: "WEREWOLF_VOTING"

        return NightStatus(
            day = session.day,
            phaseType = phaseType,
            startTime = session.stateData.phaseStartTime.takeIf { it > 0 } ?: now,
            endTime = endTime,
            werewolfMessages = werewolfMessages,
            werewolfVotes = werewolfVotes,
            actionStatuses = actionStatuses
        )
    }

    override fun broadcastNightStatus(session: Session) {
        val status = buildNightStatus(session)
        gameSessionService.broadcastEvent(
            "NIGHT_STATUS_UPDATED", mapOf(
                "guildId" to session.guildId.toString(),
                "nightStatus" to status
            )
        )
    }

    override fun notifyPhaseUpdate(guildId: Long) {
        phaseUpdateFlow.tryEmit(guildId)
    }

    override suspend fun waitForPhase(guildId: Long, timeoutMillis: Long): Session {
        // This should be called from a coroutine, but since the step engine is thread-based,
        // we might eventually bridge this. For now, let's stick to the current structure.
        // Wait, if I'm removing polling loops, I should use coroutines if possible.
        // The NightStep.run is currently:
        // override fun run(session: Session, service: GameStateService) { runBlocking { ... } }

        // Logic will be implemented in NightStep directly using the flow
        // but I provide a way to get the latest session.
        return gameSessionService.getSession(guildId).orElseThrow { Exception("Session not found") }
    }

    // Helper for NightStep to wait on the flow
    override fun getUpdateFlow(): kotlinx.coroutines.flow.SharedFlow<Long> = phaseUpdateFlow
}
