package dev.robothanzo.werewolf.model

data class CustomActionDefinition(
    val actionId: String,
    val priority: Int,
    val timing: String, // "NIGHT", "DAY", "ANYTIME"
    val targetCount: Int,
    val usageLimit: Int = -1,
    val requiresAliveTarget: Boolean = true
)

data class CustomRoleDefinition(
    val name: String,
    val camp: String, // "WEREWOLF", "GOD", "VILLAGER"
    val actions: List<CustomActionDefinition>,
    val eventListeners: List<String> = emptyList()
)
