package dev.robothanzo.werewolf.game.steps

import dev.robothanzo.werewolf.database.documents.Session
import dev.robothanzo.werewolf.game.GameStep
import dev.robothanzo.werewolf.service.GameStateService
import dev.robothanzo.werewolf.service.RoleActionService
import org.springframework.context.annotation.Lazy
import org.springframework.stereotype.Component

@Component
class SpeechStep(
    @param:Lazy private val speechService: dev.robothanzo.werewolf.service.SpeechService,
    private val roleActionService: RoleActionService
) : GameStep {
    override val id = "SPEECH_PHASE"
    override val name = "發言流程"

    override fun onStart(session: Session, service: GameStateService) {
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
