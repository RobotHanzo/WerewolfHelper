package dev.robothanzo.werewolf.game.steps

import dev.robothanzo.werewolf.database.documents.Session
import dev.robothanzo.werewolf.game.GameStep
import dev.robothanzo.werewolf.service.GameStateService
import org.springframework.stereotype.Component

@Component
class SetupStep : GameStep {
    override val id = "SETUP"
    override val name = "遊戲設置"

    override fun onStart(session: Session, service: GameStateService) {
        // Log setup started
    }

    override fun onEnd(session: Session, service: GameStateService) {
        // Ensure roles are assigned?
    }

    override fun handleInput(session: Session, input: Map<String, Any>): Map<String, Any> {
        return mapOf("success" to true)
    }

    override fun getDurationSeconds(session: Session): Int {
        return -1 // Manual start
    }
}
