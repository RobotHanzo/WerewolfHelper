package dev.robothanzo.werewolf.game.model


/**
 * Structured game state data to replace the generic stateData map.
 */
data class GameStateData(
    var phaseType: String? = null,
    var phaseStartTime: Long = 0,
    var phaseEndTime: Long = 0,
    var pendingActions: MutableList<RoleActionInstance> = mutableListOf(),

    // Unified action data (Keyed by playerId.toString() for consistency)
    var actionData: MutableMap<String, ActionData> = mutableMapOf(),
    var groupStates: MutableMap<String, GroupActionState> = mutableMapOf(), // actionId -> GroupActionState
    var werewolfVotes: MutableMap<String, WolfVote> = mutableMapOf(), // voterId -> WolfVote
    var werewolfMessages: MutableList<WerewolfMessage> = mutableListOf(),

    // Explicit Role State (Strongly typed flags)
    var lastGuardProtectedId: Int? = null,
    var nightWolfKillTargetId: Int? = null,
    var deathTriggerAvailableMap: MutableMap<String, Int> = mutableMapOf(), // actionId -> playerId
    var roleFlags: MutableMap<String, Any> = mutableMapOf() // Generic flags for flexibility
)

/**
 * Represents a single vote in the werewolf voting phase.
 */
data class WolfVote(
    val voterId: Int,
    var targetId: Int = -1
)
