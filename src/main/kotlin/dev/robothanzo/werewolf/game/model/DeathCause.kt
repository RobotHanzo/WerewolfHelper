package dev.robothanzo.werewolf.game.model

/**
 * Enum representing different causes of death in the game.
 * Each cause has a log message for internal tracking (not shown in public Discord channels).
 */
enum class DeathCause(val logMessage: String) {
    WEREWOLF("被狼人殺死"),
    POISON("被女巫毒死"),
    HUNTER_REVENGE("被獵人帶走"),
    WOLF_KING_REVENGE("被狼王帶走"),
    DOUBLE_PROTECTION("同時受到女巫解藥與守衛守護而死亡"),
    EXPEL("被放逐"),
    UNKNOWN("死亡原因未知")
}
