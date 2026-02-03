package dev.robothanzo.werewolf.game.steps

import dev.robothanzo.werewolf.database.documents.Session
import dev.robothanzo.werewolf.game.GameStep
import dev.robothanzo.werewolf.service.GameActionService
import dev.robothanzo.werewolf.service.GameStateService
import org.springframework.stereotype.Component

@Component
class DeathAnnouncementStep(
    private val gameActionService: GameActionService
) : GameStep {
    override val id = "DEATH_ANNOUNCEMENT"
    override val name = "宣布死訊"

    override fun onStart(session: Session, service: GameStateService) {
        // Announce who died last night
        // This likely needs logic to check night deaths
    }

    override fun onEnd(session: Session, service: GameStateService) {
        // Handle last words if applicable
    }

    override fun handleInput(session: Session, input: Map<String, Any>): Map<String, Any> {
        return mapOf("success" to true)
    }
}
