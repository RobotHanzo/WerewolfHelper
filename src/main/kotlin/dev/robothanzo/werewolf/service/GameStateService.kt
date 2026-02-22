package dev.robothanzo.werewolf.service

import dev.robothanzo.werewolf.database.documents.Session
import dev.robothanzo.werewolf.game.GameStep

interface GameStateService {
    fun registerStep(step: GameStep)
    fun getStep(stepId: String): GameStep?
    fun startStep(session: Session, stepId: String)
    fun nextStep(session: Session)
    fun handleInput(session: Session, input: Map<String, Any>): Map<String, Any>
    fun getAvailableSteps(): List<GameStep>
    fun getCurrentStep(session: Session): GameStep?
    fun pauseStep(session: Session)
    fun resumeStep(session: Session)
}
