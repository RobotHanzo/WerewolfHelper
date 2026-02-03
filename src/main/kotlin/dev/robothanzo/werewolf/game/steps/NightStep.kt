package dev.robothanzo.werewolf.game.steps

import dev.robothanzo.werewolf.database.documents.Session
import dev.robothanzo.werewolf.game.GameStep
import dev.robothanzo.werewolf.service.GameActionService
import dev.robothanzo.werewolf.service.GameStateService
import org.springframework.stereotype.Component

@Component
class NightStep(
    private val gameActionService: GameActionService
) : GameStep {
    override val id = "NIGHT_PHASE"
    override val name = "天黑請閉眼"

    override fun onStart(session: Session, service: GameStateService) {
        // Mute everyone
        gameActionService.muteAll(session.guildId, true)
        // TODO: Move players to night channels if needed
    }

    override fun onEnd(session: Session, service: GameStateService) {
        // Prepare for day
    }

    override fun handleInput(session: Session, input: Map<String, Any>): Map<String, Any> {
        return mapOf("success" to true)
    }

    override fun getDurationSeconds(session: Session): Int {
        return 30
    }
}
