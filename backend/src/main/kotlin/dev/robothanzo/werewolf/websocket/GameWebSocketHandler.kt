package dev.robothanzo.werewolf.websocket

import tools.jackson.databind.ObjectMapper
import dev.robothanzo.werewolf.controller.dto.GameSnapshot
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import org.springframework.web.socket.handler.TextWebSocketHandler
import java.util.concurrent.ConcurrentHashMap

/**
 * Per-guild WebSocket hub. After every mutation the backend broadcasts a **full snapshot** to that
 * guild's clients (snapshot-as-truth); long operations additionally stream progress events. Sends
 * are serialized per connection (concurrent writes on one socket throw), and empty per-guild
 * registries are cleaned up.
 */
@Component
class GameWebSocketHandler(private val mapper: ObjectMapper) : TextWebSocketHandler() {

    private val log = LoggerFactory.getLogger(javaClass)
    private val registry = ConcurrentHashMap<Long, MutableSet<WebSocketSession>>()
    private val locks = ConcurrentHashMap<String, Any>()

    override fun afterConnectionEstablished(session: WebSocketSession) {
        val guildId = guildIdOf(session) ?: return run { session.close(CloseStatus.BAD_DATA) }
        registry.getOrPut(guildId) { ConcurrentHashMap.newKeySet() }.add(session)
        locks[session.id] = Any()
    }

    override fun afterConnectionClosed(session: WebSocketSession, status: CloseStatus) {
        val guildId = guildIdOf(session) ?: return
        registry[guildId]?.let { set ->
            set.remove(session)
            if (set.isEmpty()) registry.remove(guildId)
        }
        locks.remove(session.id)
    }

    /** Broadcast a full snapshot to every client of [guildId]. */
    fun broadcastSnapshot(guildId: Long, snapshot: GameSnapshot) {
        send(guildId, mapOf("type" to "snapshot", "snapshot" to snapshot))
    }

    /** Stream a long-operation progress event (percent + log line) to the guild's clients. */
    fun broadcastProgress(guildId: Long, percent: Int, line: String, severity: String) {
        send(guildId, mapOf("type" to "progress", "percent" to percent, "line" to line, "severity" to severity))
    }

    private fun send(guildId: Long, payload: Any) {
        val clients = registry[guildId] ?: return
        val json = mapper.writeValueAsString(payload)
        for (client in clients) {
            if (!client.isOpen) continue
            val lock = locks[client.id] ?: continue
            try {
                synchronized(lock) { client.sendMessage(TextMessage(json)) }
            } catch (e: Exception) {
                log.debug("WS send failed for {}: {}", client.id, e.message)
            }
        }
    }

    private fun guildIdOf(session: WebSocketSession): Long? =
        session.attributes[ATTR_GUILD_ID] as? Long

    companion object {
        const val ATTR_GUILD_ID = "wh.guildId"
        const val ATTR_USER_ID = "wh.userId"
    }
}
