package dev.robothanzo.werewolf.domain

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document
import java.time.Instant

/** Severity drives the dashboard log colouring (info / action / alert). */
enum class LogSeverity { INFO, ACTION, ALERT }

/**
 * One audit-log event. Persists the message **key + params** *and* the rendered zh-TW text, so
 * the dashboard can show history immediately and a future locale could re-render it. Logs live in
 * their own collection, indexed by guild, and are cleared on reset.
 */
@Document(collection = "game_logs")
data class GameLogEntry(
    @Id
    val id: String,
    @Indexed
    val guildId: Long,
    val timestamp: Instant,
    val severity: LogSeverity,
    val messageKey: String,
    val params: List<String> = emptyList(),
    /** Rendered text at the time of the event (zh-TW today). */
    val rendered: String,
    val metadata: Map<String, String> = emptyMap(),
)
