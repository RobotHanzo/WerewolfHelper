package dev.robothanzo.werewolf.game.model

enum class ActionTiming {
    NIGHT,
    DAY,
    ANYTIME,
    DEATH_TRIGGER  // Actions triggered when a role holder dies (e.g., Hunter, Wolf King)
}
