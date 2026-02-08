package dev.robothanzo.werewolf.game.model

import dev.robothanzo.werewolf.game.roles.PredefinedRoles


/**
 * Represents the sub-phases of the night.
 */
enum class NightPhase {
    WEREWOLF_VOTING,
    ROLE_ACTIONS
}

enum class ActionTiming {
    NIGHT,
    DAY,
    ANYTIME,
    DEATH_TRIGGER  // Actions triggered when a role holder dies (e.g., Hunter, Wolf King)
}

enum class ActionSubmissionSource {
    PLAYER,
    JUDGE,
    SYSTEM
}

/**
 * Status of a player's action during a phase
 */
enum class ActionStatus {
    PENDING, // 1. initial stage
    ACTING, // 2-1. when an action has been selected but not the targets
    SUBMITTED, // 3. when the action is fully submitted
    SKIPPED, // 2-2. when the action is skipped
    PROCESSED // 4. when the action has been executed during death announcement
}

const val SKIP_TARGET_ID = -1

data class RoleActionInstance(
    val actor: Int,
    val actorRole: String, // Role name of the actor at the time of action
    var actionDefinitionId: String,
    val targets: MutableList<Int>,
    var submittedBy: ActionSubmissionSource,
    var status: ActionStatus, // PENDING, ACTING, SUBMITTED, SKIPPED
    var targetPromptId: Long? = null // Discord message ID for target selection prompt
)

data class ActionSubmissionStatus(
    val playerId: Int,
    val role: String,
    val status: ActionStatus, // PENDING, ACTING, SUBMITTED, SKIPPED
    val actionType: String? = null, // The action chosen (e.g., "KILL", "HEAL", "CHECK")
    val targetId: String? = null,
    val submittedAt: Long? = null
)

/**
 * Group action state for coordinated actions like wolf kills
 */
data class WolvesActionState(
    val actionId: String,
    val electorates: List<Int>, // Player IDs of players (wolves) voting
    val votes: MutableList<WolfVote> = mutableListOf(),
    val messageId: Long? = null, // Discord message ID for the group channel
    val finished: Boolean = false
)

data class WolfVote(
    val voterId: Int,
    val targetId: Int?
)

data class WolfMessage(
    val senderId: Int,
    val content: String,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Complete night phase status for real-time spectator/judge viewing.
 */
data class NightStatus(
    val day: Int,
    val phaseType: NightPhase?, // Enum for the current sub-phase
    val startTime: Long,
    val endTime: Long,

    // Werewolf voting data (shown in first screen)
    val werewolfMessages: List<WolfMessage> = emptyList(),
    val werewolfVotes: List<WolfVote> = emptyList(),

    // Role action statuses (shown in second screen)
    val actionStatuses: List<ActionSubmissionStatus> = emptyList()
)

/**
 * Structured game state data to replace the generic stateData map.
 */
data class GameStateData(
    var phaseType: NightPhase? = null,
    var phaseStartTime: Long = 0,
    var phaseEndTime: Long = 0,
    // RoleDefinition shall have defined them, copy them here to track the usage and process when extra action are granted
    var playerOwnedActions: MutableMap<Int, MutableMap<String, Int>> = mutableMapOf(), // playerId -> (actionId -> usesLeft)
    var deathTriggerAvailableMap: MutableMap<String, Int> = mutableMapOf(), // actionId -> playerId
    var submittedActions: MutableList<RoleActionInstance> = mutableListOf(), // Actions yet to be processed, executed at death announcement
    var executedActions: MutableMap<Int, MutableList<RoleActionInstance>> = mutableMapOf(), // day -> List of executed actions

    // Wolf features, these are rotated daily (in game day, as the final wolf kill is applied at death announcement and stored into executedActions)
    var wolfStates: MutableMap<String, WolvesActionState> = mutableMapOf(), // actionId -> state
    var werewolfMessages: MutableList<WolfMessage> = mutableListOf(),
) {

    // --- Computed Properties ---

    /**
     * Finds the target ID of the last successful werewolf kill in the current phase.
     */
    val nightWolfKillTargetId: Int?
        get() = submittedActions.find { it.actionDefinitionId == PredefinedRoles.WEREWOLF_KILL && it.status == ActionStatus.SUBMITTED }?.targets?.firstOrNull()

    /**
     * Finds the target ID protected by the guard in the previous night.
     */
    val lastGuardProtectedId: Int?
        get() {
            // Find in current submitted actions (if it was the previous phase) or latest executed actions
            val currentGuardAction =
                submittedActions.find { it.actionDefinitionId == PredefinedRoles.GUARD_PROTECT && it.status == ActionStatus.SUBMITTED }
            if (currentGuardAction != null) return currentGuardAction.targets.firstOrNull()

            // Sort days descending and look for the most recent guard action
            val lastNightActions = executedActions.entries.sortedByDescending { it.key }.firstOrNull()?.value
            return lastNightActions?.find { it.actionDefinitionId == PredefinedRoles.GUARD_PROTECT }?.targets?.firstOrNull()
        }

    /**
     * Finds the recipient of the Dark Merchant's trade.
     */
    val darkMerchantTradeRecipientId: Int?
        get() = submittedActions.find { it.actionDefinitionId.startsWith(PredefinedRoles.DARK_MERCHANT_TRADE_PREFIX) && it.status == ActionStatus.SUBMITTED }?.targets?.firstOrNull()
            ?: executedActions.values.flatten()
                .find { it.actionDefinitionId.startsWith(PredefinedRoles.DARK_MERCHANT_TRADE_PREFIX) }?.targets?.firstOrNull()

    /**
     * Finds the gifted skill from Dark Merchant.
     */
    val darkMerchantGiftedSkill: String?
        get() = (submittedActions.find { it.actionDefinitionId.startsWith(PredefinedRoles.DARK_MERCHANT_TRADE_PREFIX) && it.status == ActionStatus.SUBMITTED }
            ?: executedActions.values.flatten()
                .find { it.actionDefinitionId.startsWith(PredefinedRoles.DARK_MERCHANT_TRADE_PREFIX) })?.actionDefinitionId

    /**
     * Finds the day the Wolf Brother died.
     */
    val wolfBrotherDiedDay: Int?
        get() {
            return executedActions.entries.find { (_, actions) ->
                actions.any { it.actionDefinitionId == "DEATH" && it.actorRole == "狼兄" }
            }?.key
        }

    // For frontend display after death announcement
    var deadPlayers: List<Int> = emptyList()
}

/**
 * Result of night action resolution
 */
data class NightResolutionResult(
    val deaths: Map<DeathCause, List<Int>>, // cause -> list of player IDs (Int)
    val saved: List<Int>, // player IDs (Int)
)
