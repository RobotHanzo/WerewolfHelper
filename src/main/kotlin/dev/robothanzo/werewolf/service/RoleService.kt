package dev.robothanzo.werewolf.service

import dev.robothanzo.werewolf.database.documents.Session

/**
 * Service for managing the roles available in a game session
 * and handling the assignment of these roles to players.
 */
interface RoleService {
    /**
     * Adds a specific amount of a role to the role pool.
     *
     * @param session  the session to add roles to
     * @param roleName the name of the role to add
     * @param amount   the amount of the role to add
     */
    fun addRole(session: Session, roleName: String, amount: Int)

    /**
     * Removes a specific amount of a role from the role pool.
     *
     * @param session  the session to remove roles from
     * @param roleName the name of the role to remove
     * @param amount   the amount of the role to remove
     */
    fun removeRole(session: Session, roleName: String, amount: Int)

    /**
     * Randomly assigns roles from the pool to the players in a game session.
     *
     * @param session          the session
     * @param statusCallback   callback for status messages during assignment
     * @param progressCallback callback for progress percentage (0-100)
     * @throws Exception if an error occurs during assignment
     */
    @Throws(Exception::class)
    fun assignRoles(
        session: Session,
        statusCallback: (String) -> Unit,
        progressCallback: (Int) -> Unit
    )
}
