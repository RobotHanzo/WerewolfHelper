package dev.robothanzo.werewolf.game

import dev.robothanzo.werewolf.database.documents.Session
import dev.robothanzo.werewolf.service.GameStateService

/**
 * Abstract class for a step in the game (e.g., Night, Speech, Voting).
 * Handles common logic like start time tracking.
 */
abstract class GameStep {
    /**
     * The unique identifier for this step.
     */
    abstract val id: String

    /**
     * The human-readable name of this step (for display).
     */
    abstract val name: String

    /**
     * Called when the game enters this step.
     * Records the start time by default.
     */
    open fun onStart(session: Session, service: GameStateService) {
        session.stateData.stepStartTime = System.currentTimeMillis()
    }

    /**
     * Called when the game leaves this step.
     */
    open fun onEnd(session: Session, service: GameStateService) {}

    /**
     * Handles inputs from the judge or players specific to this step.
     * @return Result of the action (success/failure/message)
     */
    abstract fun handleInput(session: Session, input: Map<String, Any>): Map<String, Any>

    /**
     * Get the expected end time timestamp for this step.
     * @return timestamp in ms, or 0 if indefinite.
     */
    open fun getEndTime(session: Session): Long = 0L
}
