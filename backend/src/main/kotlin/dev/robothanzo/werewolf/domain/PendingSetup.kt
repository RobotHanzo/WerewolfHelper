package dev.robothanzo.werewolf.domain

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document

/**
 * A server-creation config stored when a server creator runs the create command, consumed when
 * the bot later joins (or gains admin in) the target guild and builds the server (FEATURES §3).
 * Keyed by the creator so the bot can match the config on guild join.
 */
@Document(collection = "pending_setups")
data class PendingSetup(
    @Id
    val id: String,
    val creatorId: Long,
    val playerCount: Int,
    val doubleIdentity: Boolean,
)
