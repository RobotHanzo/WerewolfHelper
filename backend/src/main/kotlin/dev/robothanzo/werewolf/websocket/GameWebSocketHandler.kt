package dev.robothanzo.werewolf.websocket

import tools.jackson.databind.ObjectMapper
import dev.robothanzo.werewolf.controller.dto.GameSnapshot
import dev.robothanzo.werewolf.security.DashboardRoleService
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
class GameWebSocketHandler(
    private val mapper: ObjectMapper,
    private val roleService: DashboardRoleService,
) : TextWebSocketHandler() {

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

    override fun handleTextMessage(session: WebSocketSession, message: TextMessage) {
        try {
            val node = mapper.readTree(message.payload)
            if (node.get("type")?.asText() == "ping") {
                val lock = locks[session.id] ?: return
                synchronized(lock) {
                    if (session.isOpen) {
                        session.sendMessage(TextMessage(mapper.writeValueAsString(mapOf("type" to "pong"))))
                    }
                }
            }
        } catch (e: Exception) {
            log.debug("WS handle text failed: {}", e.message)
        }
    }

    /** Broadcast a full snapshot to every client of [guildId]. */
    fun broadcastSnapshot(guildId: Long, snapshot: GameSnapshot) {
        send(guildId, mapOf("type" to "snapshot", "snapshot" to snapshot))
    }


    /** Stream a long-operation progress event (percent + log line) to the guild's clients. */
    fun broadcastProgress(guildId: Long, percent: Int, line: String, severity: String) {
        send(guildId, mapOf("type" to "progress", "percent" to percent, "line" to line, "severity" to severity))
    }

    /**
     * Nudge the guild's clients to re-fetch their own dashboard authorization (`/api/auth/me`). Used
     * when a judge promotes/demotes a member or assignment locks out seated players, so the dashboard
     * role + permissions update in real time without a manual reload. This carries **no** per-user
     * state (each client resolves its own role) — it is a signal, not a snapshot patch.
     *
     * It then **force-closes** any client that can no longer view this guild (a seated/locked-out
     * player, a blocked user) with [CLOSE_AUTH_CHANGED], so they cannot keep receiving the God's-view
     * snapshot even if their browser ignores the nudge — the anti-cheat guarantee is server-driven,
     * not dependent on the client noticing. Their reconnect is rejected by the handshake (`canView`).
     */
    fun broadcastAuthRefresh(guildId: Long) {
        send(guildId, mapOf("type" to "authRefresh"))
        disconnectUnauthorized(guildId)
    }

    /** Drop every open client of [guildId] that no longer passes `canView` (locked-out / blocked). */
    private fun disconnectUnauthorized(guildId: Long) {
        val clients = registry[guildId] ?: return
        for (client in clients) {
            if (!client.isOpen) continue
            val userId = client.attributes[ATTR_USER_ID] as? Long ?: continue
            if (roleService.canView(guildId, userId)) continue
            try {
                client.close(CloseStatus(CLOSE_AUTH_CHANGED, "auth changed"))
            } catch (e: Exception) {
                log.debug("WS auth-close failed for {}: {}", client.id, e.message)
            }
        }
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

        /** Close code: this client's dashboard authorization was revoked (locked out / blocked) —
         *  the client must re-resolve its role and route away, **not** reconnect. */
        const val CLOSE_AUTH_CHANGED = 4002
    }
}
