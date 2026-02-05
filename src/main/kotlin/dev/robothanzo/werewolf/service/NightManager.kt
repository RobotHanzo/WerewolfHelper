package dev.robothanzo.werewolf.service

import dev.robothanzo.werewolf.database.documents.Session
import dev.robothanzo.werewolf.game.model.NightStatus
import kotlinx.coroutines.flow.SharedFlow

interface NightManager {
    /**
     * Build the full night status for a session to be sent to the dashboard.
     */
    fun buildNightStatus(session: Session): NightStatus

    /**
     * Broadcast the current night status to the dashboard.
     */
    fun broadcastNightStatus(session: Session)

    /**
     * Notify that a phase-related event occurred (e.g., vote cast, action submitted).
     * This may trigger early resolution of a phase if all participants have acted.
     */
    fun notifyPhaseUpdate(guildId: Long)

    /**
     * Get the flow of guild IDs that have had night phase updates.
     */
    fun getUpdateFlow(): SharedFlow<Long>

    /**
     * Wait for a phase to complete or until timeout.
     * @return The latest session state after completion or timeout.
     */
    suspend fun waitForPhase(guildId: Long, timeoutMillis: Long): Session
}
