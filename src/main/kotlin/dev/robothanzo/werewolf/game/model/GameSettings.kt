package dev.robothanzo.werewolf.game.model

/**
 * Structured game settings to replace generic settings map.
 */
data class GameSettings(
    var witchCanSaveSelf: Boolean = true,
    var allowWolfSelfKill: Boolean = false,
    var hiddenRoleOnDeath: Boolean = false,
    var roleFlags: MutableMap<String, Any> = mutableMapOf() // For custom roles etc.
)
