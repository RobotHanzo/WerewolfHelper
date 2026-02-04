package dev.robothanzo.werewolf.service

import dev.robothanzo.werewolf.database.documents.Session
import dev.robothanzo.werewolf.game.model.DeathCause

/**
 * Service for performing game-related actions such as resetting the game,
 * marking players as dead, reviving players, and updating game state.
 */
interface GameActionService {
    /**
     * Resets the game session.
     *
     * @param session          the session to reset
     * @param statusCallback   callback for status messages
     * @param progressCallback callback for progress updates (0-100)
     * @throws Exception if an error occurs during the reset process
     */
    @Throws(Exception::class)
    fun resetGame(session: Session, statusCallback: (String) -> Unit, progressCallback: (Int) -> Unit)

    /**
     * Marks a player as dead in the game session.
     *
     * @param session        the session containing the player
     * @param userId         the ID of the user to mark as dead
     * @param allowLastWords whether the player is allowed to give last words
     */
    fun markPlayerDead(
        session: Session,
        userId: Long,
        allowLastWords: Boolean,
        cause: DeathCause = DeathCause.UNKNOWN
    )

    /**
     * Revives a player in the game session.
     *
     * @param session the session containing the player
     * @param userId  the ID of the user to revive
     */
    fun revivePlayer(session: Session, userId: Long)

    /**
     * Revives a player and assigns them a specific role.
     *
     * @param session the session containing the player
     * @param userId  the ID of the user to revive
     * @param role    the role to assign to the player
     */
    fun reviveRole(session: Session, userId: Long, role: String)

    /**
     * Assigns the police (sheriff) status to a player.
     *
     * @param session the session containing the player
     * @param userId  the ID of the user to make police
     */
    fun setPolice(session: Session, userId: Long)

    /**
     * Broadcasts a progress update for a long-running action.
     *
     * @param guildId the ID of the guild
     * @param message the progress message
     * @param percent the progress percentage (0-100)
     */
    fun broadcastProgress(guildId: Long, message: String?, percent: Int?)

    /**
     * Mutes or unmutes all players in the game (except maybe the judge).
     *
     * @param guildId the ID of the guild
     * @param mute    true to mute, false to unmute
     */
    fun muteAll(guildId: Long, mute: Boolean)
}
