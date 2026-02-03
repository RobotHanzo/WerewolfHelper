package dev.robothanzo.werewolf.service

/**
 * Service for performing game-related actions such as resetting the game,
 * marking players as dead, reviving players, and updating game state.
 */
interface GameActionService {
    /**
     * Resets the game session for a specific guild.
     *
     * @param guildId          the ID of the guild
     * @param statusCallback   callback for status messages
     * @param progressCallback callback for progress updates (0-100)
     * @throws Exception if an error occurs during the reset process
     */
    @Throws(Exception::class)
    fun resetGame(guildId: Long, statusCallback: (String) -> Unit, progressCallback: (Int) -> Unit)

    /**
     * Marks a player as dead in the game session.
     *
     * @param guildId        the ID of the guild
     * @param userId         the ID of the user to mark as dead
     * @param allowLastWords whether the player is allowed to give last words
     */
    fun markPlayerDead(guildId: Long, userId: Long, allowLastWords: Boolean)

    /**
     * Revives a player in the game session.
     *
     * @param guildId the ID of the guild
     * @param userId  the ID of the user to revive
     */
    fun revivePlayer(guildId: Long, userId: Long)

    /**
     * Revives a player and assigns them a specific role.
     *
     * @param guildId the ID of the guild
     * @param userId  the ID of the user to revive
     * @param role    the role to assign to the player
     */
    fun reviveRole(guildId: Long, userId: Long, role: String)

    /**
     * Assigns the police (sheriff) status to a player.
     *
     * @param guildId the ID of the guild
     * @param userId  the ID of the user to make police
     */
    fun setPolice(guildId: Long, userId: Long)

    /**
     * Broadcasts a progress update for a long-running action.
     *
     * @param guildId the ID of the guild
     * @param message the progress message
     * @param percent the progress percentage (0-100)
     */
    fun broadcastProgress(guildId: Long, message: String?, percent: Int?)
}
