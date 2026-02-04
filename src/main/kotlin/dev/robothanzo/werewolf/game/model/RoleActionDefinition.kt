package dev.robothanzo.werewolf.game.model

enum class ActionTiming {
    NIGHT,
    DAY,
    ANYTIME,
    DEATH_TRIGGER  // Actions triggered when a role holder dies (e.g., Hunter, Wolf King)
}

data class RoleActionDefinition(
    val actionId: String,
    val roleName: String,
    val priority: Int,
    val timing: ActionTiming,
    val targetCount: Int,
    val usageLimit: Int,
    val requiresAliveTarget: Boolean = true
)
