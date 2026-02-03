package dev.robothanzo.werewolf.game.model

enum class Camp {
    WEREWOLF,
    GOD,
    VILLAGER
}

data class RoleDefinition(
    val name: String,
    val camp: Camp,
    val eventListeners: List<RoleEventType> = emptyList()
)
