package dev.robothanzo.werewolf.game

/** Tunable game constants — the FEATURES §12 cheat-sheet in one place. Durations are seconds. */
object GameConstants {
    // Police election stages
    const val POLICE_ENROLL_SECONDS = 30
    const val POLICE_WITHDRAW_SECONDS = 20
    const val POLICE_VOTE_SECONDS = 30

    // Expel poll
    const val EXPEL_VOTE_SECONDS = 30

    /** "10 s remaining" audio fires this many seconds before a 30 s stage ends (the 20 s mark). */
    const val TEN_SECONDS_WARNING_AT = 20

    /** Identity-order swap locks this long after assignment. */
    const val ORDER_LOCK_SECONDS = 120

    /** Seconds-remaining marks at which players are reminded their identity order is about to lock. */
    val ORDER_LOCK_REMINDER_AT_SECONDS = listOf(30, 10)

    /** Default speech time per speaker. */
    const val SPEECH_SECONDS = 120

    /** Default per-phase night-action window (each topological wave runs as its own timed phase). */
    const val NIGHT_PHASE_SECONDS = 60

    /** The wolf phase (the wave that carries the collective knife) gets longer to discuss + vote. */
    const val NIGHT_WOLF_PHASE_SECONDS = 90

    /** Seconds-remaining marks at which a still-pending seat is reminded in its channel. */
    val NIGHT_REMINDER_AT_SECONDS = listOf(30, 10)

    /** Police vote weight in polls, and the parity win-check bonus. */
    const val POLICE_VOTE_WEIGHT = 1.5
    const val POLICE_PARITY_BONUS = 0.5

    /** Discord autocomplete hard limit. */
    const val AUTOCOMPLETE_CAP = 25
}
