package dev.robothanzo.werewolf.game.model

import io.swagger.v3.oas.annotations.media.Schema
import org.bson.codecs.pojo.annotations.BsonIgnore
import org.springframework.data.annotation.Transient


/**
 * Represents the sub-phases of the night.
 */
enum class NightPhase(val order: Int) {
    WOLF_YOUNGER_BROTHER_ACTION(0),
    WEREWOLF_VOTING(1),
    ROLE_ACTIONS(2)
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
    var actionDefinitionId: ActionDefinitionId?, // Null if not yet chosen
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

data class ExpelCandidateDto(
    val id: Int,
    val quit: Boolean,
    val voters: List<String>
)

data class ExpelStatus(
    val voting: Boolean,
    val endTime: Long?,
    val candidates: List<ExpelCandidateDto>
)

/**
 * Structured game state data to replace the generic stateData map.
 */
data class GameStateData(
    var phaseType: NightPhase? = null,
    var phaseStartTime: Long = 0,
    var phaseEndTime: Long = 0,
    // RoleDefinition shall have defined them, copy them here to track the usage and process when extra action are granted
    @Schema(example = "{\"1\": {\"WEREWOLF_KILL\": 1}}")
    var playerOwnedActions: MutableMap<Int, MutableMap<String, Int>> = mutableMapOf(), // playerId -> (actionId -> usesLeft)


    @Schema(description = "List of actions submitted in the current phase but not yet executed")
    var submittedActions: MutableList<RoleActionInstance> = mutableListOf(), // Actions yet to be processed, executed at death announcement

    @Schema(example = "{\"1\": [{\"actor\": 1, \"actionDefinitionId\": \"WEREWOLF_KILL\", \"targets\": [2]}]}")
    var executedActions: MutableMap<Int, MutableList<RoleActionInstance>> = mutableMapOf(), // day -> List of executed actions

    // Wolf features, these are rotated daily (in game day, as the final wolf kill is applied at death announcement and stored into executedActions)
    @Schema(example = "{\"WEREWOLF_KILL\": {\"actionId\": \"WEREWOLF_KILL\", \"finished\": false}}")
    var wolfStates: MutableMap<String, WolvesActionState> = mutableMapOf(), // actionId -> state

    @Schema(description = "List of messages sent by wolves")
    var werewolfMessages: MutableList<WolfMessage> = mutableListOf(),

    @Schema(description = "Player ID of the Wolf Younger Brother if he is awakened this night")
    var wolfBrotherAwakenedPlayerId: Int? = null,
) {
    // --- Transient fields for UI state synchronization ---
    @Transient
    @get:Schema(hidden = true)
    @BsonIgnore
    var speech: SpeechStatus? = null

    @Transient
    @get:Schema(hidden = true)
    @BsonIgnore
    var police: PoliceStatus? = null

    @Transient
    @get:Schema(hidden = true)
    @BsonIgnore
    var expel: ExpelStatus? = null

    // --- Computed Properties ---

    /**
     * Finds the target ID of the last successful werewolf kill in the current phase.
     */
    val nightWolfKillTargetId: Int?
        get() =
            submittedActions.find { it.actionDefinitionId == ActionDefinitionId.WEREWOLF_KILL && it.status == ActionStatus.SUBMITTED }?.targets?.firstOrNull()

    /**
     * Finds the target ID protected by the guard in the previous night.
     */
    val lastGuardProtectedId: Int?
        get() {
            // Find in current submitted actions (if it was the previous phase) or latest executed actions
            val currentGuardAction =
                submittedActions.find { it.actionDefinitionId == ActionDefinitionId.GUARD_PROTECT && it.status == ActionStatus.SUBMITTED }
            if (currentGuardAction != null) return currentGuardAction.targets.firstOrNull()

            // Sort days descending and look for the most recent guard action
            val lastNightActions = executedActions.entries.maxByOrNull { it.key }?.value
            return lastNightActions?.find { it.actionDefinitionId == ActionDefinitionId.GUARD_PROTECT }?.targets?.firstOrNull()
        }

    /**
     * Finds the day the Wolf Brother died.
     */
    val wolfBrotherDiedDay: Int?
        get() {
            return executedActions.entries.find { (_, actions) ->
                actions.any { it.actionDefinitionId == ActionDefinitionId.DEATH && it.actorRole == "狼兄" }
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

/**
 * UI-friendly status of the speech session
 */
data class SpeechStatus(
    val order: List<Int>,
    val currentSpeakerId: Int?,
    val endTime: Long,
    val totalTime: Int,
    val isPaused: Boolean = false,
    val interruptVotes: List<Int> = emptyList()
)

/**
 * UI-friendly status of the police election
 */
data class PoliceStatus(
    val state: String,
    val stageEndTime: Long?,
    val allowEnroll: Boolean,
    val allowUnEnroll: Boolean,
    val candidates: List<PoliceCandidateDto>
)

data class PoliceCandidateDto(
    val id: Int,
    val quit: Boolean,
    val voters: List<String>
)
