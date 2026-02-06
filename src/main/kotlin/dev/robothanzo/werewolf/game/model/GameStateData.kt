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
    var werewolfMessages: MutableList<WerewolfMessage> = mutableListOf(),

    // Explicit Role State (Strongly typed flags)
    var lastGuardProtectedId: Int? = null,
    var nightWolfKillTargetId: Int? = null,
    var deathTriggerAvailableMap: MutableMap<String, Int> = mutableMapOf(), // actionId -> playerId
    var darkMerchantTradeRecipientId: Int? = null,
    var darkMerchantGiftedSkill: String? = null,
    var wolfBrotherDiedDay: Int? = null,
    var deadPlayers: List<Int> = emptyList() // List of player IDs who died in the current phase
)
