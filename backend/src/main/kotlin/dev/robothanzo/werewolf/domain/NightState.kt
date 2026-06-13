package dev.robothanzo.werewolf.domain

/**
 * Persisted night-round state — single source of truth for both the Discord prompts and the
 * dashboard night board. Survives a restart (deadlines are absolute epoch-millis). Reset to inactive
 * once resolved.
 */
data class NightState(
    var active: Boolean = false,
    var day: Int = 0,
    var endsAt: Long = 0,
    var resolved: Boolean = false,
    var summary: String? = null,
    /** Ability ids grouped by wave (the simultaneous plan from the [dev.robothanzo.werewolf.game.night.NightPlanner]). */
    var waves: MutableList<MutableList<String>> = mutableListOf(),
    /** Alive seats taking part in the collective wolf kill. */
    var wolfParticipants: MutableSet<Int> = mutableSetOf(),
    /** voter seat → target seat (-1 = chose not to kill). */
    var wolfVotes: MutableMap<Int, Int> = mutableMapOf(),
    /** Submitted per-ability intents (excluding the collective wolf kill, which lives in wolfVotes). */
    var intents: MutableList<NightIntentData> = mutableListOf(),
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
