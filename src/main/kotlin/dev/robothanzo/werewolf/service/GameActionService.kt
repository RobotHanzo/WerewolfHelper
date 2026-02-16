package dev.robothanzo.werewolf.service

import dev.robothanzo.werewolf.database.documents.Session

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
     * Revives a player in the game session.
     *
     * @param session the session containing the player
     * @param playerId the ID of the player to revive
     */
    fun revivePlayer(session: Session, playerId: Int)

    /**
     * Revives a player and assigns them a specific role.
     *
     * @param session the session containing the player
     * @param playerId the ID of the player to revive
     * @param role    the role to assign to the player
     */
    fun reviveRole(session: Session, playerId: Int, role: String)

    /**
     * Assigns the police (sheriff) status to a player.
     *
     * @param session the session containing the player
     * @param playerId the ID of the player to make police
     */
    fun setPolice(session: Session, playerId: Int)

    /**
     * Broadcasts a progress update for a long-running action.
     *
     * @param guildId the ID of the guild
     * @param message the progress message
     * @param percent the progress percentage (0-100)
     */
    fun broadcastProgress(guildId: Long, message: String?, percent: Int?)
}
