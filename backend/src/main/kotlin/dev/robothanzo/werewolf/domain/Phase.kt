package dev.robothanzo.werewolf.domain

/**
 * Top-level game phase. The detailed step machine lives in `game/flow`; this is the coarse
 * phase persisted on the session and surfaced to the dashboard status header.
 */
enum class Phase {
    LOBBY,
    ASSIGNMENT,
    NIGHT,
    DAWN,
    DAY,
    POLICE_ELECTION,
    SPEECHES,
    EXPEL_VOTE,
    OVER,
}
