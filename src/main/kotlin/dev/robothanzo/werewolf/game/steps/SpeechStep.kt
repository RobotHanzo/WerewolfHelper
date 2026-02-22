package dev.robothanzo.werewolf.game.steps

import dev.robothanzo.werewolf.database.documents.Session
import dev.robothanzo.werewolf.game.GameStep
import dev.robothanzo.werewolf.service.GameStateService
import dev.robothanzo.werewolf.service.SpeechService
import org.springframework.context.annotation.Lazy
import org.springframework.stereotype.Component


@Component
class SpeechStep(
    @param:Lazy private val speechService: SpeechService
) : GameStep() {
    override val id = "SPEECH_STEP"
    override val name = "發言流程"

    override fun getEndTime(session: Session): Long {
        val now = System.currentTimeMillis()
        val effectiveNow = if (session.stateData.paused) (session.stateData.pauseStartTime ?: now) else now
        val speechSession = speechService.getSpeechSession(session.guildId) ?: return super.getEndTime(session)

        var totalRemainingMs = if (speechSession.currentSpeechEndTime > effectiveNow) {
            speechSession.currentSpeechEndTime - effectiveNow
        } else 0L

        for (player in speechSession.order) {
            val duration = if (player.police) 210 else 180
            totalRemainingMs += duration * 1000L
        }

        return effectiveNow + totalRemainingMs
    }

    override fun onStart(session: Session, service: GameStateService) {
        super.onStart(session, service)
        // Automatically start speech flow when entering this step
        speechService.startAutoSpeechFlow(session, session.courtTextChannel?.idLong ?: 0) {
            service.nextStep(session)
        }
    }

    override fun onEnd(session: Session, service: GameStateService) {
        speechService.interruptSession(session.guildId)
    }

    override fun handleInput(session: Session, input: Map<String, Any>): Map<String, Any> {
        val action = input["action"] as? String
        return when (action) {
            else -> mapOf("success" to false)
        }
    }
}
