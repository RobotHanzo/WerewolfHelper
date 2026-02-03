package dev.robothanzo.werewolf.game

import dev.robothanzo.werewolf.database.documents.Session
import dev.robothanzo.werewolf.service.GameStateService

/**
 * Interface for a step in the game (e.g., Night, Speech, Voting).
 */
interface GameStep {
    /**
     * The unique identifier for this step.
     */
    val id: String

    /**
     * The human-readable name of this step (for display).
     */
    val name: String

    /**
     * Called when the game enters this step.
     */
    fun onStart(session: Session, service: GameStateService)

    /**
     * Called when the game leaves this step.
     */
    fun onEnd(session: Session, service: GameStateService)

    /**
     * Handles inputs from the judge or players specific to this step.
     * @return Result of the action (success/failure/message)
     */
    fun handleInput(session: Session, input: Map<String, Any>): Map<String, Any>

    /**
     * Estimated duration in seconds for this step.
     * Return -1 for indefinite (manual advance).
     */
    fun getDurationSeconds(session: Session): Int = -1
}
