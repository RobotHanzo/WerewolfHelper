package dev.robothanzo.werewolf.security

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import dev.robothanzo.werewolf.database.documents.AuthSession
import dev.robothanzo.werewolf.websocket.WebSocketEnvelope
import dev.robothanzo.werewolf.websocket.WebSocketEventData
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import org.springframework.web.socket.handler.TextWebSocketHandler
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.stream.Stream

@Component
class GlobalWebSocketHandler : TextWebSocketHandler() {

    // Maintain a list of sessions.
    // In a real app with auth, you might map userId -> Session or guildId ->
    // List<Session>
    // Map guild ID -> Set of Sessions
    private val guildSessions = ConcurrentHashMap<String, MutableSet<WebSocketSession>>()

    @Throws(Exception::class)
    override fun afterConnectionEstablished(session: WebSocketSession) {
        val user = session.attributes["user"] as? AuthSession
        if (user == null) {
            log.warn("Rejected WS connection: No user in session")
            session.close(CloseStatus.POLICY_VIOLATION)
            return
        }

        // Only allow connection if the user is a spectator or judge
        if (!user.isPrivileged) {
            log.warn(
                "Rejected WS connection: User {} has unauthorized role {}",
                user.userId,
                user.role
            )
            session.close(CloseStatus.POLICY_VIOLATION)
            return
        }

        // Check if listening to selected server
        val uri = session.uri
        val query = uri?.query
        var requestedGuildId: String? = null
        if (query != null) {
            requestedGuildId = Stream.of(*query.split("&".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray())
                .filter { s: String -> s.startsWith("guildId=") }
                .map { s: String -> s.substring(8) }
                .findFirst()
                .orElse(null)
        }

        if (requestedGuildId == null || requestedGuildId != user.guildId) {
            log.warn(
                "Rejected WS connection: Guild mismatch. Requested: {}, Authorized: {}",
                requestedGuildId,
                user.guildId
            )
            session.close(CloseStatus.POLICY_VIOLATION)
            return
        }

        guildSessions.computeIfAbsent(requestedGuildId) { ConcurrentHashMap.newKeySet() }
            .add(session)

        log.info(
            "WebSocket connection established for user {} in guild {}: {}",
            user.userId,
            user.guildId,
            session.id
        )
    }

    override fun afterConnectionClosed(session: WebSocketSession, status: CloseStatus) {
        val user = session.attributes["user"] as? AuthSession
        if (user?.guildId != null) {
            guildSessions[user.guildId]?.remove(session)
            // Clean up empty sets to prevent memory leaks
            if (guildSessions[user.guildId]?.isEmpty() == true) {
                guildSessions.remove(user.guildId)
            }
        } else {
            // Fallback cleanup if user attr is missing
            guildSessions.values.forEach { it.remove(session) }
        }
        log.info("WebSocket connection closed: " + session.id)
    }

    override fun handleTextMessage(session: WebSocketSession, message: TextMessage) {
        val payload = message.payload

        try {
            val messageData = mapper.readValue(payload, Map::class.java)
            if (messageData["type"] == "PING") {
                val pongResponse = mapper.writeValueAsString(mapOf("type" to "PONG"))
                session.sendMessage(TextMessage(pongResponse))
            } else {
                log.warn("Received message from client with unknown type: {}", messageData["type"])
            }
        } catch (e: Exception) {
            log.debug("Received non-JSON or invalid message from client: {}", payload)
        }
    }

    fun broadcastToGuild(guildId: String, data: WebSocketEventData) {
        val sessions = guildSessions[guildId] ?: return
        for (session in sessions) {
            if (session.isOpen) {
                synchronized(session) {
                    try {
                        session.sendMessage(TextMessage(WebSocketEnvelope(data).toJson()))
                    } catch (e: IOException) {
                        log.error("Failed to send message to session " + session.id, e)
                    }
                }
            }
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(GlobalWebSocketHandler::class.java)
        val mapper = jacksonObjectMapper()
    }
}
