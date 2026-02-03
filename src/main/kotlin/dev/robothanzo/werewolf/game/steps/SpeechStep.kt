package dev.robothanzo.werewolf.game.steps

import dev.robothanzo.werewolf.database.documents.Session
import dev.robothanzo.werewolf.game.GameStep
import dev.robothanzo.werewolf.service.GameStateService
import org.springframework.context.annotation.Lazy
import org.springframework.stereotype.Component

@Component
class SpeechStep(
    @Lazy private val speechService: dev.robothanzo.werewolf.service.SpeechService
) : GameStep {
    override val id = "SPEECH_PHASE"
    override val name = "發言流程"

    override fun onStart(session: Session, service: GameStateService) {
        // Automatically start speech flow when entering this step
        speechService.startAutoSpeechFlow(session.guildId, session.courtTextChannelId)
    }

    override fun onEnd(session: Session, service: GameStateService) {
        // End speech
    }

    override fun handleInput(session: Session, input: Map<String, Any>): Map<String, Any> {
        return mapOf("success" to true)
    }
}
