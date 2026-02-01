package dev.robothanzo.werewolf.service;

import java.util.List;
import java.util.Map;

/**
 * Service for managing individual players within a game session,
 * including their roles, statuses, and ordering.
 */
public interface PlayerService {
    /**
     * Retrieves all players of a guild serialized to a JSON-compatible List of
     * Maps.
     *
     * @param guildId the ID of the guild
     * @return a list of serialized player maps
     */
    List<Map<String, Object>> getPlayersJSON(long guildId);

    /**
     * Initializes the player count for a game session.
     *
     * @param guildId    the ID of the guild
     * @param count      the number of players to create
     * @param onProgress callback for progress messages
     * @param onPercent  callback for progress percentage (0-100)
     * @throws Exception if an error occurs during initialization
     */
    void setPlayerCount(long guildId, int count, java.util.function.Consumer<String> onProgress,
                        java.util.function.Consumer<Integer> onPercent) throws Exception;

    /**
     * Updates the roles assigned to a specific player.
     *
     * @param guildId  the ID of the guild
     * @param playerId the ID of the player (e.g., "1", "2")
     * @param roles    the list of roles to assign
     */
    void updatePlayerRoles(long guildId, String playerId, List<String> roles);

    /**
     * Toggles the order of roles for a player (between first and second role).
     *
     * @param guildId  the ID of the guild
     * @param playerId the ID of the player
     */
    void switchRoleOrder(long guildId, String playerId);

    /**
     * Locks or unlocks the role position for a specific player.
     *
     * @param guildId  the ID of the guild
     * @param playerId the ID of the player
     * @param locked   true to lock the roles, false to unlock
     */
    void setRolePositionLock(long guildId, String playerId, boolean locked);
}
