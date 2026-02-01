package dev.robothanzo.werewolf.service;

import java.util.List;
import java.util.function.Consumer;

/**
 * Service for managing the roles available in a game session
 * and handling the assignment of these roles to players.
 */
public interface RoleService {
    /**
     * Adds a specific amount of a role to the role pool for a guild.
     *
     * @param guildId  the ID of the guild
     * @param roleName the name of the role to add
     * @param amount   the amount of the role to add
     */
    void addRole(long guildId, String roleName, int amount);

    /**
     * Removes a specific amount of a role from the role pool for a guild.
     *
     * @param guildId  the ID of the guild
     * @param roleName the name of the role to remove
     * @param amount   the amount of the role to remove
     */
    void removeRole(long guildId, String roleName, int amount);

    /**
     * Retrieves the current role pool for a guild.
     *
     * @param guildId the ID of the guild
     * @return a list of role names in the pool
     */
    List<String> getRoles(long guildId);

    /**
     * Randomly assigns roles from the pool to the players in a game session.
     *
     * @param guildId          the ID of the guild
     * @param statusCallback   callback for status messages during assignment
     * @param progressCallback callback for progress percentage (0-100)
     * @throws Exception if an error occurs during assignment
     */
    void assignRoles(long guildId, Consumer<String> statusCallback, Consumer<Integer> progressCallback)
            throws Exception;
}
