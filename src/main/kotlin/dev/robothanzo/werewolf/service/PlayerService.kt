package dev.robothanzo.werewolf.service

import dev.robothanzo.werewolf.database.documents.Player
import dev.robothanzo.werewolf.database.documents.Session

/**
 * Service for managing individual players within a game session,
 * including their roles, statuses, and ordering.
 */
interface PlayerService {
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
        session: Session,
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
    fun updatePlayerRoles(player: Player, roles: List<String>)

    /**
     * Toggles the order of roles for a player (between first and second role).
     *
     * @param guildId  the ID of the guild
     * @param playerId the ID of the player
     */
    fun switchRoleOrder(player: Player)

    /**
     * Locks or unlocks the role position for a specific player.
     *
     * @param guildId  the ID of the guild
     * @param playerId the ID of the player
     * @param locked   true to lock the roles, false to unlock
     */
    fun setRolePositionLock(player: Player, locked: Boolean)
}
