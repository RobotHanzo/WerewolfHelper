package dev.robothanzo.werewolf.game.model

import dev.robothanzo.werewolf.game.roles.actions.RoleAction

enum class Camp {
    WEREWOLF,
    GOD,
    VILLAGER
}

data class RoleDefinition(
    val name: String,
    val camp: Camp,
    val eventListeners: List<RoleEventType> = emptyList(),
    val actions: List<RoleAction> = emptyList()
)
