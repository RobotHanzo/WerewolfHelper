package dev.robothanzo.werewolf.service;

import dev.robothanzo.werewolf.database.documents.Session;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Service for managing game sessions, including creation, retrieval,
 * persistence, and broadcasting updates to clients.
 */
public interface GameSessionService {
    /**
     * Retrieves all active game sessions.
     *
     * @return a list of all sessions
     */
    List<Session> getAllSessions();

    /**
     * Retrieves a game session for a specific guild.
     *
     * @param guildId the ID of the guild
     * @return an Optional containing the session if found, or empty otherwise
     */
    Optional<Session> getSession(long guildId);

    /**
     * Creates a new game session for a guild.
     *
     * @param guildId the ID of the guild
     * @return the newly created session
     */
    Session createSession(long guildId);

    /**
     * Saves or updates a game session in persistence.
     *
     * @param session the session to save
     * @return the saved session
     */
    Session saveSession(Session session);

    /**
     * Deletes a game session for a guild.
     *
     * @param guildId the ID of the guild
     */
    void deleteSession(long guildId);

    /**
     * Serializes a game session to a JSON-compatible Map.
     *
     * @param session the session to serialize
     * @return the serialized session map
     */
    Map<String, Object> sessionToJSON(Session session);

    /**
     * Serializes a game session to a summary JSON-compatible Map.
     *
     * @param session the session to serialize
     * @return the serialized session summary map
     */
    Map<String, Object> sessionToSummaryJSON(Session session);

    /**
     * Serializes the players of a session to a JSON-compatible List of Maps.
     *
     * @param session the session containing the players
     * @return a list of serialized player maps
     */
    List<Map<String, Object>> playersToJSON(Session session);

    /**
     * Retrieves all members of a guild for management purposes.
     *
     * @param guildId the ID of the guild
     * @return a list of member maps containing user details and roles
     * @throws Exception if an error occurs during retrieval
     */
    List<Map<String, Object>> getGuildMembers(long guildId) throws Exception;

    /**
     * Updates the custom role for a user within a guild.
     *
     * @param guildId the ID of the guild
     * @param userId  the ID of the user
     * @param role    the new role to assign
     * @throws Exception if an error occurs during the update
     */
    void updateUserRole(long guildId, long userId, dev.robothanzo.werewolf.database.documents.UserRole role)
            throws Exception;

    /**
     * Updates the settings for a game session.
     *
     * @param guildId  the ID of the guild
     * @param settings a map of setting keys and values to update
     * @throws Exception if an error occurs during the update
     */
    void updateSettings(long guildId, Map<String, Object> settings) throws Exception;

    /**
     * Broadcasts a session update to all connected WebSocket clients for a guild.
     *
     * @param guildId the ID of the guild
     */
    void broadcastUpdate(long guildId);

    /**
     * Broadcasts a specific session update to all connected WebSocket clients.
     *
     * @param session the session that was updated
     */
    void broadcastSessionUpdate(Session session);

    /**
     * Broadcasts a general event to all connected WebSocket clients.
     *
     * @param type the type/name of the event
     * @param data the data associated with the event
     */
    void broadcastEvent(String type, Map<String, Object> data);
}
