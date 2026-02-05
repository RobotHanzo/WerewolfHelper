package dev.robothanzo.werewolf.game.model


/**
 * Structured game state data to replace the generic stateData map.
 */
data class GameStateData(
    var phaseType: String? = null,
    var phaseStartTime: Long = 0,
    var phaseEndTime: Long = 0,
    var pendingActions: MutableList<RoleActionInstance> = mutableListOf(),

    // Keyed collections for efficient lookup
    var actionUsage: MutableMap<String, MutableMap<String, Int>> = mutableMapOf(), // userId -> (actionId -> count)
    var actionStatuses: MutableMap<String, ActionStatus> = mutableMapOf(), // userId -> ActionStatus
    var actionPrompts: MutableMap<String, ActionPrompt> = mutableMapOf(), // playerId -> ActionPrompt
    var groupStates: MutableMap<String, GroupActionState> = mutableMapOf(), // actionId -> GroupActionState
    var werewolfVotes: MutableMap<String, WolfVote> = mutableMapOf(), // voterId -> WolfVote
    var werewolfMessages: MutableList<WerewolfMessage> = mutableListOf(),

    // Explicit Role State (Strongly typed flags)
    var lastGuardProtectedId: Long? = null,
    var nightWolfKillTargetId: Long? = null,
    var deathTriggerAvailableMap: MutableMap<String, Long> = mutableMapOf(), // actionId -> userId
    var roleFlags: MutableMap<String, Any> = mutableMapOf() // Generic flags for flexibility
)

/**
 * Represents the status of a player's action for UI display.
 */
data class ActionStatus(
    val playerId: String,
    val role: String,
    var status: String,
    var actionType: String = "",
    var targetId: String = "",
    var submittedAt: Long = 0L
)

/**
 * Represents a single vote in the werewolf voting phase.
 */
data class WolfVote(
    val voterId: String,
    var targetId: String = ""
)
