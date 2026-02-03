package dev.robothanzo.werewolf.game.steps

import dev.robothanzo.werewolf.database.documents.Session
import dev.robothanzo.werewolf.game.GameStep
import dev.robothanzo.werewolf.service.GameStateService
import org.springframework.stereotype.Component

@Component
class SheriffElectionStep : GameStep {
    override val id = "SHERIFF_ELECTION"
    override val name = "警長參選"

    override fun onStart(session: Session, service: GameStateService) {
        // Announce election start
    }

    override fun onEnd(session: Session, service: GameStateService) {
        // Calculate votes and assign sheriff
    }

    override fun handleInput(session: Session, input: Map<String, Any>): Map<String, Any> {
        return mapOf("success" to true)
    }
}
