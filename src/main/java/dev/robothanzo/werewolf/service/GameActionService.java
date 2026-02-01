package dev.robothanzo.werewolf.service;

import java.util.function.Consumer;

/**
 * Service for performing game-related actions such as resetting the game,
 * marking players as dead, reviving players, and updating game state.
 */
public interface GameActionService {
    /**
     * Resets the game session for a specific guild.
     *
     * @param guildId          the ID of the guild
     * @param statusCallback   callback for status messages
     * @param progressCallback callback for progress updates (0-100)
     * @throws Exception if an error occurs during the reset process
     */
    void resetGame(long guildId, Consumer<String> statusCallback, Consumer<Integer> progressCallback) throws Exception;

    /**
     * Marks a player as dead in the game session.
     *
     * @param guildId        the ID of the guild
     * @param userId         the ID of the user to mark as dead
     * @param allowLastWords whether the player is allowed to give last words
     */
    void markPlayerDead(long guildId, long userId, boolean allowLastWords);

    /**
     * Revives a player in the game session.
     *
     * @param guildId the ID of the guild
     * @param userId  the ID of the user to revive
     */
    void revivePlayer(long guildId, long userId);

    /**
     * Revives a player and assigns them a specific role.
     *
     * @param guildId the ID of the guild
     * @param userId  the ID of the user to revive
     * @param role    the role to assign to the player
     */
    void reviveRole(long guildId, long userId, String role);

    /**
     * Assigns the police (sheriff) status to a player.
     *
     * @param guildId the ID of the guild
     * @param userId  the ID of the user to make police
     */
    void setPolice(long guildId, long userId);

    /**
     * Broadcasts a progress update for a long-running action.
     *
     * @param guildId the ID of the guild
     * @param message the progress message
     * @param percent the progress percentage (0-100)
     */
    void broadcastProgress(long guildId, String message, Integer percent);
}
