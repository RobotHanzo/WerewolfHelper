package dev.robothanzo.werewolf.game.steps

import dev.robothanzo.werewolf.database.documents.Session
import dev.robothanzo.werewolf.game.GameStep
import dev.robothanzo.werewolf.service.GameStateService
import org.springframework.stereotype.Component

@Component
class VotingStep : GameStep {
    override val id = "VOTING_PHASE"
    override val name = "放逐投票"

    override fun onStart(session: Session, service: GameStateService) {
        // Open voting channels/UI
    }

    override fun onEnd(session: Session, service: GameStateService) {
        // Tally votes and exile player
    }

    override fun handleInput(session: Session, input: Map<String, Any>): Map<String, Any> {
        return mapOf("success" to true)
    }

    override fun getDurationSeconds(session: Session): Int {
        return 60
    }
}
