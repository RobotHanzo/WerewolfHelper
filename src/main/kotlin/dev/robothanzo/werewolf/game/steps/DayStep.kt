package dev.robothanzo.werewolf.game.steps

import dev.robothanzo.werewolf.database.documents.Session
import dev.robothanzo.werewolf.game.GameStep
import dev.robothanzo.werewolf.service.GameActionService
import dev.robothanzo.werewolf.service.GameStateService
import org.springframework.stereotype.Component

@Component
class DayStep(
    private val gameActionService: GameActionService
) : GameStep {
    override val id = "DAY_PHASE"
    override val name = "天亮了"

    override fun onStart(session: Session, service: GameStateService) {
        // Unmute everyone? Or keep muted for police election?
        // Usually day start = announcement, then specific phases handle muting
        gameActionService.muteAll(session.guildId, true) // Ensure silence for announcement
    }

    override fun onEnd(session: Session, service: GameStateService) {

    }

    override fun handleInput(session: Session, input: Map<String, Any>): Map<String, Any> {
        return mapOf("success" to true)
    }
}
