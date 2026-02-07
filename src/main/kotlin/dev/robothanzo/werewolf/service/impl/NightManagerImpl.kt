package dev.robothanzo.werewolf.service.impl

import dev.robothanzo.werewolf.database.documents.Session
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
