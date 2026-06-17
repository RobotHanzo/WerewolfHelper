package dev.robothanzo.werewolf.service

import dev.robothanzo.werewolf.domain.GameLogEntry
import dev.robothanzo.werewolf.domain.GameSession
import dev.robothanzo.werewolf.domain.LogSeverity
import dev.robothanzo.werewolf.domain.repo.GameLogRepository
import dev.robothanzo.werewolf.domain.repo.GameSessionRepository
import dev.robothanzo.werewolf.i18n.Msg
import dev.robothanzo.werewolf.websocket.GameWebSocketHandler
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.UUID

/** Raised when a guild has no session; rendered as a zh-TW error by the controller advice. */
class SessionNotFoundException(val guildId: Long) : RuntimeException("session not found: $guildId")

/**
 * Central access to a guild's session: load, persist a typed log entry (key + params + rendered
 * text), and — crucially — **broadcast a full snapshot after every mutation** (FEATURES §10.5).
 */
@Service
class GameSessionService(
    private val sessions: GameSessionRepository,
    private val logs: GameLogRepository,
    private val snapshots: SnapshotService,
    private val ws: GameWebSocketHandler,
    private val msg: Msg,
) {

    fun find(guildId: Long): GameSession? = sessions.findById(guildId).orElse(null)

    fun require(guildId: Long): GameSession =
        sessions.findById(guildId).orElseThrow { SessionNotFoundException(guildId) }

    fun save(session: GameSession): GameSession = sessions.save(session)

    fun delete(guildId: Long) = sessions.deleteById(guildId)

    fun recentLogs(guildId: Long): List<GameLogEntry> = logs.findByGuildIdOrderByTimestampDesc(guildId)

    /** Append a typed, localized log entry. */
    fun log(guildId: Long, severity: LogSeverity, key: String, vararg params: Any?): GameLogEntry =
        logEvent(guildId, severity, key, emptyMap(), *params)

    /**
     * Like [log], but also carries structured [metadata] used to reconstruct the replay timeline
     * (e.g. vote breakdowns, skill actors). The dashboard ignores it; the recording finalizer reads it.
     */
    fun logEvent(
        guildId: Long,
        severity: LogSeverity,
        key: String,
        metadata: Map<String, String>,
        vararg params: Any?,
    ): GameLogEntry {
        val entry = GameLogEntry(
            id = UUID.randomUUID().toString(),
            guildId = guildId,
            timestamp = Instant.now(),
            severity = severity,
            messageKey = key,
            params = params.map { it.toString() },
            rendered = msg.msg(key, *params),
            metadata = metadata,
        )
        return logs.save(entry)
    }

    fun clearLogs(guildId: Long) = logs.deleteByGuildId(guildId)

    /** Build and broadcast the full snapshot to the guild's WS clients. */
    fun broadcast(session: GameSession) {
        ws.broadcastSnapshot(session.guildId, snapshots.build(session, recentLogs(session.guildId)))
    }

    /** Nudge the guild's clients to re-fetch their dashboard authorization (role change / lockout). */
    fun broadcastAuthRefresh(guildId: Long) = ws.broadcastAuthRefresh(guildId)

    /**
     * Load the session, run [block], persist, and broadcast a fresh snapshot — the standard
     * mutate-then-sync path every controller action funnels through.
     */
    fun <T> mutate(guildId: Long, block: (GameSession) -> T): T {
        val session = require(guildId)
        val result = block(session)
        save(session)
        broadcast(session)
        return result
    }
}
