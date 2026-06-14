package dev.robothanzo.werewolf.domain

import dev.robothanzo.werewolf.game.speech.SpeechFlow
import dev.robothanzo.werewolf.game.vote.Poll
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

    /** Current night round (active during the NIGHT phase). */
    var nightState: NightState = NightState(),

    /** Active day speech flow (SPEECHES / police campaign / last words), or null when idle. */
    var speech: SpeechFlow? = null,

    /** Active day poll (police election or expel vote), or null when idle. */
    var poll: Poll? = null,

    /** Seat number of the most recently expelled (voted-out) player — the 守墓人 reads its faction. */
    var lastExpelledSeat: Int? = null,

    /** 血月使徒 自爆 seals the next night: all 神職 abilities void + the wolves cannot knife. */
    var bloodMoonSeal: Boolean = false,
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
    /** 女巫 房規: whether the witch may use her antidote on herself (ROLES.md 女巫). */
    var witchSelfSave: Boolean = false,
    /** 隱狼 房規: whether 隱狼 inherits the wolf knife once the chat wolves are dead (ROLES.md 隱狼). */
    var hiddenWolfInheritsKnife: Boolean = true,
) {
    /** Required pool size: N for single, 2N for double identity. */
    val requiredPoolSize: Int get() = if (doubleIdentity) playerCount * 2 else playerCount
}
