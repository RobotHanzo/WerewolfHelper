package dev.robothanzo.werewolf.domain

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document

/**
 * One Discord guild = one game session. The document id **is** the guild id, so the mapping is
 * one-to-one and lookups are O(1).
 *
 * Scheduled deadlines (speech/vote/timer/night) are stored as absolute epoch-millis so countdowns
 * survive a backend restart and so the dashboard ticks client-side from a server `endsAt`.
 */
@Document(collection = "sessions")
data class GameSession(
    @Id
    val guildId: Long,

    var settings: GameSettings = GameSettings(),

    /** Identity pool as a multiset: roleId → count. Dealt out at assignment. */
    var pool: MutableMap<String, Int> = mutableMapOf(),

    var seats: MutableList<Seat> = mutableListOf(),

    var phase: Phase = Phase.LOBBY,
    /** Detailed step id within `game/flow` (null in lobby). */
    var stepId: String? = null,
    var day: Int = 0,
    var paused: Boolean = false,

    var assigned: Boolean = false,

    /** Seat number of the current police, or null. */
    var policeSeat: Int? = null,

    // --- scheduled deadlines (epoch millis, null when inactive) ---
    var timerEndsAt: Long? = null,
    var stepEndsAt: Long? = null,

    /** Owner (server-creator) member id, re-granted judge on join. */
    var ownerId: Long? = null,

    /** Discord role/channel ids created at provisioning. */
    var discordIds: DiscordIds = DiscordIds(),
) {
    val playerCount: Int get() = settings.playerCount

    fun seat(number: Int): Seat? = seats.firstOrNull { it.number == number }

    fun aliveSeats(): List<Seat> = seats.filter { it.assigned && it.alive }
}

/** Pre-game configuration (FEATURES §4). */
data class GameSettings(
    var playerCount: Int = 12,
    var doubleIdentity: Boolean = false,
    var muteAfterSpeech: Boolean = true,
) {
    /** Required pool size: N for single, 2N for double identity. */
    val requiredPoolSize: Int get() = if (doubleIdentity) playerCount * 2 else playerCount
}
