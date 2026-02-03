package dev.robothanzo.werewolf.service

/**
 * Service for managing individual players within a game session,
 * including their roles, statuses, and ordering.
 */
interface PlayerService {
    /**
     * Retrieves all players of a guild serialized to a JSON-compatible List of
     * Maps.
     *
     * @param guildId the ID of the guild
     * @return a list of serialized player maps
     */
    fun getPlayersJSON(guildId: Long): List<Map<String, Any>>

    /**
     * Initializes the player count for a game session.
     *
     * @param guildId    the ID of the guild
     * @param count      the number of players to create
     * @param onProgress callback for progress messages
     * @param onPercent  callback for progress percentage (0-100)
     * @throws Exception if an error occurs during initialization
     */
    @Throws(Exception::class)
    fun setPlayerCount(
        guildId: Long,
        count: Int,
        onProgress: (String) -> Unit,
        onPercent: (Int) -> Unit
    )

    /**
     * Updates the roles assigned to a specific player.
     *
     * @param guildId  the ID of the guild
     * @param playerId the ID of the player (e.g., "1", "2")
     * @param roles    the list of roles to assign
     */
    fun updatePlayerRoles(guildId: Long, playerId: String, roles: List<String>)

    /**
     * Toggles the order of roles for a player (between first and second role).
     *
     * @param guildId  the ID of the guild
     * @param playerId the ID of the player
     */
    fun switchRoleOrder(guildId: Long, playerId: String)

    /**
     * Locks or unlocks the role position for a specific player.
     *
     * @param guildId  the ID of the guild
     * @param playerId the ID of the player
     * @param locked   true to lock the roles, false to unlock
     */
    fun setRolePositionLock(guildId: Long, playerId: String, locked: Boolean)
}
