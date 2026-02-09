package dev.robothanzo.werewolf.service

import dev.robothanzo.werewolf.controller.dto.GuildMemberDto
import dev.robothanzo.werewolf.database.documents.Session
import dev.robothanzo.werewolf.database.documents.UserRole
import java.util.*

/**
 * Service for managing game sessions, including creation, retrieval,
 * persistence, and broadcasting updates to clients.
 */
interface GameSessionService {
    /**
     * Retrieves all active game sessions.
     *
     * @return a list of all sessions
     */
    fun getAllSessions(): List<Session>

    /**
     * Retrieves a game session for a specific guild.
     *
     * @param guildId the ID of the guild
     * @return an Optional containing the session if found, or empty otherwise
     */
    fun getSession(guildId: Long): Optional<Session> // Keeping generic Optional for now for easier porting

    /**
     * Creates a new game session for a guild.
     *
     * @param guildId the ID of the guild
     * @return the newly created session
     */
    fun createSession(guildId: Long): Session

    /**
     * Saves or updates a game session in persistence.
     *
     * @param session the session to save
     * @return the saved session
     */
    fun saveSession(session: Session): Session

    /**
     * Executes a block of code with a locked session, ensuring thread-safe updates.
     * Fetches the latest session, executes the block, and saves it.
     */
    fun <T> withLockedSession(guildId: Long, block: (Session) -> T): T

    /**
     * Deletes a game session for a guild.
     *
     * @param guildId the ID of the guild
     */
    fun deleteSession(guildId: Long)

    /**
     * Serializes a game session to a JSON-compatible Map.
     *
     * @param session the session to serialize
     * @return the serialized session map
     */
    fun sessionToJSON(session: Session): Map<String, Any>

    /**
     * Serializes a game session to a summary JSON-compatible Map.
     *
     * @param session the session to serialize
     * @return the serialized session summary map
     */
    fun sessionToSummaryJSON(session: Session): Map<String, Any>

    /**
     * Serializes the players of a session to a JSON-compatible List of Maps.
     *
     * @param session the session containing the players
     * @return a list of serialized player maps
     */
    fun playersToJSON(session: Session): List<Map<String, Any>>

    /**
     * Retrieves all members of a guild for management purposes.
     *
     * @param session the session
     * @return a list of GuildMemberDto containing user details and roles
     * @throws Exception if an error occurs during retrieval
     */
    @Throws(Exception::class)
    fun getGuildMembers(session: Session): List<GuildMemberDto>

    /**
     * Updates the custom role for a user within a guild.
     *
     * @param session the session
     * @param userId  the ID of the user
     * @param role    the new role to assign
     * @throws Exception if an error occurs during the update
     */
    @Throws(Exception::class)
    fun updateUserRole(session: Session, userId: Long, role: UserRole)

    /**
     * Broadcasts a session update to all connected WebSocket clients for a guild.
     *
     * @param guildId the ID of the guild
     */
    fun broadcastUpdate(guildId: Long)

    /**
     * Broadcasts a specific session update to all connected WebSocket clients.
     *
     * @param session the session that was updated
     */
    fun broadcastSessionUpdate(session: Session)

    /**
     * Broadcasts a general event to all connected WebSocket clients.
     *
     * @param type the type/name of the event
     * @param data the data associated with the event
     */
    fun broadcastEvent(type: String, data: Map<String, Any>)

}
