package dev.robothanzo.werewolf.domain

/**
 * Persisted night-round state — single source of truth for both the Discord prompts and the
 * dashboard night board. Survives a restart (deadlines are absolute epoch-millis). Reset to inactive
 * once resolved.
 */
data class NightState(
    var active: Boolean = false,
    var day: Int = 0,
    /** Deadline of the **currently-active phase** (the wave at [currentPhase]); the dashboard counts down to it. */
    var endsAt: Long = 0,
    var resolved: Boolean = false,
    var summary: String? = null,
    /** Seat numbers that died this night (drives the dawn announcement + last-words flow). */
    var deaths: MutableList<Int> = mutableListOf(),
    /** Index into [waves] of the phase currently being prompted; phases run sequentially. */
    var currentPhase: Int = 0,
    /** Ability ids grouped by wave (the simultaneous plan from the [dev.robothanzo.werewolf.game.night.NightPlanner]). */
    var waves: MutableList<MutableList<String>> = mutableListOf(),
    /** Alive seats taking part in the collective wolf kill. */
    var wolfParticipants: MutableSet<Int> = mutableSetOf(),
    /** voter seat → target seat (-1 = chose not to kill). */
    var wolfVotes: MutableMap<Int, Int> = mutableMapOf(),
    /** Submitted per-ability intents (excluding the collective wolf kill, which lives in wolfVotes). */
    var intents: MutableList<NightIntentData> = mutableListOf(),
)

/** A single relayed wolf-chat line, in send order, for the dashboard wolf-chat panel. */
data class WolfChatData(
    var seat: Int = 0,
    /** Discord user id of the sender — the dashboard groups consecutive lines by this, not by seat. */
    var userId: Long = 0,
    var author: String = "",
    var avatar: String? = null,
    var content: String = "",
    var at: Long = 0,
)

/** A Mongo-serializable night intent (mirrors the engine's NightIntent). */
data class NightIntentData(
    var abilityId: String = "",
    var roleId: String = "",
    var actorSeats: MutableList<Int> = mutableListOf(),
    var targets: MutableList<Int> = mutableListOf(),
    var skipped: Boolean = false,
    var meta: MutableMap<String, Int> = mutableMapOf(),
)
