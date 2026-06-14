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
    /**
     * Wall-clock (epoch millis) at which the game was paused, or null while running. Every active
     * countdown is frozen here: the scheduler jobs are cancelled and the persisted `endsAt` deadlines
     * are left untouched, so the dashboard counts down to this instant (a standstill); on resume the
     * deadlines are shifted forward by the paused duration and the jobs are re-armed.
     */
    var pausedAt: Long? = null,

    var assigned: Boolean = false,

    /** Seat number of the current police, or null. */
    var policeSeat: Int? = null,

    // --- scheduled deadlines (epoch millis, null when inactive) ---
    var timerEndsAt: Long? = null,
    var stepEndsAt: Long? = null,
    /**
     * When the double-identity order swap locks (epoch millis), or null when not running. Set when
     * assignment completes; the dashboard counts down to it and seats are reminded at the
     * [dev.robothanzo.werewolf.game.GameConstants.ORDER_LOCK_REMINDER_AT_SECONDS] marks. Cleared on lock/reset.
     */
    var orderLockEndsAt: Long? = null,

    /** Owner (server-creator) member id, re-granted judge on join. */
    var ownerId: Long? = null,

    /** Discord role/channel ids created at provisioning. */
    var discordIds: DiscordIds = DiscordIds(),

    /** Current night round (active during the NIGHT phase). */
    var nightState: NightState = NightState(),

    /**
     * Wolf-team chatter relayed from the seat channels, surfaced on the judge dashboard. Lives at the
     * session level (not on [nightState]) so it keeps syncing across every phase, not just the night,
     * and survives the per-night [NightState] reset. Trimmed to a rolling cap; cleared on game reset.
     */
    var wolfChat: MutableList<WolfChatData> = mutableListOf(),

    /** Active day speech flow (SPEECHES / police campaign / last words), or null when idle. */
    var speech: SpeechFlow? = null,

    /** Active day poll (police election or expel vote), or null when idle. */
    var poll: Poll? = null,

    /** Seat number of the most recently expelled (voted-out) player — the 守墓人 reads its faction. */
    var lastExpelledSeat: Int? = null,

    /** 血月使徒 自爆 seals the next night: all 神職 abilities void + the wolves cannot knife. */
    var bloodMoonSeal: Boolean = false,

    /**
     * Whether the judge has confirmed the win banner. Until then the result stays private to the
     * judge + spectator channels; confirming reveals it to the court, unmutes everyone, and opens
     * every channel for viewing. Reset with the game.
     */
    var winRevealed: Boolean = false,
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
