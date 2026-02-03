package dev.robothanzo.werewolf.service

/**
 * Service for managing the roles available in a game session
 * and handling the assignment of these roles to players.
 */
interface RoleService {
    /**
     * Adds a specific amount of a role to the role pool for a guild.
     *
     * @param guildId  the ID of the guild
     * @param roleName the name of the role to add
     * @param amount   the amount of the role to add
     */
    fun addRole(guildId: Long, roleName: String, amount: Int)

    /**
     * Removes a specific amount of a role from the role pool for a guild.
     *
     * @param guildId  the ID of the guild
     * @param roleName the name of the role to remove
     * @param amount   the amount of the role to remove
     */
    fun removeRole(guildId: Long, roleName: String, amount: Int)

    /**
     * Retrieves the current role pool for a guild.
     *
     * @param guildId the ID of the guild
     * @return a list of role names in the pool
     */
    fun getRoles(guildId: Long): List<String>

    /**
     * Randomly assigns roles from the pool to the players in a game session.
     *
     * @param guildId          the ID of the guild
     * @param statusCallback   callback for status messages during assignment
     * @param progressCallback callback for progress percentage (0-100)
     * @throws Exception if an error occurs during assignment
     */
    @Throws(Exception::class)
    fun assignRoles(
        guildId: Long,
        statusCallback: (String) -> Unit,
        progressCallback: (Int) -> Unit
    )
}
