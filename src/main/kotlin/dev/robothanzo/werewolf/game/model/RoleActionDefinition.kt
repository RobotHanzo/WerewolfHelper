package dev.robothanzo.werewolf.game.model

enum class ActionTiming {
    NIGHT,
    DAY,
    ANYTIME
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
